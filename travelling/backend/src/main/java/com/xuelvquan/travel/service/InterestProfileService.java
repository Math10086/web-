package com.xuelvquan.travel.service;

import com.xuelvquan.travel.entity.Circle;
import com.xuelvquan.travel.entity.CircleMember;
import com.xuelvquan.travel.entity.Footprint;
import com.xuelvquan.travel.entity.Post;
import com.xuelvquan.travel.entity.Spot;
import com.xuelvquan.travel.entity.Trip;
import com.xuelvquan.travel.entity.TripMember;
import com.xuelvquan.travel.repository.CircleMemberRepository;
import com.xuelvquan.travel.repository.CircleRepository;
import com.xuelvquan.travel.repository.FootprintRepository;
import com.xuelvquan.travel.repository.PostRepository;
import com.xuelvquan.travel.repository.SpotRepository;
import com.xuelvquan.travel.repository.TripMemberRepository;
import com.xuelvquan.travel.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 动态兴趣画像服务：根据用户历史行为（队伍、报名、发帖、圈子、足迹）自动推断动态偏好。
 * 每次按用户聚合现有表并缓存5分钟，作为结伴匹配的兴趣维度来源。
 */
@Service
@RequiredArgsConstructor
public class InterestProfileService {

    private static final int W_CAPTAIN_TRIP = 4;
    private static final int W_APPROVED_TRIP = 3;
    private static final int W_APPLIED_TRIP = 1;
    private static final int W_POST_MODE = 2;
    private static final int W_POST_KEYWORD = 1;
    private static final int W_CIRCLE = 2;
    private static final int W_FOOTPRINT = 2;

    /** 兴趣标签词表（与前端TRIP_INTEREST_TAGS保持一致） */
    public static final List<String> INTEREST_TAGS = List.of(
            "徒步", "登山", "露营", "摄影", "美食", "Citywalk",
            "骑行", "自驾", "速通打卡", "博物馆", "古镇", "温泉");

    /** 帖子模式 → 兴趣标签 */
    private static final Map<String, List<String>> MODE_TAGS = Map.of(
            "PHOTO", List.of("摄影"),
            "FOOD", List.of("美食"),
            "WILD", List.of("徒步", "露营"),
            "CAMP", List.of("露营"),
            "CITYWALK", List.of("Citywalk"),
            "SPEED", List.of("速通打卡"),
            "SCENIC", List.of("摄影"),
            "LEISURE", List.of(),
            "OPTIMAL", List.of());

    /** 景点类别 → 兴趣标签 */
    private static final Map<String, List<String>> CATEGORY_TAGS = Map.of(
            "HISTORY", List.of("博物馆", "古镇"),
            "NATURE", List.of("徒步", "登山"),
            "TRENDY", List.of("Citywalk", "摄影"),
            "FOOD", List.of("美食"),
            "SECRET", List.of("Citywalk"),
            "THEME", List.of("速通打卡"));

    /** 圈子主题 → 兴趣标签 */
    private static final Map<String, List<String>> CIRCLE_TAGS = Map.of(
            "FOOD", List.of("美食"),
            "OUTDOOR", List.of("徒步", "登山"),
            "PHOTO", List.of("摄影"),
            "CAMP", List.of("露营"),
            "CITYWALK", List.of("Citywalk"),
            "SPEED", List.of("速通打卡"));

    /** 画像缓存的存活时间 */
    private static final long TTL_MS = 5 * 60 * 1000L;

    private final FootprintRepository footprintRepository;
    private final SpotRepository spotRepository;
    private final PostRepository postRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository memberRepository;
    private final CircleMemberRepository circleMemberRepository;
    private final CircleRepository circleRepository;

    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();

    // ============================================================
    // 对外
    // ============================================================

    /**
     * 手动失效（保存偏好、报名、发帖后调用，让画像立刻反映新行为）
     * @param userId 用户ID，为空时不做处理
     */
    public void invalidate(Long userId) {
        if (userId != null) {
            cache.remove(userId);
        }
    }

    /**
     * 取用户画像（带5分钟缓存）
     * @param userId 用户ID，为空时返回空画像
     * @return DerivedProfile 该用户的行为推导画像
     */
    public DerivedProfile derive(Long userId) {
        if (userId == null) {
            return new DerivedProfile();
        }
        Cached c = cache.get(userId);
        long now = System.currentTimeMillis();
        if (c != null && now - c.at < TTL_MS) {
            return c.profile;
        }
        DerivedProfile p = compute(userId);
        cache.put(userId, new Cached(p, now));
        return p;
    }

    private DerivedProfile compute(Long userId) {
        DerivedProfile out = new DerivedProfile();
        Map<String, TagStat> stats = new LinkedHashMap<>();
        Map<String, Integer> cityCount = new LinkedHashMap<>();

        List<Trip> approvedTrips = new ArrayList<>();
        List<Trip> ownTrips = new ArrayList<>();
        List<Post> posts = new ArrayList<>();

        // 1) 自己发起的队伍
        for (Trip t : tripRepository.findByCaptainIdOrderByCreatedAtDesc(userId)) {
            ownTrips.add(t);
            for (String tag : tags(t.getInterestTags())) {
                hit(stats, tag, "发队", W_CAPTAIN_TRIP);
            }
        }

        // 2) 报名记录：通过的权重更高；同时用这些队伍的属性推断天数/预算/交通/节奏
        List<TripMember> memberships = memberRepository.findByUserId(userId);
        if (!memberships.isEmpty()) {
            Set<Long> tripIds = memberships.stream()
                    .map(TripMember::getTripId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Map<Long, Trip> tripMap = new HashMap<>();
            tripRepository.findAllById(tripIds).forEach(t -> tripMap.put(t.getId(), t));
            for (TripMember tm : memberships) {
                Trip t = tripMap.get(tm.getTripId());
                if (t == null || Objects.equals(t.getCaptainId(), userId)) {
                    continue; // 自己发的队已在第1步算过，避免重复计分
                }
                boolean approved = "APPROVED".equals(tm.getStatus());
                for (String tag : tags(t.getInterestTags())) {
                    hit(stats, tag, "报名", approved ? W_APPROVED_TRIP : W_APPLIED_TRIP);
                }
                if (approved) {
                    approvedTrips.add(t);
                }
            }
        }

        // 3) 发帖：模式标签+正文关键词
        posts.addAll(postRepository.findByAuthorIdOrderByCreatedAtDesc(userId));
        for (Post p : posts) {
            String mode = p.getModeTag();
            List<String> modeTags = mode == null ? List.of() : MODE_TAGS.getOrDefault(mode, List.of());
            modeTags.forEach(tag -> hit(stats, tag, "发帖", W_POST_MODE));
            String text = nz(p.getTitle()) + " " + nz(p.getContent());
            for (String tag : INTEREST_TAGS) {
                if (text.contains(tag) && !modeTags.contains(tag)) {
                    hit(stats, tag, "发帖", W_POST_KEYWORD);
                }
            }
        }

        // 4) 圈子：主动加入本身就是兴趣声明
        List<CircleMember> members = circleMemberRepository.findByUserId(userId);
        if (!members.isEmpty()) {
            List<Long> circleIds = members.stream().map(CircleMember::getCircleId)
                    .filter(Objects::nonNull).distinct().collect(Collectors.toList());
            Map<Long, Circle> circleMap = new HashMap<>();
            circleRepository.findAllById(circleIds).forEach(c -> circleMap.put(c.getId(), c));
            for (CircleMember m : members) {
                Circle c = circleMap.get(m.getCircleId());
                if (c == null || c.getTheme() == null) {
                    continue;
                }
                for (String tag : CIRCLE_TAGS.getOrDefault(c.getTheme(), List.of())) {
                    hit(stats, tag, "圈子", W_CIRCLE);
                }
            }
            out.evidence.add("加入了" + circleMap.values().stream().map(Circle::getName)
                    .filter(Objects::nonNull).limit(3).collect(Collectors.joining("、")));
        }

        // 5) 足迹：景点类别+景点名/简介关键词+城市分布（城市用于推断目的地偏好）
        List<Footprint> footprints = footprintRepository.findByUserIdOrderByVisitedAtDesc(userId);
        if (!footprints.isEmpty()) {
            Set<Long> spotIds = footprints.stream().map(Footprint::getSpotId)
                    .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
            Map<Long, Spot> spotMap = new HashMap<>();
            if (!spotIds.isEmpty()) {
                spotRepository.findAllById(spotIds).forEach(s -> spotMap.put(s.getId(), s));
            }
            int natureLike = 0;
            for (Footprint f : footprints) {
                if (f.getCity() != null && !f.getCity().isBlank()) {
                    cityCount.merge(f.getCity().trim(), 1, Integer::sum);
                }
                Spot s = f.getSpotId() == null ? null : spotMap.get(f.getSpotId());
                String text = f.getName() + " " + (s == null ? "" : nz(s.getDescription())) + " " + nz(f.getNote());
                if (s != null && s.getCategory() != null) {
                    for (String tag : CATEGORY_TAGS.getOrDefault(s.getCategory(), List.of())) {
                        hit(stats, tag, "足迹", W_FOOTPRINT);
                        if ("NATURE".equals(s.getCategory())) {
                            natureLike++;
                        }
                    }
                }
                for (String tag : INTEREST_TAGS) {
                    // 该类别已计分则不重复，避免同类信号被放大
                    if (text.contains(tag) && s != null && s.getCategory() != null
                            && !CATEGORY_TAGS.getOrDefault(s.getCategory(), List.of()).contains(tag)) {
                        hit(stats, tag, "足迹", W_POST_KEYWORD);
                    }
                }
            }
            if (natureLike > 0) {
                out.evidence.add("足迹里有" + natureLike + " 个自然类景点");
            }
        }

        // 6) 归一化：按得分排序、换算成0-100的相对强度（最强者=100）
        List<Map.Entry<String, TagStat>> sorted = stats.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, TagStat> e) -> e.getValue().score).reversed())
                .collect(Collectors.toList());
        int maxScore = sorted.isEmpty() ? 0 : sorted.get(0).getValue().score;
        for (Map.Entry<String, TagStat> e : sorted) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tag", e.getKey());
            int score = e.getValue().score;
            int percent = maxScore == 0 ? 0 : (int) Math.round(score * 100.0 / maxScore);
            m.put("score", score);
            m.put("percent", percent);
            m.put("sources", e.getValue().describe());
            out.tags.add(m);
            // 只保留相对强度 ≥ 25% 的前6个，避免弱信号噪声
            if (percent >= 25 && out.topTags.size() < 6) {
                out.topTags.add(e.getKey());
            }
        }
        if (out.topTags.isEmpty() && !sorted.isEmpty()) {
            out.topTags.add(sorted.get(0).getKey()); // 至少给一个最可能的
        }

        // 7) 行为推断出的其它偏好（仅在手填值为空时才会被采用）
        out.expectDays = medianInt(approvedTrips.stream().map(Trip::getPrefDays).toList());
        if (out.expectDays == null) {
            out.expectDays = medianInt(posts.stream().map(Post::getDays).toList());
        }
        out.budget = medianDouble(approvedTrips.stream().map(Trip::getBudget).toList());
        if (out.budget == null) {
            out.budget = medianDouble(posts.stream().map(Post::getCostPerPerson)
                    .filter(v -> v != null && v > 0).toList());
        }
        out.transport = mode(approvedTrips.stream().map(Trip::getTransport).toList());
        out.paceType = mode(approvedTrips.stream().map(Trip::getPaceType).toList());
        out.destinationPref = cityCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .filter(e -> e.getValue() >= 2)
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);

        cityCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .forEach(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("city", e.getKey());
                    m.put("count", e.getValue());
                    out.cities.add(m);
                });

        // 8) 证据与置信度
        if (!ownTrips.isEmpty()) {
            int n = (int) ownTrips.stream().filter(t -> !tags(t.getInterestTags()).isEmpty()).count();
            if (n > 0) {
                out.evidence.add("发起过" + n + " 支带兴趣标签的队伍");
            }
        }
        if (!approvedTrips.isEmpty()) {
            out.evidence.add("报名并通过" + approvedTrips.size() + " 支队伍");
        }
        if (!posts.isEmpty()) {
            out.evidence.add("发布过" + posts.size() + " 篇游记/攻略");
        }
        if (footprints.size() >= 2) {
            out.evidence.add("记录了" + footprints.size() + " 条旅行足迹");
        }
        out.signalCount = stats.values().stream().mapToInt(s -> s.hits.values().stream().mapToInt(Integer::intValue).sum()).sum();
        out.confidence = out.signalCount >= 6 ? "HIGH" : out.signalCount >= 3 ? "MEDIUM" : out.signalCount >= 1 ? "LOW" : "NONE";
        out.computedAt = LocalDateTime.now();
        return out;
    }

    private void hit(Map<String, TagStat> stats, String tag, String source, int points) {
        if (tag == null || tag.isBlank()) {
            return;
        }
        stats.computeIfAbsent(tag.trim(), k -> new TagStat()).hit(source, points);
    }

    private Set<String> tags(String csv) {
        Set<String> set = new LinkedHashSet<>();
        if (csv == null || csv.isBlank()) {
            return set;
        }
        Arrays.stream(csv.split("[,，、/\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(set::add);
        return set;
    }

    private String nz(String v) {
        return v == null ? "" : v;
    }

    private Integer medianInt(List<Integer> values) {
        List<Integer> list = values.stream().filter(Objects::nonNull).sorted().collect(Collectors.toList());
        return list.isEmpty() ? null : list.get(list.size() / 2);
    }

    private Double medianDouble(List<Double> values) {
        List<Double> list = values.stream().filter(Objects::nonNull).sorted().collect(Collectors.toList());
        return list.isEmpty() ? null : list.get(list.size() / 2);
    }

    private String mode(List<String> values) {
        Map<String, Integer> count = new LinkedHashMap<>();
        values.stream().filter(Objects::nonNull).forEach(v -> count.merge(v, 1, Integer::sum));
        return count.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(null);
    }

    /** 单个标签的累计得分与命中来源 */
    private static final class TagStat {
        int score;
        final Map<String, Integer> hits = new LinkedHashMap<>();

        void hit(String source, int points) {
            score += points;
            hits.merge(source, 1, Integer::sum);
        }

        String describe() {
            return hits.entrySet().stream()
                    .map(e -> e.getKey() + "×" + e.getValue())
                    .collect(Collectors.joining(" · "));
        }
    }

    private record Cached(DerivedProfile profile, long at) {
    }

    /** 画像结果（对外只读） */
    public static class DerivedProfile {
        /** 全部标签及强度：[{tag, score, percent, sources}] */
        public final List<Map<String, Object>> tags = new ArrayList<>();
        /** 推断出的核心兴趣（相对强度 ≥25%，最多6个） */
        public final List<String> topTags = new ArrayList<>();
        /** 常去城市：[{city, count}] */
        public final List<Map<String, Object>> cities = new ArrayList<>();
        /** 人类可读的证据，前端直接展示 */
        public final List<String> evidence = new ArrayList<>();
        public Integer expectDays;
        public Double budget;
        public String transport;
        public String paceType;
        public String destinationPref;
        public int signalCount;
        public String confidence = "NONE";
        public LocalDateTime computedAt;

        public boolean isEmpty() {
            return topTags.isEmpty() && cities.isEmpty();
        }

        /** 画像摘要（随偏好一起返回给前端） */
        public Map<String, Object> brief() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tags", topTags);
            m.put("confidence", confidence);
            m.put("signalCount", signalCount);
            m.put("evidence", evidence);
            return m;
        }
    }
}
