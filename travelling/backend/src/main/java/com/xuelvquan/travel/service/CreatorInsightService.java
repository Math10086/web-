package com.xuelvquan.travel.service;

import com.xuelvquan.travel.entity.Post;
import com.xuelvquan.travel.entity.User;
import com.xuelvquan.travel.repository.PostRepository;
import com.xuelvquan.travel.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 博主创作数据面板服务（博客分、互动统计与选题建议）
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorInsightService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final HotService hotService;

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    public Map<String, Object> insight(Long userId) {
        User me = userRepository.findById(userId)
                .orElseThrow(() -> new com.xuelvquan.travel.common.BusinessException("用户不存在"));

        List<Post> all = postRepository.findByAuthorIdOrderByCreatedAtDesc(userId);
        List<Post> approved = all.stream().filter(p -> "APPROVED".equals(p.getStatus())).toList();

        long views = approved.stream().mapToLong(p -> nz(p.getViewCount())).sum();
        long likes = approved.stream().mapToLong(p -> nz(p.getLikeCount())).sum();
        long favorites = approved.stream().mapToLong(p -> nz(p.getFavoriteCount())).sum();
        long citations = approved.stream().mapToLong(p -> nz(p.getCitationCount())).sum();
        long comments = approved.stream().mapToLong(p -> nz(p.getCommentCount())).sum();
        // 与HotService.bloggers保持同一套权重，保证面板上的分值和热度榜一致
        long score = views + likes * 2 + favorites * 3 + citations * 5;
        // 等级阈值只有HotService.LEVEL_THRESHOLDS一份，这里不再重复维护
        int level = HotService.levelOfScore(score);
        long floor = HotService.levelFloorOf(level);
        Long levelCeiling = HotService.levelCeilingOf(level);

        Map<String, Object> meStat = new LinkedHashMap<>();
        meStat.put("score", score);
        meStat.put("level", level);
        meStat.put("levelName", hotService.levelNameOf(level));
        meStat.put("rank", rankOf(userId, score));
        meStat.put("rankTotal", bloggerTotal());
        meStat.put("postCount", approved.size());
        meStat.put("draftCount", all.size() - approved.size());
        meStat.put("views", views);
        meStat.put("likes", likes);
        meStat.put("favorites", favorites);
        meStat.put("citations", citations);
        meStat.put("comments", comments);
        meStat.put("avgViews", approved.isEmpty() ? 0 : Math.round(views * 10.0 / approved.size()) / 10.0);
        // 每千次阅读带来的收藏数：衡量「内容是否真的有用」，不受曝光量影响
        meStat.put("favoriteRate", views == 0 ? 0 : round2(favorites * 100.0 / views));
        meStat.put("nextLevelScore", levelCeiling);
        meStat.put("nextLevelName", levelCeiling == null ? null : hotService.levelNameOf(level + 1));
        // 进度条区间由后端算好下发，前端不再自己维护一份等级阈值
        meStat.put("levelFloor", floor);
        meStat.put("levelCeiling", levelCeiling);
        meStat.put("levelProgress", levelCeiling == null
                ? 100
                : (int) Math.min(100, Math.round((score - floor) * 100.0 / (levelCeiling - floor))));

        // 逐篇表现：按浏览量降序，同时给出「每千次阅读收藏数」用于横向比较
        List<Map<String, Object>> posts = new ArrayList<>();
        for (Post p : approved.stream()
                .sorted(Comparator.comparingLong((Post p) -> nz(p.getViewCount())).reversed())
                .toList()) {
            int v = nz(p.getViewCount());
            int f = nz(p.getFavoriteCount());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("title", p.getTitle());
            m.put("city", p.getCity() != null ? p.getCity() : p.getDestination());
            m.put("type", p.getType());
            m.put("createdAt", p.getCreatedAt());
            m.put("views", v);
            m.put("likes", nz(p.getLikeCount()));
            m.put("favorites", f);
            m.put("citations", nz(p.getCitationCount()));
            m.put("comments", nz(p.getCommentCount()));
            m.put("favoriteRate", v == 0 ? 0 : round2(f * 100.0 / v));
            m.put("heat", v + nz(p.getLikeCount()) * 2L + f * 3L + nz(p.getCommentCount()) * 2L + nz(p.getCitationCount()) * 5L);
            posts.add(m);
        }

        // 按月份汇总浏览量，观察账号活跃趋势
        Map<String, long[]> byMonth = new TreeMap<>();
        for (Post p : approved) {
            if (p.getCreatedAt() == null) {
                continue;
            }
            String key = YearMonth.from(p.getCreatedAt()).format(MONTH);
            long[] cell = byMonth.computeIfAbsent(key, k -> new long[2]);
            cell[0]++;                       // 篇数
            cell[1] += nz(p.getViewCount()); // 浏览量
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        byMonth.forEach((k, v) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", k);
            m.put("posts", v[0]);
            m.put("views", v[1]);
            trend.add(m);
        });

        List<String> insights = buildInsights(meStat, posts, trend, approved);
        List<Map<String, Object>> topicIdeas = topicIdeas(userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", brief(me));
        result.put("me", meStat);
        result.put("posts", posts);
        result.put("trend", trend);
        result.put("insights", insights);
        result.put("topicIdeas", topicIdeas);
        return result;
    }

    // ==================== 结论文案 ====================

    private List<String> buildInsights(Map<String, Object> meStat,
                                       List<Map<String, Object>> posts,
                                       List<Map<String, Object>> trend,
                                       List<Post> approved) {
        List<String> out = new ArrayList<>();
        if (approved.isEmpty()) {
            out.add("还没有已通过审核的帖子——先发一篇，面板就会有数据了");
            return out;
        }

        Map<String, Object> best = posts.get(0);
        out.add("《" + shorten(String.valueOf(best.get("title")), 16) + "》是你的爆款："
                + best.get("views") + " 次浏览、收藏" + best.get("favorites")
                + " 次，同类选题可以继续做");

        // 找「阅读高但收藏低」和「阅读低但收藏高」的两类典型
        List<Map<String, Object>> withViews = posts.stream()
                .filter(p -> ((Number) p.get("views")).intValue() >= 50)
                .toList();
        if (withViews.size() >= 2) {
            Map<String, Object> efficient = withViews.stream()
                    .max(Comparator.comparingDouble(p -> ((Number) p.get("favoriteRate")).doubleValue()))
                    .orElse(null);
            Map<String, Object> inefficient = withViews.stream()
                    .min(Comparator.comparingDouble(p -> ((Number) p.get("favoriteRate")).doubleValue()))
                    .orElse(null);
            if (efficient != null && inefficient != null
                    && Double.compare(((Number) efficient.get("favoriteRate")).doubleValue(),
                    ((Number) inefficient.get("favoriteRate")).doubleValue()) != 0) {
                out.add("《" + shorten(String.valueOf(efficient.get("title")), 12) + "》每千次阅读收藏 "
                        + efficient.get("favoriteRate") + " 次，是最「有用」的一篇；"
                        + "《" + shorten(String.valueOf(inefficient.get("title")), 12) + "》只有 "
                        + inefficient.get("favoriteRate") + " 次，建议补充可落地的清单或费用明细");
            }
        }

        if (trend.size() >= 2) {
            Map<String, Object> last = trend.get(trend.size() - 1);
            Map<String, Object> prev = trend.get(trend.size() - 2);
            long lv = ((Number) last.get("views")).longValue();
            long pv = ((Number) prev.get("views")).longValue();
            if (pv > 0) {
                int delta = (int) Math.round((lv - pv) * 100.0 / pv);
                out.add(delta >= 0
                        ? "最近一个月浏览" + lv + " 次，比上月增长" + delta + "%，保持更新节奏"
                        : "最近一个月浏览" + lv + " 次，比上月下滑" + Math.abs(delta) + "%，可参考下方升温话题调整选题");
            }
        }

        Object next = meStat.get("nextLevelScore");
        if (next != null) {
            long gap = ((Number) next).longValue() - ((Number) meStat.get("score")).longValue();
            out.add("距「" + meStat.get("nextLevelName") + "」还差" + gap + " 分"
                    + "（1次收藏 = 3分，1次被引用 = 5分，写能被别人引用的攻略最划算）");
        } else {
            out.add("已是平台最高等级「" + meStat.get("levelName") + "」，可以尝试带新博主或联名线路");
        }
        return out;
    }

    // ==================== 选题建议 ====================

    /**
     * 推荐站内升温、但本人尚未写过的主题词
     * @param userId 用户ID
     * @return List 含keyword（主题词）、category（类别）、heat（热度）、reason（推荐理由）
     */
    private List<Map<String, Object>> topicIdeas(Long userId) {
        List<Post> mine = postRepository.findByAuthorIdOrderByCreatedAtDesc(userId);
        String corpus = mine.stream()
                .map(p -> s(p.getTitle()) + " " + s(p.getContent()))
                .reduce("", (a, b) -> a + " " + b);

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> kw : hotService.keywords(40)) {
            String name = String.valueOf(kw.get("name"));
            if (corpus.contains(name)) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("keyword", name);
            m.put("category", kw.get("category"));
            m.put("heat", kw.get("value"));
            m.put("reason", "站内热度" + kw.get("value") + "，你还没写过这个主题");
            out.add(m);
            if (out.size() >= 5) {
                break;
            }
        }
        return out;
    }

    // ==================== 工具 ====================

    /**
     * 博主当前在全站博主中的名次（同分并列按同一名次处理）
     * @param userId 博主用户ID
     * @param myScore 该博主的博客分
     * @return int 名次，从1开始
     */
    private int rankOf(Long userId, long myScore) {
        long higher = 0;
        for (User u : userRepository.findAll()) {
            if (u.getId().equals(userId)) {
                continue;
            }
            List<Post> mine = postRepository.findByAuthorIdOrderByCreatedAtDesc(u.getId()).stream()
                    .filter(p -> "APPROVED".equals(p.getStatus())).toList();
            if (mine.isEmpty() && !"BLOGGER".equals(u.getRole())) {
                continue;
            }
            long sc = mine.stream().mapToLong(p -> nz(p.getViewCount()) + nz(p.getLikeCount()) * 2L
                    + nz(p.getFavoriteCount()) * 3L + nz(p.getCitationCount()) * 5L).sum();
            if (sc > myScore) {
                higher++;
            }
        }
        return (int) higher + 1;
    }

    private int bloggerTotal() {
        Set<Long> ids = new LinkedHashSet<>();
        for (Post p : postRepository.findAll()) {
            if ("APPROVED".equals(p.getStatus()) && p.getAuthorId() != null) {
                ids.add(p.getAuthorId());
            }
        }
        for (User u : userRepository.findAll()) {
            if ("BLOGGER".equals(u.getRole())) {
                ids.add(u.getId());
            }
        }
        return ids.size();
    }

    private Map<String, Object> brief(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("nickname", u.getNickname());
        m.put("avatar", u.getAvatar());
        m.put("bio", u.getBio());
        m.put("role", u.getRole());
        return m;
    }

    private String shorten(String v, int max) {
        if (v == null) return "";
        return v.length() <= max ? v : v.substring(0, max) + "…";
    }

    private int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private String s(String v) {
        return v == null ? "" : v;
    }
}
