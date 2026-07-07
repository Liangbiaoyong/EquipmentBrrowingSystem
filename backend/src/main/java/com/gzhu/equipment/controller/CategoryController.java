package com.gzhu.equipment.controller;

import com.gzhu.equipment.common.R;
import com.gzhu.equipment.entity.CategoryMapping;
import com.gzhu.equipment.entity.DeviceCategory;
import com.gzhu.equipment.service.CategoryService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
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
    @ApiOperation("获取所有映射规则")
    public R<List<CategoryMapping>> listMappings(
            @RequestParam(required = false) Long categoryId) {
        if (categoryId != null) {
            return R.ok(categoryService.listMappingsByCategory(categoryId));
        }
        return R.ok(categoryService.listMappings());
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
