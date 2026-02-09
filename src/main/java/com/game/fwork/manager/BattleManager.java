package com.game.fwork.manager;

import com.game.fwork.entity.Battle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 战斗对象管理器
 * 维护所有“进行中”战斗的内存索引
 * 核心结构：
 * 1. battles: battleId -> Battle对象
 * 2. userToBattle: userId -> battleId (快速查找玩家所在的战斗)
 */
@Component
public class BattleManager {

    private static final Logger logger = LoggerFactory.getLogger(BattleManager.class);

    @Value("${battle.turn-offline:120}")
    private int turnTimeoutSeconds;

    /**
     * 战斗ID到战斗实例的映射
     */
    private final Map<String, Battle> battles = new ConcurrentHashMap<>();

    /**
     * 用户ID到战斗ID的映射
     */
    private final Map<Long, String> userToBattle = new ConcurrentHashMap<>();

    /**
     * 断线玩家管理
     */
    private final Map<Long, LocalDateTime> disconnectedPlayers = new ConcurrentHashMap<>();

    /**
     * 添加战斗
     *
     * @param battle 战斗对象
     */
    public void addBattle(Battle battle) {
        battles.put(battle.getBattleId(), battle);
        userToBattle.put(battle.getPlayer1().getUserId(), battle.getBattleId());
        userToBattle.put(battle.getPlayer2().getUserId(), battle.getBattleId());
        logger.info("战斗已添加: battleId={}, player1={}, player2={}",
                battle.getBattleId(),
                battle.getPlayer1().getUserId(),
                battle.getPlayer2().getUserId());
    }

    /**
     * 移除战斗
     *
     * @param battleId 战斗ID
     */
    public void removeBattle(String battleId) {
        Battle battle = battles.remove(battleId);
        if (battle != null) {
            userToBattle.remove(battle.getPlayer1().getUserId());
            userToBattle.remove(battle.getPlayer2().getUserId());
            // 同时清理断线记录
            disconnectedPlayers.remove(battle.getPlayer1().getUserId());
            disconnectedPlayers.remove(battle.getPlayer2().getUserId());
            logger.info("战斗已移除: battleId={}", battleId);
        }
    }

    /**
     * 根据战斗ID获取战斗
     *
     * @param battleId 战斗ID
     * @return 战斗对象，不存在返回null
     */
    public Battle getBattle(String battleId) {
        return battles.get(battleId);
    }

    /**
     * 根据用户ID获取该玩家当前的战斗
     *
     * @param userId 玩家ID
     * @return 战斗对象，不存在返回null
     */
    public Battle getBattleByUserId(Long userId) {
        String battleId = userToBattle.get(userId);
        if (battleId == null) {
            return null;
        }
        return battles.get(battleId);
    }

    /**
     * 获取当前战斗总数
     *
     * @return 战斗数量
     */
    public int getBattleCount() {
        return battles.size();
    }

    /**
     * 获取所有战斗（用于定时任务扫描）
     *
     * @return 战斗列表
     */
    public List<Battle> getAllBattles() {
        return new ArrayList<>(battles.values());
    }

    // ========== 断线玩家管理 ==========

    /**
     * 标记玩家断线
     * 并不立即结束战斗，而是记录断线时间戳
     * 定时任务会轮询此列表，若超过容忍时间（如 30s）未重连，才判负
     *
     * @param userId 玩家ID
     */
    public void markPlayerDisconnected(Long userId) {
        disconnectedPlayers.put(userId, LocalDateTime.now());
        logger.info("玩家标记为断线: userId={}", userId);
    }

    /**
     * 清除玩家断线标记
     * 玩家重连时调用
     *
     * @param userId 玩家ID
     */
    public void clearPlayerDisconnected(Long userId) {
        disconnectedPlayers.remove(userId);
        logger.info("玩家断线标记已清除: userId={}", userId);
    }

    /**
     * 检查玩家是否断线
     *
     * @param userId 玩家ID
     * @return true=已断线，false=未断线
     */
    public boolean isPlayerDisconnected(Long userId) {
        return disconnectedPlayers.containsKey(userId);
    }

    /**
     * 获取玩家断线时长（秒）
     *
     * @param userId 玩家ID
     * @return 断线时长（秒），未断线返回0
     */
    public long getDisconnectDurationSeconds(Long userId) {
        LocalDateTime disconnectTime = disconnectedPlayers.get(userId);
        if (disconnectTime == null) {
            return 0;
        }
        return java.time.Duration.between(disconnectTime, LocalDateTime.now()).getSeconds();
    }

    /**
     * 获取所有断线超过120秒的玩家
     * 用于定时任务扫描
     *
     * @return 超时断线的玩家ID列表
     */
    public List<Long> getTimeoutDisconnectedPlayers() {
        List<Long> timeoutPlayers = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        disconnectedPlayers.forEach((userId, disconnectTime) -> {
            long seconds = java.time.Duration.between(disconnectTime, now).getSeconds();
            if (seconds >= turnTimeoutSeconds) {
                timeoutPlayers.add(userId);
            }
        });

        return timeoutPlayers;
    }
}