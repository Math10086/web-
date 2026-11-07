package com.xuelvquan.travel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xuelvquan.travel.entity.Trip;
import com.xuelvquan.travel.entity.TripMatch;
import com.xuelvquan.travel.entity.TripPref;
import com.xuelvquan.travel.entity.User;
import com.xuelvquan.travel.repository.TripMatchRepository;
import com.xuelvquan.travel.repository.TripMemberRepository;
import com.xuelvquan.travel.repository.TripPrefRepository;
import com.xuelvquan.travel.repository.TripRepository;
import com.xuelvquan.travel.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 结伴偏好匹配与推荐服务：基于多维加权规则计算用户与队伍的契合度，并给出Top N推荐。
 */
@Service
@RequiredArgsConstructor
public class TripMatchService {

    private final TripPrefRepository prefRepository;
    private final TripMatchRepository matchRepository;
    private final TripMemberRepository memberRepository;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final InterestProfileService profileService;

    /** 游玩节奏取值 */
    public static final String PACE_ENERGETIC = "ENERGETIC";
    public static final String PACE_BALANCED = "BALANCED";
    public static final String PACE_RELAXED = "RELAXED";

    /** 维度权重（合计100），顺序即前端展示顺序 */
    private static final int W_PACE = 25;
    private static final int W_INTEREST = 25;
    private static final int W_DESTINATION = 15;
    private static final int W_DAYS = 15;
    private static final int W_BUDGET = 12;
    private static final int W_TRANSPORT = 8;

    private static final Map<String, String> PACE_LABEL = Map.of(
            PACE_ENERGETIC, "精力满满",
            PACE_BALANCED, "节奏适中",
            PACE_RELAXED, "纯休闲放松");

    private static final Map<String, String> TRANSPORT_LABEL = Map.of(
            "TRAIN", "高铁/火车",
            "BUS", "长途大巴",
            "CAR", "自驾/租车",
            "CARPOOL", "拼车/顺风车",
            "BIKE", "骑行/徒步");

    /**
     * 取用户结伴偏好。手填值优先，缺失部分由动态兴趣画像用历史行为补齐。
     * @param userId 用户ID
     * @return TripPref 合并后的偏好，没有手填记录时给一份默认偏好
     */
    public TripPref resolvePref(Long userId) {
        TripPref db = prefRepository.findByUserId(userId).orElseGet(() -> {
            TripPref p = new TripPref();
            p.setUserId(userId);
            p.setPaceType(PACE_BALANCED);
            return p;
        });
        return mergeWithProfile(copyOf(db), userId);
    }

    /**
     * 合并手填偏好与行为推断偏好：手填标签优先排前，推断标签在未被覆盖时补齐，最多8个；
     * 天数/预算/目的地/交通方式缺失时用行为推断值兜底，节奏沿用手填值。
     * @param pref 用户手填的偏好
     * @param userId 用户ID，用于取行为画像
     * @return TripPref 补齐后的偏好
     */
    private TripPref mergeWithProfile(TripPref pref, Long userId) {
        InterestProfileService.DerivedProfile dp = profileService.derive(userId);

        Set<String> manual = tags(pref.getInterestTags());
        Set<String> merged = new LinkedHashSet<>(manual);
        for (String tag : dp.topTags) {
            if (merged.size() >= 8) {
                break;
            }
            merged.add(tag);
        }
        if (!merged.isEmpty()) {
            pref.setInterestTags(String.join(",", merged));
        }

        if (pref.getDestinationPref() == null && dp.destinationPref != null) {
            pref.setDestinationPref(dp.destinationPref);
        }
        if (pref.getExpectDays() == null && dp.expectDays != null) {
            pref.setExpectDays(dp.expectDays);
        }
        if ((pref.getBudget() == null || pref.getBudget() <= 0) && dp.budget != null) {
            pref.setBudget(dp.budget);
        }
        if (pref.getTransport() == null && dp.transport != null) {
            pref.setTransport(dp.transport);
        }
        return pref;
    }

    /**
     * 复制一份偏好，避免JPA脏检查把推断值回写到库里
     * @param s 原始偏好
     * @return TripPref 字段一致的副本
     */
    private TripPref copyOf(TripPref s) {
        TripPref p = new TripPref();
        p.setId(s.getId());
        p.setUserId(s.getUserId());
        p.setExpectDays(s.getExpectDays());
        p.setPaceType(s.getPaceType());
        p.setDestinationPref(s.getDestinationPref());
        p.setInterestTags(s.getInterestTags());
        p.setBudget(s.getBudget());
        p.setTransport(s.getTransport());
        p.setOpenToCrossSchool(s.getOpenToCrossSchool());
        p.setUpdatedAt(s.getUpdatedAt());
        return p;
    }

    /**
     * 行为变化（报名、发帖、记足迹）后让画像立刻重算，别等5分钟缓存过期
     * @param userId 用户ID
     */
    public void invalidateProfile(Long userId) {
        profileService.invalidate(userId);
    }

    /**
     * 画像详情（「我的偏好」页展示系统从行为里读到了什么）
     * @param userId 用户ID
     * @return Map 画像详情，含标签、置信度、行为依据与推断出的偏好
     */
    public Map<String, Object> profileView(Long userId) {
        InterestProfileService.DerivedProfile dp = profileService.derive(userId);
        TripPref effective = resolvePref(userId);
        TripPref manual = prefRepository.findByUserId(userId).orElse(null);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tags", dp.tags);
        m.put("topTags", dp.topTags);
        m.put("cities", dp.cities);
        m.put("evidence", dp.evidence);
        m.put("confidence", dp.confidence);
        m.put("signalCount", dp.signalCount);
        m.put("computedAt", dp.computedAt);
        m.put("manualTags", new ArrayList<>(tags(manual == null ? null : manual.getInterestTags())));
        m.put("effectiveTags", new ArrayList<>(tags(effective.getInterestTags())));
        // 顺手把推断出的其它偏好也带出去，前端可以提示"已按你的足迹自动补全"
        m.put("derivedPref", Map.of(
                "expectDays", dp.expectDays == null ? "" : dp.expectDays,
                "budget", dp.budget == null ? "" : dp.budget,
                "transport", dp.transport == null ? "" : dp.transport,
                "destinationPref", dp.destinationPref == null ? "" : dp.destinationPref));
        return m;
    }

    /**
     * 保存/更新我的结伴偏好（一人一条）
     * @param userId 用户ID
     * @param body 偏好表单，按传入的字段逐个更新
     * @return TripPref 保存后的偏好
     */
    public TripPref savePref(Long userId, Map<String, Object> body) {
        TripPref p = prefRepository.findByUserId(userId).orElseGet(() -> {
            TripPref x = new TripPref();
            x.setUserId(userId);
            return x;
        });
        if (body.containsKey("expectDays")) {
            p.setExpectDays(optInt(body.get("expectDays")));
        }
        if (body.containsKey("paceType")) {
            p.setPaceType(nz(String.valueOf(body.get("paceType")), PACE_BALANCED));
        }
        if (body.containsKey("destinationPref")) {
            p.setDestinationPref(blankToNull(body.get("destinationPref")));
        }
        if (body.containsKey("interestTags")) {
            p.setInterestTags(blankToNull(body.get("interestTags")));
        }
        if (body.containsKey("budget")) {
            p.setBudget(optDouble(body.get("budget")));
        }
        if (body.containsKey("transport")) {
            p.setTransport(blankToNull(body.get("transport")));
        }
        if (body.containsKey("openToCrossSchool")) {
            p.setOpenToCrossSchool(Boolean.TRUE.equals(body.get("openToCrossSchool")));
        }
        p.setUpdatedAt(LocalDateTime.now());
        TripPref saved = prefRepository.save(p);
        profileService.invalidate(userId); // 手填偏好变了，画像摘要要重算
        return saved;
    }

    /**
     * 计算「某用户×某队伍」的匹配明细。
     *
     * @param trip 目标队伍
     * @param user 当前用户
     * @return Map 含匹配分、推荐分、各维度打分与所用偏好，始终非null
     */
    public Map<String, Object> matchOf(Trip trip, User user) {
        TripPref pref = resolvePref(user.getId());
        List<Map<String, Object>> dims = new ArrayList<>();

        // 1) 游玩节奏
        String tp = nz(trip.getPaceType(), PACE_BALANCED);
        String up = nz(pref.getPaceType(), PACE_BALANCED);
        int paceScore;
        if (tp.equals(up)) {
            paceScore = 100;
        } else if (PACE_BALANCED.equals(tp) || PACE_BALANCED.equals(up)) {
            paceScore = 70;
        } else {
            paceScore = 30; // 精力满满vs纯休闲放松
        }
        dims.add(dim("pace", "游玩节奏", paceScore, W_PACE,
                "队伍" + label(PACE_LABEL, tp) + " · 你" + label(PACE_LABEL, up)));

        // 2) 兴趣标签（Jaccard相似度）
        Set<String> ti = tags(trip.getInterestTags());
        Set<String> ui = tags(pref.getInterestTags());
        // 哪些标签是系统从行为里推断出来的（手填里没有），用于在提示语里标注来源
        TripPref manualPref = prefRepository.findByUserId(user.getId()).orElse(null);
        Set<String> manualTags = tags(manualPref == null ? null : manualPref.getInterestTags());
        boolean hasInferred = ui.stream().anyMatch(t -> !manualTags.contains(t));
        int interestScore;
        String interestNote;
        if (ti.isEmpty() || ui.isEmpty()) {
            interestScore = 60;
            interestNote = ti.isEmpty() ? "队伍未标注兴趣标签" : "你还没设置兴趣标签";
        } else {
            Set<String> inter = new LinkedHashSet<>(ti);
            inter.retainAll(ui);
            Set<String> union = new LinkedHashSet<>(ti);
            union.addAll(ui);
            interestScore = (int) Math.round(inter.size() * 100.0 / union.size());
            interestNote = inter.isEmpty()
                    ? "无共同兴趣（队伍：" + String.join("/", ti) + "）"
                    : "共同兴趣：" + String.join("/", inter) + (hasInferred ? "（含行为推断）" : "");
        }
        dims.add(dim("interest", "兴趣标签", interestScore, W_INTEREST, interestNote));

        // 3) 目的地
        String destPref = pref.getDestinationPref();
        int destScore;
        String destNote;
        String dest = trip.getDestination() == null ? "" : trip.getDestination();
        if (destPref == null) {
            destScore = 100;
            destNote = "未设置目的地偏好，不限";
        } else if (dest.contains(destPref) || destPref.contains(dest)) {
            destScore = 100;
            destNote = "命中你的目的地偏好：" + destPref;
        } else {
            destScore = 40;
            destNote = "与你的偏好（" + destPref + "）不同";
        }
        dims.add(dim("destination", "目的地", destScore, W_DESTINATION, destNote));

        // 4) 预期天数
        Integer td = trip.getPrefDays();
        Integer ud = pref.getExpectDays();
        int daysScore;
        String daysNote;
        if (td == null || ud == null) {
            daysScore = 60;
            daysNote = "天数信息不完整（队伍" + show(td) + " 天 / 你" + show(ud) + " 天）";
        } else {
            int diff = Math.abs(td - ud);
            daysScore = switch (diff) {
                case 0 -> 100;
                case 1 -> 75;
                case 2 -> 45;
                default -> 20;
            };
            daysNote = "队伍" + td + " 天 · 你" + ud + " 天";
        }
        dims.add(dim("days", "预期天数", daysScore, W_DAYS, daysNote));

        // 5) 预算
        Double tb = trip.getBudget();
        Double ub = pref.getBudget();
        int budgetScore;
        String budgetNote;
        if (tb == null || ub == null || ub <= 0) {
            budgetScore = 60;
            budgetNote = "预算信息不完整";
        } else if (tb <= ub) {
            budgetScore = 100;
            budgetNote = "队伍 ¥" + tb + " / 人，在你的预算内（¥" + ub + "）";
        } else {
            double over = (tb - ub) / ub;
            if (over <= 0.1) {
                budgetScore = 80;
            } else if (over <= 0.2) {
                budgetScore = 55;
            } else if (over <= 0.3) {
                budgetScore = 30;
            } else {
                budgetScore = 10;
            }
            budgetNote = "队伍 ¥" + tb + " / 人，超出你预算" + Math.round(over * 100) + "%";
        }
        dims.add(dim("budget", "预算档位", budgetScore, W_BUDGET, budgetNote));

        // 6) 出行方式
        String tt = trip.getTransport();
        String ut = pref.getTransport();
        int transScore;
        String transNote;
        if (tt == null || ut == null) {
            transScore = 60;
            transNote = "出行方式待定";
        } else if (tt.equals(ut)) {
            transScore = 100;
            transNote = "都是" + label(TRANSPORT_LABEL, tt);
        } else {
            transScore = 50;
            transNote = "队伍" + label(TRANSPORT_LABEL, tt) + " · 你偏好" + label(TRANSPORT_LABEL, ut);
        }
        dims.add(dim("transport", "出行方式", transScore, W_TRANSPORT, transNote));

        int matchScore = 0;
        for (Map<String, Object> d : dims) {
            matchScore += (int) d.get("score") * (int) d.get("weight") / 100;
        }
        matchScore = clamp(matchScore);

        // 推荐分=匹配度80%+信誉分15%+成团机会5%
        int credit = nzInt(user.getCreditScore(), 100);
        long approved = memberRepository.countByTripIdAndStatus(trip.getId(), "APPROVED");
        int max = trip.getMaxMembers() == null ? 6 : trip.getMaxMembers();
        int vacancyRate = (int) Math.max(0, Math.min(100, (max - approved) * 100 / Math.max(1, max)));
        int recommendScore = clamp((int) Math.round(matchScore * 0.8 + credit * 0.15 + vacancyRate * 0.05));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tripId", trip.getId());
        m.put("userId", user.getId());
        m.put("matchScore", matchScore);
        m.put("recommendScore", recommendScore);
        m.put("dims", dims);
        m.put("prefUsed", prefView(pref));
        return m;
    }

    /**
     * Top N结伴推荐：只推还在招募（OPEN）、未满员、且校际范围允许的队伍，
     * 按推荐分倒序取前limit个，并落库为TripMatch（source=RECOMMEND）。
     * @param userId 用户ID
     * @param limit 返回条数上限
     * @return List 推荐队伍及其匹配明细，按推荐分倒序
     */
    public List<Map<String, Object>> recommend(Long userId, int limit) {
        User me = userRepository.findById(userId).orElse(null);
        if (me == null) {
            return List.of();
        }
        TripPref pref = resolvePref(userId);
        List<Map<String, Object>> scored = new ArrayList<>();
        for (Trip t : tripRepository.findByStatusOrderByCreatedAtDesc("OPEN")) {
            if (t.getCaptainId() != null && t.getCaptainId().equals(userId)) {
                continue; // 自己发起的队伍不进推荐
            }
            long approved = memberRepository.countByTripIdAndStatus(t.getId(), "APPROVED");
            if (approved >= (t.getMaxMembers() == null ? 6 : t.getMaxMembers())) {
                continue;
            }
            if (!schoolAllowed(t, me, pref)) {
                continue;
            }
            Map<String, Object> m = matchOf(t, me);
            m.put("trip", t);
            scored.add(m);
        }
        scored.sort((a, b) -> (int) b.get("recommendScore") - (int) a.get("recommendScore"));
        List<Map<String, Object>> top = scored.size() > limit ? scored.subList(0, limit) : scored;
        for (Map<String, Object> m : top) {
            saveSnapshot((Trip) m.get("trip"), userId, (int) m.get("recommendScore"), "RECOMMEND", m);
        }
        return top;
    }

    /**
     * 报名/审核时落一份匹配快照，供队长审核参考
     * @param trip 目标队伍
     * @param userId 报名用户ID
     * @param recommendScore 推荐分，为空时用当场算出的值
     * @param source 快照来源，如RECOMMEND、APPLY
     * @return TripMatch 保存后的快照，用户不存在时返回null
     */
    public TripMatch snapshot(Trip trip, Long userId, Integer recommendScore, String source) {
        User u = userRepository.findById(userId).orElse(null);
        if (u == null) {
            return null;
        }
        Map<String, Object> m = matchOf(trip, u);
        int rec = recommendScore == null ? (int) m.get("recommendScore") : recommendScore;
        return saveSnapshot(trip, userId, rec, source, m);
    }

    /**
     * 读取某队伍所有报名者的匹配度（队长审核用），返回userId -> {matchScore, recommendScore, dims}
     * @param tripId 队伍ID
     * @return Map 报名用户ID到匹配明细的映射，队伍不存在时为空
     */
    public Map<Long, Map<String, Object>> matchMapOfTrip(Long tripId) {
        Trip trip = tripRepository.findById(tripId).orElse(null);
        Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
        if (trip == null) {
            return result;
        }
        for (TripMatch tm : matchRepository.findByTripId(tripId)) {
            result.put(tm.getUserId(), matchView(tm));
        }
        return result;
    }

    /**
     * 单条TripMatch的展示结构
     * @param tm 匹配快照记录
     * @return Map 展示结构，含匹配分、推荐分、各维度与生成时间
     */
    public Map<String, Object> matchView(TripMatch tm) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("matchScore", tm.getMatchScore());
        m.put("recommendScore", tm.getRecommendScore());
        m.put("dims", parseDims(tm.getDimsJson()));
        m.put("createdAt", tm.getCreatedAt());
        return m;
    }

    /**
     * 兼容旧数据：库里可能没有偏好记录
     * @param p 偏好记录
     * @return Map 偏好展示结构，附带动态画像摘要
     */
    public Map<String, Object> prefView(TripPref p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("expectDays", p.getExpectDays());
        m.put("paceType", p.getPaceType());
        m.put("destinationPref", p.getDestinationPref());
        m.put("interestTags", p.getInterestTags());
        m.put("budget", p.getBudget());
        m.put("transport", p.getTransport());
        m.put("openToCrossSchool", p.getOpenToCrossSchool());
        m.put("updatedAt", p.getUpdatedAt());
        // 动态画像摘要：前端可据此提示「这些兴趣是系统根据用户行为补充的」
        if (p.getUserId() != null) {
            m.put("derived", profileService.derive(p.getUserId()).brief());
        }
        return m;
    }

    /**
     * 偏好详情（给「我的偏好」表单用）：返回合并后的有效值与原始手填值。
     * @param userId 用户ID
     * @return Map 合并后的有效偏好与原始手填值
     */
    public Map<String, Object> prefDetail(Long userId) {
        Map<String, Object> m = prefView(resolvePref(userId));
        TripPref raw = prefRepository.findByUserId(userId).orElse(null);
        m.put("manual", raw == null ? null : rawView(raw));
        return m;
    }

    private Map<String, Object> rawView(TripPref p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("expectDays", p.getExpectDays());
        m.put("paceType", p.getPaceType());
        m.put("destinationPref", p.getDestinationPref());
        m.put("interestTags", p.getInterestTags());
        m.put("budget", p.getBudget());
        m.put("transport", p.getTransport());
        m.put("openToCrossSchool", p.getOpenToCrossSchool());
        return m;
    }

    private TripMatch saveSnapshot(Trip trip, Long userId, int recommendScore, String source, Map<String, Object> detail) {
        TripMatch tm = matchRepository.findByTripIdAndUserId(trip.getId(), userId).orElseGet(() -> {
            TripMatch x = new TripMatch();
            x.setTripId(trip.getId());
            x.setUserId(userId);
            return x;
        });
        tm.setMatchScore((int) detail.get("matchScore"));
        tm.setRecommendScore(recommendScore);
        try {
            tm.setDimsJson(objectMapper.writeValueAsString(detail.get("dims")));
        } catch (Exception ignored) {
            tm.setDimsJson(null);
        }
        tm.setSource(source);
        tm.setCreatedAt(LocalDateTime.now());
        return matchRepository.save(tm);
    }

    private boolean schoolAllowed(Trip trip, User me, TripPref pref) {
        if (!"SAME".equals(trip.getSchoolScope())) {
            return Boolean.TRUE.equals(pref.getOpenToCrossSchool());
        }
        Long captainId = trip.getCaptainId();
        if (captainId == null) {
            return true;
        }
        String captainSchool = userRepository.findById(captainId).map(User::getSchool).orElse(null);
        return captainSchool != null && captainSchool.equals(me.getSchool());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseDims(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private Map<String, Object> dim(String key, String label, int score, int weight, String note) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("score", score);
        m.put("weight", weight);
        m.put("note", note);
        return m;
    }

    private Set<String> tags(String csv) {
        Set<String> set = new LinkedHashSet<>();
        if (csv == null || csv.isBlank()) {
            return set;
        }
        Arrays.stream(csv.split("[,，、/\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(set::add);
        return set;
    }

    private String label(Map<String, String> map, String key) {
        return Optional.ofNullable(map.get(key)).orElse(key);
    }

    private String show(Integer v) {
        return v == null ? "未填" : String.valueOf(v);
    }

    private int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    private int nzInt(Integer v, int def) {
        return v == null ? def : v;
    }

    private String nz(String v, String def) {
        return v == null || v.isBlank() ? def : v;
    }

    private String blankToNull(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    private Integer optInt(Object o) {
        if (o == null || String.valueOf(o).isBlank()) {
            return null;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double optDouble(Object o) {
        if (o == null || String.valueOf(o).isBlank()) {
            return null;
        }
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
