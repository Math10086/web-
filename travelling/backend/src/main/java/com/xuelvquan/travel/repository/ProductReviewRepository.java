package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.ProductReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {

    List<ProductReview> findByProductIdOrderByCreatedAtDesc(Long productId);

    List<ProductReview> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);

    List<ProductReview> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<ProductReview> findByOrderIdAndProductId(Long orderId, Long productId);
}
