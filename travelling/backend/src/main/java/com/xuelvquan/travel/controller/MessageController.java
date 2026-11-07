package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.BusinessException;
import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.entity.Message;
import com.xuelvquan.travel.repository.MessageRepository;
import com.xuelvquan.travel.repository.UserRepository;
import com.xuelvquan.travel.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@Transactional
public class MessageController {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    @GetMapping("/conversations")
    public Result<List<Map<String, Object>>> conversations() {
        Long userId = AuthUtil.requireUserId();
        List<Message> all = messageRepository.findByFromIdOrToIdOrderByCreatedAtAsc(userId, userId);
        // 列表按时间升序，倒序遍历后putIfAbsent => 每个peer保留最新一条
        Map<Long, Message> lastMap = new LinkedHashMap<>();
        for (int i = all.size() - 1; i >= 0; i--) {
            Message m = all.get(i);
            Long peer = m.getFromId().equals(userId) ? m.getToId() : m.getFromId();
            lastMap.putIfAbsent(peer, m);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, Message> e : lastMap.entrySet()) {
            Long peerId = e.getKey();
            Message last = e.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("peerId", peerId);
            userRepository.findById(peerId).ifPresent(u -> {
                m.put("nickname", u.getNickname());
                m.put("avatar", u.getAvatar());
            });
            m.put("lastMessage", last.getContent());
            m.put("createdAt", last.getCreatedAt());
            m.put("updatedAt", last.getCreatedAt());
            m.put("unreadCount", messageRepository.countByToIdAndFromIdAndReadFlagFalse(userId, peerId));
            result.add(m);
        }
        result.sort((a, b) -> ((LocalDateTime) b.get("updatedAt")).compareTo((LocalDateTime) a.get("updatedAt")));
        return Result.ok(result);
    }

    @GetMapping
    public Result<List<Message>> chat(@RequestParam Long peerId) {
        Long userId = AuthUtil.requireUserId();
        List<Message> all = messageRepository.findByFromIdOrToIdOrderByCreatedAtAsc(userId, peerId);
        List<Message> between = all.stream()
                .filter(m -> (m.getFromId().equals(userId) && m.getToId().equals(peerId))
                        || (m.getFromId().equals(peerId) && m.getToId().equals(userId)))
                .toList();
        // 对方发来的标记已读
        boolean dirty = false;
        for (Message m : between) {
            if (m.getFromId().equals(peerId) && !Boolean.TRUE.equals(m.getReadFlag())) {
                m.setReadFlag(true);
                dirty = true;
            }
        }
        if (dirty) {
            messageRepository.saveAll(between);
        }
        return Result.ok(between);
    }

    @PostMapping
    public Result<Message> send(@RequestBody Map<String, Object> body) {
        Long userId = AuthUtil.requireUserId();
        Long toId = Long.valueOf(String.valueOf(body.get("toId")));
        String content = String.valueOf(body.get("content"));
        if (content.isBlank()) {
            throw new BusinessException("消息内容不能为空");
        }
        userRepository.findById(toId).orElseThrow(() -> new BusinessException("接收用户不存在"));
        Message m = new Message();
        m.setFromId(userId);
        m.setToId(toId);
        m.setContent(content);
        m.setMsgType("CHAT");
        m.setReadFlag(false);
        m.setCreatedAt(LocalDateTime.now());
        return Result.ok(messageRepository.save(m));
    }
}
