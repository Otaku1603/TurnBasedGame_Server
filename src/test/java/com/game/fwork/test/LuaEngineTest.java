package com.game.fwork.test;

import com.game.fwork.entity.BattlePlayer;
import com.game.fwork.entity.Skill; // 使用实体
import com.game.fwork.util.DamageCalculator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class LuaEngineTest {

    @Autowired
    private DamageCalculator damageCalculator;

    @Test
    public void testLuaDamageCalculation() {
        System.out.println("==================== 开始测试Lua引擎 ====================");

        // 1. 创建攻击者 (控制变量：0暴击，0闪避)
        BattlePlayer attacker = new BattlePlayer();
        attacker.setAttack(100);
        attacker.setDefense(50);
        attacker.setMaxHp(500);
        attacker.setCurrentHp(500);
        attacker.setCritRate(0); // 禁止暴击
        attacker.setDodgeRate(0);
        attacker.setNickname("测试攻击者");

        // 2. 创建防御者
        BattlePlayer defender = new BattlePlayer();
        defender.setAttack(80);
        defender.setDefense(60);
        defender.setMaxHp(500);
        defender.setCurrentHp(500);
        defender.setDodgeRate(0); // 禁止闪避
        defender.setCritRate(0);
        defender.setDefending(false);
        defender.setNickname("测试防御者");

        // 3. 模拟数据库中的“普通攻击”技能
        Skill normalAttack = new Skill();
        normalAttack.setId(1);
        normalAttack.setSkillName("普通攻击");
        normalAttack.setSkillType("attack");
        normalAttack.setMultiplier(1.0);
        normalAttack.setDefenseMultiplier(0.5);

        // 期望伤害：100 * 1.0 - 60 * 0.5 = 70
        int dmg1 = damageCalculator.calculateDamage(attacker, defender, normalAttack);
        System.out.println("【普通攻击】实际: " + dmg1 + " (期望: 70)");
        assert dmg1 == 70 : "普通攻击数值错误";

        // 4. 模拟“重击”技能
        Skill heavyStrike = new Skill();
        heavyStrike.setId(2);
        heavyStrike.setSkillName("重击");
        heavyStrike.setSkillType("attack");
        heavyStrike.setMultiplier(1.5);
        heavyStrike.setDefenseMultiplier(0.3);

        // 期望伤害：100 * 1.5 - 60 * 0.3 = 150 - 18 = 132
        int dmg2 = damageCalculator.calculateDamage(attacker, defender, heavyStrike);
        System.out.println("【重击】实际: " + dmg2 + " (期望: 132)");
        assert dmg2 == 132 : "重击数值错误";

        // 5. 模拟“治疗”技能
        Skill healSkill = new Skill();
        healSkill.setId(3);
        healSkill.setSkillName("治疗术");
        healSkill.setSkillType("heal");
        healSkill.setMultiplier(0.3);

        // 期望治疗：500 * 0.3 = 150
        int heal = damageCalculator.calculateHeal(attacker, healSkill);
        System.out.println("【治疗】实际: " + heal + " (期望: 150)");
        assert heal == 150 : "治疗数值错误";

        System.out.println("==================== ✅ Lua引擎测试通过！ ====================");
    }

    @Test
    public void testLuaHotReload() {
        // 这个测试依赖 damageCalculator 内部状态，逻辑保持不变
        // 但需要构造 Skill 实体传参
        System.out.println("==================== 测试Lua脚本热更新 ====================");

        BattlePlayer attacker = new BattlePlayer();
        attacker.setMaxHp(500);

        Skill healSkill = new Skill();
        healSkill.setSkillType("heal");
        healSkill.setMultiplier(0.3);

        int before = damageCalculator.calculateHeal(attacker, healSkill);

        boolean success = damageCalculator.reloadLuaScript();
        assert success;

        int after = damageCalculator.calculateHeal(attacker, healSkill);
        assert before == after;

        System.out.println("==================== ✅ 热更新测试通过！ ====================");
    }

    @Test
    public void testLuaEngineHealth() {
        // 调用 DamageCalculator 内部自带的 self-check
        assert damageCalculator.testLuaEngine();
        System.out.println("==================== ✅ 健康检查通过！ ====================");
    }
}