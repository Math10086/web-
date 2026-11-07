package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.TripMatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TripMatchRepository extends JpaRepository<TripMatch, Long> {

    Optional<TripMatch> findByTripIdAndUserId(Long tripId, Long userId);

    List<TripMatch> findByTripId(Long tripId);

    List<TripMatch> findByUserId(Long userId);
}
