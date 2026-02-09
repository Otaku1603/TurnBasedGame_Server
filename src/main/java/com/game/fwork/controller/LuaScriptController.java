package com.game.fwork.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Lua 脚本管理接口（管理员专用）
 * 允许在后台在线查看和编辑战斗公式脚本，支持热更新
 */
@Controller
@RequestMapping("/admin/lua")
public class LuaScriptController {

    private static final Logger logger = LoggerFactory.getLogger(LuaScriptController.class);

    // Lua脚本文件路径（相对于resources）
    private static final String LUA_SCRIPT_PATH = "lua/damage_formulas.lua";

    /**
     * 读取Lua脚本内容
     */
    @GetMapping("/script/content")
    @ResponseBody
    public Map<String, Object> getScriptContent() {
        Map<String, Object> result = new HashMap<>();

        try {
            // 获取脚本文件的真实路径
            ClassPathResource resource = new ClassPathResource(LUA_SCRIPT_PATH);

            if (!resource.exists()) {
                result.put("success", false);
                result.put("message", "Lua脚本文件不存在");
                return result;
            }

            // 读取文件内容
            InputStream inputStream = resource.getInputStream();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8)
            );

            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();

            result.put("success", true);
            result.put("content", content.toString());
            result.put("path", LUA_SCRIPT_PATH);

            logger.info("读取Lua脚本成功: {}", LUA_SCRIPT_PATH);

        } catch (Exception e) {
            logger.error("读取Lua脚本失败", e);
            result.put("success", false);
            result.put("message", "读取失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 保存并热更 Lua 脚本
     * 1. 自动备份旧脚本文件（防止写错导致系统崩溃）
     * 2. 覆盖写入新内容
     * 3. 触发 Lua 引擎重新加载
     */
    @PostMapping("/script/save")
    @ResponseBody
    public Map<String, Object> saveScriptContent(@RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();

        try {
            String content = request.get("content");

            if (content == null || content.trim().isEmpty()) {
                result.put("success", false);
                result.put("message", "脚本内容不能为空");
                return result;
            }

            // 获取源文件路径
            // 注意：这里需要写入到src/main/resources，而不是target
            String projectRoot = System.getProperty("user.dir");
            Path scriptPath = Paths.get(projectRoot, "src", "main", "resources", "lua", "damage_formulas.lua");

            // 检查文件是否存在
            if (!Files.exists(scriptPath)) {
                result.put("success", false);
                result.put("message", "Lua脚本文件不存在: " + scriptPath);
                return result;
            }

            // 备份当前脚本
            String backupPath = backupScript(scriptPath);

            // 保存新内容
            Files.writeString(
                    scriptPath,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING
            );

            result.put("success", true);
            result.put("message", "Lua脚本保存成功！");
            result.put("backupPath", backupPath);

            logger.info("Lua脚本保存成功: {}, 备份: {}", scriptPath, backupPath);

        } catch (Exception e) {
            logger.error("保存Lua脚本失败", e);
            result.put("success", false);
            result.put("message", "保存失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 备份Lua脚本
     *
     * @param scriptPath 脚本文件路径
     * @return 备份文件路径
     * @throws IOException 备份失败时抛出
     */
    private String backupScript(Path scriptPath) throws IOException {
        // 生成备份文件名（带时间戳）
        String timestamp = String.valueOf(System.currentTimeMillis());
        Path backupPath = Paths.get(
                scriptPath.getParent().toString(),
                "damage_formulas_backup_" + timestamp + ".lua"
        );

        Files.copy(scriptPath, backupPath);

        logger.info("Lua脚本已备份: {}", backupPath);
        return backupPath.toString();
    }
}