package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.GuideService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GuideServiceRepository extends JpaRepository<GuideService, Long> {

    List<GuideService> findByGuideId(Long guideId);

    @Query("""
            select s from GuideService s
            where s.status = 'APPROVED'
              and (:destination is null or s.destination like concat('%', :destination, '%'))
            order by s.createdAt desc
            """)
    List<GuideService> findApproved(@Param("destination") String destination);
}
