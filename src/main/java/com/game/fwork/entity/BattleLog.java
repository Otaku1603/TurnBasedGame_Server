package com.game.fwork.entity;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 战斗日志
 */
@Getter @Setter
public class BattleLog implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer round;              // 发生在第几回合
    private Long actorUserId;           // 行动者ID
    private String actorNickname;       // 行动者昵称（冗余字段，方便显示）
    private String action;              // 操作类型（"ATTACK", "SKILL", "HEAL", "READY"等）
    private String skillName;           // 使用的技能名称（如果是技能）
    private Integer damage;             // 造成的伤害（如果是攻击）
    private Integer heal;               // 恢复的生命（如果是治疗）
    private Long targetUserId;          // 目标玩家ID（攻击/治疗的对象）
    private String targetNickname;      // 目标玩家昵称
    private String description;         // 详细描述（例如："玩家A对玩家B使用重击，造成120点伤害"）
    private LocalDateTime timestamp;    // 操作时间

    /**
     * 无参构造器
     */
    public BattleLog() {
        this.timestamp = LocalDateTime.now();
    }

    /**
     * 快速创建日志的静态方法
     *
     * @param round 回合数
     * @param actor 行动者
     * @param action 操作类型
     * @param description 描述
     * @return 日志对象
     */
    public static BattleLog create(Integer round, BattlePlayer actor,
                                   String action, String description) {
        BattleLog log = new BattleLog();
        log.setRound(round);
        log.setActorUserId(actor.getUserId());
        log.setActorNickname(actor.getNickname());
        log.setAction(action);
        log.setDescription(description);
        return log;
    }
}