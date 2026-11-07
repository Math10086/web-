package com.xuelvquan.travel.service;

import com.xuelvquan.travel.common.BusinessException;
import com.xuelvquan.travel.entity.Expense;
import com.xuelvquan.travel.entity.Footprint;
import com.xuelvquan.travel.entity.Trip;
import com.xuelvquan.travel.entity.TripMember;
import com.xuelvquan.travel.entity.TripPref;
import com.xuelvquan.travel.entity.User;
import com.xuelvquan.travel.repository.ExpenseRepository;
import com.xuelvquan.travel.repository.FootprintRepository;
import com.xuelvquan.travel.repository.TripMemberRepository;
import com.xuelvquan.travel.repository.TripPrefRepository;
import com.xuelvquan.travel.repository.TripRepository;
import com.xuelvquan.travel.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 个人出行画像服务（偏好、行程、消费与同伴的综合画像）
 */
@Service
@RequiredArgsConstructor
public class PortraitService {

    private static final List<String> CATEGORY_ORDER =
            List.of("TRANSPORT", "HOTEL", "FOOD", "TICKET", "SHOPPING", "OTHER");

    private static final Map<String, String> CATEGORY_NAMES = Map.of(
            "TRANSPORT", "交通", "HOTEL", "住宿", "FOOD", "餐饮",
            "TICKET", "门票", "SHOPPING", "购物", "OTHER", "其他");

    private static final List<String> PACE_ORDER = List.of("ENERGETIC", "BALANCED", "RELAXED");

    private static final Map<String, String> PACE_NAMES = Map.of(
            "ENERGETIC", "精力满满", "BALANCED", "节奏适中", "RELAXED", "休闲放松");

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository memberRepository;
    private final TripPrefRepository prefRepository;
    private final FootprintRepository footprintRepository;
    private final ExpenseRepository expenseRepository;
    private final CreditService creditService;

    public Map<String, Object> portrait(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        TripPref pref = prefRepository.findByUserId(userId).orElse(null);
        List<Footprint> footprints = footprintRepository.findByUserIdOrderByVisitedAtDesc(userId);

        // ==================== 参与的行程 ====================
        Map<Long, String> myRole = new LinkedHashMap<>();
        for (Trip t : tripRepository.findByCaptainIdOrderByCreatedAtDesc(userId)) {
            myRole.put(t.getId(), "CAPTAIN");
        }
        for (TripMember m : memberRepository.findByUserId(userId)) {
            if (!"APPROVED".equals(m.getStatus())) {
                continue;
            }
            myRole.putIfAbsent(m.getTripId(), "MEMBER");
        }
        List<Trip> trips = myRole.keySet().stream()
                .map(tripRepository::findById)
                .flatMap(Optional::stream)
                .toList();

        // ==================== 出行统计 ====================
        int finished = (int) trips.stream().filter(t -> "FINISHED".equals(t.getStatus())).count();
        int ongoing = (int) trips.stream().filter(t -> "ONGOING".equals(t.getStatus())).count();
        int upcoming = (int) trips.stream()
                .filter(t -> "OPEN".equals(t.getStatus()) || "FULL".equals(t.getStatus())).count();
        int asCaptain = (int) myRole.values().stream().filter("CAPTAIN"::equals).count();
        Set<String> cities = trips.stream().map(Trip::getDestination)
                .filter(Objects::nonNull).filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        int totalDays = trips.stream().mapToInt(t -> t.getPrefDays() == null ? 0 : t.getPrefDays()).sum();
        double plannedBudget = trips.stream()
                .mapToDouble(t -> t.getBudget() == null ? 0 : t.getBudget()).sum();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("tripCount", trips.size());
        stats.put("finishedCount", finished);
        stats.put("ongoingCount", ongoing);
        stats.put("upcomingCount", upcoming);
        stats.put("captainCount", asCaptain);
        stats.put("memberCount", trips.size() - asCaptain);
        stats.put("cityCount", cities.size());
        stats.put("spotCount", footprints.size());
        stats.put("totalDays", totalDays);
        stats.put("plannedBudget", round2(plannedBudget));

        // ==================== 节奏偏好：自述vs实际 ====================
        List<Map<String, Object>> paceActual = new ArrayList<>();
        Map<String, Long> paceCount = trips.stream()
                .map(t -> t.getPaceType() == null ? "BALANCED" : t.getPaceType())
                .collect(Collectors.groupingBy(p -> p, LinkedHashMap::new, Collectors.counting()));
        for (String p : PACE_ORDER) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("pace", p);
            m.put("name", PACE_NAMES.getOrDefault(p, p));
            m.put("count", paceCount.getOrDefault(p, 0L).intValue());
            paceActual.add(m);
        }
        String statedPace = pref == null || pref.getPaceType() == null ? "BALANCED" : pref.getPaceType();
        String dominantPace = paceActual.stream()
                .max(Comparator.comparingInt(m -> (int) m.get("count")))
                .filter(m -> (int) m.get("count") > 0)
                .map(m -> (String) m.get("pace"))
                .orElse(null);

        // ==================== 兴趣标签：自述+实际 ====================
        Map<String, Map<String, Object>> tagMap = new LinkedHashMap<>();
        if (pref != null) {
            for (String tag : splitTags(pref.getInterestTags())) {
                tagMap.computeIfAbsent(tag, k -> baseTag(k)).merge("count", 1, (a, b) -> (Integer) a + (Integer) b);
            }
        }
        for (Trip t : trips) {
            for (String tag : splitTags(t.getInterestTags())) {
                tagMap.computeIfAbsent(tag, k -> baseTag(k)).merge("count", 1, (a, b) -> (Integer) a + (Integer) b);
            }
        }
        List<Map<String, Object>> tagCloud = tagMap.values().stream()
                .sorted(Comparator.comparingInt((Map<String, Object> m) -> (int) m.get("count")).reversed())
                .toList();
        Set<String> prefTags = pref == null ? Set.of() : new LinkedHashSet<>(splitTags(pref.getInterestTags()));
        long tagsFromTrips = tagCloud.stream().filter(t -> !prefTags.contains(t.get("name"))).count();

        // ==================== 消费画像（个人承担口径） ====================
        double totalSpend = 0;
        double maxSingle = 0;
        Map<String, Double> byCategory = new LinkedHashMap<>();
        Map<String, Double> byMonth = new TreeMap<>();
        List<Map<String, Object>> topExpenses = new ArrayList<>();
        CATEGORY_ORDER.forEach(c -> byCategory.put(c, 0.0));

        List<Expense> personalExpenses =
                expenseRepository.findByScopeAndUserIdOrderByCreatedAtDesc("PERSONAL", userId);
        List<Expense> allExpenses = new ArrayList<>(personalExpenses);
        for (Long tid : myRole.keySet()) {
            allExpenses.addAll(expenseRepository.findByScopeAndTripIdOrderByCreatedAtDesc("TEAM", tid));
        }
        for (Expense e : allExpenses) {
            double share = "PERSONAL".equals(e.getScope())
                    ? nz(e.getAmount())
                    : shareOf(e, userId);
            if (share <= 0) {
                continue;
            }
            totalSpend += share;
            maxSingle = Math.max(maxSingle, share);
            String cat = e.getCategory() == null ? "OTHER" : e.getCategory();
            byCategory.merge(cat, share, Double::sum);
            String month = e.getCreatedAt() == null ? "更早" : e.getCreatedAt().format(MONTH_FMT);
            byMonth.merge(month, share, Double::sum);
        }
        double finalTotal = round2(totalSpend);
        List<Map<String, Object>> categoryProfile = new ArrayList<>();
        for (String c : CATEGORY_ORDER) {
            double amount = round2(byCategory.getOrDefault(c, 0.0));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("category", c);
            m.put("name", CATEGORY_NAMES.getOrDefault(c, c));
            m.put("amount", amount);
            m.put("ratio", finalTotal <= 0 ? 0 : round2(amount / finalTotal));
            categoryProfile.add(m);
        }
        List<Map<String, Object>> monthProfile = byMonth.entrySet().stream()
                .map(en -> {
                    Map<String, Object> m = new LinkedHashMap<String, Object>();
                    m.put("month", en.getKey());
                    m.put("amount", round2(en.getValue()));
                    return m;
                })
                .toList();
        topExpenses = allExpenses.stream()
                .filter(e -> "PERSONAL".equals(e.getScope()) || shareOf(e, userId) > 0)
                .sorted(Comparator.comparingDouble((Expense e) ->
                        "PERSONAL".equals(e.getScope()) ? nz(e.getAmount()) : shareOf(e, userId)).reversed())
                .limit(5)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<String, Object>();
                    m.put("title", e.getTitle());
                    m.put("category", e.getCategory());
                    m.put("categoryName", CATEGORY_NAMES.getOrDefault(e.getCategory(), e.getCategory()));
                    m.put("amount", round2("PERSONAL".equals(e.getScope()) ? nz(e.getAmount()) : shareOf(e, userId)));
                    m.put("scope", e.getScope());
                    m.put("createdAt", e.getCreatedAt());
                    return m;
                })
                .toList();

        Map<String, Object> expenseProfile = new LinkedHashMap<>();
        expenseProfile.put("totalSpend", finalTotal);
        expenseProfile.put("avgPerTrip", trips.isEmpty() ? 0 : round2(finalTotal / trips.size()));
        expenseProfile.put("maxSingle", round2(maxSingle));
        expenseProfile.put("byCategory", categoryProfile);
        expenseProfile.put("byMonth", monthProfile);
        expenseProfile.put("topExpenses", topExpenses);
        expenseProfile.put("budgetUsage", plannedBudget <= 0 ? null : round2(finalTotal / plannedBudget));

        // ==================== 目的地画像 ====================
        Map<String, List<Trip>> byCity = trips.stream()
                .filter(t -> t.getDestination() != null && !t.getDestination().isBlank())
                .collect(Collectors.groupingBy(Trip::getDestination, LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> destinations = byCity.entrySet().stream()
                .map(en -> {
                    Map<String, Object> m = new LinkedHashMap<String, Object>();
                    m.put("city", en.getKey());
                    m.put("count", en.getValue().size());
                    m.put("spots", footprints.stream().filter(f -> en.getKey().equals(f.getCity())).count());
                    m.put("lastAt", en.getValue().stream()
                            .map(Trip::getCreatedAt)
                            .filter(Objects::nonNull)
                            .max(LocalDateTime::compareTo).orElse(null));
                    return m;
                })
                .sorted(Comparator.comparingInt((Map<String, Object> m) -> (int) m.get("count")).reversed())
                .toList();

        // ==================== 同伴画像（一起结伴最多的人） ====================
        Map<Long, Integer> companionCount = new LinkedHashMap<>();
        for (Map.Entry<Long, String> en : myRole.entrySet()) {
            Optional<Trip> t = tripRepository.findById(en.getKey());
            if (t.isEmpty()) {
                continue;
            }
            Set<Long> ids = new LinkedHashSet<>();
            ids.add(t.get().getCaptainId());
            memberRepository.findByTripIdAndStatus(en.getKey(), "APPROVED")
                    .forEach(m -> ids.add(m.getUserId()));
            ids.remove(userId);
            for (Long id : ids) {
                companionCount.merge(id, 1, Integer::sum);
            }
        }
        List<Map<String, Object>> companions = companionCount.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .limit(8)
                .map(en -> {
                    Map<String, Object> m = new LinkedHashMap<String, Object>(
                            creditService.brief(en.getKey()));
                    m.put("count", en.getValue());
                    return m;
                })
                .toList();

        // ==================== 画像完整度（决定匹配推荐精度） ====================
        List<String> missing = new ArrayList<>();
        int filled = 0, totalFields = 8;
        if (user.getAvatar() != null && !user.getAvatar().isBlank()) filled++;
        else missing.add("头像");
        if (Boolean.TRUE.equals(user.getVerified())) filled++;
        else missing.add("校园实名认证");
        if (pref == null) {
            missing.addAll(List.of("结伴偏好", "游玩节奏", "兴趣标签", "预算", "交通方式", "同行范围"));
        } else {
            if (notBlank(pref.getDestinationPref())) filled++;
            else missing.add("目的地偏好");
            if (notBlank(pref.getInterestTags())) filled++;
            else missing.add("兴趣标签");
            if (pref.getBudget() != null && pref.getBudget() > 0) filled++;
            else missing.add("预算");
            if (notBlank(pref.getTransport())) filled++;
            else missing.add("交通方式");
            if (pref.getExpectDays() != null && pref.getExpectDays() > 0) filled++;
            else missing.add("期望天数");
            if (pref.getPaceType() != null) filled++;
            else missing.add("游玩节奏");
        }
        double completeness = round2((double) filled / totalFields);

        // ==================== 洞察与数据价值 ====================
        List<String> insights = new ArrayList<>();
        if (!destinations.isEmpty()) {
            Map<String, Object> top = destinations.get(0);
            insights.add("你最常结伴的目的地是「" + top.get("city") + "」（" + top.get("count")
                    + " 次），相关队伍会优先推荐给你");
        }
        if (dominantPace != null) {
            if (dominantPace.equals(statedPace)) {
                insights.add("实际出行节奏与自述偏好（" + PACE_NAMES.getOrDefault(statedPace, statedPace)
                        + "）一致，组队匹配会更有把握");
            } else {
                insights.add("自述节奏是「" + PACE_NAMES.getOrDefault(statedPace, statedPace)
                        + "」，但实际参加的行程以「" + PACE_NAMES.getOrDefault(dominantPace, dominantPace)
                        + "」为主，建议调整偏好以获得更准的推荐");
            }
        }
        Map<String, Object> topCat = categoryProfile.stream()
                .filter(c -> (double) c.get("amount") > 0)
                .max(Comparator.comparingDouble(c -> (double) c.get("amount"))).orElse(null);
        if (topCat != null) {
            insights.add("消费大头是「" + topCat.get("name") + "」（占比 "
                    + Math.round((double) topCat.get("ratio") * 100) + "%），商城与优惠会按此倾斜");
        }
        if (!companions.isEmpty()) {
            Map<String, Object> best = companions.get(0);
            insights.add("与「" + best.get("nickname") + "」已结伴" + best.get("count")
                    + " 次，是最高频同伴，可互为固定搭子");
        }
        if (tagsFromTrips > 0) {
            insights.add("有" + tagsFromTrips + " 个兴趣标签来自你的真实行程，而非仅自述——行为标签比自述更可信");
        }
        if (completeness < 0.75) {
            insights.add("画像完整度" + Math.round(completeness * 100) + "%，补全「"
                    + String.join("、", missing.stream().limit(3).toList()) + "」可显著提升组队推荐精度");
        }

        List<String> coopHints = new ArrayList<>();
        if (topCat != null) {
            coopHints.add("「" + topCat.get("name") + "」类消费占 "
                    + Math.round((double) topCat.get("ratio") * 100) + "%，是同业合作的首选切入点");
        }
        if (!destinations.isEmpty()) {
            coopHints.add("高频目的地「" + destinations.get(0).get("city")
                    + "」可对接当地景区 / 民宿做定向投放");
        }
        if (!tagCloud.isEmpty()) {
            coopHints.add("群体兴趣标签 Top："
                    + tagCloud.stream().limit(3).map(t -> String.valueOf(t.get("name")))
                            .collect(Collectors.joining("、"))
                    + "，可用于联名线路策划");
        }

        Map<String, Object> dataValue = new LinkedHashMap<>();
        dataValue.put("completeness", completeness);
        dataValue.put("missing", missing);
        dataValue.put("coopHints", coopHints);

        // ==================== 组装 ====================
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", creditService.brief(userId));
        Map<String, Object> prefView = new LinkedHashMap<>();
        if (pref != null) {
            prefView.put("paceType", pref.getPaceType());
            prefView.put("paceName", PACE_NAMES.getOrDefault(pref.getPaceType(), pref.getPaceType()));
            prefView.put("destinationPref", pref.getDestinationPref());
            prefView.put("interestTags", splitTags(pref.getInterestTags()));
            prefView.put("budget", pref.getBudget());
            prefView.put("transport", pref.getTransport());
            prefView.put("expectDays", pref.getExpectDays());
            prefView.put("openToCrossSchool", Boolean.TRUE.equals(pref.getOpenToCrossSchool()));
        }
        result.put("pref", prefView);
        result.put("stats", stats);
        result.put("paceProfile", Map.of(
                "stated", statedPace,
                "statedName", PACE_NAMES.getOrDefault(statedPace, statedPace),
                "actual", paceActual,
                "consistent", dominantPace == null || dominantPace.equals(statedPace)));
        result.put("tagCloud", tagCloud);
        result.put("expenseProfile", expenseProfile);
        result.put("destinations", destinations);
        result.put("companions", companions);
        result.put("insights", insights);
        result.put("dataValue", dataValue);
        return result;
    }

    // ==================== helpers ====================

    private Map<String, Object> baseTag(String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("count", 0);
        return m;
    }

    /**
     * 团队AA记账中某人的分摊额：优先perPerson，缺省按sharers均摊；不在名单内则为0
     * @param e 消费记录
     * @param userId 用户ID
     * @return double 该用户在这笔消费中的分摊金额
     */
    private double shareOf(Expense e, Long userId) {
        List<Long> sharers = parseIds(e.getSharers());
        if (sharers.isEmpty() || !sharers.contains(userId)) {
            return 0;
        }
        if (e.getPerPerson() != null && e.getPerPerson() > 0) {
            return e.getPerPerson();
        }
        return round2(nz(e.getAmount()) / sharers.size());
    }

    private List<Long> parseIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split("[,，、\\s]+"))
                .map(String::trim).filter(s -> s.matches("\\d+"))
                .map(Long::valueOf).toList();
    }

    /**
     * 逗号串拆标签（兼容中英文逗号、顿号、空格）
     * @param csv 标签串，各标签用逗号或顿号分隔
     * @return List 去重后的标签列表，入参为空时返回空列表
     */
    private List<String> splitTags(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split("[,，、/\\s]+"))
                .map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private double nz(Double d) {
        return d == null ? 0 : d;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
