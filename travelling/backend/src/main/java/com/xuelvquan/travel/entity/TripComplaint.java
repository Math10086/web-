package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 同行人信誉投诉（结伴后举报，区别于Report通用内容举报）。
 *
 * 业务定位（需求调研：信任缺74.5%，信誉分重要性4.14全项第一）：
 * 互评给出正向信号，投诉给出兜底信号——二者共同构成结伴信任闭环。
 *
 * 状态机：
 * PENDING（待处理）→ RESOLVED（核实属实，按类型扣信誉分）/ REJECTED（不属实，不扣分）
 * 被投诉人可在RESOLVED后发起申诉 → APPEALED（申诉中）
 * 管理员复核 → FINAL（终审），终审动作UPHELD维持原判/OVERTURNED申诉成立并回滚信誉分。
 */
@Data
@Entity
@Table(name = "trip_complaints")
public class TripComplaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联结伴行程 */
    private Long tripId;

    /** 举报人 */
    private Long reporterId;

    /** 被举报人 */
    private Long targetId;

    /** NO_SHOW爽约/LOST_CONTACT失联/MISBEHAVE言行不当/EXPENSE_DISPUTE费用不清 */
    private String type;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String evidence;

    /** 投诉状态：PENDING待处理/RESOLVED已解决/REJECTED已驳回/APPEALED申诉中/FINAL已终结 */
    private String status = "PENDING";

    @Column(columnDefinition = "TEXT")
    private String result;

    /** 本次投诉最终生效的信誉分变动（负数；申诉成立回滚时记 +|delta|） */
    private Integer creditDelta = 0;

    @Column(columnDefinition = "TEXT")
    private String appealReason;

    private LocalDateTime appealAt;

    private Long handledBy;

    private LocalDateTime handledAt;

    /** UPHELD维持原判/OVERTURNED申诉成立（仅终审后记录） */
    private String finalAction;

    private LocalDateTime createdAt;
}
