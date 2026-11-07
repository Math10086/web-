package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.security.AuthUtil;
import com.xuelvquan.travel.service.SafetyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 安全机制接口：家校知情通知+位置共享
 *
 * 权限：家校知情/位置共享属结伴安全能力，仅学生用户、博主、导游可用；
 * 商家与管理员不参与。注：GET/track/{token} 为公开接口，供家长/辅导员凭授权链接
 * 直接查看，无需登录。
 */
@RestController
@RequestMapping("/api/safety")
@RequiredArgsConstructor
public class SafetyController {

    private final SafetyService safetyService;

    // ---------------- 家校知情 ----------------

    /**
     * 出行前一键通知全队成员的家长与辅导员（仅队长）
     * @param body 请求体，含tripId（行程ID）
     * @return Result<Map<String,Object>> 通知结果
     */
    @PostMapping("/notify")
    public Result<Map<String, Object>> notifyDeparture(@RequestBody Map<String, Object> body) {
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        Long tripId = toLong(body.get("tripId"));
        return Result.ok(safetyService.notifyDeparture(tripId, userId));
    }

    /**
     * 某行程的家校知情通知记录
     * @param tripId 行程ID
     * @return Result<List<Map<String,Object>>> 通知记录列表
     */
    @GetMapping("/notices")
    public Result<List<Map<String, Object>>> notices(@RequestParam Long tripId) {
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        return Result.ok(safetyService.noticesOfTrip(tripId, userId));
    }

    /** 我收到的（作为学生的）家校知情通知 */
    @GetMapping("/notices/mine")
    public Result<List<Map<String, Object>>> myNotices() {
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        return Result.ok(safetyService.myNotices(userId));
    }

    // ---------------- 位置共享 ----------------

    /**
     * 创建/续期一条位置共享授权，返回家长可打开的链接
     * @param body 请求体，含tripId、contactId、hours、includeTeammates、note
     * @return Result<Map<String,Object>> 含授权链接的共享信息
     */
    @PostMapping("/shares")
    public Result<Map<String, Object>> createShare(@RequestBody Map<String, Object> body) {
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        return Result.ok(safetyService.createShare(
                userId,
                toLong(body.get("tripId")),
                toLong(body.get("contactId")),
                toInt(body.get("hours")),
                body.get("includeTeammates") == null ? null : toBool(body.get("includeTeammates")),
                body.get("note") == null ? null : String.valueOf(body.get("note"))));
    }

    /**
     * 我的位置共享授权列表
     * @param tripId 行程ID，可不传表示不限行程
     * @return Result<List<Map<String,Object>>> 授权列表
     */
    @GetMapping("/shares")
    public Result<List<Map<String, Object>>> shares(@RequestParam(required = false) Long tripId) {
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        return Result.ok(safetyService.myShares(userId, tripId));
    }

    /**
     * 撤销授权
     * @param id 授权ID
     * @return Result<Map<String,Object>> 撤销后的授权信息
     */
    @PostMapping("/shares/{id}/revoke")
    public Result<Map<String, Object>> revoke(@PathVariable Long id) {
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        return Result.ok(safetyService.revoke(id, userId));
    }

    /**
     * 家长/辅导员查看页数据源（公开，凭viewToken）
     * @param token 查看凭证
     * @return Result<Map<String,Object>> 位置共享详情
     */
    @GetMapping("/track/{token}")
    public Result<Map<String, Object>> track(@PathVariable String token) {
        return Result.ok(safetyService.track(token));
    }

    // ---------------- 参数转换 ----------------

    private Long toLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : Long.valueOf(s);
    }

    private Integer toInt(Object v) {
        Long l = toLong(v);
        return l == null ? null : l.intValue();
    }

    private boolean toBool(Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(v));
    }
}
