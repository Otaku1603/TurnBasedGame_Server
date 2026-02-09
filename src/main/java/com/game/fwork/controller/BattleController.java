package com.game.fwork.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.fwork.dto.BattleReportDTO;
import com.game.fwork.entity.Battle;
import com.game.fwork.repository.BattleRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.game.fwork.entity.BattleRecord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 战斗数据查询接口
 * 注意：这里只提供战报查询等 HTTP 读操作；
 * 实时的战斗指令（如攻击、技能）通过 Netty WebSocket 传输，不在本控制器处理
 */
@RestController
@RequestMapping("/api/battle")
public class BattleController {

    private static final Logger logger = LoggerFactory.getLogger(BattleController.class);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BattleRecordRepository battleRecordRepository;

    private static final String BATTLE_REPORT_KEY = "battle:report:";

    /**
     * 查询单场战斗的详细战报
     * 优先查询 Redis 缓存，如果缓存已过期则提示不存在（目前设计为临时战报）
     */
    @GetMapping("/report/{battleId}")
    public ResponseEntity<Map<String, Object>> getBattleReport(@PathVariable String battleId) {
        Map<String, Object> response = new HashMap<>();

        try {
            // 从Redis查询战报
            String key = BATTLE_REPORT_KEY + battleId;
            String json = stringRedisTemplate.opsForValue().get(key);

            if (json == null) {
                response.put("success", false);
                response.put("message", "战报不存在或已过期");
                return ResponseEntity.ok(response);
            }

            // 反序列化Battle对象
            Battle battle = objectMapper.readValue(json, Battle.class);

            // 转换为DTO
            BattleReportDTO dto = convertToDTO(battle);

            response.put("success", true);
            response.put("message", "查询成功");
            response.put("data", dto);

            logger.info("战报查询成功: battleId={}", battleId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("战报查询失败: battleId={}", battleId, e);
            response.put("success", false);
            response.put("message", "查询失败: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 分页查询玩家的历史战绩
     * 返回精简版数据（去除了详细日志），减少网络传输量，用于列表展示
     */
    @GetMapping("/reports")
    public ResponseEntity<Map<String, Object>> getBattleReports(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Map<String, Object> response = new HashMap<>();

        try {
            // 1. 构建分页请求 (按创建时间倒序，最新的在前)
            // 限制每页最大 20 条，防止请求过多
            int safeSize = Math.min(size, 20);
            PageRequest pageRequest = PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

            // 2. 执行数据库查询
            // 只要 player1 是我，或者 player2 是我，都算我的战斗
            Page<BattleRecord> recordPage = battleRecordRepository.findByPlayer1IdOrPlayer2Id(userId, userId, pageRequest);

            // 3. 转换为轻量级列表 (去除 battleLogJson)
            List<Map<String, Object>> summaryList = new ArrayList<>();

            for (BattleRecord record : recordPage.getContent()) {
                Map<String, Object> summary = new HashMap<>();
                summary.put("battleId", record.getBattleId());
                summary.put("startTime", record.getStartTime());
                summary.put("endTime", record.getEndTime());
                summary.put("duration", record.getBattleDuration());
                summary.put("totalRounds", record.getTotalRounds());
                summary.put("endReason", record.getEndReason());

                // 判断胜负
                boolean isWinner = userId.equals(record.getWinnerId());
                summary.put("result", isWinner ? "VICTORY" : "DEFEAT");

                // 确定对手信息
                // 判断当前查询者是 P1 还是 P2，以便前端正确显示“胜利/失败”和对手信息
                boolean amIPlayer1 = userId.equals(record.getPlayer1Id());
                summary.put("myCharId", amIPlayer1 ? record.getPlayer1CharId() : record.getPlayer2CharId());

                // 对手信息
                Map<String, Object> opponent = new HashMap<>();
                opponent.put("userId", amIPlayer1 ? record.getPlayer2Id() : record.getPlayer1Id());
                opponent.put("nickname", amIPlayer1 ? record.getPlayer2Nickname() : record.getPlayer1Nickname());
                opponent.put("charId", amIPlayer1 ? record.getPlayer2CharId() : record.getPlayer1CharId());
                summary.put("opponent", opponent);

                // ELO 变动 (展示当时的变动)
                summary.put("eloChange", amIPlayer1
                        ? (record.getPlayer1EloAfter() - record.getPlayer1EloBefore())
                        : (record.getPlayer2EloAfter() - record.getPlayer2EloBefore()));

                summaryList.add(summary);
            }

            // 4. 构建返回结果
            response.put("success", true);
            response.put("data", summaryList);
            response.put("totalItems", recordPage.getTotalElements());
            response.put("totalPages", recordPage.getTotalPages());
            response.put("currentPage", page);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("查询历史战报失败: userId={}", userId, e);
            response.put("success", false);
            response.put("message", "查询失败: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 检查战报是否存在
     *
     * @param battleId 战斗ID
     * @return 是否存在
     */
    @GetMapping("/report/{battleId}/exists")
    public ResponseEntity<Map<String, Object>> checkBattleReportExists(@PathVariable String battleId) {
        Map<String, Object> response = new HashMap<>();

        try {
            String key = BATTLE_REPORT_KEY + battleId;
            Boolean exists = stringRedisTemplate.hasKey(key);

            response.put("success", true);
            response.put("exists", exists != null && exists);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("检查战报存在失败: battleId={}", battleId, e);
            response.put("success", false);
            response.put("message", "检查失败: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 将Battle对象转换为DTO
     *
     * @param battle 战斗对象
     * @return DTO
     */
    private BattleReportDTO convertToDTO(Battle battle) {
        BattleReportDTO dto = new BattleReportDTO();

        dto.setBattleId(battle.getBattleId());
        dto.setState(battle.getState());
        dto.setStartTime(battle.getStartTime());
        dto.setEndTime(battle.getEndTime());

        dto.setPlayer1(BattleReportDTO.BattlePlayerDTO.fromBattlePlayer(battle.getPlayer1()));
        dto.setPlayer2(BattleReportDTO.BattlePlayerDTO.fromBattlePlayer(battle.getPlayer2()));

        dto.setCurrentRound(battle.getCurrentRound());
        dto.setCurrentActorUserId(battle.getCurrentActorUserId());

        // 转换战斗日志
        List<BattleReportDTO.BattleLogDTO> logDTOs = battle.getBattleLogs().stream()
                .map(BattleReportDTO.BattleLogDTO::fromBattleLog)
                .collect(Collectors.toList());
        dto.setLogs(logDTOs);

        dto.setWinnerId(battle.getWinnerId());
        dto.setEndReason(battle.getEndReason());

        return dto;
    }
}