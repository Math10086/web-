package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.BusinessException;
import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.entity.Route;
import com.xuelvquan.travel.entity.Spot;
import com.xuelvquan.travel.repository.RouteRepository;
import com.xuelvquan.travel.repository.SpotRepository;
import com.xuelvquan.travel.security.AuthUtil;
import com.xuelvquan.travel.service.RoutePlannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RoutePlannerService routePlannerService;
    private final RouteRepository routeRepository;
    private final SpotRepository spotRepository;

    /**
     * 路线规划（不保存）
     * @param body 请求体，含city、mode、spotIds、startMinutes、startSpotId、days、customSpots
     * @return Result<Map<String,Object>> 规划后的行程
     */
    @PostMapping("/plan")
    public Result<Map<String, Object>> plan(@RequestBody Map<String, Object> body) {
        // 路线规划仅学生用户、博主、导游可用
        AuthUtil.requireBusiness();
        String city = (String) body.get("city");
        String mode = (String) body.get("mode");
        @SuppressWarnings("unchecked")
        List<Long> spotIds = body.get("spotIds") instanceof List<?> list
                ? list.stream().map(o -> Long.valueOf(String.valueOf(o))).toList()
                : null;
        Integer startMinutes = body.get("startMinutes") == null
                ? null
                : Integer.valueOf(String.valueOf(body.get("startMinutes")));
        Long startSpotId = body.get("startSpotId") == null || "".equals(body.get("startSpotId"))
                ? null
                : Long.valueOf(String.valueOf(body.get("startSpotId")));
        // 行程天数：不传或1为单日，2~7返回按天分组的行程
        Integer days = body.get("days") == null || "".equals(body.get("days"))
                ? null
                : Integer.valueOf(String.valueOf(body.get("days")));
        // 「自定义添加想去的地点」：前端传 [{name,lng,lat}]，转成临时景点参与规划（不入库）
        List<Spot> customSpots = buildCustomSpots(body.get("customSpots"), city);
        return Result.ok(routePlannerService.plan(city, mode, spotIds, startMinutes, startSpotId, days, customSpots));
    }

    /**
     * 把前端传来的自定义地点转成「临时景点」（不入库）。
     * id用负数，避免与景点库自增主键冲突，同时让前端能稳定做列表key与去重；
     * 没有经纬度的点无法参与路线计算，直接忽略。
     * @param raw 前端传来的自定义地点数组，每项含name、lng、lat
     * @param city 所属城市
     * @return List<Spot> 临时景点列表，没有有效地点时返回null
     */
    private List<Spot> buildCustomSpots(Object raw, String city) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        List<Spot> out = new ArrayList<>();
        int seq = 0;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            String name = m.get("name") == null ? null : String.valueOf(m.get("name")).trim();
            Double lng = toDouble(m.get("lng"));
            Double lat = toDouble(m.get("lat"));
            if (name == null || name.isEmpty() || lng == null || lat == null) {
                continue;
            }
            Spot s = new Spot();
            s.setId(-(long) (++seq));
            s.setName(name);
            s.setCity(city);
            s.setCategory("CUSTOM");
            s.setLng(lng);
            s.setLat(lat);
            s.setHeat(0);
            s.setDescription("用户自定义地点");
            out.add(s);
        }
        return out.isEmpty() ? null : out;
    }

    private Double toDouble(Object o) {
        if (o == null || "".equals(o)) {
            return null;
        }
        try {
            return Double.valueOf(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 保存路线
     * @param body 请求体，含city、mode、name、spotIds、riskLevel、tips等
     * @return Result<Route> 保存后的路线
     */
    @PostMapping
    public Result<Route> save(@RequestBody Map<String, Object> body) {
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        String city = (String) body.get("city");
        String mode = (String) body.get("mode");
        if (city == null || mode == null) {
            throw new BusinessException("城市和路线模式不能为空");
        }
        Route r = new Route();
        r.setAuthorId(userId);
        r.setName((String) body.getOrDefault("name", city +"路线"));
        r.setCity(city);
        r.setMode(mode);
        r.setColor(RoutePlannerService.COLORS.getOrDefault(mode, "#2F80ED"));
        Object spotIdsObj = body.get("spotIds");
        if (spotIdsObj instanceof List<?> list) {
            r.setSpotIdsCsv(list.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse(""));
        } else {
            r.setSpotIdsCsv((String) body.get("spotIdsCsv"));
        }
        if (body.get("totalDistance") != null) {
            r.setTotalDistance(Double.valueOf(String.valueOf(body.get("totalDistance"))));
        }
        if (body.get("totalDuration") != null) {
            r.setTotalDuration(Integer.valueOf(String.valueOf(body.get("totalDuration"))));
        }
        r.setRiskLevel((String) body.get("riskLevel"));
        r.setTips((String) body.get("tips"));
        r.setCreatedAt(LocalDateTime.now());
        return Result.ok(routeRepository.save(r));
    }

    @GetMapping
    public Result<List<Route>> list(@RequestParam(required = false) String city,
                                    @RequestParam(required = false) String mode,
                                    @RequestParam(required = false) Long authorId) {
        return Result.ok(routeRepository.search(blank(city), blank(mode), authorId));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Route r = routeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("路线不存在"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("route", r);
        List<Spot> spots = new ArrayList<>();
        if (r.getSpotIdsCsv() != null && !r.getSpotIdsCsv().isBlank()) {
            Map<Long, Spot> indexed = new HashMap<>();
            for (String s : r.getSpotIdsCsv().split(",")) {
                spotRepository.findById(Long.valueOf(s.trim())).ifPresent(x -> indexed.put(x.getId(), x));
            }
            for (String s : r.getSpotIdsCsv().split(",")) {
                Spot x = indexed.get(Long.valueOf(s.trim()));
                if (x != null) {
                    spots.add(x);
                }
            }
        }
        m.put("spots", spots);
        return Result.ok(m);
    }

    private String blank(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
