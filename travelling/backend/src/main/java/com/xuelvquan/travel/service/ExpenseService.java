package com.xuelvquan.travel.service;

import com.xuelvquan.travel.common.BusinessException;
import com.xuelvquan.travel.entity.Expense;
import com.xuelvquan.travel.entity.Trip;
import com.xuelvquan.travel.entity.User;
import com.xuelvquan.travel.repository.ExpenseRepository;
import com.xuelvquan.travel.repository.TripMemberRepository;
import com.xuelvquan.travel.repository.TripRepository;
import com.xuelvquan.travel.repository.UserRepository;
import com.xuelvquan.travel.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 消费与费用管理（个人消费报表+团队共享消费报表+AA分摊结算）。
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final CreditService creditService;

    private static final Map<String, String> CATEGORY_NAMES = Map.of(
            "TRANSPORT", "交通",
            "HOTEL", "住宿",
            "FOOD", "餐饮",
            "TICKET", "门票",
            "SHOPPING", "购物",
            "OTHER", "其他"
    );

    /**
     * 记一笔消费
     * @param body 消费表单，含scope、tripId、category、title、amount、sharers、remark等字段
     * @return Expense 保存后的消费记录，金额不大于0时抛出BusinessException
     */
    public Expense add(Map<String, Object> body) {
        Long me = AuthUtil.requireUserId();
        Expense e = new Expense();
        String scope = str(body.get("scope"), "PERSONAL");
        e.setScope(scope);
        e.setTripId(body.get("tripId") == null ? null : toLong(body.get("tripId")));
        e.setUserId(body.get("userId") == null ? me : toLong(body.get("userId")));
        e.setCategory(str(body.get("category"), "OTHER"));
        e.setTitle(str(body.get("title"), "未命名支出"));
        double amount = toDouble(body.get("amount"));
        if (amount <= 0) {
            throw new BusinessException("金额需大于0");
        }
        e.setAmount(round2(amount));
        List<Long> sharers = parseSharers(body.get("sharers"));
        if (sharers.isEmpty()) {
            sharers = List.of(e.getUserId());
        }
        e.setSharers(join(sharers));
        e.setPerPerson(round2(amount / sharers.size()));
        e.setCreatedBy(me);
        e.setRemark(str(body.get("remark"), null));
        e.setCreatedAt(LocalDateTime.now());
        return expenseRepository.save(e);
    }

    /**
     * 由行程AA记账生成团队消费记录（供TripController复用）
     * @param tripId 行程ID
     * @param title 支出标题
     * @param amount 支出金额
     * @param sharers 分摊成员ID，为空时按操作人一人分摊
     * @param operator 操作人ID
     * @return Expense 保存后的团队消费记录
     */
    public Expense addTeamExpense(Long tripId, String title, double amount, List<Long> sharers, Long operator) {
        Expense e = new Expense();
        e.setScope("TEAM");
        e.setTripId(tripId);
        e.setUserId(operator);
        e.setCategory("OTHER");
        e.setTitle(title);
        e.setAmount(round2(amount));
        List<Long> s = sharers == null || sharers.isEmpty() ? List.of(operator) : sharers;
        e.setSharers(join(s));
        e.setPerPerson(round2(amount / s.size()));
        e.setCreatedBy(operator);
        e.setRemark("行程 AA 记账自动同步");
        e.setCreatedAt(LocalDateTime.now());
        return expenseRepository.save(e);
    }

    /**
     * 行程的团队消费明细（scope=TEAM），行程详情页「AA账单」卡片与团队总支出共用这一份。
     * 旧的trips.expenses_json已废弃，避免两处口径不一致。
     * @param tripId 行程ID
     * @return List 团队消费明细，按创建时间倒序
     */
    public List<Map<String, Object>> teamExpenses(Long tripId) {
        return expenseRepository.findByScopeAndTripIdOrderByCreatedAtDesc("TEAM", tripId)
                .stream().map(this::expenseView).toList();
    }

    public void delete(Long id) {
        Long me = AuthUtil.requireUserId();
        Expense e = expenseRepository.findById(id)
                .orElseThrow(() -> new BusinessException("记录不存在"));
        if (!Objects.equals(e.getCreatedBy(), me) && !AuthUtil.isAdmin()) {
            throw new BusinessException(403, "仅记录人可删除该消费记录");
        }
        expenseRepository.delete(e);
    }

    /**
     * 个人消费报表：总额、按类别、按行程、明细
     * @param userId 用户ID，为空时取当前登录用户
     * @return Map 个人报表，含总额、人均分摊、类别构成、行程汇总与明细
     */
    public Map<String, Object> personalReport(Long userId) {
        Long target = userId != null ? userId : AuthUtil.requireUserId();
        List<Expense> mine = expenseRepository.findByUserIdOrderByCreatedAtDesc(target);

        double total = mine.stream().mapToDouble(e -> nz(e.getAmount())).sum();
        Map<String, Double> byCat = new LinkedHashMap<>();
        Map<Long, Double> byTrip = new LinkedHashMap<>();
        double myShare = 0;
        for (Expense e : mine) {
            byCat.merge(categoryOf(e), nz(e.getAmount()), Double::sum);
            if (e.getTripId() != null) {
                byTrip.merge(e.getTripId(), nz(e.getAmount()), Double::sum);
            }
            myShare += e.getPerPerson() == null ? nz(e.getAmount()) : e.getPerPerson();
        }

        List<Map<String, Object>> categories = new ArrayList<>();
        byCat.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .forEach(en -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("category", en.getKey());
                    m.put("name", CATEGORY_NAMES.getOrDefault(en.getKey(), en.getKey()));
                    m.put("amount", round2(en.getValue()));
                    m.put("percent", total > 0 ? Math.round(en.getValue() / total * 1000) / 10.0 : 0);
                    categories.add(m);
                });

        List<Map<String, Object>> trips = new ArrayList<>();
        byTrip.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .forEach(en -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("tripId", en.getKey());
                    m.put("title", tripRepository.findById(en.getKey()).map(Trip::getTitle).orElse("行程 #" + en.getKey()));
                    m.put("amount", round2(en.getValue()));
                    trips.add(m);
                });

        List<Map<String, Object>> list = new ArrayList<>();
        for (Expense e : mine) {
            list.add(expenseView(e));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", target);
        result.put("total", round2(total));
        result.put("myShare", round2(myShare));
        result.put("count", mine.size());
        result.put("categories", categories);
        result.put("trips", trips);
        result.put("list", list);
        return result;
    }

    /**
     * 团队共享消费报表：全员消费情况、类别构成、AA分摊结算
     * @param tripId 行程ID
     * @return Map 团队报表，含成员支出、类别构成与结算建议
     */
    public Map<String, Object> teamReport(Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException("行程不存在"));
        List<Expense> team = expenseRepository.findByScopeAndTripIdOrderByCreatedAtDesc("TEAM", tripId);

        // 参与成员：队长+已通过成员
        LinkedHashSet<Long> memberIds = new LinkedHashSet<>();
        memberIds.add(trip.getCaptainId());
        for (var m : tripMemberRepository.findByTripIdAndStatus(tripId, "APPROVED")) {
            if (m.getUserId() != null) {
                memberIds.add(m.getUserId());
            }
        }
        memberIds.remove(null);

        Map<Long, Double> paid = new LinkedHashMap<>();
        Map<Long, Double> owed = new LinkedHashMap<>();
        Map<String, Double> byCat = new LinkedHashMap<>();
        for (Long id : memberIds) {
            paid.put(id, 0.0);
            owed.put(id, 0.0);
        }
        double total = 0;
        for (Expense e : team) {
            total += nz(e.getAmount());
            byCat.merge(categoryOf(e), nz(e.getAmount()), Double::sum);
            if (e.getUserId() != null) {
                paid.merge(e.getUserId(), nz(e.getAmount()), Double::sum);
            }
            List<Long> sharers = split(e.getSharers());
            if (sharers.isEmpty()) {
                sharers = List.of(e.getUserId() == null ? 0L : e.getUserId());
            }
            double share = nz(e.getAmount()) / sharers.size();
            for (Long s : sharers) {
                owed.merge(s, share, Double::sum);
            }
        }

        List<Map<String, Object>> members = new ArrayList<>();
        for (Long id : memberIds) {
            User u = userRepository.findById(id).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", id);
            m.put("nickname", u == null ? "用户" + id : u.getNickname());
            m.put("school", u == null ? null : u.getSchool());
            m.put("avatar", u == null ? null : u.getAvatar());
            m.put("paid", round2(paid.getOrDefault(id, 0.0)));
            m.put("owed", round2(owed.getOrDefault(id, 0.0)));
            m.put("net", round2(paid.getOrDefault(id, 0.0) - owed.getOrDefault(id, 0.0)));
            m.put("isCaptain", Objects.equals(id, trip.getCaptainId()));
            members.add(m);
        }

        List<Map<String, Object>> categories = new ArrayList<>();
        final double totalFinal = total;
        byCat.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .forEach(en -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("category", en.getKey());
                    m.put("name", CATEGORY_NAMES.getOrDefault(en.getKey(), en.getKey()));
                    m.put("amount", round2(en.getValue()));
                    m.put("percent", totalFinal > 0 ? Math.round(en.getValue() / totalFinal * 1000) / 10.0 : 0);
                    categories.add(m);
                });

        List<Map<String, Object>> list = new ArrayList<>();
        for (Expense e : team) {
            list.add(expenseView(e));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tripId", tripId);
        result.put("tripTitle", trip.getTitle());
        result.put("destination", trip.getDestination());
        result.put("budget", trip.getBudget());
        result.put("memberCount", memberIds.size());
        result.put("total", round2(total));
        result.put("avgPerPerson", round2(memberIds.isEmpty() ? 0 : total / memberIds.size()));
        result.put("categories", categories);
        result.put("members", members);
        result.put("list", list);
        result.put("settlements", settle(members));
        result.put("sharedDoc", "团队消费报表（全员可见、共享编辑）");

        // B3预算档位校验：人均实际支出vs队长发布时设定的预算（软校验，只提示不拦截）
        double avg = memberIds.isEmpty() ? 0 : total / memberIds.size();
        if (trip.getBudget() != null && trip.getBudget() > 0 && !memberIds.isEmpty()) {
            double budget = trip.getBudget();
            if (avg > budget * 1.2) {
                result.put("budgetWarning", String.format(
                        "人均实际支出 %.0f 元，已超出预算 %.0f 元的20%%，请在结算时与队友说明原因", avg, budget));
            } else if (avg > budget) {
                result.put("budgetWarning", String.format(
                        "人均实际支出 %.0f 元，略超预算 %.0f 元", avg, budget));
            } else {
                result.put("budgetWarning", String.format("人均实际支出 %.0f 元，在预算 %.0f 元之内，控制得不错", avg, budget));
            }
        }
        result.put("expensesSettled", Boolean.TRUE.equals(trip.getExpensesSettled()));
        return result;
    }

    /**
     * 一键结算确认：队长确认「结算建议里的转账已全部完成」。
     * 全队（含队长）信誉分+3（CreditService.REWARD_SETTLED），并把行程标记expensesSettled=true
     * 防止重复奖励；仍有未完成转账时拒绝确认并列出明细。
     * @param tripId 行程ID
     * @param operatorId 操作人ID，必须是该行程的队长
     * @return Map 含获奖人数与每人加分
     */
    public Map<String, Object> confirmSettled(Long tripId, Long operatorId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException("行程不存在"));
        if (!operatorId.equals(trip.getCaptainId())) {
            throw new BusinessException(403, "仅队长可以确认账目结清");
        }
        if (!"FINISHED".equals(trip.getStatus())) {
            throw new BusinessException("仅已结束的行程可以确认账目结清");
        }
        if (Boolean.TRUE.equals(trip.getExpensesSettled())) {
            throw new BusinessException("该行程已确认过账目结清，请勿重复操作");
        }
        Map<String, Object> report = teamReport(tripId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> settlements = (List<Map<String, Object>>) report.get("settlements");
        if (settlements != null && !settlements.isEmpty()) {
            throw new BusinessException("还有" + settlements.size()
                    + " 笔转账未完成（可在结算建议中查看），全部完成后再确认结清");
        }
        trip.setExpensesSettled(true);
        tripRepository.save(trip);

        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        ids.add(trip.getCaptainId());
        for (var mm : tripMemberRepository.findByTripIdAndStatus(tripId, "APPROVED")) {
            if (mm.getUserId() != null) {
                ids.add(mm.getUserId());
            }
        }
        for (Long uid : ids) {
            creditService.addCredit(uid, CreditService.REWARD_SETTLED,
                    "行程「" + trip.getTitle() + "」账目全部结清，守信加分");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tripId", tripId);
        m.put("rewardedMembers", ids.size());
        m.put("rewardPerMember", CreditService.REWARD_SETTLED);
        m.put("message", "已确认账目全部结清，全队" + ids.size() + " 人各加信誉分" + CreditService.REWARD_SETTLED + " 分");
        return m;
    }

    /**
     * 我的团队报表列表：我参与过的行程+各自总额
     * @param userId 用户ID，为空时取当前登录用户
     * @return List 我参与过的行程及各自的团队总支出
     */
    public List<Map<String, Object>> myTeams(Long userId) {
        Long me = userId != null ? userId : AuthUtil.requireUserId();
        LinkedHashSet<Long> tripIds = new LinkedHashSet<>();
        for (Trip t : tripRepository.findByCaptainIdOrderByCreatedAtDesc(me)) {
            tripIds.add(t.getId());
        }
        tripIds.addAll(tripMemberRepository.findTripIdsByUserId(me));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Long id : tripIds) {
            if (id == null) {
                continue;
            }
            Trip t = tripRepository.findById(id).orElse(null);
            if (t == null) {
                continue;
            }
            double total = expenseRepository.findByScopeAndTripIdOrderByCreatedAtDesc("TEAM", id)
                    .stream().mapToDouble(e -> nz(e.getAmount())).sum();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tripId", id);
            m.put("title", t.getTitle());
            m.put("destination", t.getDestination());
            m.put("total", round2(total));
            result.add(m);
        }
        return result;
    }

    // ==================== 分摊结算 ====================

    /**
     * 贪心配对生成"谁转给谁多少"的结算建议
     * @param members 各成员的净额信息，含userId、nickname、net
     * @return List 转账建议，每条含付款人、收款人与金额
     */
    private List<Map<String, Object>> settle(List<Map<String, Object>> members) {
        List<double[]> pool = new ArrayList<>(); // [index, net]
        for (int i = 0; i < members.size(); i++) {
            double net = ((Number) members.get(i).get("net")).doubleValue();
            pool.add(new double[]{i, net});
        }
        List<Map<String, Object>> result = new ArrayList<>();
        int guard = 0;
        while (guard++ < 200) {
            int debtor = -1;
            int creditor = -1;
            for (int i = 0; i < pool.size(); i++) {
                if (pool.get(i)[1] < -0.01 && (debtor < 0 || pool.get(i)[1] < pool.get(debtor)[1])) {
                    debtor = i;
                }
                if (pool.get(i)[1] > 0.01 && (creditor < 0 || pool.get(i)[1] > pool.get(creditor)[1])) {
                    creditor = i;
                }
            }
            if (debtor < 0 || creditor < 0) {
                break;
            }
            double amount = Math.min(-pool.get(debtor)[1], pool.get(creditor)[1]);
            if (amount < 0.01) {
                break;
            }
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("fromUserId", members.get((int) pool.get(debtor)[0]).get("userId"));
            s.put("fromName", members.get((int) pool.get(debtor)[0]).get("nickname"));
            s.put("toUserId", members.get((int) pool.get(creditor)[0]).get("userId"));
            s.put("toName", members.get((int) pool.get(creditor)[0]).get("nickname"));
            s.put("amount", round2(amount));
            result.add(s);
            pool.get(debtor)[1] += amount;
            pool.get(creditor)[1] -= amount;
        }
        return result;
    }

    // ==================== 工具 ====================

    private Map<String, Object> expenseView(Expense e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("scope", e.getScope());
        m.put("tripId", e.getTripId());
        m.put("userId", e.getUserId());
        m.put("payer", userRepository.findById(e.getUserId() == null ? -1L : e.getUserId())
                .map(User::getNickname).orElse("—"));
        m.put("category", categoryOf(e));
        m.put("categoryName", CATEGORY_NAMES.getOrDefault(categoryOf(e), "其他"));
        m.put("title", e.getTitle());
        m.put("amount", e.getAmount());
        List<Long> sharers = split(e.getSharers());
        m.put("sharerCount", sharers.size());
        m.put("sharerNames", sharers.stream()
                .map(id -> userRepository.findById(id).map(User::getNickname).orElse("用户" + id))
                .toList());
        m.put("perPerson", e.getPerPerson());
        m.put("remark", e.getRemark());
        m.put("createdAt", e.getCreatedAt());
        return m;
    }

    private String categoryOf(Expense e) {
        return e.getCategory() == null ? "OTHER" : e.getCategory();
    }

    @SuppressWarnings("unchecked")
    private List<Long> parseSharers(Object o) {
        List<Long> out = new ArrayList<>();
        if (o instanceof List<?> list) {
            for (Object item : list) {
                out.add(toLong(item));
            }
        } else if (o != null) {
            out.addAll(split(String.valueOf(o)));
        }
        out.removeIf(Objects::isNull);
        return out;
    }

    private List<Long> split(String csv) {
        List<Long> out = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return out;
        }
        for (String p : csv.split(",")) {
            String t = p.trim();
            if (!t.isEmpty()) {
                try {
                    out.add(Long.parseLong(t));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return out;
    }

    private String join(List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            if (id == null) continue;
            if (sb.length() > 0) sb.append(",");
            sb.append(id);
        }
        return sb.toString();
    }

    private String str(Object o, String def) {
        return o == null || String.valueOf(o).isBlank() ? def : String.valueOf(o);
    }

    private Long toLong(Object o) {
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private double toDouble(Object o) {
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }

    private double nz(Double d) {
        return d == null ? 0 : d;
    }

    private double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
