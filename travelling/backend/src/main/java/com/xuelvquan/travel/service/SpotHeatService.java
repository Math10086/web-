package com.xuelvquan.travel.service;

import com.xuelvquan.travel.entity.Footprint;
import com.xuelvquan.travel.entity.Interaction;
import com.xuelvquan.travel.entity.Post;
import com.xuelvquan.travel.entity.Spot;
import com.xuelvquan.travel.entity.Trip;
import com.xuelvquan.travel.repository.FootprintRepository;
import com.xuelvquan.travel.repository.InteractionRepository;
import com.xuelvquan.travel.repository.PostRepository;
import com.xuelvquan.travel.repository.SpotRepository;
import com.xuelvquan.travel.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 景点人流热度合成服务。
 *
 * 热度不再取spots.heat这个静态字段，而是由平台真实行为事件实时算出来：
 * 
 *   - 足迹打卡×5（有spotId直接命中；自定义地点按「城市+名称」反查）
 *   - 帖子提及×3（标题/正文/目的地里出现景点名）
 *   - 收藏×2（被收藏的帖子里提到的景点）
 *   - 结伴目的地×4（目的地命中景点名）；只命中城市时×1.5
 * 
 * 每条事件按时间衰减：30天内1.0、90天内0.6、180天内0.35、更早0.2。
 *
 * 合成口径：<b>综合热度=行为分×0.65+基础知名度×0.35</b>，
 * 两项各自归一化到0~100（行为分以全库最高行为量为100，基础知名度以库存heat最高值为100）。
 * 保留基础知名度是为了让"刚上架、还没人打卡"的景点不至于直接显示0，页面上会标注口径。
 */
@Service
@RequiredArgsConstructor
public class SpotHeatService {

    private final SpotRepository spotRepository;
    private final FootprintRepository footprintRepository;
    private final PostRepository postRepository;
    private final InteractionRepository interactionRepository;
    private final TripRepository tripRepository;

    /** 事件权重 */
    private static final double W_FOOTPRINT = 5.0;
    private static final double W_POST_MENTION = 3.0;
    private static final double W_FAVORITE = 2.0;
    private static final double W_TRIP_SPOT = 4.0;
    private static final double W_TRIP_CITY = 1.5;

    /** 行为分/基础知名度 的合成比例 */
    private static final double RATIO_BEHAVIOR = 0.65;
    private static final double RATIO_BASE = 0.35;

    /** 行为快照缓存60秒，避免每次列景点都全表扫一遍行为表 */
    private static final long CACHE_MILLIS = 60_000L;

    private volatile Snapshot cache;
    private volatile long cacheAt;

    /** 单个景点的累计结果 */
    private static final class Acc {
        double raw;
        int footprint;
        int mention;
        int favorite;
        int trip;
    }

    private record Snapshot(Map<Long, Acc> accs, double maxRaw, int maxBase) {
    }

    // ==================== 对外方法 ====================

    /**
     * 给景点列表补上实时热度
     * @param spots 原始景点列表
     * @return List 带实时热度的新景点列表（不修改入参）
     */
    public List<Spot> decorate(List<Spot> spots) {
        if (spots == null || spots.isEmpty()) {
            return spots;
        }
        Snapshot snap = snapshot();
        List<Spot> out = new ArrayList<>(spots.size());
        for (Spot s : spots) {
            Spot copy = new Spot();
            BeanUtils.copyProperties(s, copy);
            apply(copy, snap);
            out.add(copy);
        }
        return out;
    }

    /**
     * 给单个景点补上实时热度
     * @param spot 原始景点
     * @return Spot 带实时热度的新景点对象
     */
    public Spot decorate(Spot spot) {
        if (spot == null) {
            return null;
        }
        Spot copy = new Spot();
        BeanUtils.copyProperties(spot, copy);
        apply(copy, snapshot());
        return copy;
    }

    /** 行为数据变动后（新增足迹/帖子/收藏/结伴、管理员新增景点）主动失效缓存 */
    public void invalidate() {
        cache = null;
        cacheAt = 0L;
    }

    /** 口径说明，供前端直接展示 */
    public Map<String, Object> formula() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("formula", "综合热度 = 行为分 × 0.65+ 基础知名度 × 0.35");
        m.put("weights", Map.of(
                "footprint", W_FOOTPRINT,
                "postMention", W_POST_MENTION,
                "favorite", W_FAVORITE,
                "tripSpot", W_TRIP_SPOT,
                "tripCity", W_TRIP_CITY));
        m.put("decay", Map.of("d30", 1.0, "d90", 0.6, "d180", 0.35, "older", 0.2));
        Snapshot snap = snapshot();
        int withBehavior = 0;
        for (Acc a : snap.accs().values()) {
            if (a.raw > 0) {
                withBehavior++;
            }
        }
        m.put("spotsWithBehavior", withBehavior);
        m.put("spotsTotal", snap.accs().size());
        m.put("updatedAt", LocalDateTime.now().toString());
        return m;
    }

    // ==================== 计算 ====================

    private void apply(Spot s, Snapshot snap) {
        Acc a = snap.accs().get(s.getId());
        double raw = a == null ? 0 : a.raw;
        int base = s.getHeat() == null ? 0 : s.getHeat();

        double behavior = snap.maxRaw() > 0 ? raw / snap.maxRaw() * 100 : 0;
        double baseScore = snap.maxBase() > 0 ? (double) base / snap.maxBase() * 100 : 0;
        int heat = (int) Math.round(behavior * RATIO_BEHAVIOR + baseScore * RATIO_BASE);
        // 有基础知名度但行为少的老景点不要被抹成0（0会被前端当成「无数据」过滤掉）
        if (heat == 0 && base > 0) {
            heat = 1;
        }

        s.setHeat(heat);
        s.setBaseHeat(base);
        s.setHeatFootprint(a == null ? 0 : a.footprint);
        s.setHeatMention(a == null ? 0 : a.mention);
        s.setHeatFavorite(a == null ? 0 : a.favorite);
        s.setHeatTrip(a == null ? 0 : a.trip);
        s.setHeatRaw(Math.round(raw * 10) / 10.0);
        s.setHeatSource(heat > 0 ? "BEHAVIOR" : "NONE");
    }

    private Snapshot snapshot() {
        long now = System.currentTimeMillis();
        Snapshot snap = cache;
        if (snap != null && now - cacheAt < CACHE_MILLIS) {
            return snap;
        }
        synchronized (this) {
            if (cache != null && System.currentTimeMillis() - cacheAt < CACHE_MILLIS) {
                return cache;
            }
            Snapshot built = build();
            cache = built;
            cacheAt = System.currentTimeMillis();
            return built;
        }
    }

    private Snapshot build() {
        List<Spot> spots = spotRepository.findAll();
        Map<Long, Acc> accs = new HashMap<>();
        Map<String, List<Spot>> byName = new HashMap<>();
        Map<String, List<Spot>> byCity = new HashMap<>();
        int maxBase = 1;
        for (Spot s : spots) {
            accs.put(s.getId(), new Acc());
            if (s.getName() != null) {
                byName.computeIfAbsent(s.getName().trim(), k -> new ArrayList<>()).add(s);
            }
            if (s.getCity() != null) {
                byCity.computeIfAbsent(s.getCity().trim(), k -> new ArrayList<>()).add(s);
            }
            if (s.getHeat() != null && s.getHeat() > maxBase) {
                maxBase = s.getHeat();
            }
        }

        // ①足迹打卡
        for (Footprint f : footprintRepository.findAll()) {
            Spot hit = matchFootprint(f, spots, byName);
            if (hit == null) {
                continue;
            }
            Acc a = accs.get(hit.getId());
            a.raw += W_FOOTPRINT * decay(f.getVisitedAt() == null
                    ? f.getCreatedAt() : f.getVisitedAt().atStartOfDay());
            a.footprint++;
        }

        // ②帖子提及（顺便记下「帖子 → 提及的景点」，给收藏用）
        Map<Long, List<Long>> postSpots = new HashMap<>();
        for (Post p : postRepository.findByStatusOrderByCreatedAtDesc("APPROVED")) {
            String text = nullToEmpty(p.getTitle()) + '\u0001' + nullToEmpty(p.getContent())
                    + '\u0001' + nullToEmpty(p.getDestination());
            List<Long> hitIds = new ArrayList<>();
            for (Spot s : spots) {
                String name = s.getName() == null ? "" : s.getName().trim();
                if (name.length() < 2) {
                    continue;
                }
                if (text.contains(name)) {
                    Acc a = accs.get(s.getId());
                    a.raw += W_POST_MENTION * decay(p.getCreatedAt());
                    a.mention++;
                    hitIds.add(s.getId());
                }
            }
            if (!hitIds.isEmpty()) {
                postSpots.put(p.getId(), hitIds);
            }
        }

        // ③收藏：被收藏的帖子里的景点
        for (Interaction it : interactionRepository.findByType("FAVORITE")) {
            List<Long> ids = postSpots.get(it.getTargetId());
            if (ids == null) {
                continue;
            }
            for (Long sid : ids) {
                Acc a = accs.get(sid);
                a.raw += W_FAVORITE * decay(it.getCreatedAt());
                a.favorite++;
            }
        }

        // ④结伴目的地
        for (Trip t : tripRepository.findAll()) {
            String dest = nullToEmpty(t.getDestination()).trim();
            if (dest.isEmpty()) {
                continue;
            }
            for (Spot s : spots) {
                String name = s.getName() == null ? "" : s.getName().trim();
                Acc a = accs.get(s.getId());
                if (name.length() >= 2 && dest.contains(name)) {
                    a.raw += W_TRIP_SPOT * decay(t.getCreatedAt());
                    a.trip++;
                } else if (Objects.equals(dest, s.getCity()) && spotsOfCity(byCity, dest).size() > 1) {
                    // 仅命中城市：分摊到该城市各景点，权重低
                    a.raw += W_TRIP_CITY * decay(t.getCreatedAt()) / spotsOfCity(byCity, dest).size();
                    a.trip++;
                }
            }
        }

        double maxRaw = 0;
        for (Acc a : accs.values()) {
            if (a.raw > maxRaw) {
                maxRaw = a.raw;
            }
        }
        return new Snapshot(accs, maxRaw, maxBase);
    }

    /**
     * 足迹归属：优先spotId，其次「城市+名称」精确匹配，最后全局同名
     * @param f 足迹记录
     * @param spots 全部景点
     * @param byName 景点名到同名景点列表的索引
     * @return Spot 归一到的景点；无法归属时返回null
     */
    private Spot matchFootprint(Footprint f, List<Spot> spots, Map<String, List<Spot>> byName) {
        if (f.getSpotId() != null) {
            for (Spot s : spots) {
                if (Objects.equals(s.getId(), f.getSpotId())) {
                    return s;
                }
            }
        }
        String name = f.getName() == null ? "" : f.getName().trim();
        if (name.isEmpty()) {
            return null;
        }
        List<Spot> sameName = byName.get(name);
        if (sameName == null || sameName.isEmpty()) {
            return null;
        }
        for (Spot s : sameName) {
            if (Objects.equals(s.getCity(), f.getCity())) {
                return s;
            }
        }
        return sameName.get(0);
    }

    private List<Spot> spotsOfCity(Map<String, List<Spot>> byCity, String city) {
        List<Spot> list = byCity.get(city);
        return list == null ? List.of() : list;
    }

    /**
     * 时间衰减：越近的行为权重越高
     * @param at 行为发生时间，为空时按较早的档位处理
     * @return double 衰减系数（1.0/0.6/0.35/0.2）
     */
    private double decay(LocalDateTime at) {
        if (at == null) {
            return 0.35;
        }
        long days = Duration.between(at, LocalDateTime.now()).toDays();
        if (days <= 30) {
            return 1.0;
        }
        if (days <= 90) {
            return 0.6;
        }
        if (days <= 180) {
            return 0.35;
        }
        return 0.2;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
