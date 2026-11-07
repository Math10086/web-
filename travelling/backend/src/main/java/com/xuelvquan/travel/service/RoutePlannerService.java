package com.xuelvquan.travel.service;

import com.xuelvquan.travel.common.BusinessException;
import com.xuelvquan.travel.entity.Spot;
import com.xuelvquan.travel.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 行程路线规划服务：支持单日与多日行程，按模式筛选景点、地理聚类分天，
 * 调用真实路网计算路线并生成时间轴与建议。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoutePlannerService {

    /** 各模式固定颜色（单日路线颜色） */
    public static final Map<String, String> COLORS = Map.of(
            "OPTIMAL", "#2F80ED",
            "SPEED", "#F5222D",
            "LEISURE", "#52C41A",
            "FOOD", "#FA8C16",
            "SCENIC", "#722ED1",
            "WILD", "#13C2C2",
            "CITYWALK", "#EB2F96"
    );

    /** 多日行程每日配色（下标即第1、2、3…天；前端按同一顺序生成样式） */
    public static final List<String> DAY_COLORS = List.of(
            "#2F80ED", "#F5222D", "#52C41A", "#722ED1", "#FA8C16", "#13C2C2", "#EB2F96"
    );

    private static final double WALK_SPEED_KMH = 4.5;
    private static final double RIDE_SPEED_KMH = 25.0;
    /** 超过该直线距离（km）的段落改用乘车 */
    private static final double WALK_LEG_LIMIT_KM = 6.0;
    /** 腾讯位置服务WebServiceAPI key */
    private static final String TENCENT_KEY = "BXNBZ-IUGOT-EAEXY-L22DC-BXEZE-Y6F7J";

    private final SpotRepository spotRepository;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** 逐段路线缓存：key -> {polyline, distanceKm, durationMin, source} */
    private final Map<String, CachedLeg> legCache = new ConcurrentHashMap<>();

    private record CachedLeg(List<List<Double>> polyline, double distanceKm, int durationMin, boolean fromApi) {}

    private record Leg(List<List<Double>> polyline, double distanceKm, int durationMin, String travelMode, boolean fromApi) {}

    /** 模式解析结果：候选景点+基础停留时长+模式贴士 */
    private record Selection(List<Spot> spots, int stayMinutes, String tips, String mode) {}

    /**
     * 单日规划（兼容旧调用）
     * @param city 目的地城市
     * @param mode 路线模式，如SPEED、LEISURE、FOOD、SCENIC、WILD、CITYWALK
     * @param spotIds 指定的景点id列表，为空时按城市选取
     * @param startMinutes 出发时间（当天分钟数），为空时使用默认值
     * @param startSpotId 指定的出发景点id，可为null
     * @return Map 单日行程结果
     */
    public Map<String, Object> plan(String city, String mode, List<Long> spotIds,
                                    Integer startMinutes, Long startSpotId) {
        return plan(city, mode, spotIds, startMinutes, startSpotId, 1, null);
    }

    /**
     * 行程规划入口。
     *
     * @param city 目的地城市
     * @param mode 路线模式，如SPEED、LEISURE、FOOD、SCENIC、WILD、CITYWALK
     * @param spotIds 指定的景点id列表，为空时按城市选取
     * @param startMinutes 出发时间（当天分钟数），为空时使用默认值
     * @param startSpotId 指定的出发景点id，可为null
     * @param days 行程天数；1表示单日，2~7返回按天分组的行程
     * @return Map 行程规划结果，城市或模式非法时抛出BusinessException
     */
    public Map<String, Object> plan(String city, String mode, List<Long> spotIds,
                                    Integer startMinutes, Long startSpotId, Integer days) {
        return plan(city, mode, spotIds, startMinutes, startSpotId, days, null);
    }

    /**
     * 行程规划入口（支持自定义地点）。
     *
     * @param city 目的地城市
     * @param mode 路线模式
     * @param spotIds 指定的景点id列表，为空时按城市选取
     * @param startMinutes 出发时间（当天分钟数），为空时使用默认值
     * @param startSpotId 指定的出发景点id，可为null
     * @param days 行程天数，可为null（默认1天）
     * @param customSpots 用户临时添加的非库内地点（不入库，仅本次规划有效）
     * @return Map 行程规划结果，城市或模式非法时抛出BusinessException
     */
    public Map<String, Object> plan(String city, String mode, List<Long> spotIds,
                                    Integer startMinutes, Long startSpotId, Integer days,
                                    List<Spot> customSpots) {
        if (city == null || city.isBlank()) {
            throw new BusinessException("城市不能为空");
        }
        if (mode == null || !COLORS.containsKey(mode)) {
            throw new BusinessException("不支持的路线模式：" + mode);
        }
        int wantDays = days == null ? 1 : Math.max(1, Math.min(days, DAY_COLORS.size()));

        Selection sel = selectSpots(city, mode, spotIds, customSpots);
        int startMin = normalizeStart(startMinutes);

        // 用户指定的起点必须在候选池里，否则补进来（与单日逻辑一致）
        List<Spot> pool = new ArrayList<>(sel.spots());
        Spot startSpot = null;
        if (startSpotId != null) {
            Spot found = spotRepository.findById(startSpotId)
                    .orElseThrow(() -> new BusinessException("指定的出发景点不存在"));
            startSpot = found;
            if (pool.stream().noneMatch(p -> Objects.equals(p.getId(), found.getId()))) {
                pool.add(found);
            }
        }

        // 每天至少2个景点才有"行程"的意义，据此收敛天数
        int k = Math.max(1, Math.min(wantDays, (int) Math.ceil(pool.size() / 2.0)));

        if (k <= 1) {
            return singleDay(pool, sel, startMin, startSpot, wantDays);
        }
        return multiDay(pool, sel, startMin, startSpot, k);
    }

    // ==================== 单日 ====================

    private Map<String, Object> singleDay(List<Spot> pool, Selection sel, int startMin,
                                          Spot forcedStart, int requestedDays) {
        Map<String, Object> day = buildDay(pool, sel, startMin, forcedStart);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", sel.mode());
        result.put("color", COLORS.get(sel.mode()));
        result.put("multiDay", false);
        result.put("dayCount", 1);
        result.putAll(day);
        if (requestedDays > 1) {
            // 景点数不足以拆天时，给出原因提示
            result.put("advice", "该城市当前只有" + pool.size() + " 个可选景点，不足以拆成 "
                    + requestedDays + " 天，已按单日规划；想玩多天可以多勾选景点或换个城市。 "
                    + result.get("advice"));
        }
        result.put("tips", sel.tips());
        return result;
    }

    // ==================== 多日 ====================

    @SuppressWarnings("unchecked")
    private Map<String, Object> multiDay(List<Spot> pool, Selection sel, int startMin,
                                         Spot forcedStart, int k) {
        List<List<Spot>> groups = clusterByGeography(pool, k);

        // 将用户指定起点所在组调整到第一天
        if (forcedStart != null && groups.size() > 1) {
            int hit = -1;
            for (int i = 0; i < groups.size(); i++) {
                if (groups.get(i).stream().anyMatch(s -> Objects.equals(s.getId(), forcedStart.getId()))) {
                    hit = i;
                    break;
                }
            }
            if (hit > 0) {
                List<Spot> head = groups.get(0);
                groups.set(0, groups.get(hit));
                groups.set(hit, head);
            }
        }

        double cityLng = pool.stream().filter(s -> s.getLng() != null)
                .mapToDouble(Spot::getLng).average().orElse(112.97);
        double cityLat = pool.stream().filter(s -> s.getLat() != null)
                .mapToDouble(Spot::getLat).average().orElse(28.2);

        List<Map<String, Object>> days = new ArrayList<>();
        List<Spot> allSpots = new ArrayList<>();
        double totalDist = 0;
        int totalTravel = 0;
        int totalDur = 0;
        String risk = "LOW";

        for (int i = 0; i < groups.size(); i++) {
            List<Spot> group = groups.get(i);
            Map<String, Object> d = buildDay(group, sel, startMin, i == 0 ? forcedStart : null);
            int dayIdx = i + 1;
            String anchor = anchorOf(group);

            Map<String, Object> dm = new LinkedHashMap<>();
            dm.put("day", dayIdx);
            dm.put("title", "第" + dayIdx +"天 · " + dayZone(group, cityLng, cityLat) + "（" + anchor + "）");
            dm.put("anchor", anchor);
            dm.put("color", DAY_COLORS.get(i % DAY_COLORS.size()));
            dm.put("spotCount", group.size());
            dm.put("spots", d.get("spots"));
            dm.put("polyline", d.get("polyline"));
            dm.put("segments", d.get("segments"));
            dm.put("schedule", d.get("schedule"));
            dm.put("startTime", d.get("startTime"));
            dm.put("endTime", d.get("endTime"));
            dm.put("totalDistanceKm", d.get("totalDistanceKm"));
            dm.put("totalTravelMin", d.get("totalTravelMin"));
            dm.put("totalDurationMin", d.get("totalDurationMin"));
            dm.put("riskLevel", d.get("riskLevel"));
            dm.put("advice", d.get("advice"));
            dm.put("twoOpt", d.get("twoOpt"));
            days.add(dm);

            allSpots.addAll((List<Spot>) d.get("spots"));
            totalDist += ((Number) d.get("totalDistanceKm")).doubleValue();
            totalTravel += ((Number) d.get("totalTravelMin")).intValue();
            totalDur += ((Number) d.get("totalDurationMin")).intValue();
            String one = String.valueOf(d.get("riskLevel"));
            if (riskRank(one) > riskRank(risk)) {
                risk = one;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", sel.mode());
        result.put("color", COLORS.get(sel.mode()));
        result.put("multiDay", true);
        result.put("dayCount", days.size());
        result.put("days", days);
        result.put("spots", allSpots);
        // 多日行程按天分色，顶层不画单条折线，前端改用days[].polyline
        result.put("polyline", List.of());
        result.put("segments", List.of());
        result.put("schedule", List.of());
        result.put("startTime", clockText(startMin));
        result.put("endTime", days.get(days.size() - 1).get("endTime"));
        result.put("totalDistanceKm", Math.round(totalDist * 10) / 10.0);
        result.put("totalTravelMin", totalTravel);
        result.put("totalDurationMin", totalDur);
        result.put("riskLevel", risk);
        result.put("advice", buildMultiAdvice(days, allSpots.size(), startMin));
        result.put("tips", sel.tips());
        return result;
    }

    private int riskRank(String level) {
        return switch (level == null ? "" : level) {
            case "HIGH" -> 3;
            case "MIDDLE" -> 2;
            default -> 1;
        };
    }

    private String buildMultiAdvice(List<Map<String, Object>> days, int spotCount, int startMin) {
        double dist = 0;
        int dur = 0;
        Map<String, Object> busiest = null;
        for (Map<String, Object> d : days) {
            dist += ((Number) d.get("totalDistanceKm")).doubleValue();
            int one = ((Number) d.get("totalDurationMin")).intValue();
            dur += one;
            if (busiest == null || one > ((Number) busiest.get("totalDurationMin")).intValue()) {
                busiest = d;
            }
        }
        List<String> advice = new ArrayList<>();
        advice.add("多日行程共" + days.size() + " 天、" + spotCount + " 个景点，总里程约 "
                + (Math.round(dist * 10) / 10.0) + " km，累计游览与交通约 "
                + String.format("%.1f", dur / 60.0) + " 小时。");
        if (busiest != null) {
            advice.add("第" + busiest.get("day") +"天安排最满（" + busiest.get("totalDistanceKm")
                    + " km），建议当天提前出发并预约门票。");
        }
        String nextAnchor = days.size() > 1 ? String.valueOf(days.get(1).get("anchor")) : null;
        advice.add("跨天行程建议按天订住宿：第1天结束后住在「"
                + (nextAnchor == null ? "次日首个景点" : nextAnchor) + "」附近，行李寄存酒店，减少往返奔波。");
        advice.add("每天按" + clockText(startMin) + " 出发规划，可按当天体力上下浮动1小时。");
        return String.join(" ", advice);
    }

    /**
     * 地理聚类：把景点按位置聚成k组，每组对应一天。
     * 先按"离已有种子最远×热度加成"选取种子，再做最多30轮k-means迭代，
     * 最后做规模均衡修正，保证各组点数不过于悬殊。
     *
     * @param spots 待聚类的景点
     * @param k 目标组数
     * @return List 聚类后的分组，每组对应一天的行程
     */
    private List<List<Spot>> clusterByGeography(List<Spot> spots, int k) {
        int n = spots.size();
        List<List<Spot>> groups = new ArrayList<>();
        if (k <= 1) {
            groups.add(new ArrayList<>(spots));
            return groups;
        }
        if (n <= k) {
            // 景点数少于天数时，每个景点单独成组（按热度降序）
            List<Spot> sorted = new ArrayList<>(spots);
            sorted.sort(byHeat());
            for (Spot s : sorted) {
                List<Spot> g = new ArrayList<>();
                g.add(s);
                groups.add(g);
            }
            return groups;
        }

        // 1) 选种子
        List<Integer> seeds = new ArrayList<>();
        seeds.add(indexOfHottest(spots));
        while (seeds.size() < k) {
            int best = -1;
            double bestScore = -1;
            for (int i = 0; i < n; i++) {
                if (seeds.contains(i)) continue;
                double nearest = Double.MAX_VALUE;
                for (int s : seeds) {
                    nearest = Math.min(nearest, distanceKm(spots.get(i), spots.get(s)));
                }
                int heat = spots.get(i).getHeat() == null ? 0 : Math.min(spots.get(i).getHeat(), 100);
                double score = nearest * (1 + heat / 200.0);
                if (score > bestScore) {
                    bestScore = score;
                    best = i;
                }
            }
            if (best < 0) break;
            seeds.add(best);
        }

        int m = seeds.size();
        double[] clng = new double[m];
        double[] clat = new double[m];
        for (int c = 0; c < m; c++) {
            clng[c] = spots.get(seeds.get(c)).getLng();
            clat[c] = spots.get(seeds.get(c)).getLat();
        }

        // 2) k-means迭代
        int[] assign = new int[n];
        Arrays.fill(assign, -1);
        for (int iter = 0; iter < 30; iter++) {
            boolean changed = false;
            for (int i = 0; i < n; i++) {
                int nearestCluster = 0;
                double nearestDist = Double.MAX_VALUE;
                for (int c = 0; c < m; c++) {
                    double dist = distanceKm(spots.get(i).getLat(), spots.get(i).getLng(), clat[c], clng[c]);
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearestCluster = c;
                    }
                }
                if (assign[i] != nearestCluster) {
                    assign[i] = nearestCluster;
                    changed = true;
                }
            }
            double[] sumLng = new double[m];
            double[] sumLat = new double[m];
            int[] cnt = new int[m];
            for (int i = 0; i < n; i++) {
                sumLng[assign[i]] += spots.get(i).getLng();
                sumLat[assign[i]] += spots.get(i).getLat();
                cnt[assign[i]]++;
            }
            for (int c = 0; c < m; c++) {
                if (cnt[c] > 0) {
                    clng[c] = sumLng[c] / cnt[c];
                    clat[c] = sumLat[c] / cnt[c];
                }
            }
            if (!changed) break;
        }

        for (int c = 0; c < m; c++) {
            groups.add(new ArrayList<>());
        }
        for (int i = 0; i < n; i++) {
            groups.get(assign[i]).add(spots.get(i));
        }
        groups.removeIf(List::isEmpty);
        balanceGroups(groups);
        return groups;
    }

    /**
     * 规模均衡：将各组点数控制在 [floor, cap] 区间。
     * 每次从最大组中挑选迁移代价最小的点移入最小组，尽量保持地理集聚性。
     *
     * @param groups 待均衡的分组
     */
    private void balanceGroups(List<List<Spot>> groups) {
        int k = groups.size();
        if (k <= 1) return;
        int n = groups.stream().mapToInt(List::size).sum();
        int cap = (n + k - 1) / k;  // 每组上限
        int floor = n / k;          // 每组下限
        for (int guard = 0; guard < n * 4; guard++) {
            List<Spot> biggest = groups.stream().max(Comparator.comparingInt(List::size)).orElse(null);
            List<Spot> smallest = groups.stream().min(Comparator.comparingInt(List::size)).orElse(null);
            if (biggest == null || smallest == null || biggest == smallest) break;
            // 已均衡时退出
            if (biggest.size() <= cap && smallest.size() >= floor) break;
            // 最大的组不能再往外让了
            if (biggest.size() - 1 < Math.max(floor, 1)) break;

            double targetLng = avgLng(smallest);
            double targetLat = avgLat(smallest);
            double ownLng = avgLng(biggest);
            double ownLat = avgLat(biggest);
            Spot move = null;
            double bestCost = Double.MAX_VALUE;
            for (Spot s : biggest) {
                double cost = distanceKm(s.getLat(), s.getLng(), targetLat, targetLng)
                        - distanceKm(s.getLat(), s.getLng(), ownLat, ownLng);
                if (cost < bestCost) {
                    bestCost = cost;
                    move = s;
                }
            }
            if (move == null) break;
            biggest.remove(move);
            smallest.add(move);
        }
        groups.removeIf(List::isEmpty);
    }

    private double avgLng(List<Spot> group) {
        return group.stream().mapToDouble(Spot::getLng).average().orElse(0);
    }

    private double avgLat(List<Spot> group) {
        return group.stream().mapToDouble(Spot::getLat).average().orElse(0);
    }

    private String anchorOf(List<Spot> group) {
        return group.stream().min(byHeat()).map(Spot::getName).orElse("市区");
    }

    /**
     * 该组相对全城重心的方位，用来给每天起个像样的名字
     * @param group 某一天的景点分组
     * @param cityLng 全城重心的经度
     * @param cityLat 全城重心的纬度
     * @return String 方位名，如「城东北」，贴近重心时返回「市中心」
     */
    private String dayZone(List<Spot> group, double cityLng, double cityLat) {
        String zone = zoneOf(avgLng(group), avgLat(group), cityLng, cityLat);
        if ("市中心".equals(zone)) {
            // 簇跨度大时质心会落回中间，改用热度最高的锚点判断方位
            Spot anchor = group.stream().min(byHeat()).orElse(group.get(0));
            String alt = zoneOf(anchor.getLng(), anchor.getLat(), cityLng, cityLat);
            if (!"市中心".equals(alt)) return alt;
        }
        return zone;
    }

    private String zoneOf(double lng, double lat, double cityLng, double cityLat) {
        double dxKm = (lng - cityLng) * 111 * Math.cos(Math.toRadians(cityLat));
        double dyKm = (lat - cityLat) * 111;
        String ew = Math.abs(dxKm) < 1.5 ? "" : (dxKm > 0 ? "东" : "西");
        String ns = Math.abs(dyKm) < 1.5 ? "" : (dyKm > 0 ? "北" : "南");
        if (ew.isEmpty() && ns.isEmpty()) return "市中心";
        return "城" + ns + ew;
    }

    // ==================== 单日构建 ====================

    /**
     * 把一组景点排成一天的行程（最近邻排序+真实路网+时间轴）
     * @param input 当天的候选景点
     * @param sel 模式解析结果，提供停留时长与贴士
     * @param startMin 出发时间（当天分钟数）
     * @param forcedStart 用户指定的起点，可为null
     * @return Map 一天的行程，含景点顺序、折线、分段路况与时间轴
     */
    private Map<String, Object> buildDay(List<Spot> input, Selection sel, int startMin, Spot forcedStart) {
        String mode = sel.mode();
        int stayMinutes = sel.stayMinutes();
        if (input == null || input.isEmpty()) {
            throw new BusinessException("该城市暂无可规划景点");
        }
        List<Spot> picked = new ArrayList<>(input);

        // 起点优先取用户指定且在本组内的景点，否则取本组热度最高者
        Spot first;
        if (forcedStart != null && picked.stream().anyMatch(p -> Objects.equals(p.getId(), forcedStart.getId()))) {
            first = forcedStart;
        } else {
            // byHeat为热度降序，取min即热度最高的景点
            first = picked.stream().min(byHeat()).orElse(picked.get(0));
        }
        List<Spot> ordered = nearestNeighbor(picked, first);

        // 2-opt消除路线交叉（起点固定），优化收益随结果返回
        Map<String, Object> opt = new LinkedHashMap<>();
        ordered = twoOpt(ordered, first, opt);

        // ===== 逐段真实道路+时间轴 =====
        double totalDistance = 0;
        int totalTravelMin = 0;
        List<List<Double>> polyline = new ArrayList<>();
        List<Map<String, Object>> segments = new ArrayList<>();
        List<Map<String, Object>> schedule = new ArrayList<>();

        int clock = startMin;
        boolean lunchTaken = false;
        polyline.add(List.of(round(first.getLng(), 6), round(first.getLat(), 6)));

        for (int i = 0; i < ordered.size(); i++) {
            Spot s = ordered.get(i);

            // 跨过12:00且未休息时，在上一个点位后安排50分钟午餐休息
            if (i > 0 && !lunchTaken && clock >= 12 * 60) {
                lunchTaken = true;
                clock += 50;
                schedule.get(schedule.size() - 1).put("restAfter", "🍜 午餐休息50分钟");
            }

            int stay = stayOf(s, stayMinutes, mode);
            int arriveMin = clock;
            clock = arriveMin + stay;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("seq", i + 1);
            item.put("spotId", s.getId());
            item.put("name", s.getName());
            item.put("arrival", clockText(arriveMin));
            item.put("depart", clockText(clock));
            item.put("stayMin", stay);
            schedule.add(item);

            // 下一段路程
            if (i < ordered.size() - 1) {
                Spot next = ordered.get(i + 1);
                Leg leg = routeBetween(s, next);
                totalDistance += leg.distanceKm();
                totalTravelMin += leg.durationMin();
                clock += leg.durationMin();

                // polyline合并（去掉重复衔接点）
                List<List<Double>> pts = leg.polyline();
                for (int k = 1; k < pts.size(); k++) {
                    polyline.add(pts.get(k));
                }

                Map<String, Object> seg = new LinkedHashMap<>();
                seg.put("from", s.getName());
                seg.put("to", next.getName());
                seg.put("distanceKm", Math.round(leg.distanceKm() * 10) / 10.0);
                seg.put("travelMin", leg.durationMin());
                seg.put("travelMode", leg.travelMode());
                seg.put("realRoad", leg.fromApi());
                segments.add(seg);
            }
        }

        int totalDuration = totalTravelMin + schedule.stream()
                .mapToInt(m -> (int) m.get("stayMin")).sum();
        boolean hasSecret = ordered.stream().anyMatch(s -> "SECRET".equals(s.getCategory()));
        String riskLevel = "WILD".equals(mode) ? "HIGH" : (hasSecret ? "MIDDLE" : "LOW");

        Map<String, Object> day = new LinkedHashMap<>();
        day.put("spots", ordered);
        day.put("polyline", polyline);
        day.put("segments", segments);
        day.put("schedule", schedule);
        day.put("startTime", clockText(startMin));
        day.put("endTime", clockText(clock));
        day.put("totalDistanceKm", Math.round(totalDistance * 10) / 10.0);
        day.put("totalTravelMin", totalTravelMin);
        day.put("totalDurationMin", totalDuration);
        day.put("riskLevel", riskLevel);
        day.put("advice", buildAdvice(startMin, clock, totalDuration, ordered.size(), mode));
        day.put("twoOpt", opt);
        return day;
    }

    // ==================== 模式语义 ====================

    /**
     * 按模式筛选候选景点，并给出基础停留时长与模式贴士
     * @param city 目的地城市
     * @param mode 路线模式
     * @param spotIds 用户勾选的景点id，为空时取该城市全部景点
     * @param customSpots 用户临时添加的非库内地点，可为null
     * @return Selection 候选景点、基础停留时长与模式贴士
     */
    private Selection selectSpots(String city, String mode, List<Long> spotIds, List<Spot> customSpots) {
        boolean userPicked = spotIds != null && !spotIds.isEmpty();
        boolean hasCustom = customSpots != null && !customSpots.isEmpty();
        List<Spot> spots;
        if (userPicked) {
            spots = new ArrayList<>(spotRepository.findAllById(spotIds));
            if (spots.isEmpty()) {
                throw new BusinessException("未找到所选景点");
            }
        } else {
            spots = new ArrayList<>(spotRepository.findByCity(city));
        }
        // 只加了自定义地点、景点库为空时也要能规划
        if (spots.isEmpty() && !hasCustom) {
            throw new BusinessException("该城市暂无可规划景点");
        }

        List<Spot> picked = new ArrayList<>(spots);
        int stayMinutes;
        String tips;
        switch (mode) {
            case "SPEED" -> {
                stayMinutes = 40;
                tips = "特种兵速通模式：全程快节奏，建议最早班次出发，提前预约门票，利用地铁/打车衔接，每点停留约40分钟。";
            }
            case "LEISURE" -> {
                stayMinutes = 110;
                if (!userPicked) {
                    picked.sort(byHeat());
                    picked = new ArrayList<>(picked.subList(0, Math.min(6, picked.size())));
                }
                tips = "休闲慢游模式：每点停留约2小时，适合拍照、发呆和慢慢体验，注意午休与补水，行程偏长时会自动建议住宿。";
            }
            case "FOOD" -> {
                stayMinutes = 55;
                if (userPicked) {
                    // 美食类景点优先排在前面顺路吃，同组内按热度从高到低
                    picked.sort(Comparator.comparing((Spot s) -> !"FOOD".equals(s.getCategory()))
                            .thenComparing(byHeat()));
                } else {
                    picked.removeIf(s -> !"FOOD".equals(s.getCategory()) && !"TRENDY".equals(s.getCategory()));
                    picked.sort(byHeat());
                    picked = new ArrayList<>(picked.subList(0, Math.min(8, picked.size())));
                }
                tips = "美食专线：串联本地必吃点位，建议空腹出发、多人拼食，每样少买多尝，留意营业时间，避开节假日排队高峰。";
            }
            case "SCENIC" -> {
                stayMinutes = 65;
                if (!userPicked) {
                    picked.removeIf(s -> !List.of("NATURE", "HISTORY", "TRENDY", "THEME").contains(s.getCategory()));
                    picked.sort(byHeat());
                }
                tips = "景点专线：优先自然、历史人文、网红与主题乐园核心景点，建议提前线上购票，穿舒适鞋，带好防晒。";
            }
            case "WILD" -> {
                stayMinutes = 75;
                if (userPicked) {
                    picked.sort(Comparator.comparing((Spot s) -> !"SECRET".equals(s.getCategory()))
                            .thenComparing(byHeat()));
                } else {
                    picked.removeIf(s -> !"SECRET".equals(s.getCategory()) && !"NATURE".equals(s.getCategory()));
                    picked.sort(byHeat());
                }
                tips = "刺激野路模式，风险等级：高。野路未开发，存在迷路、滑坠、无手机信号等风险。务必结伴同行，出行前向辅导员/紧急联系人报备；携带登山鞋、头灯、急救包与充足饮水；恶劣天气立即取消。";
            }
            case "CITYWALK" -> {
                stayMinutes = 45;
                if (!userPicked) {
                    // 漫步偏好街区与人文，控制点数在5~8个保证步行可达
                    picked.sort(byHeat());
                    picked = new ArrayList<>(picked.subList(0, Math.min(8, picked.size())));
                }
                tips = "城市漫步 Citywalk：以步行节奏串起街区与人文地标，距离近的景点间直接步行，适合边走边拍，穿双舒服的鞋。";
            }
            default -> {
                stayMinutes = 60;
                tips = "最优路线：按热度从最高景点出发，以最近邻方式串联，兼顾体验与效率。建议提前查好开放时间与交通接驳。";
            }
        }

        // 不足3个时按热度自动补齐
        if (picked.size() < 3) {
            List<Spot> pool = new ArrayList<>(spotRepository.findByCity(city));
            pool.sort(byHeat());
            for (Spot s : pool) {
                if (picked.size() >= 3) break;
                if (picked.stream().noneMatch(p -> p.getId().equals(s.getId()))) {
                    picked.add(s);
                }
            }
        }
        // 自定义地点在过滤与截断后追加，并按名称去重
        if (hasCustom) {
            for (Spot c : customSpots) {
                boolean dup = picked.stream().anyMatch(p -> Objects.equals(p.getName(), c.getName()));
                if (!dup) {
                    picked.add(c);
                }
            }
        }
        if (picked.isEmpty()) {
            throw new BusinessException("该城市暂无可规划景点");
        }
        return new Selection(picked, stayMinutes, tips, mode);
    }

    /** 热度降序（热度为空的排最后） */
    private Comparator<Spot> byHeat() {
        return Comparator.comparing(Spot::getHeat, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private int indexOfHottest(List<Spot> spots) {
        int best = 0;
        int bestHeat = Integer.MIN_VALUE;
        for (int i = 0; i < spots.size(); i++) {
            int heat = spots.get(i).getHeat() == null ? 0 : spots.get(i).getHeat();
            if (heat > bestHeat) {
                bestHeat = heat;
                best = i;
            }
        }
        return best;
    }

    private int normalizeStart(Integer startMinutes) {
        return (startMinutes == null || startMinutes < 0 || startMinutes > 24 * 60)
                ? 8 * 60 + 30
                : startMinutes;
    }

    /**
     * 各点停留时长：模式基础值+类别修正
     * @param s 景点
     * @param base 该模式的基础停留分钟数
     * @param mode 路线模式
     * @return int 该景点的停留分钟数
     */
    private int stayOf(Spot s, int base, String mode) {
        int stay = base;
        switch (s.getCategory() == null ? "" : s.getCategory()) {
            case "THEME" -> stay += 60;   // 主题乐园普遍要玩更久
            case "NATURE" -> stay += 20;  // 自然景区爬山费时
            case "SECRET" -> stay += 25;  // 野路往返耗时
            case "FOOD" -> stay = Math.min(stay, 45);
            default -> { }
        }
        if ("SPEED".equals(mode)) stay = Math.min(stay, 45);
        return stay;
    }

    private String buildAdvice(int startMin, int endMin, int totalDuration, int spotCount, String mode) {
        List<String> advice = new ArrayList<>();
        if (totalDuration > 600) {
            advice.add("全程超过10小时，建议拆成两天：第一天玩前半程并预订景点附近住宿，第二天继续后半程。");
        } else if (endMin > 21 * 60) {
            advice.add("预计" + clockText(endMin) + " 结束，时间偏晚，建议提前预订住宿或适当减少景点。");
        } else if (endMin > 19 * 60) {
            advice.add("预计" + clockText(endMin) + " 结束，夜间返程注意安全，建议结伴同行。");
        } else {
            advice.add("行程节奏适中，" + clockText(startMin) + " 出发、" + clockText(endMin) + " 前结束，当天返程无压力。");
        }
        if (spotCount >= 6) {
            advice.add("途经" + spotCount + " 个景点，中途可在任意点位休息调整，量力而行。");
        }
        if ("FOOD".equals(mode)) {
            advice.add("美食线建议午餐前出发，胃容量留给重点店铺。");
        }
        return String.join(" ", advice);
    }

    /**
     * 最近邻TSP，起点指定
     * @param spots 待排序的景点
     * @param start 固定起点
     * @return List 从起点出发、逐点取最近的访问顺序
     */
    private List<Spot> nearestNeighbor(List<Spot> spots, Spot start) {
        List<Spot> remaining = new ArrayList<>(spots);
        remaining.removeIf(s -> s.getId().equals(start.getId()));
        List<Spot> ordered = new ArrayList<>();
        ordered.add(start);
        Spot current = start;
        while (!remaining.isEmpty()) {
            final Spot from = current;
            Spot next = remaining.stream()
                    .min(Comparator.comparingDouble(s -> distanceKm(from, s)))
                    .orElseThrow();
            remaining.remove(next);
            ordered.add(next);
            current = next;
        }
        return ordered;
    }

    // ==================== 2-opt路径优化 ====================

    /**
     * 2-opt局部优化：反复反转造成路径交叉的区间，直到无法改进，起点固定。
     * 距离用直线近似评估，真实里程由后续逐段路网计算。
     *
     * @param spots 待优化顺序（含固定起点）
     * @param start 固定起点
     * @param optimization 输出优化前后里程 {beforeKm, afterKm, savedPercent}
     * @return 优化后的景点顺序
     */
    private List<Spot> twoOpt(List<Spot> spots, Spot start, Map<String, Object> optimization) {
        int n = spots.size();
        if (n < 4) {
            return spots;
        }
        List<Spot> ordered = new ArrayList<>(spots);
        double before = straightLineTotal(ordered);
        boolean improved = true;
        int guard = 0;
        while (improved && guard++ < 60) {
            improved = false;
            for (int i = 1; i < n - 1; i++) {
                for (int k = i + 1; k < n; k++) {
                    Spot edgeA = i == 1 ? start : ordered.get(i - 1);
                    Spot edgeB = ordered.get(i);
                    Spot edgeC = ordered.get(k);
                    Spot edgeD = k + 1 < n ? ordered.get(k + 1) : null;
                    // delta < 0表示重连后更短：A→C + B→D代替A→B + C→D
                    double delta = dist(edgeA, edgeC) + dist(edgeB, edgeD)
                            - dist(edgeA, edgeB) - dist(edgeC, edgeD);
                    if (delta < -1e-9) {
                        // 反转 [i..k]
                        for (int l = i, r = k; l < r; l++, r--) {
                            java.util.Collections.swap(ordered, l, r);
                        }
                        improved = true;
                    }
                }
            }
        }
        double after = straightLineTotal(ordered);
        if (optimization != null) {
            optimization.put("beforeKm", Math.round(before * 10) / 10.0);
            optimization.put("afterKm", Math.round(after * 10) / 10.0);
            optimization.put("savedPercent", before > 0
                    ? Math.round((before - after) / before * 1000) / 10.0 : 0);
        }
        return ordered;
    }

    /**
     * 顺序的直线总里程（2-opt的评估函数）
     * @param ordered 已排好序的景点
     * @return double 相邻两点直线距离之和，单位km
     */
    private double straightLineTotal(List<Spot> ordered) {
        double total = 0;
        for (int i = 0; i + 1 < ordered.size(); i++) {
            total += distanceKm(ordered.get(i), ordered.get(i + 1));
        }
        return total;
    }

    /**
     * 空指针安全的直线距离（2-opt末端虚拟边界点为null，距离记0）
     * @param a 起点，可为null
     * @param b 终点，可为null
     * @return double 两点直线距离，任一为null时返回0
     */
    private double dist(Spot a, Spot b) {
        return a == null || b == null ? 0 : distanceKm(a, b);
    }

    // ==================== 真实道路 ====================

    /**
     * 两点间路线：短途步行、长途乘车，全部走真实道路；接口失败则降级仿路网折线
     * @param a 起点景点
     * @param b 终点景点
     * @return Leg 两点间的折线、里程与耗时，含是否为真实道路的标记
     */
    private Leg routeBetween(Spot a, Spot b) {
        double straight = distanceKm(a, b);
        boolean ride = straight > WALK_LEG_LIMIT_KM;
        String cacheKey = (ride ? "D" : "W") + ":" + key(a) + "->" + key(b);

        CachedLeg cached = legCache.get(cacheKey);
        if (cached != null) {
            return new Leg(cached.polyline(), cached.distanceKm(), cached.durationMin(),
                    ride ? "RIDE" : "WALK", cached.fromApi());
        }

        Leg leg = fetchTencentRoute(a, b, ride);
        if (leg == null) {
            leg = fallbackRoute(a, b, ride);
        } else {
            legCache.put(cacheKey, new CachedLeg(leg.polyline(), leg.distanceKm(), leg.durationMin(), true));
        }
        return leg;
    }

    /**
     * 调腾讯WebServiceAPI步行/驾车路线规划
     * @param a 起点景点
     * @param b 终点景点
     * @param ride true表示驾车，false表示步行
     * @return Leg 腾讯返回的路线，接口异常或无路线时返回null
     */
    @SuppressWarnings("unchecked")
    private Leg fetchTencentRoute(Spot a, Spot b, boolean ride) {
        String api = ride ? "driving" : "walking";
        String url = "https://apis.map.qq.com/ws/direction/v1/" + api + "/"
                + "?from=" + a.getLat() + "," + a.getLng()
                + "&to=" + b.getLat() + "," + b.getLng()
                + "&key=" + URLEncoder.encode(TENCENT_KEY, StandardCharsets.UTF_8);
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(6))
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> json = Json.parse(resp.body());
            if (!Integer.valueOf(0).equals(toInt(json.get("status")))) {
                log.warn("腾讯路线接口返回异常：{} {}", json.get("status"), json.get("message"));
                return null;
            }
            Map<String, Object> result = (Map<String, Object>) json.get("result");
            List<Map<String, Object>> routes = (List<Map<String, Object>>) (result == null ? null : result.get("routes"));
            if (routes == null || routes.isEmpty()) {
                return null;
            }
            Map<String, Object> route = routes.get(0);
            List<Number> compressed = ((List<Number>) route.get("polyline"));
            List<List<Double>> points = decodePolyline(compressed);
            double distanceKm = toDouble(route.get("distance")) / 1000.0;
            int durationMin = (int) Math.max(1, Math.round(toDouble(route.get("duration"))));
            return new Leg(points, distanceKm, durationMin, ride ? "RIDE" : "WALK", true);
        } catch (Exception e) {
            log.warn("腾讯路线接口调用失败（{}）：{}", api, e.getMessage());
            return null;
        }
    }

    /**
     * 降级路线：仿路网"横-竖-横"三段式折线+微抖动，杜绝直线对角线
     * @param a 起点景点
     * @param b 终点景点
     * @param ride true表示乘车，false表示步行
     * @return Leg 仿路网折线路线，标为非真实道路
     */
    private Leg fallbackRoute(Spot a, Spot b, boolean ride) {
        double straight = distanceKm(a, b);
        double roadFactor = 1.35;
        double distanceKm = straight * roadFactor;

        double lat1 = a.getLat(), lng1 = a.getLng(), lat2 = b.getLat(), lng2 = b.getLng();
        double jitter = Math.max(0.0008, straight * 0.004);
        double midLng = lng1 + (lng2 - lng1) * 0.35 + jitter * 0.6;
        double midLat = lat1 + (lat2 - lat1) * 0.62 - jitter * 0.4;

        List<List<Double>> points = List.of(
                List.of(round(lng1, 6), round(lat1, 6)),
                List.of(round(midLng, 6), round(lat1, 6)),
                List.of(round(midLng, 6), round(midLat, 6)),
                List.of(round(lng2, 6), round(midLat, 6)),
                List.of(round(lng2, 6), round(lat2, 6))
        );
        double speed = ride ? RIDE_SPEED_KMH : WALK_SPEED_KMH;
        int durationMin = (int) Math.max(3, Math.round(distanceKm / speed * 60));
        return new Leg(points, distanceKm, durationMin, ride ? "RIDE" : "WALK", false);
    }

    /**
     * 腾讯polyline解压：从第3个数起每项=前2项+本项/1e6（经纬交替）
     * @param compressed 腾讯返回的压缩坐标数组
     * @return List 解压后的 [经度, 纬度] 坐标点，数据不足时返回空列表
     */
    private List<List<Double>> decodePolyline(List<Number> compressed) {
        List<List<Double>> points = new ArrayList<>();
        if (compressed == null || compressed.size() < 4) {
            return points;
        }
        double[] co = new double[compressed.size()];
        co[0] = compressed.get(0).doubleValue();
        co[1] = compressed.get(1).doubleValue();
        for (int i = 2; i < compressed.size(); i++) {
            co[i] = co[i - 2] + compressed.get(i).doubleValue() / 1_000_000.0;
        }
        for (int i = 0; i < co.length / 2; i++) {
            points.add(List.of(round(co[i * 2 + 1], 6), round(co[i * 2], 6))); // [lng, lat]
        }
        return points;
    }

    private String key(Spot s) {
        return s.getLat() + "," + s.getLng();
    }

    /**
     * 两点球面距离（haversine），单位km
     * @param a 起点景点
     * @param b 终点景点
     * @return double 两点的球面直线距离，单位km
     */
    private double distanceKm(Spot a, Spot b) {
        return distanceKm(a.getLat(), a.getLng(), b.getLat(), b.getLng());
    }

    private double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371.0;
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * R * Math.asin(Math.sqrt(h));
    }

    private String clockText(int minutesOfDay) {
        int m = ((minutesOfDay % 1440) + 1440) % 1440;
        return String.format("%02d:%02d", m / 60, m % 60);
    }

    private double round(double v, int digits) {
        double f = Math.pow(10, digits);
        return Math.round(v * f) / f;
    }

    private Integer toInt(Object o) {
        return o instanceof Number n ? n.intValue() : null;
    }

    private double toDouble(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }

    /** 极简JSON解析（Spring自带Jackson，无需额外依赖） */
    private static class Json {
        private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
                new com.fasterxml.jackson.databind.ObjectMapper();

        @SuppressWarnings("unchecked")
        static Map<String, Object> parse(String text) throws java.io.IOException {
            return MAPPER.readValue(text, Map.class);
        }
    }
}
