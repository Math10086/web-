package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.BusinessException;
import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.entity.Merchant;
import com.xuelvquan.travel.entity.User;
import com.xuelvquan.travel.repository.MerchantRepository;
import com.xuelvquan.travel.repository.UserRepository;
import com.xuelvquan.travel.security.AuthUtil;
import com.xuelvquan.travel.service.NotifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/merchants")
@RequiredArgsConstructor
@Transactional
public class MerchantController {

    /** 商家类型白名单（TRAVEL_AGENCY即旅行社类型） */
    private static final java.util.Set<String> AGENCY_TYPES =
            java.util.Set.of("TRAVEL_AGENCY", "OUTDOOR_SHOP", "HOSTEL", "OTHER");

    private final MerchantRepository merchantRepository;
    private final UserRepository userRepository;
    private final NotifyService notifyService;

    @PostMapping("/apply")
    public Result<Merchant> apply(@RequestBody Map<String, String> body) {
        // 仅非管理员用户可申请入驻
        AuthUtil.requireNonAdmin();
        Long userId = AuthUtil.requireUserId();
        Merchant m = merchantRepository.findByUserId(userId).orElseGet(() -> {
            Merchant x = new Merchant();
            x.setUserId(userId);
            x.setCreatedAt(LocalDateTime.now());
            x.setCredit(100);
            return x;
        });
        m.setShopName(body.get("shopName"));
        m.setQualification(body.get("qualification"));
        m.setDescription(body.get("description"));
        // 商家类型/旅行社资质编号
        String agencyType = body.get("agencyType");
        if (agencyType != null && AGENCY_TYPES.contains(agencyType)) {
            m.setAgencyType(agencyType);
        } else if (agencyType == null) {
            m.setAgencyType("OTHER");
        }
        m.setLicenseNo(body.get("licenseNo"));
        m.setStatus("PENDING");
        if (m.getShopName() == null || m.getShopName().isBlank()) {
            throw new BusinessException("店铺名称不能为空");
        }
        return Result.ok(merchantRepository.save(m));
    }

    @GetMapping
    public Result<List<Merchant>> list(@RequestParam(required = false) String status) {
        if ("all".equalsIgnoreCase(status)) {
            AuthUtil.requireRole("ADMIN");
            return Result.ok(merchantRepository.findAllByOrderByCreatedAtDesc());
        }
        return Result.ok(merchantRepository.findByStatus("APPROVED"));
    }

    @GetMapping("/mine")
    public Result<Merchant> mine() {
        Long userId = AuthUtil.requireUserId();
        return Result.ok(merchantRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("尚未申请入驻商户")));
    }

    @PostMapping("/{id}/audit")
    public Result<Merchant> audit(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        AuthUtil.requireRole("ADMIN");
        Merchant m = merchantRepository.findById(id)
                .orElseThrow(() -> new BusinessException("商户不存在"));
        boolean approve = Boolean.TRUE.equals(body.get("approve"))
                || Boolean.parseBoolean(String.valueOf(body.get("approve")));
        m.setStatus(approve ? "APPROVED" : "REJECTED");
        Merchant saved = merchantRepository.save(m);
        if (approve) {
            User user = userRepository.findById(m.getUserId()).orElse(null);
            if (user != null) {
                user.setRole("MERCHANT");
                userRepository.save(user);
            }
        }
        notifyService.notify(m.getUserId(), "AUDIT",
                approve ? "商户入驻通过" : "商户入驻被驳回",
                "您的店铺「" + m.getShopName() + "」" + (approve ? "已通过审核，可以发布商品" : "未通过审核"),
                m.getId());
        return Result.ok(saved);
    }
}
