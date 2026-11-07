package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.entity.InsurancePolicy;
import com.xuelvquan.travel.repository.InsurancePolicyRepository;
import com.xuelvquan.travel.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/api/insurance")
@RequiredArgsConstructor
public class InsuranceController {

    private final InsurancePolicyRepository repository;

    @PostMapping("/purchase")
    public Result<InsurancePolicy> purchase(@RequestBody Map<String, Object> body) {
        Long userId = AuthUtil.requireUserId();
        InsurancePolicy p = new InsurancePolicy();
        p.setUserId(userId);
        p.setTripId(body.get("tripId") == null ? null : Long.valueOf(String.valueOf(body.get("tripId"))));
        p.setPlanName(String.valueOf(body.getOrDefault("planName", "学旅出行意外险")));
        p.setPolicyNo("XLV" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(100, 1000));
        p.setStatus("EFFECTIVE");
        p.setCoverageEnd(LocalDate.now().plusDays(30));
        p.setCreatedAt(LocalDateTime.now());
        return Result.ok(repository.save(p));
    }

    @GetMapping("/mine")
    public Result<List<InsurancePolicy>> mine() {
        Long userId = AuthUtil.requireUserId();
        return Result.ok(repository.findByUserIdOrderByCreatedAtDesc(userId));
    }
}
