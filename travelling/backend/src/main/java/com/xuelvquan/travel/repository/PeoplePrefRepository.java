package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.PeoplePref;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PeoplePrefRepository extends JpaRepository<PeoplePref, Long> {

    Optional<PeoplePref> findByUserId(Long userId);
}
