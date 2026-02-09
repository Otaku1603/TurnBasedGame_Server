package com.game.fwork.service;

import com.game.fwork.entity.Character;
import com.game.fwork.entity.Friend;
import com.game.fwork.entity.MessageBoard;
import com.game.fwork.entity.User;
import com.game.fwork.netty.session.SessionManager;
import com.game.fwork.repository.CharacterRepository;
import com.game.fwork.repository.FriendRepository;
import com.game.fwork.repository.MessageBoardRepository;
import com.game.fwork.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SocialService {

    @Autowired private UserRepository userRepository;
    @Autowired private FriendRepository friendRepository;
    @Autowired private MessageBoardRepository messageBoardRepository;

    @Autowired private SessionManager sessionManager;
    @Autowired private CharacterRepository characterRepository;

    /**
     * 获取排行榜数据
     * 查询 ELO 分数最高的前 50 名用户，并封装头像、职业等展示信息
     */
    public List<Map<String, Object>> getLeaderboard() {
        List<User> users = userRepository.findAll(
                PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "eloRating"))
        ).getContent();

        List<Map<String, Object>> result = new ArrayList<>();

        for (User user : users) {
            Map<String, Object> dto = new HashMap<>();
            dto.put("id", user.getId());
            dto.put("nickname", user.getNickname());
            dto.put("eloRating", user.getEloRating());
            dto.put("avatarFrameId", user.getAvatarFrameId());

            // 获取激活角色的职业用于显示头像
            String charType = "warrior"; // 默认兜底
            Character activeChar = characterRepository.findByUserIdAndIsActive(user.getId(), 1).orElse(null);
            if (activeChar != null) {
                charType = activeChar.getCharType();
            }
            dto.put("charType", charType);

            result.add(dto);
        }
        return result;
    }

    /**
     * 获取好友列表
     * 关联查询好友的在线状态（从 SessionManager）和当前职业信息，并按在线状态排序
     */
    public List<Map<String, Object>> getFriendList(Long userId) {
        List<Friend> friends = friendRepository.findByUserId(userId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Friend relation : friends) {
            User friendUser = relation.getFriend();

            Map<String, Object> dto = new HashMap<>();
            dto.put("friendId", friendUser.getId());
            dto.put("nickname", friendUser.getNickname());
            dto.put("status", relation.getStatus()); // 1=正常, 0=拉黑
            dto.put("avatarFrameId", friendUser.getAvatarFrameId());

            // 1. 查询在线状态
            boolean isOnline = sessionManager.isOnline(friendUser.getId());
            dto.put("online", isOnline);

            // 2. 查询职业头像
            String charType = "warrior";
            Character activeChar = characterRepository.findByUserIdAndIsActive(friendUser.getId(), 1).orElse(null);
            if (activeChar != null) {
                charType = activeChar.getCharType();
            }
            dto.put("charType", charType);

            result.add(dto);
        }

        // 按在线状态排序，在线的排前面
        result.sort((a, b) -> {
            boolean onlineA = (boolean) a.get("online");
            boolean onlineB = (boolean) b.get("online");
            return Boolean.compare(onlineB, onlineA); // true > false
        });

        return result;
    }

    /**
     * 添加好友
     * 目前采用直接添加模式（无需验证），并在数据库中建立双向关系
     */
    @Transactional
    public void addFriend(Long userId, Long friendId) {
        if (userId.equals(friendId)) throw new RuntimeException("不能添加自己");

        User userA = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("用户不存在"));
        User userB = userRepository.findById(friendId).orElseThrow(() -> new RuntimeException("目标用户不存在"));

        // 1. 逻辑：A 添加 B
        if (!friendRepository.existsByUserIdAndFriendId(userId, friendId)) {
            Friend relationAB = new Friend(userA, userB);
            friendRepository.save(relationAB);
        }

        // 2. 逻辑：B 也自动添加 A (实现双向)
        if (!friendRepository.existsByUserIdAndFriendId(friendId, userId)) {
            Friend relationBA = new Friend(userB, userA);
            friendRepository.save(relationBA);
        }
    }

    // 留言
    public void leaveMessage(Long senderId, Long targetId, String content) {
        User sender = userRepository.findById(senderId).orElseThrow();
        User target = userRepository.findById(targetId).orElseThrow();

        MessageBoard msg = new MessageBoard(target, sender, content);
        messageBoardRepository.save(msg);
    }

    // 获取留言列表
    public List<MessageBoard> getMessages(Long userId) {
        return messageBoardRepository.findAllRelatedMessages(userId);
    }
}