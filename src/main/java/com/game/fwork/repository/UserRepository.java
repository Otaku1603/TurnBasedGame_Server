package com.game.fwork.repository;

import com.game.fwork.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * 用户核心数据访问接口
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 根据用户名查询用户
     */
    Optional<User> findByUsername(String username);

    /**
     * 检查用户名是否已存在
     */
    boolean existsByUsername(String username);

    /**
     * 根据邮箱查询用户
     */
    Optional<User> findByEmail(String email);

    /**
     * 根据账号状态筛选用户（例如：查询所有被封禁的用户）
     */
    java.util.List<User> findByStatus(Integer status);

    /**
     * 查询ELO分数在指定范围内的用户
     */
    java.util.List<User> findByEloRatingBetween(Integer minElo, Integer maxElo);
}
