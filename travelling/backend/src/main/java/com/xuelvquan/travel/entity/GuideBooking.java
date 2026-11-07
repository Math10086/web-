package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "guide_bookings")
public class GuideBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long serviceId;
    private Long guideId;
    private Long travelerId;
    private LocalDate tripDate;
    private Double amount;

    @Column(columnDefinition = "TEXT")
    private String remark;

    /** 订单状态：PENDING待确认/PAID已支付/CONFIRMED已接单/DONE已完成/SETTLED已结算/CANCELLED已取消 */
    private String status = "PAID";

    private Double commissionRate = 0.1;
    private Double fee = 0.0;
    private Double guideIncome = 0.0;

    private Integer rating;
    @Column(columnDefinition = "TEXT")
    private String reviewContent;
    private LocalDateTime reviewedAt;

    /** 平台是否已向导游结算（结算后导游收入打款） */
    private Boolean settled = false;

    private LocalDateTime settledAt;

    private LocalDateTime createdAt;
}
