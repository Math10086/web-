package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "product_orders")
public class ProductOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long merchantId;
    private Double amount;

    /** PAID/SHIPPED/DONE/CANCELLED（下单即PAID模拟支付） */
    private String status = "PAID";

    private String receiver;
    private LocalDateTime createdAt;

    /** 平台是否已与商家结算（结算后平台佣金入账、货款打给商家） */
    private Boolean settled = false;

    private LocalDateTime settledAt;
}
