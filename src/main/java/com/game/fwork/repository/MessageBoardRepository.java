package com.game.fwork.repository;

import com.game.fwork.entity.MessageBoard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * 留言板数据访问接口
 */
@Repository
public interface MessageBoardRepository extends JpaRepository<MessageBoard, Long> {
    /**
     * 查询与用户相关的所有留言
     * 包括：别人发给我的（target=me） OR 我发给别人的（sender=me）
     * 结果按时间倒序排列
     */
    @Query("SELECT m FROM MessageBoard m " +
            "WHERE m.targetUser.id = :userId OR m.senderUser.id = :userId " +
            "ORDER BY m.createdAt DESC")
    List<MessageBoard> findAllRelatedMessages(@Param("userId") Long userId);
}