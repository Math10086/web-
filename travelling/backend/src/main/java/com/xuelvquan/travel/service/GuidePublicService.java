package com.xuelvquan.travel.service;

import com.xuelvquan.travel.common.BusinessException;
import com.xuelvquan.travel.entity.GuideBooking;
import com.xuelvquan.travel.entity.GuideProfile;
import com.xuelvquan.travel.entity.GuideService;
import com.xuelvquan.travel.entity.User;
import com.xuelvquan.travel.repository.GuideBookingRepository;
import com.xuelvquan.travel.repository.GuideProfileRepository;
import com.xuelvquan.travel.repository.GuideServiceRepository;
import com.xuelvquan.travel.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * 导游公开主页（供给侧透明化）。
 *
 * 出发点：游客在「找导游」列表只能看到评分和接单数，无法判断这位向导
 * 到底带过什么线路、别人怎么评价。这里把<b>已完成</b>的接待记录与评价公开出来，
 * 让选择依据从「一个分数」变成「一串可验证的经历」。
 *
 * 隐私边界（重要）：只公开导游侧的履约事实；游客侧一律脱敏——
 * 昵称只留首字 + 「**」，不返回游客id、订单金额与联系方式。
 * 未完成/已取消的订单不进入公开经历，避免把「被取消」也当成履历展示。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuidePublicService {

    private final GuideProfileRepository profileRepository;
    private final GuideServiceRepository serviceRepository;
    private final GuideBookingRepository bookingRepository;
    private final UserRepository userRepository;

    public Map<String, Object> publicProfile(Long userId) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        GuideProfile p = profileRepository.findByUserId(userId).orElse(null);
        if (p == null || !"APPROVED".equals(p.getStatus())) {
            throw new BusinessException("该用户还未成为认证导游");
        }

        List<GuideBooking> all = bookingRepository.findByGuideIdOrderByCreatedAtDesc(userId);
        List<GuideBooking> settled = all.stream()
                .filter(b -> "SETTLED".equals(b.getStatus()))
                .toList();
        // 有评价的订单，按评价时间倒序（没有评价时间的老数据退回出行日期）
        List<GuideBooking> reviewed = settled.stream()
                .filter(b -> b.getRating() != null)
                .sorted(Comparator.comparing(
                        (GuideBooking b) -> b.getReviewedAt() == null
                                ? (b.getTripDate() == null ? LocalDate.MIN : b.getTripDate()).atStartOfDay()
                                : b.getReviewedAt()).reversed())
                .toList();

        Map<Long, GuideService> serviceMap = new HashMap<>();
        for (GuideService s : serviceRepository.findByGuideId(userId)) {
            serviceMap.put(s.getId(), s);
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", u.getId());
        m.put("nickname", u.getNickname());
        m.put("avatar", u.getAvatar() == null ? "" : u.getAvatar());
        m.put("school", u.getSchool() == null ? "" : u.getSchool());
        m.put("verified", Boolean.TRUE.equals(u.getVerified()));
        m.put("creditScore", u.getCreditScore());
        m.put("themes", p.getThemes());
        m.put("intro", p.getIntro());
        m.put("supplyTier", p.getSupplyTier());
        m.put("supplyTierLabel", supplyTierLabel(p.getSupplyTier()));
        m.put("ratingAvg", p.getRatingAvg());
        m.put("ratingCount", p.getRatingCount());
        m.put("orderCount", p.getOrderCount());
        m.put("joinedAt", p.getCreatedAt());

        // 统计口径全部基于实际订单，不用资料里的累计值，避免「36条评分但只有6条记录」这类对不上
        long good = reviewed.stream().filter(b -> b.getRating() >= 4).count();
        double avg = reviewed.stream().mapToInt(GuideBooking::getRating).average().orElse(0);
        Set<String> cities = new TreeSet<>();
        for (GuideBooking b : settled) {
            GuideService s = serviceMap.get(b.getServiceId());
            if (s != null && s.getDestination() != null && !s.getDestination().isBlank()) {
                cities.add(s.getDestination());
            }
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("completedCount", settled.size());
        stats.put("reviewCount", reviewed.size());
        stats.put("goodRate", reviewed.isEmpty() ? null : Math.round(good * 100.0 / reviewed.size()));
        stats.put("reviewAvg", reviewed.isEmpty() ? null : Math.round(avg * 10) / 10.0);
        stats.put("cityCount", cities.size());
        stats.put("cities", new ArrayList<>(cities));
        stats.put("ongoingCount", all.stream().filter(b ->
                "PAID".equals(b.getStatus()) || "CONFIRMED".equals(b.getStatus())).count());
        m.put("stats", stats);

        // 星级分布（5 → 1），前端画条形图
        List<Map<String, Object>> distribution = new ArrayList<>();
        for (int star = 5; star >= 1; star--) {
            final int s = star;
            long c = reviewed.stream().filter(b -> b.getRating() == s).count();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("star", star);
            row.put("count", c);
            row.put("ratio", reviewed.isEmpty() ? 0 : Math.round(c * 100.0 / reviewed.size()));
            distribution.add(row);
        }
        m.put("ratingDistribution", distribution);

        // 过往接待经历（已完成，按出行日期倒序）
        List<Map<String, Object>> experiences = new ArrayList<>();
        settled.stream()
                .sorted(Comparator.comparing((GuideBooking b) ->
                        b.getTripDate() == null ? LocalDate.MIN : b.getTripDate()).reversed())
                .forEach(b -> {
                    GuideService s = serviceMap.get(b.getServiceId());
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", b.getId());
                    row.put("serviceTitle", s == null ? "导游服务" : s.getTitle());
                    row.put("destination", s == null ? "" : s.getDestination());
                    row.put("themes", s == null ? "" : s.getThemes());
                    row.put("schedule", s == null ? "" : s.getSchedule());
                    row.put("tripDate", b.getTripDate());
                    row.put("rating", b.getRating());
                    row.put("reviewed", b.getRating() != null);
                    experiences.add(row);
                });
        m.put("experiences", experiences);

        // 公开评价（游客昵称脱敏）
        List<Map<String, Object>> reviews = new ArrayList<>();
        for (GuideBooking b : reviewed) {
            GuideService s = serviceMap.get(b.getServiceId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rating", b.getRating());
            row.put("content", b.getReviewContent());
            row.put("date", b.getReviewedAt() == null ? b.getTripDate() : b.getReviewedAt().toLocalDate());
            row.put("serviceTitle", s == null ? "导游服务" : s.getTitle());
            row.put("destination", s == null ? "" : s.getDestination());
            row.put("traveler", maskNickname(
                    userRepository.findById(b.getTravelerId()).map(User::getNickname).orElse(null)));
            reviews.add(row);
        }
        m.put("reviews", reviews);

        // 在售服务
        List<Map<String, Object>> services = new ArrayList<>();
        for (GuideService s : serviceMap.values()) {
            if (!"APPROVED".equals(s.getStatus())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", s.getId());
            row.put("title", s.getTitle());
            row.put("destination", s.getDestination());
            row.put("themes", s.getThemes());
            row.put("price", s.getPrice());
            row.put("schedule", s.getSchedule());
            row.put("intro", s.getIntro());
            services.add(row);
        }
        m.put("services", services);
        return m;
    }

    /**
     * 昵称脱敏：保留首字，其余用 ** 代替（「米粉妹」→「米**」）
     * @param nickname 游客昵称，可为空
     * @return String 脱敏后的昵称，空值返回「匿名游客」
     */
    private String maskNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return "匿名游客";
        }
        String n = nickname.trim();
        return n.length() <= 1 ? n + "**" : n.charAt(0) + "**";
    }

    private String supplyTierLabel(String tier) {
        if (tier == null) {
            return "学生导游";
        }
        return switch (tier) {
            case "CERTIFIED_GUIDE" -> "平台认证导游";
            case "AGENCY" -> "旅行社";
            default -> "学生导游";
        };
    }
}
