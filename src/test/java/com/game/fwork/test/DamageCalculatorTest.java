package com.game.fwork.test;

import com.game.fwork.entity.BattlePlayer;
import com.game.fwork.entity.Skill;
import com.game.fwork.manager.LuaEngineManager;
import com.game.fwork.manager.LuaPerformanceMonitor;
import com.game.fwork.util.DamageCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("伤害计算组件单元测试")
class DamageCalculatorTest {

    @InjectMocks
    private DamageCalculator damageCalculator;

    @Mock
    private LuaEngineManager luaEngineManager;

    @Mock
    private LuaPerformanceMonitor performanceMonitor;

    private BattlePlayer attacker;
    private BattlePlayer defender;
    private Skill skill;

    @BeforeEach
    void setUp() {
        // 1. 初始化攻击者
        attacker = new BattlePlayer();
        attacker.setUserId(1001L);
        attacker.setNickname("勇者");
        attacker.setAttack(100);
        attacker.setCritRate(0); // 暂不暴击

        // 2. 初始化防御者
        defender = new BattlePlayer();
        defender.setUserId(1002L);
        defender.setNickname("魔王");
        defender.setDefense(50);
        defender.setDodgeRate(0); // 暂不闪避
        defender.setDefending(false);

        // 3. 初始化技能
        skill = new Skill();
        skill.setId(1);
        skill.setSkillName("重斩");
        skill.setSkillType("attack");
        skill.setMultiplier(1.5); // 1.5倍伤害
        skill.setDefenseMultiplier(1.0); // 100% 防御计算

        // 4. 注入私有字段 luaScriptPath (模拟 @Value)
        ReflectionTestUtils.setField(damageCalculator, "luaScriptPath", "lua/damage_formulas.lua");
    }

    @Test
    @DisplayName("测试 Lua 引擎正常计算伤害")
    void testCalculateDamage_LuaSuccess() throws Exception {
        // Arrange (准备)
        // 模拟 Lua 引擎返回 100 点伤害 (逻辑：(100 * 1.5) - 50 = 100)
        when(luaEngineManager.callLuaFunction(anyString(), eq("calculateDamage"), any(Map.class)))
                .thenReturn(100);

        // Act (执行)
        int damage = damageCalculator.calculateDamage(attacker, defender, skill);

        // Assert (断言)
        assertEquals(100, damage, "Lua计算结果应为100");
    }

    @Test
    @DisplayName("测试 Lua 引擎故障时的 Java 降级策略")
    void testCalculateDamage_JavaFallback() throws Exception {
        // Arrange (准备)
        // 模拟 Lua 引擎抛出异常
        when(luaEngineManager.callLuaFunction(anyString(), eq("calculateDamage"), any(Map.class)))
                .thenThrow(new RuntimeException("Lua脚本丢失"));

        // 执行 Java 兜底公式：(Attack * Multiplier) - (Defense * DefenseMultiplier)
        // (100 * 1.5) - (50 * 1.0) = 150 - 50 = 100

        // Act (执行)
        int damage = damageCalculator.calculateDamage(attacker, defender, skill);

        // Assert (断言)
        assertEquals(100, damage, "Lua失败时应执行Java兜底逻辑");
    }
}