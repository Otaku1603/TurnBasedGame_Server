package com.game.fwork.repository;

import com.game.fwork.entity.Character;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * 角色数据访问接口
 */
@Repository
public interface CharacterRepository extends JpaRepository<Character, Long> {

    /**
     * 查询某个用户的所有角色
     */
    List<Character> findByUserId(Long userId);

    /**
     * 查询用户当前激活（出战）的角色
     * 业务规则：一个用户同一时间只能有一个 isActive=1 的角色
     */
    Optional<Character> findByUserIdAndIsActive(Long userId, Integer isActive);

    /**
     * 查询用户拥有的特定职业角色
     */
    List<Character> findByUserIdAndCharType(Long userId, String charType);

    /**
     * 统计用户的角色总数，用于限制创建角色的数量
     */
    long countByUserId(Long userId);
}
