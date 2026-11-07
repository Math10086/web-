package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.Interaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface InteractionRepository extends JpaRepository<Interaction, Long> {

    /** 按类型取全部互动（热度合成用：一次拉完FAVORITE，避免逐帖查询） */
    List<Interaction> findByType(String type);

    List<Interaction> findByTargetIdAndType(Long targetId, String type);

    Optional<Interaction> findByUserIdAndTargetIdAndType(Long userId, Long targetId, String type);

    long countByTargetIdAndType(Long targetId, String type);

    @Transactional
    void deleteByTargetId(Long targetId);
}
