package com.game.fwork.entity;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 战斗玩家包装类
 * 是 User（账号）和 Character（数值）的聚合体
 * 专门用于战斗计算，将数据库中的静态属性转换为战斗中的动态属性（如实时血量、Buff状态）
 */
@Getter @Setter
public class BattlePlayer implements Serializable {

    private static final long serialVersionUID = 1L;

    // 玩家基础信息
    private Long userId;
    private String nickname;
    private Long characterId;

    // 战斗属性
    private Integer maxHp;
    private Integer currentHp;
    private Integer attack;
    private Integer defense;
    private Integer speed;
    private Integer critRate; // 暴击率 %
    private Integer dodgeRate; // 闪避率 %

    // 战斗状态
    private boolean isAlive;
    private boolean isReady;
    private boolean isDefending; // 本回合是否防御

    // 技能冷却管理
    /**
     * 技能冷却Map
     */
    private Map<Integer, Integer> cooldowns;

    /**
     * 无参构造器（Redis反序列化需要）
     */
    public BattlePlayer() {
        this.cooldowns = new HashMap<>();
        this.isAlive = true;
        this.isReady = false;
        this.isDefending = false;
    }

    /**
     * 完整构造器（从Character和User对象创建BattlePlayer）
     *
     * @param character 玩家的角色对象
     * @param user 玩家的用户对象
     */
    public BattlePlayer(Character character, User user) {
        // 从User获取用户信息（避免懒加载）
        this.userId = user.getId();
        this.nickname = user.getNickname();
        this.characterId = character.getId();

        // 复制角色属性
        this.maxHp = character.getMaxHp();
        this.currentHp = character.getCurrentHp();
        this.attack = character.getAttack();
        this.defense = character.getDefense();
        this.speed = character.getSpeed();
        this.critRate = character.getCritRate();
        this.dodgeRate = character.getDodgeRate();

        // 初始化状态
        this.isAlive = true;
        this.isReady = false;
        this.isDefending = false;
        this.cooldowns = new HashMap<>();
    }

    // 战斗逻辑方法

    /**
     * 受到伤害
     *
     * @param damage 伤害值（必须为正数）
     */
    public void takeDamage(int damage) {
        this.currentHp = Math.max(0, this.currentHp - damage);
        if (this.currentHp == 0) {
            this.isAlive = false;
        }
    }

    /**
     * 恢复生命值
     *
     * @param healAmount 恢复量
     */
    public void heal(int healAmount) {
        this.currentHp = Math.min(this.maxHp, this.currentHp + healAmount);
    }

    /**
     * 判断技能是否可以使用
     *
     * @param skillId 技能ID
     * @return true=可以使用
     */
    public boolean canUseSkill(Integer skillId) {
        Integer remaining = cooldowns.get(skillId);
        // 如果Map里没有记录，或者记录为0，都可以使用
        return remaining == null || remaining == 0;
    }

    /**
     * 使用技能（触发冷却）
     */
    public void useSkill(Integer skillId, Integer skillCooldown) {
        if (skillCooldown > 0) {
            cooldowns.put(skillId, skillCooldown);
        }
    }

    /**
     * 回合结束时，减少所有技能的冷却回合数
     * 每个技能的剩余冷却 -1，最小为0
     */
    public void reduceCooldowns() {
        for (Integer skillId : cooldowns.keySet()) {
            int remaining = cooldowns.get(skillId);
            if (remaining > 0) {
                cooldowns.put(skillId, remaining - 1);
            }
        }
    }

    /**
     * 获取当前血量百分比（用于UI显示）
     *
     * @return 血量百分比（0.0 ~ 1.0）
     */
    public double getHpPercentage() {
        if (maxHp == 0) return 0.0;
        return (double) currentHp / maxHp;
    }

    /**
     * 初始化技能列表
     * 将查到的技能ID放入冷却Map，初始冷却为0
     */
    public void initSkills(List<Integer> skillIds) {
        if (this.cooldowns == null) {
            this.cooldowns = new HashMap<>();
        }
        if (skillIds != null) {
            for (Integer skillId : skillIds) {
                // Key=技能ID, Value=0 (表示冷却就绪)
                this.cooldowns.put(skillId, 0);
            }
        }
    }

    /**
     * 状态重置（每回合开始/结束时调用）
     */
    public void resetTurnState() {
        this.isDefending = false; // 回合结束，防御失效
    }
}