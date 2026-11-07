package com.xuelvquan.travel.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xuelvquan.travel.common.BusinessException;
import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.entity.*;
import com.xuelvquan.travel.repository.CreditLogRepository;
import com.xuelvquan.travel.repository.EmergencyContactRepository;
import com.xuelvquan.travel.repository.FootprintRepository;
import com.xuelvquan.travel.repository.TripMemberRepository;
import com.xuelvquan.travel.repository.TripRepository;
import com.xuelvquan.travel.repository.UserRepository;
import com.xuelvquan.travel.security.AuthContext;
import com.xuelvquan.travel.security.AuthUtil;
import com.xuelvquan.travel.service.ExpenseService;
import com.xuelvquan.travel.service.NotifyService;
import com.xuelvquan.travel.service.TripMatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
@Transactional
public class TripController {

    private final TripRepository tripRepository;
    private final TripMemberRepository memberRepository;
    private final FootprintRepository footprintRepository;
    private final UserRepository userRepository;
    private final CreditLogRepository creditLogRepository;
    private final NotifyService notifyService;
    private final TripMatchService matchService;
    private final ExpenseService expenseService;
    private final ObjectMapper objectMapper;
    private final EmergencyContactRepository emergencyContactRepository;

    /** 三校编码，用于按学校筛选校验 */
    private static final Set<String> SCHOOLS = Set.of("CSU", "HUNNU", "HNU");

    // ============================================================
    // 列表/详情
    // ============================================================

    /**
     * 结伴列表（支持按学校与匹配度筛选）
     * @param destination 目的地筛选条件
     * @param status 行程状态筛选条件
     * @param school 学校筛选：ALL/空为三校全部，MINE为我的学校，CSU/HUNNU/HNU为指定学校
     * @param sort 排序方式：match表示按偏好匹配度排序（需登录）
     * @return Result<List<Map<String,Object>>> 结伴列表
     */
    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String destination,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String school,
                                                  @RequestParam(required = false) String sort) {
        // 仅前台游客及前台用户可见，管理员与商家不可见
        AuthUtil.requireFrontOrGuest();
        Long viewer = AuthContext.getUserId();
        String schoolFilter = resolveSchoolFilter(school, viewer);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Trip t : tripRepository.search(blankToNull(destination), blankToNull(status), schoolFilter)) {
            result.add(tripCard(t, viewer));
        }
        if ("match".equals(sort) && viewer != null) {
            result.sort(Comparator.comparingInt(
                    m -> -((Number) m.getOrDefault("matchScore", 0)).intValue()));
        }
        return Result.ok(result);
    }

    /** 获取我的结伴偏好 */
    @GetMapping("/pref")
    public Result<Map<String, Object>> myPref() {
        Long userId = AuthUtil.requireUserId();
        return Result.ok(matchService.prefDetail(userId));
    }

    /**
     * 动态兴趣画像：基于足迹、发帖、报名、圈子推断的兴趣与偏好，含标签得分、来源与证据
     * @return Result<Map<String,Object>> 当前用户的兴趣画像
     */
    @GetMapping("/pref/profile")
    public Result<Map<String, Object>> prefProfile() {
        Long userId = AuthUtil.requireUserId();
        return Result.ok(matchService.profileView(userId));
    }

    @PutMapping("/pref")
    public Result<Map<String, Object>> savePref(@RequestBody Map<String, Object> body) {
        Long userId = AuthUtil.requireUserId();
        matchService.savePref(userId, body);
        return Result.ok(matchService.prefDetail(userId));
    }

    /**
     * 为当前用户推荐Top N结伴团队
     * @param limit 推荐数量，默认3，最大10
     * @return Result<List<Map<String,Object>>> 推荐结伴团队列表及匹配度
     */
    @GetMapping("/recommend")
    public Result<List<Map<String, Object>>> recommend(@RequestParam(defaultValue = "3") Integer limit) {
        Long userId = AuthUtil.requireUserId();
        int n = limit == null || limit < 1 ? 3 : Math.min(limit, 10);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> m : matchService.recommend(userId, n)) {
            Map<String, Object> item = new LinkedHashMap<>();
            Trip t = (Trip) m.get("trip");
            item.put("trip", tripCard(t, userId));
            item.put("matchScore", m.get("matchScore"));
            item.put("recommendScore", m.get("recommendScore"));
            item.put("dims", m.get("dims"));
            result.add(item);
        }
        return Result.ok(result);
    }

    @PostMapping
    public Result<Trip> create(@RequestBody Map<String, Object> body) {
        // 商家不参与结伴出行
        AuthUtil.requireBusiness();
        Long userId = requireVerified();
        User me = userRepository.findById(userId).orElseThrow();
        if (me.getCreditScore() == null || me.getCreditScore() < 60) {
            throw new BusinessException("信誉分低于60，暂不能发布结伴");
        }
        requireEmergencyContact(userId, "发布结伴行程");
        Trip t = new Trip();
        t.setCaptainId(userId);
        t.setTitle((String) body.get("title"));
        t.setDestination((String) body.get("destination"));
        t.setDepartAt(parseDateTime(body.get("departAt")));
        t.setMaxMembers(body.get("maxMembers") == null ? 6 : toInt(body.get("maxMembers")));
        t.setBudget(body.get("budget") == null ? null : toDouble(body.get("budget")));
        t.setTransport((String) body.get("transport"));
        t.setMeetPoint((String) body.get("meetPoint"));
        t.setDescription((String) body.get("description"));
        t.setRiskLevel(body.get("riskLevel") == null ? "LOW" : (String) body.get("riskLevel"));
        t.setPaceType(body.get("paceType") == null ? TripMatchService.PACE_BALANCED : String.valueOf(body.get("paceType")));
        t.setPrefDays(body.get("prefDays") == null ? null : toInt(body.get("prefDays")));
        t.setInterestTags(body.get("interestTags") == null ? null : String.valueOf(body.get("interestTags")));
        t.setSchoolScope("SAME".equals(body.get("schoolScope")) ? "SAME" : "ALL");
        t.setStatus("OPEN");
        t.setCreatedAt(LocalDateTime.now());
        if (t.getTitle() == null || t.getDestination() == null) {
            throw new BusinessException("标题和目的地不能为空");
        }
        return Result.ok(tripRepository.save(t));
    }

    @GetMapping("/my")
    public Result<List<Map<String, Object>>> my() {
        // 学生/博主/导游的「我的结伴」，商家与管理员不参与
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        Map<Long, Trip> map = new LinkedHashMap<>();
        for (Trip t : tripRepository.findByCaptainIdOrderByCreatedAtDesc(userId)) {
            map.put(t.getId(), t);
        }
        for (Long tripId : memberRepository.findTripIdsByUserId(userId)) {
            if (tripId == null) {
                continue;
            }
            tripRepository.findById(tripId).ifPresent(t -> map.put(t.getId(), t));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        map.values().stream()
                .sorted(Comparator.comparing(Trip::getCreatedAt).reversed())
                .forEach(t -> result.add(tripCard(t, userId)));
        return Result.ok(result);
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        AuthUtil.requireFrontOrGuest();
        Trip t = mustGet(id);
        Map<String, Object> m = objectMapper.convertValue(t, new TypeReference<LinkedHashMap<String, Object>>() {});
        userRepository.findById(t.getCaptainId()).ifPresent(u -> m.put("captain", briefUser(u)));

        List<Map<String, Object>> members = new ArrayList<>();
        for (TripMember tm : memberRepository.findByTripId(id)) {
            Map<String, Object> mm = objectMapper.convertValue(tm, new TypeReference<LinkedHashMap<String, Object>>() {});
            userRepository.findById(tm.getUserId()).ifPresent(u -> {
                mm.put("nickname", u.getNickname());
                mm.put("avatar", u.getAvatar());
                mm.put("school", u.getSchool());
            });
            members.add(mm);
        }
        m.put("members", members);
        m.put("approvedCount", memberRepository.countByTripIdAndStatus(id, "APPROVED"));

        Long me = AuthContext.getUserId();
        String myStatus = null;
        if (me != null) {
            if (me.equals(t.getCaptainId())) {
                myStatus = "CAPTAIN";
            } else {
                myStatus = memberRepository.findByTripIdAndUserId(id, me)
                        .map(TripMember::getStatus).orElse(null);
            }
        }
        m.put("myMemberStatus", myStatus);
        // AA账单复用团队总支出数据源（expenses表）
        m.put("expenses", expenseService.teamExpenses(id));

        // 登录且非队长时，附上我的偏好匹配度
        if (me != null && !me.equals(t.getCaptainId())) {
            userRepository.findById(me).ifPresent(u -> m.put("myMatch", matchService.matchOf(t, u)));
        }
        // 队长视角：附上各报名者匹配度快照
        if (me != null && me.equals(t.getCaptainId())) {
            m.put("matchMap", matchService.matchMapOfTrip(id));
        }
        return Result.ok(m);
    }

    /**
     * 队长团队管理页：聚合队员、足迹与消费（队长或队员可访问）
     * @param id 行程ID
     * @return Result<Map<String,Object>> 团队概览，含成员、足迹时间轴与消费统计
     */
    @GetMapping("/{id}/team")
    public Result<Map<String, Object>> teamOverview(@PathVariable Long id) {
        // 仅前台用户可访问，商家与管理员不参与
        AuthUtil.requireBusiness();
        Long me = AuthUtil.requireUserId();
        Trip t = mustGet(id);

        List<TripMember> records = memberRepository.findByTripId(id);
        boolean isCaptain = Objects.equals(t.getCaptainId(), me);
        TripMember mine = records.stream()
                .filter(m -> Objects.equals(m.getUserId(), me))
                .findFirst().orElse(null);
        if (!isCaptain && mine == null) {
            throw new BusinessException(403, "仅队长与队员可查看团队管理");
        }

        // 成员集合：队长+全部报名记录
        List<Map<String, Object>> members = new ArrayList<>();
        LinkedHashSet<Long> memberIds = new LinkedHashSet<>();
        if (t.getCaptainId() != null) {
            memberIds.add(t.getCaptainId());
            members.add(memberRow(t.getCaptainId(), "CAPTAIN", null, t.getCaptainId()));
        }
        for (TripMember m : records) {
            if (m.getUserId() == null || Objects.equals(m.getUserId(), t.getCaptainId())) {
                continue; // 队长已在首行，跳过重复
            }
            memberIds.add(m.getUserId());
            members.add(memberRow(m.getUserId(), m.getStatus(), m, t.getCaptainId()));
        }

        // 批量获取全队足迹，并按用户分组
        List<Footprint> allFp = memberIds.isEmpty()
                ? List.of()
                : footprintRepository.findByUserIdInOrderByVisitedAtDesc(memberIds);
        Map<Long, List<Footprint>> fpByUser = new LinkedHashMap<>();
        for (Footprint f : allFp) {
            if (f.getUserId() == null) {
                continue;
            }
            fpByUser.computeIfAbsent(f.getUserId(), k -> new ArrayList<>()).add(f);
        }

        // 消费统计：复用团队报表（队长与已通过成员的AA分摊口径）
        Map<Long, Map<String, Object>> expenseByUser = new HashMap<>();
        double expenseTotal = 0;
        double avgPerPerson = 0;
        try {
            Map<String, Object> report = expenseService.teamReport(id);
            if (report != null) {
                if (report.get("total") instanceof Number n) {
                    expenseTotal = n.doubleValue();
                }
                if (report.get("avgPerPerson") instanceof Number n) {
                    avgPerPerson = n.doubleValue();
                }
                if (report.get("members") instanceof List<?> list) {
                    for (Object o : list) {
                        if (o instanceof Map<?, ?> row && row.get("userId") instanceof Number un) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> mm = (Map<String, Object>) row;
                            expenseByUser.put(un.longValue(), mm);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // 团队报表异常不影响队员与足迹主体信息
        }

        // 将足迹摘要与消费状态挂到每位成员上
        int footprintTotal = 0;
        int approvedCount = 0;
        int pendingCount = 0;
        for (Map<String, Object> m : members) {
            Long uid = (Long) m.get("userId");
            List<Footprint> fps = fpByUser.getOrDefault(uid, List.of());
            footprintTotal += fps.size();

            Map<String, Object> fp = new LinkedHashMap<>();
            fp.put("count", fps.size());
            List<String> cities = new ArrayList<>();
            for (Footprint f : fps) {
                if (f.getCity() != null && !f.getCity().isBlank() && !cities.contains(f.getCity())) {
                    cities.add(f.getCity());
                }
            }
            fp.put("cityCount", cities.size());
            fp.put("cities", cities);
            Footprint last = fps.isEmpty() ? null : fps.get(0);
            fp.put("lastName", last == null ? null : last.getName());
            fp.put("lastCity", last == null ? null : last.getCity());
            fp.put("lastVisitedAt", last == null ? null : last.getVisitedAt());
            m.put("footprint", fp);

            Map<String, Object> ex = expenseByUser.get(uid);
            Map<String, Object> expense = new LinkedHashMap<>();
            expense.put("paid", ex == null ? 0.0 : ex.getOrDefault("paid", 0.0));
            expense.put("owed", ex == null ? 0.0 : ex.getOrDefault("owed", 0.0));
            expense.put("net", ex == null ? 0.0 : ex.getOrDefault("net", 0.0));
            m.put("expense", expense);

            String status = String.valueOf(m.get("status"));
            if ("APPROVED".equals(status) || "CAPTAIN".equals(status)) {
                approvedCount++;
            }
            if ("PENDING".equals(status)) {
                pendingCount++;
            }
        }

        // 全队足迹时间轴（按到访日期倒序，最多100条）
        Map<Long, Map<String, Object>> byId = new HashMap<>();
        for (Map<String, Object> m : members) {
            byId.put((Long) m.get("userId"), m);
        }
        List<Footprint> timelineSrc = new ArrayList<>(allFp);
        timelineSrc.sort(Comparator.comparing(
                (Footprint f) -> f.getVisitedAt() == null ? LocalDate.MIN : f.getVisitedAt()).reversed());
        List<Map<String, Object>> timeline = new ArrayList<>();
        for (Footprint f : timelineSrc) {
            if (timeline.size() >= 100) {
                break;
            }
            Map<String, Object> owner = byId.get(f.getUserId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", f.getUserId());
            row.put("nickname", owner == null ? "队员" : owner.get("nickname"));
            row.put("avatar", owner == null ? null : owner.get("avatar"));
            row.put("name", f.getName());
            row.put("city", f.getCity());
            row.put("visitedAt", f.getVisitedAt());
            row.put("note", f.getNote());
            row.put("photo", f.getPhoto());
            timeline.add(row);
        }

        // 行程概要
        Map<String, Object> trip = new LinkedHashMap<>();
        trip.put("id", t.getId());
        trip.put("title", t.getTitle());
        trip.put("destination", t.getDestination());
        trip.put("departAt", t.getDepartAt());
        trip.put("maxMembers", t.getMaxMembers());
        trip.put("budget", t.getBudget());
        trip.put("status", t.getStatus());
        trip.put("riskLevel", t.getRiskLevel());
        trip.put("paceType", t.getPaceType());
        trip.put("interestTags", t.getInterestTags());
        trip.put("prefDays", t.getPrefDays());
        trip.put("captainId", t.getCaptainId());
        trip.put("meetPoint", t.getMeetPoint());
        trip.put("transport", t.getTransport());

        double budget = t.getBudget() == null ? 0 : t.getBudget();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("trip", trip);
        out.put("isCaptain", isCaptain);
        out.put("myStatus", isCaptain ? "CAPTAIN" : (mine == null ? null : mine.getStatus()));
        out.put("memberCount", members.size());
        out.put("approvedCount", approvedCount);
        out.put("pendingCount", pendingCount);
        out.put("footprintTotal", footprintTotal);
        out.put("expenseTotal", round2(expenseTotal));
        out.put("avgPerPerson", round2(avgPerPerson));
        out.put("budgetUsage", (budget > 0 && expenseTotal > 0)
                ? Math.round(expenseTotal / budget * 1000) / 10.0 : null);
        out.put("members", members);
        out.put("footprintTimeline", timeline);
        return Result.ok(out);
    }

    /**
     * 团队管理页成员行：用户资料、报名状态与位置共享
     * @param userId 用户ID
     * @param status 报名状态
     * @param m 报名记录，队长为null
     * @param captainId 队长用户ID
     * @return Map<String,Object> 成员行数据
     */
    private Map<String, Object> memberRow(Long userId, String status, TripMember m, Long captainId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("userId", userId);
        // 报名记录主键，队长审核时用
        row.put("memberId", m == null ? null : m.getId());
        row.put("status", status);
        row.put("isCaptain", Objects.equals(userId, captainId));
        row.put("creditSnapshot", m == null ? null : m.getCreditSnapshot());
        row.put("reason", m == null ? null : m.getReason());
        row.put("lng", m == null ? null : m.getLng());
        row.put("lat", m == null ? null : m.getLat());
        row.put("locationAt", m == null ? null : m.getLocationAt());
        userRepository.findById(userId).ifPresent(u -> {
            row.put("nickname", u.getNickname());
            row.put("avatar", u.getAvatar());
            row.put("school", u.getSchool());
            row.put("verified", u.getVerified());
            row.put("creditScore", u.getCreditScore());
        });
        return row;
    }

    private double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    // ============================================================
    // 组队经历
    // ============================================================

    private static final Map<String, String> TRIP_STATUS_TEXT = Map.of(
            "OPEN", "招募中", "FULL", "已满员", "PLANNING", "筹备中",
            "ONGOING", "进行中", "FINISHED", "已完成", "CANCELLED", "已取消");

    /**
     * 组队经历：本队全体成员的历史结伴记录
     * @param id 行程ID
     * @return Result<List<Map<String,Object>>> 成员历史行程列表（剔除当前行程）
     */
    @GetMapping("/{id}/histories")
    public Result<List<Map<String, Object>>> histories(@PathVariable Long id) {
        // 登录前台用户可见，游客不可见
        AuthUtil.requireBusiness();
        AuthUtil.requireUserId();
        Trip t = mustGet(id);

        // 成员集合：队长优先，其后为已通过队员
        LinkedHashSet<Long> uids = new LinkedHashSet<>();
        if (t.getCaptainId() != null) {
            uids.add(t.getCaptainId());
        }
        for (TripMember m : memberRepository.findByTripIdAndStatus(id, "APPROVED")) {
            if (m.getUserId() != null) {
                uids.add(m.getUserId());
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Long uid : uids) {
            User u = userRepository.findById(uid).orElse(null);
            if (u == null) {
                continue;
            }
            List<Map<String, Object>> items = new ArrayList<>();
            int asCaptain = 0;
            int asMember = 0;
            // 作为队长的历史行程
            for (Trip h : tripRepository.findByCaptainIdOrderByCreatedAtDesc(uid)) {
                if (Objects.equals(h.getId(), id)) continue;
                asCaptain++;
                items.add(historyItem(h, "队长"));
            }
            // 作为队员的历史行程（仅统计通过审核的）
            for (TripMember tm : memberRepository.findByUserId(uid)) {
                if (!"APPROVED".equals(tm.getStatus()) || Objects.equals(tm.getTripId(), id)) continue;
                Trip h = tripRepository.findById(tm.getTripId()).orElse(null);
                if (h == null) continue;
                asMember++;
                items.add(historyItem(h, "队员"));
            }
            items.sort(Comparator.comparing(
                    (Map<String, Object> x) -> (String) x.get("departAt"),
                    Comparator.nullsLast(Comparator.reverseOrder())));

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", uid);
            row.put("nickname", u.getNickname());
            row.put("avatar", u.getAvatar());
            row.put("school", u.getSchool());
            row.put("creditScore", u.getCreditScore());
            row.put("isCaptain", Objects.equals(t.getCaptainId(), uid));
            row.put("asCaptain", asCaptain);
            row.put("asMember", asMember);
            row.put("list", items);
            result.add(row);
        }
        // 队长排最前
        result.sort(Comparator.comparing(r -> !Boolean.TRUE.equals(r.get("isCaptain"))));
        return Result.ok(result);
    }

    private Map<String, Object> historyItem(Trip h, String role) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tripId", h.getId());
        m.put("title", h.getTitle());
        m.put("destination", h.getDestination());
        m.put("departAt", h.getDepartAt() == null ? null : h.getDepartAt().toLocalDate().toString());
        m.put("statusText", TRIP_STATUS_TEXT.getOrDefault(h.getStatus(), h.getStatus()));
        m.put("role", role);
        return m;
    }

    @PostMapping("/{id}/apply")
    public Result<TripMember> apply(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        // 商家不参与结伴出行
        AuthUtil.requireBusiness();
        Long userId = requireVerified();
        Trip t = mustGet(id);
        User me = userRepository.findById(userId).orElseThrow();
        if (me.getCreditScore() == null || me.getCreditScore() < 60) {
            throw new BusinessException("信誉分低于60，暂不能报名结伴");
        }
        requireEmergencyContact(userId, "报名结伴行程");
        if (!"OPEN".equals(t.getStatus())) {
            throw new BusinessException("该行程不在报名状态（已满员或已出发）");
        }
        // 仅限本校的队伍不接收外校报名
        if ("SAME".equals(t.getSchoolScope())) {
            String captainSchool = userRepository.findById(t.getCaptainId())
                    .map(User::getSchool).orElse(null);
            if (captainSchool != null && !captainSchool.equals(me.getSchool())) {
                throw new BusinessException("该队伍仅限本校同学报名");
            }
        }
        Optional<TripMember> existing = memberRepository.findByTripIdAndUserId(id, userId);
        if (existing.isPresent() && !"REJECTED".equals(existing.get().getStatus())) {
            throw new BusinessException("已提交过报名申请");
        }
        long approved = memberRepository.countByTripIdAndStatus(id, "APPROVED");
        if (approved >= nz(t.getMaxMembers())) {
            throw new BusinessException("队伍成员已满");
        }
        TripMember tm = existing.orElseGet(() -> {
            TripMember x = new TripMember();
            x.setTripId(id);
            x.setUserId(userId);
            x.setCreatedAt(LocalDateTime.now());
            return x;
        });
        tm.setStatus("PENDING");
        tm.setReason(body.get("reason") == null ? null : String.valueOf(body.get("reason")));
        tm.setCreditSnapshot(me.getCreditScore());
        TripMember saved = memberRepository.save(tm);

        // 报名改变兴趣画像，先失效缓存
        matchService.invalidateProfile(userId);

        // 附带/更新结伴偏好并落匹配度快照
        if (body.get("pref") instanceof Map<?, ?> prefBody) {
            matchService.savePref(userId, objectMapper.convertValue(prefBody, new TypeReference<Map<String, Object>>() {}));
        }
        matchService.snapshot(t, userId, null, "APPLY");

        notifyService.notify(t.getCaptainId(), "TRIP", "新的组队报名",
                me.getNickname() + " 申请加入「" + t.getTitle() + "」", t.getId());
        return Result.ok(saved);
    }

    @PostMapping("/{id}/members/{memberId}/review")
    public Result<TripMember> review(@PathVariable Long id,
                                     @PathVariable Long memberId,
                                     @RequestBody Map<String, String> body) {
        Long userId = AuthUtil.requireUserId();
        Trip t = mustGet(id);
        if (!t.getCaptainId().equals(userId)) {
            throw new BusinessException(403, "仅队长可审核报名");
        }
        TripMember tm = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException("报名记录不存在"));
        if (!tm.getTripId().equals(id)) {
            throw new BusinessException("报名记录与行程不匹配");
        }
        String action = body.get("action");
        if (!"APPROVED".equals(action) && !"REJECTED".equals(action)) {
            throw new BusinessException("action 必须为 APPROVED 或 REJECTED");
        }
        if ("APPROVED".equals(action)
                && memberRepository.countByTripIdAndStatus(id, "APPROVED") >= nz(t.getMaxMembers())
                && !"APPROVED".equals(tm.getStatus())) {
            throw new BusinessException("队伍成员已满，无法通过");
        }
        tm.setStatus(action);
        TripMember saved = memberRepository.save(tm);

        if ("APPROVED".equals(action)
                && memberRepository.countByTripIdAndStatus(id, "APPROVED") >= nz(t.getMaxMembers())) {
            t.setStatus("FULL");
            tripRepository.save(t);
        }
        notifyService.notify(tm.getUserId(), "TRIP",
                "APPROVED".equals(action) ? "报名通过" : "报名未通过",
                "你对「" + t.getTitle() + "」的报名" + ("APPROVED".equals(action) ? "已通过，请准时集合" : "被队长婉拒"),
                t.getId());
        return Result.ok(saved);
    }

    @PostMapping("/{id}/status")
    public Result<Trip> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Long userId = AuthUtil.requireUserId();
        Trip t = mustGet(id);
        if (!t.getCaptainId().equals(userId)) {
            throw new BusinessException(403, "仅队长可更新行程状态");
        }
        String status = body.get("status");
        // 状态机：OPEN/FULL（招募中）→ ONGOING（进行中）→ FINISHED（已结束）；招募中可直接CANCELLED
        if (!"ONGOING".equals(status) && !"FINISHED".equals(status) && !"CANCELLED".equals(status)) {
            throw new BusinessException("status 必须为 ONGOING / FINISHED / CANCELLED");
        }
        if ("ONGOING".equals(status) && !"OPEN".equals(t.getStatus()) && !"FULL".equals(t.getStatus())) {
            throw new BusinessException("仅招募中的行程可以开始");
        }
        if ("FINISHED".equals(status) && !"ONGOING".equals(t.getStatus())) {
            throw new BusinessException("仅进行中的行程可以结束");
        }
        if ("CANCELLED".equals(status) && "FINISHED".equals(t.getStatus())) {
            throw new BusinessException("已结束的行程不能取消");
        }
        t.setStatus(status);
        Trip saved = tripRepository.save(t);
        if ("CANCELLED".equals(status)) {
            for (TripMember tm : memberRepository.findByTripIdAndStatus(id, "APPROVED")) {
                notifyService.notify(tm.getUserId(), "TRIP", "行程已取消",
                        "「" + t.getTitle() + "」已被队长取消，请留意后续安排", t.getId());
            }
        }
        return Result.ok(saved);
    }

    @PostMapping("/{id}/share-location")
    public Result<TripMember> shareLocation(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long userId = AuthUtil.requireUserId();
        Trip t = mustGet(id);
        TripMember tm = memberRepository.findByTripIdAndUserId(id, userId).orElseGet(() -> {
            if (!userId.equals(t.getCaptainId())) {
                throw new BusinessException("你未加入该行程");
            }
            TripMember x = new TripMember();
            x.setTripId(id);
            x.setUserId(userId);
            x.setStatus("APPROVED");
            x.setCreatedAt(LocalDateTime.now());
            return x;
        });
        tm.setLng(toDouble(body.get("lng")));
        tm.setLat(toDouble(body.get("lat")));
        tm.setLocationAt(LocalDateTime.now());
        return Result.ok(memberRepository.save(tm));
    }

    @PostMapping("/{id}/expenses")
    public Result<Map<String, Object>> expenses(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long userId = AuthUtil.requireUserId();
        Trip t = mustGet(id);
        if (!t.getCaptainId().equals(userId)) {
            throw new BusinessException(403, "仅队长可登记费用");
        }
        double amount = toDouble(body.get("amount"));
        List<?> rawPayers = (List<?>) body.getOrDefault("payerIds", List.of());
        if (rawPayers.isEmpty()) {
            throw new BusinessException("分摊人不能为空");
        }
        List<Long> payerIds = rawPayers.stream().map(this::toLong).toList();

        // 团队总支出/AA结算/行程详情AA账单共用expenses表
        if (amount > 0) {
            expenseService.addTeamExpense(t.getId(), String.valueOf(body.get("title")),
                    amount, payerIds, userId);
        }
        return Result.ok(Map.of("expenses", expenseService.teamExpenses(t.getId())));
    }

    @PostMapping("/{id}/rate")
    public Result<TripMember> rate(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long fromUserId = AuthUtil.requireUserId();
        Trip t = mustGet(id);
        if (!"FINISHED".equals(t.getStatus())) {
            throw new BusinessException("仅已结束的行程可以互评");
        }
        Long targetId = toLong(body.get("userId"));
        int score = toInt(body.get("score"));
        if (score < 1 || score > 5) {
            throw new BusinessException("评分必须在1-5之间");
        }
        if (targetId.equals(fromUserId)) {
            throw new BusinessException("不能评价自己");
        }
        // 校验双方均为参与者（队长或已通过成员）
        if (!isParticipant(t, fromUserId) || !isParticipant(t, targetId)) {
            throw new BusinessException(403, "仅行程成员可互评");
        }
        TripMember target = memberRepository.findByTripIdAndUserId(id, targetId).orElseGet(() -> {
            TripMember x = new TripMember();
            x.setTripId(id);
            x.setUserId(targetId);
            x.setStatus("APPROVED");
            x.setCreatedAt(LocalDateTime.now());
            return x;
        });
        if (target.getScore() != null) {
            throw new BusinessException("已评价过该成员");
        }
        target.setScore(score);
        target.setRateComment(body.get("comment") == null ? null : String.valueOf(body.get("comment")));
        TripMember saved = memberRepository.save(target);

        int delta = (score - 3) * 2;
        User targetUser = userRepository.findById(targetId).orElseThrow();
        targetUser.setCreditScore(nz(targetUser.getCreditScore()) + delta);
        userRepository.save(targetUser);
        CreditLog log = new CreditLog();
        log.setUserId(targetId);
        log.setDelta(delta);
        log.setReason("行程「" + t.getTitle() + "」成员互评得分" + score);
        log.setCreatedAt(LocalDateTime.now());
        creditLogRepository.save(log);
        return Result.ok(saved);
    }

    // ================= helpers =================

    /**
     * 校验用户已填写紧急联系人，否则禁止发布/报名结伴
     * @param userId 用户ID
     * @param action 触发动作描述（如"发布结伴行程"），用于错误提示
     */
    private void requireEmergencyContact(Long userId, String action) {
        if (emergencyContactRepository.findByUserId(userId).isEmpty()) {
            throw new BusinessException("请先在「安全中心」添加紧急联系人，再" + action);
        }
    }

    private boolean isParticipant(Trip t, Long userId) {
        if (userId.equals(t.getCaptainId())) {
            return true;
        }
        return memberRepository.findByTripIdAndUserId(t.getId(), userId)
                .filter(m -> "APPROVED".equals(m.getStatus())).isPresent();
    }

    private Long requireVerified() {
        Long userId = AuthUtil.requireUserId();
        User u = userRepository.findById(userId).orElseThrow(() -> new BusinessException(401, "用户不存在"));
        if (!Boolean.TRUE.equals(u.getVerified())) {
            throw new BusinessException("请先完成三校学籍认证");
        }
        return userId;
    }

    private Trip mustGet(Long id) {
        return tripRepository.findById(id).orElseThrow(() -> new BusinessException("行程不存在"));
    }

    private Map<String, Object> tripCard(Trip t) {
        return tripCard(t, null);
    }

    /**
     * 构造结伴列表卡片：附加队长信息、已通过人数与偏好匹配度
     * @param t 行程对象
     * @param viewerId 查看者用户ID，为null时不计算匹配度
     * @return Map<String,Object> 列表卡片数据
     */
    private Map<String, Object> tripCard(Trip t, Long viewerId) {
        Map<String, Object> m = objectMapper.convertValue(t, new TypeReference<LinkedHashMap<String, Object>>() {});
        userRepository.findById(t.getCaptainId()).ifPresent(u -> m.put("captain", briefUser(u)));
        m.put("approvedCount", memberRepository.countByTripIdAndStatus(t.getId(), "APPROVED"));
        if (viewerId != null && !viewerId.equals(t.getCaptainId())) {
            userRepository.findById(viewerId).ifPresent(u -> {
                Map<String, Object> match = matchService.matchOf(t, u);
                m.put("matchScore", match.get("matchScore"));
                m.put("recommendScore", match.get("recommendScore"));
            });
        }
        return m;
    }

    /**
     * 解析按学校筛选参数
     * @param school 学校筛选参数
     * @param viewerId 当前用户ID
     * @return String 解析后的学校编码，无筛选返回null
     */
    private String resolveSchoolFilter(String school, Long viewerId) {
        if (school == null || school.isBlank() || "ALL".equalsIgnoreCase(school)) {
            return null;
        }
        if ("MINE".equalsIgnoreCase(school)) {
            if (viewerId == null) {
                return null;
            }
            return userRepository.findById(viewerId).map(User::getSchool).orElse(null);
        }
        String s = school.trim().toUpperCase();
        return SCHOOLS.contains(s) ? s : null;
    }

    private Map<String, Object> briefUser(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("nickname", u.getNickname());
        m.put("avatar", u.getAvatar());
        m.put("school", u.getSchool());
        m.put("verified", u.getVerified());
        return m;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseExpenses(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private LocalDateTime parseDateTime(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).replace(" ", "T");
        return LocalDateTime.parse(s);
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        return Long.valueOf(o.toString());
    }

    private int toInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        return Integer.parseInt(o.toString());
    }

    private double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        return Double.parseDouble(o.toString());
    }

    private int nz(Integer i) {
        return i == null ? 0 : i;
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
