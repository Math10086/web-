package com.xuelvquan.travel.service;

import com.xuelvquan.travel.entity.Spot;
import com.xuelvquan.travel.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 景点库增量补丁（幂等，启动时执行）。
 *
 * 背景：学生用户经常直接点名口语化的地点——"走东方红广场""去岳麓书院"。
 * 大模型能听懂、算法也能算，但<b>景点库里没有这个POI就什么都做不了</b>：
 * 点名胜景点匹配不到 → 选点结果不变 → 地图上的路线看起来"纹丝不动"，
 * 用户就会觉得"AI 不够智能"。
 *
 * 这里按名称判重后补插演示常用的长沙POI，重启不会重复插入，
 * 也给"对话式改路线"提供了可落地的坐标数据。
 */
@Slf4j
@Component
@Order(30)
@RequiredArgsConstructor
public class SpotPatchInitializer implements CommandLineRunner {

    private final SpotRepository spotRepository;

    /** 待补POI（热度刻意控制在78以下，避免把 橘子洲/五一广场 这些顶流挤出默认路线） */
    private record Seed(String name, String city, String category, int heat,
                        double lat, double lng, String description) {}

    private static final List<Seed> SEEDS = List.of(
            new Seed("东方红广场", "长沙", "HISTORY", 78, 28.1783, 112.9414,
                    "岳麓山脚下、湖南大学校园内的毛主席雕像广场，大学城地标，也是登岳麓山南线的经典起点"),
            new Seed("岳麓书院", "长沙", "HISTORY", 76, 28.1838, 112.9388,
                    "中国古代四大书院之一，「惟楚有材，于斯为盛」，就在岳麓山脚"),
            new Seed("湖南大学", "长沙", "HISTORY", 72, 28.1788, 112.9426,
                    "千年学府、没有围墙的大学，校园与岳麓山景区连成一体，可从东方红广场一路逛上山"),
            new Seed("开福寺", "长沙", "HISTORY", 74, 28.2268, 112.9762,
                    "闹市里的千年古刹，香火旺、门票便宜，适合安静地走一走"),
            new Seed("长沙IFS国金中心", "长沙", "TRENDY", 77, 28.1949, 112.9774,
                    "五一商圈地标，顶楼 KAWS 雕塑是长沙最出片的打卡位之一"),
            new Seed("湘江风光带", "长沙", "NATURE", 71, 28.1885, 112.9695,
                    "沿湘江的滨江步道，正对橘子洲，傍晚看江景和焰火最舒服"),
            new Seed("后湖国际艺术园", "长沙", "TRENDY", 68, 28.1706, 112.9353,
                    "岳麓山下的艺术街区，湖景 + 涂鸦墙 + 独立小店，人少好拍"),
            new Seed("谢子龙影像艺术馆", "长沙", "TRENDY", 73, 28.1108, 112.9122,
                    "网红清水混凝土建筑，几何线条极简，拍照出片率极高"));

    @Override
    public void run(String... args) {
        Set<String> existing;
        try {
            existing = spotRepository.findAll().stream()
                    .map(Spot::getName).collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("[学旅圈] 景点库补丁跳过（暂时读不到 spots 表）：{}", e.getMessage());
            return;
        }
        List<String> added = new ArrayList<>();
        for (Seed s : SEEDS) {
            if (existing.contains(s.name())) continue;
            Spot spot = new Spot();
            spot.setName(s.name());
            spot.setCity(s.city());
            spot.setCategory(s.category());
            spot.setHeat(s.heat());
            spot.setLat(s.lat());
            spot.setLng(s.lng());
            spot.setDescription(s.description());
            spotRepository.save(spot);
            added.add(s.name());
        }
        if (!added.isEmpty()) {
            log.info("[学旅圈] 景点库补丁：新增 {} 个 POI → {}", added.size(), added);
        }
    }
}
