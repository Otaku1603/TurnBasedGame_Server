package com.game.fwork.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 道具实体
 * 对应 t_item
 */
@Entity
@Getter @Setter
@Table(name = "t_item")
public class Item {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name", nullable = false, length = 32)
    private String name;

    @Column(name = "type", nullable = false, length = 16)
    private String type;  // AVATAR_FRAME/POTION

    @Column(name = "price", nullable = false)
    private Integer price;

    @Column(name = "effect_value")
    private Integer effectValue;

    @Column(name = "icon_path", length = 128)
    private String iconPath;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // 构造函数
    public Item() {
        this.createdAt = LocalDateTime.now();
    }

    public Item(String name, String type, Integer price) {
        this.name = name;
        this.type = type;
        this.price = price;
        this.createdAt = LocalDateTime.now();
    }
}