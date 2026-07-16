package com.gzhu.equipment.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gzhu.equipment.dto.ApprovalRequestDTO;
import com.gzhu.equipment.dto.BorrowRequestDTO;
import com.gzhu.equipment.entity.BorrowRecord;
import java.util.List;

public interface BorrowService extends IService<BorrowRecord> {

    /** 提交借用申请（支持多设备，含时间冲突检测） */
    List<BorrowRecord> submitBorrow(BorrowRequestDTO dto, Long userId);

    /** 我的借用列表 */
    IPage<BorrowRecord> myBorrows(Long userId, int page, int size, String status);

    /** 借用详情 */
    BorrowRecord getDetail(Long id);

    /** 待审批列表（按级别） */
    IPage<BorrowRecord> pendingApprovals(Long approverId, int level, int page, int size);

    /** 审批操作（通过/驳回，自动流转） */
    BorrowRecord approve(ApprovalRequestDTO dto, Long approverId);

    /** 归还登记（直接完成） */
    BorrowRecord returnDevice(Long borrowId, Long userId, String damageReport);

    /** 归还申请（学生提交，含照片要求，状态→RETURN_PENDING） */
    BorrowRecord requestReturn(Long borrowId, Long userId, String damageReport);

    /** 审批归还申请（通过→RETURNED，由设备使用人/管理员操作） */
    void approveReturn(Long borrowId, Long adminId, boolean approved, String comment);

    /** 待审批归还列表（当前用户的设备相关的RETURN_PENDING记录） */
    java.util.List<BorrowRecord> listPendingReturns(Long userId, int page, int size);

    /** 管理员核验归还（确认照片/设备状况） */
    void verifyReturn(Long borrowId, Long adminId);

    /** 管理员强制归还（逾期设备，记录操作人+备注） */
    void adminForceReturn(Long borrowId, Long adminId, String damageReport, String remark);

    /** 发送催还通知（逾期，记录到逾期记录表） */
    void sendOverdueNotify(Long borrowId, Long adminId);

    /** 取消借用（仅申请人、PENDING_APPROVAL状态） */
    void cancelBorrow(Long borrowId, Long userId);
}
