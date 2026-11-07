package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "trip_members")
public class TripMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long tripId;
    private Long userId;

    /** 报名状态：PENDING待审核/APPROVED已通过/REJECTED已拒绝 */
    private String status = "PENDING";

    @Column(columnDefinition = "TEXT")
    private String reason;

    private Integer creditSnapshot;

    private Double lng;
    private Double lat;
    private LocalDateTime locationAt;

    /** 行程结束后成员互评得分（1-5），存在被评价人的记录上 */
    private Integer score;
    @Column(columnDefinition = "TEXT")
    private String rateComment;

    private LocalDateTime createdAt;
}
