package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "interactions")
public class Interaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    /** target统一为post */
    private Long targetId;

    /** 互动类型：LIKE点赞/FAVORITE收藏 */
    private String type;

    private LocalDateTime createdAt;
}
