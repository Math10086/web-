package com.xuelvquan.travel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xuelvquan.travel.common.BusinessException;
import com.xuelvquan.travel.entity.DynamicPlan;
import com.xuelvquan.travel.entity.Spot;
import com.xuelvquan.travel.entity.Trip;
import com.xuelvquan.travel.entity.TripMember;
import com.xuelvquan.travel.repository.DynamicPlanRepository;
import com.xuelvquan.travel.repository.SpotRepository;
import com.xuelvquan.travel.repository.TripMemberRepository;
import com.xuelvquan.travel.repository.TripRepository;
import com.xuelvquan.travel.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 动态行程调整服务（接入天气等因素给出调整方案）
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PlanAdjustService {

    private final WeatherService weatherService;
    private final DynamicPlanRepository dynamicPlanRepository;
    private final SpotRepository spotRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final NotifyService notifyService;
    private final ObjectMapper objectMapper;

    /** 室内/受天气影响小的景点类型 */
    private static final Set<String> INDOOR_CATEGORIES = Set.of("HISTORY", "THEME", "FOOD");

    /**
     * 生成动态调整方案
     * @param body 含destination（目的地）、days（天数）、tripId（可选）、dayPlans（可选原分天行程）、reason（可选用户诉求）
     * @return Map 含adjustedDays（逐日调整）、reasons（调整理由）、summary（总结）、notified（通知人数）等
     */
    public Map<String, Object> adjust(Map<String, Object> body) {
        String destination = str(body.get("destination"));
        if (destination.isBlank()) {
            throw new BusinessException("请提供目的地");
        }
        int days = toInt(body.get("days"), 3);
        Long tripId = body.get("tripId") == null ? null : Long.valueOf(String.valueOf(body.get("tripId")));
        String userReason = str(body.get("reason"));

        // 1) 接入天气
        Map<String, Object> weather = weatherService.forecast(destination, days);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> weatherDays = (List<Map<String, Object>>) weather.get("days");

        // 2) 当前分天行程（用于展示原方案锚点）
        List<Map<String, Object>> dayPlans = asList(body.get("dayPlans"));

        // 3) 同城可选室内景点（顺路度：同城就近替换）
        List<Spot> indoor = spotRepository.findAll().stream()
                .filter(s -> destination.equals(s.getCity()))
                .filter(s -> s.getCategory() != null && INDOOR_CATEGORIES.contains(s.getCategory()))
                .sorted(Comparator.comparing(s -> -(s.getHeat() == null ? 0 : s.getHeat())))
                .toList();

        List<Map<String, Object>> adjustedDays = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        int badCount = 0;

        for (int i = 0; i < days; i++) {
            Map<String, Object> w = i < weatherDays.size() ? weatherDays.get(i) : null;
            boolean bad = weatherService.isBadWeather(w);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("day", i + 1);
            row.put("date", w == null ? null : w.get("date"));
            row.put("weekday", w == null ? null : w.get("weekday"));
            row.put("weather", w);
            row.put("bad", bad);

            String originalHint = dayPlans.size() > i ? str(dayPlans.get(i).get("title")) : "";
            if (originalHint.isBlank()) {
                originalHint = dayPlans.size() > i && dayPlans.get(i).get("anchor") != null
                        ? str(dayPlans.get(i).get("anchor"))
                        : "第" + (i + 1) + " 天原计划";
            }
            row.put("originalHint", originalHint);

            if (bad) {
                badCount++;
                String text = w == null ? "恶劣天气" : str(w.get("text"));
                List<Map<String, Object>> alts = new ArrayList<>();
                for (Spot s : indoor) {
                    Map<String, Object> a = new LinkedHashMap<>();
                    a.put("id", s.getId());
                    a.put("name", s.getName());
                    a.put("category", s.getCategory());
                    a.put("heat", s.getHeat());
                    alts.add(a);
                    if (alts.size() >= 3) break;
                }
                row.put("action", alts.isEmpty() ? "RESCHEDULE" : "SWAP_INDOOR");
                row.put("alternatives", alts);
                String suggestion = alts.isEmpty()
                        ? "第" + (i + 1) + " 天预计" + text + "，建议将户外行程顺延，当天改为自由活动 / 市区休整。"
                        : "第" + (i + 1) + " 天预计" + text + "，建议把户外景点替换为室内的 "
                          + alts.get(0).get("name") + " 等（博物馆 / 展馆 / 美食街区），雨小间隙再安排户外。";
                row.put("suggestion", suggestion);
                reasons.add(suggestion);
            } else {
                row.put("action", "KEEP");
                row.put("alternatives", List.of());
                row.put("suggestion", "第" + (i + 1) + " 天天气适宜，行程维持不变。");
            }
            adjustedDays.add(row);
        }

        String summary;
        if (badCount == 0) {
            summary = "未来" + days + " 天天气均适合出行，行程无需调整。";
        } else {
            summary = "未来" + days + " 天中有" + badCount + " 天可能出现不利天气，已按室内替代 / 顺延原则给出调整建议。";
        }
        if (!userReason.isBlank()) {
            summary = summary + " 已结合你的诉求「" + userReason + "」综合调整。";
        }

        // 4) 通知同行成员（若关联结伴行程且操作者为队长）
        int notified = 0;
        Long userId = AuthUtil.currentUserId();
        if (tripId != null) {
            Trip trip = tripRepository.findById(tripId).orElse(null);
            if (trip == null) {
                throw new BusinessException("行程不存在");
            }
            boolean captain = trip.getCaptainId() != null && trip.getCaptainId().equals(userId);
            if (captain) {
                List<TripMember> members = tripMemberRepository.findByTripIdAndStatus(tripId, "APPROVED");
                for (TripMember m : members) {
                    if (m.getUserId() != null && !m.getUserId().equals(userId)) {
                        notifyService.notify(m.getUserId(), "TRIP", "行程动态调整",
                                "「" + trip.getTitle() + "」" + summary, tripId);
                        notified++;
                    }
                }
            }
        }

        // 5) 留存动态调整记录
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("destination", destination);
        result.put("days", days);
        result.put("weather", weather);
        result.put("adjustedDays", adjustedDays);
        result.put("reasons", reasons);
        result.put("summary", summary);
        result.put("badCount", badCount);
        result.put("notified", notified);
        result.put("tripId", tripId);

        if (userId != null) {
            DynamicPlan plan = new DynamicPlan();
            plan.setUserId(userId);
            plan.setTripId(tripId);
            plan.setDestination(destination);
            plan.setDays(days);
            plan.setAdjustFactor(reasons.isEmpty() ? "天气适宜" : String.join("；", reasons));
            plan.setOldPlanJson(toJson(dayPlans));
            plan.setNewPlanJson(toJson(adjustedDays));
            plan.setReasonsJson(toJson(reasons));
            plan.setCreatedAt(LocalDateTime.now());
            DynamicPlan saved = dynamicPlanRepository.save(plan);
            result.put("planId", saved.getId());
        }
        return result;
    }

    /** 我的动态调整历史 */
    public List<DynamicPlan> myPlans() {
        Long userId = AuthUtil.requireUserId();
        return dynamicPlanRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    private String toJson(Object o) {
        try {
            // MySQL 5.1的utf8为3字节编码，无法存储4字节emoji（天气图标等），
            // 落库前剥离非BMP字符，避免Incorrect string value报错
            return stripNonBmp(objectMapper.writeValueAsString(o == null ? List.of() : o));
        } catch (Exception e) {
            return "[]";
        }
    }

    private String stripNonBmp(String s) {
        if (s == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isHighSurrogate(c)) {
                i++;
                continue;
            }
            if (Character.isLowSurrogate(c)) {
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asList(Object o) {
        if (o instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    out.add((Map<String, Object>) m);
                }
            }
            return out;
        }
        return List.of();
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private int toInt(Object o, int def) {
        try {
            return o == null ? def : Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return def;
        }
    }
}
