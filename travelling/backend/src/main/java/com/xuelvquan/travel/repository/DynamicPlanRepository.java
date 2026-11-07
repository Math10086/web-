package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.DynamicPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DynamicPlanRepository extends JpaRepository<DynamicPlan, Long> {

    List<DynamicPlan> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<DynamicPlan> findByTripIdOrderByCreatedAtDesc(Long tripId);
}
