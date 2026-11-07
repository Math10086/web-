package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 采集信息（数据采集与信息浏览/数据模型ScrapedInfo）。
 *
 * 受限于无法对接真实车票/酒店/门票预订系统，平台以定时采集公开基本信息的方式，
 * 结构化入库并标注来源与更新时间，供"只看不订"信息浏览页与全程费用估算使用。
 * 本实体即采集结果的结构化落库形态。
 */
@Data
@Entity
@Table(name = "scraped_infos")
public class ScrapedInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** TRAIN车次/FLIGHT航班/HOTEL酒店/TICKET门票 */
    private String type;

    /** 目的地城市 */
    private String destination;

    /** 出发地城市（车次/航班适用） */
    private String origin;

    /** 名称：车次号/航班号/酒店名/门票名 */
    private String name;

    /** 班次或时段：如09:20-13:21、每日06:30-18:00 */
    private String schedule;

    /** 参考价下限（元） */
    private Double priceMin;

    /** 参考价上限（元） */
    private Double priceMax;

    /** 评分（酒店/门票） */
    private Double rating;

    /** 采集来源，如12306/携程/景区官网 */
    private String source;

    /** 数据更新时间（采集时间） */
    private LocalDateTime updatedAt;

    @Column(columnDefinition = "TEXT")
    private String note;
}
