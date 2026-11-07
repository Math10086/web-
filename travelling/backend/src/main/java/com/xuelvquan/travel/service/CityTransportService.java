package com.xuelvquan.travel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xuelvquan.travel.common.BusinessException;
import com.xuelvquan.travel.entity.TransportOption;
import com.xuelvquan.travel.repository.TransportOptionRepository;
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
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任意两城之间的出行方案计算服务。
 * 驾车里程优先取腾讯地图真实数据，失败降级为直线距离估算；火车优先抓12306实时车次，
 * 失败按里程估算；航班按航段里程给参考价区间。各方式数据来源在结果中如实标注。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CityTransportService {

    public static final String SRC_DB = "库内基准数据";
    public static final String SRC_12306 = "12306实时抓取";
    public static final String SRC_TMAP = "腾讯地图计算";
    public static final String SRC_ESTIMATE = "按里程估算";

    /** 自驾油费：0.55元/公里（普通家用车，油价与油耗的平均口径） */
    private static final double CAR_FUEL_PER_KM = 0.55;
    /** 大巴票价：0.33元/公里（含高速通行与客运成本） */
    private static final double BUS_FARE_PER_KM = 0.33;
    /** 高铁二等座：0.45元/公里 */
    private static final double TRAIN_FARE_PER_KM = 0.45;
    /** 普速硬座：0.22元/公里 */
    private static final double SLOW_TRAIN_FARE_PER_KM = 0.22;
    /** 自驾均摊人数（车内3~4人，按3人计） */
    private static final int CAR_SHARE = 3;
    /** 拼车均摊人数（满员4人） */
    private static final int CARPOOL_SHARE = 4;
    /** 未取到腾讯过路费时的估算单价（元/公里） */
    private static final double TOLL_PER_KM = 0.45;
    /** 腾讯接口不可用时的路网系数（直线距离 → 公路里程） */
    private static final double ROAD_FACTOR = 1.25;
    /** 铁路绕行系数（直线距离 → 铁路里程） */
    private static final double RAIL_FACTOR = 1.22;
    /** 航线绕行系数（直线距离 → 航段里程） */
    private static final double AIR_FACTOR = 1.06;
    /** 超过该公路里程不再给出大巴参考（现实中不会开行这么远的班车） */
    private static final double BUS_MAX_KM = 1500;
    /** 驾车平均速度（km/h，用于降级估算） */
    private static final double DRIVE_SPEED_KMH = 85.0;

    private static final String TENCENT_KEY = "BXNBZ-IUGOT-EAEXY-L22DC-BXEZE-Y6F7J";
    private static final String GEO_URL = "https://apis.map.qq.com/ws/geocoder/v1/";
    private static final String DRIVING_URL = "https://apis.map.qq.com/ws/direction/v1/driving/";
    private static final String RAIL_BASE = "https://kyfw.12306.cn";

    private final TransportOptionRepository transportOptionRepository;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    /** 城市坐标缓存（含腾讯解析结果与内置坐标表兜底） */
    private final Map<String, GeoPoint> geoCache = new ConcurrentHashMap<>();
    /** 城市对驾车里程缓存，避免重复消耗腾讯配额 */
    private final Map<String, DriveLeg> driveCache = new ConcurrentHashMap<>();
    /** 12306车站码表缓存：城市名 → 电报码 */
    private volatile Map<String, String> stationCodes;

    private record GeoPoint(double lat, double lng, String source) {}

    private record DriveLeg(double distanceKm, int durationMin, int toll, String source) {}


    /** 内置城市坐标表覆盖的城市（前端下拉候选，用户也可直接输入其它城市） */
    public List<String> knownCities() {
        return List.copyOf(CITY_COORDS.keySet());
    }

    /**
     * 对外暴露「任意两城」的12306实时车次抓取（供定向采集复用）。
     * 车站码表取自12306全站数据，因此不局限于内置城市池，全国主要城市都能查。
     * 抓不到（接口加密/无匹配站名）时返回空列表，原因写入notices。
     *
     * 注意：内部会往notices里追加原因，因此这里做一次可变副本，
     * 避免调用方传List.of()这类不可变集合导致UnsupportedOperationException。
     * @param origin 出发城市名
     * @param destination 到达城市名
     * @param notices 提示信息列表，未取到车次的原因会追加到这里，可为null
     * @return List 车次明细列表，没有取到时返回空列表
     */
    public List<Map<String, Object>> queryTrainDetails(String origin, String destination, List<String> notices) {
        List<String> sink = notices == null ? new ArrayList<>() : new ArrayList<>(notices);
        return query12306(clean(origin), clean(destination), sink);
    }

    /**
     * 任意两城的出行方案：库内基准优先，缺失的方式用实时采集/里程计算补足
     * @param origin 出发城市名
     * @param destination 到达城市名
     * @return Map 各出行方式的方案，含时长、票价、数据来源与提示
     */
    public Map<String, Object> plan(String origin, String destination) {
        String from = clean(origin);
        String to = clean(destination);
        if (from.isEmpty() || to.isEmpty()) {
            throw new BusinessException("请填写出发地和目的地");
        }
        if (from.equals(to)) {
            throw new BusinessException("出发地和目的地不能相同");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("origin", from);
        out.put("destination", to);

        List<String> notices = new ArrayList<>();

        // 1) 库内基准数据（transport_options）：有则直接作为该方式的结果
        List<TransportOption> dbRows = transportOptionRepository.findByOriginAndDestination(from, to);
        Map<String, TransportOption> dbByMode = new LinkedHashMap<>();
        for (TransportOption t : dbRows) {
            dbByMode.putIfAbsent(t.getMode(), t);
        }

        // 2) 驾车里程：计算类方式的基础（腾讯地图真实里程/时长/过路费）
        DriveLeg drive = driveLeg(from, to);
        if (drive != null) {
            Map<String, Object> route = new LinkedHashMap<>();
            route.put("distanceKm", round1(drive.distanceKm()));
            route.put("driveMinutes", drive.durationMin());
            route.put("toll", drive.toll());
            route.put("source", drive.source());
            out.put("route", route);
            if (!SRC_TMAP.equals(drive.source())) {
                notices.add("腾讯地图接口未返回路线，里程按城市坐标直线距离 × 路网系数" + ROAD_FACTOR + " 估算");
            }
        }

        List<Map<String, Object>> options = new ArrayList<>();
        List<Map<String, Object>> details = new ArrayList<>();

        // 3) 火车：先真抓12306，失败再用里程估算
        Map<String, Object> train = trainOption(from, to, drive, dbByMode.get("TRAIN"), details, notices);
        if (train != null) {
            options.add(train);
        }

        // 4) 航班：按合规约定不抓OTA，用航段里程给出参考价区间
        Map<String, Object> flight = flightOption(from, to, drive, dbByMode.get("FLIGHT"), notices);
        if (flight != null) {
            options.add(flight);
        }

        // 5) 汽车/大巴/拼车/骑行：按腾讯里程计算
        if (drive != null) {
            options.add(dbOr(dbByMode.get("CAR"), computedCar(drive)));
            options.add(dbOr(dbByMode.get("CARPOOL"), computedCarpool(drive)));
            if (drive.distanceKm() > BUS_MAX_KM) {
                notices.add("公路里程约" + Math.round(drive.distanceKm()) + " 公里，超长途大巴现实中基本不运营，未给出参考");
            } else {
                options.add(dbOr(dbByMode.get("BUS"), computedBus(drive)));
            }
            Map<String, Object> bike = dbByMode.get("BIKE") != null ? fromDb(dbByMode.get("BIKE")) : computedBike(drive);
            if (bike != null) {
                options.add(bike);
            }
        }

        // 6) 库里还有别的方式（例如人工录入的其它模式）也一并带上
        Set<String> covered = new HashSet<>();
        for (Map<String, Object> o : options) {
            covered.add(String.valueOf(o.get("mode")));
        }
        for (TransportOption t : dbRows) {
            if (!covered.contains(t.getMode())) {
                options.add(fromDb(t));
            }
        }

        options.sort(Comparator.comparingInt(o -> (int) o.get("duration")));
        out.put("options", options);
        out.put("details", details);
        out.put("notices", notices);
        boolean hasDb = !dbRows.isEmpty();
        out.put("fromDatabase", hasDb);
        out.put("summary", hasDb
                ? "该线路在平台数据库中有基准数据，缺失的方式已用实时采集 / 里程计算补足"
                : "该线路库内无基准数据，以下结果由12306采集与腾讯地图里程计算实时生成");
        return out;
    }


    /**
     * 火车：12306实时车次优先；抓不到则按里程估算，并如实标注来源
     * @param from 出发城市名
     * @param to 到达城市名
     * @param drive 驾车里程数据（本方法未使用）
     * @param dbRow 库内该线路的火车基准记录，可为null
     * @param details 车次明细输出列表，命中实时车次时写入
     * @param notices 提示信息列表，无法给出行程时追加原因
     * @return Map 火车方案，超长途且库内无数据时返回null
     */
    private Map<String, Object> trainOption(String from, String to, DriveLeg drive,
                                            TransportOption dbRow, List<Map<String, Object>> details,
                                            List<String> notices) {
        double straight = straightKm(from, to);
        // 铁路里程：按直线距离×铁路绕行系数换算（公路里程含大量绕行，不适合估算票价）
        double railKm = straight * RAIL_FACTOR;

        int noticeMark = notices.size();
        List<Map<String, Object>> trains = query12306(from, to, notices);
        if (trains.isEmpty() && dbRow != null) {
            // 库内已有基准数据时，不暴露12306抓取失败
            while (notices.size() > noticeMark) {
                notices.remove(notices.size() - 1);
            }
        }
        if (!trains.isEmpty()) {
            details.addAll(trains);
            Map<String, Object> fastest = trains.get(0);
            int minutes = (int) fastest.get("durationMinutes");
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("mode", "TRAIN");
            o.put("duration", minutes);
            o.put("price", String.valueOf((int) Math.round(railKm * TRAIN_FARE_PER_KM)) + " 起");
            o.put("frequency", trains.size() + " 趟车次");
            o.put("comfort", 5);
            o.put("freedom", 2);
            o.put("safety", 5);
            o.put("note", "12306实时车次" + trains.size() + " 趟（点左侧箭头看车次明细）；"
                    +"票价为高铁二等座按" + TRAIN_FARE_PER_KM + " 元/公里估算，实际以12306为准");
            o.put("source", SRC_12306);
            return o;
        }

        if (railKm > 3000) {
            notices.add("铁路里程超过3000公里，长途出行一般需中转，未给出火车参考");
            return dbRow != null ? fromDb(dbRow) : null;
        }
        // 12306没取到实时车次时：库内有整理好的基准数据就先用它，否则按里程估
        if (dbRow != null) {
            return fromDb(dbRow);
        }
        int fastMin = align5(railKm / 230 * 60 + 30);
        int slowMin = (int) Math.round(railKm / 90 * 60);
        int fastPrice = (int) Math.round(railKm * TRAIN_FARE_PER_KM);
        int slowPrice = (int) Math.round(railKm * SLOW_TRAIN_FARE_PER_KM);
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("mode", "TRAIN");
        o.put("duration", fastMin);
        o.put("price", slowPrice + " - " + fastPrice);
        o.put("frequency", "按里程估算");
        o.put("comfort", 4);
        o.put("freedom", 2);
        o.put("safety", 5);
        o.put("note", "铁路里程约" + Math.round(railKm) + " 公里（直线距离 × " + RAIL_FACTOR + "）："
                +"高铁约" + hours(fastMin) + " 约" + fastPrice + " 元（" + TRAIN_FARE_PER_KM + " 元/公里），"
                +"普速约" + hours(slowMin) + " 约" + slowPrice + " 元；均为里程估算，"
                +"12306余票接口现启用加密与登录校验，未取到实时车次");
        o.put("source", SRC_ESTIMATE);
        return o;
    }

    /**
     * 航班：不抓OTA，按航段里程给经济舱参考价区间（里程用直线距离×航线系数）
     * @param from 出发城市名
     * @param to 到达城市名
     * @param drive 驾车里程数据（本方法未使用）
     * @param dbRow 库内该线路的航班基准记录，可为null
     * @param notices 提示信息列表，不给出航班参考时追加原因
     * @return Map 航班方案，距离过近或库内无数据又不估算时返回null
     */
    private Map<String, Object> flightOption(String from, String to, DriveLeg drive, TransportOption dbRow,
                                             List<String> notices) {
        if (dbRow != null) {
            return fromDb(dbRow);
        }
        double airKm = straightKm(from, to) * AIR_FACTOR;
        if (airKm < 600) {
            notices.add("两城直线距离约" + Math.round(airKm) + " 公里，民航一般不设直飞，未给出航班参考");
            return null;
        }
        int airMinutes = (int) Math.round(40 + airKm / 750 * 60);
        int min = (int) Math.round(Math.max(320, airKm * 0.55));
        int max = (int) Math.round(Math.max(500, airKm * 0.95));
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("mode", "FLIGHT");
        o.put("duration", airMinutes);
        o.put("price", min + " - " + max);
        o.put("frequency", "按航段估算");
        o.put("comfort", 4);
        o.put("freedom", 3);
        o.put("safety", 5);
        o.put("note", "航段约" + Math.round(airKm) + " 公里，飞行含起降约" + hours(airMinutes)
                + "（未计往返机场与安检）；航班舱位价格属航司 / OTA 授权数据且有反爬，"
                +"按合规约定不抓取，此处为里程估算区间");
        o.put("source", SRC_ESTIMATE);
        return o;
    }

    /**
     * 自驾（含油费+腾讯过路费，按3人均摊）
     * @param drive 两城驾车里程、时长与过路费
     * @return Map 自驾方案，价格为人均摊后的金额
     */
    private Map<String, Object> computedCar(DriveLeg drive) {
        int total = (int) Math.round(drive.distanceKm() * CAR_FUEL_PER_KM + drive.toll());
        int share = (int) Math.round(total / (double) CAR_SHARE);
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("mode", "CAR");
        o.put("duration", drive.durationMin());
        o.put("price", share + "(均摊)");
        o.put("frequency", "随时出发");
        o.put("comfort", 4);
        o.put("freedom", 5);
        o.put("safety", 4);
        o.put("note", "全程" + round1(drive.distanceKm()) + " 公里 / 驾车约" + hours(drive.durationMin())
                + "；总成本 = 油费" + (int) Math.round(drive.distanceKm() * CAR_FUEL_PER_KM) + " 元（"
                + CAR_FUEL_PER_KM + " 元/公里）+ 过路费" + drive.toll() + " 元"
                + (SRC_TMAP.equals(drive.source()) ? "（腾讯地图实时值）" : "（估算值）")
                + "，按" + CAR_SHARE + " 人均摊；注意轮换驾驶、避免疲劳");
        o.put("source", drive.source());
        return o;
    }

    /**
     * 拼车（同样按油费+过路费，满员4人均摊）
     * @param drive 两城驾车里程、时速与过路费
     * @return Map 拼车方案，价格为人均摊后的金额
     */
    private Map<String, Object> computedCarpool(DriveLeg drive) {
        int total = (int) Math.round(drive.distanceKm() * CAR_FUEL_PER_KM + drive.toll());
        int share = (int) Math.round(total / (double) CARPOOL_SHARE);
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("mode", "CARPOOL");
        o.put("duration", drive.durationMin());
        o.put("price", share + "(均摊)");
        o.put("frequency", "看拼车群");
        o.put("comfort", 3);
        o.put("freedom", 4);
        o.put("safety", 3);
        o.put("note", "拼车多在校园群 / 车站拼，按满员" + CARPOOL_SHARE + " 人均摊油费与过路费；"
                +"务必核对车主身份、上车前把行程分享给同学");
        o.put("source", drive.source());
        return o;
    }

    /**
     * 长途大巴（时间含中途停靠，票价按里程均价）
     * @param drive 两城驾车里程与时长，用于推算班车时间与票价
     * @return Map 大巴方案，含停靠后的总时长与里程票价
     */
    private Map<String, Object> computedBus(DriveLeg drive) {
        int minutes = (int) Math.round(drive.durationMin() * 1.15);
        int price = (int) Math.round(drive.distanceKm() * BUS_FARE_PER_KM);
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("mode", "BUS");
        o.put("duration", minutes);
        o.put("price", String.valueOf(price));
        o.put("frequency", drive.distanceKm() > 800 ? "每日1班" : "每日多班");
        o.put("comfort", 2);
        o.put("freedom", 3);
        o.put("safety", 4);
        o.put("note", "里程" + round1(drive.distanceKm()) + " 公里，含中途停靠约" + hours(minutes)
                + "；票价按" + BUS_FARE_PER_KM + " 元/公里估算，长途班次少，建议提早订票");
        o.put("source", drive.source());
        return o;
    }

    /**
     * 骑行/徒步（超过500公里不建议，返回null由调用方跳过）
     * @param drive 两城驾车里程，用于推算骑行时间
     * @return Map 骑行方案，里程超过500公里时返回null
     */
    private Map<String, Object> computedBike(DriveLeg drive) {
        if (drive.distanceKm() > 500) {
            return null;
        }
        int minutes = (int) Math.round(drive.distanceKm() / 15 * 60);
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("mode", "BIKE");
        o.put("duration", minutes);
        o.put("price", String.valueOf((int) Math.round(drive.distanceKm() * 0.25)));
        o.put("frequency", "随自己节奏");
        o.put("comfort", 1);
        o.put("freedom", 5);
        o.put("safety", 2);
        o.put("note", "里程" + round1(drive.distanceKm()) + " 公里，按均速15公里/小时约" + hours(minutes)
                + "；仅限户外老手并结伴，备齐头盔车灯与补胎工具");
        o.put("source", SRC_ESTIMATE);
        return o;
    }

    /**
     * 库里有就用库里的（保留人工整理的口径），没有就用计算值
     * @param dbRow 库内该方式的基准记录，可为null
     * @param computed 按里程计算出的方案
     * @return Map 优先返回库内方案，库内缺失时返回计算方案
     */
    private Map<String, Object> dbOr(TransportOption dbRow, Map<String, Object> computed) {
        return dbRow != null ? fromDb(dbRow) : computed;
    }

    private Map<String, Object> fromDb(TransportOption t) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("mode", t.getMode());
        o.put("duration", parseInt(t.getDuration(), 0));
        o.put("price", t.getPrice());
        o.put("frequency", t.getFrequency());
        o.put("comfort", t.getComfort());
        o.put("freedom", t.getFreedom());
        o.put("safety", t.getSafety());
        o.put("note", t.getNote());
        o.put("source", SRC_DB);
        return o;
    }


    /**
     * 查询12306实时车次。返回空列表表示未取到（原因写入notices），调用方转里程估算。
     * 说明：12306对余票接口启用了加密响应与登录会话校验，平台按合规约定不绕过，
     * 因此这里可能长期返回空，属于预期内的降级路径。
     * @param from 出发城市名，需能在车站码表中匹配到
     * @param to 到达城市名，需能在车站码表中匹配到
     * @param notices 提示信息列表，未取到车次的原因追加到这里
     * @return List 车次列表，按历时升序，未取到时返回空列表
     */
    private List<Map<String, Object>> query12306(String from, String to, List<String> notices) {
        try {
            Map<String, String> codes = stationCodeMap();
            String fromCode = codes.get(from);
            String toCode = codes.get(to);
            if (fromCode == null || toCode == null) {
                notices.add("12306车站码表中未匹配到「" + (fromCode == null ? from : to) + "」，车次未实时抓取");
                return List.of();
            }
            String date = LocalDate.now().plusDays(7).toString();
            String query = "?leftTicketDTO.train_date=" + date
                    + "&leftTicketDTO.from_station=" + fromCode
                    + "&leftTicketDTO.to_station=" + toCode
                    + "&purpose_codes=ADULT";
            String endpoint = "/otn/leftTicket/query";
            JsonNode root = getJson(RAIL_BASE + endpoint + query, RAIL_BASE + "/otn/leftTicket/init");
            if (root == null) {
                notices.add("12306查询接口不可达，车次未实时抓取");
                return List.of();
            }
            if (root.has("c_url")) {
                String hinted = root.get("c_url").asText();
                JsonNode again = getJson(RAIL_BASE + "/" + hinted + query, RAIL_BASE + "/otn/leftTicket/init");
                if (again != null) {
                    root = again;
                }
            }
            JsonNode result = root.path("data").path("result");
            if (!result.isArray() || result.isEmpty()) {
                notices.add("12306未返回车次数据（接口需登录会话或响应结构变化），车次按里程估算");
                return List.of();
            }
            List<Map<String, Object>> trains = new ArrayList<>();
            int limit = 0;
            for (JsonNode n : result) {
                if (limit++ >= 6) {
                    break;
                }
                String[] f = n.asText().split("\\|");
                // 明文格式：0序号3车次6出发时刻7到达时刻8出发时间9到达时间10历时
                if (f.length < 11 || f[3].isBlank() || f[3].length() > 8) {
                    continue; // 加密载荷长度远超车次名，直接跳过
                }
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("code", f[3]);
                t.put("from", from);
                t.put("to", to);
                t.put("depart", f[8]);
                t.put("arrive", f[9]);
                t.put("duration", f[10]);
                t.put("durationMinutes", parseRailMinutes(f[10]));
                t.put("source", SRC_12306);
                trains.add(t);
            }
            if (trains.isEmpty()) {
                notices.add("12306返回内容为加密载荷（官方反爬），明文车次未取到，车次按里程估算");
            }
            trains.sort(Comparator.comparingInt(t -> (int) t.get("durationMinutes")));
            return trains;
        } catch (Exception e) {
            log.warn("12306车次抓取失败：{}", e.getMessage());
            notices.add("12306车次抓取异常（" + brief(e) + "），车次按里程估算");
            return List.of();
        }
    }

    /** 12306车站码表：@简拼|站名|电报码|拼音|… ，按站名取电报码，带内存缓存 */
    private Map<String, String> stationCodeMap() {
        Map<String, String> cached = stationCodes;
        if (cached != null) {
            return cached;
        }
        try {
            String js = getText(RAIL_BASE + "/otn/resources/js/framework/station_name.js",
                    RAIL_BASE + "/otn/leftTicket/init");
            if (js != null && js.contains("|")) {
                Map<String, String> map = new HashMap<>();
                for (String seg : js.split("@")) {
                    String[] f = seg.split("\\|");
                    if (f.length >= 3 && !f[1].isBlank() && !f[2].isBlank()) {
                        map.putIfAbsent(strip(f[1]), f[2]);
                    }
                }
                if (!map.isEmpty()) {
                    stationCodes = map;
                    return map;
                }
            }
        } catch (Exception e) {
            log.warn("12306车站码表获取失败：{}", e.getMessage());
        }
        return Map.of();
    }

    /**
     * 历时文本如 "05:30" → 分钟
     * @param duration 12306 返回的历时文本，格式为 时:分
     * @return int 换算后的分钟数，解析失败返回0
     */
    private int parseRailMinutes(String duration) {
        String[] p = String.valueOf(duration).split(":");
        try {
            if (p.length == 2) {
                return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
            }
        } catch (NumberFormatException ignored) {
            // 保底：解析失败按0处理，前端仍展示原文
        }
        return 0;
    }


    /**
     * 城市名 → 坐标：内置坐标表优先（不耗配额），未收录时调腾讯地理编码
     * @param city 城市名，可带或不带行政区后缀
     * @return GeoPoint 城市坐标与数据来源，识别不出时抛出BusinessException
     */
    private GeoPoint geo(String city) {
        GeoPoint cached = geoCache.get(city);
        if (cached != null) {
            return cached;
        }
        double[] builtin = CITY_COORDS.get(strip(city));
        if (builtin != null) {
            GeoPoint p = new GeoPoint(builtin[0], builtin[1], SRC_ESTIMATE);
            geoCache.put(city, p);
            return p;
        }
        String[] tries = {city, city +"市", strip(city)};
        for (String address : tries) {
            JsonNode root = getJson(GEO_URL + "?address=" + enc(address) + "&key=" + TENCENT_KEY, null);
            JsonNode loc = root == null ? null : root.path("result").path("location");
            if (loc != null && loc.has("lat") && loc.has("lng")) {
                GeoPoint p = new GeoPoint(loc.path("lat").asDouble(), loc.path("lng").asDouble(), SRC_TMAP);
                geoCache.put(city, p);
                return p;
            }
        }
        throw new BusinessException("没能识别城市「" + city + "」，换个写法试试（如 长沙 / 张家界 / 凤凰古城）");
    }

    /**
     * 两城驾车里程：腾讯真实里程优先，失败降级为直线距离×路网系数
     * @param from 出发城市名
     * @param to 到达城市名
     * @return DriveLeg 驾车里程、时长与过路费，腾讯接口不可用时为估算值
     */
    private DriveLeg driveLeg(String from, String to) {
        String cacheKey = from + "->" + to;
        DriveLeg cached = driveCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        GeoPoint a = geo(from);
        GeoPoint b = geo(to);
        String url = DRIVING_URL + "?from=" + a.lat() + "," + a.lng() + "&to=" + b.lat() + "," + b.lng()
                + "&key=" + TENCENT_KEY;
        JsonNode root = getJson(url, null);
        JsonNode routes = root == null ? null : root.path("result").path("routes");
        if (routes != null && routes.isArray() && !routes.isEmpty()) {
            JsonNode r = routes.get(0);
            double km = r.path("distance").asDouble() / 1000.0;
            int minutes = (int) Math.max(1, Math.round(r.path("duration").asDouble()));
            int toll = (int) Math.round(r.path("toll").asDouble(km * TOLL_PER_KM));
            DriveLeg leg = new DriveLeg(km, minutes, toll, SRC_TMAP);
            driveCache.put(cacheKey, leg);
            return leg;
        }
        // 降级：直线距离×路网系数，过路费按单价估
        double straight = straightKm(a, b);
        double km = straight * ROAD_FACTOR;
        int minutes = (int) Math.max(20, Math.round(km / DRIVE_SPEED_KMH * 60));
        DriveLeg leg = new DriveLeg(km, minutes, (int) Math.round(km * TOLL_PER_KM), SRC_ESTIMATE);
        driveCache.put(cacheKey, leg);
        return leg;
    }

    private double straightKm(String from, String to) {
        return straightKm(geo(from), geo(to));
    }

    private double straightKm(GeoPoint a, GeoPoint b) {
        double earth = 6371.0;
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLng = Math.toRadians(b.lng() - a.lng());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat()))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * earth * Math.asin(Math.sqrt(h));
    }


    private JsonNode getJson(String url, String referer) {
        String body = getText(url, referer);
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            log.warn("响应不是合法 JSON：{}", body.substring(0, Math.min(60, body.length())));
            return null;
        }
    }

    private String getText(String url, String referer) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122.0 Safari/537.36")
                    .GET();
            if (referer != null) {
                b.header("Referer", referer);
            }
            HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                log.warn("请求 {} 返回 {}", url, resp.statusCode());
                return null;
            }
            return resp.body();
        } catch (Exception e) {
            log.warn("请求 {} 失败：{}", url, e.getMessage());
            return null;
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String clean(String s) {
        String v = s == null ? "" : s.trim().replace("市", "");
        return v.isEmpty() ? "" : strip(v);
    }

    /**
     * 去掉行政区后缀，便于「重庆市」与「重庆」命中同一条记录
     * @param s 城市名或车站名
     * @return String 去掉「市/县/区/自治区」等后缀后的名称
     */
    private static String strip(String s) {
        String v = s == null ? "" : s.trim();
        for (String suffix : new String[]{"特别行政区", "自治区", "地区", "自治州", "市", "县", "区"}) {
            if (v.length() > suffix.length() && v.endsWith(suffix)) {
                v = v.substring(0, v.length() - suffix.length());
            }
        }
        return v;
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(String.valueOf(s).replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int align5(double minutes) {
        return (int) Math.max(20, Math.round(minutes / 5.0) * 5);
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    /**
     * 分钟 → "x小时y分"
     * @param minutes 时长，单位为分钟
     * @return String 便于阅读的时长文本
     */
    private static String hours(int minutes) {
        if (minutes < 60) {
            return minutes + " 分钟";
        }
        int h = minutes / 60;
        int m = minutes % 60;
        return m == 0 ? h + " 小时" : h + " 小时" + m + " 分";
    }

    private static String brief(Exception e) {
        String m = e.getMessage();
        if (m == null) {
            return e.getClass().getSimpleName();
        }
        return m.length() > 40 ? m.substring(0, 40) : m;
    }

    private static final Map<String, double[]> CITY_COORDS = new LinkedHashMap<>();

    private static void city(String name, double lat, double lng) {
        CITY_COORDS.put(name, new double[]{lat, lng});
    }

    static {
        city("北京", 39.9042, 116.4074);
        city("上海", 31.2304, 121.4737);
        city("广州", 23.1291, 113.2644);
        city("深圳", 22.5431, 114.0579);
        city("天津", 39.0842, 117.2009);
        city("重庆", 29.5630, 106.5516);
        city("成都", 30.5728, 104.0668);
        city("武汉", 30.5928, 114.3055);
        city("西安", 34.3416, 108.9398);
        city("南京", 32.0603, 118.7969);
        city("杭州", 30.2741, 120.1551);
        city("苏州", 31.2989, 120.5853);
        city("无锡", 31.4912, 120.3119);
        city("宁波", 29.8683, 121.5440);
        city("温州", 27.9938, 120.6994);
        city("长沙", 28.2282, 112.9388);
        city("张家界", 29.1170, 110.4790);
        city("凤凰古城", 27.9480, 109.5996);
        city("湘潭", 27.8296, 112.9445);
        city("株洲", 27.8274, 113.1518);
        city("岳阳", 29.3572, 113.1289);
        city("衡阳", 26.8938, 112.5719);
        city("常德", 29.0316, 111.6987);
        city("怀化", 27.5698, 110.0016);
        city("郑州", 34.7466, 113.6254);
        city("洛阳", 34.6197, 112.4540);
        city("济南", 36.6512, 117.1201);
        city("青岛", 36.0671, 120.3826);
        city("合肥", 31.8206, 117.2272);
        city("福州", 26.0745, 119.2965);
        city("厦门", 24.4798, 118.0894);
        city("泉州", 24.8741, 118.6757);
        city("南昌", 28.6820, 115.8579);
        city("九江", 29.7051, 116.0019);
        city("赣州", 25.8311, 114.9350);
        city("昆明", 24.8801, 102.8329);
        city("贵阳", 26.6470, 106.6302);
        city("南宁", 22.8170, 108.3665);
        city("桂林", 25.2736, 110.2900);
        city("海口", 20.0444, 110.1999);
        city("三亚", 18.2528, 109.5119);
        city("哈尔滨", 45.8038, 126.5350);
        city("长春", 43.8171, 125.3235);
        city("沈阳", 41.8057, 123.4315);
        city("大连", 38.9140, 121.6147);
        city("呼和浩特", 40.8424, 111.7490);
        city("太原", 37.8706, 112.5489);
        city("石家庄", 38.0428, 114.5149);
        city("兰州", 36.0611, 103.8343);
        city("西宁", 36.6171, 101.7782);
        city("银川", 38.4872, 106.2309);
        city("乌鲁木齐", 43.8256, 87.6168);
        city("拉萨", 29.6520, 91.1721);
        city("珠海", 22.2707, 113.5767);
        city("东莞", 23.0207, 113.7518);
        city("佛山", 23.0215, 113.1214);
        city("宜昌", 30.6919, 111.2865);
        city("襄阳", 32.0090, 112.1224);
    }
}
