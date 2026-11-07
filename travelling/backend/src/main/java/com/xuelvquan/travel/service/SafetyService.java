package com.xuelvquan.travel.service;

import com.xuelvquan.travel.common.BusinessException;
import com.xuelvquan.travel.entity.*;
import com.xuelvquan.travel.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 安全机制服务：家校知情通知与位置共享授权。
 * 出行前由队长向全队成员的紧急联系人报备；队员可限时授权紧急联系人查看实时位置。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SafetyService {

    /** 位置共享最长授权时长（小时） */
    public static final int MAX_SHARE_HOURS = 72;
    /** 默认授权时长（小时） */
    public static final int DEFAULT_SHARE_HOURS = 24;
    /** 位置超过该小时数未更新即视为「可能已过期」 */
    private static final int STALE_HOURS = 2;

    private static final Map<String, String> SCHOOL_NAMES = Map.of(
            "CSU", "中南大学",
            "HUNNU", "湖南师范大学",
            "HNU", "湖南大学");

    private static final Map<String, String> TRANSPORT_NAMES = Map.of(
            "TRAIN", "高铁/火车",
            "BUS", "长途大巴",
            "CAR", "自驾/租车",
            "CARPOOL", "拼车/顺风车",
            "BIKE", "骑行/徒步");

    private final TripRepository tripRepository;
    private final TripMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final EmergencyContactRepository contactRepository;
    private final LocationShareRepository shareRepository;
    private final SafetyNoticeRepository noticeRepository;
    private final NotifyService notifyService;


    /**
     * 出行前家校知情通知：队长一键通知全队已审核成员的家长/辅导员。
     * @param tripId 行程ID
     * @param operatorId 操作人ID，必须是该行程的队长
     * @return Map 含通知条数、通知明细与未通知成功的成员
     */
    public Map<String, Object> notifyDeparture(Long tripId, Long operatorId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException("行程不存在"));
        if (!trip.getCaptainId().equals(operatorId)) {
            throw new BusinessException(403, "仅队长可发起出行前家校知情通知");
        }

        List<TripMember> members = new ArrayList<>(memberRepository.findByTripIdAndStatus(tripId, "APPROVED"));
        // 队长本人也算队伍成员
        if (members.stream().noneMatch(m -> Objects.equals(m.getUserId(), trip.getCaptainId()))) {
            TripMember captainAsMember = new TripMember();
            captainAsMember.setTripId(tripId);
            captainAsMember.setUserId(trip.getCaptainId());
            captainAsMember.setStatus("APPROVED");
            members.add(captainAsMember);
        }

        List<Map<String, Object>> notices = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();
        String departText = trip.getDepartAt() == null ? "待定"
                : trip.getDepartAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

        for (TripMember m : members) {
            User u = userRepository.findById(m.getUserId()).orElse(null);
            if (u == null) {
                continue;
            }
            List<EmergencyContact> authorized = contactRepository.findByUserId(u.getId()).stream()
                    .filter(c -> Boolean.TRUE.equals(c.getAuthorized()))
                    .toList();
            if (authorized.isEmpty()) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("memberUserId", u.getId());
                s.put("memberName", u.getNickname());
                s.put("reason", "未设置已授权的紧急联系人（家长 / 辅导员）");
                skipped.add(s);
                continue;
            }
            for (EmergencyContact c : authorized) {
                SafetyNotice n = new SafetyNotice();
                n.setTripId(trip.getId());
                n.setTripTitle(trip.getTitle());
                n.setDestination(trip.getDestination());
                n.setDepartAt(trip.getDepartAt());
                n.setMemberUserId(u.getId());
                n.setMemberName(u.getNickname());
                n.setMemberSchool(schoolName(u.getSchool()));
                n.setContactId(c.getId());
                n.setContactName(c.getName());
                n.setRelation(c.getRelation());
                n.setPhone(c.getPhone());
                n.setNoticeType(trip.getDepartAt() != null
                        && trip.getDepartAt().isAfter(LocalDateTime.now()) ? "TRIP_REPORT" : "DEPART_REMIND");
                n.setContent(buildNoticeContent(trip, u, c, departText));
                n.setStatus("SENT");
                n.setOperatorId(operatorId);
                n.setCreatedAt(LocalDateTime.now());
                SafetyNotice saved = noticeRepository.save(n);
                notices.add(noticeView(saved));
            }
            // 给成员本身也留一条站内提醒，做到「家校共同知情」
            notifyService.notify(u.getId(), "TRIP", "已完成出行前家校知情通知",
                    "你的家长 / 辅导员已收到《" + trip.getTitle() + "》的行前知情通知，请按时守约、注意安全。",
                    trip.getId());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tripId", trip.getId());
        result.put("tripTitle", trip.getTitle());
        result.put("memberCount", members.size());
        result.put("noticeCount", notices.size());
        result.put("notices", notices);
        result.put("skipped", skipped);
        result.put("notifiedAt", LocalDateTime.now());
        return result;
    }

    /**
     * 某行程的家校知情通知记录（队长/成员可查看）
     * @param tripId 行程ID
     * @param viewerId 查看者ID，须为队长或已报名成员
     * @return List 通知记录，按创建时间倒序
     */
    public List<Map<String, Object>> noticesOfTrip(Long tripId, Long viewerId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException("行程不存在"));
        boolean isMember = trip.getCaptainId().equals(viewerId)
                || memberRepository.findByTripIdAndUserId(tripId, viewerId).isPresent();
        if (!isMember) {
            throw new BusinessException(403, "仅行程成员可查看家校知情记录");
        }
        List<Map<String, Object>> list = new ArrayList<>();
        noticeRepository.findByTripIdOrderByCreatedAtDesc(tripId).forEach(n -> list.add(noticeView(n)));
        return list;
    }

    /**
     * 我（作为学生）收到的家校知情通知
     * @param userId 用户ID
     * @return List 我收到的通知记录，按创建时间倒序
     */
    public List<Map<String, Object>> myNotices(Long userId) {
        List<Map<String, Object>> list = new ArrayList<>();
        noticeRepository.findByMemberUserIdOrderByCreatedAtDesc(userId).forEach(n -> list.add(noticeView(n)));
        return list;
    }

    private String buildNoticeContent(Trip trip, User member, EmergencyContact contact, String departText) {
        return "【学旅圈 · 家校知情】" + safe(contact.getName()) + "（" + relationName(contact.getRelation()) + "）您好："
                +"您的孩子 / 学生" + safe(member.getNickname())
                + (member.getSchool() == null ? "" : "（" + schoolName(member.getSchool()) + "）")
                + " 已报名参加结伴出行《" + safe(trip.getTitle()) + "》。\n"
                +"目的地：" + safe(trip.getDestination()) + "；集合时间：" + departText
                + "；集合地点：" + safe(trip.getMeetPoint()) + "；出行方式：" + transportName(trip.getTransport())
                + "；队伍上限：" + (trip.getMaxMembers() == null ? "-" : trip.getMaxMembers()) + " 人。\n"
                +"该行程已在平台完成报备，队员均通过三校实名认证。如需了解实时位置，可在学生授权后通过其分享的链接查看。"
                +"祝旅途平安 —— 学旅圈安全中心";
    }

    // ==================================================================
    // 二、位置共享（限时授权给紧急联系人，家长凭链接查看）
    // ==================================================================

    /**
     * 队员创建（或续期）一条位置共享授权
     * @param userId 发起共享的队员ID
     * @param tripId 行程ID
     * @param contactId 紧急联系人ID，必须是本人已授权查看位置的联系人
     * @param hours 有效小时数，为空时用默认值
     * @param includeTeammates 是否同时向队友展示位置，为空时按true处理
     * @param note 备注，可为null
     * @return Map 授权详情，含查看链接与有效期
     */
    public Map<String, Object> createShare(Long userId, Long tripId, Long contactId,
                                           Integer hours, Boolean includeTeammates, String note) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException("行程不存在"));
        TripMember member = memberRepository.findByTripIdAndUserId(tripId, userId).orElse(null);
        boolean joined = (member != null && "APPROVED".equals(member.getStatus()))
                || trip.getCaptainId().equals(userId);
        if (!joined) {
            throw new BusinessException("你尚未加入该行程，无法共享位置");
        }
        EmergencyContact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new BusinessException("紧急联系人不存在"));
        if (!contact.getUserId().equals(userId)) {
            throw new BusinessException(403, "只能向自己的紧急联系人共享位置");
        }
        if (!Boolean.TRUE.equals(contact.getAuthorized())) {
            throw new BusinessException("请先授权该联系人「查看实时位置」，再生成共享链接");
        }

        int h = hours == null ? DEFAULT_SHARE_HOURS : Math.max(1, Math.min(MAX_SHARE_HOURS, hours));
        LocalDateTime now = LocalDateTime.now();

        // 同一「行程×联系人」已有生效授权时先撤销，避免多条链接同时可查
        for (LocationShare old : shareRepository.findByTripIdAndUserIdOrderByCreatedAtDesc(tripId, userId)) {
            if (Objects.equals(old.getContactId(), contactId) && "ACTIVE".equals(old.getStatus())) {
                old.setStatus("REVOKED");
                shareRepository.save(old);
            }
        }

        LocationShare share = new LocationShare();
        share.setTripId(tripId);
        share.setUserId(userId);
        share.setContactId(contact.getId());
        share.setContactName(contact.getName());
        share.setRelation(contact.getRelation());
        share.setPhone(contact.getPhone());
        share.setViewToken(UUID.randomUUID().toString().replace("-", ""));
        share.setStartAt(now);
        share.setEndAt(now.plusHours(h));
        share.setStatus("ACTIVE");
        share.setIncludeTeammates(includeTeammates == null || includeTeammates);
        share.setNote(note);
        share.setCreatedAt(now);
        share.setViewCount(0);
        LocationShare saved = shareRepository.save(share);

        notifyService.notify(userId, "TRIP", "已完成位置共享授权",
                "已授权「" + contact.getName() + "（" + relationName(contact.getRelation()) + "）」查看你在《"
                        + trip.getTitle() + "》期间的位置，有效期" + h + " 小时，可随时撤销。", tripId);

        return shareView(saved, trip, true);
    }

    /**
     * 我的位置共享授权列表（可按行程过滤）
     * @param userId 用户ID
     * @param tripId 行程ID，为null时不按行程过滤
     * @return List 授权列表，按创建时间倒序
     */
    public List<Map<String, Object>> myShares(Long userId, Long tripId) {
        refreshExpired();
        List<LocationShare> shares = tripId == null
                ? shareRepository.findByUserIdOrderByCreatedAtDesc(userId)
                : shareRepository.findByTripIdAndUserIdOrderByCreatedAtDesc(tripId, userId);
        List<Map<String, Object>> list = new ArrayList<>();
        for (LocationShare s : shares) {
            Trip trip = tripRepository.findById(s.getTripId()).orElse(null);
            list.add(shareView(s, trip, true));
        }
        return list;
    }

    /**
     * 撤销某条位置共享授权
     * @param shareId 授权记录ID
     * @param userId 撤销人ID，须为授权发起人本人
     * @return Map 撤销后的授权详情
     */
    public Map<String, Object> revoke(Long shareId, Long userId) {
        LocationShare share = shareRepository.findById(shareId)
                .orElseThrow(() -> new BusinessException("授权记录不存在"));
        if (!share.getUserId().equals(userId)) {
            throw new BusinessException(403, "只能撤销自己的位置共享授权");
        }
        share.setStatus("REVOKED");
        shareRepository.save(share);
        Trip trip = tripRepository.findById(share.getTripId()).orElse(null);
        return shareView(share, trip, true);
    }

    /**
     * 家长/辅导员查看页数据源（公开接口，凭viewToken访问，无需登录）。
     * 严格校验：令牌有效、授权未撤销、未过期。
     * @param token 授权链接中的查看令牌
     * @return Map 被授权人的行程与位置信息，令牌无效、已撤销或已过期时抛出BusinessException
     */
    public Map<String, Object> track(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException("授权链接无效");
        }
        LocationShare share = shareRepository.findByViewToken(token.trim())
                .orElseThrow(() -> new BusinessException(404, "授权链接无效或已被删除"));

        if ("REVOKED".equals(share.getStatus())) {
            throw new BusinessException(410, "该位置共享授权已被撤销");
        }
        LocalDateTime now = LocalDateTime.now();
        if (share.getEndAt() != null && share.getEndAt().isBefore(now)) {
            if (!"EXPIRED".equals(share.getStatus())) {
                share.setStatus("EXPIRED");
                shareRepository.save(share);
            }
            throw new BusinessException(410, "该位置共享授权已过期");
        }

        share.setLastViewedAt(now);
        share.setViewCount((share.getViewCount() == null ? 0 : share.getViewCount()) + 1);
        shareRepository.save(share);

        User member = userRepository.findById(share.getUserId()).orElse(null);
        Trip trip = tripRepository.findById(share.getTripId()).orElse(null);
        TripMember me = memberRepository.findByTripIdAndUserId(share.getTripId(), share.getUserId()).orElse(null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("active", true);
        result.put("viewCount", share.getViewCount());
        result.put("viewedAt", now);

        Map<String, Object> memberInfo = new LinkedHashMap<>();
        memberInfo.put("nickname", member == null ? "同学" : member.getNickname());
        memberInfo.put("school", member == null ? null : schoolName(member.getSchool()));
        memberInfo.put("avatar", member == null ? null : member.getAvatar());
        memberInfo.put("verified", member != null && Boolean.TRUE.equals(member.getVerified()));
        result.put("member", memberInfo);

        if (trip != null) {
            Map<String, Object> tripInfo = new LinkedHashMap<>();
            tripInfo.put("id", trip.getId());
            tripInfo.put("title", trip.getTitle());
            tripInfo.put("destination", trip.getDestination());
            tripInfo.put("departAt", trip.getDepartAt());
            tripInfo.put("meetPoint", trip.getMeetPoint());
            tripInfo.put("transport", trip.getTransport());
            tripInfo.put("status", trip.getStatus());
            tripInfo.put("riskLevel", trip.getRiskLevel());
            result.put("trip", tripInfo);
        }

        // 本人最新位置
        Map<String, Object> location = new LinkedHashMap<>();
        if (me != null && me.getLng() != null && me.getLat() != null) {
            location.put("available", true);
            location.put("lng", me.getLng());
            location.put("lat", me.getLat());
            location.put("updatedAt", me.getLocationAt());
            long mins = me.getLocationAt() == null ? -1
                    : Duration.between(me.getLocationAt(), now).toMinutes();
            location.put("minutesAgo", mins);
            location.put("stale", mins < 0 || mins > STALE_HOURS * 60L);
        } else {
            location.put("available", false);
        }
        result.put("location", location);

        // 同行队友（脱敏，仅给大致位置，便于家长判断是否离队）
        List<Map<String, Object>> teammates = new ArrayList<>();
        if (Boolean.TRUE.equals(share.getIncludeTeammates()) && trip != null) {
            for (TripMember tm : memberRepository.findByTripIdAndStatus(share.getTripId(), "APPROVED")) {
                if (Objects.equals(tm.getUserId(), share.getUserId())) {
                    continue;
                }
                if (tm.getLng() == null || tm.getLat() == null) {
                    continue;
                }
                User tu = userRepository.findById(tm.getUserId()).orElse(null);
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("name", maskName(tu == null ? null : tu.getNickname()));
                t.put("lng", tm.getLng());
                t.put("lat", tm.getLat());
                t.put("updatedAt", tm.getLocationAt());
                teammates.add(t);
            }
        }
        result.put("teammates", teammates);

        // 授权信息
        Map<String, Object> auth = new LinkedHashMap<>();
        auth.put("contactName", share.getContactName());
        auth.put("relation", share.getRelation());
        auth.put("relationName", relationName(share.getRelation()));
        auth.put("startAt", share.getStartAt());
        auth.put("endAt", share.getEndAt());
        auth.put("remainMinutes", share.getEndAt() == null ? 0
                : Math.max(0, Duration.between(now, share.getEndAt()).toMinutes()));
        auth.put("note", share.getNote());
        result.put("auth", auth);

        // 该学生的家校知情通知摘要
        List<Map<String, Object>> noticeList = new ArrayList<>();
        for (SafetyNotice n : noticeRepository.findByMemberUserIdOrderByCreatedAtDesc(share.getUserId())) {
            if (Objects.equals(n.getTripId(), share.getTripId())) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("noticeType", n.getNoticeType());
                m.put("createdAt", n.getCreatedAt());
                m.put("content", n.getContent());
                noticeList.add(m);
            }
        }
        result.put("notices", noticeList);

        List<String> tips = new ArrayList<>();
        tips.add("本次位置共享由学生本人限时授权，仅限紧急联系人查看，到期自动失效。");
        if (!teammates.isEmpty()) {
            tips.add("同行队友位置仅显示大致坐标与脱敏称呼，用于判断是否离队。");
        }
        if (location.containsKey("stale") && Boolean.TRUE.equals(location.get("stale"))) {
            tips.add("该位置已超过" + STALE_HOURS + " 小时未更新，可能不是实时位置，建议电话确认。");
        }
        tips.add("如遇紧急情况请直接联系学生本人或拨打110 / 120。");
        result.put("tips", tips);

        return result;
    }


    /** 把已过期但状态仍为ACTIVE的授权置为EXPIRED */
    private void refreshExpired() {
        LocalDateTime now = LocalDateTime.now();
        for (LocationShare s : shareRepository.findAll()) {
            if ("ACTIVE".equals(s.getStatus()) && s.getEndAt() != null && s.getEndAt().isBefore(now)) {
                s.setStatus("EXPIRED");
                shareRepository.save(s);
            }
        }
    }

    private Map<String, Object> shareView(LocationShare s, Trip trip, boolean withUrl) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("tripId", s.getTripId());
        m.put("tripTitle", trip == null ? null : trip.getTitle());
        m.put("destination", trip == null ? null : trip.getDestination());
        m.put("contactId", s.getContactId());
        m.put("contactName", s.getContactName());
        m.put("relation", s.getRelation());
        m.put("relationName", relationName(s.getRelation()));
        m.put("phone", s.getPhone());
        m.put("status", s.getStatus());
        m.put("startAt", s.getStartAt());
        m.put("endAt", s.getEndAt());
        m.put("includeTeammates", s.getIncludeTeammates());
        m.put("note", s.getNote());
        m.put("viewCount", s.getViewCount());
        m.put("lastViewedAt", s.getLastViewedAt());
        m.put("createdAt", s.getCreatedAt());
        if (s.getEndAt() != null && "ACTIVE".equals(s.getStatus())) {
            m.put("remainMinutes", Math.max(0, Duration.between(LocalDateTime.now(), s.getEndAt()).toMinutes()));
        }
        if (withUrl && s.getViewToken() != null) {
            m.put("shareUrl", "/travelling/track/" + s.getViewToken());
            m.put("trackPath", "/track/" + s.getViewToken());
        }
        return m;
    }

    private Map<String, Object> noticeView(SafetyNotice n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("tripId", n.getTripId());
        m.put("tripTitle", n.getTripTitle());
        m.put("destination", n.getDestination());
        m.put("departAt", n.getDepartAt());
        m.put("memberUserId", n.getMemberUserId());
        m.put("memberName", n.getMemberName());
        m.put("memberSchool", n.getMemberSchool());
        m.put("contactName", n.getContactName());
        m.put("relation", n.getRelation());
        m.put("relationName", relationName(n.getRelation()));
        m.put("phone", n.getPhone());
        m.put("noticeType", n.getNoticeType());
        m.put("content", n.getContent());
        m.put("status", n.getStatus());
        m.put("createdAt", n.getCreatedAt());
        return m;
    }

    public static String relationName(String relation) {
        if ("COUNSELOR".equals(relation)) {
            return "辅导员";
        }
        if ("PARENT".equals(relation)) {
            return "家长";
        }
        return relation == null ? "联系人" : relation;
    }

    private String schoolName(String code) {
        if (code == null) {
            return null;
        }
        return SCHOOL_NAMES.getOrDefault(code, code);
    }

    private String transportName(String code) {
        if (code == null) {
            return "-";
        }
        return TRANSPORT_NAMES.getOrDefault(code, code);
    }

    /**
     * 队友昵称脱敏：保留首字，其余用*代替
     * @param name 队友昵称
     * @return String 脱敏后的名称，为空时返回「队友」
     */
    private String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return "队友";
        }
        if (name.length() == 1) {
            return name;
        }
        return name.charAt(0) + "*".repeat(Math.min(name.length() - 1, 3));
    }

    private String safe(String v) {
        return v == null ? "-" : v;
    }
}
