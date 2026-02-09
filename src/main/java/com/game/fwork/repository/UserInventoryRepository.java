package com.game.fwork.repository;

import com.game.fwork.entity.UserInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * 用户背包/库存数据访问接口
 */
@Repository
public interface UserInventoryRepository extends JpaRepository<UserInventory, Long> {
    /**
     * 查询用户的背包列表
     */
    List<UserInventory> findByUserId(Long userId);

    /**
     * 查询用户是否拥有特定物品
     * 用于购买前的重复性检查或使用道具前的库存检查
     */
    Optional<UserInventory> findByUserIdAndItemId(Long userId, Integer itemId);
}