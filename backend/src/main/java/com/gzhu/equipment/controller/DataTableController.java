package com.gzhu.equipment.controller;

import com.gzhu.equipment.common.R;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据表管理 — 系统管理员/Lab Admin 数据库表浏览与编辑
 *
 * 权限分级：
 * - SYSTEM_ADMIN: 所有表（含 sys_user / system_config）
 * - LAB_ADMIN: 设备相关表（不含用户/认证/配置）
 */
@Slf4j
@RestController
@RequestMapping("/admin/data-tables")
@RequiredArgsConstructor
@Api(tags = "数据表管理")
public class DataTableController {

    private final JdbcTemplate jdbcTemplate;

    /** 实验室管理员可访问的表（白名单，不含安全敏感表） */
    private static final Set<String> LAB_ADMIN_ALLOWED = Set.of(
            "device", "device_image", "device_category", "category_mapping",
            "borrow_record", "approval_log", "attachment",
            "notification", "repair_record",
            "laboratory", "laboratory_room", "category_description",
            "borrow_outcome"
    );
    /** 实验室管理员不可见的敏感表（仅系统管理员） */
    private static final Set<String> SECURITY_SENSITIVE = Set.of(
            "sys_user", "sys_log", "system_config"
    );

    /** 系统管理员禁止编辑的表（核心安全表，只读） */
    private static final Set<String> READ_ONLY_TABLES = Set.of(
            "sys_user", "system_config"
    );

    // ==================== 表列表 ====================

    @GetMapping("/tables")
    @ApiOperation("获取所有数据表列表")
    @PreAuthorize("hasAnyAuthority('admin:user','laboratory:manage')")
    public R<List<Map<String, Object>>> listTables() {
        boolean isAdmin = isSystemAdmin();
        List<Map<String, Object>> tables = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME, TABLE_ROWS, TABLE_COMMENT, " +
                "ROUND(DATA_LENGTH/1024/1024,2) AS SIZE_MB " +
                "FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = 'device_borrow' ORDER BY TABLE_NAME");

        if (!isAdmin) {
            tables = tables.stream()
                    .filter(t -> LAB_ADMIN_ALLOWED.contains(t.get("TABLE_NAME")))
                    .collect(Collectors.toList());
        }
        return R.ok(tables);
    }

    // ==================== 表数据查询 ====================

    @GetMapping("/{tableName}")
    @ApiOperation("查询表数据（分页+排序+关键词）")
    @PreAuthorize("hasAnyAuthority('admin:user','laboratory:manage')")
    public R<Map<String, Object>> queryTable(
            @PathVariable String tableName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false, defaultValue = "asc") String order,
            @RequestParam(required = false) String keyword) {

        if (!canAccess(tableName)) return R.fail(403, "无权访问此表");

        // 校验表名防SQL注入（仅允许字母/下划线）
        if (!tableName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            return R.fail(400, "非法表名");
        }

        // 获取列信息
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME, DATA_TYPE, COLUMN_COMMENT, " +
                "IS_NULLABLE, COLUMN_KEY, EXTRA " +
                "FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = 'device_borrow' AND TABLE_NAME = ? " +
                "ORDER BY ORDINAL_POSITION", tableName);

        // 构建排序（仅允许存在的列名）
        Set<String> colNames = columns.stream()
                .map(c -> (String) c.get("COLUMN_NAME")).collect(Collectors.toSet());
        String orderBy = "";
        if (sort != null && colNames.contains(sort)) {
            String dir = "desc".equalsIgnoreCase(order) ? "DESC" : "ASC";
            orderBy = " ORDER BY `" + sort + "` " + dir;
        }

        // 关键词搜索（模糊匹配所有字符串列）
        String where = "";
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            StringBuilder sb = new StringBuilder(" WHERE (");
            boolean first = true;
            for (Map<String, Object> col : columns) {
                String dt = (String) col.get("DATA_TYPE");
                if (dt != null && (dt.contains("char") || dt.contains("text"))) {
                    if (!first) sb.append(" OR ");
                    sb.append("`").append(col.get("COLUMN_NAME")).append("` LIKE ?");
                    params.add("%" + keyword + "%");
                    first = false;
                }
            }
            sb.append(")");
            if (!first) where = sb.toString();
        }

        // 总数
        String countSql = "SELECT COUNT(*) FROM `" + tableName + "`" + where;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());

        // 分页数据
        int offset = (page - 1) * size;
        String dataSql = "SELECT * FROM `" + tableName + "`" + where + orderBy +
                " LIMIT " + offset + "," + size;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(dataSql, params.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("columns", columns);
        result.put("rows", rows);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("readOnly", READ_ONLY_TABLES.contains(tableName));

        return R.ok(result);
    }

    // ==================== 单行更新 ====================

    @PutMapping("/{tableName}/{id}")
    @ApiOperation("更新单行数据（仅系统管理员）")
    @PreAuthorize("hasAuthority('admin:user')")
    public R<String> updateRow(
            @PathVariable String tableName,
            @PathVariable Long id,
            @RequestBody Map<String, Object> updates) {

        if (!canAccess(tableName)) return R.fail(403, "无权访问");
        if (READ_ONLY_TABLES.contains(tableName)) return R.fail(403, "此表只读");
        if (!tableName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) return R.fail(400, "非法表名");
        if (updates.isEmpty()) return R.fail(400, "无更新数据");

        try {
            // 校验列名
            Set<String> validCols = getColumnNames(tableName);
            updates.keySet().removeIf(k -> !validCols.contains(k) || "id".equals(k));
            if (updates.isEmpty()) return R.fail(400, "无有效更新列");

            StringBuilder setClause = new StringBuilder(" SET ");
            List<Object> params = new ArrayList<>();
            boolean first = true;
            for (Map.Entry<String, Object> e : updates.entrySet()) {
                if (!first) setClause.append(", ");
                setClause.append("`").append(e.getKey()).append("` = ?");
                params.add(e.getValue());
                first = false;
            }
            params.add(id);

            String sql = "UPDATE `" + tableName + "`" + setClause + " WHERE id = ?";
            int rows = jdbcTemplate.update(sql, params.toArray());

            log.info("数据表编辑: table={} id={} rows={}", tableName, id, rows);
            return R.ok("已更新 " + rows + " 行");
        } catch (Exception e) {
            log.error("更新失败: table={} id={} error={}", tableName, id, e.getMessage());
            return R.fail(500, "更新失败: " + e.getMessage());
        }
    }

    // ==================== 批量更新 ====================

    @PutMapping("/{tableName}/batch")
    @ApiOperation("批量更新数据")
    @PreAuthorize("hasAuthority('admin:user')")
    public R<String> batchUpdate(
            @PathVariable String tableName,
            @RequestBody Map<String, Object> body) {

        if (!canAccess(tableName)) return R.fail(403, "无权访问");
        if (READ_ONLY_TABLES.contains(tableName)) return R.fail(403, "此表只读");
        if (!tableName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) return R.fail(400, "非法表名");

        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) body.get("ids");
        @SuppressWarnings("unchecked")
        Map<String, Object> updates = (Map<String, Object>) body.get("updates");

        if (ids == null || ids.isEmpty()) return R.fail(400, "未选择记录");
        if (updates == null || updates.isEmpty()) return R.fail(400, "无更新数据");

        try {
            Set<String> validCols = getColumnNames(tableName);
            updates.keySet().removeIf(k -> !validCols.contains(k) || "id".equals(k));
            if (updates.isEmpty()) return R.fail(400, "无有效更新列");

            StringBuilder setClause = new StringBuilder(" SET ");
            List<Object> params = new ArrayList<>();
            boolean first = true;
            for (Map.Entry<String, Object> e : updates.entrySet()) {
                if (!first) setClause.append(", ");
                setClause.append("`").append(e.getKey()).append("` = ?");
                params.add(e.getValue());
                first = false;
            }

            String placeholders = ids.stream().map(i -> "?").collect(Collectors.joining(","));
            params.addAll(ids);
            String sql = "UPDATE `" + tableName + "`" + setClause +
                    " WHERE id IN (" + placeholders + ")";
            int rows = jdbcTemplate.update(sql, params.toArray());

            log.info("批量编辑: table={} ids={} rows={}", tableName, ids.size(), rows);
            return R.ok("批量更新完成，影响 " + rows + " 行");
        } catch (Exception e) {
            log.error("批量更新失败: table={} error={}", tableName, e.getMessage());
            return R.fail(500, "批量更新失败: " + e.getMessage());
        }
    }

    // ==================== 删除行（仅管理员） ====================

    @DeleteMapping("/{tableName}/{id}")
    @ApiOperation("删除单行数据（仅系统管理员）")
    @PreAuthorize("hasAuthority('admin:user')")
    public R<String> deleteRow(@PathVariable String tableName, @PathVariable Long id) {
        if (!tableName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) return R.fail(400, "非法表名");
        if (READ_ONLY_TABLES.contains(tableName)) return R.fail(403, "此表不可删除记录");

        try {
            int rows = jdbcTemplate.update("DELETE FROM `" + tableName + "` WHERE id = ?", id);
            log.warn("数据表删除: table={} id={}", tableName, id);
            return R.ok("已删除 " + rows + " 行");
        } catch (Exception e) {
            return R.fail(500, "删除失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{tableName}/rows/batch")
    @ApiOperation("批量删除行（仅系统管理员）")
    @PreAuthorize("hasAuthority('admin:user')")
    public R<String> batchDeleteRows(@PathVariable String tableName,
                                      @RequestBody List<Long> ids) {
        if (!tableName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) return R.fail(400, "非法表名");
        if (READ_ONLY_TABLES.contains(tableName)) return R.fail(403, "此表不可删除记录");
        if (ids == null || ids.isEmpty()) return R.fail(400, "未选择记录");

        try {
            String placeholders = ids.stream().map(i -> "?").collect(java.util.stream.Collectors.joining(","));
            String sql = "DELETE FROM `" + tableName + "` WHERE id IN (" + placeholders + ")";
            int rows = jdbcTemplate.update(sql, ids.toArray());
            log.warn("批量删除行: table={} count={}", tableName, ids.size());
            return R.ok("已删除 " + rows + " 行");
        } catch (Exception e) {
            return R.fail(500, "批量删除失败: " + e.getMessage());
        }
    }

    // ==================== 新增行（仅系统管理员） ====================

    @PostMapping("/{tableName}")
    @ApiOperation("新增一行数据")
    @PreAuthorize("hasAuthority('admin:user')")
    public R<String> insertRow(@PathVariable String tableName,
                                @RequestBody Map<String, Object> row) {
        if (!canAccess(tableName)) return R.fail(403, "无权访问");
        if (READ_ONLY_TABLES.contains(tableName)) return R.fail(403, "此表不可新增记录");
        if (!tableName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) return R.fail(400, "非法表名");
        if (row.isEmpty()) return R.fail(400, "无数据");

        try {
            Set<String> validCols = getColumnNames(tableName);
            row.keySet().removeIf(k -> !validCols.contains(k) || "id".equals(k));
            if (row.isEmpty()) return R.fail(400, "无有效列");

            StringBuilder cols = new StringBuilder("(");
            StringBuilder vals = new StringBuilder("(");
            List<Object> params = new ArrayList<>();
            boolean first = true;
            for (Map.Entry<String, Object> e : row.entrySet()) {
                if (!first) { cols.append(", "); vals.append(", "); }
                cols.append("`").append(e.getKey()).append("`");
                vals.append("?");
                params.add(e.getValue());
                first = false;
            }
            cols.append(")"); vals.append(")");

            String sql = "INSERT INTO `" + tableName + "` " + cols + " VALUES " + vals;
            int rows = jdbcTemplate.update(sql, params.toArray());
            log.warn("数据表新增: table={} rows={}", tableName, rows);
            return R.ok("新增 " + rows + " 行");
        } catch (Exception e) {
            return R.fail(500, "新增失败: " + e.getMessage());
        }
    }

    // ==================== 列管理（仅系统管理员，仅支持删除列） ====================

    @DeleteMapping("/{tableName}/columns")
    @ApiOperation("删除列（仅系统管理员）")
    @PreAuthorize("hasAuthority('admin:user')")
    public R<String> dropColumn(@PathVariable String tableName,
                                 @RequestParam String columnName) {
        if (!tableName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) return R.fail(400, "非法表名");
        if (READ_ONLY_TABLES.contains(tableName)) return R.fail(403, "此表不可修改结构");
        if (!columnName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) return R.fail(400, "非法列名");
        if ("id".equals(columnName)) return R.fail(400, "不能删除主键列");

        try {
            jdbcTemplate.execute("ALTER TABLE `" + tableName + "` DROP COLUMN `" + columnName + "`");
            log.warn("列删除: table={} col={}", tableName, columnName);
            return R.ok("已删除列: " + columnName);
        } catch (Exception e) {
            return R.fail(500, "删除列失败: " + e.getMessage());
        }
    }

    // ==================== 表导出（CSV / XLSX） ====================

    @GetMapping("/{tableName}/export")
    @ApiOperation("导出表数据为CSV或XLSX")
    @PreAuthorize("hasAnyAuthority('admin:user','laboratory:manage')")
    public ResponseEntity<byte[]> exportTable(
            @PathVariable String tableName,
            @RequestParam(defaultValue = "csv") String format) {
        if (!canAccess(tableName)) {
            throw new org.springframework.security.access.AccessDeniedException("无权访问");
        }
        if (!tableName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("非法表名");
        }

        try {
            List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                    "SELECT COLUMN_NAME, DATA_TYPE, COLUMN_COMMENT FROM information_schema.COLUMNS " +
                    "WHERE TABLE_SCHEMA = 'device_borrow' AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION", tableName);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT * FROM `" + tableName + "` LIMIT 10000");

            if ("xlsx".equalsIgnoreCase(format)) {
                LinkedHashMap<String, String> headers = new LinkedHashMap<>();
                for (Map<String, Object> col : columns) {
                    String key = (String) col.get("COLUMN_NAME");
                    String label = (String) col.getOrDefault("COLUMN_COMMENT", key);
                    headers.put(key, (label == null || label.isEmpty()) ? key : key + "(" + label + ")");
                }
                byte[] xlsx = com.gzhu.equipment.common.ExcelExportUtil.exportToXlsx(rows, headers);
                org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
                h.setContentType(org.springframework.http.MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
                h.set(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=" + tableName + "_export_" + System.currentTimeMillis() + ".xlsx");
                return ResponseEntity.ok().headers(h).body(xlsx);
            } else {
                // CSV
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                bos.write(0xEF); bos.write(0xBB); bos.write(0xBF);
                OutputStreamWriter osw = new OutputStreamWriter(bos, StandardCharsets.UTF_8);
                // 表头
                List<String> colNames = columns.stream().map(m -> (String) m.get("COLUMN_NAME")).collect(Collectors.toList());
                osw.write(String.join(",", colNames) + "\n");
                for (Map<String, Object> row : rows) {
                    String line = colNames.stream()
                            .map(c -> escapeCsv(row.get(c)))
                            .collect(Collectors.joining(","));
                    osw.write(line + "\n");
                }
                osw.flush(); osw.close();
                org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
                h.setContentType(org.springframework.http.MediaType.parseMediaType("text/csv;charset=UTF-8"));
                h.set(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=" + tableName + "_export_" + System.currentTimeMillis() + ".csv");
                return ResponseEntity.ok().headers(h).body(bos.toByteArray());
            }
        } catch (org.springframework.security.access.AccessDeniedException e) { throw e;
        } catch (Exception e) {
            throw new RuntimeException("导出失败: " + e.getMessage(), e);
        }
    }

    // ==================== 辅助 ====================

    private boolean canAccess(String tableName) {
        if (isSystemAdmin()) return true;
        return LAB_ADMIN_ALLOWED.contains(tableName);
    }

    private boolean isSystemAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("admin:user"));
    }

    private Set<String> getColumnNames(String tableName) {
        return jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = 'device_borrow' AND TABLE_NAME = ?", tableName)
                .stream().map(m -> (String) m.get("COLUMN_NAME"))
                .collect(Collectors.toSet());
    }

    private String escapeCsv(Object val) {
        if (val == null) return "\"\"";
        String s = String.valueOf(val);
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
