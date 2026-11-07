package com.xuelvquan.travel.service;

import com.xuelvquan.travel.entity.Footprint;
import com.xuelvquan.travel.entity.GuideService;
import com.xuelvquan.travel.entity.Post;
import com.xuelvquan.travel.entity.Product;
import com.xuelvquan.travel.entity.Route;
import com.xuelvquan.travel.entity.Spot;
import com.xuelvquan.travel.entity.Trip;
import com.xuelvquan.travel.repository.FootprintRepository;
import com.xuelvquan.travel.repository.GuideServiceRepository;
import com.xuelvquan.travel.repository.PostRepository;
import com.xuelvquan.travel.repository.ProductRepository;
import com.xuelvquan.travel.repository.RouteRepository;
import com.xuelvquan.travel.repository.SpotRepository;
import com.xuelvquan.travel.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 需求洞察服务：面向商家/导游输出经营参考。
 *
 * 供需缺口：城市维度的需求热度（攻略×3＋结伴×5＋线路×2＋足迹×4，口径与HotService一致）
 * 对比平台供给量（导游视角为该城市在售向导服务数，商家视角为该主题在售商品数），
 * 缺口分=需求热度 ÷（供给数 ＋ 1）。
 * 出行方式/预算结构：结伴队伍的交通方式与人均预算分布，供定价与选品参考。
 *
 * 只输出聚合统计量，不含任何可识别到个人的字段；队伍数少于 #MIN_TRIPS
 * 时前端提示"样本较少，仅供参考"。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DemandInsightService {

    /** 出行方式/预算占比的最小样本量（低于此值前端会标注"样本较少"） */
    private static final int MIN_TRIPS = 3;

    /** 需求热度权重（与HotService保持一致，便于交叉验证） */
    private static final int W_POST = 3;
    private static final int W_TRIP = 5;
    private static final int W_ROUTE = 2;
    private static final int W_FOOTPRINT = 4;

    /** 交通方式代码 → 中文 */
    private static final Map<String, String> TRANSPORT_LABEL = Map.of(
            "TRAIN", "高铁 / 火车",
            "BUS", "大巴 / 汽车",
            "PLANE", "飞机",
            "CAR", "自驾 / 拼车",
            "BIKE", "骑行",
            "WALK", "步行",
            "SHIP", "轮船"
    );

    /** 兴趣标签 → 建议经营的商品品类（给商家选品参考） */
    private static final Map<String, String> THEME_CATEGORY = new LinkedHashMap<>();

    static {
        THEME_CATEGORY.put("徒步", "户外装备");
        THEME_CATEGORY.put("登山", "户外装备");
        THEME_CATEGORY.put("露营", "户外装备");
        THEME_CATEGORY.put("摄影", "摄影数码");
        THEME_CATEGORY.put("夜景", "摄影数码");
        THEME_CATEGORY.put("美食", "零食特产");
        THEME_CATEGORY.put("探店", "零食特产");
        THEME_CATEGORY.put("Citywalk", "鞋服日用");
        THEME_CATEGORY.put("历史", "文创周边");
        THEME_CATEGORY.put("古建", "文创周边");
        THEME_CATEGORY.put("博物馆", "文创周边");
        THEME_CATEGORY.put("古镇", "文创周边");
    }

    private final PostRepository postRepository;
    private final TripRepository tripRepository;
    private final RouteRepository routeRepository;
    private final SpotRepository spotRepository;
    private final FootprintRepository footprintRepository;
    private final GuideServiceRepository guideServiceRepository;
    private final ProductRepository productRepository;

    /**
     * 需求洞察总览。
     * @param audience "GUIDE" 或 "MERCHANT"，用于决定「供给」算什么
     * @return Map 含缺口榜、出行方式结构、预算结构、主题需求与样本量信息
     */
    public Map<String, Object> insight(String audience) {
        boolean forGuide = !"MERCHANT".equals(audience);

        // 需求侧：按城市维度聚合
        Map<String, long[]> demand = aggregateDemand();       // city -> [posts, trips, routes, footprints]
        Map<String, List<String>> cityThemes = cityThemes();  // city -> 该城市结伴队伍的兴趣标签
        Map<String, Integer> supply = guideSupply();

        // 供需缺口
        List<Map<String, Object>> gaps = buildGaps(demand, cityThemes, supply, forGuide);

        // 出行方式与预算结构（全局，不按城市拆分）
        List<Map<String, Object>> transportMix = transportMix();
        Map<String, Object> budget = budgetProfile();
        // 主题需求叠加在售商品数，得到品类维度供需缺口
        List<Map<String, Object>> themeDemand = themeDemand(forGuide ? null : productSupply());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("audience", forGuide ? "GUIDE" : "MERCHANT");
        out.put("supplyName", "向导服务");
        out.put("supplyUnit", "个");
        out.put("headline", forGuide
                ? headline(gaps, transportMix)
                : merchantHeadline(themeDemand));
        out.put("gaps", gaps);
        out.put("transportMix", transportMix);
        out.put("budget", budget);
        out.put("themeDemand", themeDemand);
        out.put("sample", sampleInfo(forGuide));
        return out;
    }

    // ==================== ①需求侧聚合 ====================

    /** city -> [posts, trips, routes, footprints]，城市集合取自景点库+结伴目的地 */
    private Map<String, long[]> aggregateDemand() {
        Set<String> cities = new LinkedHashSet<>();
        for (Spot s : spotRepository.findAll()) {
            if (notBlank(s.getCity())) {
                cities.add(s.getCity());
            }
        }
        // 结伴目的地里出现、但景点库没有的城市也纳入（如「武功山」）
        for (Trip t : tripRepository.findAll()) {
            String d = s(t.getDestination());
            if (!d.isEmpty() && cities.stream().noneMatch(d::contains)) {
                cities.add(d);
            }
        }

        Map<String, long[]> agg = new LinkedHashMap<>();
        for (String c : cities) {
            agg.put(c, new long[4]);
        }
        for (Post p : postRepository.findAll()) {
            if (!"APPROVED".equals(p.getStatus())) {
                continue;
            }
            bump(agg, s(p.getCity()) + " " + s(p.getDestination()), 0);
        }
        for (Trip t : tripRepository.findAll()) {
            bump(agg, s(t.getDestination()), 1);
        }
        for (Route r : routeRepository.findAll()) {
            bump(agg, s(r.getCity()), 2);
        }
        for (Footprint f : footprintRepository.findAll()) {
            bump(agg, s(f.getCity()), 3);
        }
        return agg;
    }

    /**
     * 文本里包含哪个城市就给该城市+1（idx指定累加到哪一列）
     * @param agg 城市到四项计数的累加表
     * @param text 待匹配的文本（帖子城市+目的地等）
     * @param idx 累加的列序号：0攻略、1结伴、2线路、3足迹
     */
    private void bump(Map<String, long[]> agg, String text, int idx) {
        if (text == null || text.isEmpty()) {
            return;
        }
        for (Map.Entry<String, long[]> e : agg.entrySet()) {
            if (text.contains(e.getKey())) {
                e.getValue()[idx]++;
                return; // 一条内容只归入一个城市，避免"长沙岳麓山"同时计入长沙与岳麓山
            }
        }
    }

    /** 城市 → 该城市结伴队伍提到过的兴趣标签（去重，按出现次数降序） */
    private Map<String, List<String>> cityThemes() {
        Map<String, Map<String, Integer>> raw = new LinkedHashMap<>();
        Set<String> cities = new LinkedHashSet<>();
        for (Spot s : spotRepository.findAll()) {
            if (notBlank(s.getCity())) {
                cities.add(s.getCity());
            }
        }
        for (Trip t : tripRepository.findAll()) {
            String dest = s(t.getDestination());
            String hit = cities.stream().filter(dest::contains).findFirst().orElse(dest);
            if (hit.isEmpty()) {
                continue;
            }
            Map<String, Integer> bag = raw.computeIfAbsent(hit, k -> new LinkedHashMap<>());
            for (String tag : splitTags(t.getInterestTags())) {
                bag.merge(tag, 1, Integer::sum);
            }
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        raw.forEach((city, bag) -> {
            List<String> tags = bag.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .map(Map.Entry::getKey)
                    .limit(3)
                    .toList();
            if (!tags.isEmpty()) {
                out.put(city, tags);
            }
        });
        return out;
    }

    /** 导游视角：各城市在售向导服务数（key = destination命中的城市） */
    private Map<String, Integer> guideSupply() {
        Map<String, Integer> out = new LinkedHashMap<>();
        Set<String> cities = new LinkedHashSet<>();
        for (Spot s : spotRepository.findAll()) {
            if (notBlank(s.getCity())) {
                cities.add(s.getCity());
            }
        }
        for (GuideService g : guideServiceRepository.findApproved(null)) {
            String dest = s(g.getDestination());
            String hit = cities.stream().filter(dest::contains).findFirst().orElse(dest);
            out.merge(hit, 1, Integer::sum);
        }
        return out;
    }

    /** 商家视角：各品类的在售商品数（商品无城市维度，故按品类对齐需求主题） */
    private Map<String, Integer> productSupply() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Product p : productRepository.findAll()) {
            String cat = s(p.getCategory()).isEmpty() ? "未分类" : s(p.getCategory());
            out.merge(cat, 1, Integer::sum);
        }
        return out;
    }

    // ==================== ③缺口榜 ====================

    private List<Map<String, Object>> buildGaps(Map<String, long[]> demand,
                                                Map<String, List<String>> cityThemes,
                                                Map<String, Integer> supply,
                                                boolean forGuide) {
        List<Map<String, Object>> list = new ArrayList<>();
        demand.forEach((city, a) -> {
            long heat = a[0] * W_POST + a[1] * W_TRIP + a[2] * W_ROUTE + a[3] * W_FOOTPRINT;
            if (heat <= 0) {
                return;
            }
            int supplyCount = supply.getOrDefault(city, 0);
            long gapScore = Math.round(heat / (double) (supplyCount + 1));

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("city", city);
            m.put("demandHeat", heat);
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("posts", a[0]);
            d.put("trips", a[1]);
            d.put("routes", a[2]);
            d.put("footprints", a[3]);
            m.put("demand", d);
            m.put("supplyCount", supplyCount);
            m.put("gapScore", gapScore);
            m.put("level", levelOf(supplyCount, heat, gapScore));
            m.put("themes", cityThemes.getOrDefault(city, List.of()));
            m.put("suggestion", suggestion(city, a, supplyCount, cityThemes.get(city), forGuide));
            list.add(m);
        });
        list.sort(Comparator.comparingLong((Map<String, Object> m) -> -((Number) m.get("gapScore")).longValue()));
        return list;
    }

    /**
     * 缺口等级划分：供给完全空白最紧急，其次按单份供给承担的需求量判断
     *
     * @param supplyCount 该城市（或主题）的在售供给数量
     * @param heat 需求热度值
     * @param gapScore 缺口分（需求热度 ÷（供给数 ＋ 1））
     * @return String 缺口等级标识
     */
    private String levelOf(int supplyCount, long heat, long gapScore) {
        if (supplyCount == 0 && heat >= 10) {
            return "HIGH";
        }
        if (gapScore >= 40) {
            return "HIGH";
        }
        if (supplyCount == 0 || gapScore >= 15) {
            return "MID";
        }
        return "LOW";
    }

    private String suggestion(String city, long[] a, int supplyCount, List<String> themes, boolean forGuide) {
        String themePart = (themes == null || themes.isEmpty())
                ? ""
                : "，需求集中在" + String.join("、", themes);
        if (supplyCount == 0) {
            return forGuide
                    ? "有" + a[1] + " 支队伍、 " + a[0] + " 篇攻略提到" + city + "，但平台上还没有向导服务" + themePart
                    : "有" + a[1] + " 支队伍、 " + a[0] + " 篇攻略提到" + city + "，该目的地还没有向导服务" + themePart
                            + "，可对这些队伍做定向投放";
        }
        return forGuide
                ? city +"已有" + supplyCount + " 个向导服务，需求仍在增长" + themePart + "，可考虑差异化定价"
                : city +"已有" + supplyCount + " 件相关商品，可做组合套餐" + themePart;
    }

    // ==================== ④出行方式与预算 ====================

    private List<Map<String, Object>> transportMix() {
        Map<String, List<Trip>> byMode = new TreeMap<>();
        int total = 0;
        for (Trip t : tripRepository.findAll()) {
            String mode = s(t.getTransport()).isEmpty() ? "OTHER" : t.getTransport();
            byMode.computeIfAbsent(mode, k -> new ArrayList<>()).add(t);
            total++;
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, List<Trip>> e : byMode.entrySet()) {
            List<Trip> trips = e.getValue();
            double avgBudget = trips.stream()
                    .filter(t -> t.getBudget() != null)
                    .mapToDouble(Trip::getBudget)
                    .average()
                    .orElse(0);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("mode", e.getKey());
            m.put("label", TRANSPORT_LABEL.getOrDefault(e.getKey(), e.getKey()));
            m.put("count", trips.size());
            m.put("ratio", total == 0 ? 0 : round2(trips.size() / (double) total));
            m.put("avgBudget", Math.round(avgBudget));
            list.add(m);
        }
        list.sort(Comparator.comparingInt((Map<String, Object> m) -> -((Number) m.get("count")).intValue()));
        return list;
    }

    private Map<String, Object> budgetProfile() {
        List<Double> budgets = tripRepository.findAll().stream()
                .map(Trip::getBudget)
                .filter(b -> b != null && b > 0)
                .map(Double::doubleValue)
                .sorted()
                .toList();
        Map<String, Object> out = new LinkedHashMap<>();
        if (budgets.isEmpty()) {
            out.put("avg", 0);
            out.put("min", 0);
            out.put("max", 0);
            out.put("bands", List.of());
            out.put("sample", 0);
            return out;
        }
        out.put("avg", Math.round(budgets.stream().mapToDouble(Double::doubleValue).average().orElse(0)));
        out.put("min", Math.round(budgets.get(0)));
        out.put("max", Math.round(budgets.get(budgets.size() - 1)));
        out.put("median", Math.round(budgets.get(budgets.size() / 2)));
        out.put("sample", budgets.size());

        // 分档：≤500/500-1000/1000-2000 / >2000
        String[] labels = {"500元以内", "500-1000元", "1000-2000元", "2000元以上"};
        long[] counts = new long[4];
        for (Double b : budgets) {
            if (b <= 500) {
                counts[0]++;
            } else if (b <= 1000) {
                counts[1]++;
            } else if (b <= 2000) {
                counts[2]++;
            } else {
                counts[3]++;
            }
        }
        List<Map<String, Object>> bands = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", labels[i]);
            m.put("count", counts[i]);
            m.put("ratio", round2(counts[i] / (double) budgets.size()));
            bands.add(m);
        }
        out.put("bands", bands);
        return out;
    }

    /**
     * 兴趣标签需求分布 → 建议经营品类。
     *
     * @param productSupply 各品类的在售商品数；传null表示不叠加供给（导游视角无品类供给）
     * @return List 按需求队伍数降序排列的主题列表
     */
    private List<Map<String, Object>> themeDemand(Map<String, Integer> productSupply) {
        Map<String, Integer> bag = new LinkedHashMap<>();
        int tripCount = 0;
        Set<String> seen = new LinkedHashSet<>();
        for (Trip t : tripRepository.findAll()) {
            tripCount++;
            seen.clear();
            for (String tag : splitTags(t.getInterestTags())) {
                seen.add(tag);
            }
            for (String tag : seen) {
                bag.merge(tag, 1, Integer::sum);
            }
        }
        final int total = tripCount; // lambda里只能引用effectively final的变量
        List<Map<String, Object>> list = new ArrayList<>();
        bag.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> {
                    String category = THEME_CATEGORY.getOrDefault(e.getKey(), "旅行日用");
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("tag", e.getKey());
                    m.put("count", e.getValue());
                    m.put("ratio", total == 0 ? 0 : round2(e.getValue() / (double) total));
                    m.put("categoryHint", category);
                    if (productSupply != null) {
                        int stock = productSupply.getOrDefault(category, 0);
                        m.put("productCount", stock);
                        // 需求队伍数 ÷（在售商品数+1）：每件商品摊到多少支有需求的队伍
                        m.put("gapScore", Math.round(e.getValue() / (double) (stock + 1) * 10) / 10.0);
                        m.put("level", stock == 0 && e.getValue() >= 2 ? "HIGH" : (stock == 0 ? "MID" : "LOW"));
                    }
                    list.add(m);
                });
        return list;
    }

    // ==================== 汇总文案 ====================

    private String headline(List<Map<String, Object>> gaps,
                            List<Map<String, Object>> transportMix) {
        List<String> blank = gaps.stream()
                .filter(g -> ((Number) g.get("supplyCount")).intValue() == 0)
                .map(g -> String.valueOf(g.get("city")))
                .limit(3)
                .toList();
        StringBuilder sb = new StringBuilder();
        if (!blank.isEmpty()) {
            sb.append("向导服务空白城市：")
                    .append(String.join("、", blank));
        } else {
            sb.append("各热门目的地均有供给，可关注差异化定价");
        }
        if (!transportMix.isEmpty()) {
            Map<String, Object> top = transportMix.get(0);
            int pct = (int) Math.round(((Number) top.get("ratio")).doubleValue() * 100);
            sb.append(" · 结伴队伍 ").append(pct).append("% 选").append(top.get("label"));
        }
        return sb.toString();
    }

    /**
     * 商家视角的汇总文案：只讲品类选品机会（有需求、但对应品类在售商品少的标签）。
     * @param themeDemand 主题需求列表（商家视角下叠加了在售商品数）
     * @return String 一句话选品建议
     */
    private String merchantHeadline(List<Map<String, Object>> themeDemand) {
        List<Map<String, Object>> opportunities = themeDemand.stream()
                .filter(t -> t.containsKey("productCount"))
                .filter(t -> ((Number) t.get("productCount")).intValue() == 0)
                .sorted((a, b) -> Integer.compare(
                        ((Number) b.get("count")).intValue(),
                        ((Number) a.get("count")).intValue()))
                .limit(3)
                .toList();
        if (opportunities.isEmpty()) {
            return "热门主题均已有商品覆盖，可考虑做差异化选品或升级货品结构";
        }
        StringBuilder sb = new StringBuilder("选品机会：");
        for (int i = 0; i < opportunities.size(); i++) {
            Map<String, Object> t = opportunities.get(i);
            if (i > 0) {
                sb.append("、");
            }
            sb.append(t.get("tag"))
                    .append("（")
                    .append(t.get("count"))
                    .append(" 支队伍，建议经营")
                    .append(t.get("categoryHint"))
                    .append("，暂无在售）");
        }
        return sb.toString();
    }

    private Map<String, Object> sampleInfo(boolean forGuide) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("trips", tripRepository.count());
        m.put("posts", postRepository.findAll().stream().filter(p -> "APPROVED".equals(p.getStatus())).count());
        m.put("services", forGuide ? guideServiceRepository.findApproved(null).size() : 0);
        m.put("products", forGuide ? 0 : productRepository.count());
        m.put("minTrips", MIN_TRIPS);
        m.put("enough", tripRepository.count() >= MIN_TRIPS);
        return m;
    }

    // ==================== 工具 ====================

    private List<String> splitTags(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : csv.split("[,，、\\s]+")) {
            if (!part.isBlank()) {
                out.add(part.trim());
            }
        }
        return out;
    }

    private double round2(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private String s(String v) {
        return v == null ? "" : v;
    }

    private boolean notBlank(String v) {
        return v != null && !v.isBlank();
    }
}
