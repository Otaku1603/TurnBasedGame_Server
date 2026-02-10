package com.game.fwork.netty;

import com.game.fwork.netty.handler.GameServerHandler;
import com.game.fwork.netty.handler.RealIpOverwriterHandler;
import com.game.fwork.proto.GameProto;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

/**
 * Netty 服务器启动类
 * 配置 TCP 参数、线程组（Boss/Worker）以及消息处理流水线（Pipeline）
 * Pipeline 顺序：心跳检测 -> Protobuf解码 -> Protobuf编码 -> 业务Handler
 */
@Component
public class NettyServer {

    private static final Logger logger = LoggerFactory.getLogger(NettyServer.class);

    @Value("${netty.server.port:9999}")
    private int port;

    @Value("${netty.server.boss-threads:1}")
    private int bossThreads;

    @Value("${netty.server.worker-threads:4}")
    private int workerThreads;

    @Value("${netty.server.heartbeat.reader-idle:10}")
    private int readerIdleTime;

    @Value("${netty.server.use-proxy-protocol:true}")
    private boolean useProxyProtocol;

    public static final AttributeKey<String> REAL_IP_KEY = AttributeKey.valueOf("REAL_IP");

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 服务器启动后自动执行
     */
    @PostConstruct
    public void start() {
        new Thread(() -> {
            try {
                bossGroup = new NioEventLoopGroup(bossThreads);
                workerGroup = new NioEventLoopGroup(workerThreads);

                ServerBootstrap bootstrap = new ServerBootstrap();
                bootstrap.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .option(ChannelOption.SO_BACKLOG, 128)
                        .childOption(ChannelOption.SO_KEEPALIVE, true)
                        .childOption(ChannelOption.TCP_NODELAY, true)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) throws Exception {
                                ChannelPipeline pipeline = ch.pipeline();

                                // 如果开启了代理模式，先解码 PROXY 协议头
                                if (useProxyProtocol) {
                                    // 官方解码器：把二进制头变成 Java 对象
                                    pipeline.addLast(new HAProxyMessageDecoder());

                                    // 我们的劫持处理器：把 Java 对象里的 IP 强行塞给 Channel
                                    pipeline.addLast(new RealIpOverwriterHandler());
                                }

                                // 心跳检测（一定时间内无读操作触发IdleStateEvent）
                                pipeline.addLast(new IdleStateHandler(
                                        readerIdleTime, 0, 0, TimeUnit.SECONDS
                                ));

                                // Protobuf解码器
                                pipeline.addLast(new ProtobufVarint32FrameDecoder());
                                pipeline.addLast(new ProtobufDecoder(
                                        GameProto.GameMessage.getDefaultInstance()
                                ));

                                // Protobuf编码器
                                pipeline.addLast(new ProtobufVarint32LengthFieldPrepender());
                                pipeline.addLast(new ProtobufEncoder());

                                // 业务处理器（从Spring容器获取新实例）
                                // 这样每个连接都有自己独立的Handler实例
                                pipeline.addLast(applicationContext.getBean(GameServerHandler.class));
                            }
                        });

                ChannelFuture future = bootstrap.bind(port).sync();
                serverChannel = future.channel();

                logger.info("========================================");
                logger.info("Netty服务器启动成功！");
                logger.info("监听端口: {}", port);
                logger.info("代理 Protocol: {}", useProxyProtocol ? "启用 (Nginx/HAProxy)" : "禁用 (直接连接)");
                logger.info("Boss线程数: {}", bossThreads);
                logger.info("Worker线程数: {}", workerThreads);
                logger.info("心跳超时: {}秒", readerIdleTime);
                logger.info("========================================");

                serverChannel.closeFuture().sync();

            } catch (InterruptedException e) {
                logger.error("Netty服务器启动失败", e);
                Thread.currentThread().interrupt();
            } finally {
                shutdown();
            }
        }, "NettyServerThread").start();
    }

    /**
     * 服务器关闭前自动执行
     */
    @PreDestroy
    public void shutdown() {
        logger.info("正在关闭Netty服务器...");

        if (serverChannel != null) {
            serverChannel.close();
        }

        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }

        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }

        logger.info("Netty服务器已关闭");
    }
}