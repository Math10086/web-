package com.xuelvquan.travel.service;

import com.xuelvquan.travel.entity.User;
import com.xuelvquan.travel.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 演示账号密码钉死器（每次启动都执行）。
 *
 * 背景：DataInitializer只在users表为空时播种账号；一旦数据库被重新导入
 * （旧SQL dump里的BCrypt哈希与文档口令对不上），演示账号就会登录失败。
 *
 * 方案：每次启动都校验6个演示账号的密码，与标准口令不符就强制重置，
 * 保证答辩演示时账号密码永远可用：
 *   admin/admin123，其余演示账号/123456。
 *
 * 注意：只动6个预置账号，用户自行注册的账号一律不碰。
 */
@Slf4j
@Component
@Order(30) // 早于AiMemorySeeder(40)，保证先修账号再种对话
@RequiredArgsConstructor
public class DemoAccountPasswordFixer implements CommandLineRunner {

    /** 演示账号 -> 标准口令（与前端登录页“一键填充”完全一致） */
    private static final Map<String, String> DEMO_ACCOUNTS = new LinkedHashMap<>() {{
        put("admin", "admin123");
        put("xiaolu", "123456");
        put("dashan", "123456");
        put("aya", "123456");
        put("shang", "123456");
        put("dao", "123456");
    }};

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        int fixed = 0;
        for (Map.Entry<String, String> e : DEMO_ACCOUNTS.entrySet()) {
            String username = e.getKey();
            String expected = e.getValue();
            User user = userRepository.findByUsername(username).orElse(null);
            if (user == null) {
                log.warn("[学旅圈] 演示账号 {} 不存在（users 表可能被部分导入），跳过密码校准", username);
                continue;
            }
            if (passwordEncoder.matches(expected, user.getPassword())) {
                continue; // 口令本来就对，不动
            }
            user.setPassword(passwordEncoder.encode(expected));
            userRepository.save(user);
            fixed++;
            log.info("[学旅圈] 演示账号 {} 的密码已重置为标准口令", username);
        }
        if (fixed > 0) {
            log.info("[学旅圈] 本次启动共校准 {} 个演示账号密码（admin/admin123，其余/123456）", fixed);
        } else {
            log.info("[学旅圈] 演示账号密码校验通过，无需校准");
        }
    }
}
