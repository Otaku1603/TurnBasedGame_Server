package com.game.fwork.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户背包实体类
 * 对应数据库表 t_user_inventory
 */
@Entity
@Getter @Setter
@Table(name = "t_user_inventory")
public class UserInventory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "count")
    private Integer count = 1;

    @Column(name = "is_equipped")
    private Boolean isEquipped = false;

    // 构造函数
    public UserInventory() {
    }

    public UserInventory(User user, Item item) {
        this.user = user;
        this.item = item;
    }

    public UserInventory(User user, Item item, Integer count) {
        this.user = user;
        this.item = item;
        this.count = count;
    }

    public void setIsEquipped(Boolean equipped) { this.isEquipped = equipped; }
}