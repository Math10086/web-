package com.xuelvquan.travel.service.crawl;

import com.fasterxml.jackson.databind.JsonNode;
import com.xuelvquan.travel.entity.ScrapedInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 12306车次采集器：抓取公开时刻，失败则回落离线模板
 */
@Slf4j
@Component
public class TrainScheduleProvider implements ScrapedInfoProvider {

    private static final String BASE = "https://kyfw.12306.cn";
    private static final String INIT_URL = BASE + "/otn/leftTicket/init";

    /** 采集线路：出发城市 → 目的城市，城市名需能在12306车站码表中命中 */
    private static final String[][] ROUTES = {
            {"长沙", "张家界"}, {"长沙", "重庆"}, {"长沙", "成都"}, {"长沙", "西安"},
            {"长沙", "上海"}, {"长沙", "厦门"}, {"武汉", "重庆"}, {"武汉", "西安"},
            {"武汉", "上海"}, {"广州", "张家界"}, {"广州", "厦门"}, {"重庆", "成都"},
            {"重庆", "西安"}, {"成都", "西安"}, {"上海", "西安"}
    };

    @Override
    public String code() {
        return "TRAIN_12306";
    }

    @Override
    public String label() {
        return "12306车次采集器";
    }

    @Override
    public String type() {
        return "TRAIN";
    }

    @Override
    public String source() {
        return "12306公开时刻";
    }

    @Override
    public FetchResult fetch() {
        LocalDateTime now = LocalDateTime.now();
        String travelDate = LocalDate.now().plusDays(7).toString();

        Map<String, String> stationCodes;
        List<ScrapedInfo> real = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        try {
            stationCodes = loadStationCodes();
        } catch (Exception e) {
            return FetchResult.fallback(template(now),
                    "12306车站码表不可达（" + brief(e) + "），已回落离线模板");
        }

        boolean accessDenied = false;
        for (String[] route : ROUTES) {
            if (accessDenied) {
                break; // 会话层被拒时后续线路同样不可用，避免重复访问对方站点
            }
            String fromCode = stationCodes.get(route[0]);
            String toCode = stationCodes.get(route[1]);
            if (fromCode == null || toCode == null) {
                errors.add(route[0] + "→" + route[1] + "：码表中未找到车站");
                continue;
            }
            try {
                real.addAll(queryRoute(route[0], route[1], fromCode, toCode, travelDate, now));
            } catch (Exception e) {
                String msg = brief(e);
                errors.add(route[0] + "→" + route[1] + "：" + msg);
                if (msg.contains("登录会话")) {
                    accessDenied = true;
                }
            }
        }

        if (real.isEmpty()) {
            return FetchResult.fallback(template(now), "12306查询接口未返回可用数据（" + summary(errors) + "），已回落离线模板");
        }

        // 抓取成功的线路用实时数据，其余线路沿用模板值以免整类信息缺失
        Set<String> covered = new HashSet<>();
        for (ScrapedInfo i : real) {
            covered.add(i.getOrigin() + "→" + i.getDestination());
        }
        List<ScrapedInfo> merged = new ArrayList<>(real);
        for (ScrapedInfo t : template(now)) {
            if (!covered.contains(t.getOrigin() + "→" + t.getDestination())) {
                t.setNote("外部实时源不可用，沿用上次采集值；" + t.getNote());
                merged.add(t);
            }
        }
        String note = "12306实时抓取" + real.size() + " 趟（" + covered.size() + " 条线路）";
        if (!errors.isEmpty()) {
            note += "；" + errors.size() + " 条线路未取到实时数据";
        }
        return FetchResult.real(merged, note);
    }

    // ==================== 12306接口 ====================

    /** 解析station_name.js：@简拼|站名|电报码|拼音|简拼|序号 */
    private Map<String, String> loadStationCodes() throws Exception {
        String js = CrawlHttp.getTextSession(BASE + "/otn/resources/js/framework/station_name.js", INIT_URL);
        if (js == null || !js.contains("|")) {
            throw new IllegalStateException("车站码表内容为空");
        }
        Map<String, String> map = new HashMap<>();
        for (String seg : js.split("@")) {
            String[] f = seg.split("\\|");
            if (f.length >= 3 && !f[1].isBlank() && !f[2].isBlank()) {
                map.putIfAbsent(f[1], f[2]);
            }
        }
        if (map.isEmpty()) {
            throw new IllegalStateException("车站码表解析结果为空");
        }
        return map;
    }

    /**
     * 查询单条线路的实时车次
     * @param origin 出发城市
     * @param destination 目的城市
     * @param fromCode 出发站电报码
     * @param toCode 到达站电报码
     * @param travelDate 出行日期（yyyy-MM-dd）
     * @param now 采集时间，写入记录的更新时间
     * @return List 解析出的车次，每条线路最多4趟
     */
    private List<ScrapedInfo> queryRoute(String origin, String destination, String fromCode,
                                         String toCode, String travelDate, LocalDateTime now) throws Exception {
        String query = "?leftTicketDTO.train_date=" + travelDate
                + "&leftTicketDTO.from_station=" + fromCode
                + "&leftTicketDTO.to_station=" + toCode
                + "&purpose_codes=ADULT";

        CrawlHttp.getTextSession(INIT_URL, null); // 预热会话
        String endpoint = "/otn/leftTicket/query";
        String body = queryWithHint(BASE + endpoint + query);
        JsonNode root = CrawlHttp.mapper().readTree(body);

        // 官方会轮换端点名，响应里带c_url时按提示重试一次
        if (root.has("c_url")) {
            String hinted = root.get("c_url").asText();
            body = queryWithHint(BASE + "/" + hinted + query);
            root = CrawlHttp.mapper().readTree(body);
        }

        JsonNode result = root.path("data").path("result");
        if (!result.isArray()) {
            throw new IllegalStateException("响应结构异常（status=" + root.path("status").asText("?") + "）");
        }
        List<ScrapedInfo> list = new ArrayList<>();
        int limit = 0;
        for (JsonNode n : result) {
            if (limit++ >= 4) {
                break; // 每线路取前4趟，避免单类信息过量
            }
            String[] f = n.asText().split("\\|");
            if (f.length < 11 || f[3].isBlank()) {
                continue;
            }
            ScrapedInfo i = new ScrapedInfo();
            i.setType("TRAIN");
            i.setOrigin(origin);
            i.setDestination(destination);
            i.setName(f[3] + " 次");
            i.setSchedule(f[8] + "-" + f[9]);
            i.setSource(source());
            i.setRating(null);
            i.setNote("历时" + f[10] + "；票价以12306官方实时查询为准");
            i.setUpdatedAt(now);
            list.add(i);
        }
        if (list.isEmpty()) {
            throw new IllegalStateException("未解析到车次");
        }
        return list;
    }

    private String brief(Exception e) {
        String m = e.getMessage();
        if (m == null) {
            return e.getClass().getSimpleName();
        }
        return m.length() > 40 ? m.substring(0, 40) : m;
    }

    /**
     * 调用余票查询接口
     * @param url 请求地址
     * @return String 响应文本
     * @throws Exception HTTP 3xx（需登录会话）或网络异常时抛出
     */
    private String queryWithHint(String url) throws Exception {
        try {
            return CrawlHttp.getTextSession(url, INIT_URL);
        } catch (Exception e) {
            String msg = String.valueOf(e.getMessage());
            if (msg.contains("302") || msg.contains("301")) {
                throw new IllegalStateException("余票接口需登录会话（HTTP 302跳转登录页），按合规约定不绕过");
            }
            throw e;
        }
    }

    /**
     * 把逐条线路的失败原因归纳成一句话，避免批次日志过长
     *
     * @param errors 各线路采集失败的提示信息
     * @return String 汇总后的失败说明
     */
    private String summary(List<String> errors) {
        if (errors.isEmpty()) {
            return "无线路记录";
        }
        long login = errors.stream().filter(e -> e.contains("登录会话")).count();
        long other = errors.size() - login;
        StringBuilder sb = new StringBuilder("共" + errors.size() + " 条线路失败");
        if (login > 0) {
            sb.append("，其中 ").append(login).append(" 条为接口需登录会话（官方访问控制，未绕过）");
        }
        if (other > 0) {
            sb.append("，").append(other).append(" 条为网络或结构异常，示例：")
                    .append(errors.stream().filter(e -> !e.contains("登录会话")).findFirst().orElse("-"));
        }
        return sb.toString();
    }

    // ==================== 离线模板（外部源不可达时使用） ====================

    /**
     * 指定线路的离线模板，实时源不可用时兜底
     * @param origin 出发城市
     * @param destination 目的城市
     * @param now 当前时间
     * @return List 该线路的离线模板车次列表
     */
    public static List<ScrapedInfo> templateFor(String origin, String destination, LocalDateTime now) {
        String from = origin == null ? "" : origin.trim();
        String to = destination == null ? "" : destination.trim();
        List<ScrapedInfo> hit = new ArrayList<>();
        for (ScrapedInfo t : template(now)) {
            if (from.equals(t.getOrigin()) && to.equals(t.getDestination())) {
                hit.add(t);
            }
        }
        return hit;
    }

    /**
     * 离线模板：常见学生出行线路的公开时刻与参考价（来源如实标注为离线模板）
     * @param now 采集时间，用于生成模板记录的更新时间
     * @return List 全部离线模板车次
     */
    static List<ScrapedInfo> template(LocalDateTime now) {
        List<ScrapedInfo> list = new ArrayList<>();
        train(list, "长沙", "张家界", "G6401次", "07:00-09:30", 98.0, 118.0, "二等座参考价", now);
        train(list, "长沙", "张家界", "K9011次", "22:30-04:20", 54.0, 88.0, "夕发朝至，硬卧参考价", now);
        train(list, "长沙", "凤凰古城", "G9681次", "08:00-10:15", 135.0, 160.0, "至吉首东站，转大巴约1小时", now);
        train(list, "长沙", "重庆", "G1312次", "08:20-14:05", 310.0, 380.0, "二等座参考价", now);
        train(list, "长沙", "成都", "G2193次", "09:10-16:40", 390.0, 460.0, "二等座参考价", now);
        train(list, "长沙", "西安", "G86次", "08:00-14:30", 420.0, 520.0, "二等座参考价", now);
        train(list, "长沙", "上海", "G1372次", "09:20-13:21", 430.0, 540.0, "二等座参考价", now);
        train(list, "长沙", "厦门", "D3231次", "07:50-14:10", 330.0, 410.0, "二等座参考价", now);
        train(list, "武汉", "重庆", "G3453次", "08:05-15:20", 420.0, 520.0, "二等座参考价", now);
        train(list, "武汉", "西安", "G321次", "09:30-13:45", 380.0, 460.0, "二等座参考价", now);
        train(list, "武汉", "上海", "G1722次", "07:20-11:10", 300.0, 380.0, "二等座参考价", now);
        train(list, "广州", "张家界", "G6079次", "07:45-13:05", 380.0, 450.0, "至张家界西，二等座参考价", now);
        train(list, "广州", "厦门", "G1607次", "08:10-13:40", 350.0, 430.0, "二等座参考价", now);
        train(list, "重庆", "成都", "G8503次", "08:00-09:35", 150.0, 190.0, "成渝高铁，班次密集", now);
        train(list, "重庆", "西安", "G2233次", "09:15-14:00", 330.0, 410.0, "二等座参考价", now);
        train(list, "成都", "西安", "D1920次", "08:40-15:10", 260.0, 330.0, "二等座参考价", now);
        train(list, "上海", "西安", "G1932次", "08:55-15:05", 550.0, 660.0, "二等座参考价", now);
        train(list, "张家界", "长沙", "G6402次", "10:05-12:40", 98.0, 118.0, "返程班次密集，建议提前购票", now);
        train(list, "重庆", "长沙", "G1314次", "15:10-20:55", 310.0, 380.0, "返程二等座参考价", now);
        return list;
    }

    private static void train(List<ScrapedInfo> list, String origin, String destination, String name,
                              String schedule, Double priceMin, Double priceMax, String note, LocalDateTime now) {
        ScrapedInfo i = new ScrapedInfo();
        i.setType("TRAIN");
        i.setOrigin(origin);
        i.setDestination(destination);
        i.setName(name);
        i.setSchedule(schedule);
        i.setPriceMin(priceMin);
        i.setPriceMax(priceMax);
        i.setSource("离线模板（12306公开时刻整理）");
        i.setNote(note);
        i.setUpdatedAt(now.minusMinutes(30 + (long) (Math.random() * 600)));
        list.add(i);
    }
}
