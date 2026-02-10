package com.game.fwork.netty.handler;

import com.game.fwork.entity.Battle;
import com.game.fwork.entity.User;
import com.game.fwork.enums.BattleState;
import com.game.fwork.manager.BattleManager;
import com.game.fwork.netty.session.SessionManager;
import com.game.fwork.proto.GameProto.*;
import com.game.fwork.repository.UserRepository;
import com.game.fwork.service.BattleService;
import com.game.fwork.service.MatchService;
import com.game.fwork.util.JwtUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.SocketException;

/**
 * 游戏业务消息处理器
 * Netty 接收到数据包后的统一入口
 * 职责：
 * 1. 消息路由：根据 MessageType 分发到不同的 Service 处理
 * 2. 连接管理：处理握手登录、心跳响应和断开连接事件
 */
@Component
@Scope("prototype") // 必须是 prototype，因为 Netty 为每个连接创建新 Handler
public class GameServerHandler extends SimpleChannelInboundHandler<GameMessage> {

    private static final Logger logger = LoggerFactory.getLogger(GameServerHandler.class);

    @Autowired private SessionManager sessionManager;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private MatchService matchService;
    @Autowired private BattleService battleService;
    @Autowired private BattleManager battleManager;
    @Autowired private UserRepository userRepository;

    /**
     * 消息分发核心方法
     * 所有的业务请求（登录、匹配、战斗操作）都在这里进行 Switch 路由
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, GameMessage msg) throws Exception {
        logger.debug("收到消息: type={}, channel={}", msg.getType(), ctx.channel().remoteAddress());

        switch (msg.getType()) {
            case LOGIN -> handleLogin(ctx, msg);
            case HEARTBEAT -> handleHeartbeat(ctx, msg);
            case MATCH_REQUEST -> handleMatchRequest(ctx, msg);
            case MATCH_CANCEL -> handleMatchCancel(ctx, msg);
            case BATTLE_READY -> handleBattleReady(ctx, msg);
            case BATTLE_ACTION -> handleBattleAction(ctx, msg);
            case BATTLE_SURRENDER -> handleBattleSurrender(ctx, msg);
            case BATTLE_REJOIN -> handleBattleRejoin(ctx, msg);
            default -> logger.warn("未知消息类型: type={}", msg.getType());
        }
    }

    /**
     * 处理登录请求
     * 校验 Token 合法性，建立 UserId 与 Channel 的绑定关系
     */
    private void handleLogin(ChannelHandlerContext ctx, GameMessage msg) {
        String token = msg.getToken();

        try {
            if (!jwtUtil.validateToken(token)) {
                logger.error("Token验证失败: token={}", token);
                sendErrorAndClose(ctx, "Token无效或已过期");
                return;
            }

            Long userId = jwtUtil.getUserIdFromToken(token);

            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                logger.error("用户不存在: userId={}", userId);
                sendErrorAndClose(ctx, "用户不存在");
                return;
            }

            if (user.getStatus() == 0) {
                logger.warn("封禁用户尝试登录: userId={}, username={}",
                        userId, user.getUsername());
                sendErrorAndClose(ctx, "账号已被封禁，请联系管理员");
                return;
            }

            sessionManager.addSession(userId, ctx.channel());

            if (battleManager.isPlayerDisconnected(userId)) {
                battleManager.clearPlayerDisconnected(userId);
                logger.info("玩家重连，清除断线标记: userId={}", userId);
            }

            logger.info("玩家登录成功: userId={}, username={}, IP={}",
                    userId, user.getUsername(), ctx.channel().remoteAddress());

            LoginResponse response = LoginResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("登录成功")
                    .setUserId(userId)
                    .build();

            ctx.writeAndFlush(GameMessage.newBuilder()
                    .setType(MessageType.LOGIN)
                    .setLoginResponse(response)
                    .build());

        } catch (Exception e) {
            logger.error("登录处理失败", e);
            ctx.close();
        }
    }

    /**
     * 发送错误消息并关闭连接
     */
    private void sendErrorAndClose(ChannelHandlerContext ctx, String errorMsg) {
        LoginResponse response = LoginResponse.newBuilder()
                .setSuccess(false)
                .setMessage(errorMsg)
                .build();

        ctx.writeAndFlush(GameMessage.newBuilder()
                .setType(MessageType.LOGIN)
                .setLoginResponse(response)
                .build()).addListener(future -> ctx.close());
    }

    /**
     * 处理心跳（接收并回应）
     */
    private void handleHeartbeat(ChannelHandlerContext ctx, GameMessage msg) {
        logger.debug("收到心跳: channel={}", ctx.channel().remoteAddress());

        // 构建心跳响应
        HeartbeatResponse response = HeartbeatResponse.newBuilder()
                .setTimestamp(System.currentTimeMillis())  // 服务端当前时间戳
                .build();

        GameMessage heartbeatResponse = GameMessage.newBuilder()
                .setType(MessageType.HEARTBEAT)
                .setHeartbeatResponse(response)
                .build();

        // 回应心跳包给客户端
        ctx.writeAndFlush(heartbeatResponse);

        logger.debug("心跳回应已发送: channel={}", ctx.channel().remoteAddress());
    }
    /**
     * 处理匹配请求
     */
    private void handleMatchRequest(ChannelHandlerContext ctx, GameMessage msg) {
        Long userId = msg.getMatchRequest().getUserId();
        try {
            boolean success = matchService.joinQueue(userId);
            logger.info("玩家请求匹配: userId={}, success={}", userId, success);
        } catch (Exception e) {
            logger.error("匹配请求处理失败: userId={}", userId, e);
        }
    }

    /**
     * 处理取消匹配
     */
    private void handleMatchCancel(ChannelHandlerContext ctx, GameMessage msg) {
        String token = msg.getToken();
        try {
            Long userId = jwtUtil.getUserIdFromToken(token);
            boolean success = matchService.leaveQueue(userId);
            logger.info("玩家取消匹配: userId={}, success={}", userId, success);
        } catch (Exception e) {
            logger.error("取消匹配失败", e);
        }
    }

    /**
     * 处理战斗准备
     */
    private void handleBattleReady(ChannelHandlerContext ctx, GameMessage msg) {
        BattleReadyRequest request = msg.getBattleReadyRequest();
        try {
            battleService.playerReady(request.getBattleId(), request.getUserId());
            logger.info("玩家战斗准备: userId={}, battleId={}",
                    request.getUserId(), request.getBattleId());
        } catch (Exception e) {
            logger.error("战斗准备处理失败", e);
        }
    }

    /**
     * 处理战斗操作
     */
    private void handleBattleAction(ChannelHandlerContext ctx, GameMessage msg) {
        BattleActionRequest request = msg.getBattleActionRequest();
        try {
            // 调用 BattleService 的新接口 (4个参数)
            battleService.handleBattleAction(
                    request.getBattleId(),
                    request.getUserId(),
                    request.getActionType(),
                    request.getParamId()
            );
            logger.info("玩家战斗操作: userId={}, type={}, param={}",
                    request.getUserId(), request.getActionType(), request.getParamId());
        } catch (Exception e) {
            logger.error("战斗操作处理失败", e);
        }
    }

    /**
     * 处理投降
     */
    private void handleBattleSurrender(ChannelHandlerContext ctx, GameMessage msg) {
        BattleSurrenderRequest request = msg.getBattleSurrenderRequest();
        try {
            battleService.surrender(request.getBattleId(), request.getUserId());
            logger.info("玩家投降: userId={}, battleId={}",
                    request.getUserId(), request.getBattleId());
        } catch (Exception e) {
            logger.error("投降处理失败", e);
        }
    }

    /**
     * 处理断线重连
     */
    private void handleBattleRejoin(ChannelHandlerContext ctx, GameMessage msg) {
        Long userId = msg.getBattleRejoinRequest().getUserId();
        try {
            GameMessage response = battleService.handleRejoin(userId);
            ctx.writeAndFlush(response);
            logger.info("断线重连处理完成: userId={}", userId);
        } catch (Exception e) {
            logger.error("断线重连处理失败: userId={}", userId, e);
        }
    }

    /**
     * 连接断开处理
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Long userId = sessionManager.getUserIdByChannel(ctx.channel());

        if (userId != null) {
            Battle battle = battleManager.getBattleByUserId(userId);

            if (battle != null && battle.getState() == BattleState.FIGHTING) {
                battleManager.markPlayerDisconnected(userId);
                logger.warn("玩家在战斗中断线，保留战斗状态30秒: userId={}, battleId={}",
                        userId, battle.getBattleId());
            } else {
                logger.info("玩家断开连接（不在战斗中）: userId={}", userId);
            }
        }

        sessionManager.removeSession(ctx.channel());
        logger.info("连接断开: channel={}", ctx.channel().remoteAddress());
        super.channelInactive(ctx);
    }

    /**
     * 心跳超时处理
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent event) {
            if (event.state() == IdleState.READER_IDLE) {
                Long userId = sessionManager.getUserIdByChannel(ctx.channel());

                if (userId != null) {
                    Battle battle = battleManager.getBattleByUserId(userId);

                    if (battle != null && battle.getState() == BattleState.FIGHTING) {
                        battleManager.markPlayerDisconnected(userId);
                        logger.warn("玩家心跳超时（在战斗中），保留战斗状态30秒: userId={}, battleId={}",
                                userId, battle.getBattleId());
                    } else {
                        logger.warn("玩家心跳超时（不在战斗中），关闭连接: userId={}", userId);
                    }
                }

                ctx.close();
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    /**
     * 异常捕获
     * 区分“正常断开”（如网络波动）和“异常报错”，避免日志刷屏
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (isNormalDisconnectException(cause)) {
            // 正常断线，使用INFO级别
            logger.info("连接断开（正常）: channel={}, reason={}",
                    ctx.channel().remoteAddress(),
                    cause.getClass().getSimpleName() + ": " + cause.getMessage());
        } else {
            // 真正的异常，使用ERROR级别
            logger.error("连接异常（非正常）: channel={}",
                    ctx.channel().remoteAddress(), cause);
        }

        ctx.close();
    }

    /**
     * 判断是否为常规断开引起的异常 (如 Connection reset)
     *
     * @param cause 异常对象
     * @return true=正常断线, false=真正的异常
     */
    private boolean isNormalDisconnectException(Throwable cause) {
        // 检查异常类型
        if (cause instanceof SocketException) {
            String msg = cause.getMessage();
            return msg != null && (
                    msg.contains("Connection reset") ||
                            msg.contains("Connection timed out") ||
                            msg.contains("Broken pipe")
            );
        }
        if (cause instanceof IOException) {
            String msg = cause.getMessage();
            return msg != null && (
                    msg.contains("远程主机强迫关闭了一个现有的连接") || // Windows
                            msg.contains("Connection reset by peer") || // Linux
                            msg.contains("Broken pipe")
            );
        }

        return false;
    }
}