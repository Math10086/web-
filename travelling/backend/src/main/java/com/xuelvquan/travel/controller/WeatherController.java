package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.service.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 天气接口：目的地天气预报查询，作为动态行程调整的外部数据源
 */
@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;

    @GetMapping
    public Result<Map<String, Object>> forecast(@RequestParam(required = false) String city,
                                                @RequestParam(defaultValue = "3") int days) {
        return Result.ok(weatherService.forecast(city, days));
    }
}
