package com.xuelvquan.travel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xuelvquan.travel.entity.Spot;
import com.xuelvquan.travel.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 地点搜索（地理编码）服务：把「橘子洲」这样的地名变成经纬度坐标。
 *
 * 数据源：腾讯位置服务WebServiceAPI地点搜索（与路线规划共用同一个key）。
 * key当日配额用光/网络异常时，自动降级用本地景点库模糊匹配兜底，页面不报错。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeoService {

    /** 腾讯位置服务WebServiceAPI key（可用配置xlvq.tmap-key或环境变量XLVQ_TMAP_KEY覆盖） */
    @Value("${xlvq.tmap-key:BXNBZ-IUGOT-EAEXY-L22DC-BXEZE-Y6F7J}")
    private String tencentKey;

    /** 接口域名（可覆盖，便于本地联调/换代理） */
    @Value("${xlvq.tmap-base:https://apis.map.qq.com}")
    private String tencentBase;

    private final SpotRepository spotRepository;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 按关键词搜索地点，返回 {source: 'poi'|'spot'|'none', reason, list: [{title, address, province, city, district, lng, lat}]}
     *
     * @param keyword 地点名（必填）
     * @param city    限定城市（可空，空则全国范围）
     * @return Map 搜索结果，含来源标记source、兜底原因reason与候选列表list
     */
    public Map<String, Object> search(String keyword, String city) {
        PoiResult poi = searchPoi(keyword, city);
        Map<String, Object> result = new LinkedHashMap<>();
        if (!poi.list.isEmpty()) {
            result.put("source", "poi");
            result.put("reason", null);
            result.put("list", poi.list);
            return result;
        }
        // 腾讯接口配额用光/无结果/调用失败时，用景点库模糊匹配兜底
        List<Map<String, Object>> libList = searchSpotLib(keyword);
        result.put("source", libList.isEmpty() ? "none" : "spot");
        result.put("reason", poi.failReason);
        result.put("list", libList);
        return result;
    }

    /** 腾讯POI搜索结果：list为空时failReason说明原因（quota/error/timeout/empty） */
    private static final class PoiResult {
        private List<Map<String, Object>> list = new ArrayList<>();
        private String failReason;
    }

    /**
     * 调腾讯地点搜索，异常或status!=0返回空结果（不抛错，由调用方兜底）
     * @param keyword 地点名
     * @param city 限定城市，为空则不限城市
     * @return PoiResult 命中的候选地点；失败时列表为空并带上失败原因
     */
    private PoiResult searchPoi(String keyword, String city) {
        PoiResult out = new PoiResult();
        try {
            String boundary = "region(" + (city == null || city.isBlank() ? "全国" : city) + ",0)";
            String url = tencentBase + "/ws/place/v1/search"
                    + "?keyword=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
                    + "&boundary=" + URLEncoder.encode(boundary, StandardCharsets.UTF_8)
                    + "&page_size=8&page_index=1"
                    + "&key=" + URLEncoder.encode(tencentKey, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = MAPPER.readTree(resp.body());
            int status = root.path("status").asInt(-1);
            if (status != 0) {
                // 121=此key当日调用量已达上限；110/111多为key校验失败
                out.failReason = status == 121 ? "quota" : "error";
                log.warn("地点搜索未成功：status={}，msg={}（将走景点库兜底）",
                        status, root.path("message").asText());
                return out;
            }
            for (JsonNode d : root.path("data")) {
                // 注意：province/city/district在ad_info里（顶层没有），个别接口版本会平铺
                JsonNode ad = d.path("ad_info");
                String province = ad.path("province").asText(d.path("province").asText(""));
                String dCity = ad.path("city").asText(d.path("city").asText(""));
                String district = ad.path("district").asText(d.path("district").asText(""));
                // 城市名去掉“市/自治州”等后缀，贴合页面「城市」字段习惯
                dCity = dCity.replaceAll("(市|地区|自治州|盟)$", "");
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("title", d.path("title").asText());
                item.put("address", d.path("address").asText());
                item.put("province", province);
                item.put("city", dCity);
                item.put("district", district);
                item.put("lng", d.path("location").path("lng").asDouble());
                item.put("lat", d.path("location").path("lat").asDouble());
                out.list.add(item);
            }
            if (out.list.isEmpty()) out.failReason = "empty";
        } catch (java.net.http.HttpTimeoutException e) {
            out.failReason = "timeout";
            log.warn("地点搜索超时，将走景点库兜底");
        } catch (Exception e) {
            out.failReason = "error";
            log.warn("地点搜索调用失败，将走景点库兜底：{}", e.getMessage());
        }
        return out;
    }

    /** 常见别名 → 景点库规范名：腾讯配额用尽走兜底时，先按别名归一再模糊匹配（如旧称/俗称） */
    private static final Map<String, String> SPOT_ALIASES = Map.of(
            "湖南省博物馆", "湖南博物院",
            "湖南省博", "湖南博物院",
            "省博物馆", "湖南博物院"
    );

    /**
     * 景点库模糊匹配兜底：返回与腾讯POI相同结构的候选
     * @param keyword 地点名，会先按别名归一
     * @return List 匹配到的景点候选，无坐标的景点会被跳过
     */
    private List<Map<String, Object>> searchSpotLib(String keyword) {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            String kw = SPOT_ALIASES.getOrDefault(keyword, keyword);
            List<Spot> spots = spotRepository.search(null, null, kw);
            if (spots.isEmpty() && !kw.equals(keyword)) {
                // 别名没命中再退回原词试一次
                spots = spotRepository.search(null, null, keyword);
            }
            for (Spot s : spots) {
                if (s.getLng() == null || s.getLat() == null) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("title", s.getName());
                item.put("address", s.getName());
                item.put("province", "");
                item.put("city", s.getCity() == null ? "" : s.getCity());
                item.put("district", "");
                item.put("lng", s.getLng());
                item.put("lat", s.getLat());
                list.add(item);
            }
        } catch (Exception ignored) {
        }
        return list;
    }
}
