package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "emergency_contacts")
public class EmergencyContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    /** 联系人姓名，例如：张三 */
    private String name;

    /** 与本人的关系，只能填两个值：PARENT（家长）或COUNSELOR（辅导员） */
    private String relation;

    /** 联系电话 */
    private String phone;

    /** 是否同意接收紧急通知：true已同意，false未同意 */
    private Boolean authorized = false;
    private LocalDateTime createdAt;
}
