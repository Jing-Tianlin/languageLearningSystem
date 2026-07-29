package com.cupk.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * Role — 角色表（多对多关联 user）
 */
@Data
@TableName("role")
public class Role {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;      // ROLE_USER, ROLE_VIP, ROLE_ADMIN
    private String name;      // 普通用户, VIP用户, 管理员
    private String description;
    private java.time.LocalDateTime createdAt;
}
