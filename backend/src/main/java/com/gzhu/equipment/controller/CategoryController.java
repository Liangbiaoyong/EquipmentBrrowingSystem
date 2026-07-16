package com.gzhu.equipment.controller;

import com.gzhu.equipment.common.R;
import com.gzhu.equipment.entity.CategoryMapping;
import com.gzhu.equipment.entity.DeviceCategory;
import com.gzhu.equipment.service.CategoryService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 设备分类管理控制器
 *
 * GET    /categories              → 获取所有业务分类
 * GET    /categories/mappings     → 获取所有映射规则
 * POST   /categories/mappings     → 新增映射规则
 * PUT    /categories/mappings/{id}→ 更新映射规则
 * DELETE /categories/mappings/{id}→ 删除映射规则
 * PUT    /categories/mappings/{id}/toggle → 启用/禁用
 * GET    /categories/classify?gbName=xxx → 测试自动分类
 */
@Slf4j
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
@Api(tags = "设备分类管理")
public class CategoryController {

    private final CategoryService categoryService;

    // ==================== 业务分类 ====================

    @GetMapping
    @ApiOperation("获取所有业务分类")
    public R<List<DeviceCategory>> listCategories() {
        return R.ok(categoryService.listEnabled());
    }

    @GetMapping("/top-level")
    @ApiOperation("获取一级分类列表")
    public R<List<DeviceCategory>> listTopLevel() {
        return R.ok(categoryService.listTopLevel());
    }

    // ==================== 业务分类CRUD ====================

    @PostMapping
    @ApiOperation("新增业务分类")
    @PreAuthorize("hasAnyRole('LAB_ADMIN', 'SYSTEM_ADMIN')")
    public R<DeviceCategory> addCategory(@Valid @RequestBody DeviceCategory category) {
        categoryService.save(category);
        log.info("新增业务分类: id={} name={}", category.getId(), category.getName());
        return R.ok(category);
    }

    @PutMapping("/{id}")
    @ApiOperation("更新业务分类（名称/编码/排序/状态等）")
    @PreAuthorize("hasAnyRole('LAB_ADMIN', 'SYSTEM_ADMIN')")
    public R<DeviceCategory> updateCategory(@PathVariable Long id, @RequestBody DeviceCategory category) {
        category.setId(id);
        categoryService.updateById(category);
        log.info("更新业务分类: id={}", id);
        return R.ok(category);
    }

    @PutMapping("/{id}/toggle")
    @ApiOperation("启用/禁用业务分类")
    @PreAuthorize("hasAnyRole('LAB_ADMIN', 'SYSTEM_ADMIN')")
    public R<Void> toggleCategory(@PathVariable Long id) {
        DeviceCategory cat = categoryService.getById(id);
        if (cat == null) return R.fail(404, "分类不存在");
        cat.setStatus(cat.getStatus() == 1 ? 0 : 1);
        categoryService.updateById(cat);
        log.info("切换业务分类状态: id={} status={}", id, cat.getStatus());
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @ApiOperation("删除业务分类（仅系统管理员）")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public R<Void> deleteCategory(@PathVariable Long id) {
        categoryService.removeById(id);
        log.warn("删除业务分类: id={}", id);
        return R.ok();
    }

    // ==================== 自动分类测试 ====================

    @GetMapping("/classify")
    @ApiOperation("测试自动分类（根据国标名查询匹配的业务分类ID）")
    public R<String> classify(@RequestParam String gbName) {
        Long categoryId = categoryService.classifyByGbName(gbName);
        if (categoryId != null) {
            DeviceCategory category = categoryService.getById(categoryId);
            return R.ok("匹配成功: " + (category != null ? category.getName() : "ID=" + categoryId));
        }
        return R.ok("未匹配，将归入「其他设备」");
    }

    // ==================== 映射规则管理 ====================

    @GetMapping("/mappings")
    @ApiOperation("获取所有映射规则（支持按分类/国标名/关键词搜索）")
    public R<List<CategoryMapping>> listMappings(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword) {
        return R.ok(categoryService.listMappingsFiltered(categoryId, keyword));
    }

    @PostMapping("/mappings")
    @ApiOperation("新增映射规则")
    @PreAuthorize("hasAnyRole('LAB_ADMIN', 'SYSTEM_ADMIN')")
    public R<CategoryMapping> addMapping(@Valid @RequestBody CategoryMapping mapping) {
        return R.ok(categoryService.addMapping(mapping));
    }

    @PutMapping("/mappings/{id}")
    @ApiOperation("更新映射规则")
    @PreAuthorize("hasAnyRole('LAB_ADMIN', 'SYSTEM_ADMIN')")
    public R<CategoryMapping> updateMapping(@PathVariable Long id,
                                            @RequestBody CategoryMapping mapping) {
        mapping.setId(id);
        return R.ok(categoryService.updateMapping(mapping));
    }

    @DeleteMapping("/mappings/{id}")
    @ApiOperation("删除映射规则")
    @PreAuthorize("hasAnyRole('LAB_ADMIN', 'SYSTEM_ADMIN')")
    public R<Void> deleteMapping(@PathVariable Long id) {
        categoryService.deleteMapping(id);
        return R.ok();
    }

    @PutMapping("/mappings/{id}/toggle")
    @ApiOperation("启用/禁用映射规则")
    @PreAuthorize("hasAnyRole('LAB_ADMIN', 'SYSTEM_ADMIN')")
    public R<Void> toggleMapping(@PathVariable Long id) {
        categoryService.toggleMapping(id);
        return R.ok();
    }
}
