package com.cupk.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * UserProfile — 用户扩展信息（一对一关联 user）
 */
@Data
@TableName("user_profile")
public class UserProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String bio;
    private java.time.LocalDate birthDate;
    private String education;
    private String occupation;
    private Integer totalQuizCount;
    private Integer totalQuizCorrect;
    private LocalDateTime updatedAt;
}
