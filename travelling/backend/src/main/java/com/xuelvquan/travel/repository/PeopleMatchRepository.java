package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.PeopleMatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PeopleMatchRepository extends JpaRepository<PeopleMatch, Long> {

    Optional<PeopleMatch> findByUserIdAndTargetId(Long userId, Long targetId);

    List<PeopleMatch> findByUserIdOrderByRecommendScoreDesc(Long userId);
}
