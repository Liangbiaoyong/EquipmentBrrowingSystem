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
import com.gzhu.equipment.entity.SysUser;
import com.gzhu.equipment.mapper.ApprovalLogMapper;
import com.gzhu.equipment.mapper.BorrowRecordMapper;
import com.gzhu.equipment.mapper.DeviceMapper;
import com.gzhu.equipment.mapper.SysUserMapper;
import com.gzhu.equipment.service.BorrowService;
import com.gzhu.equipment.service.NotificationService;
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
    private final SysUserMapper userMapper;
    private final SystemConfigService configService;
    private final NotificationService notificationService;

    /** application.yml 默认值，数据库未配置时使用 */
    @Value("${borrow.max-days:7}")
    private int defaultMaxDays;

    private static final List<String> OCCUPIED_STATUSES = Arrays.asList("PENDING_APPROVAL", "APPROVED", "BORROWING", "OVERDUE");

    @Override @Transactional
    public List<BorrowRecord> submitBorrow(BorrowRequestDTO dto, Long userId) {
        // 确定设备列表（兼容单设备deviceId和多设备deviceIds）
        List<Long> deviceIds = dto.getDeviceIds();
        if (deviceIds == null || deviceIds.isEmpty()) {
            if (dto.getDeviceId() == null) throw new IllegalArgumentException("请选择设备");
            deviceIds = List.of(dto.getDeviceId());
        }

        int maxDays = configService.getIntValue("borrow.max_days", defaultMaxDays);
        long borrowDays = ChronoUnit.DAYS.between(dto.getStartTime().toLocalDate(), dto.getEndTime().toLocalDate());
        if (borrowDays > maxDays) throw new IllegalArgumentException("借用时长不能超过 " + maxDays + " 天");
        if (dto.getStartTime().isAfter(dto.getEndTime())) throw new IllegalArgumentException("开始时间不能晚于结束时间");

        int totalSteps = configService.getIntValue("borrow.default_approval_steps", 2);
        String flowDef = buildFlowDef(totalSteps);

        List<BorrowRecord> records = new java.util.ArrayList<>();
        for (Long deviceId : deviceIds) {
            Device device = deviceMapper.selectById(deviceId);
            if (device == null || device.getStatus() != 1) continue;
            if (device.getAvailableQty() == null || device.getAvailableQty() <= 0) continue;
            if (hasTimeConflict(deviceId, dto.getStartTime(), dto.getEndTime())) continue;

            BorrowRecord r = new BorrowRecord();
            r.setUserId(userId); r.setDeviceId(deviceId);
            r.setStartTime(dto.getStartTime()); r.setEndTime(dto.getEndTime());
            r.setReason(dto.getReason()); r.setStatus("PENDING_APPROVAL");
            r.setCurrentStep(1); r.setApproveFlowDef(flowDef);
            borrowMapper.insert(r);

            createApprovalLog(r.getId(), 1, dto.getApproverId());
            if (dto.getApproverId() != null) notificationService.notifyBorrowSubmitted(dto.getApproverId(), device.getName(), r.getId());
            records.add(r);
            log.info("借用申请已提交: borrowId={} deviceId={}", r.getId(), deviceId);
        }
        if (records.isEmpty()) throw new IllegalArgumentException("所选设备均不可借或已被占用");
        return records;
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
            // 驳回：终止流程 + 通知申请人
            record.setStatus("REJECTED");
            borrowMapper.updateById(record);
            notificationService.notifyApprovalResult(record.getUserId(),
                    getDeviceName(record.getDeviceId()), record.getId(), false, dto.getComment());
            log.info("借用申请已被驳回: borrowId={} by approverId={}", record.getId(), approverId);
            return record;
        }

        // 通过 → 判断是否有下一级（从配置读取审批级数）
        int totalSteps = configService.getIntValue("borrow.default_approval_steps", 2);
        if (currentStep >= totalSteps) {
            // 全部通过 → 扣减库存 + 通知申请人
            record.setStatus("APPROVED");
            borrowMapper.updateById(record);

            Device device = deviceMapper.selectById(record.getDeviceId());
            if (device != null) {
                device.setAvailableQty(Math.max(0, device.getAvailableQty() - 1));
                deviceMapper.updateById(device);
            }
            notificationService.notifyApprovalResult(record.getUserId(),
                    getDeviceName(record.getDeviceId()), record.getId(), true, null);
            log.info("借用申请全部审批通过: borrowId={} deviceId={}", record.getId(), record.getDeviceId());
        } else {
            // 流转到下一级
            record.setCurrentStep(currentStep + 1);
            borrowMapper.updateById(record);
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
        // 借用人或管理员均可归还
        SysUser user = userMapper.selectById(userId);
        boolean isAdmin = user != null && (user.getUserType() == 2 || user.getUserType() == 3);
        if (!record.getUserId().equals(userId) && !isAdmin) {
            throw new IllegalArgumentException("只能归还自己的借用，或由管理员操作");
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

    @Override @Transactional
    public void verifyReturn(Long borrowId, Long adminId) {
        BorrowRecord r = borrowMapper.selectById(borrowId);
        if (r == null) throw new IllegalArgumentException("借用单不存在");
        if (!"RETURNED".equals(r.getStatus())) throw new IllegalArgumentException("该借用单尚未归还");
        // 核验通过：正式标记（可扩展为独立状态VERIFIED）
        log.info("归还核验完成: borrowId={} adminId={}", borrowId, adminId);
    }

    private String buildFlowDef(int totalSteps) {
        StringBuilder sb = new StringBuilder("{\"steps\":[");
        for (int i = 1; i <= totalSteps; i++) {
            String name = i == 1 ? "审批人" : i == totalSteps ? "最终确认" : "审核员";
            if (i > 1) sb.append(",");
            sb.append("{\"step\":").append(i).append(",\"name\":\"").append(name).append("\"}");
        }
        sb.append("],\"totalSteps\":").append(totalSteps).append("}");
        return sb.toString();
    }

    private String getDeviceName(Long deviceId) {
        Device d = deviceMapper.selectById(deviceId);
        return d != null ? d.getName() : "未知设备";
    }
}
