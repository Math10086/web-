package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.Ad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AdRepository extends JpaRepository<Ad, Long> {

    @Query("""
            select a from Ad a
            where a.status = 'APPROVED'
              and a.slot = :slot
              and :now >= a.startAt and :now <= a.endAt
            order by a.createdAt desc
            """)
    List<Ad> findActive(@Param("slot") String slot, @Param("now") LocalDateTime now);

    List<Ad> findAllByOrderByCreatedAtDesc();

    List<Ad> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);

    /** 历史数据兜底：早期广告没有merchantId，只能按投放主体匹配 */
    List<Ad> findByAdvertiserOrderByCreatedAtDesc(String advertiser);
}
