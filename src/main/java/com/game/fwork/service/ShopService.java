package com.game.fwork.service;

import com.game.fwork.entity.Item;
import com.game.fwork.entity.User;
import com.game.fwork.entity.UserInventory;
import com.game.fwork.repository.ItemRepository;
import com.game.fwork.repository.UserInventoryRepository;
import com.game.fwork.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 商城与背包业务服务
 * 处理物品购买、库存管理和装备佩戴逻辑
 */
@Service
public class ShopService {

    @Autowired private ItemRepository itemRepository;
    @Autowired private UserInventoryRepository inventoryRepository;
    @Autowired private UserRepository userRepository;

    public List<Item> getShopList() {
        return itemRepository.findAll();
    }

    public List<UserInventory> getUserInventory(Long userId) {
        return inventoryRepository.findByUserId(userId);
    }

    /**
     * 购买物品
     * 1. 校验物品唯一性（如头像框不可重复买）
     * 2. 校验并扣除金币
     * 3. 更新背包数据
     */
    @Transactional  // 保证扣除金币和发放物品的原子性
    public void buyItem(Long userId, Integer itemId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("用户不存在"));
        Item item = itemRepository.findById(itemId).orElseThrow(() -> new RuntimeException("商品不存在"));

        // 1. 检查唯一性物品是否已拥有
        // 如果是头像框，且背包里已经有了，直接报错
        if ("AVATAR_FRAME".equals(item.getType())) {
            if (inventoryRepository.findByUserIdAndItemId(userId, itemId).isPresent()) {
                throw new RuntimeException("您已拥有该头像框，无需重复购买");
            }
        }

        // 2. 检查金币
        if (user.getGold() < item.getPrice()) {
            throw new RuntimeException("金币不足");
        }

        // 3. [关键] 扣款
        user.setGold(user.getGold() - item.getPrice());
        userRepository.save(user);

        // 4. 发货
        UserInventory inventory = inventoryRepository.findByUserIdAndItemId(userId, itemId)
                .orElse(new UserInventory(user, item, 0));

        inventory.setCount(inventory.getCount() + 1);
        inventoryRepository.save(inventory);
    }

    /**
     * 装备/更换头像框
     * 逻辑：将背包中该物品设为已装备，同时重置其他同类物品为未装备
     */
    @Transactional
    public void equipAvatarFrame(Long userId, Integer itemId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("用户不存在"));

        // 1. 验证是否拥有该物品
        UserInventory targetInv = inventoryRepository.findByUserIdAndItemId(userId, itemId)
                .orElseThrow(() -> new RuntimeException("未拥有该头像框"));

        if (!"AVATAR_FRAME".equals(targetInv.getItem().getType())) {
            throw new RuntimeException("该物品不是头像框");
        }

        // 2. 遍历该用户背包，重置所有头像框的装备状态
        List<UserInventory> allInventory = inventoryRepository.findByUserId(userId);

        for (UserInventory inv : allInventory) {
            if ("AVATAR_FRAME".equals(inv.getItem().getType())) {
                // 如果是当前要装备的，设为 true；否则设为 false
                boolean equip = inv.getItem().getId().equals(itemId);
                inv.setIsEquipped(equip);

                // 顺便更新一下实体的状态，虽然 JPA 会在事务结束时自动 flush，但显式 save 更稳妥
                inventoryRepository.save(inv);
            }
        }

        // 3. 更新用户表上的当前佩戴ID (用于个人资料页快速读取)
        user.setAvatarFrameId(itemId);
        userRepository.save(user);
    }
}