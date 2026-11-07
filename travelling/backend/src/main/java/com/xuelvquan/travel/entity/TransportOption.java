package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "transport_options")
public class TransportOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 出发城市，例如 长沙 */
    private String origin;

    /** 目的城市，例如 张家界 */
    private String destination;

    /** 交通方式：TRAIN火车/BUS大巴/CAR自驾/CARPOOL拼车/BIKE骑行 */
    private String mode;

    private String duration;
    private String price;
    private String frequency;

    private Integer comfort;
    private Integer freedom;
    private Integer safety;

    @Column(columnDefinition = "TEXT")
    private String note;
}
