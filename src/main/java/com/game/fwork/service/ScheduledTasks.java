package com.game.fwork.service;

import com.game.fwork.entity.Battle;
import com.game.fwork.enums.BattleEndReason;
import com.game.fwork.enums.BattleState;
import com.game.fwork.manager.BattleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 全局定时任务组件
 * 负责系统的“心跳”检测，包括战斗超时、挂机判负和断线清理
 */
@Component
public class ScheduledTasks {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledTasks.class);

    @Autowired
    private BattleManager battleManager;

    @Autowired
    private BattleService battleService;

    @Value("${battle.turn-timeout:90}")
    private int turnTimeoutSeconds;

    @Value("${battle.waiting-timeout:30}")
    private int waitingTimeoutSeconds;

    @Scheduled(fixedRate = 5000)  // 每5秒执行一次
    public void checkBattleTimeout() {
        try {
            // 检查准备阶段超时
            checkWaitingBattles();

            // 检查挂机玩家
            checkAfkPlayers();

            // 检查断线超时玩家
            checkDisconnectTimeout();

        } catch (Exception e) {
            logger.error("定时任务执行失败", e);
        }
    }

    /**
     * 扫描正在进行的战斗，检测挂机行为
     * 如果当前行动者超过指定时间未操作，自动判负
     */
    private void checkAfkPlayers() {
        List<Battle> activeBattles = battleManager.getAllBattles();

        for (Battle battle : activeBattles) {
            // 只检查正在进行中的战斗
            if (battle.getState() != BattleState.FIGHTING) {
                continue;
            }

            long secondsSinceLastAction = battle.getSecondsSinceLastAction();

            // 挂机判定：指定秒数后未操作
            if (secondsSinceLastAction >= turnTimeoutSeconds) {
                Long afkUserId = battle.getCurrentActorUserId();
                Long winnerId = battle.getOpponent(afkUserId).getUserId();

                logger.warn("检测到挂机玩家，自动判负: battleId={}, afkUserId={}, 挂机时长={}秒",
                        battle.getBattleId(), afkUserId, secondsSinceLastAction);

                // 结束战斗：挂机者判负
                battleService.endBattleByTimeout(battle, winnerId, BattleEndReason.TIMEOUT.getCode());
            }
        }
    }

    /**
     * 检查准备阶段超时的战斗
     * 规则：创建超过指定时间仍处于 WAITING 状态
     */
    private void checkWaitingBattles() {
        List<Battle> allBattles = battleManager.getAllBattles();
        LocalDateTime now = LocalDateTime.now();

        for (Battle battle : allBattles) {
            // 只检查 WAITING 状态
            if (battle.getState() == BattleState.WAITING) {
                // Battle 的 startTime 在构造函数中被初始化为创建时间
                long seconds = Duration.between(battle.getStartTime(), now).getSeconds();

                // 超过指定时间
                if (seconds > waitingTimeoutSeconds) {
                    battleService.handleWaitingTimeout(battle);
                }
            }
        }
    }

    /**
     * 扫描断线玩家
     * 允许短暂断线（用于重连），但超过容忍时间则视为逃跑
     */
    private void checkDisconnectTimeout() {
        List<Long> timeoutPlayers = battleManager.getTimeoutDisconnectedPlayers();

        for (Long userId : timeoutPlayers) {
            Battle battle = battleManager.getBattleByUserId(userId);

            if (battle == null || battle.getState() != BattleState.FIGHTING) {
                // 战斗已结束或不存在，清理断线标记
                battleManager.clearPlayerDisconnected(userId);
                continue;
            }

            Long winnerId = battle.getOpponent(userId).getUserId();

            logger.warn("检测到断线超时玩家，自动判负: battleId={}, disconnectedUserId={}, 断线时长={}秒",
                    battle.getBattleId(), userId, battleManager.getDisconnectDurationSeconds(userId));

            // 结束战斗：断线者判负
            battleService.endBattleByTimeout(battle, winnerId, BattleEndReason.DISCONNECT.getCode());
        }
    }
}