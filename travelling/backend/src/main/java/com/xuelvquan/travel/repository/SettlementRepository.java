package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    List<Settlement> findAllByOrderByCreatedAtDesc();
}
