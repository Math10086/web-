package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.BusinessException;
import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.entity.EmergencyContact;
import com.xuelvquan.travel.repository.EmergencyContactRepository;
import com.xuelvquan.travel.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 紧急联系人管理接口，关系字段统一存储为PARENT/COUNSELOR
 */
@RestController
@RequestMapping("/api/emergency")
@RequiredArgsConstructor
public class EmergencyController {

    private final EmergencyContactRepository repository;

    @GetMapping
    public Result<List<EmergencyContact>> mine() {
        Long userId = AuthUtil.requireUserId();
        List<EmergencyContact> list = repository.findByUserId(userId);
        // 兼容历史数据里直接存的「家长/辅导员」中文
        list.forEach(c -> c.setRelation(normalize(c.getRelation())));
        return Result.ok(list);
    }

    @PostMapping
    public Result<EmergencyContact> create(@RequestBody Map<String, String> body) {
        Long userId = AuthUtil.requireUserId();
        String relation = normalize(body.get("relation"));
        if (relation == null) {
            throw new BusinessException("关系必须为家长(PARENT)或辅导员(COUNSELOR)");
        }
        EmergencyContact c = new EmergencyContact();
        c.setUserId(userId);
        c.setName(body.get("name"));
        c.setRelation(relation);
        c.setPhone(body.get("phone"));
        c.setAuthorized(false);
        c.setCreatedAt(LocalDateTime.now());
        if (c.getName() == null || c.getName().isBlank() || c.getPhone() == null || c.getPhone().isBlank()) {
            throw new BusinessException("姓名和电话不能为空");
        }
        return Result.ok(repository.save(c));
    }

    @PutMapping("/{id}")
    public Result<EmergencyContact> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long userId = AuthUtil.requireUserId();
        EmergencyContact c = mustOwn(id, userId);
        if (body.containsKey("name")) c.setName(String.valueOf(body.get("name")));
        if (body.containsKey("phone")) c.setPhone(String.valueOf(body.get("phone")));
        if (body.containsKey("relation")) {
            String relation = normalize(String.valueOf(body.get("relation")));
            if (relation == null) {
                throw new BusinessException("关系必须为家长(PARENT)或辅导员(COUNSELOR)");
            }
            c.setRelation(relation);
        }
        if (body.containsKey("authorized")) {
            c.setAuthorized(Boolean.TRUE.equals(body.get("authorized"))
                    || Boolean.parseBoolean(String.valueOf(body.get("authorized"))));
        }
        return Result.ok(repository.save(c));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = AuthUtil.requireUserId();
        repository.delete(mustOwn(id, userId));
        return Result.ok();
    }

    private EmergencyContact mustOwn(Long id, Long userId) {
        EmergencyContact c = repository.findById(id)
                .orElseThrow(() -> new BusinessException("联系人不存在"));
        if (!c.getUserId().equals(userId)) {
            throw new BusinessException(403, "只能操作自己的紧急联系人");
        }
        return c;
    }

    /**
     * 中英文关系值统一为PARENT/COUNSELOR，无法识别返回null。
     * @param raw 前端传入的关系值，中英文均可
     * @return String 统一后的PARENT/COUNSELOR，无法识别时返回null
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim();
        if (v.contains("辅导员") || "COUNSELOR".equalsIgnoreCase(v) || "TEACHER".equalsIgnoreCase(v)) {
            return "COUNSELOR";
        }
        if (v.contains("家长") || v.contains("父母") || v.contains("父亲")
                || v.contains("母亲") || "PARENT".equalsIgnoreCase(v)) {
            return "PARENT";
        }
        return null;
    }
}
