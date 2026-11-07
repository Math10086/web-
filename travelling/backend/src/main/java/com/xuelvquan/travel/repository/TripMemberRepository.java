package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.TripMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TripMemberRepository extends JpaRepository<TripMember, Long> {

    List<TripMember> findByTripId(Long tripId);

    List<TripMember> findByTripIdAndStatus(Long tripId, String status);

    Optional<TripMember> findByTripIdAndUserId(Long tripId, Long userId);

    List<TripMember> findByUserId(Long userId);

    long countByTripIdAndStatus(Long tripId, String status);

    @Query("select distinct tm.tripId from TripMember tm where tm.userId = :userId")
    List<Long> findTripIdsByUserId(@Param("userId") Long userId);
}
