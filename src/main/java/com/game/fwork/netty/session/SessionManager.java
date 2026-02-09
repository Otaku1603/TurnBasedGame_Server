package com.game.fwork.netty.session;

import com.game.fwork.proto.GameProto.GameMessage;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Netty 会话管理器
 * 维护 UserId 与 Netty Channel 的映射关系，实现向指定用户推送消息
 */
@Component
public class SessionManager {

    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);

    /**
     * 用户ID到Channel的映射
     * Key: userId
     * Value: Channel
     */
    private final Map<Long, Channel> sessions = new ConcurrentHashMap<>();

    /**
     * Channel到用户ID的反向映射
     * 用于连接断开时快速查找userId
     */
    private final Map<Channel, Long> channelToUserId = new ConcurrentHashMap<>();

    /**
     * 添加会话
     * 包含“顶号”逻辑：如果该用户已有旧连接，强制关闭旧连接并通知旧客户端下线
     *
     * @param userId 用户ID
     * @param newChannel 新的连接
     */
    public void addSession(Long userId, Channel newChannel) {
        // 放入新连接，同时获取旧连接（原子操作）
        Channel oldChannel = sessions.put(userId, newChannel);

        // 如果存在旧连接，且旧连接不是当前连接（防止自己顶自己）
        if (oldChannel != null && oldChannel != newChannel) {
            if (oldChannel.isActive()) {
                logger.warn("检测到异地登录（顶号）: userId={}, remoteAddress={}",
                        userId, oldChannel.remoteAddress());

                // 在关闭前发送一条通知消息告诉旧客户端被踢下线了
                sendKickMsg(oldChannel);
                oldChannel.close(); // 关闭旧连接
            }
            // 清理旧连接的反向映射
            channelToUserId.remove(oldChannel);
        }

        // 建立新连接的反向映射
        channelToUserId.put(newChannel, userId);

        logger.info("玩家上线: userId={}, 在线人数={}", userId, sessions.size());
    }

    /**
     * 移除会话
     * 通常在 channelInactive 中调用
     *
     * @param channel 断开的Channel
     */
    public void removeSession(Channel channel) {
        Long userId = channelToUserId.remove(channel);

        // 只有当 sessions Map 里的连接也是这个 channel 时才移除
        if (userId != null) {
            sessions.remove(userId, channel);
            logger.info("玩家下线: userId={}, 在线人数={}", userId, sessions.size());
        }
    }

    /**
     * 发送消息给指定用户
     *
     * @param userId 用户ID
     * @param message Protobuf消息
     * @return 是否发送成功
     */
    public boolean sendMessage(Long userId, GameMessage message) {
        Channel channel = sessions.get(userId);
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(message);
            return true;
        }
        return false;
    }

    /**
     * 检查用户是否在线
     *
     * @param userId 用户ID
     * @return 是否在线
     */
    public boolean isOnline(Long userId) {
        Channel channel = sessions.get(userId);
        return channel != null && channel.isActive();
    }

    /**
     * 获取在线人数
     *
     * @return 在线人数
     */
    public int getOnlineCount() {
        return sessions.size();
    }

    /**
     * 获取所有在线用户ID
     *
     * @return 用户ID集合
     */
    public java.util.Set<Long> getOnlineUserIds() {
        return sessions.keySet();
    }

    /**
     * 根据Channel获取用户ID
     *
     * @param channel 连接Channel
     * @return 用户ID，找不到返回null
     */
    public Long getUserIdByChannel(Channel channel) {
        return channelToUserId.get(channel);
    }

    /**
     * 发送踢出通知
     * 复用 LoginResponse，success=false 表示异常状态
     */
    private void sendKickMsg(Channel channel) {
        try {
            com.game.fwork.proto.GameProto.LoginResponse response = com.game.fwork.proto.GameProto.LoginResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("您的账号在其他设备登录，连接断开")
                    .setUserId(0) // 此时ID已不重要
                    .setToken("")
                    .build();

            com.game.fwork.proto.GameProto.GameMessage message = com.game.fwork.proto.GameProto.GameMessage.newBuilder()
                    .setType(com.game.fwork.proto.GameProto.MessageType.LOGIN) // 复用类型
                    .setLoginResponse(response)
                    .build();

            // 发送并等待发送完毕（但不阻塞太久），然后再由外部关闭连接
            channel.writeAndFlush(message);
        } catch (Exception e) {
            logger.error("发送踢人消息失败", e);
        }
    }
}