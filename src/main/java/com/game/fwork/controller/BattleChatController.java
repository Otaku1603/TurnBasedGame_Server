package com.game.fwork.controller;

import com.game.fwork.entity.User;
import com.game.fwork.repository.UserRepository;
import com.game.fwork.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 战斗内聊天接口
 * 使用 Redis List 存储临时的战斗弹幕/聊天记录
 */
@RestController
@RequestMapping("/api/battle/chat")
public class BattleChatController {

    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserRepository userRepository;

    /**
     * 发送弹幕消息
     * 消息存储在 Redis 中，设置 1 小时过期，不占用数据库空间
     */
    @PostMapping("/send")
    public Map<String, Object> send(@RequestHeader("Authorization") String token,
                                    @RequestBody Map<String, String> params) {
        Map<String, Object> response = new HashMap<>();
        try {
            Long userId = jwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
            User user = userRepository.findById(userId).orElseThrow();

            String battleId = params.get("battleId");
            String content = params.get("content");

            // 格式： "Nickname: Content"
            String msg = user.getNickname() + ": " + content;

            String key = "battle_chat:" + battleId;
            redisTemplate.opsForList().rightPush(key, msg);
            redisTemplate.expire(key, 1, TimeUnit.HOURS); // 1小时后自动清理

            response.put("success", true);
        } catch (Exception e) {
            response.put("success", false);
        }
        return response;
    }

    /// 获取战斗消息
    @GetMapping("/list")
    public Map<String, Object> list(@RequestParam String battleId) {
        Map<String, Object> response = new HashMap<>();
        try {
            String key = "battle_chat:" + battleId;
            // 获取最近 20 条
            List<String> messages = redisTemplate.opsForList().range(key, 0, -1);
            if (messages == null) messages = new ArrayList<>();

            response.put("success", true);
            response.put("data", messages);
        } catch (Exception e) {
            response.put("success", false);
        }
        return response;
    }
}