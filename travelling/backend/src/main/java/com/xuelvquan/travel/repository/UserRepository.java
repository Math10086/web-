package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    /** B6：学号唯一性校验（同一学号不可被两个账号认证） */
    Optional<User> findByStudentNo(String studentNo);

    /** B5/B7：撮合推荐池（已认证且填过标签档案的在校学生） */
    List<User> findByVerifiedTrueAndRoleIn(List<String> roles);

    List<User> findByUsernameContainingOrNicknameContainingOrderByIdDesc(String username, String nickname);

    List<User> findTop20ByRoleOrderByBloggerScoreDesc(String role);

    long countByCreatedAtAfter(LocalDateTime time);
}
