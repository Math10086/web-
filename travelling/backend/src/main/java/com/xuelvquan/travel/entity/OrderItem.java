package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;
    private Long productId;
    private String name;
    private Double price;
    private Integer quantity;
    @Column(length = 1024)
    private String image;
}
