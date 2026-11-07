package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "reports")
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long reporterId;

    /** 举报对象：POST帖子/USER用户/TRIP行程/PRODUCT商品/GUIDE导游服务 */
    private String targetType;

    private Long targetId;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String evidence;

    /** 处理状态：PENDING待处理/HANDLED已处理 */
    private String status = "PENDING";

    @Column(columnDefinition = "TEXT")
    private String result;

    private LocalDateTime createdAt;
}
