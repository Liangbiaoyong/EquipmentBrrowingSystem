package com.gzhu.equipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gzhu.equipment.entity.OverdueRecord;
import org.apache.ibatis.annotations.Mapper;

/** V6: 逾期记录 Mapper */
@Mapper
public interface OverdueRecordMapper extends BaseMapper<OverdueRecord> {
}
