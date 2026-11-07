package com.xuelvquan.travel.service;

import com.xuelvquan.travel.entity.CrawlBatch;
import com.xuelvquan.travel.entity.Expense;
import com.xuelvquan.travel.entity.GuideBooking;
import com.xuelvquan.travel.entity.GuideService;
import com.xuelvquan.travel.entity.Merchant;
import com.xuelvquan.travel.entity.OrderItem;
import com.xuelvquan.travel.entity.Product;
import com.xuelvquan.travel.entity.ProductOrder;
import com.xuelvquan.travel.entity.ProductReview;
import com.xuelvquan.travel.entity.Trip;
import com.xuelvquan.travel.entity.TripMember;
import com.xuelvquan.travel.entity.User;
import com.xuelvquan.travel.repository.ExpenseRepository;
import com.xuelvquan.travel.repository.GuideBookingRepository;
import com.xuelvquan.travel.repository.GuideServiceRepository;
import com.xuelvquan.travel.repository.MerchantRepository;
import com.xuelvquan.travel.repository.OrderItemRepository;
import com.xuelvquan.travel.repository.ProductOrderRepository;
import com.xuelvquan.travel.repository.ProductRepository;
import com.xuelvquan.travel.repository.ProductReviewRepository;
import com.xuelvquan.travel.repository.ScrapedInfoRepository;
import com.xuelvquan.travel.repository.TripMemberRepository;
import com.xuelvquan.travel.repository.TripRepository;
import com.xuelvquan.travel.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 分期新增功能的演示数据初始化（与DataInitializer解耦，按各自表是否为空独立判断，
 * 因此对已有数据库同样会补齐，不依赖user表是否为空）。
 *
 * 其中采集信息不再硬编码入库，而是走真实采集链路（CrawlService），
 * 由各采集器决定数据来源与回落策略，并留下可查的采集批次日志。
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class DemoContentInitializer implements CommandLineRunner {

    private final ScrapedInfoRepository scrapedInfoRepository;
    private final ExpenseRepository expenseRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final CrawlService crawlService;
    private final ProductOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductReviewRepository productReviewRepository;
    private final UserRepository userRepository;
    private final GuideBookingRepository guideBookingRepository;
    private final GuideServiceRepository guideServiceRepository;
    private final MerchantRepository merchantRepository;
    private final ProductRepository productRepository;

    /** 启动时是否执行一次采集（xlvq.crawl.startup），用于首次部署灌数据与验证采集链路 */
    @Value("${xlvq.crawl.startup:true}")
    private boolean startupCrawl;

    @Override
    public void run(String... args) {
        try {
            startupCrawl();
        } catch (Exception e) {
            log.warn("[学旅圈] 启动采集跳过：{}", e.getMessage());
        }
        try {
            seedExpenses();
        } catch (Exception e) {
            log.warn("[学旅圈] 消费演示数据初始化跳过：{}", e.getMessage());
        }
        try {
            seedProductOrders();
        } catch (Exception e) {
            log.warn("[学旅圈] 商城订单演示数据初始化跳过：{}", e.getMessage());
        }
        try {
            seedProductReviews();
        } catch (Exception e) {
            log.warn("[学旅圈] 商品评价演示数据初始化跳过：{}", e.getMessage());
        }
        try {
            seedGuideBookings();
        } catch (Exception e) {
            log.warn("[学旅圈] 向导预约演示数据初始化跳过：{}", e.getMessage());
        }
    }

    /**
     * 商城订单演示数据（管理员「交易结算」的商城侧、商家中心「交易」需要内容）。
     * 取已通过审核的店铺与其在售商品，生成若干已完成(DONE)订单，
     * 结算口径据此聚合出「成交额/平台佣金/应结货款」。已有订单时跳过。
     */
    private void seedProductOrders() {
        if (orderRepository.count() > 0) {
            return;
        }
        Merchant shop = merchantRepository.findAll().stream()
                .filter(m -> "APPROVED".equals(m.getStatus()))
                .findFirst()
                .orElse(null);
        if (shop == null) {
            return;
        }
        List<Product> products = productRepository.search(null, null, shop.getId());
        if (products.isEmpty()) {
            return;
        }
        String[][] buyers = {
                {"dashan", "菜鸟驿站（中南大学店）"},
                {"aya", "湖南师大天马公寓菜鸟驿站"},
                {"xiaolu", "中南大学南校区快递中心"},
                {"tuoz", "中南林业科技大学东门驿站"},
                {"yueyue", "湖南大学北校区菜鸟驿站"}
        };
        int idx = 0;
        for (String[] buyer : buyers) {
            User u = userRepository.findByUsername(buyer[0]).orElse(null);
            if (u == null) {
                continue;
            }
            // 每单1~2件商品，轮换取用
            Product p1 = products.get(idx % products.size());
            Product p2 = products.size() > 1 ? products.get((idx + 2) % products.size()) : null;

            double amount = p1.getPrice() == null ? 0 : p1.getPrice();
            ProductOrder o = new ProductOrder();
            o.setUserId(u.getId());
            o.setMerchantId(shop.getId());
            o.setStatus("DONE");
            o.setReceiver(buyer[1]);
            o.setSettled(false);
            o.setCreatedAt(LocalDateTime.now().minusDays(9L - idx));
            o = orderRepository.save(o);

            double total = saveItem(o.getId(), p1, 1);
            if (p2 != null && !p2.getId().equals(p1.getId()) && idx % 2 == 0) {
                total += saveItem(o.getId(), p2, 1);
            }
            o.setAmount(round2(total));
            orderRepository.save(o);
            idx++;
        }
        log.info("[学旅圈] 初始化商城订单演示数据 {} 笔（已完成待结算）", idx);
    }

    /**
     * 写入一条订单明细并返回小计
     * @param orderId 所属订单ID
     * @param p 下单商品
     * @param qty 购买数量
     * @return double 该明细小计金额（单价×数量）
     */
    private double saveItem(Long orderId, Product p, int qty) {
        OrderItem it = new OrderItem();
        it.setOrderId(orderId);
        it.setProductId(p.getId());
        it.setName(p.getName());
        it.setPrice(p.getPrice() == null ? 0 : p.getPrice());
        it.setQuantity(qty);
        it.setImage(p.getImage());
        orderItemRepository.save(it);
        return it.getPrice() * qty;
    }

    /**
     * 向导预约演示数据（管理员后台「交易结算」需要导游侧可结算内容）。
     * DataInitializer只种了1条CONFIRMED预约，这里补齐若干已完成(SETTLED)订单，
     * 使结算总览能同时展示「商城货款」与「导游分成」两块。
     * 已有预约时跳过，不会重复插入。
     */
    private void seedGuideBookings() {
        if (guideBookingRepository.count() > 1) {
            return;
        }
        User guide = userRepository.findByUsername("dao").orElse(null);
        if (guide == null) {
            return;
        }
        List<GuideService> services = guideServiceRepository.findByGuideId(guide.getId());
        if (services.isEmpty()) {
            return;
        }
        // {旅行者用户名, 服务序号, 金额, 状态, 提前天数}
        Object[][] samples = {
                {"dashan", 1, 199.0, "SETTLED", 9},
                {"aya", 2, 299.0, "SETTLED", 8},
                {"xiaolu", 2, 299.0, "SETTLED", 6},
                {"tuoz", 1, 199.0, "SETTLED", 5},
                {"yueyue", 2, 299.0, "SETTLED", 3},
                {"dashan", 0, 199.0, "CONFIRMED", 2}
        };
        int idx = 0;
        for (Object[] s : samples) {
            User traveler = userRepository.findByUsername((String) s[0]).orElse(null);
            if (traveler == null) {
                continue;
            }
            GuideService svc = services.get(Math.min((int) s[1], services.size() - 1));
            double amount = (double) s[2];
            String status = (String) s[3];

            GuideBooking b = new GuideBooking();
            b.setServiceId(svc.getId());
            b.setGuideId(guide.getId());
            b.setTravelerId(traveler.getId());
            b.setTripDate(LocalDate.now().minusDays((int) s[4]));
            b.setAmount(amount);
            b.setCommissionRate(0.1);
            // 未完成的订单不产生收入，完成时才结转平台抽成与导游收入
            if ("SETTLED".equals(status)) {
                b.setFee(round2(amount * 0.1));
                b.setGuideIncome(round2(amount * 0.9));
            } else {
                b.setFee(0.0);
                b.setGuideIncome(0.0);
            }
            b.setRemark(idx % 2 == 0 ? "周末出行，希望节奏慢一些多拍照" : "第一次请当地向导，主要想走小众线路");
            b.setStatus(status);
            b.setCreatedAt(LocalDateTime.now().minusDays((int) s[4] + 2L));
            guideBookingRepository.save(b);
            idx++;
        }
        log.info("[学旅圈] 初始化向导预约演示数据 {} 条（含已完成可结算订单）", idx);
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    /**
     * 商品评价演示数据（商家中心「评价管理」需要内容可看）：
     * 取已有的已完成订单，为其中的商品补1~5星评价，部分带商家回复。
     * 按product_reviews是否为空判断，不会重复插入。
     */
    private void seedProductReviews() {
        if (productReviewRepository.count() > 0) {
            return;
        }
        List<ProductOrder> done = orderRepository.findAll().stream()
                .filter(o -> "DONE".equals(o.getStatus()) && o.getMerchantId() != null)
                .sorted(java.util.Comparator.comparing(ProductOrder::getId))
                .limit(4)
                .toList();
        if (done.isEmpty()) {
            return;
        }
        // {星级, 买家评价, 商家回复}
        String[][] samples = {
                {"5", "背包做工扎实，承重很稳，周末武功山三天下来肩膀没怎么酸，学长发货也快。", "感谢支持！记得把腰带调到胯部位置，能省不少力气～"},
                {"4", "帐篷防雨不错，凌晨下暴雨里面是干的。就是速开说明书有点简略，第一次搭花了十分钟。", "收到建议，已把搭建视频二维码印到收纳袋上，需要视频也可以直接找客服。"},
                {"5", "防潮垫轻便又便宜，宿舍党露营完全够用，卷起来塞包里不占地方。", ""},
                {"4", "移动电源能带上飞机，在山里拍了两天照片还有电，比较安心。", ""}
        };
        int idx = 0;
        for (ProductOrder o : done) {
            for (OrderItem it : orderItemRepository.findByOrderId(o.getId())) {
                if (idx >= samples.length) {
                    break;
                }
                ProductReview r = new ProductReview();
                r.setOrderId(o.getId());
                r.setProductId(it.getProductId());
                r.setProductName(it.getName());
                r.setUserId(o.getUserId());
                userRepository.findById(o.getUserId()).ifPresent(u -> {
                    r.setNickname(u.getNickname());
                    r.setAvatar(u.getAvatar());
                });
                r.setMerchantId(o.getMerchantId());
                r.setRating(Integer.parseInt(samples[idx][0]));
                r.setContent(samples[idx][1]);
                if (!samples[idx][2].isBlank()) {
                    r.setReply(samples[idx][2]);
                    r.setRepliedAt(LocalDateTime.now().minusDays(1));
                }
                r.setCreatedAt(LocalDateTime.now().minusDays(4L + idx));
                productReviewRepository.save(r);
                idx++;
            }
            if (idx >= samples.length) {
                break;
            }
        }
        log.info("[学旅圈] 初始化商品评价演示数据 {} 条", idx);
    }

    /**
     * 启动时在后台线程执行一次全量采集：采集器按业务主键upsert，不会重复插入；
     * 首次部署即有数据，同时为"信息浏览"页生成一条可查的采集批次日志。
     *
     * 放后台执行的原因：真实采集需访问外部站点（Open-Meteo、12306等），
     * 串行会拖慢启动约10秒以上，开发期频繁重启体验较差。
     */
    private void startupCrawl() {
        if (!startupCrawl) {
            log.info("[学旅圈] 启动采集已关闭（xlvq.crawl.startup=false），当前采集信息 {} 条",
                    scrapedInfoRepository.count());
            return;
        }
        Thread t = new Thread(() -> {
            try {
                CrawlBatch batch = crawlService.crawl("STARTUP");
                log.info("[学旅圈] 启动采集完成（批次 {}）：{}", batch.getBatchNo(), batch.getMessage());
            } catch (Exception e) {
                log.warn("[学旅圈] 启动采集失败：{}", e.getMessage());
            }
        }, "startup-crawl");
        t.setDaemon(true);
        t.start();
        log.info("[学旅圈] 启动采集已在后台执行，稍后可在「信息浏览 → 采集日志」查看结果");
    }

    /** 消费与费用管理（3.21）演示数据：团队共享消费+个人消费 */
    private void seedExpenses() {
        if (expenseRepository.count() > 0) {
            return;
        }
        List<Trip> trips = tripRepository.findAll();
        trips.sort(java.util.Comparator.comparing(Trip::getId));
        if (trips.isEmpty()) {
            return;
        }
        log.info("[学旅圈] 初始化消费与费用演示数据...");

        Trip trip = trips.get(0);
        LinkedHashSet<Long> memberSet = new LinkedHashSet<>();
        memberSet.add(trip.getCaptainId());
        for (TripMember m : tripMemberRepository.findByTripIdAndStatus(trip.getId(), "APPROVED")) {
            if (m.getUserId() != null) {
                memberSet.add(m.getUserId());
            }
        }
        memberSet.remove(null);
        List<Long> sharers = new ArrayList<>(memberSet);
        Long captain = trip.getCaptainId();
        Long other = sharers.size() > 1 ? sharers.get(1) : captain;

        LocalDateTime base = LocalDateTime.now().minusDays(2);
        addExpense("TEAM", trip.getId(), captain, "TRANSPORT", "长沙往返张家界高铁票",
                330.0, sharers, captain, "二等座，2人", base.plusHours(2));
        addExpense("TEAM", trip.getId(), other, "HOTEL", "武陵源民宿（2晚 · 2人分摊）",
                520.0, sharers, captain, "含双早", base.plusHours(6));
        addExpense("TEAM", trip.getId(), captain, "TICKET", "张家界国家森林公园4日门票",
                456.0, sharers, captain, "228元 / 人，2张", base.plusHours(8));
        addExpense("TEAM", trip.getId(), other, "FOOD", "土家三下锅团队晚餐",
                186.0, sharers, other, "AA 平摊", base.plusHours(20));
        addExpense("TEAM", trip.getId(), captain, "TRANSPORT", "景区环保车 + 索道",
                240.0, sharers, captain, "金鞭溪—袁家界", base.plusHours(26));

        // 个人消费（小麓=用户2）
        addExpense("PERSONAL", null, 2L, "FOOD", "长沙 Citywalk 咖啡",
                46.0, null, 2L, "五一广场探店", base.plusDays(1));
        addExpense("PERSONAL", null, 2L, "SHOPPING", "登山杖 + 护膝",
                168.0, null, 2L, "出行装备", base.plusDays(1));
        addExpense("PERSONAL", null, 2L, "TRANSPORT", "网约车往返集合点",
                58.0, null, 2L, null, base.plusDays(2));

        log.info("[学旅圈] 消费演示数据初始化完成，共 {} 条", expenseRepository.count());
    }

    private void addExpense(String scope, Long tripId, Long userId, String category, String title,
                            Double amount, List<Long> sharers, Long createdBy, String remark,
                            LocalDateTime createdAt) {
        Expense e = new Expense();
        e.setScope(scope);
        e.setTripId(tripId);
        e.setUserId(userId);
        e.setCategory(category);
        e.setTitle(title);
        e.setAmount(amount);
        List<Long> s = sharers == null || sharers.isEmpty() ? List.of(userId) : sharers;
        StringBuilder sb = new StringBuilder();
        for (Long id : s) {
            if (sb.length() > 0) sb.append(",");
            sb.append(id);
        }
        e.setSharers(sb.toString());
        e.setPerPerson(Math.round(amount / s.size() * 100) / 100.0);
        e.setCreatedBy(createdBy);
        e.setRemark(remark);
        e.setCreatedAt(createdAt);
        expenseRepository.save(e);
    }
}
