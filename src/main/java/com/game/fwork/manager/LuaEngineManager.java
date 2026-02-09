package com.game.fwork.manager;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lua 脚本引擎管理器
 * 封装 LuaJ 库，负责 Lua 脚本的加载、编译缓存和执行
 * 提供了 Java Map 到 Lua Table 的上下文转换能力
 */
@Component  // 标记为Spring组件，让Spring管理这个Bean
public class LuaEngineManager {

    // 日志记录器（用于输出运行信息和错误）
    private static final Logger logger = LoggerFactory.getLogger(LuaEngineManager.class);

    private volatile Globals globals;

    private final Map<String, LuaValue> scriptCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            logger.info("=== 开始初始化Lua引擎 ===");

            globals = JsePlatform.standardGlobals();
            logger.info("Lua虚拟机创建成功");

            // 预加载核心公式脚本
            loadScript("lua/damage_formulas.lua");
            logger.info("=== Lua引擎初始化成功 ===");

        } catch (Exception e) {
            logger.error("Lua引擎初始化失败！", e);
            throw new RuntimeException("Lua引擎初始化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 加载并编译 Lua 脚本 (支持缓存)
     */
    private LuaValue loadScript(String scriptPath) throws Exception {
        LuaValue cached = scriptCache.get(scriptPath);
        if (cached != null) {
            logger.debug("从缓存加载Lua脚本: {}", scriptPath);
            return cached;
        }

        logger.info("首次加载Lua脚本: {}", scriptPath);

        ClassPathResource resource = new ClassPathResource(scriptPath);
        if (!resource.exists()) {
            throw new RuntimeException("Lua脚本文件不存在: " + scriptPath);
        }

        InputStream inputStream = resource.getInputStream();
        InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);

        LuaValue chunk = globals.load(reader, scriptPath);
        chunk.call();

        scriptCache.put(scriptPath, chunk);

        logger.info("Lua脚本编译成功: {}", scriptPath);
        return chunk;
    }

    /**
     * 调用 Lua 函数
     * @param context Java传递给Lua的上下文参数
     */
    public int callLuaFunction(String scriptPath, String functionName, Map<String, Object> context)
            throws Exception {

        loadScript(scriptPath);

        LuaValue luaFunction = globals.get(functionName);

        if (luaFunction.isnil()) {
            throw new RuntimeException("Lua函数不存在: " + functionName);
        }

        LuaTable luaContext = new LuaTable();

        for (Map.Entry<String, Object> entry : context.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Integer) {
                luaContext.set(key, LuaValue.valueOf((Integer) value));
            } else if (value instanceof Double) {
                luaContext.set(key, LuaValue.valueOf((Double) value));
            } else if (value instanceof String) {
                luaContext.set(key, LuaValue.valueOf((String) value));
            }
            // 显式处理 Boolean，转为 Lua 的布尔值
            else if (value instanceof Boolean) {
                luaContext.set(key, (Boolean) value ? LuaValue.TRUE : LuaValue.FALSE);
            }
            else {
                luaContext.set(key, LuaValue.valueOf(value.toString()));
            }
        }

        LuaValue result = luaFunction.call(luaContext);

        int intResult = result.toint();

        logger.debug("Lua函数调用成功: {}() = {}", functionName, intResult);
        return intResult;
    }

    /**
     * 重新加载脚本（用于热更新）
     * @param scriptPath 脚本路径
     */
    public void reloadScript(String scriptPath) {
        try {
            logger.info("热更新Lua脚本: {}", scriptPath);

            // 从缓存中移除旧脚本
            scriptCache.remove(scriptPath);

            // 重新加载脚本
            loadScript(scriptPath);

            logger.info("Lua脚本热更新成功: {}", scriptPath);

        } catch (Exception e) {
            logger.error("Lua脚本热更新失败: {}", scriptPath, e);
            throw new RuntimeException("Lua脚本热更新失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取Lua虚拟机对象（高级用法）
     * @return Globals对象
     */
    public Globals getGlobals() {
        return globals;
    }

    /**
     * 检查引擎是否已初始化
     */
    public boolean isInitialized() {
        return globals != null;
    }
}