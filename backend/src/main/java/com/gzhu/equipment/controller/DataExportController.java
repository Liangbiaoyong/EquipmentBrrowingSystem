package com.gzhu.equipment.controller;

import com.gzhu.equipment.common.ExcelExportUtil;
import com.gzhu.equipment.common.R;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务数据导出 — 供管理员把平台数据导出为 Excel/CSV 后自行分析。
 *
 * 与「数据表管理」的区别：那边导出的是**数据库原始表**（列名是英文、外键是 ID），
 * 这边导出的是**业务口径**的数据集（列名是中文、外键已展开成名称），
 * 拿到就能直接做透视表和图表。
 *
 * 权限：仅实验室管理员与系统管理员。
 */
@Slf4j
@RestController
@RequestMapping("/admin/export")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('admin:export')")
@Api(tags = "业务数据导出")
public class DataExportController {

    private final JdbcTemplate jdbcTemplate;

    /** 单个数据集：一段 SQL + 一份导出表头（LinkedHashMap 保证列顺序） */
    private record Dataset(String key, String name, String description,
                           String sql, LinkedHashMap<String, String> headers) {}

    /** 全部可导出的数据集。新增数据集只需往 buildDatasets() 里加一条。 */
    private static final List<Dataset> DATASETS = buildDatasets();

    private static LinkedHashMap<String, String> hdrs(String... kv) {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    private static List<Dataset> buildDatasets() {
        List<Dataset> list = new ArrayList<>();

        list.add(new Dataset("borrow_records", "借用记录明细",
                "全部借用单：设备、借用人与审核人、各环节时间、状态、逾期天数、设备情况、成果",
                "SELECT b.id, d.name AS device_name, d.asset_no AS device_asset_no, d.custodian, "
                        + "u.real_name AS user_name, b.purpose_category, b.purpose_subcategory, b.purpose, b.reason, "
                        + "b.status, b.start_time, b.end_time, b.pickup_time, b.return_request_time, b.real_return_time, "
                        + "b.overdue_days, b.damage_report, b.outcome, b.create_time "
                        + "FROM borrow_record b "
                        + "LEFT JOIN device d ON b.device_id = d.id "
                        + "LEFT JOIN sys_user u ON b.user_id = u.id ORDER BY b.id DESC",
                hdrs("id", "单号", "device_name", "设备名称", "device_asset_no", "资产编号", "custodian", "设备使用人",
                        "user_name", "借用人", "purpose_category", "目的大类", "purpose_subcategory", "目的子类",
                        "purpose", "目的详情", "reason", "备注", "status", "状态",
                        "start_time", "借用开始", "end_time", "应归还", "pickup_time", "实际借出",
                        "return_request_time", "提交归还申请", "real_return_time", "实际归还",
                        "overdue_days", "逾期天数", "damage_report", "设备情况", "outcome", "借用成果",
                        "create_time", "申请时间")));

        list.add(new Dataset("device_ledger", "设备台账",
                "全部设备全字段：分类、实验室、存放地、使用人、数量、金额、状态",
                "SELECT d.id, d.asset_no, d.name, d.model, d.specs, c.name AS category_name, d.location, "
                        + "l.name AS laboratory_name, d.department, d.custodian, d.total_qty, d.available_qty, "
                        + "d.unit_price, d.total_amount, d.gb_category_name, d.manufacturer, d.supplier, "
                        + "d.purchase_date, d.borrow_status, d.device_status, d.create_time "
                        + "FROM device d "
                        + "LEFT JOIN device_category c ON d.category_id = c.id "
                        + "LEFT JOIN laboratory l ON d.laboratory_id = l.id ORDER BY d.id",
                hdrs("id", "设备ID", "asset_no", "资产编号", "name", "名称", "model", "型号", "specs", "规格",
                        "category_name", "分类", "location", "存放地", "laboratory_name", "所属实验室",
                        "department", "使用单位", "custodian", "使用人", "total_qty", "总数量",
                        "available_qty", "可借数量", "unit_price", "单价", "total_amount", "金额",
                        "gb_category_name", "国标分类", "manufacturer", "厂家", "supplier", "供货商",
                        "purchase_date", "购置日期", "borrow_status", "借还状态", "device_status", "设备状态",
                        "create_time", "建档时间")));

        list.add(new Dataset("device_stats", "设备使用统计",
                "按设备汇总：借用次数、累计借用天数、折合人时数（1天=8小时）、最近一次借用时间",
                "SELECT d.id, d.asset_no, d.name, c.name AS category_name, d.custodian, "
                        + "COUNT(b.id) AS borrow_count, "
                        + "ROUND(COALESCE(SUM(GREATEST(TIMESTAMPDIFF(MINUTE, "
                        + "  COALESCE(b.pickup_time, b.start_time), COALESCE(b.real_return_time, b.end_time)), 0)), 0) / 1440, 1) AS borrow_days, "
                        + "ROUND(COALESCE(SUM(GREATEST(TIMESTAMPDIFF(MINUTE, "
                        + "  COALESCE(b.pickup_time, b.start_time), COALESCE(b.real_return_time, b.end_time)), 0)), 0) / 180, 0) AS person_hours, "
                        + "MAX(b.start_time) AS last_borrow_time "
                        + "FROM device d "
                        + "LEFT JOIN device_category c ON d.category_id = c.id "
                        + "LEFT JOIN borrow_record b ON b.device_id = d.id "
                        + "  AND b.status IN ('BORROWING','OVERDUE','RETURN_PENDING','RETURNED') "
                        + "GROUP BY d.id, d.asset_no, d.name, c.name, d.custodian ORDER BY borrow_count DESC, d.id",
                hdrs("id", "设备ID", "asset_no", "资产编号", "name", "名称", "category_name", "分类",
                        "custodian", "使用人", "borrow_count", "借用次数", "borrow_days", "累计借用天数",
                        "person_hours", "折合人时数", "last_borrow_time", "最近借用时间")));

        list.add(new Dataset("borrow_outcomes", "借用成果",
                "借用产生的成果记录：成果类型、标题、详情、记录人",
                "SELECT o.id, o.borrow_id, d.name AS device_name, u.real_name AS user_name, "
                        + "o.outcome_type, o.title, o.detail, o.file_urls, o.create_time "
                        + "FROM borrow_outcome o "
                        + "LEFT JOIN device d ON o.device_id = d.id "
                        + "LEFT JOIN sys_user u ON o.recorded_by = u.id ORDER BY o.id DESC",
                hdrs("id", "成果ID", "borrow_id", "借用单号", "device_name", "设备名称", "user_name", "记录人",
                        "outcome_type", "成果类型", "title", "标题", "detail", "详情",
                        "file_urls", "附件", "create_time", "记录时间")));

        list.add(new Dataset("overdue_records", "逾期记录",
                "当前所有逾期未归还的借用单：逾期天数、设备情况、催还次数",
                "SELECT b.id, d.name AS device_name, d.asset_no AS device_asset_no, d.custodian, "
                        + "u.real_name AS user_name, b.start_time, b.end_time, "
                        + "GREATEST(DATEDIFF(NOW(), b.end_time), 1) AS overdue_days, "
                        + "b.status, b.damage_report, b.create_time "
                        + "FROM borrow_record b "
                        + "LEFT JOIN device d ON b.device_id = d.id "
                        + "LEFT JOIN sys_user u ON b.user_id = u.id "
                        + "WHERE b.status = 'OVERDUE' OR (b.status = 'BORROWING' AND b.end_time < NOW()) "
                        + "ORDER BY b.end_time",
                hdrs("id", "单号", "device_name", "设备名称", "device_asset_no", "资产编号", "custodian", "设备使用人",
                        "user_name", "借用人", "start_time", "借用开始", "end_time", "应归还",
                        "overdue_days", "逾期天数", "status", "状态", "damage_report", "设备情况",
                        "create_time", "申请时间")));

        list.add(new Dataset("approval_logs", "审批流水",
                "每个审批节点的处理记录：审批人、结果、意见、时间",
                "SELECT a.id, a.borrow_id, a.step, u.real_name AS approver_name, u.user_type AS approver_user_type, "
                        + "a.result, a.comment, a.operate_time, "
                        + "d.name AS device_name, bu.real_name AS borrower_name "
                        + "FROM approval_log a "
                        + "LEFT JOIN sys_user u ON a.approver_id = u.id "
                        + "LEFT JOIN borrow_record b ON a.borrow_id = b.id "
                        + "LEFT JOIN device d ON b.device_id = d.id "
                        + "LEFT JOIN sys_user bu ON b.user_id = bu.id ORDER BY a.id DESC",
                hdrs("id", "记录ID", "borrow_id", "借用单号", "step", "审批级次", "device_name", "设备名称",
                        "borrower_name", "借用人", "approver_name", "审批人", "approver_user_type", "审批人角色",
                        "result", "结果", "comment", "审批意见", "operate_time", "操作时间")));

        list.add(new Dataset("purpose_stats", "借用目的统计",
                "按目的大类+子类聚合：借用次数、涉及设备数、借用人次",
                "SELECT b.purpose_category, b.purpose_subcategory, "
                        + "COUNT(*) AS borrow_count, COUNT(DISTINCT b.device_id) AS device_count, "
                        + "COUNT(DISTINCT b.user_id) AS user_count "
                        + "FROM borrow_record b "
                        + "GROUP BY b.purpose_category, b.purpose_subcategory "
                        + "ORDER BY borrow_count DESC",
                hdrs("purpose_category", "目的大类", "purpose_subcategory", "目的子类",
                        "borrow_count", "借用次数", "device_count", "涉及设备数", "user_count", "借用人次")));

        list.add(new Dataset("users", "用户名单",
                "全部用户：账号、姓名、角色、学院/部门、账号来源、状态、最近登录（不含密码）",
                "SELECT u.id, u.username, u.real_name, u.user_type, u.department, u.class_name, "
                        + "u.auth_source, u.status, u.email, u.phone, u.last_cas_login, u.create_time "
                        + "FROM sys_user u ORDER BY u.id",
                hdrs("id", "用户ID", "username", "账号", "real_name", "姓名", "user_type", "角色",
                        "department", "学院/部门", "class_name", "班级", "auth_source", "账号来源",
                        "status", "状态", "email", "邮箱", "phone", "手机号",
                        "last_cas_login", "最近登录", "create_time", "建档时间")));

        return list;
    }

    @GetMapping("/datasets")
    @ApiOperation("可导出的业务数据集列表")
    public R<List<Map<String, Object>>> datasets() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Dataset d : DATASETS) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", d.key());
            m.put("name", d.name());
            m.put("description", d.description());
            m.put("columns", d.headers().values());
            out.add(m);
        }
        return R.ok(out);
    }

    @GetMapping("/{key}")
    @ApiOperation("导出指定业务数据集（csv/xlsx）")
    public void export(@PathVariable String key,
                       @RequestParam(defaultValue = "csv") String format,
                       HttpServletResponse response) throws Exception {
        Dataset ds = DATASETS.stream().filter(d -> d.key().equals(key)).findFirst().orElse(null);
        if (ds == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(ds.sql());
        String stamp = java.time.LocalDate.now().toString();

        if ("xlsx".equalsIgnoreCase(format)) {
            byte[] xlsx = ExcelExportUtil.exportToXlsx(rows, ds.headers());
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=" + key + "_" + stamp + ".xlsx");
            response.setContentLength(xlsx.length);
            response.getOutputStream().write(xlsx);
            response.flushBuffer();
            log.info("业务数据导出: {} → xlsx, {} 行", key, rows.size());
            return;
        }

        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=" + key + "_" + stamp + ".csv");
        response.getOutputStream().write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}); // UTF-8 BOM，Excel 才不乱码
        OutputStreamWriter osw = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);
        osw.write(String.join(",", ds.headers().values()) + "\n");
        for (Map<String, Object> r : rows) {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (String col : ds.headers().keySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append(csvCell(r.get(col)));
            }
            osw.write(sb.append('\n').toString());
        }
        osw.flush();
        osw.close();
        log.info("业务数据导出: {} → csv, {} 行", key, rows.size());
    }

    /** CSV 单元格：转义引号并防公式注入（Excel 会把 = + - @ 开头的当公式执行） */
    private String csvCell(Object v) {
        if (v == null) return "";
        String s = String.valueOf(v);
        if (s.isEmpty()) return "";
        char c = s.charAt(0);
        if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') s = "'" + s;
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
