package com.game.fwork.repository;

import com.game.fwork.entity.BattleRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 战斗记录数据访问接口
 * 负责查询已结束的战斗历史、统计胜率、生成战报列表等
 */
@Repository
public interface BattleRecordRepository extends JpaRepository<BattleRecord, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<BattleRecord> {

    // ==================== 基础查询方法 ====================
    /**
     * 根据业务战斗ID查找记录（非主键ID）
     * 用于查看具体某场战斗的详情
     */
    Optional<BattleRecord> findByBattleId(String battleId);

    /**
     * 检查战斗记录是否存在，防止重复保存
     */
    boolean existsByBattleId(String battleId);

    // ==================== 玩家相关查询 ====================

    /** 查询玩家的所有战斗记录（按时间倒序） */
    List<BattleRecord> findByPlayer1IdOrPlayer2IdOrderByCreatedAtDesc(Long player1Id, Long player2Id);

    /** 查询玩家胜利记录 */
    List<BattleRecord> findByWinnerIdOrderByCreatedAtDesc(Long winnerId);

    /**
     * 查询特定时间段内玩家的战斗
     * 使用 JPQL 自定义查询，筛选出玩家参与（无论是P1还是P2）且在时间范围内的记录
     */
    @Query("SELECT b FROM BattleRecord b " +
            "WHERE (b.player1Id = :userId OR b.player2Id = :userId) " +
            "AND b.createdAt BETWEEN :startTime AND :endTime " +
            "ORDER BY b.createdAt DESC")
    List<BattleRecord> findPlayerBattlesByTimeRange(
            @Param("userId") Long userId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    // ==================== 统计查询 ====================

    /**
     * 统计某个玩家的总战斗场数
     */
    long countByPlayer1IdOrPlayer2Id(Long player1Id, Long player2Id);

    /**
     * 统计某个玩家的胜利场数
     */
    long countByWinnerId(Long winnerId);

    // ==================== 时间范围查询 ====================

    /**
     * 查询某段时间内的所有战斗记录
     */
    List<BattleRecord> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    /**
     * 查询最近 N 场战斗记录
     * 使用 Native SQL 和 LIMIT 限制返回条数，提高首页加载性能
     */
    @Query(value = "SELECT * FROM t_battle_record " +
            "ORDER BY created_at DESC " +
            "LIMIT :limit",
            nativeQuery = true)
    List<BattleRecord> findRecentBattles(@Param("limit") int limit);

    // ==================== 管理后台专用查询 ====================

    /**
     * 查询两个玩家之间的所有对战记录
     */
    @Query("SELECT b FROM BattleRecord b " +
            "WHERE (b.player1Id = :userId1 AND b.player2Id = :userId2) " +
            "OR (b.player1Id = :userId2 AND b.player2Id = :userId1) " +
            "ORDER BY b.createdAt DESC")
    List<BattleRecord> findBattlesBetweenPlayers(
            @Param("userId1") Long userId1,
            @Param("userId2") Long userId2
    );

    /**
     * 查询某种结束原因的战斗记录
     */
    List<BattleRecord> findByEndReasonOrderByCreatedAtDesc(String endReason);

    /**
     * 分页查询某玩家的所有战斗
     * Spring Data JPA 会自动解析 Pageable 参数并生成分页 SQL
     */
    org.springframework.data.domain.Page<BattleRecord> findByPlayer1IdOrPlayer2Id(
            Long player1Id,
            Long player2Id,
            org.springframework.data.domain.Pageable pageable
    );
}