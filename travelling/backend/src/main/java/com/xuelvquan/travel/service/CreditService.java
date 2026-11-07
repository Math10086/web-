package com.xuelvquan.travel.service;

import com.xuelvquan.travel.common.BusinessException;
import com.xuelvquan.travel.entity.CreditLog;
import com.xuelvquan.travel.entity.Trip;
import com.xuelvquan.travel.entity.TripComplaint;
import com.xuelvquan.travel.entity.TripMember;
import com.xuelvquan.travel.entity.User;
import com.xuelvquan.travel.repository.CreditLogRepository;
import com.xuelvquan.travel.repository.TripComplaintRepository;
import com.xuelvquan.travel.repository.TripMemberRepository;
import com.xuelvquan.travel.repository.TripRepository;
import com.xuelvquan.travel.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 信誉分体系与统一规则引擎
 */
@Service
@RequiredArgsConstructor
public class CreditService {

    private final UserRepository userRepository;
    private final CreditLogRepository creditLogRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository memberRepository;
    private final TripComplaintRepository complaintRepository;

    /** 信誉分上下限：满分150（超额信誉给徽章不加数），跌破0触底 */
    public static final int SCORE_FLOOR = 0;
    public static final int SCORE_CEIL = 150;
    public static final int DEFAULT_SCORE = 100;

    // ===== 扣分档位（四类同行人投诉，核实后生效）=====
    public static final int PENALTY_NO_SHOW = 15;        // 爽约：直接影响他人行程，最重
    public static final int PENALTY_LOST_CONTACT = 10;   // 行程中失联
    public static final int PENALTY_MISBEHAVE = 10;      // 言行不当
    public static final int PENALTY_EXPENSE = 10;        // 费用不清
    /** 行程结束且账目全部结清的守信奖励 */
    public static final int REWARD_SETTLED = 3;

    /** 组队门槛：信誉分低于60不能发布或报名结伴 */
    public static final int TRIP_THRESHOLD = 60;
    /** 学生业余导游发布门槛 */
    public static final int GUIDE_THRESHOLD = 80;

    // ==================== 统一入口 ====================

    /**
     * 信誉分变动唯一入口：clamp到 [0,150]，写CreditLog，返回更新后的用户。
     * 任何调用方都必须给出可读reason（会在信誉主页展示给本人）。
     * @param userId 用户ID
     * @param delta 变动分值，正数为加分、负数为扣分
     * @param reason 变动原因，会展示给本人，为空时记为「信誉分调整」
     * @return User 更新后的用户
     */
    @Transactional
    public User addCredit(Long userId, int delta, String reason) {
        if (delta == 0) {
            return userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException("用户不存在"));
        }
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        int old = u.getCreditScore() == null ? DEFAULT_SCORE : u.getCreditScore();
        int now = Math.max(SCORE_FLOOR, Math.min(SCORE_CEIL, old + delta));
        u.setCreditScore(now);
        userRepository.save(u);

        CreditLog log = new CreditLog();
        log.setUserId(userId);
        log.setDelta(now - old);
        log.setReason(reason == null ? "信誉分调整" : reason);
        log.setCreatedAt(LocalDateTime.now());
        creditLogRepository.save(log);
        return u;
    }

    /**
     * 投诉类型 → 扣分档位
     * @param type 投诉类型编码，如NO_SHOW、LOST_CONTACT
     * @return int 该类型对应的扣分分值，未知类型抛出BusinessException
     */
    public int penaltyFor(String type) {
        return switch (type == null ? "" : type) {
            case "NO_SHOW" -> PENALTY_NO_SHOW;
            case "LOST_CONTACT" -> PENALTY_LOST_CONTACT;
            case "MISBEHAVE" -> PENALTY_MISBEHAVE;
            case "EXPENSE_DISPUTE" -> PENALTY_EXPENSE;
            default -> throw new BusinessException("未知的投诉类型：" + type);
        };
    }

    /**
     * 门槛判定：能否发布/报名结伴（信誉分低于60时不可用）
     * @param u 待判定的用户
     * @return boolean true表示可以发布或报名结伴，false表示信誉分不足
     */
    public boolean canJoinTrip(User u) {
        return u.getCreditScore() != null && u.getCreditScore() >= TRIP_THRESHOLD;
    }

    /**
     * 门槛判定：能否发布学生业余导游计划（信誉分需达到80）
     * @param u 待判定的用户
     * @return boolean true表示可以发布导游计划，false表示信誉分不足
     */
    public boolean canPublishGuidePlan(User u) {
        return u.getCreditScore() != null && u.getCreditScore() >= GUIDE_THRESHOLD;
    }

    // ==================== 信誉画像 ====================

    /**
     * 信誉主页数据：分数、等级、徽章、统计、变动历史、涉诉记录
     * @param userId 用户ID
     * @return Map 信誉主页数据，含分数、等级、徽章与统计口径
     */
    public Map<String, Object> profile(Long userId) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        int score = u.getCreditScore() == null ? DEFAULT_SCORE : u.getCreditScore();

        // 完结行程数：作为队长或已通过成员、行程状态FINISHED
        int finishedAsCaptain = 0;
        for (Trip t : tripRepository.findByCaptainIdOrderByCreatedAtDesc(userId)) {
            if ("FINISHED".equals(t.getStatus())) {
                finishedAsCaptain++;
            }
        }
        int finishedAsMember = 0;
        List<Integer> myRates = new ArrayList<>();
        for (TripMember tm : memberRepository.findByUserId(userId)) {
            Trip t = tm.getTripId() == null ? null
                    : tripRepository.findById(tm.getTripId()).orElse(null);
            if (t != null && "FINISHED".equals(t.getStatus())
                    && "APPROVED".equals(tm.getStatus())) {
                finishedAsMember++;
            }
            if (tm.getScore() != null) {
                myRates.add(tm.getScore());
            }
        }
        int finishedTrips = finishedAsCaptain + finishedAsMember;
        double avgRate = myRates.isEmpty() ? 0
                : Math.round(myRates.stream().mapToInt(Integer::intValue).average().orElse(0) * 10) / 10.0;

        long complaintsTotal = complaintRepository.countByTargetId(userId);
        long complaintsPending = complaintRepository.countByTargetIdAndStatus(userId, "PENDING");
        long complaintsEstablished = complaintRepository.findByTargetIdOrderByCreatedAtDesc(userId).stream()
                .filter(c -> "RESOLVED".equals(c.getStatus()) || "APPEALED".equals(c.getStatus())
                        || ("FINAL".equals(c.getStatus()) && "UPHELD".equals(c.getFinalAction())))
                .count();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("score", score);
        m.put("level", levelOf(score));
        m.put("levelName", levelName(score));
        m.put("thresholds", Map.of(
                "trip", TRIP_THRESHOLD,
                "guidePlan", GUIDE_THRESHOLD,
                "canJoinTrip", score >= TRIP_THRESHOLD,
                "canPublishGuidePlan", score >= GUIDE_THRESHOLD));
        m.put("badges", badges(u, finishedTrips, finishedAsCaptain, avgRate, complaintsEstablished));
        m.put("stats", Map.of(
                "finishedTrips", finishedTrips,
                "finishedAsCaptain", finishedAsCaptain,
                "finishedAsMember", finishedAsMember,
                "avgRate", avgRate,
                "complaintsTotal", complaintsTotal,
                "complaintsPending", complaintsPending,
                "complaintsEstablished", complaintsEstablished));
        m.put("logs", creditLogRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .limit(20).map(l -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", l.getId());
                    row.put("delta", l.getDelta());
                    row.put("reason", l.getReason());
                    row.put("createdAt", l.getCreatedAt());
                    return row;
                }).toList());
        return m;
    }

    /**
     * 头像信息卡用的公开概要
     * @param userId 用户ID
     * @return Map 含nickname、avatar、school、score等公开字段
     */
    public Map<String, Object> brief(Long userId) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", u.getId());
        m.put("nickname", u.getNickname());
        m.put("avatar", u.getAvatar());
        m.put("school", u.getSchool());
        m.put("studentNo", maskStudentNo(u.getStudentNo()));
        m.put("verified", Boolean.TRUE.equals(u.getVerified()));
        m.put("score", u.getCreditScore() == null ? DEFAULT_SCORE : u.getCreditScore());
        m.put("level", levelOf(u.getCreditScore() == null ? DEFAULT_SCORE : u.getCreditScore()));
        m.put("levelName", levelName(u.getCreditScore() == null ? DEFAULT_SCORE : u.getCreditScore()));
        return m;
    }

    // ==================== 内部 ====================

    private String levelOf(int score) {
        if (score >= 110) return "EXCELLENT";
        if (score >= 90) return "GOOD";
        if (score >= 70) return "AVERAGE";
        if (score >= 60) return "RISKY";
        return "BLOCKED";
    }

    private String levelName(int score) {
        if (score >= 110) return "信誉标杆";
        if (score >= 90) return "信用良好";
        if (score >= 70) return "一般";
        if (score >= 60) return "需要积累";
        return "受限（不能结伴）";
    }

    /**
     * 徽章墙：把抽象分数变成一眼能懂的信誉资产
     * @param u 用户
     * @param finishedTrips 已完结的行程数（含队长与成员身份）
     * @param finishedAsCaptain 作为队长完结的行程数
     * @param avgRate 队友互评均分
     * @param established 被核实的投诉数
     * @return List 徽章列表，含名称、说明与是否获得
     */
    private List<Map<String, Object>> badges(User u, int finishedTrips, int finishedAsCaptain,
                                             double avgRate, long established) {
        List<Map<String, Object>> list = new ArrayList<>();
        int score = u.getCreditScore() == null ? DEFAULT_SCORE : u.getCreditScore();
        addBadge(list, "首次出行", finishedTrips >= 1, "完成第1次结伴行程");
        addBadge(list, "结伴达人", finishedTrips >= 3, "完成3次及以上结伴行程");
        addBadge(list, "靠谱队长", finishedAsCaptain >= 2, "作为队长完成2次及以上行程");
        addBadge(list, "好评如潮", avgRate >= 4.5 && finishedTrips >= 1, "队友互评均分 ≥ 4.5");
        addBadge(list, "零爽约", established == 0 && finishedTrips >= 1, "无被核实的爽约/投诉记录");
        addBadge(list, "守信结算", score > 0 && finishedTrips > 0, "参与行程账目按时结清");
        addBadge(list, "信誉标杆", score >= 110, "信誉分达到110及以上");
        return list;
    }

    private void addBadge(List<Map<String, Object>> list, String name, boolean earned, String desc) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("name", name);
        b.put("desc", desc);
        b.put("earned", earned);
        list.add(b);
    }

    /**
     * 学号脱敏：仅保留前两位与后两位
     * @param no 学号
     * @return String 脱敏后的学号，长度不超过4位时原样返回
     */
    private String maskStudentNo(String no) {
        if (no == null || no.length() <= 4) {
            return no;
        }
        return no.substring(0, 2) + "****" + no.substring(no.length() - 2);
    }

    /**
     * 投诉类型的展示名
     * @param type 投诉类型编码
     * @return String 中文展示名，未知类型原样返回
     */
    public static String complaintTypeName(String type) {
        return switch (type == null ? "" : type) {
            case "NO_SHOW" -> "爽约";
            case "LOST_CONTACT" -> "行程中失联";
            case "MISBEHAVE" -> "言行不当";
            case "EXPENSE_DISPUTE" -> "费用不清";
            default -> type;
        };
    }

    /**
     * 判断是否为某行程的有效参与者（队长或已通过成员）
     * @param t 行程
     * @param userId 用户ID
     * @param memberRepository 成员仓库，用于查报名记录
     * @return boolean true表示队长或已通过的成员，false表示不是有效参与者
     */
    public boolean isParticipant(Trip t, Long userId, TripMemberRepository memberRepository) {
        if (Objects.equals(t.getCaptainId(), userId)) {
            return true;
        }
        return memberRepository.findByTripIdAndUserId(t.getId(), userId)
                .filter(mm -> "APPROVED".equals(mm.getStatus())).isPresent();
    }
}
