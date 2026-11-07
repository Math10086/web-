package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findAllByOrderByCreatedAtDesc();

    long countByStatus(String status);
}
