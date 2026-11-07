package com.xuelvquan.travel.service;

import com.xuelvquan.travel.entity.ScrapedInfo;
import com.xuelvquan.travel.entity.Spot;
import com.xuelvquan.travel.repository.ScrapedInfoRepository;
import com.xuelvquan.travel.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 数据采集与"只看不订"信息浏览。
 *
 * 说明：真实环境由定时爬虫采集公开信息入库；本服务负责对外提供采集结果查询、
 * 模拟采集刷新（更新时间）、以及基于采集价与景点门票的"全程费用成本估算"。
 * 估算数值均为参考区间，页面对用户明确标注"仅供参考、需自行预订"。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScrapedInfoService {

    private final ScrapedInfoRepository scrapedInfoRepository;
    private final SpotRepository spotRepository;

    /** 支持的预算档位（元，人均全程） */
    private static final Map<String, Integer> BUDGET_LEVELS = new LinkedHashMap<>();

    static {
        BUDGET_LEVELS.put("LE1000", 1000);
        BUDGET_LEVELS.put("LE1500", 1500);
        BUDGET_LEVELS.put("LE2000", 2000);
        BUDGET_LEVELS.put("LE3000", 3000);
        BUDGET_LEVELS.put("NONE", 0);
    }

    private static final Map<String, String> BUDGET_LABELS = Map.of(
            "LE1000", "1000元以内",
            "LE1500", "1500元以内",
            "LE2000", "2000元以内",
            "LE3000", "3000元以内",
            "NONE", "不限预算"
    );

    /**
     * 按类型/目的地/出发地查询采集信息（出发地仅对车次/航班有意义）
     * @param type 信息类型，如TRAIN/HOTEL/TICKET，为空则不限
     * @param destination 目的地城市，为空则不限
     * @param origin 出发地城市，为空则不限
     * @return List 按价格升序排列的采集信息列表
     */
    public List<ScrapedInfo> list(String type, String destination, String origin) {
        String t = blank(type) ? null : type;
        String d = blank(destination) ? null : destination;
        String o = blank(origin) ? null : origin;
        List<ScrapedInfo> base;
        if (t != null && d != null) {
            base = scrapedInfoRepository.findByTypeAndDestinationOrderByPriceMinAsc(t, d);
        } else if (t != null) {
            base = scrapedInfoRepository.findByTypeOrderByPriceMinAsc(t);
        } else if (d != null) {
            base = scrapedInfoRepository.findByDestinationOrderByTypeAscPriceMinAsc(d);
        } else {
            base = scrapedInfoRepository.findAll().stream()
                    .sorted(Comparator.comparing(ScrapedInfo::getType)
                            .thenComparing(x -> x.getPriceMin() == null ? 0 : x.getPriceMin()))
                    .toList();
        }
        if (o == null) {
            return base;
        }
        return base.stream().filter(i -> o.equals(i.getOrigin())).toList();
    }

    /** 已采集数据覆盖的出发地城市（车次/航班） */
    public List<String> origins() {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (ScrapedInfo i : scrapedInfoRepository.findByTypeInOrderByPriceMinAsc(List.of("TRAIN", "FLIGHT"))) {
            if (!blank(i.getOrigin())) {
                set.add(i.getOrigin());
            }
        }
        List<String> list = new ArrayList<>(set);
        list.sort(Comparator.naturalOrder());
        return list;
    }

    /** 已采集数据覆盖的目的地城市 */
    public List<String> cities() {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (ScrapedInfo i : scrapedInfoRepository.findAll()) {
            if (!blank(i.getDestination())) {
                set.add(i.getDestination());
            }
        }
        for (Spot s : spotRepository.findAll()) {
            if (!blank(s.getCity())) {
                set.add(s.getCity());
            }
        }
        List<String> list = new ArrayList<>(set);
        list.sort(Comparator.naturalOrder());
        return list;
    }

    /** 模拟定时采集：刷新全部记录的数据更新时间，返回刷新条数 */
    @Transactional
    public int refresh() {
        List<ScrapedInfo> all = scrapedInfoRepository.findAll();
        LocalDateTime now = LocalDateTime.now();
        for (ScrapedInfo i : all) {
            i.setUpdatedAt(now);
        }
        scrapedInfoRepository.saveAll(all);
        return all.size();
    }

    /**
     * 全程费用成本估算（基于采集到的交通、住宿、门票、餐饮参考价，
     * 为方案输出全程费用成本估算与分项区间，支持按预算档位约束）。
     * @param destination 目的地城市
     * @param days 行程天数，会被限制在1~7天
     * @param budgetLevel 预算档位，如LE1000/NONE
     * @param origin 出发地城市，用于取对应线路的交通参考价
     * @return Map 含分项费用区间、合计区间与是否在预算内
     */
    public Map<String, Object> costEstimate(String destination, int days, String budgetLevel, String origin) {
        int d = Math.max(1, Math.min(days, 7));
        List<ScrapedInfo> transport = list("TRAIN", destination, origin);
        if (transport.isEmpty()) {
            transport = list("FLIGHT", destination, origin);
        }
        List<ScrapedInfo> hotels = list("HOTEL", destination, null);
        List<ScrapedInfo> tickets = list("TICKET", destination, null);

        List<Map<String, Object>> items = new ArrayList<>();

        // 交通（往返）
        double trLow = transport.stream().filter(i -> i.getPriceMin() != null)
                .mapToDouble(ScrapedInfo::getPriceMin).min().orElse(120);
        double trHigh = transport.stream().filter(i -> i.getPriceMax() != null)
                .mapToDouble(ScrapedInfo::getPriceMax).max().orElse(360);
        items.add(item("交通（往返参考）", round2(trLow * 2), round2(trHigh * 2),
                transport.isEmpty() ? "按同区域常规票价估算" : "取自采集到的最低 / 最高班次参考价×2"));

        // 住宿（两人一间分摊，nights = days-1）
        int nights = Math.max(0, d - 1);
        double hotelLow = hotels.stream().filter(i -> i.getPriceMin() != null)
                .mapToDouble(ScrapedInfo::getPriceMin).min().orElse(150);
        double hotelHigh = hotels.stream().filter(i -> i.getPriceMax() != null)
                .mapToDouble(ScrapedInfo::getPriceMax).max().orElse(400);
        items.add(item("住宿（" + nights + " 晚 · 2人分摊）",
                round2(hotelLow * nights / 2), round2(hotelHigh * nights / 2),
                nights == 0 ? "当日往返无需住宿" : "按采集酒店房型参考价 ÷ 2人分摊"));

        // 餐饮
        items.add(item("餐饮（" + d + " 天）", round2(60.0 * d), round2(150.0 * d),
                "学生常规正餐20~50元 / 餐"));

        // 门票
        double tkLow = tickets.stream().filter(i -> i.getPriceMin() != null)
                .mapToDouble(ScrapedInfo::getPriceMin).sum();
        double tkHigh = tickets.stream().filter(i -> i.getPriceMax() != null)
                .mapToDouble(ScrapedInfo::getPriceMax).sum();
        if (tkLow == 0 && tkHigh == 0) {
            tkLow = 30;
            tkHigh = 200;
        }
        items.add(item("门票（主要景点）", round2(tkLow), round2(tkHigh),
                tickets.isEmpty() ? "按同类型景点常规票价估算" : "取自采集到的景点门票参考价合计"));

        double totalLow = items.stream().mapToDouble(m -> ((Number) m.get("low")).doubleValue()).sum();
        double totalHigh = items.stream().mapToDouble(m -> ((Number) m.get("high")).doubleValue()).sum();

        String level = blank(budgetLevel) ? "NONE" : budgetLevel;
        Integer budget = BUDGET_LEVELS.getOrDefault(level, 0);
        boolean withinBudget = budget == 0 || totalHigh <= budget;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("destination", destination == null ? "" : destination);
        result.put("origin", origin == null ? "" : origin);
        result.put("days", d);
        result.put("items", items);
        result.put("totalLow", round2(totalLow));
        result.put("totalHigh", round2(totalHigh));
        result.put("perPersonLow", round2(totalLow));
        result.put("perPersonHigh", round2(totalHigh));
        result.put("budgetLevel", level);
        result.put("budgetLabel", BUDGET_LABELS.getOrDefault(level, "不限预算"));
        result.put("budget", budget);
        result.put("withinBudget", withinBudget);
        result.put("note", "以上为基于公开采集参考价的估算区间，仅供预算参考；实际开销与是否可订以车站 / 酒店 / 官方渠道为准。");
        return result;
    }

    private Map<String, Object> item(String name, double low, double high, String note) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("low", low);
        m.put("high", high);
        m.put("note", note);
        return m;
    }

    private double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
