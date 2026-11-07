package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.BusinessException;
import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.entity.Footprint;
import com.xuelvquan.travel.entity.Spot;
import com.xuelvquan.travel.repository.FootprintRepository;
import com.xuelvquan.travel.repository.SpotRepository;
import com.xuelvquan.travel.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/footprints")
@RequiredArgsConstructor
public class FootprintController {

    private final FootprintRepository footprintRepository;
    private final SpotRepository spotRepository;

    @GetMapping("/mine")
    public Result<List<Footprint>> mine() {
        // 「我的足迹」属学生用户/博主/导游能力，商家与管理员不参与
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        return Result.ok(footprintRepository.findByUserIdOrderByVisitedAtDesc(userId));
    }

    @GetMapping("/user/{userId}")
    public Result<List<Footprint>> userFootprints(@PathVariable Long userId) {
        return Result.ok(footprintRepository.findByUserIdOrderByVisitedAtDesc(userId));
    }

    @PostMapping
    public Result<Footprint> create(@RequestBody Map<String, Object> body) {
        // 仅前台业务用户可添加足迹
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        Footprint f = new Footprint();
        f.setUserId(userId);

        Long spotId = body.get("spotId") == null ? null : Long.valueOf(String.valueOf(body.get("spotId")));
        f.setSpotId(spotId);
        if (spotId != null) {
            Spot spot = spotRepository.findById(spotId)
                    .orElseThrow(() -> new BusinessException("关联景点不存在"));
            f.setName(spot.getName());
            f.setCity(spot.getCity());
            f.setLng(spot.getLng());
            f.setLat(spot.getLat());
        }
        if (body.get("name") != null) f.setName((String) body.get("name"));
        if (body.get("city") != null) f.setCity((String) body.get("city"));
        if (body.get("lng") != null) f.setLng(Double.valueOf(String.valueOf(body.get("lng"))));
        if (body.get("lat") != null) f.setLat(Double.valueOf(String.valueOf(body.get("lat"))));
        if (body.get("photo") != null) f.setPhoto((String) body.get("photo"));
        if (body.get("note") != null) f.setNote((String) body.get("note"));

        // visitedAt兼容多种前端格式：2026-10-08/2026-10-08T00:00:00/ISO带Z，
        // 实体字段是LocalDate（数据库date列），只取前10位的日期部分
        String visited = body.get("visitedAt") == null ? null : String.valueOf(body.get("visitedAt")).trim();
        if (visited == null || visited.isBlank()) {
            f.setVisitedAt(LocalDate.now());
        } else {
            f.setVisitedAt(LocalDate.parse(visited.length() > 10 ? visited.substring(0, 10) : visited));
        }
        f.setCreatedAt(LocalDateTime.now());
        if (f.getName() == null || f.getName().isBlank()) {
            throw new BusinessException("足迹名称不能为空");
        }
        return Result.ok(footprintRepository.save(f));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = AuthUtil.requireUserId();
        Footprint f = footprintRepository.findById(id)
                .orElseThrow(() -> new BusinessException("足迹不存在"));
        if (!f.getUserId().equals(userId)) {
            throw new BusinessException(403, "只能删除自己的足迹");
        }
        footprintRepository.delete(f);
        return Result.ok();
    }
}
