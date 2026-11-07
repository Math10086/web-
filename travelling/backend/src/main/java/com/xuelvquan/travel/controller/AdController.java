package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.BusinessException;
import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.entity.Ad;
import com.xuelvquan.travel.entity.Merchant;
import com.xuelvquan.travel.repository.AdRepository;
import com.xuelvquan.travel.repository.MerchantRepository;
import com.xuelvquan.travel.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ads")
@RequiredArgsConstructor
public class AdController {

    private final AdRepository adRepository;
    private final MerchantRepository merchantRepository;

    @GetMapping
    public Result<List<Ad>> active(@RequestParam String slot) {
        return Result.ok(adRepository.findActive(slot, LocalDateTime.now()));
    }

    /** 商家投放中心：我的广告位与曝光/点击数据 */
    @GetMapping("/mine")
    public Result<List<Ad>> mine() {
        AuthUtil.requireMerchant();
        Long userId = AuthUtil.requireUserId();
        Merchant shop = merchantRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("尚未开通店铺"));
        // 历史广告数据merchantId为空，按投放主体（店铺名）兜底匹配
        Map<Long, Ad> merged = new java.util.LinkedHashMap<>();
        for (Ad a : adRepository.findByMerchantIdOrderByCreatedAtDesc(shop.getId())) {
            merged.put(a.getId(), a);
        }
        if (shop.getShopName() != null) {
            for (Ad a : adRepository.findByAdvertiserOrderByCreatedAtDesc(shop.getShopName())) {
                merged.putIfAbsent(a.getId(), a);
            }
        }
        List<Ad> list = new java.util.ArrayList<>(merged.values());
        list.sort(java.util.Comparator.comparing(Ad::getCreatedAt,
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        return Result.ok(list);
    }

    @GetMapping("/admin")
    public Result<List<Ad>> adminList() {
        AuthUtil.requireRole("ADMIN");
        return Result.ok(adRepository.findAllByOrderByCreatedAtDesc());
    }

    @PostMapping
    public Result<Ad> create(@RequestBody Map<String, Object> body) {
        // 广告投放属商家经营能力：管理员只做广告审核，不参与投放
        AuthUtil.requireMerchant();
        Long userId = AuthUtil.requireUserId();
        Merchant shop = merchantRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("尚未开通店铺"));
        if (!"APPROVED".equals(shop.getStatus())) {
            throw new BusinessException("店铺尚未通过审核，暂不可投放广告");
        }
        Ad ad = new Ad();
        ad.setSlot((String) body.get("slot"));
        ad.setTitle((String) body.get("title"));
        ad.setImage((String) body.get("image"));
        ad.setLink((String) body.get("link"));
        // 投放主体固定为当前店铺
        ad.setMerchantId(shop.getId());
        ad.setAdvertiser(shop.getShopName());
        ad.setTargetCity((String) body.get("targetCity"));
        ad.setStatus("PENDING");
        ad.setPrice(body.get("price") == null ? 0.0 : Double.valueOf(String.valueOf(body.get("price"))));
        ad.setViewCount(0);
        ad.setClickCount(0);
        ad.setStartAt(parseDateTime(body.get("startAt")));
        ad.setEndAt(parseDateTime(body.get("endAt")));
        ad.setCreatedAt(LocalDateTime.now());
        if (ad.getSlot() == null || ad.getTitle() == null) {
            throw new BusinessException("广告位与标题不能为空");
        }
        return Result.ok(adRepository.save(ad));
    }

    @PostMapping("/{id}/audit")
    public Result<Ad> audit(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        AuthUtil.requireRole("ADMIN");
        Ad ad = adRepository.findById(id).orElseThrow(() -> new BusinessException("广告不存在"));
        boolean approve = Boolean.TRUE.equals(body.get("approve"))
                || Boolean.parseBoolean(String.valueOf(body.get("approve")));
        ad.setStatus(approve ? "APPROVED" : "REJECTED");
        return Result.ok(adRepository.save(ad));
    }

    @PostMapping("/{id}/view")
    public Result<Ad> view(@PathVariable Long id) {
        Ad ad = adRepository.findById(id).orElseThrow(() -> new BusinessException("广告不存在"));
        ad.setViewCount(nz(ad.getViewCount()) + 1);
        return Result.ok(adRepository.save(ad));
    }

    @PostMapping("/{id}/click")
    public Result<Ad> click(@PathVariable Long id) {
        Ad ad = adRepository.findById(id).orElseThrow(() -> new BusinessException("广告不存在"));
        ad.setClickCount(nz(ad.getClickCount()) + 1);
        return Result.ok(adRepository.save(ad));
    }

    private LocalDateTime parseDateTime(Object o) {
        if (o == null) return null;
        return LocalDateTime.parse(String.valueOf(o).replace(" ", "T"));
    }

    private int nz(Integer i) {
        return i == null ? 0 : i;
    }
}
