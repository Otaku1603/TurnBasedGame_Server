package com.game.fwork.util;

import com.game.fwork.entity.BattlePlayer;
import com.game.fwork.entity.Skill;
import com.game.fwork.manager.LuaEngineManager;
import com.game.fwork.manager.LuaPerformanceMonitor;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 伤害计算器组件
 * 采用“Lua 脚本优先 + Java 兜底”的混合策略
 * 允许在不重启服务器的情况下，通过热更新 Lua 脚本动态调整战斗公式（如伤害倍率、暴击算法）
 */
@Component
public class DamageCalculator {

    private static final Logger logger = LoggerFactory.getLogger(DamageCalculator.class);

    @Autowired
    private LuaEngineManager luaEngineManager;

    @Autowired
    private LuaPerformanceMonitor performanceMonitor;

    @Value("${battle.lua-script-path:lua/damage_formulas.lua}")
    private String luaScriptPath;

    /**
     * 初始化检查
     */
    @PostConstruct
    public void init() {
        // 验证路径是否非空
        if (luaScriptPath == null || luaScriptPath.trim().isEmpty()) {
            luaScriptPath = "lua/damage_formulas.lua"; // 兜底默认值
        }
        logger.info("发现Lua脚本，路径: {}", luaScriptPath);
    }

    /**
     * 计算伤害（主入口）
     * 尝试调用 Lua 脚本计算；如果脚本执行出错，自动降级为 Java 硬编码的计算逻辑，保障系统稳定性
     *
     * @param attacker 攻击者
     * @param defender 防御者
     * @param skill 使用的技能实体
     * @return 最终伤害值
     */
    public int calculateDamage(BattlePlayer attacker, BattlePlayer defender, Skill skill) {
        if ("heal".equals(skill.getSkillType())) return 0;

        try {
            return calculateDamageByLua(attacker, defender, skill);
        } catch (Exception e) {
            logger.error("Lua伤害计算失败，降级到Java实现。攻击者={}, 防御者={}, 技能={}",
                    attacker.getNickname(), defender.getNickname(), skill.getSkillName(), e);
            return calculateDamageByJava(attacker, defender, skill);
        }
    }

    /**
     * 使用Lua脚本计算伤害
     */
    private int calculateDamageByLua(BattlePlayer attacker, BattlePlayer defender, Skill skill) throws Exception {
        // 记录开始时间
        long startTime = System.currentTimeMillis();

        try {
            Map<String, Object> context = new HashMap<>();
            context.put("attackerAttack", attacker.getAttack());
            context.put("defenderDefense", defender.getDefense());
            context.put("attackerCrit", attacker.getCritRate()); // 暴击率
            context.put("defenderDodge", defender.getDodgeRate()); // 闪避率
            context.put("defenderDefending", defender.isDefending()); // 是否防御

            context.put("skillMultiplier", skill.getMultiplier());
            context.put("skillDefBreak", skill.getDefenseMultiplier());

            int damage = luaEngineManager.callLuaFunction(
                    this.luaScriptPath,
                    "calculateDamage",
                    context
            );

            logger.debug("Lua伤害计算: 攻击者[{}]攻击力={}, 防御者[{}]防御力={}, 技能={}, 伤害={}",
                    attacker.getNickname(), attacker.getAttack(),
                    defender.getNickname(), defender.getDefense(),
                    skill.getSkillName(), damage);

            return damage;

        } finally {
            // 记录性能数据
            performanceMonitor.recordCall("calculateDamage", System.currentTimeMillis() - startTime);
        }
    }

    /**
     * Java 版本的伤害计算（兜底方案）
     * 仅包含最基础的减法公式，用于在 Lua 引擎崩溃时的紧急备用
     */
    private int calculateDamageByJava(BattlePlayer attacker, BattlePlayer defender, Skill skill) {
        // 简单模拟：不做随机计算，只做基础运算保底
        double dmg = attacker.getAttack() * skill.getMultiplier();
        double def = defender.getDefense() * skill.getDefenseMultiplier();
        double finalDmg = dmg - def;
        if (defender.isDefending()) {
            finalDmg *= 0.5;
        }
        return Math.max(1, (int) finalDmg);
    }

    /**
     * 计算治疗量（主入口）
     *
     * @param healer 治疗者
     * @param skill 使用的技能实体
     * @return 恢复生命值
     */
    public int calculateHeal(BattlePlayer healer, Skill skill) {
        if (!"heal".equals(skill.getSkillType())) return 0;

        try {
            return calculateHealByLua(healer, skill);
        } catch (Exception e) {
            logger.error("Lua治疗计算失败，降级到Java实现。治疗者={}, 技能={}",
                    healer.getNickname(), skill.getSkillName(), e);
            return calculateHealByJava(healer, skill);
        }
    }

    /**
     * 使用Lua脚本计算治疗量
     */
    private int calculateHealByLua(BattlePlayer healer, Skill skill) throws Exception {

        // 记录开始时间
        long startTime = System.currentTimeMillis();

        try {
            Map<String, Object> context = new HashMap<>();
            context.put("healerMaxHp", healer.getMaxHp());
            context.put("skillMultiplier", skill.getMultiplier());

            int heal = luaEngineManager.callLuaFunction(
                    this.luaScriptPath,
                    "calculateHeal",
                    context
            );

            logger.debug("Lua治疗计算: 治疗者[{}]最大HP={}, 技能={}, 治疗量={}",
                    healer.getNickname(), healer.getMaxHp(),
                    skill.getSkillName(), heal);

            return heal;

        } finally {
            // 记录性能数据
            performanceMonitor.recordCall("calculateHeal", System.currentTimeMillis() - startTime);
        }
    }

    /**
     * 使用Java计算治疗量（兜底方案）
     */
    private int calculateHealByJava(BattlePlayer healer, Skill skill) {
        double healAmount = healer.getMaxHp() * skill.getMultiplier();
        return Math.max(1, (int) Math.round(healAmount));
    }

    /**
     * 热更新Lua脚本
     */
    public boolean reloadLuaScript() {
        try {
            logger.info("开始热更新Lua脚本: {}", this.luaScriptPath);
            luaEngineManager.reloadScript(this.luaScriptPath);
            logger.info("Lua脚本热更新成功！");
            return true;
        } catch (Exception e) {
            logger.error("Lua脚本热更新失败", e);
            return false;
        }
    }

    /**
     * 测试Lua引擎是否正常工作
     */
    public boolean testLuaEngine() {
        try {
            logger.info("=== 开始执行 Lua 引擎自检 ===");

            // 1. 构造测试对象 (Mock)
            // 攻击者：100攻，0暴击 (为了结果确定性，测试时设为0)
            BattlePlayer attacker = new BattlePlayer();
            attacker.setUserId(1L);
            attacker.setNickname("测试木桩A");
            attacker.setAttack(100);
            attacker.setDefense(10);
            attacker.setMaxHp(1000);
            attacker.setCurrentHp(1000);
            attacker.setCritRate(0); // 确保不暴击
            attacker.setDodgeRate(0);

            // 防御者：50防，0闪避，无防御状态
            BattlePlayer defender = new BattlePlayer();
            defender.setUserId(2L);
            defender.setNickname("测试木桩B");
            defender.setAttack(80);
            defender.setDefense(50);
            defender.setMaxHp(1000);
            defender.setCurrentHp(1000);
            defender.setDodgeRate(0); // 确保不闪避
            defender.setCritRate(0);
            defender.setDefending(false);

            // 2. 构造测试技能 (模拟数据库查出来的对象)
            // 攻击技能：倍率1.0，防御穿透0.5
            Skill attackSkill = new Skill();
            attackSkill.setId(999);
            attackSkill.setSkillName("测试攻击");
            attackSkill.setSkillType("attack");
            attackSkill.setMultiplier(1.0);
            attackSkill.setDefenseMultiplier(0.5);

            // 3. 执行伤害计算
            // 预期结果：(100 * 1.0) - (50 * 0.5) = 100 - 25 = 75
            int damage = calculateDamage(attacker, defender, attackSkill);

            logger.info("伤害测试结果: 预期=75, 实际={}", damage);

            // 4. 构造治疗技能
            // 治疗技能：倍率0.2 (20%)
            Skill healSkill = new Skill();
            healSkill.setId(998);
            healSkill.setSkillName("测试治疗");
            healSkill.setSkillType("heal");
            healSkill.setMultiplier(0.2);

            // 5. 执行治疗计算
            // 预期结果：1000 * 0.2 = 200
            int heal = calculateHeal(attacker, healSkill);

            logger.info("治疗测试结果: 预期=200, 实际={}", heal);

            // 6. 验证结果
            // 允许有1点的浮点数误差
            boolean damageOk = Math.abs(damage - 75) <= 1;
            boolean healOk = Math.abs(heal - 200) <= 1;

            if (damageOk && healOk) {
                logger.info("✅ Lua 引擎自检通过！");
                return true;
            } else {
                logger.error("❌ Lua 引擎自检失败！数值不匹配");
                return false;
            }

        } catch (Exception e) {
            logger.error("❌ Lua 引擎自检抛出异常", e);
            return false;
        }
    }
}