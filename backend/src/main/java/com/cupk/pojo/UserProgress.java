package com.cupk.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_progress")
public class UserProgress {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long vocabId;
    private Long lessonId;
    private String langCode;
    private Integer status;
    private Integer familiarity;
    private Integer reviewCount;
    private LocalDateTime lastReviewTime;
    private LocalDateTime nextReviewTime;
    // 新增字段
    private Integer hesitationMs;
    private Integer masteryLevel; // 0=新词 1=学习中 2=熟悉 3=已掌握
    private String errorTags;     // JSON数组字符串
    private LocalDateTime lastHesitationAt;
    private Double easeFactor;       // SM-2 难度因子(1.3~2.5)
    private Integer stability;       // 稳定性(连续正确次数)
    private Integer consecutiveCorrect; // 连续正确次数
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}
