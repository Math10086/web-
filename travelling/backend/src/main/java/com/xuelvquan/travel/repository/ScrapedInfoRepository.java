package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.ScrapedInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScrapedInfoRepository extends JpaRepository<ScrapedInfo, Long> {

    List<ScrapedInfo> findByTypeOrderByPriceMinAsc(String type);

    List<ScrapedInfo> findByTypeAndDestinationOrderByPriceMinAsc(String type, String destination);

    List<ScrapedInfo> findByDestinationOrderByTypeAscPriceMinAsc(String destination);

    List<ScrapedInfo> findByTypeInOrderByPriceMinAsc(List<String> types);

    /**
     * 采集upsert用的业务主键查询（出发地非空，即车次/航班）。
     *
     * 注意不能写成带null参数的单条派生查询：SQL中`origin = null`恒为假，
     * 而酒店/门票没有出发地，必须拆成OriginIsNull分支，否则每次采集都会重复插入。
     */
    Optional<ScrapedInfo> findByTypeAndOriginAndDestinationAndName(
            String type, String origin, String destination, String name);

    /** 采集upsert的业务主键查询（出发地为空，即酒店/门票/目的地参考） */
    Optional<ScrapedInfo> findByTypeAndOriginIsNullAndDestinationAndName(
            String type, String destination, String name);

    List<ScrapedInfo> findByTypeAndOriginOrderByPriceMinAsc(String type, String origin);
}
