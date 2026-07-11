package com.gzhu.equipment.service.impl;

import com.gzhu.equipment.dto.ApprovalRequestDTO;
import com.gzhu.equipment.dto.BorrowRequestDTO;
import com.gzhu.equipment.entity.ApprovalLog;
import com.gzhu.equipment.entity.BorrowRecord;
import com.gzhu.equipment.entity.Device;
import com.gzhu.equipment.mapper.ApprovalLogMapper;
import com.gzhu.equipment.mapper.BorrowRecordMapper;
import com.gzhu.equipment.mapper.DeviceMapper;
import com.gzhu.equipment.mapper.RepairRecordMapper;
import com.gzhu.equipment.mapper.SysUserMapper;
import com.gzhu.equipment.service.NotificationService;
import com.gzhu.equipment.service.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BorrowServiceImpl 单元测试
 *
 * 覆盖：提交借用、冲突检测、时长限制、多级审批流转、归还、取消
 */
@ExtendWith(MockitoExtension.class)
class BorrowServiceImplTest {

    @Mock
    private BorrowRecordMapper borrowMapper;

    @Mock
    private ApprovalLogMapper approvalMapper;

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private SystemConfigService configService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private RepairRecordMapper repairMapper;

    private BorrowServiceImpl borrowService;

    @BeforeEach
    void setUp() {
        borrowService = new BorrowServiceImpl(borrowMapper, approvalMapper, deviceMapper, sysUserMapper, repairMapper, configService, notificationService);
        // 默认借用时长限制宽松，各测试可覆盖
        lenient().when(configService.getIntValue(eq("borrow.max_days"), anyInt())).thenReturn(30);
        lenient().when(configService.getIntValue(eq("borrow.default_approval_steps"), anyInt())).thenReturn(2);
    }

    private Device createAvailableDevice() {
        Device d = new Device();
        d.setId(1L);
        d.setName("测试设备");
        d.setStatus(1);
        d.setTotalQty(5);
        d.setAvailableQty(3);
        return d;
    }

    private BorrowRequestDTO createBorrowRequest(Long deviceId) {
        BorrowRequestDTO dto = new BorrowRequestDTO();
        dto.setDeviceId(deviceId);
        dto.setStartTime(LocalDateTime.now().plusDays(1));
        dto.setEndTime(LocalDateTime.now().plusDays(3));
        dto.setReason("课程实验用");
        dto.setApproverId(2L);
        return dto;
    }

    // ==================== 提交借用 ====================

    @Test
    @DisplayName("提交借用（有效数据）→ 创建借用单 + 审批记录")
    void submitBorrow_valid_shouldCreateRecord() {
        Device device = createAvailableDevice();
        when(deviceMapper.selectById(1L)).thenReturn(device);
        when(configService.getIntValue(eq("borrow.max_days"), anyInt())).thenReturn(7);
        when(borrowMapper.selectCount(any())).thenReturn(0L);
        when(borrowMapper.insert(any())).thenReturn(1);

        BorrowRequestDTO dto = createBorrowRequest(1L);
        java.util.List<BorrowRecord> records = borrowService.submitBorrow(dto, 10L);

        assertThat(records).hasSize(1);
        assertThat(records.get(0).getUserId()).isEqualTo(10L);
        assertThat(records.get(0).getStatus()).isEqualTo("PENDING_APPROVAL");
        assertThat(records.get(0).getCurrentStep()).isEqualTo(1);
        verify(borrowMapper).insert(any(BorrowRecord.class));
        verify(approvalMapper, times(2)).insert(any(ApprovalLog.class));
    }

    @Test
    @DisplayName("提交借用（设备不存在）→ 所有设备不可借时抛出异常")
    void submitBorrow_deviceNotFound_shouldThrow() {
        // deviceMapper返回null默认→设备不存在→continue→空列表→异常
        assertThatThrownBy(() -> borrowService.submitBorrow(createBorrowRequest(999L), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("均不可借");
    }

    @Test
    @DisplayName("提交借用（设备不可借）→ 抛出异常")
    void submitBorrow_deviceNotAvailable_shouldThrow() {
        Device device = createAvailableDevice();
        device.setStatus(3); // 维修中
        when(deviceMapper.selectById(1L)).thenReturn(device);

        assertThatThrownBy(() -> borrowService.submitBorrow(createBorrowRequest(1L), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("均不可借");
    }

    @Test
    @DisplayName("提交借用（库存不足）→ 抛出异常")
    void submitBorrow_noStock_shouldThrow() {
        Device device = createAvailableDevice();
        device.setAvailableQty(0);
        when(deviceMapper.selectById(1L)).thenReturn(device);

        assertThatThrownBy(() -> borrowService.submitBorrow(createBorrowRequest(1L), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("均不可借");
    }

    @Test
    @DisplayName("提交借用（超时长限制）→ 抛出异常（在设备查询前校验）")
    void submitBorrow_exceedMaxDays_shouldThrow() {
        when(configService.getIntValue(eq("borrow.max_days"), anyInt())).thenReturn(7);

        BorrowRequestDTO dto = createBorrowRequest(1L);
        dto.setStartTime(LocalDateTime.now().plusDays(1));
        dto.setEndTime(LocalDateTime.now().plusDays(30)); // 29天 > 7天

        assertThatThrownBy(() -> borrowService.submitBorrow(dto, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("借用时长不能超过");
    }

    @Test
    @DisplayName("提交借用（时间冲突）→ 抛出异常")
    void submitBorrow_timeConflict_shouldThrow() {
        Device device = createAvailableDevice();
        when(deviceMapper.selectById(1L)).thenReturn(device);
        when(configService.getIntValue(eq("borrow.max_days"), anyInt())).thenReturn(7);
        when(borrowMapper.selectCount(any())).thenReturn(1L); // 有冲突

        assertThatThrownBy(() -> borrowService.submitBorrow(createBorrowRequest(1L), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已被占用");
    }

    @Test
    @DisplayName("提交借用（开始>结束）→ 抛出异常")
    void submitBorrow_startAfterEnd_shouldThrow() {
        BorrowRequestDTO dto = createBorrowRequest(1L);
        dto.setStartTime(LocalDateTime.now().plusDays(5));
        dto.setEndTime(LocalDateTime.now().plusDays(1));

        assertThatThrownBy(() -> borrowService.submitBorrow(dto, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能晚于");
    }

    // ==================== 审批 ====================

    @Test
    @DisplayName("一级审批通过 → 流转到二级")
    void approve_step1_shouldFlowToStep2() {
        BorrowRecord record = new BorrowRecord();
        record.setId(100L);
        record.setStatus("PENDING_APPROVAL");
        record.setCurrentStep(1);

        when(borrowMapper.selectById(100L)).thenReturn(record);

        ApprovalLog approvalLog = new ApprovalLog();
        approvalLog.setStep(1);
        approvalLog.setResult("PENDING");
        when(approvalMapper.selectOne(any())).thenReturn(approvalLog);

        ApprovalRequestDTO dto = new ApprovalRequestDTO();
        dto.setBorrowId(100L);
        dto.setApproved(true);

        borrowService.approve(dto, 2L);

        assertThat(record.getCurrentStep()).isEqualTo(2);
        assertThat(record.getStatus()).isEqualTo("PENDING_APPROVAL"); // 未最终通过
        verify(borrowMapper, times(1)).updateById(any());
        verify(approvalMapper).updateById(approvalLog);
    }

    @Test
    @DisplayName("二级审批通过 → 最终通过，扣减库存")
    void approve_step2_shouldFinalizeAndDeductStock() {
        BorrowRecord record = new BorrowRecord();
        record.setId(100L);
        record.setDeviceId(1L);
        record.setStatus("PENDING_APPROVAL");
        record.setCurrentStep(2); // 已经是二级

        when(borrowMapper.selectById(100L)).thenReturn(record);

        ApprovalLog approvalLog = new ApprovalLog();
        approvalLog.setStep(2);
        approvalLog.setResult("PENDING");
        when(approvalMapper.selectOne(any())).thenReturn(approvalLog);

        Device device = new Device();
        device.setId(1L);
        device.setAvailableQty(3);
        when(deviceMapper.selectById(1L)).thenReturn(device);

        ApprovalRequestDTO dto = new ApprovalRequestDTO();
        dto.setBorrowId(100L);
        dto.setApproved(true);

        borrowService.approve(dto, 3L);

        assertThat(record.getStatus()).isEqualTo("APPROVED");
        assertThat(device.getAvailableQty()).isEqualTo(2); // 3-1=2
        verify(deviceMapper).updateById(device);
    }

    @Test
    @DisplayName("审批驳回 → 状态变为REJECTED，需填写意见")
    void approve_reject_shouldSetRejected() {
        BorrowRecord record = new BorrowRecord();
        record.setId(100L);
        record.setStatus("PENDING_APPROVAL");
        record.setCurrentStep(1);

        when(borrowMapper.selectById(100L)).thenReturn(record);

        ApprovalLog approvalLog = new ApprovalLog();
        approvalLog.setStep(1);
        approvalLog.setResult("PENDING");
        when(approvalMapper.selectOne(any())).thenReturn(approvalLog);

        ApprovalRequestDTO dto = new ApprovalRequestDTO();
        dto.setBorrowId(100L);
        dto.setApproved(false);
        dto.setComment("设备已预约");

        borrowService.approve(dto, 2L);

        assertThat(record.getStatus()).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("驳回不写意见 → 抛出异常")
    void approve_rejectWithoutComment_shouldThrow() {
        BorrowRecord record = new BorrowRecord();
        record.setId(100L);
        record.setStatus("PENDING_APPROVAL");
        record.setCurrentStep(1);

        when(borrowMapper.selectById(100L)).thenReturn(record);

        ApprovalRequestDTO dto = new ApprovalRequestDTO();
        dto.setBorrowId(100L);
        dto.setApproved(false);
        dto.setComment("");

        assertThatThrownBy(() -> borrowService.approve(dto, 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("驳回时必须填写审批意见");
    }

    @Test
    @DisplayName("非当前审批人操作 → 抛出异常")
    void approve_notAuthorized_shouldThrow() {
        BorrowRecord record = new BorrowRecord();
        record.setId(100L);
        record.setStatus("PENDING_APPROVAL");
        record.setCurrentStep(1);

        when(borrowMapper.selectById(100L)).thenReturn(record);
        when(approvalMapper.selectOne(any())).thenReturn(null); // 不是审批人

        ApprovalRequestDTO dto = new ApprovalRequestDTO();
        dto.setBorrowId(100L);
        dto.setApproved(true);

        assertThatThrownBy(() -> borrowService.approve(dto, 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不是当前审批节点");
    }

    // ==================== 归还 ====================

    @Test
    @DisplayName("归还设备 → 库存恢复、状态更新")
    void returnDevice_shouldRestoreStock() {
        BorrowRecord record = new BorrowRecord();
        record.setId(100L);
        record.setDeviceId(1L);
        record.setUserId(10L);
        record.setStatus("BORROWING");
        record.setEndTime(LocalDateTime.now().plusDays(1));

        when(borrowMapper.selectById(100L)).thenReturn(record);
        when(sysUserMapper.selectById(10L)).thenReturn(null); // 10L申请人不存在→非管理员

        Device device = new Device();
        device.setId(1L);
        device.setTotalQty(5);
        device.setAvailableQty(2);
        when(deviceMapper.selectById(1L)).thenReturn(device);

        borrowService.returnDevice(100L, 10L, null);

        assertThat(record.getStatus()).isEqualTo("RETURNED");
        assertThat(record.getRealReturnTime()).isNotNull();
        assertThat(device.getAvailableQty()).isEqualTo(3); // 2+1=3
        verify(deviceMapper).updateById(device);
    }

    @Test
    @DisplayName("归还设备（逾期）→ 计算逾期天数")
    void returnDevice_overdue_shouldCalculateDays() {
        BorrowRecord record = new BorrowRecord();
        record.setId(100L);
        record.setDeviceId(1L);
        record.setUserId(10L);
        record.setStatus("BORROWING");
        record.setEndTime(LocalDateTime.now().minusDays(5)); // 5天前到期

        when(borrowMapper.selectById(100L)).thenReturn(record);
        when(sysUserMapper.selectById(10L)).thenReturn(null);

        Device device = new Device();
        device.setId(1L);
        device.setAvailableQty(2);
        device.setTotalQty(5);
        when(deviceMapper.selectById(1L)).thenReturn(device);

        borrowService.returnDevice(100L, 10L, null);

        assertThat(record.getOverdueDays()).isGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("归还设备（损坏）→ 标记维修")
    void returnDevice_damaged_shouldMarkRepair() {
        BorrowRecord record = new BorrowRecord();
        record.setId(100L);
        record.setDeviceId(1L);
        record.setUserId(10L);
        record.setStatus("BORROWING");
        record.setEndTime(LocalDateTime.now().plusDays(1));

        when(borrowMapper.selectById(100L)).thenReturn(record);
        when(sysUserMapper.selectById(10L)).thenReturn(null);

        Device device = new Device();
        device.setId(1L);
        device.setTotalQty(5);
        device.setAvailableQty(2);
        device.setStatus(1);
        when(deviceMapper.selectById(1L)).thenReturn(device);

        borrowService.returnDevice(100L, 10L, "镜头破损");

        assertThat(device.getStatus()).isEqualTo(3); // 维修中/待报废
    }

    @Test
    @DisplayName("归还（状态不允许）→ 抛出异常")
    void returnDevice_wrongStatus_shouldThrow() {
        BorrowRecord record = new BorrowRecord();
        record.setId(100L);
        record.setUserId(10L);
        record.setStatus("PENDING_APPROVAL");
        when(borrowMapper.selectById(100L)).thenReturn(record);

        assertThatThrownBy(() -> borrowService.returnDevice(100L, 10L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不允许归还");
    }

    // ==================== 取消 ====================

    @Test
    @DisplayName("取消借用（本人+审批中）→ 取消成功")
    void cancelBorrow_ownPending_shouldSucceed() {
        BorrowRecord record = new BorrowRecord();
        record.setId(100L);
        record.setUserId(10L);
        record.setStatus("PENDING_APPROVAL");
        when(borrowMapper.selectById(100L)).thenReturn(record);

        borrowService.cancelBorrow(100L, 10L);

        assertThat(record.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("取消他人借用 → 抛出异常")
    void cancelBorrow_notOwner_shouldThrow() {
        BorrowRecord record = new BorrowRecord();
        record.setId(100L);
        record.setUserId(10L);
        record.setStatus("PENDING_APPROVAL");
        when(borrowMapper.selectById(100L)).thenReturn(record);

        assertThatThrownBy(() -> borrowService.cancelBorrow(100L, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("只能取消自己的");
    }

    @Test
    @DisplayName("取消已批准的借用 → 抛出异常")
    void cancelBorrow_approved_shouldThrow() {
        BorrowRecord record = new BorrowRecord();
        record.setId(100L);
        record.setUserId(10L);
        record.setStatus("APPROVED");
        when(borrowMapper.selectById(100L)).thenReturn(record);

        assertThatThrownBy(() -> borrowService.cancelBorrow(100L, 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("只有审批中的申请可以取消");
    }
}
