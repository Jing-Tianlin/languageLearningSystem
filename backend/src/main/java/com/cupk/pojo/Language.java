package com.cupk.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("language")
public class Language {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String nameCn;
    private String nameNative;
    private String flagIcon;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}
