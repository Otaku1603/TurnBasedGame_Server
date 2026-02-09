package com.game.fwork.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;

/**
 * 技能实体 - 对应 t_skill 表
 */
@Entity
@Getter @Setter
@Table(name = "t_skill")
public class Skill {
    @Id
    private Integer id;

    @Column(name = "skill_name")
    private String skillName;

    @Column(name = "skill_type")
    private String skillType; // attack, heal

    @Column(name = "target_type")
    private String targetType; // ENEMY, SELF

    private Integer cooldown;

    private Double multiplier; // 伤害/治疗倍率

    @Column(name = "defense_multiplier")
    private Double defenseMultiplier; // 防御系数

    @Column(name = "mana_cost")
    private Integer manaCost;

    private String description;
}