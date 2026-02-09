package com.game.fwork.manager;

import com.game.fwork.entity.Item;
import com.game.fwork.repository.ItemRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 道具缓存管理器
 */
@Component
public class ItemManager {

    private static final Logger logger = LoggerFactory.getLogger(ItemManager.class);

    @Autowired
    private ItemRepository itemRepository;

    private final Map<Integer, Item> itemCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refresh();
    }

    /**
     * 刷新缓存（从数据库重新加载）
     */
    public void refresh() {
        List<Item> items = itemRepository.findAll();

        itemCache.clear();
        for (Item item : items) {
            itemCache.put(item.getId(), item);
        }
        logger.info("道具数据加载/刷新完成，共加载 {} 个道具", items.size());
    }

    public Item getItem(Integer itemId) {
        return itemCache.get(itemId);
    }
}