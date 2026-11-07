package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.BusinessException;
import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.entity.Circle;
import com.xuelvquan.travel.entity.CircleMember;
import com.xuelvquan.travel.entity.Post;
import com.xuelvquan.travel.entity.User;
import com.xuelvquan.travel.repository.CircleMemberRepository;
import com.xuelvquan.travel.repository.CircleRepository;
import com.xuelvquan.travel.repository.PostRepository;
import com.xuelvquan.travel.repository.UserRepository;
import com.xuelvquan.travel.security.AuthContext;
import com.xuelvquan.travel.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/circles")
@RequiredArgsConstructor
public class CircleController {

    private final CircleRepository circleRepository;
    private final CircleMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        // 社区圈层属C端前台：管理员与商家均已退出前台
        AuthUtil.requireFrontOrGuest();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Circle c : circleRepository.findAllOrdered()) {
            Map<String, Object> m = circleMap(c);
            result.add(m);
        }
        return Result.ok(result);
    }

    @PostMapping
    @Transactional
    public Result<Circle> create(@RequestBody Map<String, String> body) {
        // 仅前台用户可创建圈层
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        Circle c = new Circle();
        c.setName(body.get("name"));
        c.setTheme(body.get("theme"));
        c.setDescription(body.get("description"));
        c.setIcon(body.get("icon"));
        c.setCreatorId(userId);
        c.setMemberCount(0);
        c.setCreatedAt(LocalDateTime.now());
        circleRepository.save(c);

        CircleMember m = new CircleMember();
        m.setCircleId(c.getId());
        m.setUserId(userId);
        m.setJoinedAt(LocalDateTime.now());
        memberRepository.save(m);
        c.setMemberCount(1);
        return Result.ok(circleRepository.save(c));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        AuthUtil.requireFrontOrGuest();
        Circle c = circleRepository.findById(id)
                .orElseThrow(() -> new BusinessException("圈层不存在"));
        Map<String, Object> m = circleMap(c);
        Long currentUserId = AuthContext.getUserId();
        m.put("currentUserJoined",
                currentUserId != null && memberRepository.findByCircleIdAndUserId(id, currentUserId).isPresent());
        long postCount = postRepository.findByCircleId(id).stream()
                .filter(p -> "APPROVED".equals(p.getStatus())).count();
        m.put("postCount", postCount);
        return Result.ok(m);
    }

    @PostMapping("/{id}/join")
    @Transactional
    public Result<Circle> join(@PathVariable Long id) {
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        Circle c = circleRepository.findById(id)
                .orElseThrow(() -> new BusinessException("圈层不存在"));
        if (memberRepository.findByCircleIdAndUserId(id, userId).isPresent()) {
            throw new BusinessException("已加入该圈层");
        }
        CircleMember m = new CircleMember();
        m.setCircleId(id);
        m.setUserId(userId);
        m.setJoinedAt(LocalDateTime.now());
        memberRepository.save(m);
        c.setMemberCount(nz(c.getMemberCount()) + 1);
        return Result.ok(circleRepository.save(c));
    }

    @PostMapping("/{id}/leave")
    @Transactional
    public Result<Circle> leave(@PathVariable Long id) {
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        Circle c = circleRepository.findById(id)
                .orElseThrow(() -> new BusinessException("圈层不存在"));
        if (memberRepository.findByCircleIdAndUserId(id, userId).isEmpty()) {
            throw new BusinessException("未加入该圈层");
        }
        memberRepository.deleteByCircleIdAndUserId(id, userId);
        c.setMemberCount(Math.max(0, nz(c.getMemberCount()) - 1));
        return Result.ok(circleRepository.save(c));
    }

    @GetMapping("/{id}/members")
    public Result<List<Map<String, Object>>> members(@PathVariable Long id) {
        circleRepository.findById(id).orElseThrow(() -> new BusinessException("圈层不存在"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (CircleMember cm : memberRepository.findByCircleId(id)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("memberId", cm.getId());
            m.put("joinedAt", cm.getJoinedAt());
            userRepository.findById(cm.getUserId()).ifPresent(u -> {
                m.put("userId", u.getId());
                m.put("nickname", u.getNickname());
                m.put("avatar", u.getAvatar());
                m.put("school", u.getSchool());
            });
            result.add(m);
        }
        return Result.ok(result);
    }

    private Map<String, Object> circleMap(Circle c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("theme", c.getTheme());
        m.put("description", c.getDescription());
        m.put("icon", c.getIcon());
        m.put("creatorId", c.getCreatorId());
        m.put("memberCount", c.getMemberCount());
        m.put("createdAt", c.getCreatedAt());
        if (c.getCreatorId() != null) {
            userRepository.findById(c.getCreatorId()).ifPresent(u ->
                    m.put("creator", Map.of("id", u.getId(), "nickname", u.getNickname(), "avatar", u.getAvatar() == null ? "" : u.getAvatar())));
        }
        return m;
    }

    private int nz(Integer i) {
        return i == null ? 0 : i;
    }
}
