package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.GuideProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GuideProfileRepository extends JpaRepository<GuideProfile, Long> {

    Optional<GuideProfile> findByUserId(Long userId);

    List<GuideProfile> findByStatus(String status);

    List<GuideProfile> findAllByOrderByCreatedAtDesc();
}
