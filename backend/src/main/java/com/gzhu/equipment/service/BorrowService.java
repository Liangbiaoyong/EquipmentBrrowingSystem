package com.gzhu.equipment.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gzhu.equipment.dto.ApprovalRequestDTO;
import com.gzhu.equipment.dto.BorrowRequestDTO;
import com.gzhu.equipment.entity.BorrowRecord;

public interface BorrowService extends IService<BorrowRecord> {

    /** 提交借用申请（含时间冲突检测） */
    BorrowRecord submitBorrow(BorrowRequestDTO dto, Long userId);

    /** 我的借用列表 */
    IPage<BorrowRecord> myBorrows(Long userId, int page, int size, String status);

    /** 借用详情 */
    BorrowRecord getDetail(Long id);

    /** 待审批列表（按级别） */
    IPage<BorrowRecord> pendingApprovals(Long approverId, int level, int page, int size);

    /** 审批操作（通过/驳回，自动流转） */
    BorrowRecord approve(ApprovalRequestDTO dto, Long approverId);

    /** 归还登记 */
    BorrowRecord returnDevice(Long borrowId, Long userId, String damageReport);

    /** 取消借用（仅申请人、PENDING_APPROVAL状态） */
    void cancelBorrow(Long borrowId, Long userId);
}
