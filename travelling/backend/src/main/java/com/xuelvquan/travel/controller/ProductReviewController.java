package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.BusinessException;
import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.entity.Merchant;
import com.xuelvquan.travel.entity.ProductOrder;
import com.xuelvquan.travel.entity.ProductReview;
import com.xuelvquan.travel.entity.User;
import com.xuelvquan.travel.repository.MerchantRepository;
import com.xuelvquan.travel.repository.ProductOrderRepository;
import com.xuelvquan.travel.repository.ProductReviewRepository;
import com.xuelvquan.travel.repository.UserRepository;
import com.xuelvquan.travel.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品评价：买家评价（前台使用功能）+ 商家评价管理（回复）。
 * 管理员不参与评价与回复。
 */
@RestController
@RequestMapping("/api/product-reviews")
@RequiredArgsConstructor
public class ProductReviewController {

    private final ProductReviewRepository reviewRepository;
    private final ProductOrderRepository orderRepository;
    private final MerchantRepository merchantRepository;
    private final UserRepository userRepository;

    /**
     * 某商品的公开评价列表
     * @param productId 商品ID
     * @return Result<List<ProductReview>> 评价列表，按时间倒序
     */
    @GetMapping
    public Result<List<ProductReview>> list(@RequestParam Long productId) {
        return Result.ok(reviewRepository.findByProductIdOrderByCreatedAtDesc(productId));
    }

    /** 我发表过的评价（用于订单页判断哪些商品已评价） */
    @GetMapping("/mine")
    public Result<List<ProductReview>> mine() {
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        return Result.ok(reviewRepository.findByUserIdOrderByCreatedAtDesc(userId));
    }

    /** 商家：本店收到的评价（评价管理） */
    @GetMapping("/merchant")
    public Result<Map<String, Object>> merchantReviews() {
        AuthUtil.requireMerchant();
        Long userId = AuthUtil.requireUserId();
        Merchant shop = merchantRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("尚未开通店铺"));
        List<ProductReview> list = reviewRepository.findByMerchantIdOrderByCreatedAtDesc(shop.getId());
        double avg = list.stream().mapToInt(r -> r.getRating() == null ? 0 : r.getRating()).average().orElse(0);
        long replied = list.stream().filter(r -> r.getReply() != null && !r.getReply().isBlank()).count();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("list", list);
        m.put("total", list.size());
        m.put("avgRating", Math.round(avg * 10) / 10.0);
        m.put("repliedCount", replied);
        m.put("pendingReply", list.size() - replied);
        return Result.ok(m);
    }

    /**
     * 买家提交评价：需订单已完成且属于本人，同一订单同一商品只能评价一次
     * @param body 请求体，含orderId、productId、rating、content、productName
     * @return Result<ProductReview> 新建的评价
     */
    @PostMapping
    public Result<ProductReview> create(@RequestBody Map<String, Object> body) {
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        Long orderId = toLong(body.get("orderId"));
        Long productId = toLong(body.get("productId"));
        if (orderId == null || productId == null) {
            throw new BusinessException("订单与商品不能为空");
        }
        ProductOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("订单不存在"));
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(403, "只能评价自己的订单");
        }
        if (!"DONE".equals(order.getStatus())) {
            throw new BusinessException("订单完成后才可以评价");
        }
        if (reviewRepository.findByOrderIdAndProductId(orderId, productId).isPresent()) {
            throw new BusinessException("该商品已经评价过了");
        }
        int rating = body.get("rating") == null ? 5
                : Math.max(1, Math.min(5, Integer.parseInt(String.valueOf(body.get("rating")))));

        ProductReview r = new ProductReview();
        r.setOrderId(orderId);
        r.setProductId(productId);
        r.setProductName((String) body.getOrDefault("productName", null));
        r.setUserId(userId);
        userRepository.findById(userId).ifPresent(u -> {
            r.setNickname(u.getNickname());
            r.setAvatar(u.getAvatar());
        });
        r.setMerchantId(order.getMerchantId());
        r.setRating(rating);
        r.setContent((String) body.get("content"));
        r.setCreatedAt(LocalDateTime.now());
        return Result.ok(reviewRepository.save(r));
    }

    /**
     * 商家回复评价
     * @param id 评价ID
     * @param body 请求体，含reply（回复内容）
     * @return Result<ProductReview> 回复后的评价
     */
    @PostMapping("/{id}/reply")
    public Result<ProductReview> reply(@PathVariable Long id, @RequestBody Map<String, String> body) {
        AuthUtil.requireMerchant();
        Long userId = AuthUtil.requireUserId();
        Merchant shop = merchantRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("尚未开通店铺"));
        ProductReview r = reviewRepository.findById(id)
                .orElseThrow(() -> new BusinessException("评价不存在"));
        if (!shop.getId().equals(r.getMerchantId())) {
            throw new BusinessException(403, "只能回复本店评价");
        }
        String reply = body.get("reply");
        if (reply == null || reply.isBlank()) {
            throw new BusinessException("回复内容不能为空");
        }
        r.setReply(reply);
        r.setRepliedAt(LocalDateTime.now());
        return Result.ok(reviewRepository.save(r));
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        return Long.valueOf(String.valueOf(v));
    }
}
