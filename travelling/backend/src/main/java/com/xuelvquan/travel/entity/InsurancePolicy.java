package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "insurance_policies")
public class InsurancePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long tripId;
    private String planName;
    private String policyNo;

    /** 保单状态：EFFECTIVE生效中/EXPIRED已过期 */
    private String status = "EFFECTIVE";

    private LocalDate coverageEnd;
    private LocalDateTime createdAt;
}
