package com.gzhu.equipment.vo;

import com.gzhu.equipment.entity.BorrowRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BorrowVO {
    private BorrowRecord record;
    private String deviceName;
    private String deviceModel;
    private String userName;
    private String approverName;
    private String deviceAssetNo;
}
