package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ads")
public class Ad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 广告位：SPLASH开屏/BANNER横幅/FEED信息流/SIDEBAR侧边栏/DETAIL详情页 */
    private String slot;

    private String title;
    @Column(length = 1024)
    private String image;
    @Column(length = 1024)
    private String link;
    private String advertiser;
    private String targetCity;

    /** 投放商家（商家中心「投放中心」只看自己的广告） */
    private Long merchantId;

    /** 审核状态：PENDING待审核/APPROVED已通过/REJECTED已驳回 */
    private String status = "PENDING";

    private Double price;
    private Integer viewCount = 0;
    private Integer clickCount = 0;

    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private LocalDateTime createdAt;
}
