package com.xuelvquan.travel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xuelvquan.travel.entity.Spot;
import com.xuelvquan.travel.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 天气服务（动态行程调整等外部因素接入）。
 *
 * 数据来源优先使用Open-Meteo公开预报接口（无需密钥），坐标取自平台景点库；
 * 当外部接口不可用（无网络/超时/城市无坐标）时，回落到按城市与日期确定性生成的
 * 演示数据，保证功能在任何环境下都可演示，且同一城市同一天的演示数据保持稳定。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WeatherService {

    private final SpotRepository spotRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    /** 无景点坐标时的城市兜底坐标（长沙及常见目的地） */
    private static final Map<String, double[]> CITY_COORDS = new LinkedHashMap<>();

    static {
        CITY_COORDS.put("长沙", new double[]{28.2282, 112.9388});
        CITY_COORDS.put("张家界", new double[]{29.1170, 110.4790});
        CITY_COORDS.put("凤凰古城", new double[]{27.9480, 109.5990});
        CITY_COORDS.put("重庆", new double[]{29.5630, 106.5516});
        CITY_COORDS.put("成都", new double[]{30.5728, 104.0668});
        CITY_COORDS.put("西安", new double[]{34.3416, 108.9398});
        CITY_COORDS.put("上海", new double[]{31.2304, 121.4737});
        CITY_COORDS.put("厦门", new double[]{24.4798, 118.0894});
        CITY_COORDS.put("武汉", new double[]{30.5928, 114.3055});
        CITY_COORDS.put("广州", new double[]{23.1291, 113.2644});
    }

    private static final String[] WEEKDAYS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    /**
     * 查询目的地未来天气。
     *
     * @param city 目的地城市
     * @param days 需要的天数（1~7）
     * @return Map 含城市、数据来源、逐日天气数组与一句总体提示
     */
    public Map<String, Object> forecast(String city, int days) {
        int d = Math.max(1, Math.min(days, 7));
        String target = city == null || city.isBlank() ? "长沙" : city;
        List<Map<String, Object>> list;

        double[] coord = resolveCoord(target);
        list = fetchRemote(coord, d);
        boolean real = list != null && !list.isEmpty();
        if (!real) {
            list = generateDemo(target, d);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("city", target);
        result.put("source", real ? "Open-Meteo 公开预报" : "平台演示数据（外部接口不可用）");
        result.put("realTime", real);
        result.put("days", list);
        result.put("summary", summarize(list));
        return result;
    }

    /**
     * 判断某一天是否为"需要调整行程"的恶劣天气（下雨/下雪/雷暴）
     * @param day 单日天气数据（含text与rainChance）
     * @return boolean true表示属于恶劣天气，false表示可以正常出行
     */
    public boolean isBadWeather(Map<String, Object> day) {
        if (day == null) {
            return false;
        }
        String text = String.valueOf(day.getOrDefault("text", ""));
        Object rainP = day.get("rainChance");
        int rain = rainP instanceof Number ? ((Number) rainP).intValue() : 0;
        return text.contains("雨") || text.contains("雪") || text.contains("雷") || rain >= 60;
    }

    // ==================== 外部接口 ====================

    private List<Map<String, Object>> fetchRemote(double[] coord, int days) {
        if (coord == null) {
            return null;
        }
        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min,"
                        + "precipitation_probability_max,wind_speed_10m_max"
                        + "&timezone=Asia%%2FShanghai&forecast_days=%d", coord[0], coord[1], days);
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(4))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return null;
            }
            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode daily = root.get("daily");
            if (daily == null) {
                return null;
            }
            JsonNode times = daily.get("time");
            JsonNode codes = daily.get("weather_code");
            JsonNode maxs = daily.get("temperature_2m_max");
            JsonNode mins = daily.get("temperature_2m_min");
            JsonNode rains = daily.get("precipitation_probability_max");
            JsonNode winds = daily.get("wind_speed_10m_max");

            List<Map<String, Object>> list = new ArrayList<>();
            for (int i = 0; i < times.size() && i < days; i++) {
                LocalDate date = LocalDate.parse(times.get(i).asText());
                int code = codes.get(i).asInt();
                list.add(buildDay(date, code,
                        mins.get(i).asDouble(), maxs.get(i).asDouble(),
                        rains.get(i).isNull() ? 0 : rains.get(i).asInt(),
                        winds.get(i).isNull() ? 0 : winds.get(i).asDouble()));
            }
            return list;
        } catch (Exception e) {
            log.warn("[学旅圈] 天气接口调用失败，回落演示数据：{}", e.getMessage());
            return null;
        }
    }

    private double[] resolveCoord(String city) {
        List<Spot> spots = spotRepository.findAll().stream()
                .filter(s -> city.equals(s.getCity()) && s.getLat() != null && s.getLng() != null)
                .toList();
        if (!spots.isEmpty()) {
            double lat = spots.stream().mapToDouble(Spot::getLat).average().orElse(0);
            double lng = spots.stream().mapToDouble(Spot::getLng).average().orElse(0);
            if (lat != 0 && lng != 0) {
                return new double[]{lat, lng};
            }
        }
        return CITY_COORDS.get(city);
    }

    private Map<String, Object> buildDay(LocalDate date, int code, double min, double max,
                                         int rainChance, double windKmh) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", date.toString());
        m.put("weekday", WEEKDAYS[date.getDayOfWeek().getValue() - 1]);
        m.put("text", codeText(code));
        m.put("icon", codeIcon(code));
        m.put("level", codeLevel(code));
        m.put("tempMin", round1(min));
        m.put("tempMax", round1(max));
        m.put("rainChance", rainChance);
        m.put("windLevel", windText(windKmh));
        m.put("advice", advice(min, max, rainChance));
        return m;
    }

    private String codeText(int code) {
        switch (code) {
            case 0: return "晴";
            case 1: return "少云";
            case 2: return "多云";
            case 3: return "阴";
            case 45: case 48: return "有雾";
            case 51: case 53: case 55: case 56: case 57: return "毛毛雨";
            case 61: return "小雨";
            case 63: return "中雨";
            case 65: return "大雨";
            case 66: case 67: return "冻雨";
            case 71: return "小雪";
            case 73: return "中雪";
            case 75: case 77: return "大雪";
            case 80: return "阵雨";
            case 81: return "强阵雨";
            case 82: return "暴雨";
            case 85: case 86: return "阵雪";
            case 95: return "雷阵雨";
            case 96: case 99: return "雷暴冰雹";
            default: return "多云";
        }
    }

    private String codeIcon(int code) {
        if (code == 0) return "☀️";
        if (code <= 2) return "🌤️";
        if (code == 3) return "☁️";
        if (code <= 48) return "🌫️";
        if (code <= 67) return "🌧️";
        if (code <= 77) return "❄️";
        if (code <= 82) return "🌦️";
        if (code <= 86) return "🌨️";
        return "⛈️";
    }

    private String codeLevel(int code) {
        if (code >= 95) return "SEVERE";
        if (code >= 71) return "SNOW";
        if (code >= 51) return "RAIN";
        if (code == 45 || code == 48) return "FOG";
        if (code <= 3) return "CLEAR";
        return "CLOUD";
    }

    private String windText(double kmh) {
        if (kmh >= 39) return "6级以上大风";
        if (kmh >= 20) return "3-4级";
        if (kmh >= 12) return "2-3级";
        return "1-2级";
    }

    private String advice(double min, double max, int rainChance) {
        StringBuilder sb = new StringBuilder();
        if (max >= 32) {
            sb.append("高温注意防晒补水；");
        } else if (max >= 24) {
            sb.append("短袖为主，早晚可加薄外套；");
        } else if (max >= 16) {
            sb.append("长袖 + 薄外套正合适；");
        } else if (max >= 8) {
            sb.append("建议厚外套 / 毛衣；");
        } else {
            sb.append("需羽绒服、帽子手套；");
        }
        if (rainChance >= 60) {
            sb.append("降雨概率高，记得带伞，户外行程建议备选室内方案");
        } else if (rainChance >= 30) {
            sb.append("可能有阵雨，建议携带折叠伞");
        } else {
            sb.append("天气适合出行");
        }
        return sb.toString();
    }

    // ==================== 演示数据回退 ====================

    private List<Map<String, Object>> generateDemo(String city, int days) {
        List<Map<String, Object>> list = new ArrayList<>();
        LocalDate today = LocalDate.now();
        long seed = city.hashCode() * 31L + today.toEpochDay();
        Random rnd = new Random(seed);
        for (int i = 0; i < days; i++) {
            LocalDate date = today.plusDays(i);
            int[] options = {0, 2, 3, 61, 63, 80};
            int code = options[rnd.nextInt(options.length)];
            double max = 16 + rnd.nextInt(14);
            double min = max - 6 - rnd.nextInt(4);
            int rain = code >= 51 ? 60 + rnd.nextInt(35) : rnd.nextInt(25);
            list.add(buildDay(date, code, min, max, rain, 5 + rnd.nextInt(20)));
        }
        return list;
    }

    private String summarize(List<Map<String, Object>> list) {
        long bad = list.stream().filter(this::isBadWeather).count();
        if (bad == 0) {
            return "未来" + list.size() + " 天整体天气不错，行程可正常执行。";
        }
        return "未来" + list.size() + " 天中有" + bad + " 天可能出现降雨等不利天气，建议提前准备调整方案。";
    }

    private double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    public String formatDate(LocalDate d) {
        return d.format(DateTimeFormatter.ISO_DATE) + " " + WEEKDAYS[d.getDayOfWeek().getValue() - 1];
    }
}
