package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI助手的用户长期记忆（学旅圈个性化AI · 画像摘要）。
 *
 * 个性化不靠微调模型，靠摘要维持长期记忆：每轮对话结束后，让大模型把
 * 旧摘要+最新对话融合成一份新的画像摘要（性格、偏好、历史诉求），
 * 存回本表。下次对话时注入system prompt——对话越多摘要越准，
 * 表现上就是「AI被养熟了」。
 *
 * 用户一句话没说过时摘要为空，这时只注入档案标签（users.profile_tags）
 * 和结伴偏好（trip_prefs），AI从第一条回复起就认识这个用户。
 */
@Data
@Entity
@Table(name = "ai_user_memories")
public class AiUserMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private Long userId;

    /** 大模型维护的画像摘要（中文自然语言，控制在几百字内） */
    @Column(length = 2048)
    private String memorySummary;

    /** 累计对话轮数 */
    private Integer chatCount = 0;

    private LocalDateTime updatedAt;
}
