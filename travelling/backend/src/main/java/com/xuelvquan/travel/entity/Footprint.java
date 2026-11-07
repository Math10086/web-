package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "footprints")
public class Footprint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long spotId;
    private String name;
    private String city;
    private Double lng;
    private Double lat;
    private LocalDate visitedAt;
    @Column(length = 1024)
    private String photo;

    @Column(columnDefinition = "TEXT")
    private String note;

    private LocalDateTime createdAt;
}
