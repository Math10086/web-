package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    /** 通知类型：SYSTEM系统/TRIP行程/INTERACT互动/AUDIT审核/TRADE交易 */
    private String type;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    private Long refId;
    private Boolean readFlag = false;
    private LocalDateTime createdAt;
}
