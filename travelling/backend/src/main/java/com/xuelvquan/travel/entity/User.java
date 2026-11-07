package com.xuelvquan.travel.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private String nickname;
    @Column(length = 1024)
    private String avatar;

    /** 所在学校：CSU中南大学/HUNNU湖南师范大学/HNU湖南大学 */
    private String school;
    private String studentNo;
    private String realName;

    private Boolean verified = false;

    // ===== B6：认证闭环与头像信息卡（学院 · 专业 · 届别）=====

    /** 学院 */
    private String college;
    /** 专业 */
    private String major;
    /** 届别（入学年份，如2023） */
    private String grade;
    /** 认证方式：STUDENT_NO学号格式校验 */
    private String verifyMethod;
    private LocalDateTime verifiedAt;

    // ===== B0：标签体系v1 =====

    /**
     * 档案标签，格式为dimension:code逗号串，
     * 如pace:BALANCED,interest:PHOTO,interest:FOOD,budget:MODERATE,destination:CITY。
     * 字典见tag_dict表（/api/tags/dict）。
     */
    @Column(length = 1024)
    private String profileTags;

    /** 用户角色：USER普通用户/BLOGGER博主/MERCHANT商家/GUIDE导游/ADMIN管理员 */
    private String role = "USER";

    private Integer creditScore = 100;
    private Integer bloggerLevel = 0;
    private Integer bloggerScore = 0;

    /** 兴趣标签逗号串 */
    private String interests;
    private String bio;
    private Boolean banned = false;

    private LocalDateTime createdAt;
}
