package com.gzhu.equipment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gzhu.equipment.entity.Device;
import com.gzhu.equipment.entity.Laboratory;
import com.gzhu.equipment.entity.LaboratoryRoom;
import com.gzhu.equipment.mapper.DeviceMapper;
import com.gzhu.equipment.mapper.LaboratoryMapper;
import com.gzhu.equipment.mapper.LaboratoryRoomMapper;
import com.gzhu.equipment.service.LaboratoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LaboratoryServiceImpl extends ServiceImpl<LaboratoryMapper, Laboratory> implements LaboratoryService {

    private final LaboratoryMapper laboratoryMapper;
    private final LaboratoryRoomMapper roomMapper;
    private final DeviceMapper deviceMapper;

    @Override
    public IPage<Laboratory> pageQuery(int page, int size, String keyword) {
        LambdaQueryWrapper<Laboratory> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Laboratory::getName, keyword)
                    .or().like(Laboratory::getCode, keyword);
        }
        wrapper.orderByAsc(Laboratory::getName);
        return laboratoryMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public List<Laboratory> listEnabled() {
        return laboratoryMapper.selectList(
                new LambdaQueryWrapper<Laboratory>()
                        .eq(Laboratory::getStatus, 1)
                        .orderByAsc(Laboratory::getName));
    }

    @Override
    public Laboratory getDetail(Long id) {
        return laboratoryMapper.selectById(id);
    }

    @Override
    public IPage<LaboratoryRoom> pageRooms(int page, int size, Long laboratoryId, String roomName) {
        LambdaQueryWrapper<LaboratoryRoom> wrapper = new LambdaQueryWrapper<>();
        if (laboratoryId != null) {
            wrapper.eq(LaboratoryRoom::getLaboratoryId, laboratoryId);
        }
        if (StringUtils.hasText(roomName)) {
            wrapper.like(LaboratoryRoom::getRoomName, roomName);
        }
        wrapper.orderByAsc(LaboratoryRoom::getRoomName);
        return roomMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public void addRoom(LaboratoryRoom room) {
        roomMapper.insert(room);
    }

    @Override
    public void updateRoom(Long id, LaboratoryRoom room) {
        room.setId(id);
        roomMapper.updateById(room);
    }

    @Override
    public void deleteRoom(Long id) {
        roomMapper.deleteById(id);
    }

    @Override
    public List<String> listDistinctLocations() {
        return deviceMapper.selectList(
                        new LambdaQueryWrapper<Device>()
                                .select(Device::getLocation)
                                .isNotNull(Device::getLocation)
                                .groupBy(Device::getLocation))
                .stream()
                .map(Device::getLocation)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public int syncDeviceLaboratories() {
        List<Device> devices = deviceMapper.selectList(
                new LambdaQueryWrapper<Device>().isNotNull(Device::getLocation));
        int updated = 0;
        for (Device device : devices) {
            Long matchedLabId = matchLaboratoryId(device.getLocation());
            if (matchedLabId != null && !matchedLabId.equals(device.getLaboratoryId())) {
                device.setLaboratoryId(matchedLabId);
                deviceMapper.updateById(device);
                updated++;
            }
        }
        log.info("实验室同步完成：{} 台设备已更新", updated);
        return updated;
    }

    @Override
    public Long matchLaboratoryId(String location) {
        if (!StringUtils.hasText(location)) return null;
        List<LaboratoryRoom> rooms = roomMapper.selectList(null);
        // 优先最长匹配（最精确的房间名优先匹配）
        rooms.sort((a, b) -> b.getRoomName().length() - a.getRoomName().length());
        for (LaboratoryRoom room : rooms) {
            if (location.contains(room.getRoomName())) {
                return room.getLaboratoryId();
            }
        }
        return null;
    }
}
