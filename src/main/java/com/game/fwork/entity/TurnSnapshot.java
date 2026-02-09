package com.game.fwork.entity;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 回合快照实体
 * 用于断线重连机制
 * 在每回合结束时将战斗状态完整序列化，若服务器宕机或玩家掉线，可据此恢复现场
 */
@Getter @Setter
public class TurnSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    private String battleId;
    private Integer currentRound;
    private Long currentActorUserId;

    // 玩家1的状态
    private Long player1UserId;
    private String player1Nickname;
    private Integer player1MaxHp;
    private Integer player1CurrentHp;
    private Integer player1Attack;
    private Integer player1Defense;
    private boolean player1Alive;
    private Map<Integer, Integer> player1Cooldowns;

    // 玩家2的状态
    private Long player2UserId;
    private String player2Nickname;
    private Integer player2MaxHp;
    private Integer player2CurrentHp;
    private Integer player2Attack;
    private Integer player2Defense;
    private boolean player2Alive;
    private Map<Integer, Integer> player2Cooldowns;

    private LocalDateTime snapshotTime;

    /**
     * 无参构造器（Redis序列化需要）
     */
    public TurnSnapshot() {
        this.snapshotTime = LocalDateTime.now();
    }

    /**
     * 从Battle对象创建快照
     *
     * @param battle 战斗对象
     * @return 快照
     */
    public static TurnSnapshot fromBattle(Battle battle) {
        TurnSnapshot snapshot = new TurnSnapshot();

        snapshot.setBattleId(battle.getBattleId());
        snapshot.setCurrentRound(battle.getCurrentRound());
        snapshot.setCurrentActorUserId(battle.getCurrentActorUserId());

        // 玩家1
        BattlePlayer p1 = battle.getPlayer1();
        snapshot.setPlayer1UserId(p1.getUserId());
        snapshot.setPlayer1Nickname(p1.getNickname());
        snapshot.setPlayer1MaxHp(p1.getMaxHp());
        snapshot.setPlayer1CurrentHp(p1.getCurrentHp());
        snapshot.setPlayer1Attack(p1.getAttack());
        snapshot.setPlayer1Defense(p1.getDefense());
        snapshot.setPlayer1Alive(p1.isAlive());
        snapshot.setPlayer1Cooldowns(p1.getCooldowns());

        // 玩家2
        BattlePlayer p2 = battle.getPlayer2();
        snapshot.setPlayer2UserId(p2.getUserId());
        snapshot.setPlayer2Nickname(p2.getNickname());
        snapshot.setPlayer2MaxHp(p2.getMaxHp());
        snapshot.setPlayer2CurrentHp(p2.getCurrentHp());
        snapshot.setPlayer2Attack(p2.getAttack());
        snapshot.setPlayer2Defense(p2.getDefense());
        snapshot.setPlayer2Alive(p2.isAlive());
        snapshot.setPlayer2Cooldowns(p2.getCooldowns());

        snapshot.setSnapshotTime(LocalDateTime.now());

        return snapshot;
    }
}