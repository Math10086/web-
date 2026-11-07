package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.LocationShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LocationShareRepository extends JpaRepository<LocationShare, Long> {

    List<LocationShare> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<LocationShare> findByTripIdAndUserIdOrderByCreatedAtDesc(Long tripId, Long userId);

    List<LocationShare> findByTripIdOrderByCreatedAtDesc(Long tripId);

    List<LocationShare> findByUserIdAndStatus(Long userId, String status);

    Optional<LocationShare> findByViewToken(String viewToken);
}
