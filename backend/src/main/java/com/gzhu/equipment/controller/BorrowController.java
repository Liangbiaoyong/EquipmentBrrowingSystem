package com.gzhu.equipment.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gzhu.equipment.common.R;
import com.gzhu.equipment.dto.ApprovalRequestDTO;
import com.gzhu.equipment.dto.BorrowRequestDTO;
import com.gzhu.equipment.entity.BorrowRecord;
import com.gzhu.equipment.security.JwtUserPrincipal;
import com.gzhu.equipment.entity.Attachment;
import com.gzhu.equipment.mapper.AttachmentMapper;
import com.gzhu.equipment.service.BorrowService;
import com.gzhu.equipment.service.MinioFileService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.time.LocalDateTime;

/**
 * 借用审批控制器
 *
 * POST   /borrows              → 提交借用申请
 * GET    /borrows/my           → 我的借用列表
 * GET    /borrows/{id}         → 借用详情
 * POST   /borrows/{id}/cancel  → 取消借用
 *
 * GET    /borrows/pending/first   → 一级待审批
 * GET    /borrows/pending/second  → 二级待审批
 * POST   /borrows/approve         → 审批操作
 *
 * POST   /borrows/{id}/return  → 归还登记
 * GET    /borrows/overdue      → 逾期列表
 */
@RestController
@RequestMapping("/borrows")
@RequiredArgsConstructor
@Api(tags = "借用审批管理")
public class BorrowController {

    private final BorrowService borrowService;
    private final MinioFileService minioFileService;
    private final AttachmentMapper attachmentMapper;

    // ==================== 借用申请 ====================

    @PostMapping
    @ApiOperation("提交借用申请")
    @PreAuthorize("hasAnyAuthority('borrow:create','borrow:my')")
    public R<BorrowRecord> submitBorrow(@Valid @RequestBody BorrowRequestDTO dto) {
        Long userId = getCurrentUserId();
        try {
            BorrowRecord record = borrowService.submitBorrow(dto, userId);
            return R.ok("借用申请已提交，等待审批", record);
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    @GetMapping("/my")
    @ApiOperation("我的借用列表")
    @PreAuthorize("hasAuthority('borrow:my')")
    public R<IPage<BorrowRecord>> myBorrows(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return R.ok(borrowService.myBorrows(getCurrentUserId(), page, size, status));
    }

    @GetMapping("/{id}")
    @ApiOperation("借用详情")
    @PreAuthorize("hasAuthority('borrow:view')")
    public R<BorrowRecord> getDetail(@PathVariable Long id) {
        BorrowRecord record = borrowService.getDetail(id);
        if (record == null) return R.fail(404, "借用单不存在");
        return R.ok(record);
    }

    @PostMapping("/{id}/cancel")
    @ApiOperation("取消借用申请")
    @PreAuthorize("hasAuthority('borrow:create')")
    public R<String> cancelBorrow(@PathVariable Long id) {
        try {
            borrowService.cancelBorrow(id, getCurrentUserId());
            return R.ok("已取消");
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    // ==================== 审批 ====================

    @GetMapping("/pending/first")
    @ApiOperation("一级审批列表（教师/admin）")
    @PreAuthorize("hasAuthority('approval:first')")
    public R<IPage<BorrowRecord>> pendingFirst(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(borrowService.pendingApprovals(getCurrentUserId(), 1, page, size));
    }

    @GetMapping("/pending/second")
    @ApiOperation("二级审批列表（实验室管理员/admin）")
    @PreAuthorize("hasAuthority('approval:second')")
    public R<IPage<BorrowRecord>> pendingSecond(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(borrowService.pendingApprovals(getCurrentUserId(), 2, page, size));
    }

    @PostMapping("/approve")
    @ApiOperation("审批操作（通过/驳回）")
    @PreAuthorize("hasAnyAuthority('approval:first','approval:second')")
    public R<BorrowRecord> approve(@Valid @RequestBody ApprovalRequestDTO dto) {
        try {
            BorrowRecord record = borrowService.approve(dto, getCurrentUserId());
            String msg = Boolean.TRUE.equals(dto.getApproved()) ? "审批通过" : "已驳回";
            return R.ok(msg, record);
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    // ==================== 归还 ====================

    @PostMapping("/{id}/return")
    @ApiOperation("归还登记（支持上传归还照片）")
    @PreAuthorize("hasAuthority('borrow:return')")
    public R<BorrowRecord> returnDevice(@PathVariable Long id,
                                         @RequestParam(required = false) String damageReport,
                                         @RequestParam(value = "file", required = false) MultipartFile file) {
        try {
            BorrowRecord record = borrowService.returnDevice(id, getCurrentUserId(), damageReport);

            // 上传归还照片（如有）
            if (file != null && !file.isEmpty()) {
                String objectPath = minioFileService.uploadImage(file, "RETURN");
                Attachment att = new Attachment();
                att.setBizType("RETURN_IMG");
                att.setBizId(record.getId());
                att.setFileUrl(objectPath);
                att.setFileSize(file.getSize());
                att.setUploadTime(LocalDateTime.now());
                att.setExpireTime(LocalDateTime.now().plusMonths(6)); // 半年后过期
                attachmentMapper.insert(att);
            }

            return R.ok("归还成功", record);
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        } catch (Exception e) {
            return R.fail("归还失败: " + e.getMessage());
        }
    }

    @GetMapping("/overdue")
    @ApiOperation("逾期未归还列表")
    @PreAuthorize("hasAuthority('return:manage')")
    public R<IPage<BorrowRecord>> overdueList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        IPage<BorrowRecord> pg = borrowService.page(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size),
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BorrowRecord>()
                        .eq(BorrowRecord::getStatus, "OVERDUE")
                        .orderByDesc(BorrowRecord::getOverdueDays));
        return R.ok(pg);
    }

    // ==================== 辅助 ====================

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof JwtUserPrincipal p) {
            return p.getUserId();
        }
        throw new IllegalStateException("未登录");
    }
}
