package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "guide_services")
public class GuideService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long guideId;
    private String title;
    private String destination;
    private String themes;
    private Double price;
    private String schedule;

    @Column(columnDefinition = "TEXT")
    private String intro;

    /** 上架状态：PENDING待审核/APPROVED已上架/REJECTED已驳回 */
    private String status = "APPROVED";

    private LocalDateTime createdAt;
}
