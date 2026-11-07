package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.TripPref;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TripPrefRepository extends JpaRepository<TripPref, Long> {

    Optional<TripPref> findByUserId(Long userId);
}
