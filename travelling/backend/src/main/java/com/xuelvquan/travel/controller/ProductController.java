package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.BusinessException;
import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.entity.Merchant;
import com.xuelvquan.travel.entity.Product;
import com.xuelvquan.travel.repository.MerchantRepository;
import com.xuelvquan.travel.repository.ProductRepository;
import com.xuelvquan.travel.security.AuthContext;
import com.xuelvquan.travel.security.AuthUtil;
import com.xuelvquan.travel.service.ProductSalesService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Transactional
public class ProductController {

    private final ProductRepository productRepository;
    private final MerchantRepository merchantRepository;
    private final ProductSalesService productSalesService;

    @GetMapping
    public Result<List<Product>> list(@RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) String category,
                                      @RequestParam(required = false) Long merchantId) {
        return Result.ok(productRepository.search(blank(keyword), blank(category), merchantId));
    }

    /**
     * 商家销售洞察：交易量、季度/月度高锋分布、品类结构与备货建议。
     * 仅商家本人可见（按登录账号反查店铺，不接受前端传merchantId，避免越权看别家数据）。
     */
    @GetMapping("/merchant/insight")
    public Result<Map<String, Object>> merchantInsight() {
        AuthUtil.requireMerchant();
        Long userId = AuthUtil.requireUserId();
        return Result.ok(productSalesService.merchantInsight(userId));
    }

    /**
     * 商品平替/替代品推荐（买家视角）：同品类里更便宜的、更高配的，带差价与销量理由。
     * 公开接口，游客也能看。
     * @param id 商品ID
     * @return Result<Map<String,Object>> 替代商品及其差价、销量理由
     */
    @GetMapping("/{id}/alternatives")
    public Result<Map<String, Object>> alternatives(@PathVariable Long id) {
        return Result.ok(productSalesService.alternatives(id));
    }

    @GetMapping("/{id}")
    public Result<Product> detail(@PathVariable Long id) {
        return Result.ok(productRepository.findById(id)
                .orElseThrow(() -> new BusinessException("商品不存在")));
    }

    @PostMapping
    public Result<Product> create(@RequestBody Product body) {
        // 开店上架属商家经营能力：管理员只做后台治理，不参与经营
        AuthUtil.requireMerchant();
        Long userId = AuthUtil.requireUserId();
        Long merchantId = body.getMerchantId();
        Merchant own = merchantRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("尚未开通店铺"));
        if (!"APPROVED".equals(own.getStatus())) {
            throw new BusinessException("店铺尚未通过审核");
        }
        if (merchantId != null && !merchantId.equals(own.getId())) {
            throw new BusinessException(403, "无权在他人店铺发布商品");
        }
        body.setId(null);
        body.setMerchantId(own.getId());
        if (body.getSales() == null) body.setSales(0);
        if (body.getStock() == null) body.setStock(0);
        body.setCreatedAt(LocalDateTime.now());
        return Result.ok(productRepository.save(body));
    }

    @PutMapping("/{id}")
    public Result<Product> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        AuthUtil.requireMerchant();
        Long userId = AuthUtil.requireUserId();
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException("商品不存在"));
        Merchant shop = merchantRepository.findById(p.getMerchantId())
                .orElseThrow(() -> new BusinessException("店铺不存在"));
        if (!shop.getUserId().equals(userId)) {
            throw new BusinessException(403, "只能修改本店商品");
        }
        if (body.containsKey("name")) p.setName((String) body.get("name"));
        if (body.containsKey("category")) p.setCategory((String) body.get("category"));
        if (body.containsKey("image")) p.setImage((String) body.get("image"));
        if (body.containsKey("description")) p.setDescription((String) body.get("description"));
        if (body.get("price") != null) p.setPrice(Double.valueOf(String.valueOf(body.get("price"))));
        if (body.get("stock") != null) p.setStock(Integer.valueOf(String.valueOf(body.get("stock"))));
        return Result.ok(productRepository.save(p));
    }

    private String blank(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
