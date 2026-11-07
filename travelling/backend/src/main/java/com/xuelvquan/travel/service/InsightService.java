package com.xuelvquan.travel.service;

import com.xuelvquan.travel.entity.Expense;
import com.xuelvquan.travel.entity.Post;
import com.xuelvquan.travel.entity.ProductOrder;
import com.xuelvquan.travel.entity.ScrapedInfo;
import com.xuelvquan.travel.entity.Trip;
import com.xuelvquan.travel.entity.User;
import com.xuelvquan.travel.repository.ExpenseRepository;
import com.xuelvquan.travel.repository.PostRepository;
import com.xuelvquan.travel.repository.ProductOrderRepository;
import com.xuelvquan.travel.repository.ScrapedInfoRepository;
import com.xuelvquan.travel.repository.TripRepository;
import com.xuelvquan.travel.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 匿名聚合洞察服务：对外公开看板的聚合统计，不暴露任何可识别到个人的字段。
 */
@Service
@RequiredArgsConstructor
public class InsightService {

    /** 发帖/组队/采集三类信号在热度中的权重 */
    private static final int W_POST = 3;
    private static final int W_TRIP = 5;
    private static final int W_CRAWL = 1;

    private final PostRepository postRepository;
    private final TripRepository tripRepository;
    private final ScrapedInfoRepository scrapedInfoRepository;
    private final ProductOrderRepository productOrderRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;

    @Value("${xlvq.insight.min-sample:30}")
    private int minSample;

    /**
     * 目的地热度榜：按发帖、出行队伍、采集热度加权得到的公开聚合值。
     *
     * @return Map 含通过样本量门槛的Top 10榜单、因样本不足被隐藏的目的地数量，以及全量地图数据
     */
    public Map<String, Object> destinationHeat() {
        Map<String, int[]> stat = new LinkedHashMap<>(); // destination -> [post, trip, crawl]

        for (Post p : postRepository.findAll()) {
            if (!"APPROVED".equals(p.getStatus())) {
                continue;
            }
            String d = clean(p.getDestination());
            if (d != null) {
                stat.computeIfAbsent(d, k -> new int[3])[0]++;
            }
        }
        for (Trip t : tripRepository.findAll()) {
            String d = clean(t.getDestination());
            if (d != null) {
                stat.computeIfAbsent(d, k -> new int[3])[1]++;
            }
        }
        for (ScrapedInfo s : scrapedInfoRepository.findAll()) {
            String d = clean(s.getDestination());
            if (d != null) {
                stat.computeIfAbsent(d, k -> new int[3])[2]++;
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        int suppressed = 0;
        for (Map.Entry<String, int[]> e : stat.entrySet()) {
            int[] v = e.getValue();
            int raw = v[0] + v[1] + v[2];
            // 脱敏硬门槛：样本量不足一律不出
            if (raw < minSample) {
                suppressed++;
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("destination", e.getKey());
            m.put("postCount", v[0]);
            m.put("tripCount", v[1]);
            m.put("crawlCount", v[2]);
            m.put("sampleSize", raw);
            m.put("heat", v[0] * W_POST + v[1] * W_TRIP + v[2] * W_CRAWL);
            rows.add(m);
        }
        rows.sort(Comparator.comparingInt((Map<String, Object> m) -> (int) m.get("heat")).reversed());

        // 榜单只取Top 10，地图返回全量达标目的地
        List<Map<String, Object>> items = rows.size() > 10 ? new ArrayList<>(rows.subList(0, 10)) : rows;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("threshold", minSample);
        out.put("destinationsTotal", stat.size());
        out.put("suppressedCount", suppressed);
        out.put("insufficient", items.isEmpty());
        out.put("items", items);
        out.put("mapItems", rows);
        out.put("weights", Map.of("post", W_POST, "trip", W_TRIP, "crawl", W_CRAWL));
        return out;
    }

    // ============================================================
    // ②学生消费画像
    // ============================================================

    /**
     * 学生消费画像：商城订单金额区间分布 ＋ 行程消费类目占比 ＋ 人均消费区间分布。
     * 每个区间/类目样本量不足 #minSample一律不输出。
     */
    public Map<String, Object> consumptionProfile() {
        // --- 商城订单：按订单金额分档 ---
        List<ProductOrder> orders = productOrderRepository.findAll();
        int[] orderBuckets = new int[4];
        int validOrders = 0;
        for (ProductOrder o : orders) {
            if (!"DONE".equals(o.getStatus()) || o.getAmount() == null) {
                continue;
            }
            orderBuckets[bucketOf(o.getAmount())]++;
            validOrders++;
        }

        // --- 行程消费：按类目占比+人均消费分档 ---
        List<Expense> expenses = expenseRepository.findAll();
        Map<String, Integer> categoryCount = new TreeMap<>();
        Map<String, Double> categoryAmount = new TreeMap<>();
        int[] personBuckets = new int[4];
        int validExpenses = 0;
        for (Expense e : expenses) {
            String c = clean(e.getCategory());
            if (c == null) {
                c = "OTHER";
            }
            categoryCount.merge(c, 1, Integer::sum);
            categoryAmount.merge(c, e.getAmount() == null ? 0.0 : e.getAmount(), Double::sum);
            Double per = e.getPerPerson() != null ? e.getPerPerson() : e.getAmount();
            if (per != null) {
                personBuckets[bucketOf(per)]++;
            }
            validExpenses++;
        }

        List<Map<String, Object>> orderDist = new ArrayList<>();
        List<Map<String, Object>> personDist = new ArrayList<>();
        int suppressedBuckets = 0;
        String[] labels = {"300元以下", "300–800元", "800–1500元", "1500元以上"};
        for (int i = 0; i < 4; i++) {
            if (orderBuckets[i] >= minSample) {
                orderDist.add(bucket("ORDER", labels[i], orderBuckets[i], 0));
            } else {
                suppressedBuckets++;
            }
            if (personBuckets[i] >= minSample) {
                personDist.add(bucket("PERSON", labels[i], personBuckets[i], 0));
            } else {
                suppressedBuckets++;
            }
        }

        double totalAmount = categoryAmount.values().stream().mapToDouble(Double::doubleValue).sum();
        List<Map<String, Object>> categories = new ArrayList<>();
        for (Map.Entry<String, Integer> e : categoryCount.entrySet()) {
            if (e.getValue() < minSample) {
                suppressedBuckets++;
                continue;
            }
            double amt = categoryAmount.getOrDefault(e.getKey(), 0.0);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("category", e.getKey());
            m.put("categoryLabel", categoryLabel(e.getKey()));
            m.put("count", e.getValue());
            m.put("amount", round2(amt));
            m.put("share", totalAmount > 0 ? round2(amt * 100 / totalAmount) : 0.0);
            categories.add(m);
        }
        categories.sort(Comparator.comparingDouble((Map<String, Object> m) -> (double) m.get("amount")).reversed());

        boolean insufficient = orderDist.isEmpty() && personDist.isEmpty() && categories.isEmpty();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("threshold", minSample);
        out.put("sample", Map.of(
                "orders", validOrders,
                "expenses", validExpenses,
                "ordersTotal", orders.size(),
                "expensesTotal", expenses.size()));
        out.put("suppressedBuckets", suppressedBuckets);
        out.put("insufficient", insufficient);
        out.put("orderDistribution", orderDist);
        out.put("perPersonDistribution", personDist);
        out.put("categoryShare", categories);
        return out;
    }

    // ============================================================
    // ③看板概览（含机制说明，供前端渲染顶部说明）
    // ============================================================

    /** 公开看板的元信息：门槛、数据量、脱敏机制说明。 */
    public Map<String, Object> summary() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("threshold", minSample);
        out.put("volumes", Map.of(
                "posts", postRepository.count(),
                "trips", tripRepository.count(),
                "crawl", scrapedInfoRepository.count(),
                "orders", productOrderRepository.count(),
                "expenses", expenseRepository.count()));
        out.put("principle", "本看板仅输出聚合统计量，样本量不足" + minSample
                + " 一律不展示；页面不含任何用户昵称、头像、学号等可识别到个人的字段。");
        return out;
    }

    // ============================================================
    // ④兴趣—目的地—消费 桑基流转（信息关联图）
    // ============================================================

    /**
     * 兴趣标签 → 目的地城市 → 消费类目 三层流转，线条宽度代表人数。
     *
     * 左→中为发帖作者兴趣标签与帖子目的地，中→右为行程目的地与消费类目，均为聚合统计量。
     */
    public Map<String, Object> interestFlow() {
        Map<String, Integer> interestNode = new LinkedHashMap<>();
        Map<String, Integer> cityNode = new LinkedHashMap<>();
        Map<String, Integer> linkIC = new LinkedHashMap<>(); // 兴趣→目的地

        for (Post p : postRepository.findAll()) {
            String dest = clean(p.getDestination());
            if (dest == null) {
                dest = clean(p.getCity());
            }
            if (dest == null || p.getAuthorId() == null) {
                continue;
            }
            User author = userRepository.findById(p.getAuthorId()).orElse(null);
            String interests = author == null ? null : clean(author.getInterests());
            if (interests == null) {
                continue;
            }
            cityNode.merge(dest, 1, Integer::sum);
            for (String it : interests.split("[,，、;；]")) {
                String tag = clean(it);
                if (tag == null || "长沙".equals(tag) || "成都".equals(tag) || "重庆".equals(tag)) {
                    continue; // 兴趣栏里混入的城市名不算兴趣
                }
                interestNode.merge(tag, 1, Integer::sum);
                linkIC.merge(tag + "\u0001" + dest, 1, Integer::sum);
            }
        }

        Map<String, Integer> catNode = new LinkedHashMap<>();
        Map<String, Integer> linkCC = new LinkedHashMap<>(); // 目的地→消费类目
        Map<Long, Trip> trips = new LinkedHashMap<>();
        for (Trip t : tripRepository.findAll()) {
            trips.put(t.getId(), t);
        }
        for (Expense e : expenseRepository.findAll()) {
            if (e.getTripId() == null) {
                continue;
            }
            Trip t = trips.get(e.getTripId());
            if (t == null) {
                continue;
            }
            String dest = clean(t.getDestination());
            if (dest == null) {
                continue;
            }
            String cat = categoryLabel(e.getCategory() == null ? "OTHER" : e.getCategory());
            catNode.merge(cat, 1, Integer::sum);
            linkCC.merge(dest + "\u0001" + cat, 1, Integer::sum);
        }

        // 节点收敛：兴趣/目的地各取Top 8，避免长尾把图搅成一团毛线
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<String> keepInterests = topKeys(interestNode, 8);
        List<String> keepCities = topKeys(cityNode, 8);
        for (String k : keepInterests) {
            nodes.add(Map.of("name", k, "layer", "兴趣"));
        }
        for (String k : keepCities) {
            nodes.add(Map.of("name", k, "layer", "目的地"));
        }
        for (String k : catNode.keySet()) {
            nodes.add(Map.of("name", k, "layer", "消费类目"));
        }

        List<Map<String, Object>> links = new ArrayList<>();
        for (Map.Entry<String, Integer> e : linkIC.entrySet()) {
            int sep = e.getKey().indexOf('\u0001');
            String src = e.getKey().substring(0, sep);
            String dst = e.getKey().substring(sep + 1);
            if (keepInterests.contains(src) && keepCities.contains(dst)) {
                links.add(Map.of("source", src, "target", dst, "value", e.getValue()));
            }
        }
        for (Map.Entry<String, Integer> e : linkCC.entrySet()) {
            int sep = e.getKey().indexOf('\u0001');
            String src = e.getKey().substring(0, sep);
            String dst = e.getKey().substring(sep + 1);
            if (keepCities.contains(src)) {
                links.add(Map.of("source", src, "target", dst, "value", e.getValue()));
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("nodes", nodes);
        out.put("links", links);
        out.put("insufficient", nodes.isEmpty() || links.isEmpty());
        out.put("note", "线条宽度 = 人数 / 笔数：左层为发帖作者兴趣标签，右层为行程消费类目。");
        return out;
    }

    /**
     * 导游三层供给体系：学生导游 → 平台认证导游 → 旅行社的数量结构。
     * 真实数据总量过少时返回insufficient标记。
     *
     * @return Map 含各层级名称、数量与总计，以及样本不足标记
     */
    public Map<String, Object> supplyPyramid() {
        int student = 0;
        int certified = 0;
        int agency = 0;
        for (User u : userRepository.findAll()) {
            if ("GUIDE".equals(u.getRole())) {
                if (Boolean.TRUE.equals(u.getVerified())) {
                    certified++;
                } else {
                    student++;
                }
            } else if ("MERCHANT".equals(u.getRole())) {
                agency++;
            }
        }
        List<Map<String, Object>> levels = new ArrayList<>();
        levels.add(Map.of("name", "学生导游", "value", student,
                "gate", "注册即可接单", "desc", "在校学生凭兴趣与课余时间带团"));
        levels.add(Map.of("name", "平台认证导游", "value", certified,
                "gate", "实名认证 + 信誉分 ≥ 80", "desc", "通过平台资质审核的专业导游"));
        levels.add(Map.of("name", "旅行社", "value", agency,
                "gate", "企业入驻 + 资质备案", "desc", "提供成规模线路与地接服务"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("levels", levels);
        out.put("total", student + certified + agency);
        out.put("insufficient", student + certified + agency < 6);
        return out;
    }

    /**
     * 取计数最高的前n个key（按插入序稳定输出）
     * @param m 计数表
     * @param n 最多取几个
     * @return List 按计数降序排列的key列表
     */
    private List<String> topKeys(Map<String, Integer> m, int n) {
        return m.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(e -> -e.getValue())
                        .thenComparing(Map.Entry::getKey))
                .limit(n)
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * 金额分档：0:<300，1:300–800，2:800–1500，3:>1500
     * @param amount 待分档的金额
     * @return int 分档序号，0至3
     */
    private int bucketOf(double amount) {
        if (amount < 300) return 0;
        if (amount < 800) return 1;
        if (amount <= 1500) return 2;
        return 3;
    }

    private Map<String, Object> bucket(String type, String label, int count, double amount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("label", label);
        m.put("count", count);
        if (amount > 0) {
            m.put("amount", round2(amount));
        }
        return m;
    }

    private String categoryLabel(String code) {
        return switch (code) {
            case "TRANSPORT" -> "交通";
            case "HOTEL" -> "住宿";
            case "FOOD" -> "餐饮";
            case "TICKET" -> "门票";
            case "SHOPPING" -> "购物";
            default -> "其他";
        };
    }

    private String clean(String s) {
        if (s == null) {
            return null;
        }
        String v = s.trim();
        return v.isEmpty() ? null : v;
    }

    private double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
