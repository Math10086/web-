package com.xuelvquan.travel.service;

import com.xuelvquan.travel.entity.Circle;
import com.xuelvquan.travel.entity.Footprint;
import com.xuelvquan.travel.entity.Post;
import com.xuelvquan.travel.entity.Route;
import com.xuelvquan.travel.entity.Spot;
import com.xuelvquan.travel.entity.Trip;
import com.xuelvquan.travel.entity.User;
import com.xuelvquan.travel.repository.CircleRepository;
import com.xuelvquan.travel.repository.FootprintRepository;
import com.xuelvquan.travel.repository.PostRepository;
import com.xuelvquan.travel.repository.RouteRepository;
import com.xuelvquan.travel.repository.SpotRepository;
import com.xuelvquan.travel.repository.TripRepository;
import com.xuelvquan.travel.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 热度中心（热搜与词云+博主评级与推荐）。
 *
 * 全部基于平台自身的浏览/点赞/收藏/引用行为计算，规则可解释：
 * 热帖热度=浏览×1+点赞×2+收藏×3+评论×2+引用×5；
 * 词云权重=标题命中×3+城市/目的地/模式命中×2+正文命中×1；
 * 博主评级=浏览量×1+获赞×2+收藏×3+引用×5，并按分值划分等级。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HotService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final RouteRepository routeRepository;
    private final SpotRepository spotRepository;
    private final CircleRepository circleRepository;
    private final FootprintRepository footprintRepository;

    /** 主题关键词 → 分类（用于词云着色与说明） */
    private static final Map<String, String> THEME_WORDS = new LinkedHashMap<>();

    static {
        THEME_WORDS.put("美食", "FOOD");
        THEME_WORDS.put("小吃", "FOOD");
        THEME_WORDS.put("火锅", "FOOD");
        THEME_WORDS.put("探店", "FOOD");
        THEME_WORDS.put("徒步", "OUTDOOR");
        THEME_WORDS.put("露营", "OUTDOOR");
        THEME_WORDS.put("登山", "OUTDOOR");
        THEME_WORDS.put("野路", "OUTDOOR");
        THEME_WORDS.put("日出", "OUTDOOR");
        THEME_WORDS.put("云海", "OUTDOOR");
        THEME_WORDS.put("Citywalk", "ACTIVITY");
        THEME_WORDS.put("特种兵", "ACTIVITY");
        THEME_WORDS.put("夜游", "ACTIVITY");
        THEME_WORDS.put("摄影", "ACTIVITY");
        THEME_WORDS.put("古镇", "SCENIC");
        THEME_WORDS.put("博物馆", "SCENIC");
        THEME_WORDS.put("玻璃栈道", "SCENIC");
        THEME_WORDS.put("避坑", "GUIDE");
        THEME_WORDS.put("攻略", "GUIDE");
        THEME_WORDS.put("路线", "GUIDE");
        THEME_WORDS.put("预算", "GUIDE");
        THEME_WORDS.put("结伴", "SOCIAL");
        THEME_WORDS.put("搭子", "SOCIAL");
    }

    private static final Map<String, String> LEVEL_NAMES = Map.of(
            "0", "新星",
            "1", "青铜博主",
            "2", "白银博主",
            "3", "黄金博主",
            "4", "铂金博主",
            "5", "钻石博主"
    );

    /**
     * 博主等级阈值（<b>全平台唯一一份</b>）：下标即等级，值为进入该等级所需的博客分下限。
     * 等级0「新星」无门槛，故数组首位为0。
     *
     * 博主评级榜、创作者中心的等级判定与升级进度都从这里取，
     * 前端也通过接口拿到同一组数（见CreatorInsightService的levelLadder），
     * 任何调档只需改这一个数组。
     */
    public static final long[] LEVEL_THRESHOLDS = {0L, 80L, 300L, 800L, 2000L, 5000L};

    // ==================== 3.12热搜榜 ====================

    /**
     * 热帖榜：平台内互动量最高的帖子（帖子+作者+热度分）
     * @param limit 返回条数上限，至少按1条处理
     * @return List 热帖列表，按热度倒序，含作者概要
     */
    public List<Map<String, Object>> hotPosts(int limit) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<Post> posts = postRepository.findAll().stream()
                .filter(p -> "APPROVED".equals(p.getStatus()))
                .sorted(Comparator.comparingLong(this::postHeat).reversed())
                .limit(Math.max(1, limit))
                .toList();
        int rank = 1;
        for (Post p : posts) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rank", rank++);
            m.put("id", p.getId());
            m.put("title", p.getTitle());
            m.put("type", p.getType());
            m.put("city", p.getCity());
            m.put("destination", p.getDestination());
            m.put("views", nz(p.getViewCount()));
            m.put("likes", nz(p.getLikeCount()));
            m.put("favorites", nz(p.getFavoriteCount()));
            m.put("comments", nz(p.getCommentCount()));
            m.put("citations", nz(p.getCitationCount()));
            m.put("heat", postHeat(p));
            m.put("trustedBadge", Boolean.TRUE.equals(p.getTrustedBadge()));
            userRepository.findById(p.getAuthorId() == null ? -1L : p.getAuthorId())
                    .ifPresent(u -> m.put("author", briefUser(u)));
            result.add(m);
        }
        return result;
    }

    /**
     * 热搜词榜：由平台内容真实聚合的高频词，带检索热度值
     * @param limit 返回条数上限
     * @return List 热搜词列表，含词、热度值与所属分类
     */
    public List<Map<String, Object>> keywords(int limit) {
        Map<String, String> vocab = buildVocab();
        Map<String, Integer> counts = new LinkedHashMap<>();

        List<Post> posts = postRepository.findAll().stream()
                .filter(p -> "APPROVED".equals(p.getStatus())).toList();
        for (Post p : posts) {
            String title = s(p.getTitle());
            String body = s(p.getContent());
            String city = s(p.getCity());
            String dest = s(p.getDestination());
            String mode = s(p.getModeTag());
            for (String kw : vocab.keySet()) {
                int c = count(title, kw) * 3
                        + count(city, kw) * 2
                        + count(dest, kw) * 2
                        + count(mode, kw) * 2
                        + count(body, kw);
                if (c > 0) {
                    counts.merge(kw, c, Integer::sum);
                }
            }
        }
        for (Trip t : tripRepository.findAll()) {
            String dest = s(t.getDestination());
            for (String kw : vocab.keySet()) {
                if (dest.contains(kw)) {
                    counts.merge(kw, 2, Integer::sum);
                }
            }
        }
        for (Route r : routeRepository.findAll()) {
            String city = s(r.getCity());
            for (String kw : vocab.keySet()) {
                if (!city.isEmpty() && city.contains(kw)) {
                    counts.merge(kw, 1, Integer::sum);
                }
            }
        }

        List<Map<String, Object>> list = new ArrayList<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(Math.max(1, limit))
                .forEach(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", e.getKey());
                    m.put("value", e.getValue());
                    m.put("category", vocab.getOrDefault(e.getKey(), "OTHER"));
                    list.add(m);
                });
        return list;
    }

    /**
     * 热门目的地城市榜：内容量+结伴量+足迹量 综合
     * @param limit 返回条数上限
     * @return List 城市榜，含帖子、结伴、路线、足迹数量与综合热度
     */
    public List<Map<String, Object>> hotCities(int limit) {
        Map<String, long[]> agg = new LinkedHashMap<>(); // city -> [posts, trips, routes, footprints]
        Set<String> cities = new LinkedHashSet<>();
        for (Spot s : spotRepository.findAll()) {
            if (notBlank(s.getCity())) {
                cities.add(s.getCity());
            }
        }
        for (String c : cities) {
            agg.put(c, new long[4]);
        }
        for (Post p : postRepository.findAll()) {
            long[] a = agg.get(p.getCity());
            if (a != null) a[0]++;
        }
        for (Trip t : tripRepository.findAll()) {
            for (String c : cities) {
                if (s(t.getDestination()).contains(c)) agg.get(c)[1]++;
            }
        }
        for (Route r : routeRepository.findAll()) {
            long[] a = agg.get(r.getCity());
            if (a != null) a[2]++;
        }
        for (Footprint f : footprintRepository.findAll()) {
            long[] a = agg.get(f.getCity());
            if (a != null) a[3]++;
        }

        List<Map<String, Object>> result = new ArrayList<>();
        agg.forEach((city, a) -> {
            long heat = a[0] * 3 + a[1] * 5 + a[2] * 2 + a[3] * 4;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("city", city);
            m.put("postCount", a[0]);
            m.put("tripCount", a[1]);
            m.put("routeCount", a[2]);
            m.put("footprintCount", a[3]);
            m.put("heat", heat);
            result.add(m);
        });
        result.sort(Comparator.comparingLong(m -> -((Number) m.get("heat")).longValue()));
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }

    // ====================博主评级与推荐 ====================

    /**
     * 博主评级榜+精品推荐位（浏览量高、被引用量高者进入推荐位）
     * @param limit 榜单条数上限
     * @return Map 含list（带名次的榜单）与featured（推荐位，最多2人）
     */
    public Map<String, Object> bloggers(int limit) {
        // 候选：博主角色+有已通过内容的作者
        Set<Long> authorIds = new LinkedHashSet<>();
        for (Post p : postRepository.findAll()) {
            if ("APPROVED".equals(p.getStatus()) && p.getAuthorId() != null) {
                authorIds.add(p.getAuthorId());
            }
        }
        for (User u : userRepository.findAll()) {
            if ("BLOGGER".equals(u.getRole())) {
                authorIds.add(u.getId());
            }
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (Long uid : authorIds) {
            User u = userRepository.findById(uid).orElse(null);
            if (u == null) {
                continue;
            }
            List<Post> mine = postRepository.findByAuthorIdOrderByCreatedAtDesc(uid).stream()
                    .filter(p -> "APPROVED".equals(p.getStatus())).toList();
            long views = mine.stream().mapToLong(p -> nz(p.getViewCount())).sum();
            long likes = mine.stream().mapToLong(p -> nz(p.getLikeCount())).sum();
            long favorites = mine.stream().mapToLong(p -> nz(p.getFavoriteCount())).sum();
            long citations = mine.stream().mapToLong(p -> nz(p.getCitationCount())).sum();
            long score = views + likes * 2 + favorites * 3 + citations * 5;
            int level = levelOf(score);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("nickname", u.getNickname());
            m.put("avatar", u.getAvatar());
            m.put("school", u.getSchool());
            m.put("bio", u.getBio());
            m.put("role", u.getRole());
            m.put("postCount", mine.size());
            m.put("views", views);
            m.put("likes", likes);
            m.put("favorites", favorites);
            m.put("citations", citations);
            m.put("score", score);
            m.put("level", level);
            m.put("levelName", LEVEL_NAMES.getOrDefault(String.valueOf(level), "新星"));
            list.add(m);
        }
        list.sort(Comparator.comparingLong(m -> -((Number) m.get("score")).longValue()));
        List<Map<String, Object>> ranked = list.size() > limit ? new ArrayList<>(list.subList(0, limit)) : list;
        int r = 1;
        for (Map<String, Object> m : ranked) {
            m.put("rank", r++);
        }
        // 精品推荐位：引用量优先，其次浏览量
        List<Map<String, Object>> featured = ranked.stream()
                .filter(m -> ((Number) m.get("postCount")).longValue() > 0)
                .sorted(Comparator
                        .comparingLong((Map<String, Object> m) -> -((Number) m.get("citations")).longValue())
                        .thenComparingLong(m -> -((Number) m.get("views")).longValue()))
                .limit(2)
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", ranked);
        result.put("featured", featured);
        return result;
    }

    // ==================== 内部工具 ====================

    private Map<String, String> buildVocab() {
        Map<String, String> vocab = new LinkedHashMap<>();
        for (Spot sp : spotRepository.findAll()) {
            if (notBlank(sp.getCity())) {
                vocab.putIfAbsent(sp.getCity(), "CITY");
            }
            if (notBlank(sp.getName())) {
                vocab.putIfAbsent(sp.getName(), "SPOT");
            }
        }
        for (Trip t : tripRepository.findAll()) {
            if (notBlank(t.getDestination()) && t.getDestination().length() <= 6) {
                vocab.putIfAbsent(t.getDestination(), "CITY");
            }
        }
        for (Circle c : circleRepository.findAll()) {
            if (notBlank(c.getName())) {
                String kw = c.getName().replace("圈", "").replace("层", "").replace("组", "");
                if (kw.length() >= 2) {
                    vocab.putIfAbsent(kw, "THEME");
                }
            }
        }
        vocab.putAll(THEME_WORDS);
        return vocab;
    }

    private long postHeat(Post p) {
        return nz(p.getViewCount())
                + nz(p.getLikeCount()) * 2L
                + nz(p.getFavoriteCount()) * 3L
                + nz(p.getCommentCount()) * 2L
                + nz(p.getCitationCount()) * 5L;
    }

    private int levelOf(long score) {
        return levelOfScore(score);
    }

    /**
     * 按博客分判定等级（公开给创作者中心复用，避免阈值散落多份）
     * @param score 博客分，由浏览、点赞、收藏、引用加权得出
     * @return int 等级下标，0为最低级
     */
    public static int levelOfScore(long score) {
        for (int i = LEVEL_THRESHOLDS.length - 1; i >= 0; i--) {
            if (score >= LEVEL_THRESHOLDS[i]) {
                return i;
            }
        }
        return 0;
    }

    /**
     * 等级名称（新星/青铜博主 …），供创作面板展示
     * @param level 等级下标
     * @return String 等级名称，未知下标按「新星」返回
     */
    public String levelNameOf(int level) {
        return LEVEL_NAMES.getOrDefault(String.valueOf(level), "新星");
    }

    /**
     * 当前等级的下限分（进度条起点）
     * @param level 等级下标，超出范围时按边界值处理
     * @return long 该等级的下限分
     */
    public static long levelFloorOf(int level) {
        int i = Math.max(0, Math.min(level, LEVEL_THRESHOLDS.length - 1));
        return LEVEL_THRESHOLDS[i];
    }

    /**
     * 当前等级的下一档门槛；已是最高级返回null
     * @param level 等级下标
     * @return Long 下一档门槛分，已是最高级时返回null
     */
    public static Long levelCeilingOf(int level) {
        int next = level + 1;
        return next >= LEVEL_THRESHOLDS.length ? null : LEVEL_THRESHOLDS[next];
    }

    private Map<String, Object> briefUser(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("nickname", u.getNickname());
        m.put("avatar", u.getAvatar());
        m.put("school", u.getSchool());
        return m;
    }

    private int count(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isEmpty()) {
            return 0;
        }
        int c = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            c++;
            idx += needle.length();
        }
        return c;
    }

    private String s(String v) {
        return v == null ? "" : v;
    }

    private boolean notBlank(String v) {
        return v != null && !v.isBlank();
    }

    private int nz(Integer i) {
        return i == null ? 0 : i;
    }
}
