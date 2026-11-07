package com.xuelvquan.travel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xuelvquan.travel.entity.PeopleMatch;
import com.xuelvquan.travel.entity.PeoplePref;
import com.xuelvquan.travel.entity.TripMember;
import com.xuelvquan.travel.entity.User;
import com.xuelvquan.travel.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 人→人契合度推荐服务：基于双方档案标签与社交关系做五维打分。
 * 五维权重：兴趣0.30、出行节奏0.25、预算档0.15、目的地偏好0.15、半熟人度0.15。
 */
@Service
@RequiredArgsConstructor
public class PeopleMatchService {

    private static final int W_INTEREST = 30;
    private static final int W_PACE = 25;
    private static final int W_BUDGET = 15;
    private static final int W_DESTINATION = 15;
    private static final int W_ACQUAINTANCE = 15;

    /** 半熟人阈值：低于此值视为陌生人，默认不进推荐池 */
    public static final int ACQUAINTANCE_THRESHOLD = 70;

    /** 预算档位顺序，用于算"相邻/跨档" */
    private static final List<String> BUDGET_ORDER = List.of("ECONOMY", "MODERATE", "COMFORT", "PREMIUM");
    private static final Map<String, String> BUDGET_LABEL = Map.of(
            "ECONOMY", "经济 <300", "MODERATE", "适中300–800",
            "COMFORT", "舒适800–1500", "PREMIUM", "品质 >1500");
    private static final Map<String, String> PACE_LABEL = Map.of(
            "ENERGETIC", "精力满满", "BALANCED", "节奏适中", "RELAXED", "纯休闲放松");

    /** 可进入撮合池的角色 */
    private static final List<String> POOL_ROLES = List.of("USER", "BLOGGER", "GUIDE");

    private final UserRepository userRepository;
    private final PeopleMatchRepository peopleMatchRepository;
    private final PeoplePrefRepository peoplePrefRepository;
    private final CircleMemberRepository circleMemberRepository;
    private final TripMemberRepository tripMemberRepository;
    private final TagDictRepository tagDictRepository;
    private final ObjectMapper objectMapper;

    /** dimension:code -> 展示名（字典缓存，懒加载一次） */
    private volatile Map<String, String> labelCache;

    public PeoplePref resolvePref(Long userId) {
        return peoplePrefRepository.findByUserId(userId).orElseGet(() -> {
            PeoplePref p = new PeoplePref();
            p.setUserId(userId);
            p.setOpenToStranger(false);
            p.setCrossSchool(true);
            return p;
        });
    }

    public PeoplePref savePref(Long userId, Map<String, Object> body) {
        PeoplePref p = peoplePrefRepository.findByUserId(userId).orElseGet(() -> {
            PeoplePref x = new PeoplePref();
            x.setUserId(userId);
            return x;
        });
        if (body.containsKey("openToStranger")) {
            p.setOpenToStranger(Boolean.TRUE.equals(body.get("openToStranger"))
                    || "true".equals(String.valueOf(body.get("openToStranger"))));
        }
        if (body.containsKey("crossSchool")) {
            p.setCrossSchool(Boolean.TRUE.equals(body.get("crossSchool"))
                    || "true".equals(String.valueOf(body.get("crossSchool"))));
        }
        p.setUpdatedAt(LocalDateTime.now());
        return peoplePrefRepository.save(p);
    }

    // ============================================================
    // 核心：两人契合度
    // ============================================================

    /**
     * 计算「我×他」的契合度明细。
     *
     * @param me 当前用户
     * @param target 对方用户
     * @return { targetId, matchScore, recommendScore, acquaintance, dims[], target }，始终非null
     */
    public Map<String, Object> matchOf(User me, User target) {
        Map<String, Set<String>> mt = parseProfileTags(me.getProfileTags());
        Map<String, Set<String>> tt = parseProfileTags(target.getProfileTags());
        List<Map<String, Object>> dims = new ArrayList<>();

        // 1) 兴趣主题0.30 —— Jaccard相似度
        Set<String> mi = mt.getOrDefault("interest", Set.of());
        Set<String> ti = tt.getOrDefault("interest", Set.of());
        int interestScore;
        String interestNote;
        if (mi.isEmpty() || ti.isEmpty()) {
            interestScore = 60;
            interestNote = mi.isEmpty() ? "你还没设置兴趣标签" : "对方还未设置兴趣标签";
        } else {
            Set<String> inter = new LinkedHashSet<>(mi);
            inter.retainAll(ti);
            Set<String> union = new LinkedHashSet<>(mi);
            union.addAll(ti);
            interestScore = (int) Math.round(inter.size() * 100.0 / union.size());
            interestNote = inter.isEmpty()
                    ? "暂无共同兴趣"
                    : "共同兴趣" + inter.size() + " 项";
        }
        dims.add(dim("interest", "兴趣主题", interestScore, W_INTEREST, interestNote,
                sharedLabels("interest", mi, ti)));

        // 2) 出行节奏0.25
        String mp = first(mt.get("pace"), "BALANCED");
        String tp = first(tt.get("pace"), "BALANCED");
        int paceScore;
        if (mp.equals(tp)) {
            paceScore = 100;
        } else if ("BALANCED".equals(mp) || "BALANCED".equals(tp)) {
            paceScore = 70;
        } else {
            paceScore = 30;
        }
        dims.add(dim("pace", "出行节奏", paceScore, W_PACE,
                "你" + paceLabel(mp) + " · 对方" + paceLabel(tp), List.of()));

        // 3) 预算档0.15
        String mb = first(mt.get("budget"), "MODERATE");
        String tb = first(tt.get("budget"), "MODERATE");
        int bi = BUDGET_ORDER.indexOf(mb);
        int bj = BUDGET_ORDER.indexOf(tb);
        int dist = (bi < 0 || bj < 0) ? 1 : Math.abs(bi - bj);
        int budgetScore = switch (dist) {
            case 0 -> 100;
            case 1 -> 60;
            case 2 -> 25;
            default -> 10;
        };
        dims.add(dim("budget", "预算档", budgetScore, W_BUDGET,
                dist == 0 ? "预算档一致（" + budgetLabel(mb) + "）"
                        : "你" + budgetLabel(mb) + " · 对方" + budgetLabel(tb), List.of()));

        // 4) 目的地偏好0.15
        Set<String> md = mt.getOrDefault("destination", Set.of());
        Set<String> td = tt.getOrDefault("destination", Set.of());
        int destScore;
        String destNote;
        if (md.isEmpty() || td.isEmpty()) {
            destScore = 60;
            destNote = "目的地偏好信息不完整";
        } else {
            Set<String> inter = new LinkedHashSet<>(md);
            inter.retainAll(td);
            if (inter.isEmpty()) {
                destScore = 35;
                destNote = "偏好的旅行地类型不同";
            } else {
                destScore = 100;
                List<String> names = new ArrayList<>();
                for (String c : inter) {
                    names.add(labelOf("destination", c));
                }
                destNote = "都想去：" + String.join("/", names);
            }
        }
        dims.add(dim("destination", "目的地偏好", destScore, W_DESTINATION, destNote, List.of()));

        // 5) 半熟人度0.15
        Acquaintance acq = acquaintance(me, target);
        dims.add(dim("acquaintance", "半熟人度", acq.score, W_ACQUAINTANCE, acq.note, List.of()));

        int matchScore = 0;
        for (Map<String, Object> d : dims) {
            matchScore += (int) d.get("score") * (int) d.get("weight") / 100;
        }
        matchScore = clamp(matchScore);

        int credit = target.getCreditScore() == null ? 100 : target.getCreditScore();
        int recommendScore = clamp((int) Math.round(matchScore * 0.85 + credit * 0.15));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("targetId", target.getId());
        m.put("matchScore", matchScore);
        m.put("recommendScore", recommendScore);
        m.put("acquaintance", acq.score);
        m.put("dims", dims);
        m.put("target", targetView(target));
        return m;
    }

    // ============================================================
    // 半熟人度
    // ============================================================

    /** 半熟人度结果 */
    public record Acquaintance(int score, String note, List<String> reasons) {
    }

    /**
     * 半熟人度：基线40，逐项叠加，封顶100。
     * 同校+30、同学院+15、同届+10、同圈子+10、曾同队+10；低于阈值视为陌生人。
     *
     * @param me 当前用户
     * @param target 候选用户
     * @return Acquaintance 含分数、说明与叠加原因
     */
    public Acquaintance acquaintance(User me, User target) {
        int score = 40;
        List<String> reasons = new ArrayList<>();

        if (notBlank(me.getSchool()) && me.getSchool().equals(target.getSchool())) {
            score += 30;
            reasons.add("同校");
        }
        if (notBlank(me.getCollege()) && me.getCollege().equals(target.getCollege())
                && notBlank(me.getSchool()) && me.getSchool().equals(target.getSchool())) {
            score += 15;
            reasons.add("同学院");
        }
        if (notBlank(me.getGrade()) && me.getGrade().equals(target.getGrade())) {
            score += 10;
            reasons.add("同届");
        }
        if (shareCircle(me.getId(), target.getId())) {
            score += 10;
            reasons.add("同圈子");
        }
        if (everTeammate(me.getId(), target.getId())) {
            score += 10;
            reasons.add("曾同队");
        }
        score = clamp(score);

        String note;
        if (reasons.isEmpty()) {
            note = "纯陌生人（无共同身份联系）";
        } else if (score >= ACQUAINTANCE_THRESHOLD) {
            note = String.join(" · ", reasons);
        } else {
            note = String.join(" · ", reasons) + "（仍未达半熟人线）";
        }
        return new Acquaintance(score, note, reasons);
    }

    private boolean shareCircle(Long a, Long b) {
        Set<Long> mine = new HashSet<>();
        circleMemberRepository.findByUserId(a).forEach(m -> mine.add(m.getCircleId()));
        if (mine.isEmpty()) {
            return false;
        }
        for (var m : circleMemberRepository.findByUserId(b)) {
            if (mine.contains(m.getCircleId())) {
                return true;
            }
        }
        return false;
    }

    private boolean everTeammate(Long a, Long b) {
        Set<Long> mine = new HashSet<>();
        tripMemberRepository.findByUserId(a).forEach(m -> mine.add(m.getTripId()));
        if (mine.isEmpty()) {
            return false;
        }
        for (TripMember m : tripMemberRepository.findByUserId(b)) {
            if (mine.contains(m.getTripId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Top N契合度推荐。
     *
     * 候选池=已认证的在校学生，且档案标签填满4个核心维度（未填满不进池）。
     * 过滤规则：排除自己；半熟人度不足且我不接受陌生人时排除；不接受跨校时排除外校。
     * @param userId 用户ID
     * @param limit 返回条数上限
     * @return List 推荐列表，含匹配明细，按推荐分倒序
     */
    public List<Map<String, Object>> recommend(Long userId, int limit) {
        User me = userRepository.findById(userId).orElse(null);
        if (me == null) {
            return List.of();
        }
        PeoplePref pref = resolvePref(userId);
        List<Map<String, Object>> scored = new ArrayList<>();
        for (User u : userRepository.findByVerifiedTrueAndRoleIn(POOL_ROLES)) {
            if (u.getId().equals(userId)) {
                continue;
            }
            if (Boolean.TRUE.equals(u.getBanned())) {
                continue;
            }
            if (!inPool(u)) {
                continue; // 未填满核心标签，不进推荐池
            }
            if (!Boolean.TRUE.equals(pref.getCrossSchool())
                    && notBlank(me.getSchool()) && !me.getSchool().equals(u.getSchool())) {
                continue;
            }
            if (!Boolean.TRUE.equals(pref.getOpenToStranger())
                    && acquaintance(me, u).score() < ACQUAINTANCE_THRESHOLD) {
                continue;
            }
            scored.add(matchOf(me, u));
        }
        scored.sort((a, b) -> (int) b.get("recommendScore") - (int) a.get("recommendScore"));
        List<Map<String, Object>> top = scored.size() > limit ? scored.subList(0, limit) : scored;
        for (Map<String, Object> m : top) {
            saveSnapshot(userId, (Long) m.get("targetId"), m, "RECOMMEND");
        }
        return top;
    }

    /**
     * 单人明细+落库快照（source=VIEW）
     * @param userId 当前用户ID
     * @param targetId 对方用户ID
     * @return Map 两人契合度明细，任一用户不存在时返回null
     */
    public Map<String, Object> detail(Long userId, Long targetId) {
        User me = userRepository.findById(userId).orElse(null);
        User target = userRepository.findById(targetId).orElse(null);
        if (me == null || target == null) {
            return null;
        }
        Map<String, Object> m = matchOf(me, target);
        saveSnapshot(userId, targetId, m, "VIEW");
        return m;
    }

    /**
     * 是否已填满4个核心维度（进池门槛）
     * @param u 待判定的用户
     * @return boolean true表示节奏、兴趣、预算、目的地四个维度都已填写，false表示还有缺失
     */
    public boolean inPool(User u) {
        Map<String, Set<String>> t = parseProfileTags(u.getProfileTags());
        return !t.getOrDefault("pace", Set.of()).isEmpty()
                && !t.getOrDefault("interest", Set.of()).isEmpty()
                && !t.getOrDefault("budget", Set.of()).isEmpty()
                && !t.getOrDefault("destination", Set.of()).isEmpty();
    }

    /**
     * 我的档案是否已填满核心标签（前端用来提示"填完才进推荐池"）
     * @param userId 用户ID
     * @return Map 含是否进池与缺失的维度列表
     */
    public Map<String, Object> poolStatus(Long userId) {
        User me = userRepository.findById(userId).orElse(null);
        Map<String, Set<String>> t = me == null ? Map.of() : parseProfileTags(me.getProfileTags());
        List<String> missing = new ArrayList<>();
        if (t.getOrDefault("pace", Set.of()).isEmpty()) {
            missing.add("pace");
        }
        if (t.getOrDefault("interest", Set.of()).isEmpty()) {
            missing.add("interest");
        }
        if (t.getOrDefault("budget", Set.of()).isEmpty()) {
            missing.add("budget");
        }
        if (t.getOrDefault("destination", Set.of()).isEmpty()) {
            missing.add("destination");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("inPool", missing.isEmpty());
        m.put("missingDimensions", missing);
        return m;
    }

    private void saveSnapshot(Long userId, Long targetId, Map<String, Object> detail, String source) {
        PeopleMatch pm = peopleMatchRepository.findByUserIdAndTargetId(userId, targetId)
                .orElseGet(() -> {
                    PeopleMatch x = new PeopleMatch();
                    x.setUserId(userId);
                    x.setTargetId(targetId);
                    return x;
                });
        pm.setMatchScore((int) detail.get("matchScore"));
        pm.setRecommendScore((int) detail.get("recommendScore"));
        pm.setAcquaintance((int) detail.get("acquaintance"));
        try {
            pm.setDimsJson(objectMapper.writeValueAsString(detail.get("dims")));
        } catch (Exception ignored) {
            pm.setDimsJson(null);
        }
        pm.setSource(source);
        pm.setCreatedAt(LocalDateTime.now());
        peopleMatchRepository.save(pm);
    }

    /**
     * 解析dimension:code逗号串为Map<dimension, Set<code>>。
     * 非法token直接忽略，不抛异常。
     * @param raw 形如 "pace:BALANCED,interest:PHOTO" 的标签串，可为null
     * @return Map 维度到标签集合的映射，非法片段被忽略
     */
    public Map<String, Set<String>> parseProfileTags(String raw) {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String token : raw.split("[,，;；\\s]+")) {
            String s = token.trim();
            int idx = s.indexOf(':');
            if (idx <= 0 || idx == s.length() - 1) {
                continue;
            }
            String dim = s.substring(0, idx);
            String code = s.substring(idx + 1);
            out.computeIfAbsent(dim, k -> new LinkedHashSet<>()).add(code);
        }
        return out;
    }

    /**
     * 给前端展示用的用户公开视图（不含手机号/学号等敏感项）
     * @param u 目标用户
     * @return Map 公开资料视图，不含手机号与学号
     */
    private Map<String, Object> targetView(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("nickname", u.getNickname());
        m.put("avatar", u.getAvatar() == null ? "" : u.getAvatar());
        m.put("school", u.getSchool());
        m.put("college", u.getCollege());
        m.put("major", u.getMajor());
        m.put("grade", u.getGrade());
        m.put("bio", u.getBio());
        m.put("creditScore", u.getCreditScore());
        m.put("verified", Boolean.TRUE.equals(u.getVerified()));
        return m;
    }

    /**
     * 交集标签的展示名列表（用于"共同兴趣：摄影/美食"）
     * @param dimension 标签维度
     * @param a 我的标签集合
     * @param b 对方的标签集合
     * @return List 两者共同标签的展示名
     */
    private List<String> sharedLabels(String dimension, Set<String> a, Set<String> b) {
        List<String> out = new ArrayList<>();
        for (String code : a) {
            if (b.contains(code)) {
                out.add(labelOf(dimension, code));
            }
        }
        return out;
    }

    /**
     * 字典查找：dimension:code -> 展示名；查不到时回退原code，保证不会空白
     * @param dimension 标签维度
     * @param code 标签编码
     * @return String 展示名，字典里没有时返回原code
     */
    private String labelOf(String dimension, String code) {
        Map<String, String> cache = labelCache;
        if (cache == null) {
            Map<String, String> m = new HashMap<>();
            for (var t : tagDictRepository.findByActiveTrueOrderBySortNoAscIdAsc()) {
                m.put(t.getDimension() + ":" + t.getCode(), t.getLabel());
            }
            labelCache = cache = m;
        }
        return cache.getOrDefault(dimension + ":" + code, code);
    }

    private Map<String, Object> dim(String key, String label, int score, int weight, String note,
                                    List<String> shared) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("score", score);
        m.put("weight", weight);
        m.put("note", note);
        if (!shared.isEmpty()) {
            m.put("sharedTags", shared);
        }
        return m;
    }

    private String first(Set<String> set, String def) {
        if (set == null || set.isEmpty()) {
            return def;
        }
        return set.iterator().next();
    }

    private String paceLabel(String code) {
        return PACE_LABEL.getOrDefault(code, code);
    }

    private String budgetLabel(String code) {
        return BUDGET_LABEL.getOrDefault(code, code);
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }
}
