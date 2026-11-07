package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 动态规划记录（动态行程调整/数据模型DynamicPlan）。
 *
 * 区别于"一次性预定、不可改动"的静态行程：行程生成后接入天气等外部因素，
 * 用户提出修改或遇突变时，系统综合天气、交通、营业时间、顺路度给出调整后方案，
 * 并同步行程/地图/费用估算与通知同行成员。本实体留存每次调整的原方案、
 * 调整因素与新方案，便于回溯。
 */
@Data
@Entity
@Table(name = "dynamic_plans")
public class DynamicPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 发起调整的用户 */
    private Long userId;

    /** 关联结伴行程（可空：纯个人行程规划） */
    private Long tripId;

    private String destination;

    private Integer days;

    /** 调整因素摘要，如"第2天雷阵雨" */
    private String adjustFactor;

    /** 原方案快照（JSON） */
    @Column(columnDefinition = "TEXT")
    private String oldPlanJson;

    /** 新方案快照（JSON） */
    @Column(columnDefinition = "TEXT")
    private String newPlanJson;

    /** 调整理由明细（JSON） */
    @Column(columnDefinition = "TEXT")
    private String reasonsJson;

    /** 关联行程方案/规划记录id（可空） */
    private Long routeId;

    private LocalDateTime createdAt;
}
