package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.security.AuthUtil;
import com.xuelvquan.travel.service.CreditService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 信誉分体系与信誉画像接口
 */
@RestController
@RequestMapping("/api/credit")
@RequiredArgsConstructor
public class CreditController {

    private final CreditService creditService;

    /** 我的信誉主页 */
    @GetMapping("/profile")
    public Result<Map<String, Object>> profile() {
        Long userId = AuthUtil.requireUserId();
        return Result.ok(creditService.profile(userId));
    }

    /**
     * 指定用户信誉概要（头像卡/成员列表展示用，学号脱敏）
     * @param userId 用户ID
     * @return Result<Map<String,Object>> 信誉概要
     */
    @GetMapping("/users/{userId}")
    public Result<Map<String, Object>> brief(@PathVariable Long userId) {
        AuthUtil.requireUserId();
        return Result.ok(creditService.brief(userId));
    }
}
