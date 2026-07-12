package com.gzhu.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/** V4.1: 借用成果记录 */
@Data
@TableName("borrow_outcome")
public class BorrowOutcome implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long borrowId;
    private Long deviceId;

    /** 成果类型: 学术论文/专利/软著/科研项目结题/科研获奖/竞赛获奖/学术著作/研究报告/标准制定/新产品工艺/教学成果奖/毕设论文/实体模型/大创结题/媒体报道/人才培养/其他 */
    private String outcomeType;
    /** 成果标题 */
    private String title;
    /** 成果详情（JSON） */
    private String detail;
    /** 附件URL列表 */
    private String fileUrls;
    /** 录入人ID */
    private Long recordedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
