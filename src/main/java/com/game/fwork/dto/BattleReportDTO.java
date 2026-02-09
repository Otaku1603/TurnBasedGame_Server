package com.game.fwork.dto;

import com.game.fwork.entity.BattleLog;
import com.game.fwork.entity.BattlePlayer;
import com.game.fwork.enums.BattleState;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 战报数据传输对象
 * 用于将复杂的 Battle/BattleRecord 实体转换为前端易读的 JSON 格式
 * 剔除了服务端专用的逻辑字段，仅保留展示所需的数据（如血量变化、操作日志）
 */
@Getter @Setter
public class BattleReportDTO {

    private String battleId;
    private BattleState state;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private BattlePlayerDTO player1;
    private BattlePlayerDTO player2;

    private Integer currentRound;
    private Long currentActorUserId;

    private List<BattleLogDTO> logs;

    private Long winnerId;
    private String endReason;

    /**
     * 战斗中玩家的简化信息
     */
    @Getter @Setter
    public static class BattlePlayerDTO {
        private Long userId;
        private String nickname;
        private Integer maxHp;
        private Integer currentHp;
        private Integer attack;
        private Integer defense;
        private boolean isAlive;

        public static BattlePlayerDTO fromBattlePlayer(BattlePlayer player) {
            BattlePlayerDTO dto = new BattlePlayerDTO();
            dto.setUserId(player.getUserId());
            dto.setNickname(player.getNickname());
            dto.setMaxHp(player.getMaxHp());
            dto.setCurrentHp(player.getCurrentHp());
            dto.setAttack(player.getAttack());
            dto.setDefense(player.getDefense());
            dto.setAlive(player.isAlive());
            return dto;
        }
    }

    /**
     * 战斗日志的简化信息
     */
    @Getter @Setter
    public static class BattleLogDTO {
        private Integer round;
        private Long actorUserId;
        private String actorNickname;
        private String action;
        private String skillName;
        private Integer damage;
        private Integer heal;
        private Long targetUserId;
        private String targetNickname;
        private String description;
        private LocalDateTime timestamp;

        public static BattleLogDTO fromBattleLog(BattleLog log) {
            BattleLogDTO dto = new BattleLogDTO();
            dto.setRound(log.getRound());
            dto.setActorUserId(log.getActorUserId());
            dto.setActorNickname(log.getActorNickname());
            dto.setAction(log.getAction());
            dto.setSkillName(log.getSkillName());
            dto.setDamage(log.getDamage());
            dto.setHeal(log.getHeal());
            dto.setTargetUserId(log.getTargetUserId());
            dto.setTargetNickname(log.getTargetNickname());
            dto.setDescription(log.getDescription());
            dto.setTimestamp(log.getTimestamp());
            return dto;
        }
    }
}