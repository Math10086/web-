package com.xuelvquan.travel.service;

import com.xuelvquan.travel.common.BusinessException;
import com.xuelvquan.travel.entity.AiUserMemory;
import com.xuelvquan.travel.entity.Spot;
import com.xuelvquan.travel.entity.TripPref;
import com.xuelvquan.travel.repository.AiUserMemoryRepository;
import com.xuelvquan.travel.repository.SpotRepository;
import com.xuelvquan.travel.repository.TripPrefRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能路线规划：解析用户意图，结合画像与景点打分选点，交由确定性规划器生成行程并生成自然语言解释。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmartRouteService {

    private final SpotRepository spotRepository;
    private final TripPrefRepository tripPrefRepository;
    private final AiUserMemoryRepository memoryRepository;
    private final RoutePlannerService routePlannerService;
    private final AiIntentService aiIntentService;
    private final AiChatService aiChatService;

    /** 兴趣标签 → 景点类目亲和度（1.0完全契合，0.15弱相关兜底） */
    private static final Map<String, Map<String, Double>> TAG_AFFINITY = Map.of(
            "摄影", Map.of("NATURE", 1.0, "TRENDY", 0.8, "THEME", 0.6, "HISTORY", 0.5),
            "美食", Map.of("FOOD", 1.0, "TRENDY", 0.6),
            "徒步登山", Map.of("NATURE", 1.0, "SECRET", 0.7),
            "历史人文", Map.of("HISTORY", 1.0, "TRENDY", 0.4),
            "自然风光", Map.of("NATURE", 1.0, "SECRET", 0.5),
            "城市漫步", Map.of("TRENDY", 1.0, "HISTORY", 0.7),
            "露营", Map.of("NATURE", 1.0, "SECRET", 0.4),
            "骑行", Map.of("NATURE", 0.8, "TRENDY", 0.5));

    /** 标签同义词：用于在自由文本（AI记忆摘要/个性签名）里识别标签存在（刻意收窄，保证区分度） */
    private static final Map<String, List<String>> TAG_ALIASES = Map.of(
            "摄影", List.of("摄影", "拍照", "照相", "出片", "拍美照"),
            "美食", List.of("美食", "小吃", "好吃", "吃货", "探店"),
            "徒步登山", List.of("爬山", "徒步", "登山"),
            "历史人文", List.of("历史", "古迹", "博物馆", "人文", "书院"),
            "自然风光", List.of("自然风光", "山水", "红叶", "江景"),
            "城市漫步", List.of("citywalk", "城市漫步", "逛街", "漫步", "街区"),
            "露营", List.of("露营", "野营"),
            "骑行", List.of("骑行", "骑车"));

    /** 模式 → 类目契合度 */
    private static final Map<String, String> MODE_CORE_CATEGORY = Map.of(
            "FOOD", "FOOD", "SCENIC", "NATURE", "WILD", "SECRET", "CITYWALK", "TRENDY");

    /** 景点打分权重：兴趣0.45/热度0.25/记忆0.20/模式0.10 */
    private static final double W_INTEREST = 0.45, W_HEAT = 0.25, W_MEMORY = 0.20, W_MODE = 0.10;

    /**
     * 智能规划主入口：解析意图、画像打分选点、生成行程并附带AI解释。
     *
     * @param userId 用户ID
     * @param text 用户的一句话需求，如「长沙2天，喜欢拍照和美食」
     * @return 与/routes/plan同构的行程结果，附带aiPlan元数据
     */
    public Map<String, Object> smartPlan(Long userId, String text) {
        return smartPlan(userId, text, 0);
    }

    /**
     * 智能规划主入口，支持指定换路线轮次。
     * @param userId 用户ID
     * @param text 用户输入的自然语言需求
     * @param round 换路线轮次（0为首次规划）；大于0时对备选景点做环形偏移换一批点
     * @return Map 行程结果，附带aiPlan元数据
     */
    public Map<String, Object> smartPlan(Long userId, String text, int round) {
        if (text == null || text.isBlank()) {
            throw new BusinessException("请告诉我想去哪玩，例如「长沙2天，喜欢拍照和美食」");
        }

        // 解析用户意图
        List<String> cities = spotRepository.findCities();
        List<Spot> allSpots = spotRepository.findAll();
        List<String> spotNames = allSpots.stream().map(Spot::getName).toList();
        Map<String, Object> hit = aiIntentService.parseIntent(text, cities, spotNames);
        if (hit == null) {
            hit = localRuleParse(text, cities);
        }

        // 读取用户画像
        String memorySummary = memoryRepository.findByUserId(userId)
                .map(AiUserMemory::getMemorySummary).orElse(null);
        TripPref pref = tripPrefRepository.findByUserId(userId).orElse(null);
        String prefTags = pref != null && pref.getInterestTags() != null ? pref.getInterestTags() : "";

        // 匹配用户点名的景点
        NamedMatch nm = matchNamed(hit.get("spotNames"), allSpots);
        List<Spot> named = nm.matched();
        List<String> unmatched = nm.unmatched();

        // 处理用户明确排除的地点
        final List<Long> excludedRaw = extractExcludedIds(text, allSpots);
        List<Long> excludedIds = excludedRaw;
        if (!excludedRaw.isEmpty()) {
            // 排除后剩余过少则忽略本次排除
            long remain = allSpots.stream().filter(s -> !excludedRaw.contains(s.getId())).count();
            if (remain < 3) {
                excludedIds = List.of();
            } else {
                named = named.stream().filter(n -> !excludedRaw.contains(n.getId())).toList();
            }
        }
        final List<Long> exFinal = excludedIds;
        List<String> excludedNames = exFinal.isEmpty() ? List.of()
                : allSpots.stream().filter(s -> exFinal.contains(s.getId())).map(Spot::getName).toList();

        // 识别换路线意图（口语说法或点名未收录地点时触发）
        int effectiveRound = Math.max(0, round);
        if (effectiveRound == 0 && isReplan(text)) effectiveRound = 1;
        if (effectiveRound == 0 && !unmatched.isEmpty()) effectiveRound = 1;

        String city = (String) hit.get("city");
        if (city == null && !named.isEmpty()) {
            city = named.get(0).getCity();   // 仅提供场馆名时使用其所在城市
        }
        if (city == null) {
            city = cityFromMemory(memorySummary, cities);  // 记忆中有城市时作为默认城市
        }
        if (city == null) {
            // 无法确定城市时返回自然追问
            return clarify(text, "你想去哪座城市呀？长沙、张家界、成都我都能排，说个地名就行～");
        }

        Integer days = hit.get("tripDays") instanceof Integer n ? n : 1;
        String mode = (String) hit.get("mode");
        String startTime = (String) hit.get("startTime");
        String duration = (String) hit.get("durationHint");

        // 记忆中"怕走路"时改用休闲慢游模式
        boolean walkAverse = memorySummary != null
                && (memorySummary.contains("怕走路") || memorySummary.contains("少走")
                    || memorySummary.contains("减少步行") || memorySummary.contains("不想走"));
        if (mode == null && walkAverse) {
            mode = "LEISURE";
        } else if (mode == null) {
            mode = "OPTIMAL";
        }

        // 按时长提示控制每天景点数
        int perDay;
        if ("HALF".equals(duration) || "AFTERNOON".equals(duration)) {
            perDay = 3;
            if (startTime == null && "AFTERNOON".equals(duration)) {
                startTime = "13:30";
            }
        } else if ("HOURS".equals(duration)) {
            perDay = 2;
        } else {
            perDay = "SPEED".equals(mode) ? 6 : ("LEISURE".equals(mode) || walkAverse ? 4 : 5);
        }

        // 逐景点打分
        final String finalCity = city;
        List<Spot> candidates = allSpots.stream()
                .filter(s -> finalCity.equals(s.getCity()) && !exFinal.contains(s.getId())).toList();
        if (candidates.isEmpty()) {
            throw new BusinessException("景点库暂无「" + city + "」的景点，换个城市试试");
        }
        List<Scored> scoredList = new ArrayList<>();
        for (Spot s : candidates) {
            scoredList.add(score(s, prefTags, memorySummary, mode, named));
        }
        scoredList.sort(Comparator.comparingDouble((Scored sc) -> sc.total).reversed());

        int want = Math.min(days * perDay, scoredList.size());
        // 用户点名的景点强制入选
        List<Scored> picked = new ArrayList<>();
        for (Spot n : named) {
            if (!n.getCity().equals(city)) continue;
            for (Scored sc : scoredList) {
                if (sc.spot.getId().equals(n.getId())) {
                    picked.add(sc);
                    break;
                }
            }
        }
        // 剩余名额按分数顺序补足；换路线时对候选做环形偏移换一批点
        List<Scored> pool = new ArrayList<>();
        for (Scored sc : scoredList) {
            boolean locked = picked.stream().anyMatch(p -> p.spot.getId().equals(sc.spot.getId()));
            if (!locked) pool.add(sc);
        }
        int need = Math.max(0, want - picked.size());
        if (need > 0 && !pool.isEmpty()) {
            int start = Math.floorMod(effectiveRound * need, pool.size());
            for (int i = 0; i < Math.min(need, pool.size()); i++) {
                picked.add(pool.get((start + i) % pool.size()));
            }
        }
        if (picked.isEmpty()) {
            throw new BusinessException("「" + city + "」暂时排不出可用的路线，换个条件再试试");
        }
        List<Long> pickedIds = picked.stream().map(sc -> sc.spot.getId()).toList();

        // 调用确定性规划器生成行程
        Integer startMinutes = parseStart(startTime);
        Map<String, Object> result = routePlannerService.plan(city, mode, pickedIds, startMinutes, null, days);

        // 组装AI元数据
        List<Map<String, Object>> spotScores = new ArrayList<>();
        for (Scored sc : picked) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("spotId", sc.spot.getId());
            m.put("name", sc.spot.getName());
            m.put("score", round2(sc.total));
            m.put("interest", round2(sc.interest));
            m.put("heat", round2(sc.heat));
            m.put("memory", round2(sc.memory));
            m.put("reasons", sc.reasons);
            spotScores.add(m);
        }

        Map<String, Object> profile = new LinkedHashMap<>();
        if (memorySummary != null) {
            profile.put("memoryBrief", brief(memorySummary, 120));
        }
        if (!prefTags.isBlank()) {
            profile.put("prefTags", prefTags);
        }
        profile.put("walkAverse", walkAverse);
        profile.put("named", named.stream().map(Spot::getName).toList());
        profile.put("unmatched", unmatched);            // 未匹配到的原始说法
        profile.put("excluded", excludedNames);         // 已排除的地点
        profile.put("replanRound", effectiveRound);     // 换路线轮次
        profile.put("durationHint", duration);
        profile.put("keywords", picked.stream().findFirst().map(sc -> sc.hitTags).orElse(List.of()));

        // 换路线或未匹配时，在解释前补充说明
        List<String> notes = new ArrayList<>();
        if (!unmatched.isEmpty()) {
            notes.add("「" + String.join("」「", unmatched) + "」我库里暂时没收录");
        }
        if (!excludedNames.isEmpty()) {
            notes.add("已把「" + String.join("」「", excludedNames) + "」从路线里去掉");
        }
        if (!notes.isEmpty()) {
            notes.add("先带你走这几个周边点");
        } else if (effectiveRound > 0) {
            notes.add("按你说的换了一条思路");
        }
        String note = notes.isEmpty() ? "" : String.join("，", notes) + "，";
        String explanation = explain(text, city, mode, days, walkAverse, picked);
        if (!note.isEmpty()) {
            explanation = note + explanation;
            log.info("[学旅圈] 对话式改路线：round={}，点名={}，未收录={}，剔除={}，新点集={}",
                    effectiveRound, named.stream().map(Spot::getName).toList(), unmatched, excludedNames,
                    picked.stream().map(sc -> sc.spot.getName()).toList());
        }
        String engine = aiIntentService.engineName();
        if (engine.startsWith("rule")) {
            engine = "fallback";
        }

        Map<String, Object> aiPlan = new LinkedHashMap<>();
        aiPlan.put("profile", profile);
        aiPlan.put("spotScores", spotScores);
        aiPlan.put("explanation", explanation);
        aiPlan.put("engine", engine);
        aiPlan.put("formula", "0.45×兴趣 + 0.25×热度 + 0.20×记忆命中 + 0.10×模式契合");
        result.put("aiPlan", aiPlan);
        result.put("aiExplanation", explanation);
        result.put("aiReply", hit.get("reply"));
        log.info("[学旅圈] V2智能规划：{} → {} {}天 {}站，点名景点 {}，记忆命中画像：怕走路={}",
                text, city, days, picked.size(), profile.get("named"), walkAverse);
        return result;
    }

    /**
     * 无法确定目的地时返回的「自然追问」结构（前端识别clarify后直接展示，不再报错）
     * @param text 用户原话
     * @param fallbackAsk 模型没有给出追问时的兜底文案
     * @return Map 含clarify标记与追问文案
     */
    private Map<String, Object> clarify(String text, String fallbackAsk) {
        Map<String, Object> m = new LinkedHashMap<>();
        String ask = aiChatService.complete(
                "你是「学旅圈」的旅行搭子，学生用户说的话没听出目的地。"
                        +"用一句不超过30字的中文口语追问去哪座城市，语气亲切，不要模板腔，不要列清单。",
                text);
        if (ask == null || ask.isBlank()) {
            ask = fallbackAsk;
        }
        m.put("clarify", true);
        m.put("askText", ask.trim());
        log.info("[学旅圈] 意图不完整，转自然追问：{} → {}", text, ask);
        return m;
    }

    /** 点名匹配结果：命中的真实景点+没匹配上的原始说法（用于给用户一句诚实的回话） */
    private record NamedMatch(List<Spot> matched, List<String> unmatched) {}

    /**
     * 将抽取出的景点名匹配到真实景点：先做口语归一，再用字符二元组Dice相似度打分。
     *
     * @param rawNames 抽取出的景点名列表
     * @param allSpots 全部景点
     * @return 命中的真实景点与未匹配的原始说法
     */
    private NamedMatch matchNamed(Object rawNames, List<Spot> allSpots) {
        List<Spot> matched = new ArrayList<>();
        List<String> unmatched = new ArrayList<>();
        if (!(rawNames instanceof List<?> names) || names.isEmpty()) {
            return new NamedMatch(matched, unmatched);
        }
        for (Object o : names) {
            String raw = String.valueOf(o);
            String q = normalizePoi(raw);
            if (q.isEmpty()) continue;
            Spot best = null;
            double bestScore = 0;
            for (Spot s : allSpots) {
                double d = dice(q, normalizePoi(s.getName()));
                // 名称/描述里含用户说的词也给一点加权（例如"橘子洲头"→"橘子洲"）
                if (s.getName().contains(raw) || raw.contains(s.getName())) d = Math.max(d, 0.9);
                if (d > bestScore) {
                    bestScore = d;
                    best = s;
                }
            }
            if (best != null && bestScore >= 0.5) {
                final Spot hitSpot = best;
                boolean dup = matched.stream().anyMatch(x -> x.getId().equals(hitSpot.getId()));
                if (!dup) matched.add(hitSpot);
            } else if (!unmatched.contains(raw)) {
                unmatched.add(raw);
            }
        }
        return new NamedMatch(matched, unmatched);
    }

    /** 口语化的"换一条路"说法识别（前端已传轮次时只作兜底） */
    private static final java.util.regex.Pattern REPLAN_PATTERN = java.util.regex.Pattern.compile(
            ".*(换(一|条|个|种|上|掉)?[^，。！？]{0,3}(路|路线|行程)|换个|重新(规划|排|安排|来|换)"
            + "|再来(一)?(条|遍|次)|另一条|其他路线|别的路线|换个想法|不走这|不喜欢|不满意|不想要"
            + "|变一变|重排|重来).*");

    private static boolean isReplan(String text) {
        return text != null && REPLAN_PATTERN.matcher(text).matches();
    }

    /** "不去"类排除词：只在词附近的小窗口里找景点名，避免把"不想去太远的地方"误判成排除 */
    private static final java.util.regex.Pattern EXCLUDE_PATTERN = java.util.regex.Pattern.compile(
            "(?:不要|别去|不去|去掉|排除|删掉|移除|不想去)([^，。！？；、]{0,12})"
            + "|([^，。！？；、]{0,12})(?:去掉|排除|删掉|移除)");

    /**
     * 提取用户明确排除的地点，返回需从候选中移除的景点id。
     *
     * @param text 用户输入
     * @param allSpots 全部景点
     * @return 需排除的景点id列表
     */
    private List<Long> extractExcludedIds(String text, List<Spot> allSpots) {
        List<Long> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;
        var m = EXCLUDE_PATTERN.matcher(text);
        while (m.find()) {
            String seg = m.group(1) != null ? m.group(1) : m.group(2);
            if (seg == null || seg.isBlank()) continue;
            String q = normalizePoi(seg);
            if (q.isEmpty()) continue;
            for (Spot s : allSpots) {
                String n = normalizePoi(s.getName());
                if (n.isEmpty()) continue;
                boolean hit = q.contains(n) || seg.contains(s.getName())
                        || (q.length() >= 2 && n.contains(q));
                if (hit && !out.contains(s.getId())) {
                    out.add(s.getId());
                }
            }
        }
        return out;
    }

    /**
     * 口语归一：去修饰词+同义替换+去城市前缀（"省博物馆"→"博物院"、"湖南博物院"→"博物院"）
     * @param s 用户说出的地点名，可为null
     * @return String 归一口语后的地点名，null时返回空串
     */
    private static String normalizePoi(String s) {
        if (s == null) return "";
        String t = s.toLowerCase()
                .replaceAll("[\\s,，。、！？?的想去玩逛看看一下我们你他她帮规划路线把将]", "");
        t = t.replace("省博物馆", "博物院").replace("省博物", "博物院").replace("省博", "博物院")
             .replace("博物馆", "博物院");
        for (String c : List.of("湖南", "长沙", "张家界", "重庆", "成都", "西安")) {
            t = t.replace(c, "");
        }
        return t;
    }

    private static java.util.Set<String> bigrams(String s) {
        java.util.Set<String> set = new java.util.HashSet<>();
        for (int i = 0; i + 1 < s.length(); i++) {
            set.add(s.substring(i, i + 2));
        }
        return set;
    }

    /**
     * 字符二元组Dice相似度（对中文短名足够稳，且不需要分词库）
     * @param a 待比较的名称
     * @param b 待比较的名称
     * @return double 相似度，取值0~1
     */
    private static double dice(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        if (a.equals(b)) return 1.0;
        if (a.contains(b) || b.contains(a)) return 0.9;
        java.util.Set<String> ba = bigrams(a), bb = bigrams(b);
        if (ba.isEmpty() || bb.isEmpty()) return 0;
        long inter = ba.stream().filter(bb::contains).count();
        return 2.0 * inter / (ba.size() + bb.size());
    }

    /**
     * 记忆里出现过城市名 → 当作默认目的地（"我在长沙上学" 就能让AI直接排长沙）
     * @param memory 用户记忆摘要，可为null
     * @param cities 候选城市名列表
     * @return String 命中的城市名，没命中返回null
     */
    private String cityFromMemory(String memory, List<String> cities) {
        if (memory == null || memory.isBlank()) return null;
        return cities.stream().filter(memory::contains).findFirst().orElse(null);
    }

    // ==================== 打分 ====================

    /** 单景点打分结果（含可解释的理由列表） */
    private record Scored(Spot spot, double total, double interest, double heat,
                          double memory, List<String> reasons, List<String> hitTags) {}

    private Scored score(Spot s, String prefTags, String memorySummary, String mode, List<Spot> named) {
        String hay = s.getName() + " " + nullToEmpty(s.getDescription()) + " " + s.getCategory();
        List<String> reasons = new ArrayList<>();
        List<String> hitTags = new ArrayList<>();

        // 兴趣分：由档案偏好标签驱动
        double interest = 0;
        // 记忆分：由记忆摘要中的标签出现次数驱动（封顶避免长摘要拉满）
        List<Double> memoryContribs = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : TAG_ALIASES.entrySet()) {
            String tag = e.getKey();
            Map<String, Double> aff = TAG_AFFINITY.get(tag);
            if (aff == null) continue;
            double base = aff.getOrDefault(s.getCategory(), 0.15);
            boolean textHit = e.getValue().stream().anyMatch(hay::contains);
            double textBonus = textHit ? 0.3 : 0;

            // 档案标签（强度0.6）
            if (containsTag(prefTags, tag)) {
                interest = Math.min(1.0, interest + base * 0.6 + textBonus);
                if (base >= 0.6) reasons.add("与你档案里的「" + tag + "」兴趣契合");
            }
            // 记忆信号：出现次数决定强度
            if (memorySummary != null) {
                long count = e.getValue().stream().mapToInt(a -> countOccurrences(memorySummary, a)).sum();
                if (count > 0) {
                    double strength = Math.min(1.0, count / 3.0);
                    memoryContribs.add(base * strength + textBonus * strength);
                    if (base >= 0.6 && strength >= 0.33 && !hitTags.contains(tag)) {
                        hitTags.add(tag);
                        reasons.add("命中 AI 记忆标签「" + tag + "」×" + count);
                    }
                }
            }
        }
        // 主命中加次命中加权，避免多标签叠加饱和
        memoryContribs.sort(Comparator.reverseOrder());
        double memory = memoryContribs.isEmpty() ? 0
                : Math.min(1.0, memoryContribs.get(0)
                        + 0.3 * memoryContribs.subList(1, Math.min(2, memoryContribs.size()))
                                .stream().mapToDouble(Double::doubleValue).sum());

        double heat = s.getHeat() == null ? 0 : Math.min(1.0, s.getHeat() / 100.0);
        if (heat >= 0.7) {
            reasons.add("平台热度" + s.getHeat() + "，人气打卡地");
        }

        String core = MODE_CORE_CATEGORY.get(mode);
        double modeFit = core != null && core.equals(s.getCategory()) ? 1.0 : 0.4;

        double total = hasMemory(memorySummary)
                ? W_INTEREST * interest + W_HEAT * heat + W_MEMORY * memory + W_MODE * modeFit
                : 0.55 * (interest + memory) + 0.45 * heat;

        // 用户点名的景点直接置顶
        if (named != null && named.stream().anyMatch(n -> n.getId().equals(s.getId()))) {
            total = Math.max(total, 1.5);
            interest = Math.max(interest, 1.0);
            reasons.add(0, "你点名要去的「" + s.getName() + "」");
        }
        if (reasons.isEmpty()) {
            reasons.add("顺路串联周边景点，丰富行程层次");
        }
        return new Scored(s, total, interest, heat, memory, reasons, hitTags);
    }

    // ==================== AI解释层 ====================

    /**
     * 根据打分数据生成行程推荐的自然语言解释。
     * 若模型输出与算法结果矛盾（编造天数或误称已入选景点被跳过），则退回确定性文案。
     *
     * @param userText 用户原话
     * @param city 目的地城市
     * @param mode 路线模式
     * @param days 行程天数
     * @param walkAverse 是否偏好少步行
     * @param picked 已入选景点
     * @return 行程解释文案
     */
    private String explain(String userText, String city, String mode, int days,
                           boolean walkAverse, List<Scored> picked) {
        StringBuilder data = new StringBuilder();
        for (int i = 0; i < Math.min(picked.size(), 8); i++) {
            Scored sc = picked.get(i);
            data.append(String.format("%d.%s（%.2f分：兴趣%.2f 热度%.2f 记忆%.2f｜%s）%n",
                    i + 1, sc.spot.getName(), sc.total, sc.interest, sc.heat, sc.memory,
                    String.join("；", sc.reasons.subList(0, Math.min(2, sc.reasons.size())))));
        }
        String system = """
                你是「学旅圈」旅行搭子，负责解释路线算法的推荐结果。要求：
                1. 只能提到下面打分列表里出现过的景点，绝不编造或假设其他地点、其他行程；
                2. 不超过90字，口语化、像朋友聊天，用第二人称；
                3. 重点讲"为什么这些点适合 TA"（引用画像/记忆标签），如果提到怕走路就说明已放慢节奏；
                4. 天数、城市以给定数据为准，不要说成别的数字；
                5. 不要和"上一次""别的路线"作对比，不要说你舍掉了什么——列表里的点就是全部。
                """;
        String user = ("用户原话：" + userText + "\n目的地：" + city + "，" + days + " 天，模式 "
                + (walkAverse ? "LEISURE(因怕走路自动放慢)" : mode)
                + "\n本次实际入选景点（共" + picked.size() + " 个，全部会去）："
                + picked.stream().map(sc -> sc.spot.getName()).toList()
                + "\n逐站打分：\n" + data);
        String reply = aiChatService.complete(system, user);
        if (reply != null && !reply.isBlank()) {
            String clean = clip(reply.trim(), 160);
            // 与算法结果矛盾时退回确定性文案
            boolean dayLie = inventsDays(clean, days);
            boolean routeLie = contradictsRoute(clean, picked);
            if (!dayLie && !routeLie) {
                return clean;
            }
            log.info("[学旅圈] AI 解释与算法结果不符（{}），退回确定性文案：{}",
                    dayLie ? "疑似编造天数" : "把已入选景点说成被跳过/别去", clean);
        }
        // 兜底：确定性文案
        String head = "已按你的画像挑出" + picked.size() + " 个最合拍的点"
                + (walkAverse ? "，并放慢节奏、减少步行" : "");
        List<String> namedNames = picked.stream()
                .filter(sc -> sc.reasons.stream().anyMatch(r -> r.startsWith("你点名要去的")))
                .map(sc -> sc.spot.getName()).toList();
        String tail = namedNames.isEmpty() ? ""
                : "，其中「" + String.join("」「", namedNames) + "」是你点名要去的，已经排进去了";
        Scored sample = picked.stream()
                .filter(sc -> sc.reasons.stream().noneMatch(r -> r.startsWith("你点名要去的")))
                .findFirst().orElse(picked.isEmpty() ? null : picked.get(0));
        String first = sample == null ? "。"
                : "。先看「" + sample.spot.getName() + "」——" + sample.reasons.get(0) + "。";
        return head + tail + first +"顺序已按距离做过2-opt 优化，路线已经画在地图上啦。";
    }

    /** 句子级一致性检查用：负向表述 */
    private static final java.util.regex.Pattern NEG_PATTERN = java.util.regex.Pattern.compile(
            "跳过|跳开|避开|别去|不要去|不去|排除|略过|省掉|舍弃|就不要|可以不");

    /**
     * 判断解释是否将已入选景点说成被跳过/避开。
     * 若某句同时含负向词与本次入选景点名称，则判定为矛盾。
     * @param text 模型生成的解释文案
     * @param picked 本次入选的景点
     * @return boolean true表示存在把入选景点说成跳过或避开的句子，false表示没有矛盾
     */
    private static boolean contradictsRoute(String text, List<Scored> picked) {
        for (String sentence : text.split("[。！？；;]")) {
            if (!NEG_PATTERN.matcher(sentence).find()) continue;
            for (Scored sc : picked) {
                if (sentence.contains(sc.spot.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 按最大长度裁剪，且不把emoji的代理对从中间截断——否则响应体会出现非法UTF-8，前端JSON.parse直接失败
     * @param s 原文本
     * @param max 最大字符数
     * @return String 裁剪后的文本，不会从代理对中间截断
     */
    private static String clip(String s, int max) {
        if (s.length() <= max) return s;
        int end = max;
        if (Character.isHighSurrogate(s.charAt(end - 1))) end--;
        return s.substring(0, end);
    }

    /**
     * 判断解释中提到的天数是否超过实际行程天数。
     * @param text 模型生成的解释文案
     * @param days 实际行程天数
     * @return boolean true表示解释里提到的天数超过实际天数，false表示没有超过
     */
    private static boolean inventsDays(String text, int days) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("([一二两三四五六七八九十]|\\d+)\\s*[天日]").matcher(text);
        while (m.find()) {
            if (cnNum(m.group(1)) > days) return true;
        }
        return false;
    }

    private static int cnNum(String s) {
        switch (s) {
            case "一": return 1;
            case "两", "二": return 2;
            case "三": return 3;
            case "四": return 4;
            case "五": return 5;
            case "六": return 6;
            case "七": return 7;
            case "八": return 8;
            case "九": return 9;
            case "十": return 10;
            default:
                try {
                    return Integer.parseInt(s);
                } catch (NumberFormatException e) {
                    return 0;
                }
        }
    }

    // ==================== 小工具 ====================

    /**
     * 本地规则兜底解析：提取city/mode/days等关键信息
     * @param text 用户输入
     * @param cities 候选城市名列表
     * @return Map 解析出的city、tripDays、mode等字段
     */
    private Map<String, Object> localRuleParse(String text, List<String> cities) {
        Map<String, Object> hit = new LinkedHashMap<>();
        cities.stream().filter(text::contains).findFirst().ifPresent(c -> hit.put("city", c));
        var dm = java.util.regex.Pattern.compile("(\\d+)\\s*天").matcher(text);
        if (dm.find()) {
            hit.put("tripDays", Math.min(7, Math.max(1, Integer.parseInt(dm.group(1)))));
        }
        if (text.matches(".*(美食|好吃|吃).*")) hit.put("mode", "FOOD");
        else if (text.matches(".*(休闲|慢游|放松|松一点).*")) hit.put("mode", "LEISURE");
        else if (text.matches(".*(速通|特种兵|赶时间).*")) hit.put("mode", "SPEED");
        return hit;
    }

    private boolean containsTag(String prefTags, String tag) {
        return prefTags != null && (prefTags.contains(tag) || TAG_ALIASES.getOrDefault(tag, List.of())
                .stream().anyMatch(prefTags::contains));
    }

    private boolean hasMemory(String m) {
        return m != null && !m.isBlank();
    }

    /**
     * 子串出现次数（记忆强度度量）
     * @param text 待检索的文本
     * @param sub 要统计的子串
     * @return int 子串出现的次数
     */
    private int countOccurrences(String text, String sub) {
        int n = 0, idx = 0;
        if (sub == null || sub.isEmpty()) return 0;
        while ((idx = text.indexOf(sub, idx)) >= 0) {
            n++;
            idx += sub.length();
        }
        return n;
    }

    private Integer parseStart(String hhmm) {
        if (hhmm == null || !hhmm.matches("\\d{1,2}:\\d{2}")) return null;
        return Integer.parseInt(hhmm.split(":")[0]) * 60 + Integer.parseInt(hhmm.split(":")[1]);
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private String brief(String s, int max) {
        String one = s.replace("\n", " ").replaceAll("\\s+", " ").trim();
        return one.length() > max ? one.substring(0, max) + "…" : one;
    }
}
