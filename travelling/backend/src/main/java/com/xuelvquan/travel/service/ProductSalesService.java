package com.xuelvquan.travel.service;

import com.xuelvquan.travel.common.BusinessException;
import com.xuelvquan.travel.entity.Merchant;
import com.xuelvquan.travel.entity.OrderItem;
import com.xuelvquan.travel.entity.Product;
import com.xuelvquan.travel.entity.ProductOrder;
import com.xuelvquan.travel.entity.ProductReview;
import com.xuelvquan.travel.repository.MerchantRepository;
import com.xuelvquan.travel.repository.OrderItemRepository;
import com.xuelvquan.travel.repository.ProductOrderRepository;
import com.xuelvquan.travel.repository.ProductRepository;
import com.xuelvquan.travel.repository.ProductReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

/**
 * 商家销售数据洞察与商品平替推荐服务
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductSalesService {

    private final MerchantRepository merchantRepository;
    private final ProductRepository productRepository;
    private final ProductOrderRepository orderRepository;
    private final ProductReviewRepository reviewRepository;
    private final OrderItemRepository orderItemRepository;

    private static final String[] QUARTER_LABELS = {"Q1 1–3月", "Q2 4–6月", "Q3 7–9月", "Q4 10–12月"};
    private static final String[] MONTH_LABELS = {
            "1月", "2月", "3月", "4月", "5月", "6月",
            "7月", "8月", "9月", "10月", "11月", "12月"
    };

    // ==================== 商家侧：销售洞察 ====================

    public Map<String, Object> merchantInsight(Long userId) {
        Merchant shop = merchantRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("尚未开通店铺"));
        List<Product> products = productRepository.search(null, null, shop.getId());
        // 取消订单不计入交易量，否则高峰分布会被退款单污染
        List<ProductOrder> orders = orderRepository.findByMerchantIdOrderByCreatedAtDesc(shop.getId()).stream()
                .filter(o -> !"CANCELLED".equals(o.getStatus()))
                .toList();
        Map<Long, double[]> reviewStat = reviewStats();

        double revenue = orders.stream()
                .mapToDouble(o -> o.getAmount() == null ? 0 : o.getAmount())
                .sum();
        int orderCount = orders.size();
        int itemSold = products.stream().mapToInt(p -> p.getSales() == null ? 0 : p.getSales()).sum();

        // 季度/月度聚合（跨年份合并，用于看季节性而不是看某一年）
        int[] qOrders = new int[4];
        double[] qRevenue = new double[4];
        int[] mOrders = new int[12];
        double[] mRevenue = new double[12];
        TreeSet<YearMonth> months = new TreeSet<>();
        for (ProductOrder o : orders) {
            LocalDateTime t = o.getCreatedAt();
            if (t == null) {
                continue;
            }
            int qi = (t.getMonthValue() - 1) / 3;
            double amt = o.getAmount() == null ? 0 : o.getAmount();
            qOrders[qi]++;
            qRevenue[qi] += amt;
            mOrders[t.getMonthValue() - 1]++;
            mRevenue[t.getMonthValue() - 1] += amt;
            months.add(YearMonth.from(t));
        }

        List<Map<String, Object>> quarters = new ArrayList<>();
        int peakQuarter = -1;
        for (int i = 0; i < 4; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", "Q" + (i + 1));
            row.put("label", QUARTER_LABELS[i]);
            row.put("orders", qOrders[i]);
            row.put("revenue", round2(qRevenue[i]));
            row.put("ratio", orderCount == 0 ? 0 : Math.round(qOrders[i] * 100.0 / orderCount));
            quarters.add(row);
            if (qOrders[i] > 0 && (peakQuarter < 0 || qRevenue[i] > qRevenue[peakQuarter])) {
                peakQuarter = i;
            }
        }

        List<Map<String, Object>> monthRows = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", i + 1);
            row.put("label", MONTH_LABELS[i]);
            row.put("orders", mOrders[i]);
            row.put("revenue", round2(mRevenue[i]));
            monthRows.add(row);
        }
        int peakMonth = -1;
        int lowMonth = -1;
        for (int i = 0; i < 12; i++) {
            if (mOrders[i] == 0) {
                continue;
            }
            if (peakMonth < 0 || mRevenue[i] > mRevenue[peakMonth]) {
                peakMonth = i;
            }
            if (lowMonth < 0 || mRevenue[i] < mRevenue[lowMonth]) {
                lowMonth = i;
            }
        }

        // 商品维度：成交额按销量×单价估算
        List<Map<String, Object>> productRows = new ArrayList<>();
        for (Product p : products) {
            int sold = p.getSales() == null ? 0 : p.getSales();
            double price = p.getPrice() == null ? 0 : p.getPrice();
            double[] rs = reviewStat.getOrDefault(p.getId(), new double[]{0, 0});
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", p.getId());
            row.put("name", p.getName());
            row.put("image", p.getImage());
            row.put("category", p.getCategory());
            row.put("price", price);
            row.put("stock", p.getStock());
            row.put("sales", sold);
            row.put("revenue", round2(sold * price));
            row.put("ratingAvg", rs[0] == 0 ? null : Math.round(rs[0] / rs[1] * 10) / 10.0);
            row.put("reviewCount", (int) rs[1]);
            row.put("share", itemSold == 0 ? 0 : Math.round(sold * 100.0 / itemSold));
            productRows.add(row);
        }
        productRows.sort((a, b) -> Double.compare((double) b.get("revenue"), (double) a.get("revenue")));

        // 品类维度统计
        Map<String, double[]> catStat = new LinkedHashMap<>();
        for (Product p : products) {
            String c = p.getCategory() == null || p.getCategory().isBlank() ? "未分类" : p.getCategory();
            double[] v = catStat.computeIfAbsent(c, k -> new double[3]);
            int sold = p.getSales() == null ? 0 : p.getSales();
            v[0] += sold;
            v[1] += sold * (p.getPrice() == null ? 0 : p.getPrice());
            v[2] += p.getStock() == null ? 0 : p.getStock();
        }
        List<Map<String, Object>> categories = new ArrayList<>();
        for (Map.Entry<String, double[]> e : catStat.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("category", e.getKey());
            row.put("sales", (int) e.getValue()[0]);
            row.put("revenue", round2(e.getValue()[1]));
            row.put("stock", (int) e.getValue()[2]);
            categories.add(row);
        }
        categories.sort((a, b) -> Double.compare((double) b.get("revenue"), (double) a.get("revenue")));

        // 滞销：有库存但卖不动（库存 >= 20且 销量<库存的1/5）
        List<Map<String, Object>> slow = productRows.stream()
                .filter(r -> ((int) r.get("stock")) >= 20 && ((int) r.get("sales")) * 5 < ((int) r.get("stock")))
                .sorted(Comparator.comparingInt(r -> (int) r.get("sales")))
                .limit(3)
                .toList();

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("productCount", products.size());
        overview.put("orderCount", orderCount);
        overview.put("revenue", round2(revenue));
        overview.put("itemSold", itemSold);
        overview.put("avgOrderValue", orderCount == 0 ? 0 : round2(revenue / orderCount));
        overview.put("monthCount", months.size());

        Map<String, Object> peak = new LinkedHashMap<>();
        peak.put("quarter", peakQuarter < 0 ? null : "Q" + (peakQuarter + 1));
        peak.put("quarterLabel", peakQuarter < 0 ? null : QUARTER_LABELS[peakQuarter]);
        peak.put("quarterRevenue", peakQuarter < 0 ? 0 : round2(qRevenue[peakQuarter]));
        peak.put("month", peakMonth < 0 ? null : peakMonth + 1);
        peak.put("monthLabel", peakMonth < 0 ? null : MONTH_LABELS[peakMonth]);
        peak.put("lowMonthLabel", lowMonth < 0 || lowMonth == peakMonth ? null : MONTH_LABELS[lowMonth]);

        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("monthCount", months.size());
        coverage.put("firstMonth", months.isEmpty() ? null : months.iterator().next().toString());
        coverage.put("lastMonth", months.isEmpty() ? null : months.last().toString());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("shopName", shop.getShopName());
        out.put("overview", overview);
        out.put("quarters", quarters);
        out.put("months", monthRows);
        out.put("peak", peak);
        out.put("coverage", coverage);
        out.put("topProducts", productRows.stream().limit(5).toList());
        out.put("products", productRows);
        out.put("categories", categories);
        out.put("slowProducts", slow);
        out.put("advice", buildAdvice(peak, months.size(), quarters, productRows, slow));
        out.put("note", "成交额按「累计销量 × 当前单价」估算；季度 / 月度分布取自真实订单时间。"
                +"单价调整会影响历史成交额的估算值，请以订单明细为准。");
        return out;
    }

    /**
     * 统计本店「品类×月份」销量矩阵，作为热力图数据源
     * @param shopId 店铺ID
     * @return Map 含categories（品类列表）、cells（品类-月份销量单元）、total（总销量）、insufficient（明细是否充足）
     */
    private Map<String, Object> categoryMonths(Long shopId) {
        // 本店商品id → 品类
        Map<Long, String> catOf = new HashMap<>();
        for (Product p : productRepository.search(null, null, shopId)) {
            catOf.put(p.getId(), p.getCategory() == null || p.getCategory().isBlank() ? "未分类" : p.getCategory());
        }
        // 本店订单id → 下单月份
        Map<Long, Integer> monthOf = new HashMap<>();
        for (ProductOrder o : orderRepository.findByMerchantIdOrderByCreatedAtDesc(shopId)) {
            if (o.getCreatedAt() != null && !"CANCELLED".equals(o.getStatus())) {
                monthOf.put(o.getId(), o.getCreatedAt().getMonthValue());
            }
        }
        Map<String, int[]> matrix = new LinkedHashMap<>(); // 品类 → 12个月销量
        int total = 0;
        for (OrderItem it : orderItemRepository.findAll()) {
            String cat = catOf.get(it.getProductId());
            Integer month = monthOf.get(it.getOrderId());
            if (cat == null || month == null) {
                continue;
            }
            int q = it.getQuantity() == null ? 1 : it.getQuantity();
            matrix.computeIfAbsent(cat, k -> new int[12])[month - 1] += q;
            total += q;
        }
        List<String> categories = new ArrayList<>(matrix.keySet());
        categories.sort(Comparator.comparingInt((String c) ->
                -Arrays.stream(matrix.get(c)).sum()));
        List<Map<String, Object>> cells = new ArrayList<>();
        for (String cat : categories) {
            int[] arr = matrix.get(cat);
            for (int i = 0; i < 12; i++) {
                if (arr[i] > 0) {
                    Map<String, Object> cell = new LinkedHashMap<>();
                    cell.put("category", cat);
                    cell.put("month", i + 1);
                    cell.put("count", arr[i]);
                    cells.add(cell);
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("categories", categories);
        out.put("cells", cells);
        out.put("total", total);
        out.put("insufficient", total < 20); // 明细不足时由前端演示样例兜底
        return out;
    }

    private List<String> buildAdvice(Map<String, Object> peak, int monthCount,
                                     List<Map<String, Object>> quarters,
                                     List<Map<String, Object>> products,
                                     List<Map<String, Object>> slow) {
        List<String> advice = new ArrayList<>();
        Object pq = peak.get("quarterLabel");
        Object pm = peak.get("monthLabel");
        if (pq != null) {
            advice.add("当前交易高峰在" + pq + "，占全部订单 "
                    + quarters.get(Integer.parseInt(String.valueOf(peak.get("quarter")).substring(1)) - 1).get("ratio")
                    + "%。建议高峰前2–4周完成备货，并把广告投放预算压到高峰前半个月。");
        }
        if (pm != null) {
            String text = "月度看，" + pm + " 成交额最高";
            Object low = peak.get("lowMonthLabel");
            if (low != null) {
                text += "，" + low + " 相对最低，可在低峰做限时折扣或「热销品 + 滞销品」组合装平滑销量";
            }
            advice.add(text + "。");
        }
        if (monthCount > 0 && monthCount < 12) {
            advice.add("目前只有" + monthCount + " 个月的真实订单数据，季节结论会随数据积累更准，"
                    +"建议先按「高峰前备货、低峰清库存」的思路试运行。");
        }
        if (products.size() > 1) {
            Map<String, Object> top = products.get(0);
            advice.add("「" + top.get("name") + "」贡献了约" + top.get("share")
                    + "% 的销量（成交额 ¥" + top.get("revenue") + "），是店铺主推款，"
                    +"保持库存充足并优先给它导流。");
        }
        for (Map<String, Object> s : slow) {
            advice.add("「" + s.get("name") + "」库存" + s.get("stock") + " 件、累计只卖出 "
                    + s.get("sales") + " 件，建议降价清仓，或在商品详情页挂到热销款旁边做关联推荐。");
        }
        return advice;
    }

    // ==================== 买家侧：平替/替代品 ====================

    public Map<String, Object> alternatives(Long productId) {
        Product cur = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException("商品不存在"));
        double curPrice = cur.getPrice() == null ? 0 : cur.getPrice();
        Set<String> curTags = useTags(cur);
        Map<Long, double[]> stat = reviewStats();
        Map<Long, String> shopNames = shopNames();

        List<Product> sameCategory = productRepository.findAll().stream()
                .filter(p -> !Objects.equals(p.getId(), cur.getId()))
                .filter(p -> p.getCategory() != null && p.getCategory().equals(cur.getCategory()))
                .filter(p -> p.getStock() == null || p.getStock() > 0)
                .toList();

        List<Map<String, Object>> cheaper = new ArrayList<>();
        List<Map<String, Object>> premium = new ArrayList<>();
        List<Map<String, Object>> others = new ArrayList<>();
        for (Product p : sameCategory) {
            double price = p.getPrice() == null ? 0 : p.getPrice();
            int sold = p.getSales() == null ? 0 : p.getSales();
            double[] rs = stat.getOrDefault(p.getId(), new double[]{0, 0});
            Double rating = rs[1] == 0 ? null : Math.round(rs[0] / rs[1] * 10) / 10.0;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", p.getId());
            row.put("name", p.getName());
            row.put("category", p.getCategory());
            row.put("price", price);
            row.put("sales", sold);
            row.put("stock", p.getStock());
            row.put("image", p.getImage());
            row.put("ratingAvg", rating);
            row.put("reviewCount", (int) rs[1]);
            row.put("shopName", shopNames.getOrDefault(p.getMerchantId(), "学旅自营"));
            row.put("priceDiff", round2(price - curPrice));
            row.put("savingPct", curPrice <= 0 ? 0 : Math.round((curPrice - price) * 100.0 / curPrice));

            // 只有「用途相同」才谈得上替代：同品类但一个是背包一个是保温杯，不能叫平替
            Set<String> tags = useTags(p);
            tags.retainAll(curTags);
            boolean comparable = !tags.isEmpty();

            if (!comparable) {
                row.put("kind", "OTHER");
                row.put("reason", "同品类热销，用途与你正在看的商品不同");
                others.add(row);
                continue;
            }
            if (curPrice > 0 && price <= curPrice * 0.9) {
                row.put("kind", "CHEAPER");
                row.put("reason", "同用途便宜" + Math.round((curPrice - price) * 100.0 / curPrice)
                        + "%（省 ¥" + round2(curPrice - price) + "），已售" + sold + " 件");
                cheaper.add(row);
            } else if (curPrice > 0 && price >= curPrice * 1.1) {
                row.put("kind", "PREMIUM");
                row.put("reason", "同用途更高配，贵 ¥" + round2(price - curPrice)
                        + (rating == null ? "" : "，评分" + rating)
                        + "，已售" + sold + " 件");
                premium.add(row);
            }
        }
        // 平替优先「卖得好」（销量代表别人验证过），同销量再比价格
        cheaper.sort((a, b) -> {
            int c = Integer.compare((int) b.get("sales"), (int) a.get("sales"));
            return c != 0 ? c : Double.compare((double) a.get("price"), (double) b.get("price"));
        });
        premium.sort((a, b) -> {
            double ra = a.get("ratingAvg") == null ? 0 : (double) a.get("ratingAvg");
            double rb = b.get("ratingAvg") == null ? 0 : (double) b.get("ratingAvg");
            int c = Double.compare(rb, ra);
            return c != 0 ? c : Integer.compare((int) b.get("sales"), (int) a.get("sales"));
        });
        others.sort((a, b) -> Integer.compare((int) b.get("sales"), (int) a.get("sales")));

        String note;
        if (sameCategory.isEmpty()) {
            note = "平台该品类暂时只有这一件在售，暂无同品类可比价的替代品";
        } else if (cheaper.isEmpty() && premium.isEmpty()) {
            note = "该品类下没有功能相近的替代品，可看看同类其他热销；"
                    +"如果你只是想换个牌子，建议优先比较售后与库存。";
        } else {
            note = "推荐基于同品类、同用途商品的价格、销量与评分，仅供参考";
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("product", Map.of(
                "id", cur.getId(),
                "name", cur.getName(),
                "category", cur.getCategory() == null ? "" : cur.getCategory(),
                "price", curPrice));
        out.put("cheaper", cheaper.stream().limit(3).toList());
        out.put("premium", premium.stream().limit(2).toList());
        out.put("others", others.stream().limit(3).toList());
        out.put("note", note);
        return out;
    }

    /** 从商品名称与描述中提取用途标签 */
    private static final String[] USE_KEYWORDS = {
            "背包", "帐篷", "防潮垫", "睡袋", "徒步鞋", "登山鞋", "冲锋衣", "速干",
            "保温", "水壶", "移动电源", "充电", "相机", "镜头", "头灯", "手电",
            "炉", "炊具", "登山杖", "护膝", "雨衣", "指南针", "地图", "压缩袋", "吊床"
    };

    private Set<String> useTags(Product p) {
        String text = ((p.getName() == null ? "" : p.getName()) + " "
                + (p.getDescription() == null ? "" : p.getDescription()));
        Set<String> tags = new HashSet<>();
        for (String k : USE_KEYWORDS) {
            if (text.contains(k)) {
                tags.add(k);
            }
        }
        return tags;
    }

    /** productId → [评分合计, 评价条数] */
    private Map<Long, double[]> reviewStats() {
        Map<Long, double[]> stat = new HashMap<>();
        for (ProductReview r : reviewRepository.findAll()) {
            if (r.getProductId() == null || r.getRating() == null) {
                continue;
            }
            double[] v = stat.computeIfAbsent(r.getProductId(), k -> new double[2]);
            v[0] += r.getRating();
            v[1] += 1;
        }
        return stat;
    }

    private Map<Long, String> shopNames() {
        Map<Long, String> m = new HashMap<>();
        for (Merchant s : merchantRepository.findAll()) {
            m.put(s.getId(), s.getShopName());
        }
        return m;
    }

    private double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
