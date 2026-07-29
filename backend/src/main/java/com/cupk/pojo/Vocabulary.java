package com.cupk.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("vocabulary")
public class Vocabulary {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String langCode;
    private Long lessonId;
    private String word;
    private String phonetic;
    private String romanization;
    private String definition;
    private String exampleSentence;
    private String exampleTranslation;
    private String audioUrl;
    private String partOfSpeech;
    private String level;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}
