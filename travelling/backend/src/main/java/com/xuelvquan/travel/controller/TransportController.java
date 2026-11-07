package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.entity.TransportOption;
import com.xuelvquan.travel.repository.TransportOptionRepository;
import com.xuelvquan.travel.security.AuthUtil;
import com.xuelvquan.travel.service.CityTransportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transport")
@RequiredArgsConstructor
public class TransportController {

    private final TransportOptionRepository transportOptionRepository;
    private final CityTransportService cityTransportService;

    /**
     * 出行方式比较。
     * - 同时传origin + destination：返回「出发地 → 目的地」的所有方式
     * - 只传origin：返回该出发地能到的全部方案（前端用来提示可达目的地）
     * - 都不传：兼容旧调用，默认返回去张家界的方案
     * @param origin 出发地，可为空
     * @param destination 目的地，可为空
     * @return Result<List<TransportOption>> 出行方案列表
     */
    @GetMapping
    public Result<List<TransportOption>> list(
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String destination) {
        boolean hasOrigin = origin != null && !origin.isBlank();
        boolean hasDest = destination != null && !destination.isBlank();
        if (hasOrigin && hasDest) {
            return Result.ok(transportOptionRepository.findByOriginAndDestination(origin.trim(), destination.trim()));
        }
        if (hasOrigin) {
            return Result.ok(transportOptionRepository.findByOrigin(origin.trim()));
        }
        if (hasDest) {
            return Result.ok(transportOptionRepository.findByDestination(destination.trim()));
        }
        return Result.ok(transportOptionRepository.findByDestination("张家界"));
    }

    /**
     * 任意两座城市的出行方案：
     * 库内基准数据优先，缺失的方式由12306采集（火车）与腾讯地图里程计算（汽车/大巴/拼车）补足，
     * 每一条都带source字段说明数据来源，前端据此标注"实时抓取/里程计算/库内基准"。
     * @param origin 出发地
     * @param destination 目的地
     * @return Result<Map<String,Object>> 两城之间的出行方案
     */
    @GetMapping("/plan")
    public Result<Map<String, Object>> plan(@RequestParam String origin,
                                            @RequestParam String destination) {
        return Result.ok(cityTransportService.plan(origin, destination));
    }

    /** 可选出发地/目的地（按当前数据动态返回，前端下拉框用） */
    @GetMapping("/cities")
    public Result<Map<String, Object>> cities() {
        Map<String, Object> m = new LinkedHashMap<>();
        // 库内已有的城市+内置城市坐标表，保证任意常见城市都能选（下拉仍可手动输入其它城市）
        java.util.LinkedHashSet<String> origins = new java.util.LinkedHashSet<>(transportOptionRepository.findDistinctOrigins());
        java.util.LinkedHashSet<String> destinations = new java.util.LinkedHashSet<>(transportOptionRepository.findDistinctDestinations());
        origins.addAll(cityTransportService.knownCities());
        destinations.addAll(cityTransportService.knownCities());
        m.put("origins", List.copyOf(origins));
        m.put("destinations", List.copyOf(destinations));
        return Result.ok(m);
    }

    @PostMapping
    public Result<TransportOption> create(@RequestBody TransportOption option) {
        AuthUtil.requireRole("ADMIN");
        return Result.ok(transportOptionRepository.save(option));
    }
}
