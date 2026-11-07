package com.xuelvquan.travel.service;

import com.xuelvquan.travel.entity.Circle;
import com.xuelvquan.travel.entity.Footprint;
import com.xuelvquan.travel.entity.Post;
import com.xuelvquan.travel.entity.Route;
import com.xuelvquan.travel.entity.Spot;
import com.xuelvquan.travel.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final TripRepository tripRepository;
    private final ProductOrderRepository orderRepository;
    private final GuideBookingRepository bookingRepository;
    private final CircleRepository circleRepository;
    private final MerchantRepository merchantRepository;
    private final SpotRepository spotRepository;
    private final FootprintRepository footprintRepository;
    private final RouteRepository routeRepository;

    private static final Map<String, String> CATEGORY_NAMES = Map.of(
            "NATURE", "自然风光",
            "HISTORY", "历史人文",
            "TRENDY", "网红打卡",
            "FOOD", "美食探店",
            "SECRET", "小众野路",
            "THEME", "主题乐园"
    );

    private static final List<String> HOT_WORDS = List.of(
            "长沙", "张家界", "重庆", "成都", "西安", "厦门", "武汉", "广州",
            "Citywalk", "露营", "徒步", "美食", "摄影", "避坑", "特种兵",
            "野路", "玻璃栈道", "熊猫", "火锅", "夜爬", "古镇", "看海",
            "岳麓山", "橘子洲", "洪崖洞", "宽窄巷子", "大唐不夜城", "天门山"
    );

    public Map<String, Object> overview() {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userCount", userRepository.count());
        m.put("postCount", postRepository.count());
        m.put("tripCount", tripRepository.count());
        m.put("routeCount", routeRepository.count());
        m.put("footprintCount", footprintRepository.count());
        m.put("spotCount", spotRepository.count());
        m.put("orderCount", orderRepository.count());
        m.put("bookingCount", bookingRepository.count());
        m.put("circleCount", circleRepository.count());
        m.put("merchantCount", merchantRepository.count());
        m.put("newUsersToday", userRepository.countByCreatedAtAfter(startOfToday));
        m.put("newPostsToday", postRepository.countByCreatedAtAfter(startOfToday));
        return m;
    }

    public Map<String, Object> spotHeat() {
        List<Spot> spots = spotRepository.findAll();
        Map<String, Long> pie = new LinkedHashMap<>();
        for (Spot s : spots) {
            pie.merge(s.getCategory(), 1L, Long::sum);
        }
        List<Map<String, Object>> categoryPie = new ArrayList<>();
        pie.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> categoryPie.add(Map.of(
                        "name", CATEGORY_NAMES.getOrDefault(e.getKey(), e.getKey()),
                        "value", e.getValue())));

        List<Map<String, Object>> topSpots = spots.stream()
                .sorted(Comparator.comparing(Spot::getHeat, Comparator.nullsLast(Comparator.<Integer>reverseOrder())))
                .limit(10)
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", s.getId());
                    m.put("name", s.getName());
                    m.put("city", s.getCity());
                    m.put("heat", s.getHeat());
                    return m;
                })
                .toList();

        return Map.of("categoryPie", categoryPie, "topSpots", topSpots);
    }

    public List<Map<String, Object>> circleActivity() {
        List<Circle> circles = circleRepository.findAllOrdered();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Circle c : circles) {
            long postCount = c.getId() == null ? 0
                    : postRepository.findByCircleId(c.getId()).stream()
                            .filter(p -> "APPROVED".equals(p.getStatus())).count();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("memberCount", c.getMemberCount());
            m.put("postCount", postCount);
            result.add(m);
        }
        return result;
    }

    public List<Map<String, Object>> postTrend() {
        List<Post> posts = postRepository.findAll();
        LocalDate today = LocalDate.now();
        Map<LocalDate, Long> counter = new TreeMap<>();
        for (int i = 6; i >= 0; i--) {
            counter.put(today.minusDays(i), 0L);
        }
        for (Post p : posts) {
            if (p.getCreatedAt() == null) {
                continue;
            }
            LocalDate d = p.getCreatedAt().toLocalDate();
            if (counter.containsKey(d)) {
                counter.merge(d, 1L, Long::sum);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        counter.forEach((d, c) -> result.add(Map.of("date", d.toString(), "count", c)));
        return result;
    }

    public List<Map<String, Object>> hotWords() {
        List<Post> posts = postRepository.findAll().stream()
                .filter(p -> "APPROVED".equals(p.getStatus())).toList();
        Map<String, Integer> scores = new LinkedHashMap<>();
        HOT_WORDS.forEach(w -> scores.put(w, 0));
        for (Post p : posts) {
            String title = nullToEmpty(p.getTitle());
            String city = nullToEmpty(p.getCity());
            String dest = nullToEmpty(p.getDestination());
            String content = nullToEmpty(p.getContent());
            for (String w : HOT_WORDS) {
                if (title.contains(w)) scores.merge(w, 3, Integer::sum);
                if (city.contains(w) || dest.contains(w)) scores.merge(w, 3, Integer::sum);
                if (content.contains(w)) scores.merge(w, 1, Integer::sum);
            }
        }
        return scores.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(20)
                .map(e -> Map.<String, Object>of("name", e.getKey(), "value", e.getValue()))
                .toList();
    }

    public List<Map<String, Object>> hotSearch() {
        List<Post> posts = postRepository.findAll().stream()
                .filter(p -> "APPROVED".equals(p.getStatus())).toList();
        Map<String, Long> scores = new LinkedHashMap<>();
        for (Post p : posts) {
            long hot = nz(p.getViewCount()) + nz(p.getLikeCount()) * 2L
                    + nz(p.getFavoriteCount()) * 3L + nz(p.getCitationCount()) * 5L;
            String text = nullToEmpty(p.getTitle()) + nullToEmpty(p.getCity()) + nullToEmpty(p.getDestination());
            for (String w : HOT_WORDS) {
                if (text.contains(w)) {
                    scores.merge(w, hot + 1, Long::sum);
                }
            }
        }
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> Map.<String, Object>of("name", e.getKey(), "value", e.getValue()))
                .toList();
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** 各城市活跃人数：去过（足迹）/ 发过帖/存过路线 的去重用户数+内容量 */
    public List<Map<String, Object>> cityActivity() {
        // 城市名单以景点库为准
        List<String> cities = spotRepository.findAll().stream()
                .map(Spot::getCity)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .toList();

        Map<String, Set<Long>> users = new LinkedHashMap<>();
        Map<String, int[]> counts = new LinkedHashMap<>();
        for (String c : cities) {
            users.put(c, new HashSet<>());
            counts.put(c, new int[3]); // 三个计数依次为：帖子数、足迹数、路线数
        }

        for (Post p : postRepository.findAll()) {
            int[] cnt = counts.get(p.getCity());
            if (cnt != null) {
                cnt[0]++;
                if (p.getAuthorId() != null) users.get(p.getCity()).add(p.getAuthorId());
            }
        }
        for (Footprint f : footprintRepository.findAll()) {
            int[] cnt = counts.get(f.getCity());
            if (cnt != null) {
                cnt[1]++;
                if (f.getUserId() != null) users.get(f.getCity()).add(f.getUserId());
            }
        }
        for (Route r : routeRepository.findAll()) {
            int[] cnt = counts.get(r.getCity());
            if (cnt != null) {
                cnt[2]++;
                if (r.getAuthorId() != null) users.get(r.getCity()).add(r.getAuthorId());
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (String c : cities) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("city", c);
            m.put("activeUsers", users.get(c).size());
            m.put("postCount", counts.get(c)[0]);
            m.put("footprintCount", counts.get(c)[1]);
            m.put("routeCount", counts.get(c)[2]);
            result.add(m);
        }
        result.sort(Comparator.comparing(m -> -((Number) m.get("activeUsers")).longValue()));
        return result;
    }

    /** 景点到访人次：足迹打卡数（含无打卡记录的按热度折算展示） */
    public List<Map<String, Object>> spotVisits() {
        Map<Long, Long> visitCounter = new LinkedHashMap<>();
        for (Footprint f : footprintRepository.findAll()) {
            if (f.getSpotId() != null) {
                visitCounter.merge(f.getSpotId(), 1L, Long::sum);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Spot s : spotRepository.findAll()) {
            long visits = visitCounter.getOrDefault(s.getId(), 0L);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("spotId", s.getId());
            m.put("name", s.getName());
            m.put("city", s.getCity());
            m.put("visits", visits);
            m.put("heat", s.getHeat() == null ? 0 : s.getHeat());
            result.add(m);
        }
        // 排序：实际打卡优先，其次热度
        result.sort(Comparator
                .comparing((Map<String, Object> m) -> -((Number) m.get("visits")).longValue())
                .thenComparing(m -> -((Number) m.get("heat")).longValue()));
        return result.size() > 12 ? result.subList(0, 12) : result;
    }

    /** 路线选择排行榜：按模式/按城市 的保存数量 */
    public Map<String, Object> routeRanking() {
        Map<String, Long> byMode = new LinkedHashMap<>();
        Map<String, Long> byCity = new LinkedHashMap<>();
        for (Route r : routeRepository.findAll()) {
            if (r.getMode() != null) byMode.merge(r.getMode(), 1L, Long::sum);
            if (r.getCity() != null) byCity.merge(r.getCity(), 1L, Long::sum);
        }
        List<Map<String, Object>> modeRank = byMode.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("mode", e.getKey());
                    m.put("name", ROUTE_MODE_NAMES.getOrDefault(e.getKey(), e.getKey()));
                    m.put("count", e.getValue());
                    return m;
                }).toList();
        List<Map<String, Object>> cityRank = byCity.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("city", e.getKey());
                    m.put("count", e.getValue());
                    return m;
                }).toList();
        return Map.of("modeRank", modeRank, "cityRank", cityRank);
    }

    private static final Map<String, String> ROUTE_MODE_NAMES = Map.of(
            "OPTIMAL", "最优路线",
            "SPEED", "特种兵速通",
            "LEISURE", "休闲慢游",
            "FOOD", "美食专线",
            "SCENIC", "景点专线",
            "WILD", "刺激野路",
            "CITYWALK", "城市漫步"
    );

    private int nz(Integer i) {
        return i == null ? 0 : i;
    }
}
