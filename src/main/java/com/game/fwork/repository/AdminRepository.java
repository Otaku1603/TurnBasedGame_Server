package com.game.fwork.repository;

import com.game.fwork.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 管理员数据访问接口
 * 提供对 t_admin 表的基础 CRUD 操作
 */
@Repository
public interface AdminRepository extends JpaRepository<Admin, Long> {
    /**
     * 根据用户名查找管理员
     */
    Optional<Admin> findByUsername(String username);
}