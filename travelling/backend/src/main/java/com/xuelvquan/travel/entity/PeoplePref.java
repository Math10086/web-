package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 人-人撮合偏好。
 *
 * 五维打分本身取自用户档案标签（users.profile_tags），这里只放"筛选口径"类的开关。
 */
@Data
@Entity
@Table(name = "people_prefs")
public class PeoplePref {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private Long userId;

    /**
     * 是否愿意认识陌生人：false（默认）时，半熟人度<70的同学不进推荐池。
     */
    private Boolean openToStranger = false;

    /** 是否接受跨校推荐，默认接受 */
    private Boolean crossSchool = true;

    private LocalDateTime updatedAt;
}
