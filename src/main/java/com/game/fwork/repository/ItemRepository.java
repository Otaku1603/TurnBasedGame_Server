package com.game.fwork.repository;

import com.game.fwork.entity.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * 物品/商城数据访问接口
 */
@Repository
public interface ItemRepository extends JpaRepository<Item, Integer> {
    /**
     * 根据类型筛选商品（例如：仅显示头像框，或仅显示药水）
     */
    List<Item> findByType(String type);
}