package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.BusinessException;
import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.entity.Spot;
import com.xuelvquan.travel.repository.SpotRepository;
import com.xuelvquan.travel.security.AuthUtil;
import com.xuelvquan.travel.service.SpotHeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/spots")
@RequiredArgsConstructor
public class SpotController {

    private final SpotRepository spotRepository;
    private final SpotHeatService spotHeatService;

    @GetMapping
    public Result<List<Spot>> list(@RequestParam(required = false) String category,
                                   @RequestParam(required = false) String city,
                                   @RequestParam(required = false) String keyword) {
        return Result.ok(spotHeatService.decorate(
                spotRepository.search(blank(category), blank(city), blank(keyword))));
    }

    /** 热度口径说明（权重/衰减/有行为数据的景点数），前端展示在图表说明里 */
    @GetMapping("/heat-formula")
    public Result<Map<String, Object>> heatFormula() {
        return Result.ok(spotHeatService.formula());
    }

    @GetMapping("/cities")
    public Result<List<String>> cities() {
        return Result.ok(spotRepository.findCities());
    }

    @GetMapping("/{id}")
    public Result<Spot> detail(@PathVariable Long id) {
        return Result.ok(spotHeatService.decorate(spotRepository.findById(id)
                .orElseThrow(() -> new BusinessException("景点不存在"))));
    }

    @PostMapping
    public Result<Spot> create(@RequestBody Spot spot) {
        AuthUtil.requireRole("ADMIN");
        if (spot.getHeat() == null) {
            spot.setHeat(0);
        }
        Spot saved = spotRepository.save(spot);
        spotHeatService.invalidate();
        return Result.ok(saved);
    }

    private String blank(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
