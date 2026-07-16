package com.gzhu.equipment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gzhu.equipment.common.R;
import com.gzhu.equipment.entity.Device;
import com.gzhu.equipment.entity.ScrapRule;
import com.gzhu.equipment.mapper.DeviceMapper;
import com.gzhu.equipment.mapper.ScrapRuleMapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/scrap")
@RequiredArgsConstructor
@Api(tags = "报废管理")
public class ScrapController {

    private final DeviceMapper deviceMapper;
    private final ScrapRuleMapper ruleMapper;
    private final JdbcTemplate jdbcTemplate;

    // ==================== 设备列表（含报废资格判断） ====================

    @GetMapping("/devices")
    @ApiOperation("可报废评估设备列表")
    @PreAuthorize("hasAnyAuthority('repair:manage','laboratory:manage','device:manage')")
    public R<IPage<Map<String,Object>>> listDevices(
            @RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size,
            @RequestParam(required=false)String keyword,@RequestParam(required=false)Long categoryId,
            @RequestParam(required=false)String sortBy,@RequestParam(required=false,defaultValue="desc")String order) {

        // 查询所有启用的报废规则
        List<ScrapRule> rules = ruleMapper.selectList(
                new LambdaQueryWrapper<ScrapRule>().eq(ScrapRule::getStatus,1).orderByAsc(ScrapRule::getPriority));

        LambdaQueryWrapper<Device> w = new LambdaQueryWrapper<>();
        w.ne(Device::getDeviceStatus, 5); // 排除已报废
        w.isNotNull(Device::getPurchaseDate);
        if (keyword != null && !keyword.trim().isEmpty())
            w.and(wp -> wp.like(Device::getName,keyword).or().like(Device::getAssetNo,keyword).or().like(Device::getGbCategoryName,keyword));
        if (categoryId != null) w.eq(Device::getCategoryId, categoryId);

        // 排序（id/purchaseDate/yearsUsed由SQL排序；minYears由内存排序后二次处理）
        boolean asc = "asc".equalsIgnoreCase(order);
        if ("id".equals(sortBy)) w.orderBy(true, asc, Device::getId);
        else if ("purchaseDate".equals(sortBy)) w.orderBy(true, asc, Device::getPurchaseDate);
        else if ("yearsUsed".equals(sortBy)) w.orderBy(true, asc, Device::getPurchaseDate);
        else if ("minYears".equals(sortBy)) {} // 后续内存排序
        else w.orderBy(true, asc, Device::getId);

        IPage<Device> devicePage = deviceMapper.selectPage(new Page<>(page, size), w);
        LocalDate today = LocalDate.now();

        List<Map<String,Object>> enriched = new ArrayList<>();
        for (Device d : devicePage.getRecords()) {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("id",d.getId()); row.put("assetNo",d.getAssetNo()); row.put("name",d.getName());
            row.put("model",d.getModel()); row.put("gbCategoryName",d.getGbCategoryName());
            row.put("location",d.getLocation()); row.put("custodian",d.getCustodian());
            row.put("deviceStatus",d.getDeviceStatus()); row.put("unitPrice",d.getUnitPrice());

            // 使用年限
            long yearsUsed = d.getPurchaseDate() != null
                    ? ChronoUnit.DAYS.between(d.getPurchaseDate(), today) / 365 : 0;
            row.put("purchaseDate", d.getPurchaseDate() != null ? d.getPurchaseDate().toString() : null);
            row.put("yearsUsed", yearsUsed);

            // 匹配报废规则
            int minYears = -1; String ruleMatch = "未匹配";
            for (ScrapRule rule : rules) {
                if (d.getGbCategoryName() != null && d.getGbCategoryName().contains(rule.getGbKeyword())) {
                    minYears = rule.getMinYears(); ruleMatch = rule.getGbKeyword(); break;
                }
            }
            // 全域兜底匹配
            if (minYears < 0 && d.getGbCategoryName() != null) {
                for (ScrapRule rule : rules) {
                    if (d.getGbCategoryName().contains(rule.getGbKeyword().substring(0, Math.min(2, rule.getGbKeyword().length())))) {
                        minYears = rule.getMinYears(); ruleMatch = rule.getGbKeyword(); break;
                    }
                }
            }
            if (minYears < 0) { minYears = 6; ruleMatch = "默认"; } // 无匹配默认6年

            row.put("minYears", minYears);
            row.put("ruleMatch", ruleMatch);
            row.put("scrapEligible", yearsUsed >= minYears);
            row.put("remainingYears", Math.max(0, minYears - yearsUsed));

            enriched.add(row);
        }

        // 内存排序
        if ("minYears".equals(sortBy)) {
            enriched.sort((a,b) -> {
                int va = (int)a.getOrDefault("minYears",0), vb = (int)b.getOrDefault("minYears",0);
                return "asc".equals(order) ? va - vb : vb - va;
            });
        }
        if ("yearsUsed".equals(sortBy)) {
            enriched.sort((a,b) -> {
                long va = (long)a.getOrDefault("yearsUsed",0L), vb = (long)b.getOrDefault("yearsUsed",0L);
                return "asc".equals(order) ? Long.compare(va,vb) : Long.compare(vb,va);
            });
        }

        Map<String,Object> result = new LinkedHashMap<>();
        result.put("records", enriched); result.put("total", devicePage.getTotal());
        result.put("page", page); result.put("size", size);
        return R.ok(new Page<Map<String,Object>>(page,size,devicePage.getTotal()){{
            setRecords(enriched);
        }});
    }

    // ==================== 执行报废 ====================

    @PutMapping("/{deviceId}/confirm")
    @ApiOperation("确认报废设备")
    @PreAuthorize("hasAnyAuthority('repair:manage','laboratory:manage','device:manage')")
    public R<String> confirmScrap(@PathVariable Long deviceId, @RequestParam(required=false,defaultValue="")String remark) {
        Device d = deviceMapper.selectById(deviceId);
        if (d == null) return R.fail(404,"设备不存在");
        d.setBorrowStatus(3); d.setDeviceStatus(5);
        deviceMapper.updateById(d);

        // 创建维修记录留痕
        jdbcTemplate.update("INSERT INTO repair_record (device_id,fault_description,status,repair_comment,fixed_time) VALUES (?,?,?,?,NOW())",
                deviceId, "报废: " + (remark.isEmpty() ? "管理员确认报废" : remark), "FIXED", remark);

        log.warn("设备已报废: deviceId={}", deviceId);
        return R.ok("设备已报废");
    }

    // ==================== 报废规则 CRUD ====================

    @GetMapping("/rules")
    @ApiOperation("获取所有报废规则，按优先级升序")
    @PreAuthorize("hasAnyAuthority('repair:manage','laboratory:manage','device:manage')")
    public R<List<ScrapRule>> listRules() {
        return R.ok(ruleMapper.selectList(
                new LambdaQueryWrapper<ScrapRule>().orderByAsc(ScrapRule::getPriority)));
    }

    @PostMapping("/rules")
    @ApiOperation("新增报废规则（相同gbKeyword自动合并为更低minYears）")
    @PreAuthorize("hasAnyAuthority('admin:user','laboratory:manage','device:manage','repair:manage')")
    public R<ScrapRule> addRule(@RequestBody ScrapRule rule) {
        // 检查相同gbKeyword的已有规则，取更小的minYears合并
        List<ScrapRule> existing = ruleMapper.selectList(
                new LambdaQueryWrapper<ScrapRule>()
                        .eq(ScrapRule::getGbKeyword, rule.getGbKeyword())
                        .last("LIMIT 1"));
        if (!existing.isEmpty()) {
            ScrapRule old = existing.get(0);
            if (old.getMinYears() > rule.getMinYears()) {
                old.setMinYears(rule.getMinYears());
                ruleMapper.updateById(old);
                log.info("更新规则(更严格年限): gbKeyword={} minYears={}→{}", rule.getGbKeyword(), old.getMinYears(), rule.getMinYears());
            }
            return R.ok(old);
        }
        ruleMapper.insert(rule);
        log.info("新增报废规则: id={} keyword={} minYears={}", rule.getId(), rule.getGbKeyword(), rule.getMinYears());
        return R.ok(rule);
    }

    @PostMapping("/rules/deduplicate")
    @ApiOperation("去重：合并相同gbKeyword的规则，保留最低minYears")
    @PreAuthorize("hasAnyAuthority('admin:user','laboratory:manage','device:manage','repair:manage')")
    public R<Map<String,Object>> deduplicateRules() {
        // 1. 查出所有规则
        List<ScrapRule> all = ruleMapper.selectList(
                new LambdaQueryWrapper<ScrapRule>().orderByAsc(ScrapRule::getPriority));
        // 2. 按gbKeyword分组，保留minYears最小的那条，其余删除
        Map<String, ScrapRule> best = new LinkedHashMap<>();
        List<Long> toDelete = new ArrayList<>();
        for (ScrapRule r : all) {
            if (r.getGbKeyword() == null) continue;
            ScrapRule existing = best.get(r.getGbKeyword());
            if (existing == null) {
                best.put(r.getGbKeyword(), r);
            } else {
                toDelete.add(r.getId());
                // 如果新规则的年限更低，更新保留的规则
                if (r.getMinYears() < existing.getMinYears()) {
                    existing.setMinYears(r.getMinYears());
                    ruleMapper.updateById(existing);
                }
            }
        }
        // 3. 删除多余的规则
        if (!toDelete.isEmpty()) {
            ruleMapper.deleteBatchIds(toDelete);
        }
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("beforeCount", all.size());
        result.put("afterCount", best.size());
        result.put("deletedCount", toDelete.size());
        log.info("报废规则去重完成: {} → {} (删除{})", all.size(), best.size(), toDelete.size());
        return R.ok(result);
    }

    @PutMapping("/rules/{id}")
    @ApiOperation("更新报废规则")
    @PreAuthorize("hasAnyAuthority('admin:user','laboratory:manage','device:manage','repair:manage')")
    public R<ScrapRule> updateRule(@PathVariable Long id, @RequestBody ScrapRule rule) {
        rule.setId(id); ruleMapper.updateById(rule); return R.ok(rule);
    }

    @DeleteMapping("/rules/{id}")
    @ApiOperation("删除报废规则")
    @PreAuthorize("hasAnyAuthority('admin:user','laboratory:manage','device:manage','repair:manage')")
    public R<String> deleteRule(@PathVariable Long id) {
        ruleMapper.deleteById(id); return R.ok("已删除");
    }

    @PutMapping("/rules/{id}/toggle")
    @ApiOperation("启用/禁用报废规则")
    @PreAuthorize("hasAnyAuthority('admin:user','laboratory:manage','device:manage','repair:manage')")
    public R<String> toggleRule(@PathVariable Long id) {
        ScrapRule r = ruleMapper.selectById(id);
        if (r != null) { r.setStatus(r.getStatus()==1?0:1); ruleMapper.updateById(r); }
        return R.ok("已切换");
    }
}
