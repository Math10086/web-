package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 位置共享授权（数据模型：位置共享LocationShare）。
 *
 * 队员把自己的实时位置「限时、明确授权」给紧急联系人（家长/辅导员）查看，
 * 联系人凭viewToken打开公开查看页，无需注册平台账号。
 * 遵循文档「隐私与位置权限」要求：按需授权、限时可查、仅限紧急联系人、最小化收集。
 */
@Data
@Entity
@Table(name = "location_shares")
public class LocationShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long tripId;
    private Long userId;

    /** 被授权查看的紧急联系人 */
    private Long contactId;
    private String contactName;

    /** 关系：PARENT家长/COUNSELOR辅导员 */
    private String relation;
    private String phone;

    /** 授权凭证：家长凭https://.../track/{viewToken}查看，无需登录 */
    @Column(length = 64)
    private String viewToken;

    /** 授权时段 */
    private LocalDateTime startAt;
    private LocalDateTime endAt;

    /** ACTIVE生效中/REVOKED已撤销/EXPIRED已过期 */
    private String status = "ACTIVE";

    /** 是否附带同行队友大致位置（便于家长判断是否离队） */
    private Boolean includeTeammates = true;

    /** 授权时的一句话留言 */
    @Column(length = 255)
    private String note;

    private LocalDateTime createdAt;
    private LocalDateTime lastViewedAt;
    private Integer viewCount = 0;
}
