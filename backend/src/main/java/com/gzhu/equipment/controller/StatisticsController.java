package com.gzhu.equipment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gzhu.equipment.common.R;
import lombok.extern.slf4j.Slf4j;
import com.gzhu.equipment.entity.BorrowRecord;
import com.gzhu.equipment.entity.Device;
import com.gzhu.equipment.entity.SysUser;
import com.gzhu.equipment.mapper.BorrowRecordMapper;
import com.gzhu.equipment.mapper.DeviceCategoryMapper;
import com.gzhu.equipment.mapper.DeviceMapper;
import com.gzhu.equipment.mapper.SysUserMapper;
import com.gzhu.equipment.security.JwtUserPrincipal;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/statistics")
@RequiredArgsConstructor
@Api(tags = "数据统计")
public class StatisticsController {

    private final DeviceMapper deviceMapper;
    private final BorrowRecordMapper borrowMapper;
    private final DeviceCategoryMapper categoryMapper;
    private final SysUserMapper sysUserMapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /** 获取当前用户姓名，用于个人范围筛选。
     *  scope=personal → 筛选当前用户的持有设备（适用于所有角色：教师/实验室管理员/系统管理员）
     *  scope=global   → 不筛选，看全部
     *  scope=auto     → 兼容旧逻辑：教师默认个人，其他默认全局
     *  返回null表示不需要筛选 */
    private String getCustodianForScope(String scope) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof JwtUserPrincipal p)) return null;
        SysUser user = sysUserMapper.selectById(p.getUserId());
        if (user == null) return null;
        Integer userType = user.getUserType();
        // student — never filters
        if (userType == null || userType == 0) return null;
        // personal: explicit request
        if ("personal".equals(scope)) return user.getRealName();
        // global: explicit request
        if ("global".equals(scope)) return null;
        // auto: backward compatible — teacher→personal, lab_admin/system_admin→global
        if (userType == 1) return user.getRealName(); // teacher → personal
        return null; // lab_admin(2) / system_admin(3) → global
    }

    /** 构建设备查询条件（按scope决定是否限定当前用户持有设备） */
    private LambdaQueryWrapper<Device> deviceWrapper(String scope) {
        LambdaQueryWrapper<Device> w = new LambdaQueryWrapper<>();
        String custodian = getCustodianForScope(scope);
        if (custodian != null) w.eq(Device::getCustodian, custodian);
        return w;
    }

    @GetMapping("/overview")
    @ApiOperation("仪表盘概览（scope: auto|personal|global）")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public R<Map<String, Object>> overview(@RequestParam(defaultValue = "auto") String scope) {
        Map<String, Object> data = new LinkedHashMap<>();

        Long totalDevices = deviceMapper.selectCount(deviceWrapper(scope));
        Long availableCount = deviceMapper.selectCount(deviceWrapper(scope).eq(Device::getBorrowStatus, 1));
        Long deviceBorrowingCount = deviceMapper.selectCount(deviceWrapper(scope).eq(Device::getBorrowStatus, 2));
        Long unavailableCount = deviceMapper.selectCount(deviceWrapper(scope).eq(Device::getBorrowStatus, 3));
        Long overdueCount2 = deviceMapper.selectCount(deviceWrapper(scope).eq(Device::getBorrowStatus, 4));
        Long normalCount = deviceMapper.selectCount(deviceWrapper(scope).eq(Device::getDeviceStatus, 1));
        Long repairCount = deviceMapper.selectCount(deviceWrapper(scope).eq(Device::getDeviceStatus, 3));
        Long pendingRepair = deviceMapper.selectCount(deviceWrapper(scope).eq(Device::getDeviceStatus, 2));
        Long pendingScrap = deviceMapper.selectCount(deviceWrapper(scope).eq(Device::getDeviceStatus, 4));
        Long scrappedCount = deviceMapper.selectCount(deviceWrapper(scope).eq(Device::getDeviceStatus, 5));

        Map<String, Long> deviceStats = new LinkedHashMap<>();
        deviceStats.put("total", totalDevices);
        deviceStats.put("borrowAvailable", availableCount);
        deviceStats.put("borrowBorrowing", deviceBorrowingCount);
        deviceStats.put("borrowUnavailable", unavailableCount);
        deviceStats.put("borrowOverdue", overdueCount2);
        deviceStats.put("deviceNormal", normalCount);
        deviceStats.put("devicePendingRepair", pendingRepair);
        deviceStats.put("deviceRepairing", repairCount);
        deviceStats.put("devicePendingScrap", pendingScrap);
        deviceStats.put("deviceScrapped", scrappedCount);
        // 向后兼容旧前端
        deviceStats.put("available", availableCount);
        deviceStats.put("borrowing", deviceBorrowingCount);
        deviceStats.put("repair", repairCount);
        deviceStats.put("scrap", pendingScrap + scrappedCount);
        data.put("deviceStats", deviceStats);

        // 借用概览：借出中/逾期 使用设备维度(与借还状态卡片一致)；待审批/总借用使用记录维度
        String custodian = getCustodianForScope(scope);
        LambdaQueryWrapper<BorrowRecord> bwBase = new LambdaQueryWrapper<>();
        if (custodian != null) {
            bwBase.apply("device_id IN (SELECT id FROM device WHERE custodian = {0})", custodian);
        }
        Long pendingCount = borrowMapper.selectCount(bwBase.clone().eq(BorrowRecord::getStatus, "PENDING_APPROVAL"));
        Long totalBorrows = borrowMapper.selectCount(bwBase);

        Map<String, Long> borrowStats = new LinkedHashMap<>();
        borrowStats.put("borrowing", deviceBorrowingCount);
        borrowStats.put("overdue", overdueCount2);
        borrowStats.put("pendingApproval", pendingCount);
        borrowStats.put("total", totalBorrows);
        data.put("borrowStats", borrowStats);

        return R.ok(data);
    }

    /** 按scope限制借用查询条件（个人范围时筛选当前用户持有设备） */
    private void applyScopeFilter(QueryWrapper<BorrowRecord> w, String scope) {
        String custodian = getCustodianForScope(scope);
        if (custodian != null) {
            w.apply("device_id IN (SELECT id FROM device WHERE custodian = {0})", custodian);
        }
    }

    @GetMapping("/trend")
    @ApiOperation("本月借用趋势（按天，scope: auto|personal|global）")
    @PreAuthorize("hasAuthority('statistics:view')")
    public R<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "auto") String scope) {
        LocalDate start = LocalDate.now().withDayOfMonth(1);
        LocalDate end = LocalDate.now();

        var w = new QueryWrapper<BorrowRecord>()
                .select("DATE(create_time) as date", "COUNT(*) as count")
                .ge("create_time", start.atStartOfDay())
                .le("create_time", end.plusDays(1).atStartOfDay())
                .groupBy("DATE(create_time)")
                .orderByAsc("date");
        applyScopeFilter(w, scope);
        var rows = borrowMapper.selectMaps(w);

        return R.ok(rows);
    }

    @GetMapping("/top-devices")
    @ApiOperation("热门设备 TOP10（scope: auto|personal|global）")
    @PreAuthorize("hasAuthority('statistics:view')")
    public R<List<Map<String, Object>>> topDevices(@RequestParam(defaultValue = "auto") String scope) {
        try {
            var w = new QueryWrapper<BorrowRecord>()
                    .select("d.name as deviceName", "COUNT(*) as borrowCount")
                    .apply("LEFT JOIN device d ON borrow_record.device_id = d.id")
                    .groupBy("d.name")
                    .orderByDesc("borrowCount")
                    .last("LIMIT 10");
            applyScopeFilter(w, scope);
            var rows = borrowMapper.selectMaps(w);
            return R.ok(rows != null ? rows : java.util.Collections.emptyList());
        } catch (Exception e) {
            log.warn("热门设备查询失败: {}", e.getMessage());
            return R.ok(java.util.Collections.emptyList());
        }
    }

    @GetMapping("/top-users")
    @ApiOperation("高频借用用户 TOP10（scope: auto|personal|global）")
    @PreAuthorize("hasAuthority('statistics:view')")
    public R<List<Map<String, Object>>> topUsers(@RequestParam(defaultValue = "auto") String scope) {
        try {
            var w = new QueryWrapper<BorrowRecord>()
                    .select("u.real_name as userName", "COUNT(*) as borrowCount")
                    .apply("LEFT JOIN sys_user u ON borrow_record.user_id = u.id")
                    .groupBy("u.real_name")
                    .orderByDesc("borrowCount")
                    .last("LIMIT 10");
            applyScopeFilter(w, scope);
            var rows = borrowMapper.selectMaps(w);
            return R.ok(rows != null ? rows : java.util.Collections.emptyList());
        } catch (Exception e) {
            log.warn("热门用户查询失败: {}", e.getMessage());
            return R.ok(java.util.Collections.emptyList());
        }
    }

    @GetMapping("/utilization")
    @ApiOperation("设备利用率分析（按分类，scope: auto|personal|global）")
    @PreAuthorize("hasAuthority('statistics:view')")
    public R<List<Map<String, Object>>> utilization(@RequestParam(defaultValue = "auto") String scope) {
        var w = new QueryWrapper<BorrowRecord>()
                .select("dc.name as categoryName", "COUNT(*) as borrowCount")
                .apply("LEFT JOIN device d ON borrow_record.device_id = d.id")
                .apply("LEFT JOIN device_category dc ON d.category_id = dc.id")
                .groupBy("dc.name")
                .orderByDesc("borrowCount")
                .last("LIMIT 10");
        applyScopeFilter(w, scope);
        var rows = borrowMapper.selectMaps(w);
        return R.ok(rows);
    }

    @GetMapping("/export")
    @ApiOperation("导出统计报表（CSV/XLSX）")
    @PreAuthorize("hasAuthority('statistics:view')")
    public void exportCsv(@RequestParam(defaultValue = "csv") String format,
                           javax.servlet.http.HttpServletResponse response) throws Exception {

        if ("xlsx".equalsIgnoreCase(format)) {
            byte[] xlsx = buildComprehensiveXlsx();
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=statistics_export_" + System.currentTimeMillis() + ".xlsx");
            response.setContentLength(xlsx.length);
            response.getOutputStream().write(xlsx);
            response.flushBuffer();
            return;
        }

        // CSV — 完整报表
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=statistics_export_" + System.currentTimeMillis() + ".csv");
        response.getOutputStream().write(new byte[]{(byte)0xEF,(byte)0xBB,(byte)0xBF});
        try (OutputStreamWriter w = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8)) {
            LocalDate start = LocalDate.now().withDayOfMonth(1), end = LocalDate.now();

            // 1. 设备概览
            w.write("=== 设备概览 ===\n指标,数值\n");
            w.write("设备总数," + deviceMapper.selectCount(null) + "\n");
            w.write("可借用(借还状态),"+deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getBorrowStatus,1))+"\n");
            w.write("借用中(借还状态),"+deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getBorrowStatus,2))+"\n");
            w.write("不可借(借还状态),"+deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getBorrowStatus,3))+"\n");
            w.write("逾期(借还状态),"+deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getBorrowStatus,4))+"\n");
            w.write("正常(物理状态),"+deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getDeviceStatus,1))+"\n");
            w.write("待维修(物理状态),"+deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getDeviceStatus,2))+"\n");
            w.write("维修中(物理状态),"+deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getDeviceStatus,3))+"\n");
            w.write("待报废(物理状态),"+deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getDeviceStatus,4))+"\n");
            w.write("已报废(物理状态),"+deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getDeviceStatus,5))+"\n");
            w.write("借出中(记录),"+deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getBorrowStatus,2))+"\n");
            w.write("逾期未还(记录),"+deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getBorrowStatus,4))+"\n");
            w.write("待审批,"+borrowMapper.selectCount(new LambdaQueryWrapper<BorrowRecord>().eq(BorrowRecord::getStatus,"PENDING_APPROVAL"))+"\n");
            w.write("总借用记录,"+borrowMapper.selectCount(null)+"\n\n");

            // 2. 借用趋势
            w.write("=== 本月借用趋势 ===\n日期,次数\n");
            for (var r : borrowMapper.selectMaps(new QueryWrapper<BorrowRecord>()
                    .select("DATE(create_time) as date","COUNT(*) as count")
                    .ge("create_time",start.atStartOfDay()).le("create_time",end.plusDays(1).atStartOfDay())
                    .groupBy("DATE(create_time)").orderByAsc("date")))
                w.write(r.get("date")+","+r.get("count")+"\n");

            // 3. 热门设备 TOP10
            w.write("\n=== 热门设备 TOP10 ===\n排名,设备名称,借用次数\n");
            try {
                int rank=1;
                for (var r : jdbcTemplate.queryForList("SELECT d.name AS name,COUNT(*) AS cnt FROM borrow_record LEFT JOIN device d ON borrow_record.device_id=d.id GROUP BY d.name ORDER BY cnt DESC LIMIT 10"))
                    w.write((rank++)+","+r.get("name")+","+r.get("cnt")+"\n");
            } catch(Exception e){log.warn("热门设备查询失败: {}",e.getMessage());}

            // 4. 高频用户 TOP10
            w.write("\n=== 高频用户 TOP10 ===\n排名,用户,借用次数\n");
            try {
                int rank=1;
                for (var r : jdbcTemplate.queryForList("SELECT u.real_name AS name,COUNT(*) AS cnt FROM borrow_record LEFT JOIN sys_user u ON borrow_record.user_id=u.id GROUP BY u.real_name ORDER BY cnt DESC LIMIT 10"))
                    w.write((rank++)+","+r.get("name")+","+r.get("cnt")+"\n");
            } catch(Exception e){log.warn("高频用户查询失败: {}",e.getMessage());}

            // 5. 分类利用率 TOP10
            w.write("\n=== 设备分类利用率 TOP10 ===\n排名,设备分类,借用次数\n");
            try {
                int rank=1;
                for (var r : jdbcTemplate.queryForList("SELECT dc.name AS name,COUNT(*) AS cnt FROM borrow_record LEFT JOIN device d ON borrow_record.device_id=d.id LEFT JOIN device_category dc ON d.category_id=dc.id GROUP BY dc.name ORDER BY cnt DESC LIMIT 10"))
                    w.write((rank++)+","+r.get("name")+","+r.get("cnt")+"\n");
            } catch(Exception e){log.warn("分类利用率查询失败: {}",e.getMessage());}

            // 6. 目的分布 — 大类
            w.write("\n=== 借用目的分布（大类） ===\n目的大类,次数\n");
            for (var r : borrowMapper.selectMaps(new QueryWrapper<BorrowRecord>()
                    .select("COALESCE(purpose_category,'未分类') as name","COUNT(*) as cnt")
                    .groupBy("purpose_category").orderByDesc("cnt")))
                w.write(r.get("name")+","+r.get("cnt")+"\n");

            // 7. 目的分布 — 子分类
            w.write("\n=== 借用目的分布（子分类） ===\n目的子分类,次数\n");
            for (var r : borrowMapper.selectMaps(new QueryWrapper<BorrowRecord>()
                    .select("COALESCE(purpose_subcategory,'未指定') as name","COUNT(*) as cnt")
                    .groupBy("purpose_subcategory").orderByDesc("cnt")))
                w.write(r.get("name")+","+r.get("cnt")+"\n");

            // 8. 目的按设备分类分布
            w.write("\n=== 目的按设备分类分布 ===\n设备分类,次数\n");
            try {
                for (var r : jdbcTemplate.queryForList("SELECT COALESCE(dc.name,'未分类设备') AS name,COUNT(*) AS cnt FROM borrow_record LEFT JOIN device d ON borrow_record.device_id=d.id LEFT JOIN device_category dc ON d.category_id=dc.id GROUP BY dc.name ORDER BY cnt DESC"))
                    w.write(r.get("name")+","+r.get("cnt")+"\n");
            } catch(Exception e){log.warn("目的按设备分类查询失败: {}",e.getMessage());}

            // 9. 成果统计 — 总览
            w.write("\n=== 成果统计 ===\n");
            Long oc = borrowMapper.selectCount(new LambdaQueryWrapper<BorrowRecord>().isNotNull("outcome").ne("outcome",""));
            w.write("成果总数,"+(oc!=null?oc:0)+"\n");

            // 10. 成果类型分布（从 borrow_outcome 表）
            w.write("\n=== 成果类型分布 ===\n成果类型,次数\n");
            try {
                for (var r : jdbcTemplate.queryForList("SELECT COALESCE(outcome_type,'未分类') AS name,COUNT(*) AS cnt FROM borrow_outcome GROUP BY outcome_type ORDER BY cnt DESC"))
                    w.write(r.get("name")+","+r.get("cnt")+"\n");
            } catch(Exception e){log.warn("成果类型分布查询失败: {}",e.getMessage());}

            // 11. 成果设备 TOP10
            w.write("\n=== 成果设备 TOP10 ===\n排名,设备名称,成果数\n");
            try {
                int rank=1;
                for (var r : jdbcTemplate.queryForList("SELECT d.name AS name,COUNT(*) AS cnt FROM borrow_outcome o LEFT JOIN device d ON o.device_id=d.id GROUP BY d.name ORDER BY cnt DESC LIMIT 10"))
                    w.write((rank++)+","+r.get("name")+","+r.get("cnt")+"\n");
            } catch(Exception e){log.warn("成果设备TOP10查询失败: {}",e.getMessage());}

            // 12. 成果月度趋势
            w.write("\n=== 成果月度趋势 ===\n月份,成果数\n");
            try {
                for (var r : jdbcTemplate.queryForList("SELECT DATE_FORMAT(create_time,'%Y-%m') AS month,COUNT(*) AS cnt FROM borrow_outcome GROUP BY month ORDER BY month LIMIT 12"))
                    w.write(r.get("month")+","+r.get("cnt")+"\n");
            } catch(Exception e){log.warn("成果月度趋势查询失败: {}",e.getMessage());}
        }
    }

    private byte[] buildComprehensiveXlsx() {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            var cs = wb.createCellStyle(); var cf = wb.createFont(); cf.setBold(true); cs.setFont(cf);
            LocalDate start = LocalDate.now().withDayOfMonth(1), end = LocalDate.now();
            int idx = 0, ri;

            // Sheet 1: 设备概览
            org.apache.poi.ss.usermodel.Sheet s = wb.createSheet("设备概览");
            String[] h1 = {"指标","数值"};
            String[] labels = {"设备总数","可借用(借还状态)","借用中(借还状态)","不可借(借还状态)","逾期(借还状态)",
                "正常(物理状态)","待维修(物理状态)","维修中(物理状态)","待报废(物理状态)","已报废(物理状态)",
                "借出中","逾期未还","待审批","总借用记录"};
            long[] vals = {
                deviceMapper.selectCount(null),
                deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getBorrowStatus,1)),
                deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getBorrowStatus,2)),
                deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getBorrowStatus,3)),
                deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getBorrowStatus,4)),
                deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getDeviceStatus,1)),
                deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getDeviceStatus,2)),
                deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getDeviceStatus,3)),
                deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getDeviceStatus,4)),
                deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getDeviceStatus,5)),
                deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getBorrowStatus,2)),
                deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getBorrowStatus,4)),
                borrowMapper.selectCount(new LambdaQueryWrapper<BorrowRecord>().eq(BorrowRecord::getStatus,"PENDING_APPROVAL")),
                borrowMapper.selectCount(null)
            };
            ri = writeHeader(s, cs, h1);
            for (int i=0;i<labels.length;i++){org.apache.poi.ss.usermodel.Row r=s.createRow(ri++);r.createCell(0).setCellValue(labels[i]);r.createCell(1).setCellValue(vals[i]);}
            s.setColumnWidth(0,6000); s.setColumnWidth(1,4000);

            // Sheet 2: 借用趋势
            s = wb.createSheet("借用趋势");
            var trend = borrowMapper.selectMaps(new QueryWrapper<BorrowRecord>()
                    .select("DATE(create_time) as date","COUNT(*) as count")
                    .ge("create_time",start.atStartOfDay()).le("create_time",end.plusDays(1).atStartOfDay())
                    .groupBy("DATE(create_time)").orderByAsc("date"));
            ri = writeHeader(s, cs, "日期","次数");
            for (var row : trend){org.apache.poi.ss.usermodel.Row r=s.createRow(ri++);r.createCell(0).setCellValue(String.valueOf(row.get("date")));r.createCell(1).setCellValue(((Number)row.get("count")).doubleValue());}
            s.setColumnWidth(0,5000);

            // Sheet 3: 热门设备 TOP10
            s = wb.createSheet("热门设备TOP10");
            ri = writeHeader(s, cs, "排名","设备名称","借用次数");
            try {
                var rows = jdbcTemplate.queryForList("SELECT d.name AS name,COUNT(*) AS cnt FROM borrow_record LEFT JOIN device d ON borrow_record.device_id=d.id GROUP BY d.name ORDER BY cnt DESC LIMIT 10");
                int rank=1; for (var row : rows){org.apache.poi.ss.usermodel.Row r=s.createRow(ri++);r.createCell(0).setCellValue(rank++);r.createCell(1).setCellValue(String.valueOf(row.get("name")));r.createCell(2).setCellValue(((Number)row.get("cnt")).doubleValue());}
            } catch(Exception e){log.warn("热门设备: {}",e.getMessage());}
            s.setColumnWidth(0,3000); s.setColumnWidth(1,6000);

            // Sheet 4: 高频用户 TOP10
            s = wb.createSheet("高频用户TOP10");
            ri = writeHeader(s, cs, "排名","用户","借用次数");
            try {
                var rows = jdbcTemplate.queryForList("SELECT u.real_name AS name,COUNT(*) AS cnt FROM borrow_record LEFT JOIN sys_user u ON borrow_record.user_id=u.id GROUP BY u.real_name ORDER BY cnt DESC LIMIT 10");
                int rank=1; for (var row : rows){org.apache.poi.ss.usermodel.Row r=s.createRow(ri++);r.createCell(0).setCellValue(rank++);r.createCell(1).setCellValue(String.valueOf(row.get("name")));r.createCell(2).setCellValue(((Number)row.get("cnt")).doubleValue());}
            } catch(Exception e){log.warn("高频用户: {}",e.getMessage());}
            s.setColumnWidth(0,3000); s.setColumnWidth(1,5000);

            // Sheet 5: 分类利用率 TOP10
            s = wb.createSheet("分类利用率TOP10");
            ri = writeHeader(s, cs, "排名","设备分类","借用次数");
            try {
                var rows = jdbcTemplate.queryForList("SELECT dc.name AS name,COUNT(*) AS cnt FROM borrow_record LEFT JOIN device d ON borrow_record.device_id=d.id LEFT JOIN device_category dc ON d.category_id=dc.id GROUP BY dc.name ORDER BY cnt DESC LIMIT 10");
                int rank=1; for (var row : rows){org.apache.poi.ss.usermodel.Row r=s.createRow(ri++);r.createCell(0).setCellValue(rank++);r.createCell(1).setCellValue(String.valueOf(row.get("name")));r.createCell(2).setCellValue(((Number)row.get("cnt")).doubleValue());}
            } catch(Exception e){log.warn("分类利用率: {}",e.getMessage());}
            s.setColumnWidth(0,3000); s.setColumnWidth(1,5000);

            // Sheet 6: 目的分布（大类）
            s = wb.createSheet("目的分布-大类");
            ri = writeHeader(s, cs, "目的大类","次数");
            for (var row : borrowMapper.selectMaps(new QueryWrapper<BorrowRecord>().select("COALESCE(purpose_category,'未分类') as name","COUNT(*) as cnt").groupBy("purpose_category").orderByDesc("cnt"))){
                org.apache.poi.ss.usermodel.Row r=s.createRow(ri++);r.createCell(0).setCellValue(String.valueOf(row.get("name")));r.createCell(1).setCellValue(((Number)row.get("cnt")).doubleValue());}
            s.setColumnWidth(0,5000);

            // Sheet 7: 目的分布（子分类）
            s = wb.createSheet("目的分布-子分类");
            ri = writeHeader(s, cs, "目的子分类","次数");
            for (var row : borrowMapper.selectMaps(new QueryWrapper<BorrowRecord>().select("COALESCE(purpose_subcategory,'未指定') as name","COUNT(*) as cnt").groupBy("purpose_subcategory").orderByDesc("cnt"))){
                org.apache.poi.ss.usermodel.Row r=s.createRow(ri++);r.createCell(0).setCellValue(String.valueOf(row.get("name")));r.createCell(1).setCellValue(((Number)row.get("cnt")).doubleValue());}
            s.setColumnWidth(0,5000);

            // Sheet 8: 目的按设备分类分布
            s = wb.createSheet("目的-按设备分类");
            ri = writeHeader(s, cs, "设备分类","次数");
            try {
                for (var row : jdbcTemplate.queryForList("SELECT COALESCE(dc.name,'未分类设备') AS name,COUNT(*) AS cnt FROM borrow_record LEFT JOIN device d ON borrow_record.device_id=d.id LEFT JOIN device_category dc ON d.category_id=dc.id GROUP BY dc.name ORDER BY cnt DESC")){
                    org.apache.poi.ss.usermodel.Row r=s.createRow(ri++);r.createCell(0).setCellValue(String.valueOf(row.get("name")));r.createCell(1).setCellValue(((Number)row.get("cnt")).doubleValue());}
            } catch(Exception e){log.warn("目的设备分类: {}",e.getMessage());}
            s.setColumnWidth(0,5000);

            // Sheet 9: 成果类型分布
            s = wb.createSheet("成果类型分布");
            ri = writeHeader(s, cs, "成果类型","次数");
            try {
                for (var row : jdbcTemplate.queryForList("SELECT COALESCE(outcome_type,'未分类') AS name,COUNT(*) AS cnt FROM borrow_outcome GROUP BY outcome_type ORDER BY cnt DESC")){
                    org.apache.poi.ss.usermodel.Row r=s.createRow(ri++);r.createCell(0).setCellValue(String.valueOf(row.get("name")));r.createCell(1).setCellValue(((Number)row.get("cnt")).doubleValue());}
            } catch(Exception e){log.warn("成果类型: {}",e.getMessage());}
            s.setColumnWidth(0,5000);

            // Sheet 10: 成果设备 TOP10
            s = wb.createSheet("成果设备TOP10");
            ri = writeHeader(s, cs, "排名","设备名称","成果数");
            try {
                var rows = jdbcTemplate.queryForList("SELECT d.name AS name,COUNT(*) AS cnt FROM borrow_outcome o LEFT JOIN device d ON o.device_id=d.id GROUP BY d.name ORDER BY cnt DESC LIMIT 10");
                int rank=1; for (var row : rows){org.apache.poi.ss.usermodel.Row r=s.createRow(ri++);r.createCell(0).setCellValue(rank++);r.createCell(1).setCellValue(String.valueOf(row.get("name")));r.createCell(2).setCellValue(((Number)row.get("cnt")).doubleValue());}
            } catch(Exception e){log.warn("成果设备: {}",e.getMessage());}
            s.setColumnWidth(0,3000); s.setColumnWidth(1,6000);

            // Sheet 11: 成果月度趋势
            s = wb.createSheet("成果月度趋势");
            ri = writeHeader(s, cs, "月份","成果数");
            try {
                for (var row : jdbcTemplate.queryForList("SELECT DATE_FORMAT(create_time,'%Y-%m') AS month,COUNT(*) AS cnt FROM borrow_outcome GROUP BY month ORDER BY month LIMIT 12")){
                    org.apache.poi.ss.usermodel.Row r=s.createRow(ri++);r.createCell(0).setCellValue(String.valueOf(row.get("month")));r.createCell(1).setCellValue(((Number)row.get("cnt")).doubleValue());}
            } catch(Exception e){log.warn("成果月度: {}",e.getMessage());}
            s.setColumnWidth(0,5000);

            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            wb.write(bos); return bos.toByteArray();
        } catch (Exception e) { throw new RuntimeException("XLSX生成失败: "+e.getMessage(), e); }
    }

    private int writeHeader(org.apache.poi.ss.usermodel.Sheet s, org.apache.poi.ss.usermodel.CellStyle cs, String... headers) {
        org.apache.poi.ss.usermodel.Row r = s.createRow(0);
        for (int i = 0; i < headers.length; i++) { var c = r.createCell(i); c.setCellValue(headers[i]); c.setCellStyle(cs); }
        return 1;
    }

    // ==================== V6 目的与成果统计（增强版） ====================

    @GetMapping("/purposes")
    @ApiOperation("借用目的分布统计（按大类，scope: auto|personal|global）")
    @PreAuthorize("hasAuthority('statistics:view')")
    public R<List<Map<String, Object>>> purposeStats(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "auto") String scope) {
        var w = new QueryWrapper<BorrowRecord>()
                .select("COALESCE(NULLIF(purpose_category,''),'未分类') as name", "COUNT(*) as value")
                .isNotNull("purpose_category").ne("purpose_category", "")
                .groupBy("purpose_category").orderByDesc("value");
        if (startDate != null) w.ge("borrow_record.create_time", startDate + " 00:00:00");
        if (endDate != null) w.le("borrow_record.create_time", endDate + " 23:59:59");
        if (categoryId != null) w.apply("device_id IN (SELECT id FROM device WHERE category_id = {0})", categoryId);
        applyScopeFilter(w, scope);
        var rows = borrowMapper.selectMaps(w);
        // 补充未分类统计
        var uncatW = new QueryWrapper<BorrowRecord>()
                .select("'未分类' as name", "COUNT(*) as value")
                .and(qw -> qw.isNull("purpose_category").or().eq("purpose_category", ""));
        if (startDate != null) uncatW.ge("create_time", startDate + " 00:00:00");
        if (endDate != null) uncatW.le("create_time", endDate + " 23:59:59");
        if (categoryId != null) uncatW.apply("device_id IN (SELECT id FROM device WHERE category_id = {0})", categoryId);
        applyScopeFilter(uncatW, scope);
        var uncat = borrowMapper.selectMaps(uncatW);
        if (uncat != null && !uncat.isEmpty() && uncat.get(0) != null) {
            Long v = (Long) uncat.get(0).get("value");
            if (v != null && v > 0) {
                Map<String, Object> m = new java.util.LinkedHashMap<>(); m.put("name", "未分类"); m.put("value", v); rows.add(m);
            }
        }
        return R.ok(rows);
    }

    @GetMapping("/purposes/detail")
    @ApiOperation("目的分类详细统计（大类+子分类+设备分类，scope: auto|personal|global）")
    @PreAuthorize("hasAuthority('statistics:view')")
    public R<Map<String, Object>> purposeDetail(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "auto") String scope) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();

        // 子分类统计
        var subW = new QueryWrapper<BorrowRecord>()
                .select("COALESCE(NULLIF(purpose_subcategory,''),'未指定') as name", "COUNT(*) as value")
                .groupBy("purpose_subcategory").orderByDesc("value");
        if (startDate != null) subW.ge("borrow_record.create_time", startDate + " 00:00:00");
        if (endDate != null) subW.le("borrow_record.create_time", endDate + " 23:59:59");
        if (categoryId != null) subW.apply("device_id IN (SELECT id FROM device WHERE category_id = {0})", categoryId);
        applyScopeFilter(subW, scope);
        result.put("subcategories", borrowMapper.selectMaps(subW));

        // 按设备分类的目的分布
        var catW = new QueryWrapper<BorrowRecord>()
                .select("COALESCE(dc.name,'未分类设备') as name", "COUNT(*) as value")
                .apply("LEFT JOIN device d ON borrow_record.device_id = d.id")
                .apply("LEFT JOIN device_category dc ON d.category_id = dc.id")
                .groupBy("dc.name").orderByDesc("value");
        if (startDate != null) catW.ge("borrow_record.create_time", startDate + " 00:00:00");
        if (endDate != null) catW.le("borrow_record.create_time", endDate + " 23:59:59");
        if (categoryId != null) catW.apply("d.category_id = {0}", categoryId);
        applyScopeFilter(catW, scope);
        result.put("byDeviceCategory", borrowMapper.selectMaps(catW));

        return R.ok(result);
    }

    @GetMapping("/outcomes/stats")
    @ApiOperation("成果产出统计（概述+分布+趋势，scope: auto|personal|global）")
    @PreAuthorize("hasAuthority('statistics:view')")
    public R<Map<String, Object>> outcomeStats(
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "auto") String scope) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();

        // 个人范围子查询条件
        String custodian = getCustodianForScope(scope);
        String scopeSql = (custodian != null) ? " AND o.device_id IN (SELECT id FROM device WHERE custodian = '" + custodian.replace("'","''") + "')" : "";

        // 成果总数（从 borrow_outcome 表统计，数据更可靠）
        Long outcomeTotal;
        try {
            var sb = new StringBuilder("SELECT COUNT(*) FROM borrow_outcome o WHERE 1=1");
            sb.append(scopeSql);
            if (deviceId != null) sb.append(" AND o.device_id = ").append(deviceId);
            if (startDate != null) sb.append(" AND o.create_time >= '").append(startDate).append(" 00:00:00'");
            if (endDate != null) sb.append(" AND o.create_time <= '").append(endDate).append(" 23:59:59'");
            outcomeTotal = jdbcTemplate.queryForObject(sb.toString(), Long.class);
        } catch (Exception e) {
            var fw = new LambdaQueryWrapper<BorrowRecord>().isNotNull(BorrowRecord::getOutcome).ne(BorrowRecord::getOutcome, "");
            if (deviceId != null) fw.eq(BorrowRecord::getDeviceId, deviceId);
            if (custodian != null) fw.apply("device_id IN (SELECT id FROM device WHERE custodian = {0})", custodian);
            outcomeTotal = borrowMapper.selectCount(fw);
        }
        result.put("outcomeTotal", outcomeTotal != null ? outcomeTotal : 0);

        // 成果类型分布（从 borrow_outcome 表）
        try {
            var typeW = new QueryWrapper<BorrowRecord>()
                    .select("COALESCE(o.outcome_type,'未分类') as name", "COUNT(*) as value")
                    .apply("RIGHT JOIN borrow_outcome o ON borrow_record.id = o.borrow_id")
                    .groupBy("o.outcome_type").orderByDesc("value");
            if (custodian != null) typeW.apply("o.device_id IN (SELECT id FROM device WHERE custodian = {0})", custodian);
            if (deviceId != null) typeW.eq("o.device_id", deviceId);
            if (startDate != null) typeW.ge("o.create_time", startDate + " 00:00:00");
            if (endDate != null) typeW.le("o.create_time", endDate + " 23:59:59");
            result.put("distribution", borrowMapper.selectMaps(typeW));
        } catch (Exception e) {
            log.warn("成果类型分布查询失败: {}", e.getMessage());
            result.put("distribution", java.util.Collections.emptyList());
        }

        // 按设备统计 TOP10（从 borrow_outcome 表）
        try {
            var sb = new StringBuilder("SELECT d.name AS name, COUNT(*) AS value FROM borrow_outcome o LEFT JOIN device d ON o.device_id = d.id WHERE 1=1");
            sb.append(scopeSql);
            if (deviceId != null) sb.append(" AND o.device_id = ").append(deviceId);
            if (startDate != null) sb.append(" AND o.create_time >= '").append(startDate).append(" 00:00:00'");
            if (endDate != null) sb.append(" AND o.create_time <= '").append(endDate).append(" 23:59:59'");
            sb.append(" GROUP BY d.name ORDER BY value DESC LIMIT 10");
            result.put("deviceTop10", jdbcTemplate.queryForList(sb.toString()));
        } catch (Exception e) {
            log.warn("成果设备TOP10查询失败: {}", e.getMessage());
            result.put("deviceTop10", java.util.Collections.emptyList());
        }

        // 按月趋势（从 borrow_outcome 表）
        try {
            var sb = new StringBuilder("SELECT DATE_FORMAT(o.create_time,'%Y-%m') AS name, COUNT(*) AS value FROM borrow_outcome o WHERE 1=1");
            sb.append(scopeSql);
            if (deviceId != null) sb.append(" AND o.device_id = ").append(deviceId);
            if (startDate != null) sb.append(" AND o.create_time >= '").append(startDate).append(" 00:00:00'");
            if (endDate != null) sb.append(" AND o.create_time <= '").append(endDate).append(" 23:59:59'");
            sb.append(" GROUP BY DATE_FORMAT(o.create_time,'%Y-%m') ORDER BY name LIMIT 12");
            result.put("monthTrend", jdbcTemplate.queryForList(sb.toString()));
        } catch (Exception e) {
            log.warn("成果月度趋势查询失败: {}", e.getMessage());
            result.put("monthTrend", java.util.Collections.emptyList());
        }

        return R.ok(result);
    }

    @GetMapping("/device-outcomes")
    @ApiOperation("按设备查看成果列表")
    @PreAuthorize("hasAuthority('statistics:view')")
    public R<List<Map<String, Object>>> deviceOutcomes(@RequestParam Long deviceId) {
        var rows = borrowMapper.selectMaps(new QueryWrapper<BorrowRecord>()
                .select("id", "purpose", "purpose_category", "outcome",
                        "outcome_recorded_time", "user_id")
                .eq("device_id", deviceId)
                .isNotNull("outcome").ne("outcome", "")
                .orderByDesc("outcome_recorded_time"));
        return R.ok(rows != null ? rows : java.util.Collections.emptyList());
    }
}
