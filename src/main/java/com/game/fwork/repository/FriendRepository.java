package com.game.fwork.repository;

import com.game.fwork.entity.Friend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * 好友关系数据访问接口
 */
@Repository
public interface FriendRepository extends JpaRepository<Friend, Long> {
    /**
     * 获取用户的好友列表
     */
    List<Friend> findByUserId(Long userId);

    /**
     * 检查两人是否已经是好友关系，避免重复添加
     */
    boolean existsByUserIdAndFriendId(Long userId, Long friendId);
}