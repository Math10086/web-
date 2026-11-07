package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByFromIdOrToIdOrderByCreatedAtAsc(Long fromId, Long toId);

    long countByToIdAndFromIdAndReadFlagFalse(Long toId, Long fromId);
}
