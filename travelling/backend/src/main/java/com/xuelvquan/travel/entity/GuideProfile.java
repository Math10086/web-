package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "guide_profiles")
public class GuideProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String themes;

    @Column(columnDefinition = "TEXT")
    private String intro;

    /** 认证状态：PENDING待审核/APPROVED已认证/REJECTED未通过 */
    private String status = "PENDING";

    /**
     * B10供给层级：STUDENT_GUIDE学生导游/CERTIFIED_GUIDE平台认证导游。
     * 旅行社属商家体系（见merchants.agencyType），不在导游档案里。
     */
    private String supplyTier = "STUDENT_GUIDE";

    private Double ratingAvg = 0.0;
    private Integer ratingCount = 0;
    private Integer orderCount = 0;
    private LocalDateTime createdAt;
}
