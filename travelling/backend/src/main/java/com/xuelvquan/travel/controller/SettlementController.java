package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.entity.Settlement;
import com.xuelvquan.travel.security.AuthUtil;
import com.xuelvquan.travel.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 平台结算（管理员后台能力）：商城货款与向导收入的结算概览与发放，仅ADMIN可访问。
 */
@RestController
@RequestMapping("/api/admin/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    /** 结算总览：待结算（商城按商家/向导按导游）+ 已发放记录 */
    @GetMapping
    public Result<Map<String, Object>> overview() {
        AuthUtil.requireRole("ADMIN");
        return Result.ok(settlementService.overview());
    }

    /**
     * 执行一次结算发放：body = {type: 'SHOP'|'GUIDE', targetId}
     * @param body 请求体，含type（SHOP/GUIDE）与targetId（商家或导游ID）
     * @return Result<Settlement> 本次结算记录
     */
    @PostMapping("/payout")
    public Result<Settlement> payout(@RequestBody Map<String, Object> body) {
        AuthUtil.requireRole("ADMIN");
        Long operatorId = AuthUtil.requireUserId();
        String type = (String) body.get("type");
        Long targetId = body.get("targetId") == null
                ? null : Long.valueOf(String.valueOf(body.get("targetId")));
        return Result.ok(settlementService.payout(type, targetId, operatorId));
    }
}
