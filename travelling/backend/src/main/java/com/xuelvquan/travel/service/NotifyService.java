package com.xuelvquan.travel.service;

import com.xuelvquan.travel.entity.Notification;
import com.xuelvquan.travel.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 站内通知统一发送入口。
 */
@Service
@RequiredArgsConstructor
public class NotifyService {

    private final NotificationRepository notificationRepository;

    public void notify(Long userId, String type, String title, String content, Long refId) {
        if (userId == null) {
            return;
        }
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setRefId(refId);
        n.setReadFlag(false);
        n.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(n);
    }
}
