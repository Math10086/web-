package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.entity.DynamicPlan;
import com.xuelvquan.travel.security.AuthUtil;
import com.xuelvquan.travel.service.PlanAdjustService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 动态行程调整接口
 */
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanAdjustController {

    private final PlanAdjustService planAdjustService;

    /**
     * 生成动态调整方案（接入天气，给出调整后行程与理由，可通知同行成员）
     * @param body 请求体，含行程与触发调整的信息
     * @return Result<Map<String,Object>> 调整后的行程与理由
     */
    @PostMapping("/adjust")
    public Result<Map<String, Object>> adjust(@RequestBody Map<String, Object> body) {
        AuthUtil.requireBusiness();
        return Result.ok(planAdjustService.adjust(body));
    }

    /** 我的动态调整历史 */
    @GetMapping("/mine")
    public Result<List<DynamicPlan>> mine() {
        AuthUtil.requireBusiness();
        return Result.ok(planAdjustService.myPlans());
    }
}
