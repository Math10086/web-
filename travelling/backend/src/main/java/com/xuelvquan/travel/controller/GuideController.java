package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.BusinessException;
import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.entity.*;
import com.xuelvquan.travel.repository.*;
import com.xuelvquan.travel.security.AuthUtil;
import com.xuelvquan.travel.service.GuidePublicService;
import com.xuelvquan.travel.service.NotifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/guides")
@RequiredArgsConstructor
@Transactional
public class GuideController {

    /** 学生导游发布向导服务的信誉分门槛 */
    private static final int MIN_GUIDE_CREDIT = 80;

    private final GuideProfileRepository profileRepository;
    private final GuideServiceRepository serviceRepository;
    private final GuideBookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final CreditLogRepository creditLogRepository;
    private final NotifyService notifyService;
    private final GuidePublicService guidePublicService;

    // ================= 导游申请与审核 =================

    @PostMapping("/apply")
    public Result<GuideProfile> apply(@RequestBody Map<String, String> body) {
        // 仅前台用户可提交，商家不参与
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        GuideProfile p = profileRepository.findByUserId(userId).orElseGet(() -> {
            GuideProfile x = new GuideProfile();
            x.setUserId(userId);
            x.setRatingAvg(0.0);
            x.setRatingCount(0);
            x.setOrderCount(0);
            x.setSupplyTier("STUDENT_GUIDE");
            x.setCreatedAt(LocalDateTime.now());
            return x;
        });
        p.setThemes(body.get("themes"));
        p.setIntro(body.get("intro"));
        p.setStatus("PENDING");
        // 申请阶段固定为学生导游，审核通过后升级
        p.setSupplyTier("STUDENT_GUIDE");
        if (p.getThemes() == null || p.getThemes().isBlank()) {
            throw new BusinessException("擅长主题不能为空");
        }
        return Result.ok(profileRepository.save(p));
    }

    @GetMapping("/profiles")
    public Result<List<Map<String, Object>>> profiles(@RequestParam(required = false) String status) {
        List<GuideProfile> profiles;
        if ("all".equalsIgnoreCase(status)) {
            // 全量（含待审核）为管理后台审核队列，仅管理员
            AuthUtil.requireRole("ADMIN");
            profiles = profileRepository.findAllByOrderByCreatedAtDesc();
        } else {
            profiles = profileRepository.findByStatus("APPROVED");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (GuideProfile p : profiles) {
            result.add(profileView(p));
        }
        return Result.ok(result);
    }

    /**
     * 查询我自己的导游申请
     * @return Result<Map<String,Object>> 导游申请信息，未申请时返回null
     */
    @GetMapping("/my-profile")
    public Result<Map<String, Object>> myProfile() {
        Long userId = AuthUtil.requireUserId();
        return Result.ok(profileRepository.findByUserId(userId).map(this::profileView).orElse(null));
    }

    /**
     * 导游公开主页：展示向导的接待经历、游客评价与在售服务（游客可见）
     * @param userId 向导用户ID
     * @return Result<Map<String,Object>> 导游公开主页
     */
    @GetMapping("/{userId}/public")
    public Result<Map<String, Object>> publicProfile(@PathVariable Long userId) {
        return Result.ok(guidePublicService.publicProfile(userId));
    }

    /**
     * 导游档案对外视图：档案字段与用户信息平铺
     * @param p 导游档案
     * @return Map<String,Object> 对外视图数据
     */
    private Map<String, Object> profileView(GuideProfile p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("userId", p.getUserId());
        m.put("themes", p.getThemes());
        m.put("intro", p.getIntro());
        m.put("status", p.getStatus());
        // 供给层级（学生导游/平台认证导游）
        m.put("supplyTier", p.getSupplyTier());
        m.put("supplyTierLabel", supplyTierLabel(p.getSupplyTier()));
        m.put("ratingAvg", p.getRatingAvg());
        m.put("ratingCount", p.getRatingCount());
        m.put("orderCount", p.getOrderCount());
        m.put("createdAt", p.getCreatedAt());
        userRepository.findById(p.getUserId()).ifPresent(u -> {
            m.put("nickname", u.getNickname());
            m.put("avatar", u.getAvatar() == null ? "" : u.getAvatar());
            m.put("school", u.getSchool() == null ? "" : u.getSchool());
            m.put("user", Map.of("id", u.getId(), "nickname", u.getNickname(),
                    "avatar", u.getAvatar() == null ? "" : u.getAvatar(),
                    "school", u.getSchool() == null ? "" : u.getSchool()));
        });
        m.put("profile", p);
        return m;
    }

    @PostMapping("/profiles/{id}/audit")
    public Result<GuideProfile> auditProfile(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        AuthUtil.requireRole("ADMIN");
        GuideProfile p = profileRepository.findById(id)
                .orElseThrow(() -> new BusinessException("导游申请不存在"));
        boolean approve = Boolean.TRUE.equals(body.get("approve"))
                || Boolean.parseBoolean(String.valueOf(body.get("approve")));
        p.setStatus(approve ? "APPROVED" : "REJECTED");
        // 审核通过即升为平台认证导游
        p.setSupplyTier(approve ? "CERTIFIED_GUIDE" : "STUDENT_GUIDE");
        GuideProfile saved = profileRepository.save(p);
        if (approve) {
            userRepository.findById(p.getUserId()).ifPresent(u -> {
                u.setRole("GUIDE");
                userRepository.save(u);
            });
        }
        notifyService.notify(p.getUserId(), "AUDIT",
                approve ? "导游认证通过" : "导游认证被驳回",
                approve ? "恭喜，您已成为认证导游，可以发布导游服务" : "您的导游认证未通过，请完善资料后重新申请", p.getId());
        return Result.ok(saved);
    }

    // ================= 导游服务 =================

    @GetMapping("/guide-services")
    public Result<List<Map<String, Object>>> services(@RequestParam(required = false) String destination) {
        // 仅前台游客及前台用户可见，管理员与商家不可见
        AuthUtil.requireFrontOrGuest();
        List<GuideService> services = serviceRepository.findApproved(blank(destination));
        List<Map<String, Object>> result = new ArrayList<>();
        for (GuideService s : services) {
            result.add(serviceView(s));
        }
        return Result.ok(result);
    }

    /**
     * 导游服务对外视图：服务字段与向导信息平铺
     * @param s 导游服务
     * @return Map<String,Object> 对外视图数据
     */
    private Map<String, Object> serviceView(GuideService s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("guideId", s.getGuideId());
        m.put("title", s.getTitle());
        m.put("destination", s.getDestination());
        m.put("themes", s.getThemes());
        m.put("price", s.getPrice());
        m.put("schedule", s.getSchedule());
        m.put("intro", s.getIntro());
        m.put("status", s.getStatus());
        m.put("createdAt", s.getCreatedAt());
        userRepository.findById(s.getGuideId()).ifPresent(u -> {
            m.put("guideName", u.getNickname());
            m.put("guideAvatar", u.getAvatar() == null ? "" : u.getAvatar());
            m.put("guideSchool", u.getSchool() == null ? "" : u.getSchool());
            m.put("guide", Map.of("id", u.getId(), "nickname", u.getNickname(),
                    "avatar", u.getAvatar() == null ? "" : u.getAvatar()));
        });
        profileRepository.findByUserId(s.getGuideId()).ifPresent(p -> {
            m.put("ratingAvg", p.getRatingAvg());
            m.put("ratingCount", p.getRatingCount());
            m.put("orderCount", p.getOrderCount());
            // 附带供给层级
            m.put("supplyTier", p.getSupplyTier());
            m.put("supplyTierLabel", supplyTierLabel(p.getSupplyTier()));
        });
        // 价格档便于快速筛选
        m.put("priceTier", priceTier(s.getPrice()));
        m.put("priceTierLabel", priceTierLabel(priceTier(s.getPrice())));
        m.put("service", s);
        return m;
    }

    @PostMapping("/guide-services")
    public Result<GuideService> createService(@RequestBody Map<String, Object> body) {
        // 仅认证导游账号可发布
        AuthUtil.requireGuide();
        Long userId = AuthUtil.requireUserId();
        // 学生导游发布服务须完成三校认证且信誉分 ≥ 80
        User me = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(401, "用户不存在"));
        if (!Boolean.TRUE.equals(me.getVerified())) {
            throw new BusinessException("发布向导服务需先完成三校学生认证");
        }
        int credit = me.getCreditScore() == null ? 100 : me.getCreditScore();
        if (credit < MIN_GUIDE_CREDIT) {
            throw new BusinessException("信誉分不足" + MIN_GUIDE_CREDIT
                    + "（当前" + credit + "），暂不能发布向导服务");
        }
        GuideService s = new GuideService();
        s.setGuideId(userId);
        s.setTitle((String) body.get("title"));
        s.setDestination((String) body.get("destination"));
        s.setThemes((String) body.get("themes"));
        s.setPrice(Double.valueOf(String.valueOf(body.get("price"))));
        s.setSchedule((String) body.get("schedule"));
        s.setIntro((String) body.get("intro"));
        s.setStatus("APPROVED");
        s.setCreatedAt(LocalDateTime.now());
        if (s.getTitle() == null || s.getDestination() == null) {
            throw new BusinessException("服务标题和目的地不能为空");
        }
        return Result.ok(serviceRepository.save(s));
    }

    @PutMapping("/guide-services/{id}")
    public Result<GuideService> updateService(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        AuthUtil.requireGuide();
        Long userId = AuthUtil.requireUserId();
        GuideService s = serviceRepository.findById(id)
                .orElseThrow(() -> new BusinessException("服务不存在"));
        if (!s.getGuideId().equals(userId)) {
            throw new BusinessException(403, "无权修改该服务");
        }
        if (body.containsKey("title")) s.setTitle((String) body.get("title"));
        if (body.containsKey("destination")) s.setDestination((String) body.get("destination"));
        if (body.containsKey("themes")) s.setThemes((String) body.get("themes"));
        if (body.containsKey("schedule")) s.setSchedule((String) body.get("schedule"));
        if (body.containsKey("intro")) s.setIntro((String) body.get("intro"));
        if (body.get("price") != null) s.setPrice(Double.valueOf(String.valueOf(body.get("price"))));
        return Result.ok(serviceRepository.save(s));
    }

    // ================= 导游订单 =================

    @PostMapping("/guide-bookings")
    public Result<GuideBooking> book(@RequestBody Map<String, Object> body) {
        // 仅前台用户可预订，管理员与商家不参与
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        Long serviceId = Long.valueOf(String.valueOf(body.get("serviceId")));
        GuideService s = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new BusinessException("导游服务不存在"));
        if (!"APPROVED".equals(s.getStatus())) {
            throw new BusinessException("该服务暂不可预订");
        }
        if (s.getGuideId().equals(userId)) {
            throw new BusinessException("不能预订自己的导游服务");
        }
        GuideBooking b = new GuideBooking();
        b.setServiceId(serviceId);
        b.setGuideId(s.getGuideId());
        b.setTravelerId(userId);
        b.setTripDate(LocalDate.parse(String.valueOf(body.get("tripDate"))));
        b.setAmount(s.getPrice());
        b.setRemark(body.get("remark") == null ? null : String.valueOf(body.get("remark")));
        b.setStatus("PAID");
        b.setCommissionRate(0.1);
        b.setFee(0.0);
        b.setGuideIncome(0.0);
        b.setCreatedAt(LocalDateTime.now());
        GuideBooking saved = bookingRepository.save(b);

        notifyService.notify(s.getGuideId(), "TRADE", "您有新的导游订单",
                "用户预订了「" + s.getTitle() + "」，平台已代收 ¥" + s.getPrice() + "，请尽快确认", saved.getId());
        return Result.ok(saved);
    }

    @GetMapping("/guide-bookings")
    public Result<List<Map<String, Object>>> bookings(@RequestParam(required = false, defaultValue = "traveler") String role) {
        // 仅前台用户可见，管理员与商家不参与
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        // 接单视角仅导游账号可查看
        if ("guide".equalsIgnoreCase(role)) {
            AuthUtil.requireGuide();
        }
        List<GuideBooking> list = "guide".equalsIgnoreCase(role)
                ? bookingRepository.findByGuideIdOrderByCreatedAtDesc(userId)
                : bookingRepository.findByTravelerIdOrderByCreatedAtDesc(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (GuideBooking b : list) {
            result.add(bookingView(b));
        }
        return Result.ok(result);
    }

    /**
     * 导游订单对外视图：订单字段、服务标题与双方昵称平铺
     * @param b 导游订单
     * @return Map<String,Object> 对外视图数据
     */
    private Map<String, Object> bookingView(GuideBooking b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("serviceId", b.getServiceId());
        m.put("guideId", b.getGuideId());
        m.put("travelerId", b.getTravelerId());
        m.put("tripDate", b.getTripDate());
        m.put("amount", b.getAmount());
        m.put("remark", b.getRemark());
        m.put("status", b.getStatus());
        m.put("commissionRate", b.getCommissionRate());
        m.put("fee", b.getFee());
        m.put("guideIncome", b.getGuideIncome());
        m.put("rating", b.getRating());
        m.put("reviewContent", b.getReviewContent());
        m.put("reviewedAt", b.getReviewedAt());
        m.put("settled", b.getSettled());
        m.put("settledAt", b.getSettledAt());
        m.put("createdAt", b.getCreatedAt());
        serviceRepository.findById(b.getServiceId()).ifPresent(s -> {
            m.put("serviceTitle", s.getTitle());
            m.put("destination", s.getDestination());
        });
        userRepository.findById(b.getGuideId()).ifPresent(u -> m.put("guideName", u.getNickname()));
        userRepository.findById(b.getTravelerId()).ifPresent(u -> m.put("travelerName", u.getNickname()));
        m.put("booking", b);
        return m;
    }

    @PostMapping("/guide-bookings/{id}/confirm")
    public Result<GuideBooking> confirm(@PathVariable Long id) {
        AuthUtil.requireGuide();
        Long userId = AuthUtil.requireUserId();
        GuideBooking b = mustGetBooking(id);
        if (!b.getGuideId().equals(userId)) {
            throw new BusinessException(403, "仅导游本人可确认订单");
        }
        if (!"PAID".equals(b.getStatus())) {
            throw new BusinessException("当前状态不可确认");
        }
        b.setStatus("CONFIRMED");
        GuideBooking saved = bookingRepository.save(b);
        notifyService.notify(b.getTravelerId(), "TRADE", "导游已确认订单",
                "您预订的导游服务已被确认，请按约定时间集合出行", b.getId());
        return Result.ok(saved);
    }

    @PostMapping("/guide-bookings/{id}/complete")
    public Result<GuideBooking> complete(@PathVariable Long id) {
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        GuideBooking b = mustGetBooking(id);
        // 游客或导游任一方均可推进到结算
        if (!b.getTravelerId().equals(userId) && !b.getGuideId().equals(userId)) {
            throw new BusinessException(403, "仅订单参与方可完成订单");
        }
        if (!"CONFIRMED".equals(b.getStatus())) {
            throw new BusinessException("仅已确认的订单可完成");
        }
        b.setStatus("SETTLED");
        b.setFee(Math.round(b.getAmount() * b.getCommissionRate() * 100) / 100.0);
        b.setGuideIncome(Math.round(b.getAmount() * (1 - b.getCommissionRate()) * 100) / 100.0);
        GuideBooking saved = bookingRepository.save(b);

        profileRepository.findByUserId(b.getGuideId()).ifPresent(p -> {
            p.setOrderCount(p.getOrderCount() + 1);
            profileRepository.save(p);
        });
        notifyService.notify(b.getGuideId(), "TRADE", "订单已结算",
                "导游订单 #" + b.getId() + " 已完成并结算，导游收入 ¥" + b.getGuideIncome(), b.getId());
        return Result.ok(saved);
    }

    @PostMapping("/guide-bookings/{id}/review")
    public Result<GuideBooking> review(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        GuideBooking b = mustGetBooking(id);
        if (!b.getTravelerId().equals(userId)) {
            throw new BusinessException(403, "仅下单游客可评价");
        }
        if (!"SETTLED".equals(b.getStatus())) {
            throw new BusinessException("订单完成后才可评价");
        }
        if (b.getRating() != null) {
            throw new BusinessException("该订单已评价");
        }
        int rating = Integer.parseInt(String.valueOf(body.get("rating")));
        if (rating < 1 || rating > 5) {
            throw new BusinessException("评分必须在1-5之间");
        }
        b.setRating(rating);
        b.setReviewContent(body.get("content") == null ? null : String.valueOf(body.get("content")));
        b.setReviewedAt(LocalDateTime.now());
        GuideBooking saved = bookingRepository.save(b);

        GuideProfile p = profileRepository.findByUserId(b.getGuideId()).orElse(null);
        if (p != null) {
            int count = p.getRatingCount() == null ? 0 : p.getRatingCount();
            double avg = p.getRatingAvg() == null ? 0 : p.getRatingAvg();
            p.setRatingAvg(Math.round((avg * count + rating) / (count + 1) * 10) / 10.0);
            p.setRatingCount(count + 1);
            profileRepository.save(p);
        }
        // 导游信誉分调整
        User guide = userRepository.findById(b.getGuideId()).orElse(null);
        if (guide != null) {
            int delta = (rating - 3) * 2;
            guide.setCreditScore((guide.getCreditScore() == null ? 100 : guide.getCreditScore()) + delta);
            userRepository.save(guide);
            CreditLog log = new CreditLog();
            log.setUserId(guide.getId());
            log.setDelta(delta);
            log.setReason("导游订单 #" + b.getId() + " 评价" + rating + " 星");
            log.setCreatedAt(LocalDateTime.now());
            creditLogRepository.save(log);
        }
        notifyService.notify(b.getGuideId(), "INTERACT", "收到新评价",
                "您的导游服务收到" + rating + " 星评价", b.getId());
        return Result.ok(saved);
    }

    @PostMapping("/guide-bookings/{id}/cancel")
    public Result<GuideBooking> cancel(@PathVariable Long id) {
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        GuideBooking b = mustGetBooking(id);
        if (!b.getTravelerId().equals(userId) && !b.getGuideId().equals(userId)) {
            throw new BusinessException(403, "仅订单参与方可取消");
        }
        if (!"PAID".equals(b.getStatus()) && !"CONFIRMED".equals(b.getStatus())) {
            throw new BusinessException("当前订单状态不可取消");
        }
        b.setStatus("CANCELLED");
        GuideBooking saved = bookingRepository.save(b);
        Long other = b.getTravelerId().equals(userId) ? b.getGuideId() : b.getTravelerId();
        notifyService.notify(other, "TRADE", "导游订单已取消",
                "导游订单 #" + b.getId() + " 已被取消", b.getId());
        return Result.ok(saved);
    }

    private GuideBooking mustGetBooking(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new BusinessException("导游订单不存在"));
    }

    private String blank(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /**
     * 供给层级文案
     * @param tier 供给层级编码
     * @return String 供给层级文案
     */
    private String supplyTierLabel(String tier) {
        if (tier == null) {
            return "学生导游";
        }
        return switch (tier) {
            case "CERTIFIED_GUIDE" -> "平台认证导游";
            case "AGENCY" -> "旅行社";
            default -> "学生导游";
        };
    }

    /**
     * 服务价格档（用于列表卡片标签前置）
     * @param price 服务价格
     * @return String 价格档编码（LOW/MID/HIGH）
     */
    private String priceTier(Double price) {
        if (price == null) {
            return "UNKNOWN";
        }
        if (price < 100) {
            return "LOW";
        }
        if (price <= 300) {
            return "MID";
        }
        return "HIGH";
    }

    private String priceTierLabel(String tier) {
        return switch (tier) {
            case "LOW" -> "平价 <100";
            case "MID" -> "适中100–300";
            case "HIGH" -> "专业 >300";
            default -> "价格面议";
        };
    }
}
