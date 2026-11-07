package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.security.AuthUtil;
import com.xuelvquan.travel.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 平台运营数据接口。
 *
 * 权限：平台运营指标（注册用户、交易额、待审内容等）属管理员专属，
 * 只在「管理后台 → 数据看板」内展示；其中「社区热搜/热词」是首页对游客公开的社区内容，
 * 单独保留公开访问。
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    /** 管理员专属：后台数据看板 */
    private void requireAdmin() {
        AuthUtil.requireRole("ADMIN");
    }

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        requireAdmin();
        return Result.ok(statsService.overview());
    }

    @GetMapping("/spot-heat")
    public Result<Map<String, Object>> spotHeat() {
        requireAdmin();
        return Result.ok(statsService.spotHeat());
    }

    @GetMapping("/circle-activity")
    public Result<List<Map<String, Object>>> circleActivity() {
        requireAdmin();
        return Result.ok(statsService.circleActivity());
    }

    @GetMapping("/post-trend")
    public Result<List<Map<String, Object>>> postTrend() {
        requireAdmin();
        return Result.ok(statsService.postTrend());
    }

    /** 公开：首页「论坛热搜榜」标签（社区内容，游览即可见） */
    @GetMapping("/hot-search")
    public Result<List<Map<String, Object>>> hotSearch() {
        // 首页热搜属C端前台内容：管理员与商家不参与
        AuthUtil.requireFrontOrAdmin();
        return Result.ok(statsService.hotSearch());
    }

    /** 公开：首页词云与社区热词（社区内容） */
    @GetMapping("/hot-words")
    public Result<List<Map<String, Object>>> hotWords() {
        AuthUtil.requireFrontOrAdmin();
        return Result.ok(statsService.hotWords());
    }

    @GetMapping("/city-activity")
    public Result<List<Map<String, Object>>> cityActivity() {
        requireAdmin();
        return Result.ok(statsService.cityActivity());
    }

    @GetMapping("/spot-visits")
    public Result<List<Map<String, Object>>> spotVisits() {
        requireAdmin();
        return Result.ok(statsService.spotVisits());
    }

    @GetMapping("/route-ranking")
    public Result<Map<String, Object>> routeRanking() {
        requireAdmin();
        return Result.ok(statsService.routeRanking());
    }
}
