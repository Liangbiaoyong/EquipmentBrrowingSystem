package com.gzhu.equipment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gzhu.equipment.dto.ApprovalRequestDTO;
import com.gzhu.equipment.dto.BorrowRequestDTO;
import com.gzhu.equipment.entity.ApprovalLog;
import com.gzhu.equipment.entity.BorrowRecord;
import com.gzhu.equipment.entity.Device;
import com.gzhu.equipment.mapper.ApprovalLogMapper;
import com.gzhu.equipment.mapper.BorrowRecordMapper;
import com.gzhu.equipment.mapper.DeviceMapper;
import com.gzhu.equipment.service.BorrowService;
import com.gzhu.equipment.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

/**
 * 借用审批服务 — 状态机驱动的核心业务
 *
 * 状态流转：
 *   PENDING_APPROVAL → (全部通过) → APPROVED → BORROWING → RETURNED
 *                    → (任一驳回) → REJECTED
 *                    → (用户取消) → CANCELLED
 *   BORROWING → (逾期) → OVERDUE
 *
 * 审批流程（默认二级）：
 *   一级：审批人（教师，由申请人指定）
 *   二级：审核员（实验室管理员）
 *   admin 可处理任一级别
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BorrowServiceImpl extends ServiceImpl<BorrowRecordMapper, BorrowRecord> implements BorrowService {

    private final BorrowRecordMapper borrowMapper;
    private final ApprovalLogMapper approvalMapper;
    private final DeviceMapper deviceMapper;
    private final SystemConfigService configService;

    /** application.yml 默认值，数据库未配置时使用 */
    @Value("${borrow.max-days:7}")
    private int defaultMaxDays;

    // 冲突检测用的状态：这些状态下设备被占用
    private static final List<String> OCCUPIED_STATUSES = Arrays.asList(
            "PENDING_APPROVAL", "APPROVED", "BORROWING", "OVERDUE");

    private static final int DEFAULT_APPROVAL_STEPS = 2; // 默认二级审批

    // ==================== 提交借用申请 ====================

    @Override
    @Transactional
    public BorrowRecord submitBorrow(BorrowRequestDTO dto, Long userId) {
        // 1. 校验设备存在且可借
        Device device = deviceMapper.selectById(dto.getDeviceId());
        if (device == null) throw new IllegalArgumentException("设备不存在");
        if (device.getStatus() != 1) throw new IllegalArgumentException("设备不可借（维修中/已报废）");
        if (device.getAvailableQty() == null || device.getAvailableQty() <= 0) {
            throw new IllegalArgumentException("设备库存不足");
        }

        // 2. 借用时长校验（数据库优先，application.yml兜底）
        int maxDays = configService.getIntValue("borrow.max_days", defaultMaxDays);
        long borrowDays = ChronoUnit.DAYS.between(dto.getStartTime().toLocalDate(), dto.getEndTime().toLocalDate());
        if (borrowDays > maxDays) {
            throw new IllegalArgumentException("借用时长不能超过 " + maxDays + " 天，当前 " + borrowDays + " 天");
        }

        // 3. 时间冲突检测
        if (hasTimeConflict(dto.getDeviceId(), dto.getStartTime(), dto.getEndTime())) {
            throw new IllegalArgumentException("所选时段设备已被占用，请重新选择时间");
        }
        if (dto.getStartTime().isAfter(dto.getEndTime())) {
            throw new IllegalArgumentException("借用开始时间不能晚于结束时间");
        }

        // 3. 创建借用单
        BorrowRecord record = new BorrowRecord();
        record.setUserId(userId);
        record.setDeviceId(dto.getDeviceId());
        record.setStartTime(dto.getStartTime());
        record.setEndTime(dto.getEndTime());
        record.setReason(dto.getReason());
        record.setStatus("PENDING_APPROVAL");
        record.setCurrentStep(1);
        // 审批流快照：二级审批 JSON
        record.setApproveFlowDef("{\"steps\":[{\"step\":1,\"name\":\"审批人\"},{\"step\":2,\"name\":\"审核员\"}],\"totalSteps\":2}");

        borrowMapper.insert(record);

        // 4. 创建一级审批记录
        createApprovalLog(record.getId(), 1, dto.getApproverId());

        log.info("借用申请已提交: borrowId={} userId={} deviceId={}", record.getId(), userId, dto.getDeviceId());
        return record;
    }

    // ==================== 我的借用 ====================

    @Override
    public IPage<BorrowRecord> myBorrows(Long userId, int page, int size, String status) {
        LambdaQueryWrapper<BorrowRecord> w = new LambdaQueryWrapper<BorrowRecord>()
                .eq(BorrowRecord::getUserId, userId)
                .orderByDesc(BorrowRecord::getCreateTime);
        if (status != null && !status.isEmpty()) {
            w.eq(BorrowRecord::getStatus, status);
        }
        return borrowMapper.selectPage(new Page<>(page, size), w);
    }

    @Override
    public BorrowRecord getDetail(Long id) {
        return borrowMapper.selectById(id);
    }

    // ==================== 待审批列表 ====================

    @Override
    public IPage<BorrowRecord> pendingApprovals(Long approverId, int level, int page, int size) {
        // 查找该审批人负责的待审批记录
        // 通过 approval_log 表关联：step=level AND approver_id=approverId AND result=PENDING
        // 简化为子查询方式
        Page<BorrowRecord> pg = new Page<>(page, size);
        return borrowMapper.selectPage(pg,
                new LambdaQueryWrapper<BorrowRecord>()
                        .eq(BorrowRecord::getStatus, "PENDING_APPROVAL")
                        .eq(BorrowRecord::getCurrentStep, level)
                        .apply("id IN (SELECT borrow_id FROM approval_log WHERE step = {0} AND approver_id = {1} AND result = 'PENDING')",
                                level, approverId)
                        .orderByDesc(BorrowRecord::getCreateTime));
    }

    // ==================== 审批操作 ====================

    @Override
    @Transactional
    public BorrowRecord approve(ApprovalRequestDTO dto, Long approverId) {
        BorrowRecord record = borrowMapper.selectById(dto.getBorrowId());
        if (record == null) throw new IllegalArgumentException("借用单不存在");
        if (!"PENDING_APPROVAL".equals(record.getStatus())) {
            throw new IllegalArgumentException("该借用单不在审批中状态");
        }

        int currentStep = record.getCurrentStep();
        String result = Boolean.TRUE.equals(dto.getApproved()) ? "APPROVED" : "REJECTED";

        if (Boolean.FALSE.equals(dto.getApproved()) && (dto.getComment() == null || dto.getComment().trim().isEmpty())) {
            throw new IllegalArgumentException("驳回时必须填写审批意见");
        }

        // 更新审批记录
        ApprovalLog approvalLog = approvalMapper.selectOne(
                new LambdaQueryWrapper<ApprovalLog>()
                        .eq(ApprovalLog::getBorrowId, dto.getBorrowId())
                        .eq(ApprovalLog::getStep, currentStep)
                        .eq(ApprovalLog::getApproverId, approverId)
                        .eq(ApprovalLog::getResult, "PENDING"));
        if (approvalLog == null) {
            throw new IllegalArgumentException("您不是当前审批节点的审批人");
        }
        approvalLog.setResult(result);
        approvalLog.setComment(dto.getComment());
        approvalLog.setOperateTime(LocalDateTime.now());
        approvalMapper.updateById(approvalLog);

        if ("REJECTED".equals(result)) {
            // 驳回：终止流程
            record.setStatus("REJECTED");
            borrowMapper.updateById(record);
            log.info("借用申请已被驳回: borrowId={} by approverId={}", record.getId(), approverId);
            return record;
        }

        // 通过 → 判断是否有下一级
        int totalSteps = DEFAULT_APPROVAL_STEPS;
        if (currentStep >= totalSteps) {
            // 全部通过 → 扣减库存
            record.setStatus("APPROVED");
            borrowMapper.updateById(record);

            Device device = deviceMapper.selectById(record.getDeviceId());
            if (device != null) {
                device.setAvailableQty(Math.max(0, device.getAvailableQty() - 1));
                deviceMapper.updateById(device);
            }
            log.info("借用申请全部审批通过: borrowId={} deviceId={}", record.getId(), record.getDeviceId());
        } else {
            // 流转到下一级
            record.setCurrentStep(currentStep + 1);
            borrowMapper.updateById(record);
            // 创建下一级审批记录（审批人未定，由实验室管理员处理）
            createApprovalLog(record.getId(), currentStep + 1, null);
            log.info("审批流转: borrowId={} step={}→{}", record.getId(), currentStep, currentStep + 1);
        }

        return record;
    }

    // ==================== 归还 ====================

    @Override
    @Transactional
    public BorrowRecord returnDevice(Long borrowId, Long userId, String damageReport) {
        BorrowRecord record = borrowMapper.selectById(borrowId);
        if (record == null) throw new IllegalArgumentException("借用单不存在");
        if (!Arrays.asList("APPROVED", "BORROWING", "OVERDUE").contains(record.getStatus())) {
            throw new IllegalArgumentException("该借用单当前状态不允许归还");
        }

        record.setStatus("RETURNED");
        record.setRealReturnTime(LocalDateTime.now());
        record.setDamageReport(damageReport);

        // 逾期判断
        if (record.getEndTime() != null && LocalDateTime.now().isAfter(record.getEndTime())) {
            long days = java.time.Duration.between(record.getEndTime(), LocalDateTime.now()).toDays();
            record.setOverdueDays((int) days);
        }

        borrowMapper.updateById(record);

        // 归还库存
        Device device = deviceMapper.selectById(record.getDeviceId());
        if (device != null) {
            device.setAvailableQty(Math.min(device.getAvailableQty() + 1, device.getTotalQty()));
            // 有损坏报告 → 标记维修中
            if (damageReport != null && !damageReport.trim().isEmpty()) {
                device.setStatus(2);
            }
            deviceMapper.updateById(device);
        }

        log.info("设备已归还: borrowId={} overdueDays={}", borrowId, record.getOverdueDays());
        return record;
    }

    @Override
    @Transactional
    public void cancelBorrow(Long borrowId, Long userId) {
        BorrowRecord record = borrowMapper.selectById(borrowId);
        if (record == null) throw new IllegalArgumentException("借用单不存在");
        if (!record.getUserId().equals(userId)) throw new IllegalArgumentException("只能取消自己的借用申请");
        if (!"PENDING_APPROVAL".equals(record.getStatus())) {
            throw new IllegalArgumentException("只有审批中的申请可以取消");
        }
        record.setStatus("CANCELLED");
        borrowMapper.updateById(record);
        log.info("借用申请已取消: borrowId={}", borrowId);
    }

    // ==================== 冲突检测 ====================

    private boolean hasTimeConflict(Long deviceId, LocalDateTime start, LocalDateTime end) {
        // 查询该设备在目标时段内是否有未完成/未归还的借用
        Long count = borrowMapper.selectCount(
                new LambdaQueryWrapper<BorrowRecord>()
                        .eq(BorrowRecord::getDeviceId, deviceId)
                        .in(BorrowRecord::getStatus, OCCUPIED_STATUSES)
                        .lt(BorrowRecord::getStartTime, end)
                        .gt(BorrowRecord::getEndTime, start));
        return count != null && count > 0;
    }

    private void createApprovalLog(Long borrowId, int step, Long approverId) {
        ApprovalLog log = new ApprovalLog();
        log.setBorrowId(borrowId);
        log.setStep(step);
        log.setApproverId(approverId);
        log.setResult("PENDING");
        approvalMapper.insert(log);
    }
}
