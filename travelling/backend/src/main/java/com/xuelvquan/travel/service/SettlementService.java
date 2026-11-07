package com.xuelvquan.travel.service;

import com.xuelvquan.travel.common.BusinessException;
import com.xuelvquan.travel.entity.GuideBooking;
import com.xuelvquan.travel.entity.Merchant;
import com.xuelvquan.travel.entity.ProductOrder;
import com.xuelvquan.travel.entity.Settlement;
import com.xuelvquan.travel.entity.User;
import com.xuelvquan.travel.repository.GuideBookingRepository;
import com.xuelvquan.travel.repository.MerchantRepository;
import com.xuelvquan.travel.repository.ProductOrderRepository;
import com.xuelvquan.travel.repository.SettlementRepository;
import com.xuelvquan.travel.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台结算（管理员后台「结算」能力）。
 *
 * 结算口径：
 *   商城（SHOP）—— 仅统计已完成(DONE)且未结算的订单，平台佣金固定5%；
 *   导游（GUIDE）—— 仅统计已完成(SETTLED)且未打款的向导订单，平台抽成取订单上的commissionRate（默认10%）。
 *
 * 每次结算会生成一条settlements记录，并把相关订单标记settled=true，
 * 因此重复点击「结算」不会重复计入（幂等）。
 */
@Service
@RequiredArgsConstructor
public class SettlementService {

    /** 商城平台佣金比例 */
    public static final double SHOP_COMMISSION_RATE = 0.05;

    private final ProductOrderRepository orderRepository;
    private final GuideBookingRepository bookingRepository;
    private final MerchantRepository merchantRepository;
    private final UserRepository userRepository;
    private final SettlementRepository settlementRepository;

    /** 结算总览：待结算明细（商城/导游） + 已发放记录 */
    public Map<String, Object> overview() {
        List<Map<String, Object>> shopRows = shopPending();
        List<Map<String, Object>> guideRows = guidePending();
        List<Settlement> records = settlementRepository.findAllByOrderByCreatedAtDesc();

        double shopPayable = shopRows.stream().mapToDouble(r -> (double) r.get("payableAmount")).sum();
        double guidePayable = guideRows.stream().mapToDouble(r -> (double) r.get("payableAmount")).sum();
        double pendingCommission = shopRows.stream().mapToDouble(r -> (double) r.get("commissionAmount")).sum()
                + guideRows.stream().mapToDouble(r -> (double) r.get("commissionAmount")).sum();
        double paidTotal = records.stream().mapToDouble(r -> nz(r.getPayableAmount())).sum();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("shopCommissionRate", SHOP_COMMISSION_RATE);
        m.put("shopRows", shopRows);
        m.put("guideRows", guideRows);
        m.put("records", records);
        m.put("pendingShopPayable", round(shopPayable));
        m.put("pendingGuidePayable", round(guidePayable));
        m.put("pendingPayable", round(shopPayable + guidePayable));
        m.put("pendingCommission", round(pendingCommission));
        m.put("paidTotal", round(paidTotal));
        m.put("paidCount", records.size());
        return m;
    }

    /** 商城待结算明细：按商家聚合 */
    private List<Map<String, Object>> shopPending() {
        Map<Long, List<ProductOrder>> byMerchant = new LinkedHashMap<>();
        for (ProductOrder o : orderRepository.findSettleable("DONE")) {
            if (o.getMerchantId() == null) {
                continue;
            }
            byMerchant.computeIfAbsent(o.getMerchantId(), k -> new ArrayList<>()).add(o);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        byMerchant.forEach((merchantId, orders) -> {
            double gross = orders.stream().mapToDouble(o -> nz(o.getAmount())).sum();
            double commission = round(gross * SHOP_COMMISSION_RATE);
            Merchant shop = merchantRepository.findById(merchantId).orElse(null);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("type", "SHOP");
            r.put("targetId", merchantId);
            r.put("targetName", shop != null && shop.getShopName() != null
                    ? shop.getShopName() : ("店铺 #" + merchantId));
            r.put("orderCount", orders.size());
            r.put("grossAmount", round(gross));
            r.put("commissionRate", SHOP_COMMISSION_RATE);
            r.put("commissionAmount", commission);
            r.put("payableAmount", round(gross - commission));
            rows.add(r);
        });
        rows.sort(Comparator.comparingDouble((Map<String, Object> r) -> (double) r.get("payableAmount")).reversed());
        return rows;
    }

    /** 导游待结算明细：按导游聚合（收入已在订单完成时按commissionRate算好） */
    private List<Map<String, Object>> guidePending() {
        Map<Long, List<GuideBooking>> byGuide = new LinkedHashMap<>();
        for (GuideBooking b : bookingRepository.findSettleable("SETTLED")) {
            if (b.getGuideId() == null) {
                continue;
            }
            byGuide.computeIfAbsent(b.getGuideId(), k -> new ArrayList<>()).add(b);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        byGuide.forEach((guideId, bookings) -> {
            double gross = bookings.stream().mapToDouble(b -> nz(b.getAmount())).sum();
            double fee = bookings.stream().mapToDouble(b -> nz(b.getFee())).sum();
            double income = bookings.stream().mapToDouble(b -> nz(b.getGuideIncome())).sum();
            User guide = userRepository.findById(guideId).orElse(null);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("type", "GUIDE");
            r.put("targetId", guideId);
            r.put("targetName", guide != null && guide.getNickname() != null
                    ? guide.getNickname() : ("导游 #" + guideId));
            r.put("orderCount", bookings.size());
            r.put("grossAmount", round(gross));
            r.put("commissionRate", gross > 0 ? round(fee / gross) : 0.1);
            r.put("commissionAmount", round(fee));
            r.put("payableAmount", round(income));
            rows.add(r);
        });
        rows.sort(Comparator.comparingDouble((Map<String, Object> r) -> (double) r.get("payableAmount")).reversed());
        return rows;
    }

    /**
     * 执行一次结算发放（幂等：结算后订单标记settled=true，不会重复计入）
     * @param type 结算类型，取SHOP（商城）或GUIDE（导游）
     * @param targetId 结算对象ID，商城为商家ID，导游为导游用户ID
     * @param operatorId 操作的管理员用户ID
     * @return Settlement 本次生成的结算记录
     */
    @Transactional
    public Settlement payout(String type, Long targetId, Long operatorId) {
        if (type == null || targetId == null) {
            throw new BusinessException("结算对象不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        Settlement s = new Settlement();
        s.setOperatorId(operatorId);
        s.setCreatedAt(now);
        s.setStatus("PAID");

        if ("SHOP".equalsIgnoreCase(type)) {
            List<ProductOrder> orders = orderRepository.findSettleable("DONE").stream()
                    .filter(o -> targetId.equals(o.getMerchantId())).toList();
            if (orders.isEmpty()) {
                throw new BusinessException("该店铺当前没有待结算订单");
            }
            double gross = orders.stream().mapToDouble(o -> nz(o.getAmount())).sum();
            double commission = round(gross * SHOP_COMMISSION_RATE);
            Merchant shop = merchantRepository.findById(targetId).orElse(null);
            orders.forEach(o -> {
                o.setSettled(true);
                o.setSettledAt(now);
            });
            orderRepository.saveAll(orders);

            s.setType("SHOP");
            s.setTargetId(targetId);
            s.setTargetName(shop != null && shop.getShopName() != null
                    ? shop.getShopName() : ("店铺 #" + targetId));
            s.setOrderCount(orders.size());
            s.setGrossAmount(round(gross));
            s.setCommissionRate(SHOP_COMMISSION_RATE);
            s.setCommissionAmount(commission);
            s.setPayableAmount(round(gross - commission));
            s.setRemark("商城订单货款结算：成交 ¥" + round(gross) + "，平台佣金" + (int) (SHOP_COMMISSION_RATE * 100)
                    + "%（¥" + commission + "），打款商家 ¥" + round(gross - commission));
        } else if ("GUIDE".equalsIgnoreCase(type)) {
            List<GuideBooking> bookings = bookingRepository.findSettleable("SETTLED").stream()
                    .filter(b -> targetId.equals(b.getGuideId())).toList();
            if (bookings.isEmpty()) {
                throw new BusinessException("该导游当前没有待结算订单");
            }
            double gross = bookings.stream().mapToDouble(b -> nz(b.getAmount())).sum();
            double fee = bookings.stream().mapToDouble(b -> nz(b.getFee())).sum();
            double income = bookings.stream().mapToDouble(b -> nz(b.getGuideIncome())).sum();
            User guide = userRepository.findById(targetId).orElse(null);
            bookings.forEach(b -> {
                b.setSettled(true);
                b.setSettledAt(now);
            });
            bookingRepository.saveAll(bookings);

            s.setType("GUIDE");
            s.setTargetId(targetId);
            s.setTargetName(guide != null && guide.getNickname() != null
                    ? guide.getNickname() : ("导游 #" + targetId));
            s.setOrderCount(bookings.size());
            s.setGrossAmount(round(gross));
            s.setCommissionRate(gross > 0 ? round(fee / gross) : 0.1);
            s.setCommissionAmount(round(fee));
            s.setPayableAmount(round(income));
            s.setRemark("向导订单结算：成交 ¥" + round(gross) + "，平台抽成 ¥" + round(fee)
                    + "，打款导游 ¥" + round(income));
        } else {
            throw new BusinessException("结算类型非法（仅支持 SHOP / GUIDE）");
        }

        return settlementRepository.save(s);
    }

    private static double nz(Double v) {
        return v == null ? 0.0 : v;
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
