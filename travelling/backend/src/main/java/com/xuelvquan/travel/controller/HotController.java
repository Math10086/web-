package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.service.HotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 热度中心接口
 */
@RestController
@RequestMapping("/api/hot")
@RequiredArgsConstructor
public class HotController {

    private final HotService hotService;

    /**
     * 前置校验：仅前台用户与游客可访问热度接口
     */
    @ModelAttribute
    public void guardFrontOnly() {
        com.xuelvquan.travel.security.AuthUtil.requireFrontOrGuest();
    }

    /** 热帖榜 */
    @GetMapping("/posts")
    public Result<List<Map<String, Object>>> posts(@RequestParam(defaultValue = "10") int limit) {
        return Result.ok(hotService.hotPosts(limit));
    }

    /** 词云/热搜词 */
    @GetMapping("/keywords")
    public Result<List<Map<String, Object>>> keywords(@RequestParam(defaultValue = "40") int limit) {
        return Result.ok(hotService.keywords(limit));
    }

    /** 热门目的地城市榜 */
    @GetMapping("/cities")
    public Result<List<Map<String, Object>>> cities(@RequestParam(defaultValue = "12") int limit) {
        return Result.ok(hotService.hotCities(limit));
    }

    /** 博主评级榜+精品推荐位 */
    @GetMapping("/bloggers")
    public Result<Map<String, Object>> bloggers(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(hotService.bloggers(limit));
    }
}
