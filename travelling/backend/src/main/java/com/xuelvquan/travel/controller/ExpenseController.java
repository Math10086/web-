package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.entity.Expense;
import com.xuelvquan.travel.security.AuthUtil;
import com.xuelvquan.travel.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 消费与费用管理接口
 */
@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    /**
     * 队长确认团队账目全部结清
     * @param tripId 行程ID
     * @return Result<Map<String,Object>> 结算结果（全队信誉分+3）
     */
    @PostMapping("/team/{tripId}/settle-confirm")
    public Result<Map<String, Object>> confirmSettled(@PathVariable Long tripId) {
        AuthUtil.requireBusiness();
        Long operatorId = AuthUtil.requireUserId();
        return Result.ok(expenseService.confirmSettled(tripId, operatorId));
    }

    /**
     * 记一笔消费
     * @param body 请求体，含金额、类别、行程等消费字段
     * @return Result<Expense> 新增的消费记录
     */
    @PostMapping
    public Result<Expense> add(@RequestBody Map<String, Object> body) {
        AuthUtil.requireBusiness();
        return Result.ok(expenseService.add(body));
    }

    /**
     * 个人消费报表
     * @param userId 用户ID，不传时按当前登录用户统计
     * @return Result<Map<String,Object>> 个人消费报表
     */
    @GetMapping("/personal")
    public Result<Map<String, Object>> personal(@RequestParam(required = false) Long userId) {
        AuthUtil.requireBusiness();
        return Result.ok(expenseService.personalReport(userId));
    }

    /** 我的团队消费报表列表 */
    @GetMapping("/my-teams")
    public Result<List<Map<String, Object>>> myTeams() {
        AuthUtil.requireBusiness();
        return Result.ok(expenseService.myTeams(null));
    }

    /**
     * 团队共享消费报表（含AA分摊结算）
     *
     * @param tripId 行程ID
     * @return Map 团队消费明细、分摊结果与人均金额
     */
    @GetMapping("/team/{tripId}")
    public Result<Map<String, Object>> team(@PathVariable Long tripId) {
        AuthUtil.requireBusiness();
        return Result.ok(expenseService.teamReport(tripId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        AuthUtil.requireBusiness();
        expenseService.delete(id);
        return Result.ok(null);
    }

    /** 当前登录用户id（前端默认报表对象） */
    @GetMapping("/whoami")
    public Result<Long> whoami() {
        AuthUtil.requireBusiness();
        return Result.ok(AuthUtil.requireUserId());
    }
}
