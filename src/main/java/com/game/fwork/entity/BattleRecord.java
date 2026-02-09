package com.game.fwork.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 战斗记录归档实体
 * 对应数据库表 t_battle_record
 * 当战斗结束后，Battle 对象会被销毁，关键数据（结果、战报、变动分）转移到此处持久化保存
 */
@Entity
@Getter @Setter
@Table(name = "t_battle_record")
public class BattleRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 业务唯一ID，关联 Redis Key */
    @Column(name = "battle_id", length = 100, nullable = false, unique = true)
    private String battleId;

    // ==================== 玩家1基本信息 ====================

    /**
     * 玩家1的用户ID
     * 对应：t_user.id
     */
    @Column(name = "player1_id", nullable = false)
    private Long player1Id;

    /**
     * 玩家1昵称（冗余字段）
     */
    @Column(name = "player1_nickname", length = 50)
    private String player1Nickname;

    /**
     * 玩家1使用的角色ID
     * 对应：t_character.id
     */
    @Column(name = "player1_char_id")
    private Long player1CharId;

    // ==================== 玩家2基本信息 ====================

    /**
     * 玩家2的用户ID
     * 对应：t_user.id
     */
    @Column(name = "player2_id", nullable = false)
    private Long player2Id;

    /**
     * 玩家2昵称（冗余字段）
     */
    @Column(name = "player2_nickname", length = 50)
    private String player2Nickname;

    /**
     * 玩家2使用的角色ID
     * 对应：t_character.id
     */
    @Column(name = "player2_char_id")
    private Long player2CharId;

    // ==================== 战斗结果 ====================

    /**
     * 获胜者的用户ID
     * 对应：t_user.id
     */
    @Column(name = "winner_id")
    private Long winnerId;

    /**
     * 战斗结束原因
     */
    @Column(name = "end_reason", length = 50)
    private String endReason;

    // ==================== 战斗统计数据 ====================

    /**
     * 总回合数
     */
    @Column(name = "total_rounds")
    private Integer totalRounds;

    /**
     * 战斗总时长（单位：秒）
     */
    @Column(name = "battle_duration")
    private Integer battleDuration;

    // ==================== 玩家1战斗数据 ====================

    /**
     * 玩家1战斗结束时的剩余血量
     */
    @Column(name = "player1_final_hp")
    private Integer player1FinalHp;

    /**
     * 玩家1战前ELO积分
     */
    @Column(name = "player1_elo_before")
    private Integer player1EloBefore;

    /**
     * 玩家1战后ELO积分
     */
    @Column(name = "player1_elo_after")
    private Integer player1EloAfter;

    // ==================== 玩家2战斗数据 ====================

    /**
     * 玩家2战斗结束时的剩余血量
     */
    @Column(name = "player2_final_hp")
    private Integer player2FinalHp;

    /**
     * 玩家2战前ELO积分
     */
    @Column(name = "player2_elo_before")
    private Integer player2EloBefore;

    /**
     * 玩家2战后ELO积分
     */
    @Column(name = "player2_elo_after")
    private Integer player2EloAfter;

    // ==================== 战斗详细日志 ====================

    /** 战斗完整日志 (JSON格式) */
    @Column(name = "battle_log_json", columnDefinition = "TEXT")
    private String battleLogJson;

    // ==================== 时间字段 ====================

    /**
     * 战斗开始时间
     */
    @Column(name = "start_time")
    private LocalDateTime startTime;

    /**
     * 战斗结束时间
     */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    /**
     * 记录创建时间（数据插入MySQL的时间）
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // ==================== 构造方法 ====================

    /**
     * 无参构造方法（JPA要求）
     */
    public BattleRecord() {
    }

    /**
     * 全参构造方法（方便创建对象）
     */
    public BattleRecord(String battleId, Long player1Id, String player1Nickname, Long player1CharId,
                        Long player2Id, String player2Nickname, Long player2CharId,
                        Long winnerId, String endReason, Integer totalRounds, Integer battleDuration,
                        Integer player1FinalHp, Integer player1EloBefore, Integer player1EloAfter,
                        Integer player2FinalHp, Integer player2EloBefore, Integer player2EloAfter,
                        String battleLogJson, LocalDateTime startTime, LocalDateTime endTime) {
        this.battleId = battleId;
        this.player1Id = player1Id;
        this.player1Nickname = player1Nickname;
        this.player1CharId = player1CharId;
        this.player2Id = player2Id;
        this.player2Nickname = player2Nickname;
        this.player2CharId = player2CharId;
        this.winnerId = winnerId;
        this.endReason = endReason;
        this.totalRounds = totalRounds;
        this.battleDuration = battleDuration;
        this.player1FinalHp = player1FinalHp;
        this.player1EloBefore = player1EloBefore;
        this.player1EloAfter = player1EloAfter;
        this.player2FinalHp = player2FinalHp;
        this.player2EloBefore = player2EloBefore;
        this.player2EloAfter = player2EloAfter;
        this.battleLogJson = battleLogJson;
        this.startTime = startTime;
        this.endTime = endTime;
        this.createdAt = LocalDateTime.now(); // 自动设置创建时间
    }

    // ==================== toString ====================
    // 方便调试和日志输出

    @Override
    public String toString() {
        return "BattleRecord{" +
                "id=" + id +
                ", battleId='" + battleId + '\'' +
                ", player1Id=" + player1Id +
                ", player1Nickname='" + player1Nickname + '\'' +
                ", player2Id=" + player2Id +
                ", player2Nickname='" + player2Nickname + '\'' +
                ", winnerId=" + winnerId +
                ", endReason='" + endReason + '\'' +
                ", totalRounds=" + totalRounds +
                ", battleDuration=" + battleDuration + "秒" +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", createdAt=" + createdAt +
                '}';
    }
}