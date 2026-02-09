package com.game.fwork.controller;

import com.game.fwork.entity.Admin;
import com.game.fwork.entity.Battle;
import com.game.fwork.entity.BattleRecord;
import com.game.fwork.entity.User;
import com.game.fwork.manager.BattleManager;
import com.game.fwork.manager.ItemManager;
import com.game.fwork.manager.LuaPerformanceMonitor;
import com.game.fwork.manager.SkillManager;
import com.game.fwork.netty.session.SessionManager;
import com.game.fwork.repository.AdminRepository;
import com.game.fwork.repository.BattleRecordRepository;
import com.game.fwork.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.fwork.util.DamageCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;

import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collections;

/**
 * 后台管理控制器
 * 负责管理员登录、数据看板、用户封禁、战报查询等功能
 * 采用 Thymeleaf 模板引擎进行服务端页面渲染（非前后端分离）
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private BattleManager battleManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BattleRecordRepository battleRecordRepository; // 战斗记录Repository

    @Autowired
    private DamageCalculator damageCalculator;

    @Autowired
    private LuaPerformanceMonitor performanceMonitor;

    @Autowired
    private SkillManager skillManager;

    @Autowired
    private ItemManager itemManager;

    /**
     * 管理后台首页（无登录信息则重定向到登录页）
     */
    @GetMapping("")
    public String index(HttpSession session) {
        if (session.getAttribute("admin") != null) {
            return "redirect:/admin/dashboard";
        }
        return "redirect:/admin/login";
    }

    /**
     * 登录页面
     */
    @GetMapping("/login")
    public String loginPage(HttpSession session) {
        if (session.getAttribute("admin") != null) {
            return "redirect:/admin/dashboard";
        }
        return "admin/login";
    }

    /**
     * 处理登录
     */
    @PostMapping("/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        HttpSession session,
                        Model model) {
        try {
            Admin admin = adminRepository.findByUsername(username).orElse(null);

            if (admin == null || !passwordEncoder.matches(password, admin.getPassword())) {
                model.addAttribute("error", "用户名或密码错误");
                return "admin/login";
            }

            // 更新最后登录时间
            admin.setLastLogin(LocalDateTime.now());
            adminRepository.save(admin);

            // 存入Session
            session.setAttribute("admin", admin);

            logger.info("管理员登录成功: username={}", username);
            return "redirect:/admin/dashboard";

        } catch (Exception e) {
            logger.error("管理员登录失败", e);
            model.addAttribute("error", "登录失败: " + e.getMessage());
            return "admin/login";
        }
    }

    /**
     * 登出
     */
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.removeAttribute("admin");
        return "redirect:/admin/login";
    }

    /**
     * 管理后台首页（控制台）
     * 聚合展示系统核心指标：在线人数、进行中的战斗、历史数据统计等
     */
    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        if (!checkLogin(session)) {
            return "redirect:/admin/login";
        }
        try {
            // ========== 原有统计数据 ==========
            // 总用户数（从MySQL查询）
            long totalUsers = userRepository.count();
            // 在线人数（从SessionManager查询）
            int onlineUsers = sessionManager.getOnlineCount();
            // 进行中战斗（从BattleManager查询内存中的战斗）
            int activeBattles = battleManager.getBattleCount();
            // Redis战报数量（TTL=7天的战报）
            Set<String> reportKeys = stringRedisTemplate.keys("battle:report:*");
            int totalReports = reportKeys != null ? reportKeys.size() : 0;

            // ========== 历史战斗统计（从MySQL查询）==========
            // 总战斗数（t_battle_record表）
            long totalHistoryBattles = battleRecordRepository.count();
            // 正常结束的战斗数
            long normalEndBattles = battleRecordRepository
                    .findByEndReasonOrderByCreatedAtDesc("NORMAL").size();
            // 投降结束的战斗数
            long surrenderBattles = battleRecordRepository
                    .findByEndReasonOrderByCreatedAtDesc("SURRENDER").size();
            // 异常结束的战斗数（超时+断线）
            long timeoutBattles = battleRecordRepository
                    .findByEndReasonOrderByCreatedAtDesc("TIMEOUT").size();
            long disconnectBattles = battleRecordRepository
                    .findByEndReasonOrderByCreatedAtDesc("DISCONNECT").size();
            long abnormalBattles = timeoutBattles + disconnectBattles;

            // ========== 传递数据到页面 ==========
            // 将统计数据放入 Model，传递给前端页面进行渲染
            // 原有数据
            model.addAttribute("totalUsers", totalUsers);
            model.addAttribute("onlineUsers", onlineUsers);
            model.addAttribute("activeBattles", activeBattles);
            model.addAttribute("totalReports", totalReports);
            // 历史战斗统计
            model.addAttribute("totalHistoryBattles", totalHistoryBattles);
            model.addAttribute("normalEndBattles", normalEndBattles);
            model.addAttribute("surrenderBattles", surrenderBattles);
            model.addAttribute("abnormalBattles", abnormalBattles);
            logger.info("管理员进入控制台，总用户={}, 在线={}, 进行中战斗={}, 历史战斗={}",
                    totalUsers, onlineUsers, activeBattles, totalHistoryBattles);
            return "admin/dashboard";
        } catch (Exception e) {
            logger.error("加载控制台数据失败", e);
            model.addAttribute("error", "加载数据失败：" + e.getMessage());
            return "admin/dashboard";
        }
    }

    /**
     * 用户管理页面
     */
    @GetMapping("/users")
    public String users(HttpSession session,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                        Model model) {
        if (!checkLogin(session)) {
            return "redirect:/admin/login";
        }

        // 查询所有用户（简单分页）
        List<User> users = userRepository.findAll();

        model.addAttribute("users", users);
        return "admin/users";
    }

    /**
     * 用户详情页面
     */
    @GetMapping("/users/{id}")
    public String userDetail(HttpSession session,
                             @PathVariable Long id,
                             Model model) {
        if (!checkLogin(session)) {
            return "redirect:/admin/login";
        }

        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            model.addAttribute("error", "用户不存在");
            return "admin/error";
        }

        // 检查是否在线
        boolean online = sessionManager.isOnline(id);

        // 检查是否在战斗中
        Battle battle = battleManager.getBattleByUserId(id);

        model.addAttribute("user", user);
        model.addAttribute("online", online);
        model.addAttribute("inBattle", battle != null);
        model.addAttribute("battleId", battle != null ? battle.getBattleId() : null);

        return "admin/user_detail";
    }

    /**
     * 封禁/解封用户接口
     * 通过 AJAX 调用，切换用户的 status 状态（1=正常，0=封禁）
     */
    @PostMapping("/users/{id}/toggle-ban")
    @ResponseBody
    public String toggleBan(HttpSession session, @PathVariable Long id) {
        if (!checkLogin(session)) {
            return "{\"success\":false,\"message\":\"未登录\"}";
        }

        try {
            User user = userRepository.findById(id).orElse(null);
            if (user == null) {
                return "{\"success\":false,\"message\":\"用户不存在\"}";
            }

            // 切换状态（1=正常，0=封禁）
            if (user.getStatus() == 1) {
                user.setStatus(0);
            } else {
                user.setStatus(1);
            }

            userRepository.save(user);

            logger.info("用户状态已更新: userId={}, status={}", id, user.getStatus());

            String action = user.getStatus() == 0 ? "封禁" : "解封";
            return "{\"success\":true,\"message\":\"" + action + "成功\",\"status\":" + user.getStatus() + "}";

        } catch (Exception e) {
            logger.error("更新用户状态失败: userId={}", id, e);
            return "{\"success\":false,\"message\":\"操作失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 战斗历史列表页
     * 支持多维度筛选（用户ID、结束原因）和分页查询
     * 混合数据源：从 MySQL 查询归档记录，从 Redis 查询近期热数据（如果有）
     */
    @GetMapping("/battles")
    public String battleHistory(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String endReason,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String dir,
            Model model,
            HttpSession session
    ) {
        // 登录检查
        if (!checkLogin(session)) {
            return "redirect:/admin/login";
        }

        try {
            // ================== MySQL 分页查询逻辑 (Start) ==================

            // 构建分页和排序对象
            Sort.Direction direction = "asc".equalsIgnoreCase(dir) ? Sort.Direction.ASC : Sort.Direction.DESC;
            // 限制每页最大50条，防止恶意请求
            int safeSize = Math.min(size, 50);
            Pageable pageable = PageRequest.of(page, safeSize, Sort.by(direction, sort));

            // 构建动态查询条件 (Specification)
            Specification<BattleRecord> spec = (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();

                // 筛选用户：(player1Id == userId OR player2Id == userId)
                if (userId != null) {
                    Predicate p1 = cb.equal(root.get("player1Id"), userId);
                    Predicate p2 = cb.equal(root.get("player2Id"), userId);
                    predicates.add(cb.or(p1, p2));
                }

                // 筛选结束原因
                if (endReason != null && !endReason.isEmpty()) {
                    predicates.add(cb.equal(root.get("endReason"), endReason));
                }

                return cb.and(predicates.toArray(new Predicate[0]));
            };

            // 执行分页查询
            Page<BattleRecord> pageResult = battleRecordRepository.findAll(spec, pageable);

            // 传递分页结果到页面
            model.addAttribute("battles", pageResult.getContent());
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", pageResult.getTotalPages());
            model.addAttribute("totalItems", pageResult.getTotalElements());
            model.addAttribute("sortField", sort);
            model.addAttribute("sortDir", dir);

            // 回显筛选框的值
            model.addAttribute("filterUserId", userId);
            model.addAttribute("filterEndReason", endReason);

            // 如果筛选了用户，查询一下昵称方便显示
            if (userId != null) {
                User user = userRepository.findById(userId).orElse(null);
                if (user != null) {
                    model.addAttribute("filterUserNickname", user.getNickname());
                }
            }

            // ================== 统计面板数据 (Start) ==================
            // 注意：统计数据我们依然查全量，不受筛选和分页影响，用于顶部展示
            long totalBattles = battleRecordRepository.count();
            // 这里用 countByEndReason 会更高效，但为了兼容之前的逻辑，我们用 filter 计算
            // 如果数据量巨大，建议改为专门的 count SQL
            long normalEnd = battleRecordRepository.findByEndReasonOrderByCreatedAtDesc("NORMAL").size();
            long surrender = battleRecordRepository.findByEndReasonOrderByCreatedAtDesc("SURRENDER").size();
            long timeoutEnd = battleRecordRepository.findByEndReasonOrderByCreatedAtDesc("TIMEOUT").size();
            long disconnectEnd = battleRecordRepository.findByEndReasonOrderByCreatedAtDesc("DISCONNECT").size();
            long abnormalEnd = timeoutEnd + disconnectEnd;

            model.addAttribute("totalBattles", totalBattles);
            model.addAttribute("normalEnd", normalEnd);
            model.addAttribute("surrender", surrender);
            model.addAttribute("abnormalEnd", abnormalEnd);

            // ================== Redis 战报内存分页 (Start) ==================
            // Redis KEYS 命令无法分页，只能全取出后在内存切片

            Set<String> allKeys = stringRedisTemplate.keys("battle:report:*");
            List<String> keyList = new ArrayList<>(allKeys != null ? allKeys : Collections.emptySet());

            // 简单倒序（最新的战报ID通常更大）
            Collections.sort(keyList, Collections.reverseOrder());

            // 计算内存分页的起始和结束索引
            int start = page * safeSize;
            int end = Math.min((page + 1) * safeSize, keyList.size());

            List<String> pageKeys = Collections.emptyList();
            if (start < keyList.size()) {
                pageKeys = keyList.subList(start, end);
            }

            model.addAttribute("reportKeys", pageKeys);
            // 告诉前端 Redis 总共有多少条，以便前端决定是否显示"更多"（虽然目前前端没做Redis翻页）
            model.addAttribute("redisTotal", keyList.size());

            return "admin/battle_history";

        } catch (Exception e) {
            logger.error("查询战斗历史失败", e);
            model.addAttribute("error", "查询失败：" + e.getMessage());
            return "admin/battle_history";
        }
    }

    /**
     * 战斗详情JSON接口（包含完整战斗日志）
     */
    @GetMapping("/battle/{battleId}/detail")
    @ResponseBody
    public String getBattleDetail(@PathVariable String battleId, HttpSession session) {
        // 检查管理员是否登录
        if (!checkLogin(session)) {
            return "{\"error\":\"未登录\"}";
        }

        try {
            // 查询战斗记录
            BattleRecord record = battleRecordRepository.findByBattleId(battleId).orElse(null);

            if (record == null) {
                return "{\"error\":\"战斗记录不存在\"}";
            }

            // 返回完整的战斗记录JSON（使用ObjectMapper序列化）
            return objectMapper.writeValueAsString(record);

        } catch (Exception e) {
            logger.error("查询战斗详情失败，battleId={}", battleId, e);
            return "{\"error\":\"查询失败\"}";
        }
    }

    /**
     * 删除战斗记录（管理员功能）
     */
    @PostMapping("/battle/{battleId}/delete")
    public String deleteBattle(@PathVariable String battleId, HttpSession session) {
        // 检查管理员是否登录
        if (!checkLogin(session)) {
            return "redirect:/admin/login";
        }

        try {
            // 查询战斗记录
            BattleRecord record = battleRecordRepository.findByBattleId(battleId).orElse(null);

            if (record != null) {
                battleRecordRepository.delete(record);
                logger.info("管理员删除战斗记录，battleId={}", battleId);
            }

            return "redirect:/admin/battles";

        } catch (Exception e) {
            logger.error("删除战斗记录失败，battleId={}", battleId, e);
            return "redirect:/admin/battles?error=删除失败";
        }
    }

    /**
     * 查询某个玩家的战斗统计
     */
    @GetMapping("/user/{userId}/battle-stats")
    @ResponseBody
    public String getUserBattleStats(@PathVariable Long userId, HttpSession session) {
        // 检查管理员是否登录
        if (!checkLogin(session)) {
            return "{\"error\":\"未登录\"}";
        }

        try {
            // 统计总战斗数
            long totalBattles = battleRecordRepository.countByPlayer1IdOrPlayer2Id(userId, userId);

            // 统计胜利数
            long winCount = battleRecordRepository.countByWinnerId(userId);

            // 计算胜率
            double winRate = totalBattles > 0 ? (double) winCount / totalBattles * 100 : 0.0;

            // 查询最近5场战斗
            List<BattleRecord> recentBattles = battleRecordRepository
                    .findByPlayer1IdOrPlayer2IdOrderByCreatedAtDesc(userId, userId)
                    .stream()
                    .limit(5)
                    .toList(); // JDK16+的toList()方法

            // 构造返回数据（手动拼接JSON）
            StringBuilder json = new StringBuilder("{");
            json.append("\"totalBattles\":").append(totalBattles).append(",");
            json.append("\"winCount\":").append(winCount).append(",");
            json.append("\"winRate\":\"").append(String.format("%.2f", winRate)).append("\",");
            json.append("\"recentBattlesCount\":").append(recentBattles.size());
            json.append("}");

            return json.toString();

        } catch (Exception e) {
            logger.error("查询玩家战斗统计失败，userId={}", userId, e);
            return "{\"error\":\"查询失败\"}";
        }
    }

    /**
     * 检查是否已登录
     */
    private boolean checkLogin(HttpSession session) {
        return session.getAttribute("admin") != null;
    }

    /**
     * Lua脚本管理页面
     */
    @GetMapping("/lua")
    public String luaManager(Model model) {
        // 获取性能统计数据
        Map<String, LuaPerformanceMonitor.FunctionStats> stats = performanceMonitor.getAllStats();
        model.addAttribute("luaStats", stats);

        // 计算总调用次数
        long totalCalls = performanceMonitor.getTotalCallCount();
        model.addAttribute("totalCalls", totalCalls);

        // 计算平均耗时
        double avgTime = 0.0;
        if (!stats.isEmpty()) {
            double totalTime = 0.0;
            for (LuaPerformanceMonitor.FunctionStats stat : stats.values()) {
                totalTime += stat.getAverageExecutionTime();
            }
            avgTime = totalTime / stats.size();
        }
        model.addAttribute("avgExecutionTime", avgTime);

        return "admin/lua_manager";
    }

    /**
     * 热更新Lua脚本
     */
    @PostMapping("/lua/reload")
    @ResponseBody
    public Map<String, Object> reloadLuaScript() {
        Map<String, Object> result = new HashMap<>();

        try {
            boolean success = damageCalculator.reloadLuaScript();

            if (success) {
                result.put("success", true);
                result.put("message", "Lua脚本热更新成功！");
            } else {
                result.put("success", false);
                result.put("message", "Lua脚本热更新失败，请查看日志");
            }

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "热更新异常: " + e.getMessage());
        }

        return result;
    }

    /**
     * 测试Lua引擎
     */
    @PostMapping("/lua/test")
    @ResponseBody
    public Map<String, Object> testLuaEngine() {
        Map<String, Object> result = new HashMap<>();

        try {
            boolean passed = damageCalculator.testLuaEngine();

            if (passed) {
                result.put("success", true);
                result.put("message", "Lua引擎测试通过！");
            } else {
                result.put("success", false);
                result.put("message", "Lua引擎测试失败，请查看日志");
            }

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "测试异常: " + e.getMessage());
        }

        return result;
    }

    /**
     * 重置性能统计
     */
    @PostMapping("/lua/reset-stats")
    @ResponseBody
    public Map<String, Object> resetLuaStats() {
        Map<String, Object> result = new HashMap<>();

        try {
            performanceMonitor.resetStats();
            result.put("success", true);
            result.put("message", "性能统计已重置");

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "重置失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 获取Lua性能统计（AJAX）
     */
    @GetMapping("/lua/stats")
    @ResponseBody
    public Map<String, Object> getLuaStats() {
        Map<String, Object> result = new HashMap<>();

        try {
            Map<String, LuaPerformanceMonitor.FunctionStats> stats = performanceMonitor.getAllStats();
            long totalCalls = performanceMonitor.getTotalCallCount();

            result.put("success", true);
            result.put("stats", stats);
            result.put("totalCalls", totalCalls);

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "获取统计失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 手动刷新系统缓存
     * 当修改了数据库配置（如技能数值、商品价格）后，调用此接口强制更新内存缓存，无需重启服务器
     */
    @PostMapping("/system/refresh")
    @ResponseBody
    public Map<String, Object> refreshSystemCache(HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        // 鉴权
        if (!checkLogin(session)) {
            result.put("success", false);
            result.put("message", "未登录");
            return result;
        }

        try {
            long start = System.currentTimeMillis();

            // 执行刷新
            skillManager.refresh();
            itemManager.refresh();

            // 顺便重载一下Lua脚本，确保万无一失
            damageCalculator.reloadLuaScript();

            long duration = System.currentTimeMillis() - start;

            result.put("success", true);
            result.put("message", "所有缓存刷新成功！耗时: " + duration + "ms");
            logger.info("管理员手动刷新了系统缓存");

        } catch (Exception e) {
            logger.error("刷新缓存失败", e);
            result.put("success", false);
            result.put("message", "刷新失败: " + e.getMessage());
        }

        return result;
    }
}