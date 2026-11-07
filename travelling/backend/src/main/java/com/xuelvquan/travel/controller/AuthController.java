package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.BusinessException;
import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.entity.User;
import com.xuelvquan.travel.repository.UserRepository;
import com.xuelvquan.travel.security.AuthUtil;
import com.xuelvquan.travel.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String nickname = body.get("nickname");
        String school = body.get("school");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new BusinessException("用户名和密码不能为空");
        }
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException("用户名已存在");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname == null || nickname.isBlank() ? username : nickname);
        user.setSchool(school);
        user.setRole("USER");
        user.setVerified(false);
        user.setBanned(false);
        user.setCreditScore(100);
        user.setBloggerScore(0);
        user.setBloggerLevel(0);
        user.setCreatedAt(LocalDateTime.now());
        userRepository.save(user);

        String token = jwtUtil.generate(user.getId(), user.getRole(), user.getUsername());
        return Result.ok(tokenResult(token, user));
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        User user = userRepository.findByUsername(username == null ? "" : username)
                .orElseThrow(() -> new BusinessException("用户名或密码错误"));
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (Boolean.TRUE.equals(user.getBanned())) {
            throw new BusinessException(403, "账号已被封禁");
        }
        String token = jwtUtil.generate(user.getId(), user.getRole(), user.getUsername());
        return Result.ok(tokenResult(token, user));
    }

    @GetMapping("/me")
    public Result<User> me() {
        Long userId = AuthUtil.requireUserId();
        return Result.ok(userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(401, "用户不存在")));
    }

    private Map<String, Object> tokenResult(String token, User user) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("token", token);
        m.put("user", user);
        return m;
    }
}
