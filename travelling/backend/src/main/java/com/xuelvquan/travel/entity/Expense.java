package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消费记录（消费与费用管理/数据模型Expense）。
 *
 * 支撑个人消费报表与团队共享消费报表：记录每笔支出的项目、金额、经手人（付款人）
 * 与分摊成员，并据此计算AA结算与对账。
 */
@Data
@Entity
@Table(name = "expenses")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** PERSONAL个人消费/TEAM团队消费 */
    private String scope = "PERSONAL";

    /** 团队消费所属结伴行程（可空） */
    private Long tripId;

    /** 付款人（经手人） */
    private Long userId;

    /** 消费分类：TRANSPORT交通/HOTEL住宿/FOOD餐饮/TICKET门票/SHOPPING购物/OTHER其他 */
    private String category = "OTHER";

    /** 支出项目 */
    private String title;

    /** 金额（元） */
    private Double amount = 0.0;

    /** 分摊成员userId逗号串；为空表示个人自付 */
    @Column(length = 1000)
    private String sharers;

    /** 人均分摊金额 */
    private Double perPerson;

    /** 记录人 */
    private Long createdBy;

    @Column(length = 500)
    private String remark;

    private LocalDateTime createdAt;
}
