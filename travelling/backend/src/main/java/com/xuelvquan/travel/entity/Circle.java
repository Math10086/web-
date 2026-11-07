package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "circles")
public class Circle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String theme;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String icon;
    private Long creatorId;
    private Integer memberCount = 0;
    private LocalDateTime createdAt;
}
