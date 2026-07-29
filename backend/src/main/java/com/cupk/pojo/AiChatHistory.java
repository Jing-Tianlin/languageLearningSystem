package com.cupk.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * AiChatHistory — AI 对话历史表
 */
@Data
@TableName("ai_chat_history")
public class AiChatHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String langCode;
    private String role;     // user / ai
    private String content;
    private java.time.LocalDateTime createdAt;
}
