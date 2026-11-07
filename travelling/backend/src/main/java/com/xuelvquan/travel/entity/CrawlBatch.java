package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 采集批次日志（平台定时采集公开信息并标注来源与更新时间）。
 *
 * 每次采集（定时触发或手动触发）生成一条批次记录，用于说明"数据是怎么来的"：
 * 触发方式、每个采集器的数据源与命中模式（真实抓取/回落模板）、抓取与入库条数、耗时与失败原因。
 */
@Data
@Entity
@Table(name = "crawl_batches")
public class CrawlBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 批次号，如CB20261010143012 */
    @Column(length = 32)
    private String batchNo;

    /** 触发方式：SCHEDULED定时任务/MANUAL手动触发/STARTUP启动补采 */
    @Column(length = 16)
    private String triggerType;

    /** 批次状态：RUNNING/SUCCESS/PARTIAL/FAILED */
    @Column(length = 16)
    private String status;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    /** 总耗时（毫秒） */
    private Long durationMs;

    /** 采集器总数 */
    private Integer providerTotal;

    /** 真实抓取成功的采集器数 */
    private Integer realCount;

    /** 回落模板的采集器数 */
    private Integer fallbackCount;

    /** 抓取到的原始条数 */
    private Integer fetched;

    /** 新增入库条数 */
    private Integer inserted;

    /** 更新覆盖条数 */
    private Integer updated;

    /** 失败采集器数 */
    private Integer failed;

    /** 每个采集器的明细：来源 | 模式 | 抓取/新增/更新 | 耗时 | 备注，逐行拼接 */
    @Column(columnDefinition = "TEXT")
    private String detail;

    /** 批次概述 */
    @Column(columnDefinition = "TEXT")
    private String message;
}
