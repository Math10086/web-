package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.CreditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CreditLogRepository extends JpaRepository<CreditLog, Long> {

    List<CreditLog> findByUserIdOrderByCreatedAtDesc(Long userId);
}
