package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.InsurancePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InsurancePolicyRepository extends JpaRepository<InsurancePolicy, Long> {

    List<InsurancePolicy> findByUserIdOrderByCreatedAtDesc(Long userId);
}
