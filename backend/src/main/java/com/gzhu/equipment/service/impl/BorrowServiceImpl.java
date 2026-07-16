package com.gzhu.equipment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gzhu.equipment.dto.ApprovalRequestDTO;
import com.gzhu.equipment.dto.BorrowRequestDTO;
import com.gzhu.equipment.entity.ApprovalLog;
import com.gzhu.equipment.entity.Attachment;
import com.gzhu.equipment.entity.BorrowRecord;
import com.gzhu.equipment.entity.Device;
import com.gzhu.equipment.entity.OverdueRecord;
import com.gzhu.equipment.entity.SysUser;
import com.gzhu.equipment.mapper.ApprovalLogMapper;
import com.gzhu.equipment.mapper.AttachmentMapper;
import com.gzhu.equipment.mapper.BorrowRecordMapper;
import com.gzhu.equipment.mapper.DeviceMapper;
import com.gzhu.equipment.mapper.OverdueRecordMapper;
import com.gzhu.equipment.mapper.RepairRecordMapper;
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
 *   初审：审批人（教师，由申请人指定）
 *   终审：审核员（实验室管理员）
 *   admin 可处理任一级别
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BorrowServiceImpl extends ServiceImpl<BorrowRecordMapper, BorrowRecord> implements BorrowService {

    private final BorrowRecordMapper borrowMapper;
    private final ApprovalLogMapper approvalMapper;
    private final AttachmentMapper attachmentMapper;
    private final DeviceMapper deviceMapper;
    private final SysUserMapper userMapper;
    private final RepairRecordMapper repairMapper;
    private final OverdueRecordMapper overdueMapper;
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
            if (device == null || device.getBorrowStatus() == null || device.getBorrowStatus() != 1) continue;
            if (device.getAvailableQty() == null || device.getAvailableQty() <= 0) continue;
            if (hasTimeConflict(deviceId, dto.getStartTime(), dto.getEndTime())) continue;

            BorrowRecord r = new BorrowRecord();
            r.setUserId(userId); r.setDeviceId(deviceId);
            r.setStartTime(dto.getStartTime()); r.setEndTime(dto.getEndTime());
            r.setReason(dto.getReason()); r.setPurpose(dto.getPurpose());
            r.setPurposeCategory(dto.getPurposeCategory());
            r.setPurposeSubcategory(dto.getPurposeSubcategory());
            r.setStatus("PENDING_APPROVAL");
            r.setCurrentStep(1); r.setApproveFlowDef(flowDef);
            borrowMapper.insert(r);

            // 初审人：请求指定的 > 设备默认审批人 > 根据使用人查找 > null
            Long approver = dto.getApproverId();
            if (approver == null) approver = device.getDefaultApproverId();
            if (approver == null && device.getCustodian() != null) {
                SysUser custodianUser = userMapper.selectOne(
                        new LambdaQueryWrapper<SysUser>().eq(SysUser::getRealName, device.getCustodian()));
                if (custodianUser != null) approver = custodianUser.getId();
            }
            createApprovalLog(r.getId(), 1, approver);

            // 终审人：自动分配第一个实验室管理员（userType=2）
            if (totalSteps >= 2) {
                SysUser labAdmin = userMapper.selectOne(
                        new LambdaQueryWrapper<SysUser>().eq(SysUser::getUserType, 2).eq(SysUser::getStatus, 1).last("LIMIT 1"));
                createApprovalLog(r.getId(), 2, labAdmin != null ? labAdmin.getId() : null);
            }
            // 三级审批人：自动分配系统管理员
            if (totalSteps >= 3) {
                SysUser sysAdmin = userMapper.selectOne(
                        new LambdaQueryWrapper<SysUser>().eq(SysUser::getUserType, 3).eq(SysUser::getStatus, 1).last("LIMIT 1"));
                createApprovalLog(r.getId(), 3, sysAdmin != null ? sysAdmin.getId() : null);
            }
            if (approver != null) notificationService.notifyBorrowSubmitted(approver, device.getName(), r.getId());
            records.add(r);
            log.info("借用申请已提交: borrowId={} deviceId={}", r.getId(), deviceId);
        }
        if (records.isEmpty()) throw new IllegalArgumentException("所选设备均不可借或已被占用");
        // 通知申请人申请已提交
        if (!records.isEmpty()) {
            notificationService.send(userId, "借用申请已提交",
                    "您已提交" + records.size() + "条借用申请，请等待审批。", "APPROVAL");
        }
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
        SysUser user = userMapper.selectById(approverId);
        boolean isLabOrSysAdmin = user != null && (user.getUserType() == 2 || user.getUserType() == 3);
        boolean isTeacher = user != null && user.getUserType() != null && user.getUserType() == 1;
        Page<BorrowRecord> pg = new Page<>(page, size);

        LambdaQueryWrapper<BorrowRecord> w = new LambdaQueryWrapper<BorrowRecord>()
                .eq(BorrowRecord::getStatus, "PENDING_APPROVAL")
                .eq(BorrowRecord::getCurrentStep, level)
                .orderByDesc(BorrowRecord::getCreateTime);

        if (isLabOrSysAdmin) {
            // 管理员：显示所有待审批记录（不限审批人、不限设备使用人）
            w.apply("id IN (SELECT borrow_id FROM approval_log WHERE step = {0} AND result = 'PENDING')", level);
        } else if (isTeacher) {
            // 教师：按设备使用人(custodian)姓名匹配，不限approver_id
            String realName = user.getRealName();
            w.apply("id IN (SELECT borrow_id FROM approval_log WHERE step = {0} AND result = 'PENDING')", level);
            w.apply("device_id IN (SELECT id FROM device WHERE custodian = {0})", realName);
        } else {
            // 学生：按分配的approver_id查看
            w.apply("id IN (SELECT borrow_id FROM approval_log WHERE step = {0} AND approver_id = {1} AND result = 'PENDING')",
                    level, approverId);
        }
        return borrowMapper.selectPage(pg, w);
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

        // 判断审批人身份
        SysUser approverUser = userMapper.selectById(approverId);
        boolean isAdmin = approverUser != null && (approverUser.getUserType() == 2 || approverUser.getUserType() == 3);
        boolean isTeacher = approverUser != null && approverUser.getUserType() != null && approverUser.getUserType() == 1;

        // 查找审批记录
        ApprovalLog approvalLog = null;
        if (isAdmin) {
            // 管理员可以审批当前步的任何待审批记录（不限approver_id）
            approvalLog = approvalMapper.selectOne(
                    new LambdaQueryWrapper<ApprovalLog>()
                            .eq(ApprovalLog::getBorrowId, dto.getBorrowId())
                            .eq(ApprovalLog::getStep, currentStep)
                            .eq(ApprovalLog::getResult, "PENDING"));
        } else {
            // 非管理员只能审批分配给自己的
            approvalLog = approvalMapper.selectOne(
                    new LambdaQueryWrapper<ApprovalLog>()
                            .eq(ApprovalLog::getBorrowId, dto.getBorrowId())
                            .eq(ApprovalLog::getStep, currentStep)
                            .eq(ApprovalLog::getResult, "PENDING")
                            .eq(ApprovalLog::getApproverId, approverId));
            // 教师按设备使用人姓名匹配回退
            if (approvalLog == null && isTeacher) {
                Device device = deviceMapper.selectById(record.getDeviceId());
                if (device != null && approverUser.getRealName() != null
                        && approverUser.getRealName().equals(device.getCustodian())) {
                    approvalLog = approvalMapper.selectOne(
                            new LambdaQueryWrapper<ApprovalLog>()
                                    .eq(ApprovalLog::getBorrowId, dto.getBorrowId())
                                    .eq(ApprovalLog::getStep, currentStep)
                                    .eq(ApprovalLog::getResult, "PENDING"));
                }
            }
        }
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
                device.setBorrowStatus(2); // 借用中
                deviceMapper.updateById(device);
            }
            notificationService.notifyApprovalResult(record.getUserId(),
                    getDeviceName(record.getDeviceId()), record.getId(), true, null);
            log.info("借用申请全部审批通过: borrowId={} deviceId={}", record.getId(), record.getDeviceId());
        } else {
            // 流转到下一级（审批日志已在提交时创建）
            record.setCurrentStep(currentStep + 1);
            borrowMapper.updateById(record);
            log.info("审批流转: borrowId={} step={}→{}", record.getId(), currentStep, currentStep + 1);
        }

        return record;
    }

    // ==================== 归还申请+审批（学生→设备使用人） ====================

    @Override
    @Transactional
    public BorrowRecord requestReturn(Long borrowId, Long userId, String damageReport) {
        BorrowRecord record = borrowMapper.selectById(borrowId);
        if (record == null) throw new IllegalArgumentException("借用单不存在");
        if (!Arrays.asList("BORROWING", "OVERDUE").contains(record.getStatus())) {
            throw new IllegalArgumentException("该借用单当前状态不允许申请归还");
        }
        if (!record.getUserId().equals(userId)) {
            throw new IllegalArgumentException("只能申请归还自己的借用");
        }

        // 检查是否已上传归还照片
        Long photoCount = attachmentMapper.selectCount(
                new LambdaQueryWrapper<Attachment>()
                        .eq(Attachment::getBizType, "RETURN_IMG")
                        .eq(Attachment::getBizId, borrowId));
        if (photoCount == null || photoCount == 0) {
            throw new IllegalArgumentException("请先上传归还照片再提交归还申请");
        }

        record.setStatus("RETURN_PENDING");
        record.setDamageReport(damageReport);

        // 逾期判断
        if (record.getEndTime() != null && LocalDateTime.now().isAfter(record.getEndTime())) {
            long days = java.time.Duration.between(record.getEndTime(), LocalDateTime.now()).toDays();
            record.setOverdueDays((int) days);
        }
        borrowMapper.updateById(record);

        // 通知设备使用人/原审批人审批归还
        Long notifyUserId = findReturnApproverId(record);
        if (notifyUserId != null) {
            String deviceName = getDeviceName(record.getDeviceId());
            notificationService.send(notifyUserId, "归还审批提醒",
                    "用户申请归还设备【" + deviceName + "】的借用单#" + borrowId + "，请审核确认。", "RETURN");
        }
        log.info("归还申请已提交: borrowId={} status=RETURN_PENDING", borrowId);
        return record;
    }

    @Override
    @Transactional
    public void approveReturn(Long borrowId, Long adminId, boolean approved, String comment) {
        BorrowRecord record = borrowMapper.selectById(borrowId);
        if (record == null) throw new IllegalArgumentException("借用单不存在");
        if (!"RETURN_PENDING".equals(record.getStatus())) {
            throw new IllegalArgumentException("该借用单不在待审批归还状态");
        }

        if (approved) {
            // 通过 → 执行归还逻辑
            record.setStatus("RETURNED");
            record.setRealReturnTime(LocalDateTime.now());
            if (comment != null) record.setDamageReport(comment);
            borrowMapper.updateById(record);

            // 恢复设备库存和状态
            Device device = deviceMapper.selectById(record.getDeviceId());
            if (device != null) {
                device.setAvailableQty(Math.min(device.getAvailableQty() + 1, device.getTotalQty()));
                if (record.getDamageReport() != null && !record.getDamageReport().trim().isEmpty()) {
                    device.setBorrowStatus(3);
                    device.setDeviceStatus(2);
                    com.gzhu.equipment.entity.RepairRecord repair = new com.gzhu.equipment.entity.RepairRecord();
                    repair.setDeviceId(record.getDeviceId());
                    repair.setBorrowId(record.getId());
                    repair.setFaultDescription(record.getDamageReport());
                    repair.setStatus("PENDING");
                    repairMapper.insert(repair);
                } else {
                    device.setBorrowStatus(1);
                    device.setDeviceStatus(1);
                }
                deviceMapper.updateById(device);
            }
            log.info("归还已审批通过: borrowId={} adminId={}", borrowId, adminId);
        } else {
            // 驳回 → 退回借用中
            record.setStatus("BORROWING");
            borrowMapper.updateById(record);
            log.info("归还申请被驳回: borrowId={} adminId={}", borrowId, adminId);
        }

        // 通知申请人
        String deviceName = getDeviceName(record.getDeviceId());
        String msg = approved ? "您的归还申请已通过，设备【" + deviceName + "】已确认归还。"
                : "您的归还申请未被通过，原因：" + (comment != null ? comment : "设备状况需确认") + "，请处理后重新申请。";
        notificationService.send(record.getUserId(), "归还审批结果", msg, "RETURN");
    }

    @Override
    public List<BorrowRecord> listPendingReturns(Long userId, int page, int size) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) return List.of();

        LambdaQueryWrapper<BorrowRecord> w = new LambdaQueryWrapper<BorrowRecord>()
                .eq(BorrowRecord::getStatus, "RETURN_PENDING")
                .orderByDesc(BorrowRecord::getCreateTime);

        // 管理员看所有，教师/学生看自己的设备相关的
        if (user.getUserType() != null && user.getUserType() >= 2) {
            // 实验室管理员/系统管理员：全部
        } else if (user.getUserType() != null && user.getUserType() == 1) {
            // 教师：看custodian等于自己的设备
            w.and(wp -> wp.apply("device_id IN (SELECT id FROM device WHERE custodian = {0})", user.getRealName()));
        } else {
            w.eq(BorrowRecord::getUserId, userId);
            return List.of(); // 学生看不到待审批
        }

        Page<BorrowRecord> pg = new Page<>(page, size);
        return borrowMapper.selectPage(pg, w).getRecords();
    }

    /**
     * 查找归还审批人：取原一级审批通过的人，其次设备使用人
     */
    private Long findReturnApproverId(BorrowRecord record) {
        // 优先找一级审批通过的人
        ApprovalLog al = approvalMapper.selectOne(
                new LambdaQueryWrapper<ApprovalLog>()
                        .eq(ApprovalLog::getBorrowId, record.getId())
                        .eq(ApprovalLog::getStep, 1)
                        .eq(ApprovalLog::getResult, "APPROVED")
                        .last("LIMIT 1"));
        if (al != null && al.getApproverId() != null) return al.getApproverId();
        // 其次找设备使用人
        Device d = deviceMapper.selectById(record.getDeviceId());
        if (d != null && d.getCustodian() != null) {
            SysUser custodian = userMapper.selectOne(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getRealName, d.getCustodian()).last("LIMIT 1"));
            if (custodian != null) return custodian.getId();
        }
        // 最后交给系统管理员
        SysUser admin = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUserType, 3).eq(SysUser::getStatus, 1).last("LIMIT 1"));
        return admin != null ? admin.getId() : 1L;
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

        // 归还库存 + 恢复设备状态
        Device device = deviceMapper.selectById(record.getDeviceId());
        if (device != null) {
            device.setAvailableQty(Math.min(device.getAvailableQty() + 1, device.getTotalQty()));
            // 有损坏报告 → 标记设备待维修 + 自动创建维修记录
            if (damageReport != null && !damageReport.trim().isEmpty()) {
                device.setBorrowStatus(3); // 不可借
                device.setDeviceStatus(2); // 待维修
                com.gzhu.equipment.entity.RepairRecord repair = new com.gzhu.equipment.entity.RepairRecord();
                repair.setDeviceId(record.getDeviceId());
                repair.setBorrowId(record.getId());
                repair.setFaultDescription(damageReport);
                repair.setStatus("PENDING");
                repairMapper.insert(repair);
                log.info("损坏归还自动创建维修记录: repairId={} deviceId={}", repair.getId(), record.getDeviceId());
            } else {
                device.setBorrowStatus(1); // 可借用
                device.setDeviceStatus(1); // 正常
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
        // 确保 approverId 不为 null：null时自动分配系统管理员
        if (approverId == null) {
            SysUser sysAdmin = userMapper.selectOne(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getUserType, 3).eq(SysUser::getStatus, 1).last("LIMIT 1"));
            approverId = sysAdmin != null ? sysAdmin.getId() : 1L;
        }
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

    // ==================== V6 逾期管理增强 ====================

    @Override @Transactional
    public void adminForceReturn(Long borrowId, Long adminId, String damageReport, String remark) {
        BorrowRecord record = borrowMapper.selectById(borrowId);
        if (record == null) throw new IllegalArgumentException("借用单不存在");
        // 允许强制归还在 BORROWING（已过期） 或 OVERDUE 状态
        if (!"OVERDUE".equals(record.getStatus()) && !"BORROWING".equals(record.getStatus())) {
            throw new IllegalArgumentException("仅 BORROWING（已过期）或 OVERDUE 状态的借用单可以强制归还");
        }
        // 自动标记逾期
        if ("BORROWING".equals(record.getStatus())) {
            record.setStatus("OVERDUE");
            if (record.getEndTime() != null) {
                long days = java.time.Duration.between(record.getEndTime(), LocalDateTime.now()).toDays();
                record.setOverdueDays(Math.max(1, (int) days));
            } else {
                record.setOverdueDays(1);
            }
        }

        // 执行归还逻辑
        record.setStatus("RETURNED");
        record.setRealReturnTime(LocalDateTime.now());
        record.setDamageReport(damageReport);
        borrowMapper.updateById(record);

        // 恢复设备状态
        Device device = deviceMapper.selectById(record.getDeviceId());
        if (device != null) {
            device.setAvailableQty(Math.min(device.getAvailableQty() + 1, device.getTotalQty()));
            if (damageReport != null && !damageReport.trim().isEmpty()) {
                device.setBorrowStatus(3);
                device.setDeviceStatus(2);
                com.gzhu.equipment.entity.RepairRecord repair = new com.gzhu.equipment.entity.RepairRecord();
                repair.setDeviceId(record.getDeviceId());
                repair.setBorrowId(record.getId());
                repair.setFaultDescription(damageReport);
                repair.setStatus("PENDING");
                repairMapper.insert(repair);
            } else {
                device.setBorrowStatus(1);
                device.setDeviceStatus(1);
            }
            deviceMapper.updateById(device);
        }

        // 更新逾期记录表
        OverdueRecord ov = overdueMapper.selectOne(
                new LambdaQueryWrapper<OverdueRecord>()
                        .eq(OverdueRecord::getBorrowId, borrowId)
                        .orderByDesc(OverdueRecord::getCreateTime)
                        .last("LIMIT 1"));
        if (ov == null) {
            ov = new OverdueRecord();
            ov.setBorrowId(borrowId);
            ov.setDeviceId(record.getDeviceId());
            ov.setUserId(record.getUserId());
            ov.setOverdueDays(record.getOverdueDays());
        }
        ov.setCollectionStatus("COLLECTED");
        ov.setAdminCollectTime(LocalDateTime.now());
        ov.setCollectAdminId(adminId);
        ov.setCollectRemark(remark);
        if (ov.getId() == null) overdueMapper.insert(ov);
        else overdueMapper.updateById(ov);

        log.info("管理员强制归还: borrowId={} adminId={} remark={}", borrowId, adminId, remark);
    }

    @Override @Transactional
    public void sendOverdueNotify(Long borrowId, Long adminId) {
        BorrowRecord record = borrowMapper.selectById(borrowId);
        if (record == null) throw new IllegalArgumentException("借用单不存在");

        // 查找或创建逾期记录
        OverdueRecord ov = overdueMapper.selectOne(
                new LambdaQueryWrapper<OverdueRecord>()
                        .eq(OverdueRecord::getBorrowId, borrowId)
                        .orderByDesc(OverdueRecord::getCreateTime)
                        .last("LIMIT 1"));
        if (ov == null) {
            ov = new OverdueRecord();
            ov.setBorrowId(borrowId);
            ov.setDeviceId(record.getDeviceId());
            ov.setUserId(record.getUserId());
            ov.setOverdueDays(record.getOverdueDays() != null ? record.getOverdueDays() : 0);
            ov.setCollectionStatus("NOTIFIED");
            ov.setNotifyCount(1);
            ov.setLastNotifyTime(LocalDateTime.now());
            overdueMapper.insert(ov);
        } else {
            ov.setCollectionStatus("NOTIFIED");
            ov.setNotifyCount((ov.getNotifyCount() == null ? 0 : ov.getNotifyCount()) + 1);
            ov.setLastNotifyTime(LocalDateTime.now());
            overdueMapper.updateById(ov);
        }

        // 发送通知
        String deviceName = getDeviceName(record.getDeviceId());
        notificationService.notifyOverdue(record.getUserId(), deviceName, borrowId,
                record.getOverdueDays() != null ? record.getOverdueDays() : 0);
        log.info("逾期催还通知已发送: borrowId={} adminId={} count={}", borrowId, adminId, ov.getNotifyCount());
    }

    private String buildFlowDef(int totalSteps) {
        StringBuilder sb = new StringBuilder("{\"steps\":[");
        for (int i = 1; i <= totalSteps; i++) {
            String name = i == 1 ? "初审" : i == totalSteps ? "终审" : "审核员";
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
