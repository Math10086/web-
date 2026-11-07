package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家校知情/行程报备通知记录（组队结伴流程中的家校知情环节）。
 *
 * 结伴出行前，由队长一键把行程信息推送给学生已授权的紧急联系人（家长/辅导员），
 * 家长没有平台账号，因此这里按「学生×联系人」落一条通知记录，作为家校共同的知情凭证。
 */
@Data
@Entity
@Table(name = "safety_notices")
public class SafetyNotice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long tripId;
    private String tripTitle;
    private String destination;
    private LocalDateTime departAt;

    /** 被通知的学生（行程成员）*/
    private Long memberUserId;
    private String memberName;
    private String memberSchool;

    /** 接收通知的紧急联系人 */
    private Long contactId;
    private String contactName;

    /** PARENT家长/COUNSELOR辅导员 */
    private String relation;
    private String phone;

    /** TRIP_REPORT行程报备/DEPART_REMIND出行提醒 */
    private String noticeType;

    @Column(columnDefinition = "TEXT")
    private String content;

    /** SENT已发送 */
    private String status = "SENT";

    /** 发起人（队长）*/
    private Long operatorId;
    private LocalDateTime createdAt;
}
