package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "credit_logs")
public class CreditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Integer delta;

    @Column(columnDefinition = "TEXT")
    private String reason;

    private LocalDateTime createdAt;
}
