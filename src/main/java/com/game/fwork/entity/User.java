package com.game.fwork.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 用户实体类
 * 对应数据库表 t_user，存储账号核心信息
 * 包含密码（加密存储）、ELO分数、金币等高频变动数据
 */
@Entity
@Getter @Setter
@Table(name = "t_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String username;

    @Column(nullable = false, length = 128)
    private String password;

    @Column(nullable = false, length = 32)
    private String nickname;

    @Column(length = 64)
    private String email;

    @Column(name = "elo_rating")
    private Integer eloRating = 1000;

    @Column(name = "total_battles")
    private Integer totalBattles = 0;

    @Column(name = "win_count")
    private Integer winCount = 0;

    @Column(name = "gold")
    private Integer gold = 1000;

    @Column(name = "avatar_frame_id")
    private Integer avatarFrameId = 0;

    @Column(nullable = false)
    private Integer status = 1;

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

    // 业务方法：计算胜率
    public double getWinRate() {
        if (totalBattles == null || totalBattles == 0) {
            return 0.0;
        }
        // 防止空指针
        int wins = winCount == null ? 0 : winCount;
        return (double) wins / totalBattles * 100;
    }

    public boolean isBanned() {
        return status != null && status == 0;
    }

    // ================= toString =================

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", nickname='" + nickname + '\'' +
                ", eloRating=" + eloRating +
                ", status=" + status +
                '}'; // 输出不包含密码
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}