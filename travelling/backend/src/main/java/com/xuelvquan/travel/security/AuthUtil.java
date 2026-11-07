package com.xuelvquan.travel.security;

import com.xuelvquan.travel.common.BusinessException;

import java.util.Arrays;

/**
 * 控制器内鉴权工具：写操作调用requireUserId()，角色操作调用requireRole()。
 */
public final class AuthUtil {

    private AuthUtil() {
    }

    public static Long currentUserId() {
        return AuthContext.getUserId();
    }

    public static Long requireUserId() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录或登录已失效");
        }
        return userId;
    }

    public static String currentRole() {
        return AuthContext.getRole();
    }

    public static boolean isAdmin() {
        return "ADMIN".equals(AuthContext.getRole());
    }

    /**
     * 要求当前用户具有指定角色之一，否则403；未登录401。
     * @param roles 允许的角色，命中任意一个即通过
     */
    public static void requireRole(String... roles) {
        requireUserId();
        String role = AuthContext.getRole();
        if (roles != null && Arrays.stream(roles).noneMatch(r -> r.equals(role))) {
            throw new BusinessException(403, "权限不足");
        }
    }

    /**
     * C端业务功能（社区互动、结伴、路线规划、足迹、消费报表、家校安全等）的可用角色。
     * 游客仅可浏览公开内容，商家不参与这些业务功能，管理员另走后台。
     */
    public static void requireBusiness() {
        requireRole("USER", "BLOGGER", "GUIDE");
    }

    /**
     * 导游侧能力（发布导游服务、接单确认与完成）：**仅认证导游GUIDE**。
     * 需求口径：接单模块只有导游账号具备，攻略博主/普通用户只保留「找导游」下单能力；
     * 博主申请导游认证并通过审核后，角色会自动转为GUIDE，届时即可接单。
     */
    public static void requireGuide() {
        requireRole("GUIDE");
    }

    /**
     * 商家侧能力（开店上架、广告投放、发货）：仅MERCHANT。
     * 管理员只做后台治理，不参与经营，故不含ADMIN。
     */
    public static void requireMerchant() {
        requireRole("MERCHANT");
    }

    /**
     * 「非管理员」即可的写操作：管理员只保留后台运营与治理能力，
     * 前台使用功能（内容发布、点赞评论、报名、举报、入驻申请等）一律清空。
     * 未登录仍返回401。
     */
    public static void requireNonAdmin() {
        requireUserId();
        if (isAdmin()) {
            throw new BusinessException(403, "管理员账号仅可进行后台运营与治理操作");
        }
    }

    /**
     * C端前台内容（社区圈层、论坛帖子、热搜、结伴、导游服务等）的可见性：
     * 游客可匿名浏览，学生用户/博主/导游可浏览与互动；
     * **管理员与商家均已退出前台**，即使列表类接口无需登录也不放行。
     */
    public static void requireFrontOrGuest() {
        String role = AuthContext.getRole();
        if ("ADMIN".equals(role)) {
            throw new BusinessException(403, "管理员账号仅可访问管理后台");
        }
        if ("MERCHANT".equals(role)) {
            throw new BusinessException(403, "商家账号仅可进行开店、投放、交易与评价管理");
        }
    }

    /**
     * 商业合作方（商家/导游）的经营类能力：需求洞察、供需缺口等。
     * 这是给「供货方」看的经营参考，学生与普通用户不参与，故不放行。
     *
     * @return 当前角色（"MERCHANT" 或 "GUIDE"），便于调用方按角色切换口径
     */
    public static String requireBusinessPartner() {
        requireRole("MERCHANT", "GUIDE");
        return AuthContext.getRole();
    }

    /**
     * 「前台+游客+管理员」：用于既服务C端前台、又会被管理后台数据看板
     * 复用的统计类接口（如热词云、热搜榜）。管理员看平台统计数据是正当场景，
     * 故仅拒绝商家，不再把管理员挡在外面。
     */
    public static void requireFrontOrAdmin() {
        String role = AuthContext.getRole();
        if ("MERCHANT".equals(role)) {
            throw new BusinessException(403, "商家账号仅可进行开店、投放、交易与评价管理");
        }
    }
}
