package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.ProductOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductOrderRepository extends JpaRepository<ProductOrder, Long> {

    List<ProductOrder> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ProductOrder> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);

    /**
     * 待结算订单：状态达标且尚未结算。
     * settled列是后加的，老数据为NULL，故必须显式容错（`settled <> true`对NULL不成立）。
     */
    @Query("select o from ProductOrder o where o.status = :status and (o.settled is null or o.settled = false)")
    List<ProductOrder> findSettleable(@Param("status") String status);
}
