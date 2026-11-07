package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.TripComplaint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TripComplaintRepository extends JpaRepository<TripComplaint, Long> {

    List<TripComplaint> findByTripIdOrderByCreatedAtDesc(Long tripId);

    List<TripComplaint> findByReporterIdOrderByCreatedAtDesc(Long reporterId);

    List<TripComplaint> findByTargetIdOrderByCreatedAtDesc(Long targetId);

    boolean existsByTripIdAndReporterIdAndTargetId(Long tripId, Long reporterId, Long targetId);

    long countByTargetId(Long targetId);

    long countByTargetIdAndStatus(Long targetId, String status);
}
