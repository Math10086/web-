package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.security.AuthUtil;
import com.xuelvquan.travel.service.CreatorInsightService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 创作者中心 · 博客数据面板。
 *
 * 与「需求洞察」（商家/导游看）方向相反：这里的数据只回给博主<b>本人</b>，
 * 不涉及其他用户的任何明细，统计口径全部来自帖子自身的互动计数。
 *
 * 受众限定为<b>内容创作者</b>：BLOGGER与GUIDE（导游本身包含博主能力）。
 * 普通学生用户请先在个人中心「申请成为博主」，否则这里没有可看的经营数据。
 */
@RestController
@RequestMapping("/api/creator")
@RequiredArgsConstructor
public class CreatorInsightController {

    private final CreatorInsightService creatorInsightService;

    /** 我的创作数据：博客分/等级/排名+逐篇表现+月度趋势+结论+选题建议 */
    @GetMapping("/insight")
    public Result<Map<String, Object>> insight() {
        AuthUtil.requireRole("BLOGGER", "GUIDE");
        Long userId = AuthUtil.requireUserId();
        return Result.ok(creatorInsightService.insight(userId));
    }
}
