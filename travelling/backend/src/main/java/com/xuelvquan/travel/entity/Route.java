package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "routes")
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long authorId;
    private String name;
    private String city;

    /** 规划模式：OPTIMAL综合最优/SPEED省时/LEISURE休闲/FOOD美食/SCENIC观景/WILD野趣 */
    private String mode;
    private String color;

    /** 景点id逗号串（按规划顺序） */
    private String spotIdsCsv;

    private Double totalDistance;
    private Integer totalDuration;

    /** 强度等级：LOW轻松/MIDDLE适中/HIGH高强度 */
    private String riskLevel;

    @Column(columnDefinition = "TEXT")
    private String tips;

    private LocalDateTime createdAt;
}
