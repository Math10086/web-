package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.BusinessException;
import com.xuelvquan.travel.common.CreditBadge;
import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.config.VerifyProperties;
import com.xuelvquan.travel.entity.CreditLog;
import com.xuelvquan.travel.entity.Post;
import com.xuelvquan.travel.entity.TagDict;
import com.xuelvquan.travel.entity.User;
import com.xuelvquan.travel.repository.CreditLogRepository;
import com.xuelvquan.travel.repository.PostRepository;
import com.xuelvquan.travel.repository.TagDictRepository;
import com.xuelvquan.travel.repository.UserRepository;
import com.xuelvquan.travel.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private static final Set<String> SCHOOLS = Set.of("CSU", "HUNNU", "HNU");
    private static final Set<String> ROLES = Set.of("USER", "BLOGGER", "MERCHANT", "GUIDE", "ADMIN");

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CreditLogRepository creditLogRepository;
    private final TagDictRepository tagDictRepository;
    private final VerifyProperties verifyProperties;

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        List<Post> posts = postRepository.findByAuthorIdOrderByCreatedAtDesc(id).stream()
                .filter(p -> "APPROVED".equals(p.getStatus())).toList();
        long totalViews = posts.stream().mapToLong(p -> nz(p.getViewCount())).sum();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("user", user);
        m.put("postCount", posts.size());
        m.put("totalViews", totalViews);
        m.put("fans", 0);
        return Result.ok(m);
    }

    @GetMapping
    public Result<List<User>> list(@RequestParam(required = false) String keyword) {
        AuthUtil.requireRole("ADMIN");
        if (keyword == null || keyword.isBlank()) {
            return Result.ok(userRepository.findAll());
        }
        return Result.ok(userRepository
                .findByUsernameContainingOrNicknameContainingOrderByIdDesc(keyword, keyword));
    }

    @PutMapping("/profile")
    public Result<User> updateProfile(@RequestBody Map<String, String> body) {
        Long userId = AuthUtil.requireUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(401, "用户不存在"));
        if (body.containsKey("nickname")) user.setNickname(body.get("nickname"));
        if (body.containsKey("avatar")) user.setAvatar(body.get("avatar"));
        if (body.containsKey("bio")) user.setBio(body.get("bio"));
        if (body.containsKey("interests")) user.setInterests(body.get("interests"));
        // 学院/专业/届别（头像信息卡展示）
        if (body.containsKey("college")) user.setCollege(blankToNull(body.get("college")));
        if (body.containsKey("major")) user.setMajor(blankToNull(body.get("major")));
        if (body.containsKey("grade")) user.setGrade(blankToNull(body.get("grade")));
        // 档案标签（dimension:code逗号串），校验全部code都在字典里
        if (body.containsKey("profileTags")) {
            user.setProfileTags(validateProfileTags(body.get("profileTags")));
        }
        return Result.ok(userRepository.save(user));
    }

    /**
     * 档案标签校验：只接受字典里存在的dimension:code，非法项直接丢弃。
     * @param raw 前端提交的标签串（dimension:code逗号分隔）
     * @return String 过滤后的标签串，没有合法项时返回null
     */
    private String validateProfileTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Set<String> legal = new java.util.HashSet<>();
        for (TagDict t : tagDictRepository.findByScopeAndActiveTrueOrderBySortNoAscIdAsc("USER")) {
            legal.add(t.getDimension() + ":" + t.getCode());
        }
        List<String> kept = new ArrayList<>();
        for (String token : raw.split("[,，;；\\s]+")) {
            String s = token.trim();
            if (!s.isEmpty() && legal.contains(s) && !kept.contains(s)) {
                kept.add(s);
            }
        }
        return kept.isEmpty() ? null : String.join(",", kept);
    }

    /**
     * 身份认证：学号格式校验自动通过（无需人工审核）。
     *
     * 三道校验：①姓名/学号非空；②学校在三校白名单；③学号匹配该校配置的正则；
     * 另加学号唯一性校验，防止一个学号认证多个账号。
     * @param body 请求体，含realName、studentNo、school，可带college、major、grade
     * @return Result<User> 认证后的用户信息
     */
    @PostMapping("/verify")
    public Result<User> verify(@RequestBody Map<String, String> body) {
        Long userId = AuthUtil.requireUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(401, "用户不存在"));
        String realName = body.get("realName");
        String studentNo = body.get("studentNo");
        String school = body.get("school");
        if (realName == null || realName.isBlank() || studentNo == null || studentNo.isBlank()) {
            throw new BusinessException("真实姓名与学号不能为空");
        }
        if (school == null || !SCHOOLS.contains(school)) {
            throw new BusinessException("仅支持中南大学(CSU)、湖南师范大学(HUNNU)、湖南大学(HNU)三校认证");
        }
        String no = studentNo.trim();
        // 学号格式校验：正则按学校配置（见application.yml -> xlvq.verify）
        String pattern = verifyProperties.patternOf(school);
        if (!Pattern.matches(pattern, no)) {
            throw new BusinessException(verifyProperties.hintOf(school));
        }
        // 学号唯一性：同一学号不允许被多个账号占用（自己重复提交放行）
        userRepository.findByStudentNo(no).ifPresent(other -> {
            if (!other.getId().equals(user.getId())) {
                throw new BusinessException("该学号已被其他账号认证");
            }
        });
        user.setRealName(realName.trim());
        user.setStudentNo(no);
        user.setSchool(school);
        user.setVerified(true);
        user.setVerifyMethod("STUDENT_NO");
        user.setVerifiedAt(LocalDateTime.now());
        // 认证时顺带接收学院/专业/届别（前端认证表单一起提交）
        if (body.containsKey("college")) user.setCollege(blankToNull(body.get("college")));
        if (body.containsKey("major")) user.setMajor(blankToNull(body.get("major")));
        if (body.containsKey("grade")) user.setGrade(blankToNull(body.get("grade")));
        return Result.ok(userRepository.save(user));
    }

    /**
     * 头像信息卡（公开可读）：学院·专业·届别+认证徽章+信誉标签+标签档案。
     *
     * 不返回密码、学号等敏感字段；学号只回显后四位。
     * @param id 用户ID
     * @return Result<Map<String,Object>> 头像信息卡数据
     */
    @GetMapping("/{id}/card")
    public Result<Map<String, Object>> card(@PathVariable Long id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("nickname", u.getNickname());
        m.put("avatar", u.getAvatar() == null ? "" : u.getAvatar());
        m.put("school", u.getSchool());
        m.put("college", u.getCollege());
        m.put("major", u.getMajor());
        m.put("grade", u.getGrade());
        m.put("bio", u.getBio());
        m.put("role", u.getRole());
        m.put("verified", Boolean.TRUE.equals(u.getVerified()));
        m.put("verifyMethod", u.getVerifyMethod());
        m.put("studentNoMasked", maskStudentNo(u.getStudentNo()));
        int credit = u.getCreditScore() == null ? 100 : u.getCreditScore();
        m.put("creditScore", credit);
        m.put("creditLevel", CreditBadge.levelOf(credit));
        m.put("creditLabel", CreditBadge.labelOf(credit));
        m.put("tags", tagViews(u.getProfileTags()));
        return Result.ok(m);
    }

    /**
     * 把profile_tags里的code解析成 {dimension, dimensionLabel, code, label}，供前端直接渲染
     * @param profileTags 逗号分隔的标签编码串
     * @return List<Map<String,Object>> 前端可直接渲染的标签列表
     */
    private List<Map<String, Object>> tagViews(String profileTags) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (profileTags == null || profileTags.isBlank()) {
            return out;
        }
        Map<String, TagDict> dict = new LinkedHashMap<>();
        for (TagDict t : tagDictRepository.findByScopeAndActiveTrueOrderBySortNoAscIdAsc("USER")) {
            dict.put(t.getDimension() + ":" + t.getCode(), t);
        }
        for (String token : profileTags.split(",")) {
            TagDict t = dict.get(token.trim());
            if (t == null) {
                continue;
            }
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("dimension", t.getDimension());
            v.put("code", t.getCode());
            v.put("label", t.getLabel());
            out.add(v);
        }
        return out;
    }

    private String maskStudentNo(String studentNo) {
        if (studentNo == null || studentNo.isBlank()) {
            return null;
        }
        int len = studentNo.length();
        if (len <= 4) {
            return studentNo;
        }
        return "*".repeat(len - 4) + studentNo.substring(len - 4);
    }

    private String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String v = s.trim();
        return v.isEmpty() ? null : v;
    }

    @PostMapping("/apply-blogger")
    public Result<User> applyBlogger() {
        // 仅前台用户可申请，管理员只做后台治理
        AuthUtil.requireNonAdmin();
        Long userId = AuthUtil.requireUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(401, "用户不存在"));
        user.setRole("BLOGGER");
        if (user.getBloggerLevel() == null || user.getBloggerLevel() == 0) {
            user.setBloggerLevel(1);
        }
        return Result.ok(userRepository.save(user));
    }

    @PutMapping("/{id}/admin")
    @Transactional
    public Result<User> adminUpdate(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        AuthUtil.requireRole("ADMIN");
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        if (body.get("banned") != null) {
            user.setBanned(Boolean.valueOf(body.get("banned").toString()));
        }
        if (body.get("role") != null) {
            String role = body.get("role").toString();
            if (!ROLES.contains(role)) {
                throw new BusinessException("非法角色");
            }
            user.setRole(role);
        }
        if (body.get("creditScore") != null) {
            int newScore = Integer.parseInt(body.get("creditScore").toString());
            // 与CreditService的分数上下限保持一致，避免管理员改出越界分值
            newScore = Math.max(0, Math.min(150, newScore));
            int delta = newScore - nz(user.getCreditScore());
            if (delta != 0) {
                CreditLog log = new CreditLog();
                log.setUserId(id);
                log.setDelta(delta);
                log.setReason("管理员调整信誉分");
                log.setCreatedAt(LocalDateTime.now());
                creditLogRepository.save(log);
            }
            user.setCreditScore(newScore);
        }
        return Result.ok(userRepository.save(user));
    }

    @GetMapping("/rankings/bloggers")
    public Result<List<Map<String, Object>>> bloggerRankings() {
        List<User> bloggers = userRepository.findTop20ByRoleOrderByBloggerScoreDesc("BLOGGER");
        List<Map<String, Object>> result = bloggers.stream().map(u -> {
            List<Post> posts = postRepository.findByAuthorIdOrderByCreatedAtDesc(u.getId()).stream()
                    .filter(p -> "APPROVED".equals(p.getStatus())).toList();
            long totalViews = posts.stream().mapToLong(p -> nz(p.getViewCount())).sum();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("nickname", u.getNickname());
            m.put("avatar", u.getAvatar());
            m.put("school", u.getSchool());
            m.put("level", u.getBloggerLevel());
            m.put("bloggerScore", u.getBloggerScore());
            m.put("postCount", posts.size());
            m.put("totalViews", totalViews);
            return m;
        }).toList();
        return Result.ok(result);
    }

    private int nz(Integer i) {
        return i == null ? 0 : i;
    }
}
