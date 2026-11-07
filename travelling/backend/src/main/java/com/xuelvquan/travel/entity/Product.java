package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long merchantId;
    private String name;
    private String category;
    private Double price;
    private Integer stock = 0;
    private Integer sales = 0;
    @Column(length = 1024)
    private String image;

    @Column(columnDefinition = "TEXT")
    private String description;

    private LocalDateTime createdAt;
}
