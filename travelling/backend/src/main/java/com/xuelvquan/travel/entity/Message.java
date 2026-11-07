package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long fromId;
    private Long toId;

    @Column(columnDefinition = "TEXT")
    private String content;

    /** 消息类型：CHAT用户私聊/SYSTEM系统消息 */
    private String msgType = "CHAT";

    private Boolean readFlag = false;
    private LocalDateTime createdAt;
}
