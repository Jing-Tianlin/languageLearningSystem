package com.cupk.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * Permission — 权限表
 */
@Data
@TableName("permission")
public class Permission {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;      // "vocab:read", "vocab:create", "practice:do" 等
    private String name;
    private String module;    // 所属模块
    private java.time.LocalDateTime createdAt;
}
