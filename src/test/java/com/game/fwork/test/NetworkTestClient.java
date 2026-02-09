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
import java.util.concurrent.atomic.AtomicLong;
import java.util.ArrayList;
import java.util.List;

/**
 * 网络功能测试客户端 (通用版)
 * 用于测试：心跳、延迟、断线重连底层机制
 */
public class NetworkTestClient {

    private volatile Channel channel;
    private String token;
    private Long userId;

    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicBoolean manualDisconnect = new AtomicBoolean(false);

    private ScheduledExecutorService heartbeatScheduler;
    private volatile long heartbeatInterval = 5000;
    private volatile long heartbeatTimeout = 30000;
    private volatile long lastHeartbeatResponseTime;
    private final AtomicInteger heartbeatCounter = new AtomicInteger(0);
    private final AtomicInteger heartbeatResponseCounter = new AtomicInteger(0);

    private final List<Long> latencySamples = new ArrayList<>();
    private final AtomicLong totalLatency = new AtomicLong(0);

    private EventLoopGroup group;

    public static void main(String[] args) {
        new NetworkTestClient().start();
    }

    public void start() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== 网络基建测试客户端 ===");
        System.out.print("请输入JWT Token: ");
        token = scanner.nextLine().trim();

        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
        connectToServer();

        try {
            Thread.sleep(1000);
            if (isConnected.get()) {
                sendLogin();
                Thread.sleep(1000);
                showMenu(scanner);
            } else {
                System.out.println("连接失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            shutdown();
        }
    }

    private void connectToServer() {
        try {
            if (group != null) group.shutdownGracefully();
            group = new NioEventLoopGroup();
            Bootstrap b = new Bootstrap();
            b.group(group).channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new ProtobufVarint32FrameDecoder())
                                    .addLast(new ProtobufDecoder(GameMessage.getDefaultInstance()))
                                    .addLast(new ProtobufVarint32LengthFieldPrepender())
                                    .addLast(new ProtobufEncoder())
                                    .addLast(new NetworkHandler());
                        }
                    });

            ChannelFuture f = b.connect("localhost", 9999);
            f.addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    channel = future.channel();
                    isConnected.set(true);
                    manualDisconnect.set(false);
                    lastHeartbeatResponseTime = System.currentTimeMillis();
                    System.out.println("✓ 连接成功");
                    startHeartbeat();
                } else {
                    System.out.println("✗ 连接失败");
                }
            });
            f.sync();
        } catch (Exception e) {
            System.out.println("连接异常: " + e.getMessage());
        }
    }

    private void startHeartbeat() {
        if (heartbeatScheduler != null) heartbeatScheduler.shutdown();
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (isConnected.get() && channel != null && channel.isActive() && !manualDisconnect.get()) {
                long now = System.currentTimeMillis();
                if (now - lastHeartbeatResponseTime > heartbeatTimeout) {
                    System.out.println("⚠ 心跳超时，尝试重连...");
                    isConnected.set(false);
                    connectToServer();
                } else {
                    sendHeartbeat();
                }
            }
        }, 1000, heartbeatInterval, TimeUnit.MILLISECONDS);
    }

    private void sendLogin() {
        channel.writeAndFlush(GameMessage.newBuilder().setType(MessageType.LOGIN).setToken(token).build());
    }

    private void sendHeartbeat() {
        heartbeatCounter.incrementAndGet();
        channel.writeAndFlush(GameMessage.newBuilder().setType(MessageType.HEARTBEAT)
                .setHeartbeat(Heartbeat.newBuilder().setTimestamp(System.currentTimeMillis()).build()).build());
    }

    private void showMenu(Scanner scanner) {
        while (true) {
            System.out.println("\n1. 测试单次延迟");
            System.out.println("2. 模拟断线");
            System.out.println("3. 模拟重连");
            System.out.println("0. 退出");
            System.out.print("> ");
            String op = scanner.nextLine().trim();
            if ("0".equals(op)) break;
            if ("1".equals(op)) sendHeartbeat();
            if ("2".equals(op)) {
                if (channel != null) channel.close();
                isConnected.set(false);
                manualDisconnect.set(true);
                System.out.println("已断开");
            }
            if ("3".equals(op)) {
                manualDisconnect.set(false);
                connectToServer();
            }
        }
    }

    private void shutdown() {
        if (heartbeatScheduler != null) heartbeatScheduler.shutdownNow();
        if (group != null) group.shutdownGracefully();
    }

    private class NetworkHandler extends SimpleChannelInboundHandler<GameMessage> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, GameMessage msg) {
            if (msg.getType() == MessageType.HEARTBEAT && msg.hasHeartbeatResponse()) {
                long latency = System.currentTimeMillis() - msg.getHeartbeatResponse().getTimestamp();
                lastHeartbeatResponseTime = System.currentTimeMillis();
                System.out.println("❤ 延迟: " + latency + "ms");
            } else if (msg.getType() == MessageType.LOGIN) {
                System.out.println("登录结果: " + msg.getLoginResponse().getSuccess());
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (!manualDisconnect.get()) System.out.println("连接意外断开");
        }
    }
}