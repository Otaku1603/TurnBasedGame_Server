package com.game.fwork.controller;

import com.game.fwork.config.GameConfig;
import com.game.fwork.entity.Character;
import com.game.fwork.entity.CharacterTemplate;
import com.game.fwork.entity.User;
import com.game.fwork.repository.CharacterRepository;
import com.game.fwork.repository.CharacterTemplateRepository;
import com.game.fwork.repository.UserRepository;
import com.game.fwork.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 用户认证接口控制器
 * 提供注册、登录、Token校验等核心安全功能
 * 遵循 RESTful 风格，返回 JSON 数据供前端调用
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired private UserRepository userRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private CharacterTemplateRepository characterTemplateRepository;

    /**
     * 用户注册
     * 1. 校验用户名唯一性和密码长度
     * 2. 创建用户账号并初始化默认角色（根据选定的职业）
     * 3. 注册成功后直接签发 Token，实现“注册即登录”
     */
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> requestBody) {
        Map<String, Object> response = new HashMap<>();

        try {
            // 获取参数
            String username = requestBody.get("username");
            String password = requestBody.get("password");
            String nickname = requestBody.get("nickname");
            String email = requestBody.get("email");
            String charType = requestBody.getOrDefault("charType", "warrior");

            // 参数校验
            if (username == null || username.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "用户名不能为空");
                return response;
            }
            if (password == null || password.length() < 6) {
                response.put("success", false);
                response.put("message", "密码长度不能少于6位");
                return response;
            }

            // 检查用户名是否已存在
            if (userRepository.existsByUsername(username)) {
                response.put("success", false);
                response.put("message", "用户名已存在");
                return response;
            }

            // 创建用户
            User user = new User();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(password));
            user.setNickname(nickname != null ? nickname : username);
            user.setEmail(email);
            user.setEloRating(1000);

            User savedUser = userRepository.save(user);

            // 自动创建默认角色
            // 从数据库查询模板
            CharacterTemplate template = characterTemplateRepository.findByCharType(charType)
                    .orElseThrow(() -> new RuntimeException("无效的职业类型: " + charType));

            Character newCharacter = new Character();
            newCharacter.setUser(savedUser);
            // 角色名 = 昵称 + 职业前缀 (例如：玩家1的见习剑圣)
            newCharacter.setCharName(nickname + "的" + template.getCharNamePrefix());
            newCharacter.setCharType(template.getCharType());

            // 从模板读取数值
            newCharacter.setMaxHp(template.getBaseMaxHp());
            newCharacter.setCurrentHp(template.getBaseMaxHp());
            newCharacter.setAttack(template.getBaseAttack());
            newCharacter.setDefense(template.getBaseDefense());
            newCharacter.setSpeed(template.getBaseSpeed());

            newCharacter.setIsActive(1);
            characterRepository.save(newCharacter);

            // 生成JWT Token
            String token = jwtUtil.generateToken(savedUser.getId(), savedUser.getUsername());

            // 返回成功响应
            response.put("success", true);
            response.put("message", "注册成功");
            response.put("token", token);
            response.put("userId", savedUser.getId());
            response.put("username", savedUser.getUsername());
            response.put("nickname", savedUser.getNickname());

        } catch (Exception e) {
            logger.error("用户注册失败", e);
            response.put("success", false);
            response.put("message", "注册失败: " + e.getMessage());
        }

        return response;
    }

    /**
     * 用户登录
     * 1. 校验用户名密码（使用 BCrypt 匹配加密密码）
     * 2. 检查账号是否被封禁
     * 3. 生成 JWT Token 并返回用户基础信息和角色信息
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> requestBody) {
        Map<String, Object> response = new HashMap<>();

        try {
            // 获取参数
            String username = requestBody.get("username");
            String password = requestBody.get("password");

            // 参数校验
            if (username == null || username.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "用户名不能为空");
                return response;
            }
            if (password == null || password.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "密码不能为空");
                return response;
            }

            // 查询用户
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (!userOpt.isPresent()) {
                response.put("success", false);
                response.put("message", "用户名或密码错误");
                return response;
            }

            User user = userOpt.get();

            // 检查账号状态
            if (user.isBanned()) {
                response.put("success", false);
                response.put("message", "账号已被封禁");
                return response;
            }

            // 验证密码（BCrypt自动处理盐值）
            if (!passwordEncoder.matches(password, user.getPassword())) {
                response.put("success", false);
                response.put("message", "用户名或密码错误");
                return response;
            }

            // 生成JWT Token
            String token = jwtUtil.generateToken(user.getId(), user.getUsername());

            // 查询激活角色
            Optional<Character> activeCharOpt = characterRepository
                    .findByUserIdAndIsActive(user.getId(), 1);

            // 返回成功响应
            response.put("success", true);
            response.put("message", "登录成功");
            response.put("token", token);
            response.put("userId", user.getId());
            response.put("username", user.getUsername());
            response.put("nickname", user.getNickname());
            response.put("eloRating", user.getEloRating());
            response.put("totalBattles", user.getTotalBattles());
            response.put("winCount", user.getWinCount());
            response.put("winRate", String.format("%.2f%%", user.getWinRate()));
            response.put("gold", user.getGold());
            response.put("avatarFrameId", user.getAvatarFrameId());

            // 如果有激活角色，也返回角色信息
            if (activeCharOpt.isPresent()) {
                Character character = activeCharOpt.get();
                Map<String, Object> charInfo = new HashMap<>();
                charInfo.put("characterId", character.getId());
                charInfo.put("charName", character.getCharName());
                charInfo.put("charType", character.getCharType());
                charInfo.put("level", character.getLevel());
                charInfo.put("maxHp", character.getMaxHp());
                charInfo.put("attack", character.getAttack());
                charInfo.put("defense", character.getDefense());
                charInfo.put("speed", character.getSpeed());
                response.put("character", charInfo);
            }

        } catch (Exception e) {
            logger.error("用户登录失败", e);
            response.put("success", false);
            response.put("message", "登录失败: " + e.getMessage());
        }

        return response;
    }

    /**
     * Token 有效性验证
     * 前端在页面加载时调用，用于判断本地 Token 是否过期，决定是否跳转登录页
     */
    @GetMapping("/verify")
    public Map<String, Object> verifyToken(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        Map<String, Object> response = new HashMap<>();

        try {
            // 检查Authorization头
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                response.put("success", false);
                response.put("message", "缺少Token");
                return response;
            }

            // 提取Token（去掉"Bearer "前缀）
            String token = authorization.substring(7);

            // 验证Token
            if (!jwtUtil.validateToken(token)) {
                response.put("success", false);
                response.put("message", "Token无效或已过期");
                return response;
            }

            // 解析用户信息
            Long userId = jwtUtil.getUserIdFromToken(token);
            String username = jwtUtil.getUsernameFromToken(token);

            response.put("success", true);
            response.put("message", "Token有效");
            response.put("userId", userId);
            response.put("username", username);

        } catch (Exception e) {
            logger.error("Token验证失败", e);
            response.put("success", false);
            response.put("message", "验证失败: " + e.getMessage());
        }

        return response;
    }
}