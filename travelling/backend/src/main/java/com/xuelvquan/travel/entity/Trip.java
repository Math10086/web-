package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "trips")
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long captainId;
    private String title;
    private String destination;
    private LocalDateTime departAt;
    private Integer maxMembers;
    private Double budget;
    private String transport;
    private String meetPoint;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** 强度等级：LOW轻松/MIDDLE适中/HIGH高强度 */
    private String riskLevel = "LOW";

    // ===== 结伴偏好（队长发布时设定，作为偏好匹配与推荐的基准）=====

    /** 游玩节奏：ENERGETIC精力满满/BALANCED适中/RELAXED纯休闲放松 */
    private String paceType = "BALANCED";

    /** 预期游玩天数 */
    private Integer prefDays;

    /** 兴趣标签逗号串，如：徒步,摄影,美食 */
    @Column(length = 255)
    private String interestTags;

    /** 队伍开放范围：ALL三校跨校/SAME仅本校 */
    private String schoolScope = "ALL";

    /** 行程状态：OPEN招募中/FULL已满员/ONGOING进行中/FINISHED已结束 */
    private String status = "OPEN";

    private LocalDateTime createdAt;

    /** 费用明细JSON数组（含每人均摊） */
    @Column(columnDefinition = "TEXT")
    private String expensesJson;

    /** B3：队长已确认全队账目结清（确认后全队获得守信加分，防止重复奖励） */
    private Boolean expensesSettled = false;
}
