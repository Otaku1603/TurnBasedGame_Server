package com.game.fwork.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.fwork.dto.MatchQueueItem;
import com.game.fwork.entity.Character;
import com.game.fwork.entity.User;
import com.game.fwork.netty.session.SessionManager;
import com.game.fwork.proto.GameProto.*;
import com.game.fwork.repository.CharacterRepository;
import com.game.fwork.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 匹配服务
 * 实现基于 ELO 分数的“分桶 + 动态范围扩充”匹配算法
 * 策略：优先匹配分段相近的玩家，随着等待时间增加，逐步扩大搜索范围
 */
@Service
public class MatchService {

    private static final Logger logger = LoggerFactory.getLogger(MatchService.class);

    @Autowired private StringRedisTemplate stringRedisTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private SessionManager sessionManager;
    @Autowired private BattleService battleService;

    @Value("${match.queue-key:game:match:queue}")
    private String queueKey;

    // 匹配范围配置
    @Value("${match.timeout:30}")
    private int matchTimeoutSeconds;
    @Value("${match.elo-range:100}")
    private int baseEloRange;
    private static final int MAX_ELO_RANGE = 500;
    private static final int RANGE_INCREASE_PER_10S = 50;
    private static final int BUCKET_SIZE = 100; // 每100分一个桶

    // 内存桶索引（定时任务中重建）
    private final Map<Integer, List<MatchQueueItem>> buckets = new ConcurrentHashMap<>();

    /**
     * 玩家加入匹配队列
     * 校验玩家状态（是否封禁、是否有角色），并将请求推入 Redis 队列
     */
    public boolean joinQueue(Long userId) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                logger.error("用户不存在: userId={}", userId);
                return false;
            }

            if (user.getStatus() == 0) {
                logger.warn("封禁用户尝试加入匹配: userId={}", userId);
                return false;
            }

            Character character = characterRepository
                    .findByUserIdAndIsActive(userId, 1)
                    .orElse(null);
            if (character == null) {
                logger.error("用户没有激活角色: userId={}", userId);
                return false;
            }

            MatchQueueItem item = new MatchQueueItem(
                    userId,
                    user.getEloRating(),
                    user.getNickname(),
                    character.getId()
            );

            String json = objectMapper.writeValueAsString(item);
            stringRedisTemplate.opsForList().rightPush(queueKey, json);

            logger.info("玩家加入匹配: userId={}, elo={}", userId, user.getEloRating());
            return true;

        } catch (Exception e) {
            logger.error("加入队列失败: userId={}", userId, e);
            return false;
        }
    }

    /**
     * 离开匹配队列
     */
    public boolean leaveQueue(Long userId) {
        try {
            List<String> allItems = stringRedisTemplate.opsForList()
                    .range(queueKey, 0, -1);
            if (allItems == null || allItems.isEmpty()) {
                return false;
            }

            for (String json : allItems) {
                MatchQueueItem item = objectMapper.readValue(json, MatchQueueItem.class);
                if (item.getUserId().equals(userId)) {
                    stringRedisTemplate.opsForList().remove(queueKey, 1, json);
                    logger.info("玩家离开匹配: userId={}", userId);
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            logger.error("离开队列失败: userId={}", userId, e);
            return false;
        }
    }

    /**
     * 定时执行匹配逻辑（核心算法）
     * 1. 清理超时玩家
     * 2. 将等待玩家按 ELO 分数分桶
     * 3. 按等待时间优先匹配，并在动态范围内搜索对手
     */
    @Scheduled(fixedDelay = 2000)
    public void tryMatchAll() {
        try {
            // 从Redis加载所有等待玩家
            List<String> allJsons = stringRedisTemplate.opsForList().range(queueKey, 0, -1);
            if (allJsons == null || allJsons.isEmpty()) return;

            List<MatchQueueItem> queue = new ArrayList<>();
            List<String> timeoutJsons = new ArrayList<>();

            // 解析并过滤超时玩家
            for (String json : allJsons) {
                try {
                    MatchQueueItem item = objectMapper.readValue(json, MatchQueueItem.class);
                    if (item.getWaitingTimeInSeconds() > matchTimeoutSeconds) {
                        timeoutJsons.add(json);
                        logger.info("匹配超时移除: userId={}", item.getUserId());
                    } else {
                        queue.add(item);
                    }
                } catch (Exception e) {
                    // 脏数据处理
                    timeoutJsons.add(json);
                }
            }

            // 批量清理Redis中的超时/脏数据
            for (String json : timeoutJsons) {
                stringRedisTemplate.opsForList().remove(queueKey, 1, json);
            }

            if (queue.size() < 2) return;

            // 重建分桶索引：将玩家按分数段（如每100分）分组，减少双重循环的搜索次数
            buckets.clear();
            for (MatchQueueItem item : queue) {
                int bucket = item.getEloRating() / BUCKET_SIZE;
                buckets.computeIfAbsent(bucket, k -> new ArrayList<>()).add(item);
            }

            // 按等待时间降序排序（优先匹配等待久的）
            queue.sort(Comparator.comparingLong(MatchQueueItem::getWaitingTimeInSeconds).reversed());

            // 执行匹配逻辑
            Set<Long> matchedUserIds = new HashSet<>(); // 本轮已匹配的玩家

            for (MatchQueueItem player1 : queue) {
                if (matchedUserIds.contains(player1.getUserId())) continue;

                int dynamicRange = calculateDynamicRange(player1);
                int p1Bucket = player1.getEloRating() / BUCKET_SIZE;
                int searchRange = (dynamicRange / BUCKET_SIZE) + 1;

                // 搜索相邻桶
                boolean matched = false;
                for (int b = p1Bucket - searchRange; b <= p1Bucket + searchRange; b++) {
                    List<MatchQueueItem> candidates = buckets.get(b);
                    if (candidates == null) continue;

                    for (MatchQueueItem player2 : candidates) {
                        if (player1.getUserId().equals(player2.getUserId())) continue;
                        if (matchedUserIds.contains(player2.getUserId())) continue;

                        // 根据等待时间计算动态范围，等待越久，允许的分差越大
                        if (player1.canMatchWith(player2, dynamicRange)) {
                            // 匹配成功
                            handleMatchSuccess(player1, player2, allJsons);
                            matchedUserIds.add(player1.getUserId());
                            matchedUserIds.add(player2.getUserId());
                            matched = true;
                            break;
                        }
                    }
                    if (matched) break;
                }
            }

        } catch (Exception e) {
            logger.error("匹配任务异常", e);
        }
    }

    /**
     * 计算动态匹配范围
     * 规则：每等待 10 秒，允许的分差扩大 50 分，上限 500 分
     */
    private int calculateDynamicRange(MatchQueueItem player) {
        long waitSeconds = player.getWaitingTimeInSeconds();
        int additionalRange = (int) (waitSeconds / 10) * RANGE_INCREASE_PER_10S;
        int totalRange = baseEloRange + additionalRange;
        return Math.min(totalRange, MAX_ELO_RANGE);
    }

    /**
     * 处理匹配成功逻辑
     * 创建战斗实例，并通知双方客户端跳转到准备页面
     */
    private void handleMatchSuccess(MatchQueueItem player1, MatchQueueItem player2,
                                    List<String> allJsons) {
        try {
            // 创建战斗
            String battleId = battleService.createBattle(
                    player1.getUserId(),
                    player2.getUserId()
            );

            // 构建并推送消息
            sendMatchSuccessMessage(player1, player2, battleId);
            sendMatchSuccessMessage(player2, player1, battleId);

            // 从Redis移除
            removeFromQueue(player1, allJsons);
            removeFromQueue(player2, allJsons);

        } catch (Exception e) {
            logger.error("匹配成功处理失败", e);
        }
    }

    private void sendMatchSuccessMessage(MatchQueueItem player,
                                         MatchQueueItem opponent,
                                         String battleId) {
        PlayerInfo opponentInfo = PlayerInfo.newBuilder()
                .setUserId(opponent.getUserId())
                .setNickname(opponent.getNickname())
                .setEloRating(opponent.getEloRating())
                .build();

        MatchSuccessResponse response = MatchSuccessResponse.newBuilder()
                .setBattleId(battleId)
                .setOpponent(opponentInfo)
                .build();

        GameMessage message = GameMessage.newBuilder()
                .setType(MessageType.MATCH_SUCCESS)
                .setMatchSuccessResponse(response)
                .build();

        sessionManager.sendMessage(player.getUserId(), message);
    }

    private void removeFromQueue(MatchQueueItem player, List<String> allJsons) {
        try {
            for (String json : allJsons) {
                MatchQueueItem item = objectMapper.readValue(json, MatchQueueItem.class);
                if (item.getUserId().equals(player.getUserId())) {
                    stringRedisTemplate.opsForList().remove(queueKey, 1, json);
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("从队列移除失败: userId={}", player.getUserId(), e);
        }
    }
}