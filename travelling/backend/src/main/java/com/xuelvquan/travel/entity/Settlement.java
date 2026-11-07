package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台结算发放记录（管理员后台「结算」能力）。
 *
 * 两类结算对象：
 *   SHOP商家 —— 商城订单货款（平台按5% 抽取佣金）
 *   GUIDE导游 —— 向导订单收入（平台按订单commissionRate抽成，默认10%）
 *
 * 每生成一条记录，对应的订单会被标记settled=true，重复结算不会重复计入。
 */
@Data
@Entity
@Table(name = "settlements")
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 结算对象：SHOP商家/GUIDE导游 */
    private String type;

    /** 结算对象id：SHOP = merchantId，GUIDE = guideUserId */
    private Long targetId;

    private String targetName;

    private Integer orderCount = 0;

    /** 成交总额 */
    private Double grossAmount = 0.0;

    /** 平台佣金比例 */
    private Double commissionRate = 0.0;

    /** 平台佣金（平台收入） */
    private Double commissionAmount = 0.0;

    /** 应付对象金额 */
    private Double payableAmount = 0.0;

    /** PAID已发放 */
    private String status = "PAID";

    @Column(columnDefinition = "TEXT")
    private String remark;

    private Long operatorId;

    private LocalDateTime createdAt;
}
