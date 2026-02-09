package com.game.fwork.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 角色实体类
 * 对应数据库表 t_character
 * 一个用户可以拥有多个角色，但同一时间只能激活（isActive=1）一个作为出战角色
 */
@Entity
@Getter @Setter
@Table(name = "t_character")
public class Character {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "char_name", nullable = false, length = 32)
    private String charName;

    @Column(name = "char_type", nullable = false, length = 16)
    private String charType;

    @Column(nullable = false)
    private Integer level = 1;

    @Column(nullable = false)
    private Integer exp = 0;

    @Column(name = "max_hp", nullable = false)
    private Integer maxHp = 100;

    @Column(name = "current_hp", nullable = false)
    private Integer currentHp = 100;

    @Column(nullable = false)
    private Integer attack = 10;

    @Column(nullable = false)
    private Integer defense = 5;

    @Column(nullable = false)
    private Integer speed = 10;

    @Column(name = "crit_rate", nullable = false)
    private Integer critRate = 5;

    @Column(name = "dodge_rate", nullable = false)
    private Integer dodgeRate = 5;

    @Column(name = "is_active", nullable = false)
    private Integer isActive = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // 业务方法
    public boolean isAlive() {
        return currentHp > 0;
    }

    public void restoreHp() {
        this.currentHp = this.maxHp;
    }

    public void takeDamage(int damage) {
        this.currentHp = Math.max(0, this.currentHp - damage);
    }

    public void heal(int healAmount) {
        this.currentHp = Math.min(this.maxHp, this.currentHp + healAmount);
    }

    public int getHpPercentage() {
        if (maxHp == 0) return 0;
        return (int) ((double) currentHp / maxHp * 100);
    }

    // ================= toString =================

    @Override
    public String toString() {
        return "Character{" +
                "id=" + id +
                ", userId=" + (user != null ? user.getId() : "null") + // 避免懒加载问题
                ", charName='" + charName + '\'' +
                ", charType='" + charType + '\'' +
                ", level=" + level +
                ", currentHp=" + currentHp +
                ", maxHp=" + maxHp +
                '}';
    }
}