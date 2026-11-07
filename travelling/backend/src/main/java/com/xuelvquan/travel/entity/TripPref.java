package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 结伴偏好（数据模型：结伴偏好TripPref）
 * 每位同学一份，用于偏好匹配打分与AI结伴推荐；不填写时按默认值参与匹配。
 */
@Data
@Entity
@Table(name = "trip_prefs")
public class TripPref {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private Long userId;

    /** 预期游玩天数 */
    private Integer expectDays;

    /** 游玩节奏：ENERGETIC精力满满/BALANCED适中/RELAXED纯休闲放松 */
    private String paceType = "BALANCED";

    /** 目的地偏好（关键词，可为空表示不限） */
    private String destinationPref;

    /** 兴趣标签逗号串，如：徒步,摄影,美食 */
    @Column(length = 255)
    private String interestTags;

    /** 可接受的人均预算上限（元） */
    private Double budget;

    /** 偏好出行方式：TRAIN/BUS/CAR/CARPOOL/BIKE */
    private String transport;

    /** 是否愿意接收跨校队伍推荐 */
    private Boolean openToCrossSchool = true;

    private LocalDateTime updatedAt;
}
