package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.GuideBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GuideBookingRepository extends JpaRepository<GuideBooking, Long> {

    List<GuideBooking> findByTravelerIdOrderByCreatedAtDesc(Long travelerId);

    List<GuideBooking> findByGuideIdOrderByCreatedAtDesc(Long guideId);

    /** 待向导游结算的订单：已完成（SETTLED）且尚未打款（settled为NULL视为未结算） */
    @Query("select b from GuideBooking b where b.status = :status and (b.settled is null or b.settled = false)")
    List<GuideBooking> findSettleable(@Param("status") String status);
}
