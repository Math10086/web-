package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.security.AuthUtil;
import com.xuelvquan.travel.service.DemandInsightService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 需求洞察接口（商家/导游端经营参考）
 *
 * 与/api/insight面向学生与游客的公开看板使用同一份聚合数据：本接口从供给视角回答
 * "哪里火但没人供货"，商家看供需缺口与定价参考，导游看哪些城市还没有向导服务。
 */
@RestController
@RequestMapping("/api/demand")
@RequiredArgsConstructor
public class DemandInsightController {

    private final DemandInsightService demandInsightService;

    /**
     * 需求洞察总览：供需缺口榜+出行方式占比+预算分布+主题需求。
     * 商家与导游看到的「供给口径」不同（商品品类/向导服务），故按角色分别计算。
     */
    @GetMapping("/insight")
    public Result<Map<String, Object>> insight() {
        String role = AuthUtil.requireBusinessPartner();
        return Result.ok(demandInsightService.insight(role));
    }
}
