package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.CrawlBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CrawlBatchRepository extends JpaRepository<CrawlBatch, Long> {

    List<CrawlBatch> findTop20ByOrderByStartedAtDesc();
}
