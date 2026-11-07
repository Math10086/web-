package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 结伴匹配（数据模型：结伴匹配TripMatch）
 * 记录某用户与某队伍的偏好匹配度，支撑AI Top 3推荐、队长审核参考与匹配度公示。
 */
@Data
@Entity
@Table(name = "trip_matches")
public class TripMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long tripId;
    private Long userId;

    /** 偏好匹配度0-100 */
    private Integer matchScore;

    /** 推荐分（在匹配度基础上叠加信誉分、成团紧迫度等修正） */
    private Integer recommendScore;

    /** 各维度明细JSON数组：[{key,label,score,weight,note}] */
    @Column(columnDefinition = "TEXT")
    private String dimsJson;

    /** 计算时的来源：APPLY报名时快照/RECOMMEND推荐时计算 */
    private String source;

    private LocalDateTime createdAt;
}
