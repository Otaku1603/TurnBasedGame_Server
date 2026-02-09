package com.game.fwork.controller;

import com.game.fwork.dto.BattleHistoryDTO;
import com.game.fwork.dto.UserProfileDTO;
import com.game.fwork.repository.BattleRecordRepository;
import com.game.fwork.repository.CharacterRepository;
import com.game.fwork.repository.UserRepository;
import com.game.fwork.service.SocialService;
import com.game.fwork.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 社交功能接口
 * 提供排行榜查询、战绩查询、好友管理和留言板交互功能
 */
@RestController
@RequestMapping("/api/social")
public class SocialController {

    @Autowired private SocialService socialService;
    @Autowired private JwtUtil jwtUtil;

    @Autowired private BattleRecordRepository battleRecordRepository;

    @Autowired private UserRepository userRepository;
    @Autowired private CharacterRepository characterRepository;

    /**
     * 获取全服排行榜
     * 按 ELO 分数倒序排列，展示前 50 名高玩信息
     */
    @GetMapping("/leaderboard")
    public Map<String, Object> getLeaderboard() {
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        // Service 现在返回 List<Map<String, Object>>
        res.put("data", socialService.getLeaderboard());
        return res;
    }

    /**
     * 获取我的好友列表
     * 数据包含好友的实时在线状态（从内存获取），方便邀请对战
     */
    @GetMapping("/friend/list")
    public Map<String, Object> getFriends(@RequestHeader("Authorization") String token) {
        Long userId = jwtUtil.getUserIdFromToken(token.substring(7));
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        // Service 现在返回 List<Map<String, Object>>
        res.put("data", socialService.getFriendList(userId));
        return res;
    }

    /// 添加好友
    @PostMapping("/friend/add")
    public Map<String, Object> addFriend(@RequestHeader("Authorization") String token, @RequestBody Map<String, Long> body) {
        Long userId = jwtUtil.getUserIdFromToken(token.substring(7));
        Long friendId = body.get("friendId");
        Map<String, Object> res = new HashMap<>();
        try {
            socialService.addFriend(userId, friendId);
            res.put("success", true);
            res.put("message", "添加成功");
        } catch (Exception e) {
            res.put("success", false);
            res.put("message", e.getMessage());
        }
        return res;
    }

    /// 发送留言
    @PostMapping("/message/send")
    public Map<String, Object> sendMessage(@RequestHeader("Authorization") String token, @RequestBody Map<String, Object> body) {
        Long userId = jwtUtil.getUserIdFromToken(token.substring(7));
        // 这里前端传过来的 JSON 数字可能会被转成 Integer，需要注意类型转换
        Long targetId = ((Number) body.get("targetId")).longValue();
        String content = (String) body.get("content");

        Map<String, Object> res = new HashMap<>();
        try {
            socialService.leaveMessage(userId, targetId, content);
            res.put("success", true);
            res.put("message", "留言成功");
        } catch (Exception e) {
            res.put("success", false);
            res.put("message", e.getMessage());
        }
        return res;
    }

    /// 查看留言
    @GetMapping("/message/list")
    public Map<String, Object> getMessages(@RequestHeader("Authorization") String token) {
        Long userId = jwtUtil.getUserIdFromToken(token.substring(7));
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("data", socialService.getMessages(userId));
        return res;
    }

    /// 获取指定用户的最近 10 场战绩
    @GetMapping("/history")
    public Map<String, Object> getBattleHistory(@RequestParam Long userId) {
        Map<String, Object> response = new HashMap<>();
        try {
            // 使用 Repository 已有的方法
            List<com.game.fwork.entity.BattleRecord> records = battleRecordRepository
                    .findByPlayer1IdOrPlayer2IdOrderByCreatedAtDesc(userId, userId);

            // 转换为 DTO
            List<BattleHistoryDTO> historyList = records.stream()
                    .limit(10) // 只取最近10场
                    .map(r -> BattleHistoryDTO.fromEntity(r, userId))
                    .collect(java.util.stream.Collectors.toList());

            response.put("success", true);
            response.put("data", historyList);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        return response;
    }

    /// 获取任意用户的详细资料
    @GetMapping("/user/profile")
    public Map<String, Object> getUserProfile(@RequestParam Long targetId) {
        Map<String, Object> response = new HashMap<>();
        try {
            com.game.fwork.entity.User user = userRepository.findById(targetId).orElse(null);
            if (user == null) {
                throw new RuntimeException("用户不存在");
            }

            UserProfileDTO dto = new UserProfileDTO();
            dto.setUserId(user.getId());
            dto.setNickname(user.getNickname());
            dto.setEloRating(user.getEloRating());
            dto.setGold(user.getGold());
            dto.setAvatarFrameId(user.getAvatarFrameId());
            dto.setTotalBattles(user.getTotalBattles());
            dto.setWinCount(user.getWinCount());
            dto.setWinRate(String.format("%.2f%%", user.getWinRate()));

            // 获取当前激活的角色
            com.game.fwork.entity.Character c = characterRepository.findByUserIdAndIsActive(targetId, 1).orElse(null);
            if (c != null) {
                UserProfileDTO.CharacterDTO charDto = new UserProfileDTO.CharacterDTO();
                charDto.setCharType(c.getCharType());
                charDto.setCharName(c.getCharName());
                charDto.setLevel(c.getLevel());
                charDto.setMaxHp(c.getMaxHp());
                charDto.setAttack(c.getAttack());
                charDto.setDefense(c.getDefense());
                charDto.setSpeed(c.getSpeed());
                dto.setCharacter(charDto);
            }

            response.put("success", true);
            response.put("data", dto);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        return response;
    }
}