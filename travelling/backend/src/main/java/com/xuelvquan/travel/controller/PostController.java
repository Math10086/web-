package com.xuelvquan.travel.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xuelvquan.travel.common.BusinessException;
import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.entity.*;
import com.xuelvquan.travel.repository.CommentRepository;
import com.xuelvquan.travel.repository.InteractionRepository;
import com.xuelvquan.travel.repository.PostRepository;
import com.xuelvquan.travel.repository.TagDictRepository;
import com.xuelvquan.travel.repository.UserRepository;
import com.xuelvquan.travel.security.AuthContext;
import com.xuelvquan.travel.security.AuthUtil;
import com.xuelvquan.travel.service.NotifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@Transactional
public class PostController {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final InteractionRepository interactionRepository;
    private final UserRepository userRepository;
    private final TagDictRepository tagDictRepository;
    private final NotifyService notifyService;
    private final ObjectMapper objectMapper;

    // ================= 列表/发布/审核 =================

    @GetMapping
    public Result<List<Post>> list(@RequestParam(required = false) String type,
                                   @RequestParam(required = false) Long circleId,
                                   @RequestParam(required = false) String city,
                                   @RequestParam(required = false) Long authorId,
                                   @RequestParam(required = false) String keyword,
                                   @RequestParam(required = false, defaultValue = "latest") String sort,
                                   @RequestParam(required = false) Integer mine,
                                   @RequestParam(required = false) String status) {
        // 仅前台游客及前台用户可见，管理员与商家不可见
        AuthUtil.requireFrontOrGuest();
        String kw = blankToNull(keyword);
        List<Post> posts;
        if (mine != null && mine == 1) {
            Long me = AuthUtil.requireUserId();
            posts = query(null, type, circleId, city, me, kw, sort);
        } else {
            String queryStatus = "APPROVED";
            if (status != null && AuthUtil.isAdmin()) {
                queryStatus = "all".equalsIgnoreCase(status) ? null : status;
            }
            posts = query(queryStatus, type, circleId, city, authorId, kw, sort);
        }
        return Result.ok(posts);
    }

    @PostMapping
    public Result<Post> create(@RequestBody Map<String, Object> body) {
        // 仅前台用户可发布，管理员与商家不参与
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        String type = (String) body.get("type");
        String title = (String) body.get("title");
        if (type == null || title == null || title.isBlank()) {
            throw new BusinessException("帖子类型和标题不能为空");
        }
        Post p = new Post();
        p.setType(type);
        p.setTitle(title);
        p.setContent((String) body.get("content"));
        p.setCircleId(toLong(body.get("circleId")));
        p.setCity((String) body.get("city"));
        p.setDestination((String) body.get("destination"));
        p.setModeTag((String) body.get("modeTag"));
        p.setImages(imagesCsv(body.get("images")));
        // 预算结构化字段
        p.setCostPerPerson(toDouble(body.get("costPerPerson")));
        p.setDays(toInt(body.get("days")));
        p.setAvoidTags(validateDictTags("POST", "avoid", body.get("avoidTags")));
        p.setSuitableFor(validateDictTags("POST", "suitable", body.get("suitableFor")));
        p.setAuthorId(userId);
        p.setViewCount(0);
        p.setLikeCount(0);
        p.setFavoriteCount(0);
        p.setCitationCount(0);
        p.setCommentCount(0);
        p.setTrustedBadge(false);
        p.setStatus("PENDING");
        p.setCreatedAt(LocalDateTime.now());
        return Result.ok(postRepository.save(p));
    }

    @GetMapping("/pending")
    public Result<List<Map<String, Object>>> pending() {
        AuthUtil.requireRole("ADMIN");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Post p : postRepository.findByStatusOrderByCreatedAtDesc("PENDING")) {
            Map<String, Object> m = objectMapper.convertValue(p, new TypeReference<LinkedHashMap<String, Object>>() {});
            userRepository.findById(p.getAuthorId()).ifPresent(u ->
                    m.put("author", briefUser(u)));
            result.add(m);
        }
        return Result.ok(result);
    }

    @PostMapping("/{id}/audit")
    public Result<Post> audit(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        AuthUtil.requireRole("ADMIN");
        Post p = postRepository.findById(id).orElseThrow(() -> new BusinessException("帖子不存在"));
        boolean approve = Boolean.TRUE.equals(body.get("approve"))
                || Boolean.parseBoolean(String.valueOf(body.get("approve")));
        p.setStatus(approve ? "APPROVED" : "REJECTED");
        Post saved = postRepository.save(p);
        notifyService.notify(p.getAuthorId(), "AUDIT",
                approve ? "攻略审核通过" : "攻略审核未通过",
                "您的帖子《" + p.getTitle() + "》" + (approve ? "已通过审核" : "未通过审核：" + body.get("reason")),
                p.getId());
        return Result.ok(saved);
    }

    // ================= 详情/编辑/删除 =================

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        AuthUtil.requireFrontOrGuest();
        Post p = postRepository.findById(id).orElseThrow(() -> new BusinessException("帖子不存在"));
        p.setViewCount(nz(p.getViewCount()) + 1);
        postRepository.save(p);

        Map<String, Object> m = objectMapper.convertValue(p, new TypeReference<LinkedHashMap<String, Object>>() {});
        userRepository.findById(p.getAuthorId()).ifPresent(u -> m.put("author", briefUser(u)));

        Long userId = AuthContext.getUserId();
        m.put("liked", userId != null
                && interactionRepository.findByUserIdAndTargetIdAndType(userId, id, "LIKE").isPresent());
        m.put("favorited", userId != null
                && interactionRepository.findByUserIdAndTargetIdAndType(userId, id, "FAVORITE").isPresent());

        List<Map<String, Object>> comments = new ArrayList<>();
        for (Comment c : commentRepository.findByPostIdOrderByCreatedAtAsc(id)) {
            Map<String, Object> cm = objectMapper.convertValue(c, new TypeReference<LinkedHashMap<String, Object>>() {});
            userRepository.findById(c.getUserId()).ifPresent(u -> {
                cm.put("nickname", u.getNickname());
                cm.put("avatar", u.getAvatar());
            });
            comments.add(cm);
        }
        m.put("comments", comments);
        return Result.ok(m);
    }

    @PutMapping("/{id}")
    public Result<Post> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        Post p = postRepository.findById(id).orElseThrow(() -> new BusinessException("帖子不存在"));
        if (!p.getAuthorId().equals(userId)) {
            throw new BusinessException(403, "无权修改该帖子");
        }
        if (body.containsKey("title")) p.setTitle((String) body.get("title"));
        if (body.containsKey("content")) p.setContent((String) body.get("content"));
        if (body.containsKey("type")) p.setType((String) body.get("type"));
        if (body.containsKey("circleId")) p.setCircleId(toLong(body.get("circleId")));
        if (body.containsKey("city")) p.setCity((String) body.get("city"));
        if (body.containsKey("destination")) p.setDestination((String) body.get("destination"));
        if (body.containsKey("modeTag")) p.setModeTag((String) body.get("modeTag"));
        if (body.containsKey("images")) p.setImages(imagesCsv(body.get("images")));
        // 预算结构化字段
        if (body.containsKey("costPerPerson")) p.setCostPerPerson(toDouble(body.get("costPerPerson")));
        if (body.containsKey("days")) p.setDays(toInt(body.get("days")));
        if (body.containsKey("avoidTags")) p.setAvoidTags(validateDictTags("POST", "avoid", body.get("avoidTags")));
        if (body.containsKey("suitableFor")) p.setSuitableFor(validateDictTags("POST", "suitable", body.get("suitableFor")));
        return Result.ok(postRepository.save(p));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        Post p = postRepository.findById(id).orElseThrow(() -> new BusinessException("帖子不存在"));
        if (!p.getAuthorId().equals(userId)) {
            throw new BusinessException(403, "无权删除该帖子");
        }
        commentRepository.deleteByPostId(id);
        interactionRepository.deleteByTargetId(id);
        postRepository.delete(p);
        return Result.ok();
    }

    // ================= 点赞/收藏/引用 =================

    @PostMapping("/{id}/like")
    public Result<Map<String, Object>> like(@PathVariable Long id) {
        return Result.ok(toggle(id, "LIKE"));
    }

    @PostMapping("/{id}/favorite")
    public Result<Map<String, Object>> favorite(@PathVariable Long id) {
        return Result.ok(toggle(id, "FAVORITE"));
    }

    @PostMapping("/{id}/cite")
    public Result<Post> cite(@PathVariable Long id) {
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        Post p = postRepository.findById(id).orElseThrow(() -> new BusinessException("帖子不存在"));
        p.setCitationCount(nz(p.getCitationCount()) + 1);
        postRepository.save(p);
        User author = userRepository.findById(p.getAuthorId()).orElse(null);
        if (author != null) {
            author.setBloggerScore(nz(author.getBloggerScore()) + 2);
            userRepository.save(author);
        }
        notifyService.notify(p.getAuthorId(), "INTERACT", "您的攻略被引用",
                "用户引用了您的帖子《" + p.getTitle() + "》，博主积分+2", p.getId());
        return Result.ok(p);
    }

    // ================= 评论 =================

    @GetMapping("/{id}/comments")
    public Result<List<Map<String, Object>>> comments(@PathVariable Long id) {
        postRepository.findById(id).orElseThrow(() -> new BusinessException("帖子不存在"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Comment c : commentRepository.findByPostIdOrderByCreatedAtAsc(id)) {
            Map<String, Object> cm = objectMapper.convertValue(c, new TypeReference<LinkedHashMap<String, Object>>() {});
            userRepository.findById(c.getUserId()).ifPresent(u -> {
                cm.put("nickname", u.getNickname());
                cm.put("avatar", u.getAvatar());
            });
            result.add(cm);
        }
        return Result.ok(result);
    }

    @PostMapping("/{id}/comments")
    public Result<Comment> addComment(@PathVariable Long id, @RequestBody Map<String, String> body) {
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        Post p = postRepository.findById(id).orElseThrow(() -> new BusinessException("帖子不存在"));
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            throw new BusinessException("评论内容不能为空");
        }
        Comment c = new Comment();
        c.setPostId(id);
        c.setUserId(userId);
        c.setContent(content);
        c.setCreatedAt(LocalDateTime.now());
        commentRepository.save(c);

        p.setCommentCount((int) commentRepository.countByPostId(id));
        postRepository.save(p);

        if (!p.getAuthorId().equals(userId)) {
            notifyService.notify(p.getAuthorId(), "INTERACT", "收到新评论",
                    "您的帖子《" + p.getTitle() + "》收到一条新评论", p.getId());
        }
        return Result.ok(c);
    }

    // ================= 私有方法 =================

    private List<Post> query(String status, String type, Long circleId, String city, Long authorId, String kw, String sort) {
        if ("hot".equalsIgnoreCase(sort)) {
            return postRepository.searchHot(status, blankToNull(type), circleId, blankToNull(city), authorId, kw);
        }
        return postRepository.search(status, blankToNull(type), circleId, blankToNull(city), authorId, kw);
    }

    private Map<String, Object> toggle(Long postId, String type) {
        // 仅前台用户可用，管理员与商家不参与
        AuthUtil.requireBusiness();
        Long userId = AuthUtil.requireUserId();
        Post p = postRepository.findById(postId).orElseThrow(() -> new BusinessException("帖子不存在"));
        Optional<Interaction> existing =
                interactionRepository.findByUserIdAndTargetIdAndType(userId, postId, type);
        boolean active;
        if (existing.isPresent()) {
            interactionRepository.delete(existing.get());
            active = false;
        } else {
            Interaction i = new Interaction();
            i.setUserId(userId);
            i.setTargetId(postId);
            i.setType(type);
            i.setCreatedAt(LocalDateTime.now());
            interactionRepository.save(i);
            active = true;
        }
        if ("LIKE".equals(type)) {
            p.setLikeCount((int) interactionRepository.countByTargetIdAndType(postId, "LIKE"));
        } else {
            p.setFavoriteCount((int) interactionRepository.countByTargetIdAndType(postId, "FAVORITE"));
        }
        postRepository.save(p);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("active", active);
        result.put("likeCount", p.getLikeCount());
        result.put("favoriteCount", p.getFavoriteCount());
        return result;
    }

    private Map<String, Object> briefUser(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("nickname", u.getNickname());
        m.put("avatar", u.getAvatar());
        m.put("school", u.getSchool());
        m.put("verified", u.getVerified());
        m.put("bloggerLevel", u.getBloggerLevel());
        return m;
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        String s = o.toString();
        return s.isBlank() ? null : Long.valueOf(s);
    }

    private String imagesCsv(Object images) {
        if (images == null) return null;
        if (images instanceof List<?> list) {
            return String.join(",", list.stream().map(String::valueOf).toList());
        }
        return images.toString();
    }

    /**
     * 攻略标签校验：只保留字典中存在的code，非法项丢弃
     * @param scope 标签作用域
     * @param dimension 标签维度
     * @param raw 原始标签（数组或逗号分隔字符串）
     * @return String 校验后的标签，全部非法时返回null
     */
    private String validateDictTags(String scope, String dimension, Object raw) {
        if (raw == null) {
            return null;
        }
        java.util.Set<String> legal = new java.util.HashSet<>();
        for (TagDict t : tagDictRepository
                .findByScopeAndDimensionAndActiveTrueOrderBySortNoAscIdAsc(scope, dimension)) {
            legal.add(t.getCode());
        }
        // 兼容数组与 "A,B" 字符串两种入参形式
        List<String> tokens = new ArrayList<>();
        if (raw instanceof java.util.Collection<?> col) {
            for (Object o : col) {
                if (o != null) {
                    tokens.add(String.valueOf(o));
                }
            }
        } else if (raw instanceof Object[] arr) {
            for (Object o : arr) {
                if (o != null) {
                    tokens.add(String.valueOf(o));
                }
            }
        } else {
            for (String token : String.valueOf(raw).split("[,，;；\\s]+")) {
                tokens.add(token);
            }
        }
        List<String> kept = new ArrayList<>();
        for (String token : tokens) {
            String s = token.replace("[", "").replace("]", "").replace("\"", "").trim();
            if (!s.isEmpty() && legal.contains(s) && !kept.contains(s)) {
                kept.add(s);
            }
        }
        return kept.isEmpty() ? null : String.join(",", kept);
    }

    private Double toDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        String s = o.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return Double.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer toInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        String s = o.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private int nz(Integer i) {
        return i == null ? 0 : i;
    }
}
