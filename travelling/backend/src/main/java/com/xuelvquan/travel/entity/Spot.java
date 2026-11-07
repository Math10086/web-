package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "spots")
public class Spot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    /** 景点分类：NATURE自然风光/HISTORY历史人文/TRENDY网红打卡/FOOD美食/SECRET小众秘境/THEME主题乐园 */
    private String category;

    private String city;
    private Double lng;
    private Double lat;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 1024)
    private String image;

    /** 库存的基础知名度（0~200，后台可维护），也是热度合成时的35% 基础分来源 */
    private Integer heat = 0;

    // ================= 以下为热度合成时填充的运行期字段，不落库 =================

    /** 基础知名度原值（未合成前的heat） */
    @Transient
    private Integer baseHeat;

    /** 加热前的原始行为加权分（已含时间衰减），用于解释"为什么热" */
    @Transient
    private Double heatRaw;

    @Transient
    private Integer heatFootprint;
    @Transient
    private Integer heatMention;
    @Transient
    private Integer heatFavorite;
    @Transient
    private Integer heatTrip;

    /** BEHAVIOR = 有真实行为支撑；NONE = 暂无行为 */
    @Transient
    private String heatSource;
}
