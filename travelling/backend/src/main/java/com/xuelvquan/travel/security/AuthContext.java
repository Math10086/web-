package com.xuelvquan.travel.security;

/**
 * 基于ThreadLocal的当前登录用户上下文。
 */
public final class AuthContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLE = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(Long userId, String role, String username) {
        USER_ID.set(userId);
        ROLE.set(role);
        USERNAME.set(username);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static String getRole() {
        return ROLE.get();
    }

    public static String getUsername() {
        return USERNAME.get();
    }

    public static void clear() {
        USER_ID.remove();
        ROLE.remove();
        USERNAME.remove();
    }
}
