package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "merchants")
public class Merchant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String shopName;

    @Column(columnDefinition = "TEXT")
    private String qualification;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** 入驻状态：PENDING待审核/APPROVED已通过/REJECTED已驳回 */
    private String status = "PENDING";

    /**
     * B10商家类型：TRAVEL_AGENCY旅行社/OUTDOOR_SHOP户外装备店 /
     * HOSTEL民宿客栈/OTHER其他。旅行社即「三层供给」的第 ③层。
     */
    private String agencyType = "OTHER";

    /** B10旅行社资质编号（agencyType=TRAVEL_AGENCY时填写，展示在资质信息位） */
    @Column(length = 128)
    private String licenseNo;

    private Integer credit = 100;
    private LocalDateTime createdAt;
}
