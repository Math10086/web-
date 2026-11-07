package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.BusinessException;
import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.entity.Trip;
import com.xuelvquan.travel.entity.TripComplaint;
import com.xuelvquan.travel.repository.TripComplaintRepository;
import com.xuelvquan.travel.repository.TripMemberRepository;
import com.xuelvquan.travel.repository.TripRepository;
import com.xuelvquan.travel.repository.UserRepository;
import com.xuelvquan.travel.security.AuthUtil;
import com.xuelvquan.travel.service.CreditService;
import com.xuelvquan.travel.service.NotifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 同行人信誉投诉控制器：发起投诉、查看、申诉与管理员复核
 */
@RestController
@RequiredArgsConstructor
public class TripComplaintController {

    private final TripComplaintRepository complaintRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final CreditService creditService;
    private final NotifyService notifyService;

    /** 投诉类型的固定顺序，用于分布图展示 */
    private static final List<String> TYPE_ORDER =
            List.of("NO_SHOW", "LOST_CONTACT", "MISBEHAVE", "EXPENSE_DISPUTE");

    private static final Set<String> TYPES = Set.copyOf(TYPE_ORDER);

    // ==================== 发起与查看 ====================

    /**
     * 对某行程的队友发起投诉
     * @param tripId 行程ID
     * @param body 投诉内容（targetId、type、description、evidence）
     * @return Result<Map<String,Object>> 投诉记录视图
     */
    @PostMapping("/api/trips/{tripId}/complaints")
    public Result<Map<String, Object>> create(@PathVariable Long tripId,
                                        @RequestBody Map<String, Object> body) {
        Long reporterId = AuthUtil.requireUserId();
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException("行程不存在"));
        if (!"FINISHED".equals(trip.getStatus()) && !"ONGOING".equals(trip.getStatus())) {
            throw new BusinessException("仅进行中或已结束的行程可以发起投诉");
        }
        if (!creditService.isParticipant(trip, reporterId, memberRepository)) {
            throw new BusinessException(403, "仅行程成员可以发起投诉");
        }
        Long targetId = body.get("targetId") == null ? null
                : Long.valueOf(String.valueOf(body.get("targetId")));
        if (targetId == null) {
            throw new BusinessException("请选择被投诉人");
        }
        if (targetId.equals(reporterId)) {
            throw new BusinessException("不能投诉自己");
        }
        if (!creditService.isParticipant(trip, targetId, memberRepository)) {
            throw new BusinessException(403, "被投诉人不是该行程成员");
        }
        String type = String.valueOf(body.getOrDefault("type", ""));
        if (!TYPES.contains(type)) {
            throw new BusinessException("投诉类型必须为 NO_SHOW / LOST_CONTACT / MISBEHAVE / EXPENSE_DISPUTE");
        }
        if (complaintRepository.existsByTripIdAndReporterIdAndTargetId(tripId, reporterId, targetId)) {
            throw new BusinessException("你在该行程中已投诉过这位成员");
        }

        TripComplaint c = new TripComplaint();
        c.setTripId(tripId);
        c.setReporterId(reporterId);
        c.setTargetId(targetId);
        c.setType(type);
        c.setDescription(body.get("description") == null ? null : String.valueOf(body.get("description")));
        c.setEvidence(body.get("evidence") == null ? null : String.valueOf(body.get("evidence")));
        c.setStatus("PENDING");
        c.setCreatedAt(LocalDateTime.now());
        TripComplaint saved = complaintRepository.save(c);

        notifyService.notify(targetId, "TRIP", "你被队友投诉",
                "关于行程「" + trip.getTitle() + "」的「" + CreditService.complaintTypeName(type)
                        + "」投诉已提交，待管理员核实", tripId);
        return Result.ok(view(saved));
    }

    /**
     * 某行程的投诉列表
     * @param tripId 行程ID
     * @return Result<List<Map<String,Object>>> 投诉列表（行程参与者可见）
     */
    @GetMapping("/api/trips/{tripId}/complaints")
    public Result<List<Map<String, Object>>> listByTrip(@PathVariable Long tripId) {
        Long userId = AuthUtil.requireUserId();
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException("行程不存在"));
        if (!creditService.isParticipant(trip, userId, memberRepository)) {
            throw new BusinessException(403, "仅行程成员可以查看投诉记录");
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (TripComplaint c : complaintRepository.findByTripIdOrderByCreatedAtDesc(tripId)) {
            list.add(view(c));
        }
        return Result.ok(list);
    }

    /**
     * 我的投诉：我发起的与被投诉的
     * @return Result<Map<String,Object>> filed 为我发起，received为我被投诉
     */
    @GetMapping("/api/credit/complaints/mine")
    public Result<Map<String, Object>> mine() {
        Long userId = AuthUtil.requireUserId();
        List<Map<String, Object>> filed = new ArrayList<>();
        for (TripComplaint c : complaintRepository.findByReporterIdOrderByCreatedAtDesc(userId)) {
            filed.add(view(c));
        }
        List<Map<String, Object>> received = new ArrayList<>();
        for (TripComplaint c : complaintRepository.findByTargetIdOrderByCreatedAtDesc(userId)) {
            received.add(view(c));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("filed", filed);
        m.put("received", received);
        return Result.ok(m);
    }

    // ==================== 申诉与处理 ====================

    /**
     * 被投诉人对已核实结果发起申诉
     * @param id 投诉记录ID
     * @param body 申诉理由
     * @return Result<Map<String,Object>> 投诉记录视图
     */
    @PostMapping("/api/credit/complaints/{id}/appeal")
    public Result<Map<String, Object>> appeal(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Long userId = AuthUtil.requireUserId();
        TripComplaint c = mustGet(id);
        if (!c.getTargetId().equals(userId)) {
            throw new BusinessException(403, "仅被投诉人可以申诉");
        }
        if (!"RESOLVED".equals(c.getStatus())) {
            throw new BusinessException("仅被核实属实的投诉可以申诉");
        }
        String reason = body.get("reason");
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("请填写申诉理由");
        }
        c.setStatus("APPEALED");
        c.setAppealReason(reason);
        c.setAppealAt(LocalDateTime.now());
        return Result.ok(view(complaintRepository.save(c)));
    }

    /**
     * 管理员处理投诉
     * @param id 投诉记录ID
     * @param body 处理动作与结果（action、result）
     * @return Result<Map<String,Object>> 投诉记录视图
     */
    @PostMapping("/api/credit/complaints/{id}/handle")
    public Result<Map<String, Object>> handle(@PathVariable Long id, @RequestBody Map<String, String> body) {
        AuthUtil.requireRole("ADMIN");
        Long operatorId = AuthUtil.requireUserId();
        TripComplaint c = mustGet(id);
        String action = body.get("action");
        String result = body.get("result");

        switch (action == null ? "" : action) {
            case "RESOLVED" -> {
                if (!"PENDING".equals(c.getStatus())) {
                    throw new BusinessException("该投诉已在处理中或已处理");
                }
                int penalty = creditService.penaltyFor(c.getType());
                creditService.addCredit(c.getTargetId(), -penalty,
                        "行程投诉核实属实（" + CreditService.complaintTypeName(c.getType()) + "），扣" + penalty + " 分");
                c.setCreditDelta(-penalty);
                c.setStatus("RESOLVED");
            }
            case "REJECTED" -> {
                if (!"PENDING".equals(c.getStatus())) {
                    throw new BusinessException("该投诉已在处理中或已处理");
                }
                c.setCreditDelta(0);
                c.setStatus("REJECTED");
            }
            case "UPHELD" -> {
                if (!"APPEALED".equals(c.getStatus())) {
                    throw new BusinessException("该投诉不在申诉状态");
                }
                c.setFinalAction("UPHELD");
                c.setStatus("FINAL");
            }
            case "OVERTURNED" -> {
                if (!"APPEALED".equals(c.getStatus())) {
                    throw new BusinessException("该投诉不在申诉状态");
                }
                int rollback = c.getCreditDelta() == null ? 0 : -c.getCreditDelta();
                if (rollback != 0) {
                    creditService.addCredit(c.getTargetId(), rollback,
                            "投诉申诉成立，回滚信誉分" + rollback + " 分");
                }
                c.setCreditDelta(0);
                c.setFinalAction("OVERTURNED");
                c.setStatus("FINAL");
            }
            default -> throw new BusinessException("action 必须为 RESOLVED / REJECTED / UPHELD / OVERTURNED");
        }
        c.setResult(result);
        c.setHandledBy(operatorId);
        c.setHandledAt(LocalDateTime.now());
        TripComplaint saved = complaintRepository.save(c);
        notifyService.notify(c.getReporterId(), "TRIP", "投诉处理结果",
                "你发起的投诉已有处理结果：" + (result == null ? action : result), c.getTripId());
        if (!c.getTargetId().equals(c.getReporterId())) {
            notifyService.notify(c.getTargetId(), "TRIP", "投诉处理结果",
                    "关于你的投诉已有处理结果：" + (result == null ? action : result), c.getTripId());
        }
        return Result.ok(view(saved));
    }

    // ==================== 管理员工作台 ====================

    /**
     * 管理员投诉工作台：全量投诉、KPI概览与类型分布，支持状态/类型/关键字筛选与分页
     * @param status 状态筛选
     * @param type 类型筛选
     * @param keyword 关键字（匹配行程标题/投诉人/被投诉人/投诉描述）
     * @param page 页码，默认1
     * @param size 每页条数，默认10
     * @return Result<Map<String,Object>> 列表、总数、KPI与类型分布
     */
    @GetMapping("/api/credit/complaints/admin")
    public Result<Map<String, Object>> adminList(@RequestParam(required = false) String status,
                                                @RequestParam(required = false) String type,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        AuthUtil.requireRole("ADMIN");
        List<TripComplaint> all = complaintRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));

        long pending = 0, appealed = 0, resolved = 0, rejected = 0, finalDone = 0, overturned = 0, penalized = 0;
        Map<String, Integer> typeDist = new LinkedHashMap<>();
        for (String t : TYPE_ORDER) {
            typeDist.put(t, 0);
        }
        for (TripComplaint c : all) {
            switch (c.getStatus() == null ? "" : c.getStatus()) {
                case "PENDING" -> pending++;
                case "APPEALED" -> appealed++;
                case "RESOLVED" -> resolved++;
                case "REJECTED" -> rejected++;
                case "FINAL" -> {
                    finalDone++;
                    if ("OVERTURNED".equals(c.getFinalAction())) overturned++;
                }
                default -> { }
            }
            if (c.getCreditDelta() != null && c.getCreditDelta() < 0) {
                penalized += -c.getCreditDelta();
            }
            typeDist.merge(c.getType(), 1, Integer::sum);
        }
        // 终审推翻率=推翻数/终审数
        double overturnRate = finalDone == 0 ? 0 : Math.round(overturned * 1000.0 / finalDone) / 10.0;

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("total", all.size());
        kpis.put("pending", pending);
        kpis.put("appealed", appealed);
        kpis.put("resolved", resolved);
        kpis.put("rejected", rejected);
        kpis.put("final", finalDone);
        kpis.put("overturned", overturned);
        kpis.put("overturnRate", overturnRate);
        kpis.put("penalizedPoints", penalized);

        List<Map<String, Object>> typeDistribution = new ArrayList<>();
        for (Map.Entry<String, Integer> e : typeDist.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", e.getKey());
            m.put("name", CreditService.complaintTypeName(e.getKey()));
            m.put("value", e.getValue());
            typeDistribution.add(m);
        }

        // ---- 筛选 ----
        String kw = keyword == null ? null : keyword.trim().toLowerCase();
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (TripComplaint c : all) {
            if (status != null && !status.isBlank() && !status.equals(c.getStatus())) continue;
            if (type != null && !type.isBlank() && !type.equals(c.getType())) continue;
            Map<String, Object> v = view(c);
            if (kw != null && !kw.isEmpty()) {
                String hay = (str(v.get("tripTitle")) + "|" + str(v.get("reporterName")) + "|"
                        + str(v.get("targetName")) + "|" + str(v.get("description"))).toLowerCase();
                if (!hay.contains(kw)) continue;
            }
            filtered.add(v);
        }

        // ---- 分页 ----
        int p = Math.max(1, page);
        int s = Math.min(50, Math.max(1, size));
        int from = Math.min((p - 1) * s, filtered.size());
        int to = Math.min(from + s, filtered.size());
        List<Map<String, Object>> pageList = new ArrayList<>(filtered.subList(from, to));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("list", pageList);
        m.put("total", filtered.size());
        m.put("page", p);
        m.put("size", s);
        m.put("kpis", kpis);
        m.put("typeDistribution", typeDistribution);
        return Result.ok(m);
    }

    // ==================== helpers ====================

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private TripComplaint mustGet(Long id) {
        return complaintRepository.findById(id)
                .orElseThrow(() -> new BusinessException("投诉记录不存在"));
    }

    private Map<String, Object> view(TripComplaint c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("tripId", c.getTripId());
        m.put("tripTitle", tripRepository.findById(c.getTripId()).map(Trip::getTitle).orElse(null));
        m.put("type", c.getType());
        m.put("typeName", CreditService.complaintTypeName(c.getType()));
        m.put("reporterId", c.getReporterId());
        m.put("reporterName", userRepository.findById(c.getReporterId())
                .map(u -> u.getNickname()).orElse(null));
        m.put("targetId", c.getTargetId());
        m.put("targetName", userRepository.findById(c.getTargetId())
                .map(u -> u.getNickname()).orElse(null));
        // 附上被投诉人当前信誉水平，供管理员判定
        m.put("targetCreditScore", userRepository.findById(c.getTargetId())
                .map(u -> u.getCreditScore()).orElse(null));
        m.put("targetComplaintCount", complaintRepository.countByTargetId(c.getTargetId()));
        m.put("targetUpheldCount",
                complaintRepository.countByTargetIdAndStatus(c.getTargetId(), "RESOLVED")
                        + complaintRepository.countByTargetIdAndStatus(c.getTargetId(), "FINAL"));
        m.put("description", c.getDescription());
        m.put("evidence", c.getEvidence());
        m.put("status", c.getStatus());
        m.put("result", c.getResult());
        m.put("creditDelta", c.getCreditDelta());
        m.put("appealReason", c.getAppealReason());
        m.put("appealAt", c.getAppealAt());
        m.put("finalAction", c.getFinalAction());
        m.put("handledBy", c.getHandledBy());
        m.put("handledByName", c.getHandledBy() == null ? null
                : userRepository.findById(c.getHandledBy()).map(u -> u.getNickname()).orElse(null));
        m.put("handledAt", c.getHandledAt());
        m.put("createdAt", c.getCreatedAt());
        return m;
    }
}
