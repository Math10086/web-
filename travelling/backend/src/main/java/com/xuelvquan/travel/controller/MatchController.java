package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.BusinessException;
import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.entity.PeoplePref;
import com.xuelvquan.travel.security.AuthUtil;
import com.xuelvquan.travel.service.PeopleMatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 半熟人撮合与契合度推荐接口
 */
@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
public class MatchController {

    private final PeopleMatchService peopleMatchService;

    /**
     * 给我推荐契合度最高的N个人（默认10）
     * @param limit 返回人数，限制在1~50
     * @return Result<Map<String,Object>> 推荐列表、撮合池状态与当前偏好
     */
    @GetMapping("/people")
    public Result<Map<String, Object>> people(@RequestParam(defaultValue = "10") int limit) {
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        int n = Math.max(1, Math.min(50, limit));
        List<Map<String, Object>> list = peopleMatchService.recommend(userId, n);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("list", list);
        m.put("total", list.size());
        m.put("poolStatus", peopleMatchService.poolStatus(userId));
        m.put("pref", peopleMatchService.resolvePref(userId));
        return Result.ok(m);
    }

    /**
     * 我与指定用户的契合度明细（含逐维解释）
     * @param targetId 目标用户ID
     * @return Result<Map<String,Object>> 契合度明细
     */
    @GetMapping("/people/{targetId}")
    public Result<Map<String, Object>> detail(@PathVariable Long targetId) {
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        if (userId.equals(targetId)) {
            throw new BusinessException("不能与自己匹配");
        }
        Map<String, Object> m = peopleMatchService.detail(userId, targetId);
        if (m == null) {
            throw new BusinessException("用户不存在");
        }
        return Result.ok(m);
    }

    /** 我的撮合筛选偏好 */
    @GetMapping("/pref")
    public Result<PeoplePref> pref() {
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        return Result.ok(peopleMatchService.resolvePref(userId));
    }

    /**
     * 更新撮合筛选偏好（是否接受陌生人/是否接受跨校）
     * @param body 请求体，含偏好开关字段
     * @return Result<PeoplePref> 保存后的偏好
     */
    @PutMapping("/pref")
    public Result<PeoplePref> savePref(@RequestBody Map<String, Object> body) {
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        return Result.ok(peopleMatchService.savePref(userId, body));
    }

    /** 我的档案是否已填满核心标签（未填满不进推荐池） */
    @GetMapping("/pool-status")
    public Result<Map<String, Object>> poolStatus() {
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        return Result.ok(peopleMatchService.poolStatus(userId));
    }
}
