package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.Footprint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface FootprintRepository extends JpaRepository<Footprint, Long> {

    List<Footprint> findByUserIdOrderByVisitedAtDesc(Long userId);

    /** 批量取多个队员的足迹（队长团队管理页一次拉全队，避免逐人N+1查询） */
    List<Footprint> findByUserIdInOrderByVisitedAtDesc(Collection<Long> userIds);
}
