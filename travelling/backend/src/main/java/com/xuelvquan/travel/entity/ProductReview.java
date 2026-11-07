package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品评价（商家中心「评价管理」的数据来源）。
 * 一条订单中的一个商品只能评价一次（orderId + productId唯一）。
 */
@Data
@Entity
@Table(name = "product_reviews")
public class ProductReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;
    private Long productId;
    private String productName;

    /** 评价人（买家） */
    private Long userId;
    private String nickname;
    private String avatar;

    /** 所属店铺 */
    private Long merchantId;

    /** 1~5星 */
    private Integer rating = 5;

    @Column(columnDefinition = "TEXT")
    private String content;

    /** 商家回复 */
    @Column(columnDefinition = "TEXT")
    private String reply;

    private LocalDateTime repliedAt;

    private LocalDateTime createdAt;
}
