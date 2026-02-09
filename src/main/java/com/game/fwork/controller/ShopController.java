package com.game.fwork.controller;

import com.game.fwork.service.ShopService;
import com.game.fwork.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 商城与背包接口
 * 处理商品的展示、购买以及背包物品的装备操作
 */
@RestController
@RequestMapping("/api/shop")
public class ShopController {

    @Autowired private ShopService shopService;
    @Autowired private JwtUtil jwtUtil;

    /// 获取商品列表
    @GetMapping("/items")
    public Map<String, Object> getShopItems() {
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("data", shopService.getShopList());
        return res;
    }

    /// 获取我的背包
    @GetMapping("/inventory")
    public Map<String, Object> getInventory(@RequestHeader("Authorization") String token) {
        Long userId = jwtUtil.getUserIdFromToken(token.substring(7));
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("data", shopService.getUserInventory(userId));
        return res;
    }

    /**
     * 购买商品
     * 核心逻辑在 Service 层事务中处理，此处仅负责参数接收和异常捕获
     */
    @PostMapping("/buy")
    public Map<String, Object> buyItem(@RequestHeader("Authorization") String token, @RequestBody Map<String, Integer> body) {
        Long userId = jwtUtil.getUserIdFromToken(token.substring(7));
        Integer itemId = body.get("itemId");
        Map<String, Object> res = new HashMap<>();
        try {
            shopService.buyItem(userId, itemId);
            res.put("success", true);
            res.put("message", "购买成功");
        } catch (Exception e) {
            res.put("success", false);
            res.put("message", e.getMessage());
        }
        return res;
    }

    /**
     * 装备物品（如头像框）
     * 更新用户的当前佩戴设置，并在好友列表等位置展示
     */
    @PostMapping("/equip")
    public Map<String, Object> equipItem(@RequestHeader("Authorization") String token, @RequestBody Map<String, Integer> body) {
        Long userId = jwtUtil.getUserIdFromToken(token.substring(7));
        Integer itemId = body.get("itemId");
        Map<String, Object> res = new HashMap<>();
        try {
            shopService.equipAvatarFrame(userId, itemId);
            res.put("success", true);
            res.put("message", "装备成功");
        } catch (Exception e) {
            res.put("success", false);
            res.put("message", e.getMessage());
        }
        return res;
    }
}