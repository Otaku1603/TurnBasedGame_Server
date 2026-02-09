package com.game.fwork.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.fwork.config.GameConfig;
import com.game.fwork.entity.*;
import com.game.fwork.entity.Character;
import com.game.fwork.enums.BattleState;
import com.game.fwork.manager.BattleManager;
import com.game.fwork.manager.ItemManager;
import com.game.fwork.manager.SkillManager;
import com.game.fwork.netty.session.SessionManager;
import com.game.fwork.proto.GameProto.*;
import com.game.fwork.repository.*;
import com.game.fwork.util.DamageCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 战斗核心业务服务
 * 负责战斗全生命周期管理：从创建、回合流转、技能结算到最终的数据持久化
 * 该服务与 NettyHandler 紧密配合，处理实时交互，并利用 Redis 缓存保证战斗状态的高速读取
 */
@Service
public class BattleService {
    private static final Logger logger = LoggerFactory.getLogger(BattleService.class);

    @Autowired private BattleManager battleManager;
    @Autowired private SessionManager sessionManager;
    @Autowired private UserRepository userRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private StringRedisTemplate stringRedisTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BattleRecordRepository battleRecordRepository;
    @Autowired private DamageCalculator damageCalculator;
    @Autowired private UserInventoryRepository userInventoryRepository;
    @Autowired private SkillRepository skillRepository;

    @Autowired private SkillManager skillManager;
    @Autowired private ItemManager itemManager;

    @Autowired private GameConfig gameConfig;

    @Autowired
    @Lazy
    private MatchService matchService;

    private static final String BATTLE_CACHE_KEY = "battle:cache:";
    private static final String BATTLE_REPORT_KEY = "battle:report:";
    private static final String BATTLE_SNAPSHOT_KEY = "battle:snapshot:";

    /**
     * 创建并初始化一场新战斗
     * 1. 校验双方玩家和角色状态
     * 2. 从数据库加载角色属性和技能配置
     * 3. 初始化战斗对象并存入内存和 Redis 缓存
     *
     * @param player1Id 玩家1 ID
     * @param player2Id 玩家2 ID
     * @return 生成的唯一战斗ID
     */
    public String createBattle(Long player1Id, Long player2Id) {
        try {
            // 生成战斗ID：时间戳+UUID，确保唯一性
            String battleId = "BATTLE_" + System.currentTimeMillis() + "_" +
                    UUID.randomUUID().toString().substring(0, 8);

            User user1 = userRepository.findById(player1Id).orElseThrow(() -> new RuntimeException("P1不存在"));
            User user2 = userRepository.findById(player2Id).orElseThrow(() -> new RuntimeException("P2不存在"));

            Character char1 = characterRepository.findByUserIdAndIsActive(player1Id, 1).orElseThrow(() -> new RuntimeException("P1无角色"));
            Character char2 = characterRepository.findByUserIdAndIsActive(player2Id, 1).orElseThrow(() -> new RuntimeException("P2无角色"));

            BattlePlayer bp1 = new BattlePlayer(char1, user1);
            BattlePlayer bp2 = new BattlePlayer(char2, user2);

            // 从数据库查询角色技能，并初始化到 BattlePlayer 中
            List<Integer> p1Skills = skillRepository.findSkillIdsByCharacterId(char1.getId());
            List<Integer> p2Skills = skillRepository.findSkillIdsByCharacterId(char2.getId());

            bp1.initSkills(p1Skills);
            bp2.initSkills(p2Skills);

            logger.info("P1技能: {}, P2技能: {}", p1Skills, p2Skills); // 调试日志

            Battle battle = new Battle(battleId, bp1, bp2);
            // 将战斗对象存入本地内存（用于快速处理）和 Redis（用于备份）
            battleManager.addBattle(battle);
            cacheBattle(battle);

            logger.info("战斗创建成功: {}", battleId);
            return battleId;
        } catch (Exception e) {
            logger.error("创建战斗异常", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 处理玩家“准备就绪”请求
     * 当双方都准备就绪时，自动触发战斗开始逻辑
     */
    public void playerReady(String battleId, Long userId) {
        Battle battle = battleManager.getBattle(battleId);
        if (battle == null) return;

        BattlePlayer player = battle.getPlayerByUserId(userId);
        if (player != null) {
            player.setReady(true);
            logger.info("玩家准备: {}", player.getNickname());

            // 简单的准备日志
            addLogAndBroadcast(battle, player, null, "准备就绪", 0, 0, player.getNickname() + " 已准备");

            if (battle.getPlayer1().isReady() && battle.getPlayer2().isReady()) {
                startBattle(battle);
            }
        }
    }

    /**
     * 正式开始战斗
     * 1. 变更战斗状态为 FIGHTING
     * 2. 向双方客户端发送 BATTLE_START 消息（包含初始盘面数据）
     */
    private void startBattle(Battle battle) {
        battle.setState(BattleState.FIGHTING);

        BattleStartResponse response = BattleStartResponse.newBuilder()
                .setBattleId(battle.getBattleId())
                .setPlayer1(buildPlayerData(battle.getPlayer1()))
                .setPlayer2(buildPlayerData(battle.getPlayer2()))
                .setCurrentActorUserId(battle.getCurrentActorUserId())
                .setCurrentRound(battle.getCurrentRound())
                .build();

        GameMessage msg = GameMessage.newBuilder()
                .setType(MessageType.BATTLE_START)
                .setBattleStartResponse(response)
                .build();

        sessionManager.sendMessage(battle.getPlayer1().getUserId(), msg);
        sessionManager.sendMessage(battle.getPlayer2().getUserId(), msg);

        cacheBattle(battle);
    }

    /**
     * 统一处理战斗内的玩家操作
     * 负责校验回合归属权，并根据操作类型（技能/防御/道具）分发到具体逻辑
     *
     * @param actionType 1=使用技能, 2=防御, 3=使用道具
     * @param paramId 对应的技能ID或道具ID
     */
    public void handleBattleAction(String battleId, Long userId, int actionType, int paramId) {
        Battle battle = battleManager.getBattle(battleId);
        if (battle == null || battle.getState() != BattleState.FIGHTING) return;

        // 轮次检查
        if (!battle.isPlayerTurn(userId)) {
            logger.warn("非当前回合操作: userId={}", userId);
            return;
        }

        BattlePlayer actor = battle.getPlayerByUserId(userId);
        BattlePlayer target = battle.getOpponent(userId);

        // 更新最后操作时间，防止被定时任务误判为挂机
        battle.updateLastActionTime();

        if (actionType == 1) {
            handleSkillAction(battle, actor, target, paramId);
        } else if (actionType == 2) {
            handleDefendAction(battle, actor);
        } else if (actionType == 3) {
            handleItemAction(battle, actor, paramId);
        } else {
            logger.warn("未知操作类型: {}", actionType);
        }
    }

    /**
     * 执行技能逻辑
     * 调用 DamageCalculator 进行数值计算，并处理扣减 CD、记录日志等副作用
     */
    private void handleSkillAction(Battle battle, BattlePlayer actor, BattlePlayer target, int skillId) {
        Skill skill = skillManager.getSkill(skillId);
        if (skill == null) {
            logger.error("技能数据缺失: ID={}", skillId);
            return;
        }

        // 检查冷却
        if (!actor.canUseSkill(skillId)) {
            logger.warn("技能冷却中: {}", skill.getSkillName());
            return;
        }

        int damage = 0;
        int heal = 0;
        String desc;

        // 根据技能类型区分处理：治疗技能加血，攻击技能扣血
        if ("heal".equals(skill.getSkillType())) {
            heal = damageCalculator.calculateHeal(actor, skill);
            actor.heal(heal);
            desc = String.format("%s 使用 %s，恢复了 %d 点生命", actor.getNickname(), skill.getSkillName(), heal);
        } else {
            damage = damageCalculator.calculateDamage(actor, target, skill);
            target.takeDamage(damage);
            desc = String.format("%s 对 %s 使用 %s，造成 %d 点伤害",
                    actor.getNickname(), target.getNickname(), skill.getSkillName(), damage);

            // 简单的状态描述追加
            if (damage == 0) desc += " (被闪避!)";
            if (target.isDefending() && damage > 0) desc += " (防御减伤)";
        }

        // 触发冷却
        actor.useSkill(skillId, skill.getCooldown());

        // 记录与推送
        addLogAndBroadcast(battle, actor, target, skill.getSkillName(), damage, heal, desc);

        // 推进回合
        proceedTurn(battle, actor, target);
    }

    /**
     * 执行防御逻辑
     * 标记玩家为防御状态，下回合受到的伤害将减免
     */
    private void handleDefendAction(Battle battle, BattlePlayer actor) {
        actor.setDefending(true);
        String desc = actor.getNickname() + " 进入防御姿态，下回合受到的伤害减半";

        addLogAndBroadcast(battle, actor, null, "防御", 0, 0, desc);
        proceedTurn(battle, actor, battle.getOpponent(actor.getUserId()));
    }

    /**
     * 执行道具逻辑
     * 1. 校验背包库存，扣除物品
     * 2. 应用道具效果（目前主要是恢复生命）
     */
    private void handleItemAction(Battle battle, BattlePlayer actor, int itemId) {
        // 1. 基础校验
        Item item = itemManager.getItem(itemId);
        if (item == null) {
            logger.error("道具配置不存在: ID={}", itemId);
            return;
        }

        if (!"POTION".equals(item.getType())) {
            logger.warn("非药水道具不可在战斗中使用: {}", item.getName());
            return;
        }

        // 2. 检查并扣除库存
        UserInventory inventory = userInventoryRepository
                .findByUserIdAndItemId(actor.getUserId(), itemId)
                .orElse(null);

        if (inventory == null || inventory.getCount() <= 0) {
            logger.warn("玩家试图使用未拥有的道具: userId={}, itemId={}", actor.getUserId(), itemId);
            return;
        }

        // 扣除数量
        inventory.setCount(inventory.getCount() - 1);
        // 如果道具用光了，直接从数据库删除记录；否则更新剩余数量
        if (inventory.getCount() <= 0) {
            userInventoryRepository.delete(inventory);
        } else {
            userInventoryRepository.save(inventory);
        }
        logger.info("玩家使用道具: {}, 剩余数量: {}", item.getName(), inventory.getCount());

        // 3. 应用效果 (完全信任数据库的 effect_value)
        int heal = item.getEffectValue();
        actor.heal(heal);

        String desc = String.format("%s 使用了 %s，恢复了 %d 点生命", actor.getNickname(), item.getName(), heal);

        // 4. 广播与推进
        addLogAndBroadcast(battle, actor, null, "道具:" + item.getName(), 0, heal, desc);
        proceedTurn(battle, actor, battle.getOpponent(actor.getUserId()));
    }

    /**
     * 推进战斗回合（核心流转逻辑）
     * 1. 判断战斗是否结束（一方死亡）
     * 2. 切换当前行动者
     * 3. 处理回合结算（减少冷却时间、重置防御状态等）
     * 4. 保存快照以备断线重连
     */
    private void proceedTurn(Battle battle, BattlePlayer actor, BattlePlayer target) {
        // 1. 检查战斗结束
        if (battle.shouldEnd()) {
            endBattle(battle, actor.getUserId(), "NORMAL");
            return;
        }

        // 2. 切换行动者
        battle.switchActor();

        // 3. 处理状态变更
        // 刚行动完的人：减少技能CD
        actor.reduceCooldowns();

        // 下一个要行动的人：重置回合状态（如解除上一轮的防御）
        // 注意：切换后 getCurrentActorUserId 已经是下一个人了
        BattlePlayer nextActor = battle.getPlayerByUserId(battle.getCurrentActorUserId());
        nextActor.resetTurnState();

        // 4. 缓存状态
        // 保存回合快照到 Redis，确保玩家断线重连时能恢复到当前回合状态
        cacheBattle(battle);
        saveSnapshot(battle);
    }

    /**
     * 记录战斗日志并向客户端广播状态更新
     * 这里需要预判下一个行动者，以便前端 UI 正确显示“轮到对方”
     */
    private void addLogAndBroadcast(Battle battle, BattlePlayer actor, BattlePlayer target,
                                    String skillName, int damage, int heal, String desc) {
        // 1. 记录日志
        BattleLog log = new BattleLog();
        log.setRound(battle.getCurrentRound());
        log.setActorUserId(actor.getUserId());
        log.setActorNickname(actor.getNickname());
        log.setAction(skillName);
        log.setSkillName(skillName);
        log.setDamage(damage);
        log.setHeal(heal);
        if (target != null) {
            log.setTargetUserId(target.getUserId());
            log.setTargetNickname(target.getNickname());
        }
        log.setDescription(desc);
        battle.addLog(log);

        // 2. 计算下一个行动者 ID
        Long nextActorId;
        if (battle.getPlayer1().getUserId().equals(actor.getUserId())) {
            nextActorId = battle.getPlayer2().getUserId();
        } else {
            nextActorId = battle.getPlayer1().getUserId();
        }

        if (battle.getState() == BattleState.FIGHTING) {
            nextActorId = battle.getOpponent(actor.getUserId()).getUserId();
        } else {
            // 还没开始打，保持当前状态
            nextActorId = battle.getCurrentActorUserId();
        }

        // 3. 构建 Protobuf 消息
        BattleUpdateResponse.Builder builder = BattleUpdateResponse.newBuilder()
                .setBattleId(battle.getBattleId())
                .setCurrentRound(battle.getCurrentRound())
                .setActorUserId(actor.getUserId())
                .setSkillName(skillName)
                .setDamage(damage)
                .setHeal(heal)
                .setDescription(desc)
                .setPlayer1(buildPlayerData(battle.getPlayer1()))
                .setPlayer2(buildPlayerData(battle.getPlayer2()))
                .setNextActorUserId(nextActorId);

        if (target != null) {
            builder.setTargetUserId(target.getUserId());
        }

        GameMessage msg = GameMessage.newBuilder()
                .setType(MessageType.BATTLE_UPDATE)
                .setBattleUpdateResponse(builder)
                .build();

        // 4. 推送
        sessionManager.sendMessage(battle.getPlayer1().getUserId(), msg);
        sessionManager.sendMessage(battle.getPlayer2().getUserId(), msg);
    }

    // === 辅助方法 ===
    private BattlePlayerData buildPlayerData(BattlePlayer player) {
        BattlePlayerData.Builder builder = BattlePlayerData.newBuilder()
                .setUserId(player.getUserId())
                .setNickname(player.getNickname())
                .setMaxHp(player.getMaxHp())
                .setCurrentHp(player.getCurrentHp())
                .setAttack(player.getAttack())
                .setDefense(player.getDefense())
                .setIsAlive(player.isAlive());

        player.getCooldowns().forEach(builder::putCooldowns);
        return builder.build();
    }

    /**
     * 正常结束战斗
     * 广播结算面板，并触发异步的数据持久化流程
     */
    private void endBattle(Battle battle, Long winnerId, String endReason) {
        battle.endBattle(winnerId, endReason);
        Long loserId = battle.getPlayer1().getUserId().equals(winnerId)
                ? battle.getPlayer2().getUserId() : battle.getPlayer1().getUserId();

        logger.info("战斗结束: winner={}, reason={}", winnerId, endReason);

        BattleEndResponse response = BattleEndResponse.newBuilder()
                .setBattleId(battle.getBattleId())
                .setWinnerId(winnerId)
                .setLoserId(loserId)
                .setEndReason(endReason)
                .setTotalRounds(battle.getCurrentRound())
                .setPlayer1(buildPlayerData(battle.getPlayer1()))
                .setPlayer2(buildPlayerData(battle.getPlayer2()))
                .build();

        GameMessage msg = GameMessage.newBuilder()
                .setType(MessageType.BATTLE_END)
                .setBattleEndResponse(response)
                .build();

        sessionManager.sendMessage(battle.getPlayer1().getUserId(), msg);
        sessionManager.sendMessage(battle.getPlayer2().getUserId(), msg);

        saveBattleResultAsync(battle, winnerId, loserId);
        battleManager.removeBattle(battle.getBattleId());
    }

    /**
     * 异常结束战斗（挂机/断线）
     * 仅通知获胜方（如果在线），不广播给离线方
     */
    public void endBattleByTimeout(Battle battle, Long winnerId, String endReason) {
        // 与 endBattle 类似，但只发给在线的人，且不用广播
        battle.endBattle(winnerId, endReason);
        Long loserId = battle.getPlayer1().getUserId().equals(winnerId)
                ? battle.getPlayer2().getUserId() : battle.getPlayer1().getUserId();

        logger.info("异常结束: winner={}, reason={}", winnerId, endReason);

        BattleEndResponse response = BattleEndResponse.newBuilder()
                .setBattleId(battle.getBattleId())
                .setWinnerId(winnerId)
                .setEndReason(endReason) // 客户端根据这个提示 "对手逃跑"
                .build();

        GameMessage msg = GameMessage.newBuilder()
                .setType(MessageType.BATTLE_END)
                .setBattleEndResponse(response)
                .build();

        // 尝试发送给胜者（他可能在线）
        sessionManager.sendMessage(winnerId, msg);

        saveBattleResultAsync(battle, winnerId, loserId);
        battleManager.removeBattle(battle.getBattleId());
    }

    /**
     * 处理准备阶段超时
     * 如果一方已准备而另一方未准备，判未准备方失败，并让已准备方重新匹配
     */
    public void handleWaitingTimeout(Battle battle) {
        boolean p1Ready = battle.getPlayer1().isReady();
        boolean p2Ready = battle.getPlayer2().isReady();

        battleManager.removeBattle(battle.getBattleId());

        if (!p1Ready && !p2Ready) {
            // 双方都未准备 -> 结束
        } else {
            Long readyUser = p1Ready ? battle.getPlayer1().getUserId() : battle.getPlayer2().getUserId();
            Long notReadyUser = p1Ready ? battle.getPlayer2().getUserId() : battle.getPlayer1().getUserId();

            // 通知已准备的人重新匹配
            BattleEndResponse res = BattleEndResponse.newBuilder()
                    .setEndReason("MATCH_TIMEOUT_OPPONENT")
                    .build();
            sessionManager.sendMessage(readyUser, GameMessage.newBuilder()
                    .setType(MessageType.BATTLE_END).setBattleEndResponse(res).build());

            // 自动重新匹配
            matchService.joinQueue(readyUser);

            // 通知未准备的人
            sessionManager.sendMessage(notReadyUser, GameMessage.newBuilder()
                    .setType(MessageType.BATTLE_END)
                    .setBattleEndResponse(BattleEndResponse.newBuilder().setEndReason("MATCH_TIMEOUT_YOU").build())
                    .build());
        }
    }

    public void surrender(String battleId, Long userId) {
        Battle battle = battleManager.getBattle(battleId);
        if (battle != null) {
            Long opponentId = battle.getOpponent(userId).getUserId();
            endBattle(battle, opponentId, "SURRENDER");
        }
    }

    /**
     * 处理断线重连请求
     * 优先从 Redis 读取最新的回合快照恢复战斗现场
     */
    public GameMessage handleRejoin(Long userId) {
        Battle battle = battleManager.getBattleByUserId(userId);
        if (battle == null || battle.getState().isEnded()) {
            return GameMessage.newBuilder().setType(MessageType.BATTLE_REJOIN_RESPONSE)
                    .setBattleRejoinResponse(BattleRejoinResponse.newBuilder()
                            .setSuccess(false).setMessage("无进行中战斗").build()).build();
        }

        // 尝试读快照
        String key = BATTLE_SNAPSHOT_KEY + battle.getBattleId();
        String json = stringRedisTemplate.opsForValue().get(key);
        TurnSnapshot snapshot = null;
        if (json != null) {
            try {
                snapshot = objectMapper.readValue(json, TurnSnapshot.class);
            } catch (Exception e) {}
        }
        if (snapshot == null) snapshot = TurnSnapshot.fromBattle(battle);

        // 构建BattlePlayerData
        BattlePlayerData p1Data = buildPlayerData(battle.getPlayer1());
        BattlePlayerData p2Data = buildPlayerData(battle.getPlayer2());

        return GameMessage.newBuilder()
                .setType(MessageType.BATTLE_REJOIN_RESPONSE)
                .setBattleRejoinResponse(BattleRejoinResponse.newBuilder()
                        .setSuccess(true)
                        .setBattleId(battle.getBattleId())
                        .setCurrentRound(battle.getCurrentRound())
                        .setCurrentActorUserId(battle.getCurrentActorUserId())
                        .setPlayer1(p1Data)
                        .setPlayer2(p2Data)
                        .build())
                .build();
    }

    /**
     * 异步保存战斗结算数据
     * 包括：更新玩家 ELO 分数、金币、胜场数，并将完整战报写入 MySQL
     * 使用 @Async 避免阻塞主线程的战斗逻辑
     */
    @Async
    public void saveBattleResultAsync(Battle battle, Long winnerId, Long loserId) {
        try {
            User winner = userRepository.findById(winnerId).orElse(null);
            User loser = userRepository.findById(loserId).orElse(null);
            if (winner == null || loser == null) return;

            // ELO 计算
            int k = gameConfig.getElo().getKFactor();
            int winnerChange = k; // 简化版：赢了加K分
            int loserChange = -15; // 输了扣固定分

            // 记录原始分
            int winnerOldElo = winner.getEloRating();
            int loserOldElo = loser.getEloRating();

            // 更新
            winner.setEloRating(winnerOldElo + winnerChange);
            winner.setWinCount(winner.getWinCount() + 1);
            winner.setTotalBattles(winner.getTotalBattles() + 1);
            // 赢家加金币 (简化：固定加100)
            winner.setGold(winner.getGold() == null ? 100 : winner.getGold() + 100);

            loser.setEloRating(Math.max(0, loserOldElo + loserChange));
            loser.setTotalBattles(loser.getTotalBattles() + 1);
            // 输家加低保金币(简化：固定加20)
            loser.setGold(loser.getGold() == null ? 20 : loser.getGold() + 20);

            userRepository.save(winner);
            userRepository.save(loser);

            // 保存 Redis 战报
            String json = objectMapper.writeValueAsString(battle);
            stringRedisTemplate.opsForValue().set(BATTLE_REPORT_KEY + battle.getBattleId(), json, 7, TimeUnit.DAYS);

            // 保存 MySQL 记录
            // 检查重复
            if (battleRecordRepository.existsByBattleId(battle.getBattleId())) {
                return;
            }

            // 准备数据
            BattlePlayer p1 = battle.getPlayer1();
            BattlePlayer p2 = battle.getPlayer2();

            // 确定 P1 的 ELO 变动情况
            int p1EloBefore, p1EloAfter;
            if (p1.getUserId().equals(winnerId)) {
                p1EloBefore = winnerOldElo;
                p1EloAfter = winner.getEloRating();
            } else {
                p1EloBefore = loserOldElo;
                p1EloAfter = loser.getEloRating();
            }

            // 确定 P2 的 ELO 变动情况
            int p2EloBefore, p2EloAfter;
            if (p2.getUserId().equals(winnerId)) {
                p2EloBefore = winnerOldElo;
                p2EloAfter = winner.getEloRating();
            } else {
                p2EloBefore = loserOldElo;
                p2EloAfter = loser.getEloRating();
            }

            // 序列化日志
            String logJson = "[]";
            try {
                logJson = objectMapper.writeValueAsString(battle.getBattleLogs());
            } catch (Exception ex) {
                logger.error("日志序列化失败", ex);
            }

            // 计算时长
            Integer duration = 0;
            if (battle.getStartTime() != null && battle.getEndTime() != null) {
                duration = (int) java.time.Duration.between(battle.getStartTime(), battle.getEndTime()).getSeconds();
            }

            // 构建实体对象
            BattleRecord record = new BattleRecord();
            record.setBattleId(battle.getBattleId());

            record.setPlayer1Id(p1.getUserId());
            record.setPlayer1Nickname(p1.getNickname());
            record.setPlayer1CharId(p1.getCharacterId());
            record.setPlayer1FinalHp(p1.getCurrentHp());
            record.setPlayer1EloBefore(p1EloBefore);
            record.setPlayer1EloAfter(p1EloAfter);

            record.setPlayer2Id(p2.getUserId());
            record.setPlayer2Nickname(p2.getNickname());
            record.setPlayer2CharId(p2.getCharacterId());
            record.setPlayer2FinalHp(p2.getCurrentHp());
            record.setPlayer2EloBefore(p2EloBefore);
            record.setPlayer2EloAfter(p2EloAfter);

            record.setWinnerId(winnerId);
            record.setEndReason(battle.getEndReason());
            record.setTotalRounds(battle.getCurrentRound());
            record.setBattleDuration(duration);
            // 将战斗过程日志序列化为 JSON 字符串存储，便于后续回放或详情展示
            record.setBattleLogJson(logJson);

            record.setStartTime(battle.getStartTime());
            record.setEndTime(battle.getEndTime());
            record.setCreatedAt(java.time.LocalDateTime.now());

            // 执行插入
            battleRecordRepository.save(record);

            logger.info("战斗结算完成，已保存数据库。");

        } catch (Exception e) {
            logger.error("保存战斗结果失败", e);
        }
    }

    /// 发送结束消息到单个用户
    private void sendBattleEndToUser(Battle battle, Long userId, String reason) {
        BattleEndResponse response = BattleEndResponse.newBuilder()
                .setBattleId(battle.getBattleId())
                .setEndReason(reason)
                .setTotalRounds(0)
                .build();

        GameMessage message = GameMessage.newBuilder()
                .setType(MessageType.BATTLE_END)
                .setBattleEndResponse(response)
                .build();

        sessionManager.sendMessage(userId, message);
    }

    private void cacheBattle(Battle battle) {
        try {
            String json = objectMapper.writeValueAsString(battle);
            stringRedisTemplate.opsForValue().set(BATTLE_CACHE_KEY + battle.getBattleId(), json, 30, TimeUnit.MINUTES);
        } catch (Exception e) { logger.error("缓存失败", e); }
    }

    private void saveSnapshot(Battle battle) {
        try {
            TurnSnapshot snapshot = TurnSnapshot.fromBattle(battle);
            String json = objectMapper.writeValueAsString(snapshot);
            stringRedisTemplate.opsForValue().set(BATTLE_SNAPSHOT_KEY + battle.getBattleId(), json, 30, TimeUnit.MINUTES);
        } catch (Exception e) { logger.error("快照失败", e); }
    }
}