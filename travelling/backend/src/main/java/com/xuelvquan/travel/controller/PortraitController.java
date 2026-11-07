package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.BusinessException;
import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.security.AuthUtil;
import com.xuelvquan.travel.service.PortraitService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 个人出行画像接口
 *
 * 画像页把用户自身行为数据可视化地呈现给用户；对外仅输出脱敏后的群体统计。
 */
@RestController
@RequestMapping("/api/portrait")
@RequiredArgsConstructor
public class PortraitController {

    private final PortraitService portraitService;

    /** 我的出行画像（本人视角） */
    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        return Result.ok(portraitService.portrait(AuthUtil.requireUserId()));
    }

    /**
     * 指定用户的画像（仅本人或管理员可看）
     * @param userId 用户ID
     * @return Result<Map<String,Object>> 出行画像数据
     */
    @GetMapping("/users/{userId}")
    public Result<Map<String, Object>> of(@PathVariable Long userId) {
        Long me = AuthUtil.requireUserId();
        if (!me.equals(userId)) {
            AuthUtil.requireRole("ADMIN");
        }
        return Result.ok(portraitService.portrait(userId));
    }
}
