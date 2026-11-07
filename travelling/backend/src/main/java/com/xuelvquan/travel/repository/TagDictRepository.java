package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.TagDict;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TagDictRepository extends JpaRepository<TagDict, Long> {

    List<TagDict> findByActiveTrueOrderBySortNoAscIdAsc();

    List<TagDict> findByScopeAndActiveTrueOrderBySortNoAscIdAsc(String scope);

    List<TagDict> findByDimensionAndActiveTrueOrderBySortNoAscIdAsc(String dimension);

    /** 按scope + dimension取维度下的全部选项（攻略标签/向导标签校验用） */
    List<TagDict> findByScopeAndDimensionAndActiveTrueOrderBySortNoAscIdAsc(String scope, String dimension);

    long countByActiveTrue();

    Optional<TagDict> findByScopeAndDimensionAndCode(String scope, String dimension, String code);
}
