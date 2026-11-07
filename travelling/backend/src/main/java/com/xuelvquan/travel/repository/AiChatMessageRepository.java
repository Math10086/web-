package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.AiChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {

    /** 最近N条对话（近程上下文+历史回显用，调用方自行反转顺序） */
    List<AiChatMessage> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    List<AiChatMessage> findByUserIdOrderByCreatedAtAscIdAsc(Long userId);

    void deleteByUserId(Long userId);
}
