package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 人-人契合度快照（半熟人撮合/契合度推荐）。
 *
 * 结构对齐既有trip_matches（人→队伍），便于前端复用展示组件；
 * 区别在于这里是人 → 人的匹配结果。
 */
@Data
@Entity
@Table(name = "people_matches")
public class PeopleMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 发起匹配的用户 */
    private Long userId;

    /** 被匹配的对象 */
    private Long targetId;

    /** 五维加权契合度0-100 */
    private Integer matchScore;

    /** 推荐分=契合度85%+信誉分15% */
    private Integer recommendScore;

    /** 各维度明细JSON：[{key,label,score,weight,note}] */
    @Column(columnDefinition = "TEXT")
    private String dimsJson;

    /** 半熟人度（0-100），单独落库便于筛选 */
    private Integer acquaintance;

    /** VIEW查看时计算/RECOMMEND推荐时算 */
    private String source;

    private LocalDateTime createdAt;
}
