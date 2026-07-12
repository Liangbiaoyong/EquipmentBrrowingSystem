package com.gzhu.equipment.controller;

import com.gzhu.equipment.common.R;
import com.gzhu.equipment.entity.CategoryDescription;
import com.gzhu.equipment.service.CategoryDescriptionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * V5: 分类描述元数据控制器
 *
 * GET /category-descriptions?type=PURPOSE     → 目的分类描述列表
 * GET /category-descriptions?type=OUTCOME     → 成果类型描述列表
 * GET /category-descriptions/grouped          → 按类型分组的所有描述
 * GET /category-descriptions/lookup?type=PURPOSE&name=教学与培养 → 查找单个描述
 */
@RestController
@RequestMapping("/category-descriptions")
@RequiredArgsConstructor
@Api(tags = "分类描述管理")
public class CategoryDescriptionController {

    private final CategoryDescriptionService descriptionService;

    @GetMapping
    @ApiOperation("按类型获取分类描述列表")
    @PreAuthorize("hasAnyAuthority('borrow:create','borrow:my','laboratory:view','statistics:view')")
    public R<List<CategoryDescription>> listByType(@RequestParam String type) {
        return R.ok(descriptionService.listByType(type));
    }

    @GetMapping("/grouped")
    @ApiOperation("获取所有分类描述（按类型分组）")
    @PreAuthorize("hasAnyAuthority('borrow:create','borrow:my','laboratory:view','statistics:view')")
    public R<Map<String, List<CategoryDescription>>> listGrouped() {
        return R.ok(descriptionService.listAllGrouped());
    }

    @GetMapping("/lookup")
    @ApiOperation("按类型+名称查找分类描述")
    @PreAuthorize("hasAnyAuthority('borrow:create','borrow:my','laboratory:view','statistics:view')")
    public R<CategoryDescription> lookup(
            @RequestParam String type,
            @RequestParam String name) {
        CategoryDescription cd = descriptionService.getByTypeAndName(type, name);
        return cd != null ? R.ok(cd) : R.fail(404, "未找到分类描述");
    }
}
