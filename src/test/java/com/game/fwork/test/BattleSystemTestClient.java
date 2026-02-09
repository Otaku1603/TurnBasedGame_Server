package com.game.fwork.test;

import com.game.fwork.proto.GameProto.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;

import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 战斗系统测试客户端 (适配 MVP 1.2 新协议)
 * 支持：技能、防御、道具操作
 */
public class BattleSystemTestClient {

    private volatile Channel channel;
    private String token;
    private Long userId;
    private String battleId;

    // 连接状态管理
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicBoolean isInBattle = new AtomicBoolean(false);
    private final AtomicBoolean manualDisconnect = new AtomicBoolean(false);

    // 基础心跳
    private ScheduledExecutorService heartbeatScheduler;
    private static final long HEARTBEAT_INTERVAL = 5000;
    private final AtomicInteger heartbeatCounter = new AtomicInteger(0);

    // Netty 线程组
    private EventLoopGroup group;

    // 客户端状态
    private enum ClientState {
        DISCONNECTED, CONNECTED, LOGGED_IN,
        MATCHING, IN_BATTLE, RECONNECTING
    }

    private volatile ClientState currentState = ClientState.DISCONNECTED;

    public static void main(String[] args) {
        BattleSystemTestClient client = new BattleSystemTestClient();
        client.start();
    }

    public void start() {
        Scanner scanner = new Scanner(System.in);

        System.out.println("========================================");
        System.out.println("战斗系统测试客户端 v1.2 (Data-Driven)");
        System.out.println("========================================");

        // 1. 输入Token
        System.out.print("请输入JWT Token: ");
        token = scanner.nextLine().trim();

        // 2. 初始化心跳调度器
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();

        // 3. 连接服务器
        connectToServer();

        try {
            Thread.sleep(1000);

            if (!isConnected.get()) {
                System.out.println("✗ 连接服务器失败");
                return;
            }

            // 4. 自动登录
            sendLogin();

            Thread.sleep(1000);

            // 5. 交互式菜单
            showMenu(scanner);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            shutdown();
        }
    }

    private void connectToServer() {
        try {
            if (group != null) {
                group.shutdownGracefully();
            }

            group = new NioEventLoopGroup();
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new ProtobufVarint32FrameDecoder())
                                    .addLast(new ProtobufDecoder(GameMessage.getDefaultInstance()))
                                    .addLast(new ProtobufVarint32LengthFieldPrepender())
                                    .addLast(new ProtobufEncoder())
                                    .addLast(new BattleSystemHandler()); // 注册Handler
                        }
                    });

            System.out.println("正在连接服务器 localhost:9999 ...");
            ChannelFuture future = bootstrap.connect("localhost", 9999);

            future.addListener((ChannelFutureListener) f -> {
                if (f.isSuccess()) {
                    channel = f.channel();
                    isConnected.set(true);
                    manualDisconnect.set(false);
                    currentState = ClientState.CONNECTED;
                    System.out.println("✓ 已连接到服务器");
                    startHeartbeat();
                } else {
                    System.out.println("✗ 连接服务器失败: " + f.cause().getMessage());
                    currentState = ClientState.DISCONNECTED;
                }
            });

            future.sync();

        } catch (Exception e) {
            System.out.println("连接异常: " + e.getMessage());
            currentState = ClientState.DISCONNECTED;
        }
    }

    private void startHeartbeat() {
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdown();
            heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
        }

        heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                if (!isConnected.get() || channel == null || !channel.isActive() || manualDisconnect.get()) {
                    return;
                }
                sendHeartbeat();
            } catch (Exception e) {
                System.err.println("[心跳] 异常: " + e.getMessage());
            }
        }, 1000, HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);
    }

    // === 发送消息方法 ===

    private void sendLogin() {
        GameMessage msg = GameMessage.newBuilder()
                .setType(MessageType.LOGIN)
                .setToken(token)
                .build();
        channel.writeAndFlush(msg);
        System.out.println("→ 发送登录消息");
    }

    private void sendHeartbeat() {
        heartbeatCounter.incrementAndGet();
        GameMessage msg = GameMessage.newBuilder()
                .setType(MessageType.HEARTBEAT)
                .setHeartbeat(Heartbeat.newBuilder().setTimestamp(System.currentTimeMillis()).build())
                .build();
        channel.writeAndFlush(msg);
    }

    private void sendMatchRequest() {
        if (currentState != ClientState.LOGGED_IN) {
            System.out.println("✗ 请先登录");
            return;
        }
        currentState = ClientState.MATCHING;
        GameMessage msg = GameMessage.newBuilder()
                .setType(MessageType.MATCH_REQUEST)
                .setMatchRequest(MatchRequest.newBuilder().setUserId(userId).build())
                .build();
        channel.writeAndFlush(msg);
        System.out.println("→ 发送匹配请求");
    }

    private void sendBattleReady() {
        if (battleId == null) {
            System.out.println("✗ 未匹配");
            return;
        }
        GameMessage msg = GameMessage.newBuilder()
                .setType(MessageType.BATTLE_READY)
                .setBattleReadyRequest(BattleReadyRequest.newBuilder().setBattleId(battleId).setUserId(userId).build())
                .build();
        channel.writeAndFlush(msg);
        System.out.println("→ 发送战斗准备");
    }

    private void sendBattleAction(Scanner scanner) {
        if (!isInBattle.get()) {
            System.out.println("✗ 战斗未开始");
            return;
        }

        System.out.println("\n=== 选择操作 ===");
        System.out.println("1. 释放技能");
        System.out.println("2. 防御 (本回合减伤)");
        System.out.println("3. 使用道具");
        System.out.print("请选择 > ");

        try {
            int typeChoice = Integer.parseInt(scanner.nextLine().trim());
            int actionType;
            int paramId = 0;

            switch (typeChoice) {
                case 1: // 技能
                    actionType = 1;
                    System.out.println("技能列表(DB): 1=普攻, 2=重击, 3=治疗");
                    System.out.print("输入技能ID > ");
                    paramId = Integer.parseInt(scanner.nextLine().trim());
                    break;
                case 2: // 防御
                    actionType = 2;
                    System.out.println("即将进入防御状态...");
                    paramId = 0; // 防御无需参数
                    break;
                case 3: // 道具
                    actionType = 3;
                    System.out.println("道具列表(DB): 1=生命药水(50), 2=强力药水(100)");
                    System.out.print("输入道具ID > ");
                    paramId = Integer.parseInt(scanner.nextLine().trim());
                    break;
                default:
                    System.out.println("无效选择");
                    return;
            }

            // 构建新协议的请求
            BattleActionRequest request = BattleActionRequest.newBuilder()
                    .setBattleId(battleId)
                    .setUserId(userId)
                    .setActionType(actionType)
                    .setParamId(paramId)
                    .build();

            GameMessage msg = GameMessage.newBuilder()
                    .setType(MessageType.BATTLE_ACTION)
                    .setBattleActionRequest(request)
                    .build();

            channel.writeAndFlush(msg);
            System.out.printf("→ 发送操作: Type=%d, Param=%d%n", actionType, paramId);

        } catch (NumberFormatException e) {
            System.out.println("输入格式错误");
        }
    }

    private void sendSurrender() {
        if (!isInBattle.get()) return;
        GameMessage msg = GameMessage.newBuilder()
                .setType(MessageType.BATTLE_SURRENDER)
                .setBattleSurrenderRequest(BattleSurrenderRequest.newBuilder().setBattleId(battleId).setUserId(userId).build())
                .build();
        channel.writeAndFlush(msg);
        System.out.println("→ 投降");
    }

    private void sendRejoin() {
        GameMessage msg = GameMessage.newBuilder()
                .setType(MessageType.BATTLE_REJOIN)
                .setBattleRejoinRequest(BattleRejoinRequest.newBuilder().setUserId(userId).build())
                .build();
        channel.writeAndFlush(msg);
        System.out.println("→ 发送重连请求");
    }

    // === 菜单与辅助 ===

    private void showMenu(Scanner scanner) {
        while (true) {
            System.out.println("\n========== 菜单 ==========");
            System.out.println("状态: " + currentState + (battleId != null ? " | BattleID: " + battleId : ""));
            System.out.println("1. 请求匹配");
            System.out.println("2. 战斗准备");
            System.out.println("3. 战斗行动 (技能/防御/道具)");
            System.out.println("4. 投降");
            System.out.println("5. 断线重连");
            System.out.println("6. 模拟断网");
            System.out.println("7. 模拟恢复网络");
            System.out.println("0. 退出");
            System.out.println("==========================");
            System.out.print("> ");

            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1": sendMatchRequest(); break;
                case "2": sendBattleReady(); break;
                case "3": sendBattleAction(scanner); break;
                case "4": sendSurrender(); break;
                case "5": sendRejoin(); break;
                case "6": simulateDisconnect(); break;
                case "7": simulateReconnect(); break;
                case "0": shutdown(); return;
                default: System.out.println("无效指令");
            }

            try { Thread.sleep(200); } catch (Exception e) {}
        }
    }

    private void simulateDisconnect() {
        if (channel != null) channel.close();
        isConnected.set(false);
        currentState = ClientState.DISCONNECTED;
        System.out.println("⚠ 已断开连接");
    }

    private void simulateReconnect() {
        manualDisconnect.set(false);
        connectToServer();
        try { Thread.sleep(1000); } catch (Exception e) {}
        if (isConnected.get()) sendLogin();
    }

    private void shutdown() {
        if (heartbeatScheduler != null) heartbeatScheduler.shutdownNow();
        if (channel != null) channel.close();
        if (group != null) group.shutdownGracefully();
        System.out.println("客户端退出");
    }

    /**
     * 内部Handler类
     */
    private class BattleSystemHandler extends SimpleChannelInboundHandler<GameMessage> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, GameMessage msg) {
            switch (msg.getType()) {
                case LOGIN:
                    if (msg.getLoginResponse().getSuccess()) {
                        userId = msg.getLoginResponse().getUserId();
                        currentState = ClientState.LOGGED_IN;
                        System.out.println("✓ 登录成功, ID: " + userId);
                    } else {
                        System.out.println("✗ 登录失败: " + msg.getLoginResponse().getMessage());
                    }
                    break;
                case MATCH_SUCCESS:
                    battleId = msg.getMatchSuccessResponse().getBattleId();
                    System.out.println("★ 匹配成功! 对手: " + msg.getMatchSuccessResponse().getOpponent().getNickname());
                    System.out.println("请按 2 发送准备");
                    break;
                case BATTLE_START:
                    isInBattle.set(true);
                    currentState = ClientState.IN_BATTLE;
                    System.out.println("⚔ 战斗开始!");
                    System.out.println("先手ID: " + msg.getBattleStartResponse().getCurrentActorUserId());
                    if (msg.getBattleStartResponse().getCurrentActorUserId() == userId) {
                        System.out.println("👉 轮到你了！请按 3 行动");
                    }
                    break;
                case BATTLE_UPDATE:
                    BattleUpdateResponse update = msg.getBattleUpdateResponse();
                    System.out.println("\n>>> 回合 " + update.getCurrentRound() + " <<<");
                    System.out.println(update.getDescription());
                    System.out.printf("P1血量: %d/%d, P2血量: %d/%d%n",
                            update.getPlayer1().getCurrentHp(), update.getPlayer1().getMaxHp(),
                            update.getPlayer2().getCurrentHp(), update.getPlayer2().getMaxHp());

                    if (update.getNextActorUserId() == userId) {
                        System.out.println("👉 轮到你了！请按 3 行动");
                    } else {
                        System.out.println("⏳ 等待对手行动...");
                    }
                    break;
                case BATTLE_END:
                    isInBattle.set(false);
                    currentState = ClientState.LOGGED_IN;
                    battleId = null;
                    System.out.println("\n🏁 战斗结束");
                    System.out.println("原因: " + msg.getBattleEndResponse().getEndReason());
                    System.out.println("赢家ID: " + msg.getBattleEndResponse().getWinnerId());
                    break;
                case BATTLE_REJOIN_RESPONSE:
                    if (msg.getBattleRejoinResponse().getSuccess()) {
                        System.out.println("✓ 重连成功，恢复战斗状态");
                        battleId = msg.getBattleRejoinResponse().getBattleId();
                        isInBattle.set(true);
                        currentState = ClientState.IN_BATTLE;
                    } else {
                        System.out.println("✗ 重连失败: " + msg.getBattleRejoinResponse().getMessage());
                    }
                    break;
                case HEARTBEAT:
                    // 忽略心跳回包日志，避免刷屏
                    break;
                default:
                    System.out.println("收到未知消息: " + msg.getType());
            }
        }
    }
}