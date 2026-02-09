package com.game.fwork.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 好友关系实体
 * 对应数据库表 t_friend
 * 采用双向存储策略（A是B的好友，则数据库会有 A->B 和 B->A 两条记录），方便查询
 */
@Entity
@Getter @Setter
@Table(name = "t_friend", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "friend_id"})
})
public class Friend {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "friend_id", nullable = false)
    private User friend;

    @Column(name = "status")
    private Integer status = 1;  // 1=正常, 0=拉黑

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // 构造函数
    public Friend() {
        this.createdAt = LocalDateTime.now();
    }

    public Friend(User user, User friend) {
        this.user = user;
        this.friend = friend;
        this.createdAt = LocalDateTime.now();
    }
}