package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "posts")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 帖子类型：GUIDE攻略/MOMENT动态/LOCAL本地见闻/QUESTION提问 */
    private String type;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    private Long circleId;
    private String city;
    private String destination;
    private String modeTag;

    // ================= B2预算向结构化攻略 =================

    /** 人均预算（元/人），列表卡片露出「人均 ¥320」 */
    private Double costPerPerson;

    /** 建议游玩天数，露出「建议3天」 */
    private Integer days;

    /** 避坑主题，字典POST:avoid的code逗号串（防宰/门票坑/交通陷阱/住宿雷区） */
    @Column(length = 255)
    private String avoidTags;

    /** 适合人群，字典POST:suitable的code逗号串（学生党/情侣/亲子/独行/团队） */
    @Column(length = 255)
    private String suitableFor;

    /** 图片逗号串 */
    @Column(length = 4000)
    private String images;

    private Long authorId;

    private Integer viewCount = 0;
    private Integer likeCount = 0;
    private Integer favoriteCount = 0;
    private Integer citationCount = 0;
    private Integer commentCount = 0;

    /** 本地可信标识 */
    private Boolean trustedBadge = false;

    /** 审核状态：PENDING待审核/APPROVED已通过/REJECTED已驳回 */
    private String status = "PENDING";

    private LocalDateTime createdAt;
}
