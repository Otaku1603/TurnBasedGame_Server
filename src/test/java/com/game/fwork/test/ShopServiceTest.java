package com.game.fwork.test;

import com.game.fwork.entity.Item;
import com.game.fwork.entity.User;
import com.game.fwork.entity.UserInventory;
import com.game.fwork.repository.ItemRepository;
import com.game.fwork.repository.UserInventoryRepository;
import com.game.fwork.repository.UserRepository;
import com.game.fwork.service.ShopService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("商城服务单元测试")
class ShopServiceTest {

    @InjectMocks
    private ShopService shopService;

    @Mock private UserRepository userRepository;
    @Mock private ItemRepository itemRepository;
    @Mock private UserInventoryRepository inventoryRepository;

    @Test
    @DisplayName("测试购买商品：金币不足应抛出异常")
    void testBuyItem_InsufficientGold() {
        // 1. 准备数据
        User poorUser = new User();
        poorUser.setId(1L);
        poorUser.setGold(100); // 只有100金币

        Item expensiveItem = new Item();
        expensiveItem.setId(1);
        expensiveItem.setPrice(1000); // 售价1000
        expensiveItem.setType("POTION");

        // 2. 模拟依赖行为
        when(userRepository.findById(1L)).thenReturn(Optional.of(poorUser));
        when(itemRepository.findById(1)).thenReturn(Optional.of(expensiveItem));

        // 3. 执行并断言
        Exception exception = assertThrows(RuntimeException.class, () -> {
            shopService.buyItem(1L, 1);
        });

        assertEquals("金币不足", exception.getMessage());

        // 4. 验证没发生扣款和发货
        verify(userRepository, never()).save(any());
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("测试购买商品：成功购买流程")
    void testBuyItem_Success() {
        // 1. 准备数据
        User richUser = new User();
        richUser.setId(2L);
        richUser.setGold(2000); // 有2000金币

        Item item = new Item();
        item.setId(2);
        item.setPrice(500);
        item.setType("POTION");

        // 2. 模拟依赖
        when(userRepository.findById(2L)).thenReturn(Optional.of(richUser));
        when(itemRepository.findById(2)).thenReturn(Optional.of(item));
        // 假设背包里还没这个东西
        when(inventoryRepository.findByUserIdAndItemId(2L, 2)).thenReturn(Optional.empty());

        // 3. 执行
        shopService.buyItem(2L, 2);

        // 4. 断言验证
        // 验证用户金币被扣除 (2000 - 500 = 1500)
        assertEquals(1500, richUser.getGold());
        verify(userRepository).save(richUser); // 确保调用了保存用户

        // 验证背包增加了物品
        verify(inventoryRepository).save(argThat(inventory ->
                inventory.getCount() == 1 &&
                        inventory.getItem().getId() == 2 &&
                        inventory.getUser().getId() == 2L
        ));
    }
}