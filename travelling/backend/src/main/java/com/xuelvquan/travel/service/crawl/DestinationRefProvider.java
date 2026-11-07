package com.xuelvquan.travel.service.crawl;

import com.fasterxml.jackson.databind.JsonNode;
import com.xuelvquan.travel.entity.ScrapedInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 目的地参考采集器（真实数据源：Open-Meteo公开地理编码+气象预报接口，均无需密钥）。
 *
 * 面向"只看不订"场景中"目的地基本情况"这类公开信息：采集目的地城市的真实坐标、
 * 海拔、所属行政区、常住人口，以及未来三天天气与出行提示，结构化入库为REFERENCE类型。
 *
 * 与车次/航班/酒店/门票等商业数据不同，本采集器使用的数据源是公开、稳定且
 * 允许程序化访问的开放接口，因此在离线环境下也能真实抓取成功。
 */
@Slf4j
@Component
public class DestinationRefProvider implements ScrapedInfoProvider {

    /** 采集目标城市（与平台覆盖的目的地一致） */
    private static final List<String> CITIES = List.of(
            "长沙", "张家界", "凤凰古城", "重庆", "成都", "西安", "上海", "厦门", "武汉", "广州");

    /** 城市检索别名（拼音）：避免中文同名地点误匹配 */
    private static final Map<String, String> ALIAS = Map.of(
            "长沙", "Changsha",
            "张家界", "Zhangjiajie",
            "凤凰古城", "Fenghuang",
            "重庆", "Chongqing",
            "成都", "Chengdu",
            "西安", "Xian",
            "上海", "Shanghai",
            "厦门", "Xiamen",
            "武汉", "Wuhan",
            "广州", "Guangzhou");

    /** 城市中心参考坐标，用于校验地理编码结果、剔除同名误匹配 */
    private static final Map<String, double[]> EXPECTED = Map.of(
            "长沙", new double[]{28.2282, 112.9388},
            "张家界", new double[]{29.1170, 110.4790},
            "凤凰古城", new double[]{27.9480, 109.5990},
            "重庆", new double[]{29.5630, 106.5516},
            "成都", new double[]{30.5728, 104.0668},
            "西安", new double[]{34.3416, 108.9398},
            "上海", new double[]{31.2304, 121.4737},
            "厦门", new double[]{24.4798, 118.0894},
            "武汉", new double[]{30.5928, 114.3055},
            "广州", new double[]{23.1291, 113.2644});

    /** 允许的最大偏差（公里），超出视为误匹配 */
    private static final double MAX_DEVIATION_KM = 150;

    private static final String[] WEEKDAYS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    @Override
    public String code() {
        return "REF_OPEN_METEO";
    }

    @Override
    public String label() {
        return "目的地参考采集器";
    }

    @Override
    public String type() {
        return "REFERENCE";
    }

    @Override
    public String source() {
        return "Open-Meteo 公开气象与地理数据";
    }

    @Override
    public FetchResult fetch() {
        LocalDateTime now = LocalDateTime.now();
        List<ScrapedInfo> items = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (String city : CITIES) {
            try {
                JsonNode geo = geocode(city);
                if (geo == null) {
                    errors.add(city + "：地理编码无结果");
                    continue;
                }
                double lat = geo.path("latitude").asDouble();
                double lng = geo.path("longitude").asDouble();
                String admin1 = text(geo, "admin1");
                String admin2 = text(geo, "admin2");
                String country = text(geo, "country");
                int elevation = geo.path("elevation").asInt(0);
                long population = geo.path("population").asLong(0);

                List<String> days = forecast(lat, lng);

                StringBuilder note = new StringBuilder();
                note.append("坐标 ").append(round2(lat)).append(", ").append(round2(lng));
                if (elevation > 0) {
                    note.append(" · 海拔 ").append(elevation).append(" 米");
                }
                String region = (admin1 + " " + admin2).trim();
                if (!region.isBlank()) {
                    note.append(" · 所在地 ").append(region);
                }
                if (population > 0) {
                    note.append(" · 常住人口 ").append(population / 10000).append(" 万");
                }
                if (!days.isEmpty()) {
                    note.append(" ｜ 未来三天：").append(String.join(" / ", days));
                }

                items.add(build(city, days.isEmpty() ? "目的地地理与气候参考" : days.get(0),
                        note.toString(), now));
            } catch (Exception e) {
                errors.add(city + "：" + brief(e));
            }
        }

        if (items.isEmpty()) {
            // 外部接口完全不可达：回落离线模板，如实标注
            return FetchResult.fallback(template(now),
                    "Open-Meteo 接口不可达（" + String.join("；", errors) + "），已回落离线模板");
        }
        String note = "真实抓取" + items.size() + " 个目的地";
        if (!errors.isEmpty()) {
            note += "；" + errors.size() + " 个城市失败：" + String.join("；", errors);
        }
        return FetchResult.real(items, note);
    }

    // ==================== Open-Meteo ====================

    /**
     * 地理编码：用拼音别名检索多个候选，再按"与城市中心参考坐标的距离"挑选最匹配的一条，
     * 距离超出 #MAX_DEVIATION_KM视为同名误匹配。
     * @param city 城市中文名
     * @return JsonNode 命中的地理编码结果；无结果或全是误匹配时返回null
     */
    private JsonNode geocode(String city) throws Exception {
        String query = ALIAS.getOrDefault(city, city);
        String url = "https://geocoding-api.open-meteo.com/v1/search?name="
                + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8)
                + "&count=5&language=zh&format=json";
        JsonNode root = CrawlHttp.getJson(url, null);
        JsonNode results = root.get("results");
        if (results == null || !results.isArray() || results.isEmpty()) {
            return null;
        }
        double[] expect = EXPECTED.get(city);
        JsonNode best = null;
        double bestDist = Double.MAX_VALUE;
        for (JsonNode r : results) {
            if (!"CN".equals(r.path("country_code").asText())) {
                continue;
            }
            if (expect == null) {
                // 无参考坐标时退化为"取人口最多的候选"
                if (best == null || r.path("population").asLong(0) > best.path("population").asLong(0)) {
                    best = r;
                }
                continue;
            }
            double dist = distanceKm(expect[0], expect[1],
                    r.path("latitude").asDouble(), r.path("longitude").asDouble());
            if (dist < bestDist) {
                bestDist = dist;
                best = r;
            }
        }
        if (best != null && expect != null && bestDist > MAX_DEVIATION_KM) {
            return null; // 所有候选都离城市中心太远，视为同名误匹配
        }
        return best;
    }

    /**
     * 两点间大圆距离（公里）
     * @param lat1 起点纬度
     * @param lng1 起点经度
     * @param lat2 终点纬度
     * @param lng2 终点经度
     * @return double 两点间的球面距离，单位公里
     */
    private double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    /**
     * 未来三天天气摘要，如 "10-11周六 晴18~26℃"
     * @param lat 目的地纬度
     * @param lng 目的地经度
     * @return List 逐日天气摘要；接口不可达时返回空列表
     */
    private List<String> forecast(double lat, double lng) {
        List<String> out = new ArrayList<>();
        try {
            String url = String.format(java.util.Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                            + "&daily=weather_code,temperature_2m_max,temperature_2m_min,"
                            + "precipitation_probability_max&timezone=Asia%%2FShanghai&forecast_days=3",
                    lat, lng);
            JsonNode root = CrawlHttp.getJson(url, null);
            JsonNode daily = root.get("daily");
            if (daily == null) {
                return out;
            }
            JsonNode times = daily.get("time");
            JsonNode codes = daily.get("weather_code");
            JsonNode maxs = daily.get("temperature_2m_max");
            JsonNode mins = daily.get("temperature_2m_min");
            JsonNode rains = daily.get("precipitation_probability_max");
            for (int i = 0; i < times.size(); i++) {
                LocalDate date = LocalDate.parse(times.get(i).asText());
                int rain = rains == null || rains.get(i).isNull() ? 0 : rains.get(i).asInt();
                out.add(String.format("%s %s %s %.0f~%.0f℃ 降水概率%d%%",
                        date.toString().substring(5), WEEKDAYS[date.getDayOfWeek().getValue() - 1],
                        codeText(codes.get(i).asInt()),
                        mins.get(i).asDouble(), maxs.get(i).asDouble(), rain));
            }
        } catch (Exception e) {
            log.debug("[学旅圈] 目的地参考天气抓取失败：{}", e.getMessage());
        }
        return out;
    }

    // ==================== 组装 ====================

    private ScrapedInfo build(String city, String schedule, String note, LocalDateTime now) {
        ScrapedInfo i = new ScrapedInfo();
        i.setType("REFERENCE");
        i.setOrigin(null);
        i.setDestination(city);
        i.setName(city + " 目的地参考");
        i.setSchedule(schedule);
        i.setPriceMin(null);
        i.setPriceMax(null);
        i.setRating(null);
        i.setSource(source());
        i.setNote(note);
        i.setUpdatedAt(now);
        return i;
    }

    private List<ScrapedInfo> template(LocalDateTime now) {
        List<ScrapedInfo> list = new ArrayList<>();
        for (String city : CITIES) {
            list.add(build(city, "目的地地理与气候参考（离线模板）",
                    "外部开放接口暂不可达，已回落离线模板：坐标、海拔与气候数据请以官方或地图服务发布为准。", now));
        }
        return list;
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? "" : v.asText();
    }

    private String codeText(int code) {
        if (code == 0) return "晴";
        if (code <= 2) return "多云";
        if (code == 3) return "阴";
        if (code <= 48) return "雾";
        if (code <= 57) return "毛毛雨";
        if (code <= 67) return "雨";
        if (code <= 77) return "雪";
        if (code <= 82) return "阵雨";
        if (code <= 86) return "阵雪";
        return "雷阵雨";
    }

    private double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private String brief(Exception e) {
        String m = e.getMessage();
        if (m == null) {
            return e.getClass().getSimpleName();
        }
        return m.length() > 40 ? m.substring(0, 40) : m;
    }
}
