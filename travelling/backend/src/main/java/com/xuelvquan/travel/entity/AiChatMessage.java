package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI助手对话记录（学旅圈个性化AI · 会话历史）。
 *
 * 每轮对话存两行：role=user一行、role=assistant一行。
 * 用途：①前端聊天窗口的历史回显；②作为大模型的近程上下文（最近N轮），
 * 与AiUserMemory的远程记忆（画像摘要）配合，构成两层记忆。
 */
@Data
@Entity
@Table(name = "ai_chat_messages", indexes = {
        @Index(name = "idx_ai_chat_user_time", columnList = "user_id,created_at")
})
public class AiChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    /** user用户发言/assistant AI回复/system系统提示（仅调试用，一般不落库） */
    private String role;

    @Column(length = 4096)
    private String content;

    /** 本条回复由哪个引擎生成：ollama本地/cloud云端兜底/offline离线兜底 */
    @Column(length = 32)
    private String source;

    private LocalDateTime createdAt;
}
