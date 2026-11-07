package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.AiUserMemory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiUserMemoryRepository extends JpaRepository<AiUserMemory, Long> {

    Optional<AiUserMemory> findByUserId(Long userId);
}
