package com.game.fwork.entity;

import com.game.fwork.enums.BattleState;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 战斗运行时对象（非数据库实体）
 * 仅存储在内存和 Redis 中，用于管理正在进行中的战斗状态
 * 包含双方即时的血量、技能冷却、当前回合归属等动态数据
 */
@Getter @Setter
public class Battle implements Serializable {

    private static final long serialVersionUID = 1L;

    // ========== 战斗基础信息 ==========
    private String battleId;
    private BattleState state;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    // ========== 双方玩家 ==========
    private BattlePlayer player1;
    private BattlePlayer player2;

    // ========== 回合信息 ==========
    private Integer currentRound;
    private Long currentActorUserId;

    // 最后操作时间（用于挂机检测）
    private LocalDateTime lastActionTime;

    // ========== 战斗日志 ==========
    private List<BattleLog> battleLogs;

    // ========== 战斗结果 ==========
    private Long winnerId;
    private String endReason;

    /**
     * 无参构造器（Redis序列化需要）
     */
    public Battle() {
        this.battleLogs = new ArrayList<>();
        this.currentRound = 1;
        this.state = BattleState.WAITING;
        this.lastActionTime = LocalDateTime.now();
    }

    /**
     * 完整构造器（创建新战斗时使用）
     *
     * @param battleId 战斗ID
     * @param player1 玩家1
     * @param player2 玩家2
     */
    public Battle(String battleId, BattlePlayer player1, BattlePlayer player2) {
        this.battleId = battleId;
        this.player1 = player1;
        this.player2 = player2;
        this.state = BattleState.WAITING;
        this.startTime = LocalDateTime.now();
        this.currentRound = 1;
        this.battleLogs = new ArrayList<>();
        this.lastActionTime = LocalDateTime.now();

        // 默认player1先手
        this.currentActorUserId = player1.getUserId();
    }

    // ========== 战斗逻辑方法 ==========

    /**
     * 判断是否轮到指定玩家行动
     *
     * @param userId 玩家ID
     * @return true=轮到该玩家，false=不是该玩家的回合
     */
    public boolean isPlayerTurn(Long userId) {
        return userId.equals(currentActorUserId);
    }

    /**
     * 切换行动者（轮流行动）
     * 调用此方法后，行动权从player1切换到player2，或从player2切换到player1
     */
    public void switchActor() {
        if (currentActorUserId.equals(player1.getUserId())) {
            currentActorUserId = player2.getUserId();
        } else {
            currentActorUserId = player1.getUserId();
            // 如果切回player1，说明一个完整回合结束，回合数+1
            currentRound++;
        }
        // 切换行动者时更新最后操作时间
        this.lastActionTime = LocalDateTime.now();
    }

    /**
     * 根据userId获取对应的BattlePlayer对象
     *
     * @param userId 玩家ID
     * @return 对应的BattlePlayer，找不到返回null
     */
    public BattlePlayer getPlayerByUserId(Long userId) {
        if (player1.getUserId().equals(userId)) {
            return player1;
        } else if (player2.getUserId().equals(userId)) {
            return player2;
        }
        return null;
    }

    /**
     * 获取对手
     *
     * @param userId 当前玩家ID
     * @return 对手的BattlePlayer对象
     */
    public BattlePlayer getOpponent(Long userId) {
        if (player1.getUserId().equals(userId)) {
            return player2;
        } else if (player2.getUserId().equals(userId)) {
            return player1;
        }
        return null;
    }

    /**
     * 添加战斗日志
     *
     * @param log 日志对象
     */
    public void addLog(BattleLog log) {
        this.battleLogs.add(log);
    }

    /**
     * 检查战斗是否应该结束
     * 当任意一方死亡时，战斗结束
     *
     * @return true=战斗应该结束，false=继续战斗
     */
    public boolean shouldEnd() {
        return !player1.isAlive() || !player2.isAlive();
    }

    /**
     * 结束战斗
     * 设置胜利者和结束原因
     *
     * @param winnerId 胜利者ID
     * @param reason 结束原因
     */
    public void endBattle(Long winnerId, String reason) {
        this.state = BattleState.FINISHED;
        this.winnerId = winnerId;
        this.endReason = reason;
        this.endTime = LocalDateTime.now();
    }

    /**
     * 更新最后操作时间
     * 在玩家执行操作时调用
     */
    public void updateLastActionTime() {
        this.lastActionTime = LocalDateTime.now();
    }

    /**
     * 获取距离最后操作的秒数
     * 用于挂机检测
     *
     * @return 距离最后操作的秒数
     */
    public long getSecondsSinceLastAction() {
        if (lastActionTime == null) {
            return 0;
        }
        return java.time.Duration.between(lastActionTime, LocalDateTime.now()).getSeconds();
    }
}