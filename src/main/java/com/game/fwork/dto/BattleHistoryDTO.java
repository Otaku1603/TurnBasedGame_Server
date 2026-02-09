package com.game.fwork.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.format.DateTimeFormatter;

/**
 * 战斗历史列表项 DTO
 * 用于前端战绩展示
 * 将数据库实体转换为精简的展示对象，提前处理好时间格式和胜负关系，减轻前端逻辑负担
 */
@Getter @Setter
public class BattleHistoryDTO {
    private String battleId;
    private String time;        // 格式化后的时间
    private String opponentName;// 对手名字
    private String result;      // "WIN", "LOSE", "DRAW"
    private int rounds;         // 回合数
    private int eloChange; // ELO 变动值

    /**
     * 静态工厂方法：将 Entity 转换为 DTO
     * 核心逻辑：根据当前查看者的 ID (myUserId)，动态判断对手是谁以及胜负状态
     *
     * @param record 数据库原始记录
     * @param myUserId 当前用户的ID
     * @return 转换后的展示对象
     */
    public static BattleHistoryDTO fromEntity(com.game.fwork.entity.BattleRecord record, Long myUserId) {
        BattleHistoryDTO dto = new BattleHistoryDTO();
        dto.setBattleId(record.getBattleId());
        // 检查时间是否为空，格式化时间，方便前端直接显示
        if (record.getCreatedAt() != null) {
            dto.setTime(record.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        } else {
            dto.setTime("Unknown Time");
        }
        dto.setRounds(record.getTotalRounds());

        // 判断当前用户在记录中是 Player1 还是 Player2
        Long p1Id = record.getPlayer1Id();
        boolean isP1 = p1Id != null && p1Id.equals(myUserId);

        // 根据身份获取对手的昵称
        dto.setOpponentName(isP1 ? record.getPlayer2Nickname() : record.getPlayer1Nickname());

        // 计算胜负逻辑
        if (record.getWinnerId() == null) {
            dto.setResult("DRAW");
        } else if (record.getWinnerId().equals(myUserId)) {
            dto.setResult("WIN");
        } else {
            dto.setResult("LOSE");
        }

        // 计算 ELO 变动
        // 逻辑：After - Before = 变动值 (可能是正数也可能是负数)
        if (isP1) {
            // 防止空指针 (虽然理论上不为空)
            int after = record.getPlayer1EloAfter() != null ? record.getPlayer1EloAfter() : 0;
            int before = record.getPlayer1EloBefore() != null ? record.getPlayer1EloBefore() : 0;
            dto.setEloChange(after - before);
        } else {
            int after = record.getPlayer2EloAfter() != null ? record.getPlayer2EloAfter() : 0;
            int before = record.getPlayer2EloBefore() != null ? record.getPlayer2EloBefore() : 0;
            dto.setEloChange(after - before);
        }

        return dto;
    }
}