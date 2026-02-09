package com.game.fwork.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色职业初始模板实体
 */
@Entity
@Getter @Setter
@Table(name = "t_character_template")
public class CharacterTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "char_type", nullable = false, unique = true)
    private String charType;

    @Column(name = "char_name_prefix", nullable = false)
    private String charNamePrefix;

    @Column(name = "base_max_hp", nullable = false)
    private Integer baseMaxHp;

    @Column(name = "base_attack", nullable = false)
    private Integer baseAttack;

    @Column(name = "base_defense", nullable = false)
    private Integer baseDefense;

    @Column(name = "base_speed", nullable = false)
    private Integer baseSpeed;
}