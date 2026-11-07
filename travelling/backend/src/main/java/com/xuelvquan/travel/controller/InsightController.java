package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.security.AuthUtil;
import com.xuelvquan.travel.service.InsightService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 匿名聚合洞察公开看板接口
 */
@RestController
@RequestMapping("/api/insight")
@RequiredArgsConstructor
public class InsightController {

    private final InsightService insightService;

    /** 看板概览：门槛、数据量、脱敏机制说明 */
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        AuthUtil.requireFrontOrGuest();
        return Result.ok(insightService.summary());
    }

    /** 目的地热度指数（公开脱敏聚合） */
    @GetMapping("/destination-heat")
    public Result<Map<String, Object>> destinationHeat() {
        AuthUtil.requireFrontOrGuest();
        return Result.ok(insightService.destinationHeat());
    }

    /** 学生消费画像（公开脱敏聚合） */
    @GetMapping("/consumption-profile")
    public Result<Map<String, Object>> consumptionProfile() {
        AuthUtil.requireFrontOrGuest();
        return Result.ok(insightService.consumptionProfile());
    }

    /** 兴趣—目的地—消费 桑基流转（公开脱敏聚合） */
    @GetMapping("/interest-flow")
    public Result<Map<String, Object>> interestFlow() {
        AuthUtil.requireFrontOrGuest();
        return Result.ok(insightService.interestFlow());
    }

    /** 导游三层供给体系（公开脱敏聚合） */
    @GetMapping("/supply-pyramid")
    public Result<Map<String, Object>> supplyPyramid() {
        AuthUtil.requireFrontOrGuest();
        return Result.ok(insightService.supplyPyramid());
    }
}
