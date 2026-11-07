package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.entity.Notification;
import com.xuelvquan.travel.repository.NotificationRepository;
import com.xuelvquan.travel.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;

    @GetMapping
    public Result<Map<String, Object>> list() {
        Long userId = AuthUtil.requireUserId();
        List<Notification> list = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return Result.ok(Map.of(
                "list", list,
                "unread", notificationRepository.countByUserIdAndReadFlagFalse(userId)));
    }

    @PutMapping("/read")
    public Result<Void> readAll() {
        Long userId = AuthUtil.requireUserId();
        notificationRepository.markAllRead(userId);
        return Result.ok();
    }
}
