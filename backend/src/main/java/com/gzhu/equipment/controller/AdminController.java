package com.gzhu.equipment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gzhu.equipment.common.ExcelExportUtil;
import com.gzhu.equipment.common.R;
import com.gzhu.equipment.entity.SysUser;
import com.gzhu.equipment.mapper.SysUserMapper;
import com.gzhu.equipment.service.SysUserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Api(tags = "系统管理")
public class AdminController {

    private final SysUserMapper userMapper;
    private final SysUserService userService;

    @GetMapping("/users/{id}")
    @ApiOperation("用户详情")
    @PreAuthorize("hasAnyAuthority('admin:user','approval:first','approval:second','return:manage')")
    public R<SysUser> getUser(@PathVariable Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) return R.fail(404, "用户不存在");
        return R.ok(user);
    }

    @GetMapping("/users")
    @ApiOperation("用户列表（支持部门/角色筛选）")
    @PreAuthorize("hasAuthority('admin:user')")
    public R<IPage<SysUser>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Integer userType) {
        LambdaQueryWrapper<SysUser> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(wp -> wp.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getRealName, keyword));
        }
        if (StringUtils.hasText(department)) {
            w.like(SysUser::getDepartment, department);
        }
        if (userType != null) {
            w.eq(SysUser::getUserType, userType);
        }
        w.orderByAsc(SysUser::getUserType).orderByDesc(SysUser::getCreateTime);
        return R.ok(userMapper.selectPage(new Page<>(page, size), w));
    }

    // ==================== 单个创建/删除 ====================

    @PostMapping("/users")
    @ApiOperation("创建本地账户")
    @PreAuthorize("hasAuthority('admin:user')")
    public R<SysUser> createUser(
            @RequestParam String username,
            @RequestParam String realName,
            @RequestParam Integer userType,
            @RequestParam String password,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone) {
        try {
            SysUser user = userService.createLocalUser(username, realName, userType, department, email, phone, password);
            return R.ok(user);
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    @DeleteMapping("/users/{id}")
    @ApiOperation("删除用户")
    @PreAuthorize("hasAuthority('admin:user')")
    public R<String> deleteUser(@PathVariable Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) return R.fail(404, "用户不存在");
        if ("admin".equals(user.getUsername())) return R.fail(400, "不能删除admin账户");
        userMapper.deleteById(id);
        return R.ok("已删除");
    }

    // ==================== 批量操作 ====================

    @PostMapping("/users/batch")
    @ApiOperation("批量创建账户")
    @PreAuthorize("hasAuthority('admin:user')")
    public R<Map<String, Object>> batchCreate(@RequestBody List<Map<String, String>> users) {
        int success = 0, fail = 0;
        List<String> errors = new ArrayList<>();
        for (Map<String, String> u : users) {
            try {
                userService.createLocalUser(
                        u.get("username"), u.get("realName"),
                        Integer.parseInt(u.getOrDefault("userType", "0")),
                        u.get("department"), u.get("email"), u.get("phone"),
                        u.get("password"));
                success++;
            } catch (Exception e) {
                fail++;
                errors.add((u.get("username") != null ? u.get("username") : "?") + ": " + e.getMessage());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success); result.put("fail", fail); result.put("errors", errors);
        log.info("批量创建用户: success={} fail={}", success, fail);
        return R.ok(result);
    }

    @DeleteMapping("/users/batch")
    @ApiOperation("批量删除账户")
    @PreAuthorize("hasAuthority('admin:user')")
    public R<Map<String, Object>> batchDelete(@RequestBody List<Long> ids) {
        int deleted = 0, skipped = 0;
        for (Long id : ids) {
            SysUser u = userMapper.selectById(id);
            if (u == null || "admin".equals(u.getUsername())) { skipped++; continue; }
            userMapper.deleteById(id);
            deleted++;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", deleted); result.put("skipped", skipped);
        log.warn("批量删除用户: deleted={} skipped={}", deleted, skipped);
        return R.ok(result);
    }

    // ==================== 模板导出（CSV/XLSX） ====================

    @GetMapping("/users/template")
    @ApiOperation("下载批量操作模板")
    @PreAuthorize("hasAuthority('admin:user')")
    public void downloadTemplate(@RequestParam(defaultValue = "xlsx") String format,
                                  javax.servlet.http.HttpServletResponse response) throws Exception {
        if ("csv".equalsIgnoreCase(format)) {
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=用户批量操作模板.csv");
            response.getOutputStream().write(new byte[]{(byte)0xEF,(byte)0xBB,(byte)0xBF});
            OutputStreamWriter w = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);
            w.write("用户名,姓名,角色(0学生1教师2实验室管理员3系统管理员),部门,密码,操作(create/destroy)\n");
            w.write("zhangsan,张三,0,建筑学院,abc123456,create\n");
            w.flush(); w.close(); return;
        }

        // XLSX 模板
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("用户批量操作");
            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font hf = wb.createFont(); hf.setBold(true); headerStyle.setFont(hf);

            String[] hdrs = {"用户名", "姓名", "角色(0学生1教师2实验室管理员3系统管理员)", "部门", "密码(至少8位)", "操作(create/destroy)"};
            Row hr = sheet.createRow(0);
            for (int i = 0; i < hdrs.length; i++) { Cell c = hr.createCell(i); c.setCellValue(hdrs[i]); c.setCellStyle(headerStyle); }
            Row ex = sheet.createRow(1);
            ex.createCell(0).setCellValue("zhangsan"); ex.createCell(1).setCellValue("张三");
            ex.createCell(2).setCellValue("0"); ex.createCell(3).setCellValue("建筑学院");
            ex.createCell(4).setCellValue("abc123456"); ex.createCell(5).setCellValue("create");
            for (int i = 0; i < hdrs.length; i++) sheet.setColumnWidth(i, 4500);

            ByteArrayOutputStream bos = new ByteArrayOutputStream(); wb.write(bos);
            byte[] xlsx = bos.toByteArray();
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=用户批量操作模板.xlsx");
            response.setContentLength(xlsx.length);
            response.getOutputStream().write(xlsx);
            response.flushBuffer();
        }
    }

    // ==================== 模板导入批量操作 ====================

    @PostMapping("/users/import")
    @ApiOperation("导入模板文件批量创建/销毁账户")
    @PreAuthorize("hasAuthority('admin:user')")
    public R<Map<String, Object>> importUsers(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new LinkedHashMap<>();
        int created = 0, destroyed = 0, failed = 0;
        List<String> errors = new ArrayList<>();

        try {
            List<Map<String, String>> rows = parseUserTemplate(file);
            for (Map<String, String> row : rows) {
                String action = row.getOrDefault("action", "create").trim().toLowerCase();
                String username = row.get("username");
                if (!StringUtils.hasText(username)) { failed++; errors.add("缺少用户名"); continue; }

                try {
                    if ("destroy".equals(action)) {
                        SysUser exist = userMapper.selectOne(
                                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
                        if (exist == null) { failed++; errors.add(username + ": 用户不存在"); continue; }
                        if ("admin".equals(exist.getUsername())) { failed++; errors.add("admin: 不可销毁"); continue; }
                        userMapper.deleteById(exist.getId());
                        destroyed++;
                    } else {
                        userService.createLocalUser(username, row.get("realName"),
                                Integer.parseInt(row.getOrDefault("userType", "0")),
                                row.get("department"), row.get("email"), row.get("phone"),
                                row.get("password"));
                        created++;
                    }
                } catch (Exception e) {
                    failed++;
                    errors.add(username + ": " + (e instanceof IllegalArgumentException ? e.getMessage() : "操作失败"));
                }
            }
        } catch (Exception e) {
            log.error("模板导入失败: {}", e.getMessage(), e);
            return R.fail("文件解析失败: " + e.getMessage());
        }

        result.put("created", created); result.put("destroyed", destroyed);
        result.put("failed", failed); result.put("errors", errors);
        log.info("批量导入: created={} destroyed={} failed={}", created, destroyed, failed);
        return R.ok(result);
    }

    // ==================== 角色/状态管理 ====================

    @PutMapping("/users/{id}/role")
    @ApiOperation("修改用户角色")
    @PreAuthorize("hasAuthority('admin:user')")
    public R<Void> updateRole(@PathVariable Long id, @RequestParam Integer userType) {
        SysUser user = userMapper.selectById(id);
        if (user == null) return R.fail(404, "用户不存在");
        user.setUserType(userType);
        userMapper.updateById(user);
        return R.ok();
    }

    @PutMapping("/users/{id}/status")
    @ApiOperation("启用/禁用用户")
    @PreAuthorize("hasAuthority('admin:user')")
    public R<Void> toggleStatus(@PathVariable Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) return R.fail(404, "用户不存在");
        user.setStatus(user.getStatus() == 1 ? 0 : 1);
        userMapper.updateById(user);
        return R.ok();
    }

    @PutMapping("/users/{id}/password")
    @ApiOperation("管理员重置用户密码")
    @PreAuthorize("hasAuthority('admin:user')")
    public R<String> resetPassword(@PathVariable Long id, @RequestParam String newPassword) {
        SysUser user = userMapper.selectById(id);
        if (user == null) return R.fail(404, "用户不存在");
        if (newPassword == null || newPassword.length() < 8) return R.fail(400, "密码至少8位");
        user.setPassword(userService.encodePassword(newPassword));
        userMapper.updateById(user);
        log.info("管理员重置密码: userId={} username={}", id, user.getUsername());
        return R.ok("密码已重置");
    }

    // ==================== 文件解析 ====================

    private List<Map<String, String>> parseUserTemplate(MultipartFile file) throws Exception {
        String name = file.getOriginalFilename();
        if (name == null) throw new IllegalArgumentException("文件名不能为空");
        String lower = name.toLowerCase();

        if (lower.endsWith(".csv")) {
            return parseCsv(file);
        } else if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return parseExcel(file);
        }
        throw new IllegalArgumentException("仅支持 CSV / XLSX 格式");
    }

    private List<Map<String, String>> parseCsv(MultipartFile file) throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();
        byte[] bytes = file.getBytes();
        String content = new String(bytes, detectEncoding(bytes));
        String[] lines = content.split("\\r?\\n");
        if (lines.length < 2) return rows;

        String[] headers = lines[0].split(",");
        // 映射中文/英文列名到标准 key
        Map<Integer, String> colMap = new LinkedHashMap<>();
        for (int i = 0; i < headers.length; i++) {
            String h = headers[i].trim().replace("\"", "");
            if (h.contains("用户名") || h.equalsIgnoreCase("username")) colMap.put(i, "username");
            else if (h.contains("姓名") || h.equalsIgnoreCase("realName")) colMap.put(i, "realName");
            else if (h.contains("角色") || h.equalsIgnoreCase("userType")) colMap.put(i, "userType");
            else if (h.contains("部门") || h.equalsIgnoreCase("department")) colMap.put(i, "department");
            else if (h.contains("密码") || h.equalsIgnoreCase("password")) colMap.put(i, "password");
            else if (h.contains("操作") || h.equalsIgnoreCase("action")) colMap.put(i, "action");
        }

        for (int r = 1; r < lines.length; r++) {
            String line = lines[r].trim();
            if (line.isEmpty()) continue;
            String[] vals = parseCsvLine(line);
            Map<String, String> row = new LinkedHashMap<>();
            for (Map.Entry<Integer, String> e : colMap.entrySet()) {
                if (e.getKey() < vals.length) row.put(e.getValue(), vals[e.getKey()].trim().replace("\"", ""));
            }
            if (row.containsKey("username") && !row.get("username").isEmpty()) rows.add(row);
        }
        return rows;
    }

    private List<Map<String, String>> parseExcel(MultipartFile file) throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();
        Workbook wb;
        try { wb = new XSSFWorkbook(file.getInputStream()); }
        catch (Exception e) { wb = WorkbookFactory.create(file.getInputStream()); }

        Sheet sheet = wb.getSheetAt(0);
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) { wb.close(); return rows; }

        Map<Integer, String> colMap = new LinkedHashMap<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell c = headerRow.getCell(i);
            if (c == null) continue;
            String h = c.getStringCellValue().trim();
            if (h.contains("用户名") || h.equalsIgnoreCase("username")) colMap.put(i, "username");
            else if (h.contains("姓名") || h.equalsIgnoreCase("realName")) colMap.put(i, "realName");
            else if (h.contains("角色") || h.equalsIgnoreCase("userType")) colMap.put(i, "userType");
            else if (h.contains("部门") || h.equalsIgnoreCase("department")) colMap.put(i, "department");
            else if (h.contains("密码") || h.equalsIgnoreCase("password")) colMap.put(i, "password");
            else if (h.contains("操作") || h.equalsIgnoreCase("action")) colMap.put(i, "action");
        }

        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row dataRow = sheet.getRow(r);
            if (dataRow == null) continue;
            Map<String, String> row = new LinkedHashMap<>();
            for (Map.Entry<Integer, String> e : colMap.entrySet()) {
                Cell c = dataRow.getCell(e.getKey());
                String val = "";
                if (c != null) {
                    c.setCellType(CellType.STRING);
                    val = c.getStringCellValue().trim();
                }
                row.put(e.getValue(), val);
            }
            if (row.containsKey("username") && !row.get("username").isEmpty()) rows.add(row);
        }
        wb.close();
        return rows;
    }

    private String detectEncoding(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF)
            return "UTF-8";
        try { new String(bytes, "UTF-8"); return "UTF-8"; } catch (Exception e) { return "GBK"; }
    }

    private String[] parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') { inQuotes = !inQuotes; }
            else if (c == ',' && !inQuotes) { result.add(sb.toString()); sb.setLength(0); }
            else { sb.append(c); }
        }
        result.add(sb.toString());
        return result.toArray(new String[0]);
    }
}
