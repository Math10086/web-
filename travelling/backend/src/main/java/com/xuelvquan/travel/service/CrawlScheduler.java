package com.xuelvquan.travel.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 采集定时调度（平台定时采集公开信息并标注来源与更新时间）。
 *
 * 默认每天03:30执行一次全量采集，cron与开关可在application.yml的xlvq.crawl下调整。
 * 采集过程与结果全部落在采集批次日志中，可在"信息浏览"页查看。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlScheduler {

    private final CrawlService crawlService;

    @Value("${xlvq.crawl.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${xlvq.crawl.cron:0 30 3 * * ?}")
    public void scheduledCrawl() {
        if (!enabled) {
            log.info("[学旅圈] 定时采集已关闭（xlvq.crawl.enabled=false），跳过");
            return;
        }
        log.info("[学旅圈] 定时采集任务启动...");
        try {
            crawlService.crawl("SCHEDULED");
        } catch (Exception e) {
            log.error("[学旅圈] 定时采集任务异常：{}", e.getMessage());
        }
    }
}
