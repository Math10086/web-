package com.xuelvquan.travel.service;

import com.xuelvquan.travel.entity.GuideProfile;
import com.xuelvquan.travel.entity.Merchant;
import com.xuelvquan.travel.repository.GuideProfileRepository;
import com.xuelvquan.travel.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * B10三层供给：把老库里的导游档案/商家数据补齐层级标识。
 *
 * Hibernate ddl-auto=update新增的列在老数据行上是NULL ——
 * 这里做一次性幂等回填：
 * 
 *   - guide_profiles.supply_tier：已认证(APPROVED) → 平台认证导游；其余 → 学生导游；
 *   - merchants.agency_type：留空的一律按「户外装备店」归类（演示商家主营装备）。
 * 
 */
@Slf4j
@Component
@Order(6)
@RequiredArgsConstructor
public class SupplyTierInitializer implements CommandLineRunner {

    private final GuideProfileRepository profileRepository;
    private final MerchantRepository merchantRepository;

    @Override
    public void run(String... args) {
        int guides = 0;
        for (GuideProfile p : profileRepository.findAll()) {
            if (p.getSupplyTier() == null || p.getSupplyTier().isBlank()) {
                p.setSupplyTier("APPROVED".equals(p.getStatus()) ? "CERTIFIED_GUIDE" : "STUDENT_GUIDE");
                profileRepository.save(p);
                guides++;
            }
        }
        int merchants = 0;
        for (Merchant m : merchantRepository.findAll()) {
            if (m.getAgencyType() == null || m.getAgencyType().isBlank()) {
                m.setAgencyType("OUTDOOR_SHOP");
                merchantRepository.save(m);
                merchants++;
            }
        }
        if (guides > 0 || merchants > 0) {
            log.info("[B10] 三层供给层级回填完成：导游档案 {} 条 / 商家 {} 条", guides, merchants);
        }
    }
}
