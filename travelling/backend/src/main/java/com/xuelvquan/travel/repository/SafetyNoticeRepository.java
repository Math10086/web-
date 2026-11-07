package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.SafetyNotice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SafetyNoticeRepository extends JpaRepository<SafetyNotice, Long> {

    List<SafetyNotice> findByTripIdOrderByCreatedAtDesc(Long tripId);

    List<SafetyNotice> findByMemberUserIdOrderByCreatedAtDesc(Long memberUserId);

    long countByTripId(Long tripId);
}
