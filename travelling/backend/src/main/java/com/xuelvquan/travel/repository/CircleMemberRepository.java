package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.CircleMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface CircleMemberRepository extends JpaRepository<CircleMember, Long> {

    Optional<CircleMember> findByCircleIdAndUserId(Long circleId, Long userId);

    List<CircleMember> findByCircleId(Long circleId);

    List<CircleMember> findByUserId(Long userId);

    long countByCircleId(Long circleId);

    @Transactional
    void deleteByCircleIdAndUserId(Long circleId, Long userId);
}
