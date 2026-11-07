package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.BusinessException;
import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.entity.*;
import com.xuelvquan.travel.repository.*;
import com.xuelvquan.travel.security.AuthUtil;
import com.xuelvquan.travel.service.NotifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Transactional
public class OrderController {

    private final ProductOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final MerchantRepository merchantRepository;
    private final NotifyService notifyService;

    @PostMapping
    public Result<ProductOrder> create(@RequestBody Map<String, Object> body) {
        // 仅前台用户可下单
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null || items.isEmpty()) {
            throw new BusinessException("订单商品不能为空");
        }
        String receiver = String.valueOf(body.getOrDefault("receiver", ""));

        // 第一阶段：校验库存并锁定商户（全部商品应属于同一店铺）
        Long merchantId = null;
        double amount = 0;
        List<Product> products = new ArrayList<>();
        List<Integer> quantities = new ArrayList<>();
        for (Map<String, Object> item : items) {
            Long productId = Long.valueOf(String.valueOf(item.get("productId")));
            int qty = Integer.parseInt(String.valueOf(item.get("quantity")));
            if (qty <= 0) {
                throw new BusinessException("商品数量必须大于0");
            }
            Product p = productRepository.findById(productId)
                    .orElseThrow(() -> new BusinessException("商品不存在：" + productId));
            if (p.getStock() < qty) {
                throw new BusinessException("商品「" + p.getName() + "」库存不足");
            }
            if (merchantId == null) {
                merchantId = p.getMerchantId();
            } else if (!merchantId.equals(p.getMerchantId())) {
                throw new BusinessException("一次只能下单同一店铺的商品");
            }
            amount += p.getPrice() * qty;
            products.add(p);
            quantities.add(qty);
        }

        ProductOrder order = new ProductOrder();
        order.setUserId(userId);
        order.setMerchantId(merchantId);
        order.setAmount(Math.round(amount * 100) / 100.0);
        order.setStatus("PAID");
        order.setReceiver(receiver);
        order.setCreatedAt(LocalDateTime.now());
        orderRepository.save(order);

        // 扣库存、累销量、生成明细
        for (int i = 0; i < products.size(); i++) {
            Product p = products.get(i);
            int qty = quantities.get(i);
            p.setStock(p.getStock() - qty);
            p.setSales(p.getSales() + qty);
            productRepository.save(p);

            OrderItem oi = new OrderItem();
            oi.setOrderId(order.getId());
            oi.setProductId(p.getId());
            oi.setName(p.getName());
            oi.setPrice(p.getPrice());
            oi.setQuantity(qty);
            oi.setImage(p.getImage());
            orderItemRepository.save(oi);
        }

        merchantRepository.findById(merchantId).ifPresent(m ->
                notifyService.notify(m.getUserId(), "TRADE", "您有新的订单",
                        "店铺收到一笔新订单，金额 ¥" + order.getAmount() + "，请尽快发货", order.getId()));
        return Result.ok(order);
    }

    @GetMapping("/mine")
    public Result<List<Map<String, Object>>> mine() {
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        return Result.ok(attachItems(orderRepository.findByUserIdOrderByCreatedAtDesc(userId)));
    }

    @GetMapping("/shop")
    public Result<List<Map<String, Object>>> shop() {
        // 仅商家可查看店铺订单
        AuthUtil.requireMerchant();
        Long userId = AuthUtil.requireUserId();
        Merchant shop = merchantRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("尚未开通店铺"));
        return Result.ok(attachItems(orderRepository.findByMerchantIdOrderByCreatedAtDesc(shop.getId())));
    }

    @PostMapping("/{id}/ship")
    public Result<ProductOrder> ship(@PathVariable Long id) {
        AuthUtil.requireMerchant();
        Long userId = AuthUtil.requireUserId();
        ProductOrder order = mustGet(id);
        Merchant shop = merchantRepository.findById(order.getMerchantId())
                .orElseThrow(() -> new BusinessException("店铺不存在"));
        if (!shop.getUserId().equals(userId)) {
            throw new BusinessException(403, "仅店主可发货");
        }
        if (!"PAID".equals(order.getStatus())) {
            throw new BusinessException("当前订单状态不可发货");
        }
        order.setStatus("SHIPPED");
        ProductOrder saved = orderRepository.save(order);
        notifyService.notify(order.getUserId(), "TRADE", "订单已发货",
                "您的订单 #" + order.getId() + " 已发货，请注意查收", order.getId());
        return Result.ok(saved);
    }

    @PostMapping("/{id}/confirm")
    public Result<ProductOrder> confirm(@PathVariable Long id) {
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        ProductOrder order = mustGet(id);
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(403, "仅下单人可确认收货");
        }
        if (!"SHIPPED".equals(order.getStatus())) {
            throw new BusinessException("当前订单状态不可确认收货");
        }
        order.setStatus("DONE");
        ProductOrder saved = orderRepository.save(order);
        merchantRepository.findById(order.getMerchantId()).ifPresent(m ->
                notifyService.notify(m.getUserId(), "TRADE", "订单已完成",
                        "订单 #" + order.getId() + " 已确认收货", order.getId()));
        return Result.ok(saved);
    }

    private List<Map<String, Object>> attachItems(List<ProductOrder> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> ids = orders.stream().map(ProductOrder::getId).toList();
        List<OrderItem> allItems = orderItemRepository.findByOrderIdIn(ids);
        Map<Long, List<OrderItem>> grouped = new HashMap<>();
        for (OrderItem oi : allItems) {
            grouped.computeIfAbsent(oi.getOrderId(), k -> new ArrayList<>()).add(oi);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (ProductOrder o : orders) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("order", o);
            m.put("items", grouped.getOrDefault(o.getId(), List.of()));
            result.add(m);
        }
        return result;
    }

    private ProductOrder mustGet(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException("订单不存在"));
    }
}
