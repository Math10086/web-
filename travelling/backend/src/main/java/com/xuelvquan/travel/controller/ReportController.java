package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.BusinessException;
import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.entity.Report;
import com.xuelvquan.travel.repository.ReportRepository;
import com.xuelvquan.travel.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private static final Set<String> TARGET_TYPES = Set.of("POST", "USER", "TRIP", "PRODUCT", "GUIDE");

    private final ReportRepository reportRepository;

    @PostMapping
    public Result<Report> create(@RequestBody Map<String, Object> body) {
        // 仅非管理员用户可提交举报
        AuthUtil.requireNonAdmin();
        Long userId = AuthUtil.requireUserId();
        String targetType = (String) body.get("targetType");
        if (targetType == null || !TARGET_TYPES.contains(targetType)) {
            throw new BusinessException("举报对象类型非法");
        }
        Report r = new Report();
        r.setReporterId(userId);
        r.setTargetType(targetType);
        r.setTargetId(Long.valueOf(String.valueOf(body.get("targetId"))));
        r.setReason((String) body.get("reason"));
        r.setEvidence((String) body.get("evidence"));
        r.setStatus("PENDING");
        r.setCreatedAt(LocalDateTime.now());
        if (r.getReason() == null || r.getReason().isBlank()) {
            throw new BusinessException("举报原因不能为空");
        }
        return Result.ok(reportRepository.save(r));
    }

    @GetMapping
    public Result<List<Report>> list() {
        AuthUtil.requireRole("ADMIN");
        return Result.ok(reportRepository.findAllByOrderByCreatedAtDesc());
    }

    @PostMapping("/{id}/handle")
    public Result<Report> handle(@PathVariable Long id, @RequestBody Map<String, String> body) {
        AuthUtil.requireRole("ADMIN");
        Report r = reportRepository.findById(id)
                .orElseThrow(() -> new BusinessException("举报不存在"));
        r.setStatus("HANDLED");
        r.setResult(body.get("result"));
        return Result.ok(reportRepository.save(r));
    }
}
