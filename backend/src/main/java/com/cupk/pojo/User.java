package com.cupk.pojo;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    private String email;
    private String phone;
    private String nickname;
    private String avatar;
    private Integer gender;
    private String currentLangCode;
    private String currentLevel;
    private Integer totalStudyDays;
    private Integer totalWordsLearned;
    private Integer points;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
        @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    /** 最近一次修改密码时间（用于密码重置后旧 token 失效判定） */
    private LocalDateTime lastPasswordChangeAt;
    @TableField(exist = false)
    private List<String> roles;
    @TableLogic
    private Integer isDeleted;
}
