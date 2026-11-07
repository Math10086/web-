package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.entity.CrawlBatch;
import com.xuelvquan.travel.entity.ScrapedInfo;
import com.xuelvquan.travel.security.AuthUtil;
import com.xuelvquan.travel.service.CrawlService;
import com.xuelvquan.travel.service.ScrapedInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据采集与"只看不订"信息浏览接口
 *
 * 权限：信息浏览对游客开放（公开部分）；触发采集/查看采集日志为
 * 规划决策能力，仅学生用户、博主、导游可用（商家与管理员不参与）。
 */
@RestController
@RequestMapping("/api/scraped")
@RequiredArgsConstructor
public class ScrapedInfoController {

    private final ScrapedInfoService scrapedInfoService;
    private final CrawlService crawlService;

    /** 采集信息浏览（只看不订）：type = TRAIN/FLIGHT/HOTEL/TICKET，origin仅对车次/航班有意义 */
    @GetMapping
    public Result<List<ScrapedInfo>> list(@RequestParam(required = false) String type,
                                          @RequestParam(required = false) String destination,
                                          @RequestParam(required = false) String origin) {
        return Result.ok(scrapedInfoService.list(type, destination, origin));
    }

    /** 已采集数据覆盖的目的地城市 */
    @GetMapping("/cities")
    public Result<List<String>> cities() {
        return Result.ok(scrapedInfoService.cities());
    }

    /** 已采集数据覆盖的出发地城市（车次/航班） */
    @GetMapping("/origins")
    public Result<List<String>> origins() {
        return Result.ok(scrapedInfoService.origins());
    }

    /** 全程费用成本估算 */
    @GetMapping("/cost-estimate")
    public Result<Map<String, Object>> costEstimate(@RequestParam(required = false) String destination,
                                                    @RequestParam(defaultValue = "3") int days,
                                                    @RequestParam(required = false) String budgetLevel,
                                                    @RequestParam(required = false) String origin) {
        return Result.ok(scrapedInfoService.costEstimate(destination, days, budgetLevel, origin));
    }

    /** 模拟定时采集：刷新数据更新时间 */
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh() {
        int count = scrapedInfoService.refresh();
        return Result.ok(Map.of("refreshed", count, "message", "已模拟采集刷新" + count + " 条公开信息"));
    }

    /**
     * 手动触发一次真实采集：调用全部采集器抓取公开信息并upsert入库，
     * 返回本次采集批次日志（含每个采集器的数据来源、命中模式与条数）。
     */
    @PostMapping("/crawl")
    public Result<CrawlBatch> crawl() {
        AuthUtil.requireBusiness();
        return Result.ok(crawlService.crawl("MANUAL"));
    }

    /** 采集批次日志：说明数据来源与更新时间（定时/手动/启动触发） */
    @GetMapping("/crawl/batches")
    public Result<List<CrawlBatch>> crawlBatches() {
        AuthUtil.requireBusiness();
        return Result.ok(crawlService.recentBatches());
    }

    /**
     * 定向采集：只抓「出发地 → 目的地」这一条线路的公开信息（12306车次）。
     * 面向用户手输的任意城市——定点采集不局限于内置城市池，抓完立即可在列表里查到。
     */
    @PostMapping("/crawl-route")
    public Result<CrawlBatch> crawlRoute(@RequestParam(required = false) String origin,
                                        @RequestParam(required = false) String destination) {
        AuthUtil.requireBusiness();
        return Result.ok(crawlService.crawlRoute(origin, destination, "MANUAL"));
    }
}
