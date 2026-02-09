package com.game.fwork.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Netty 服务器参数配置
 * 映射 application.properties 中 netty.server 开头的配置项
 * 包含端口、IO线程数、心跳检测时间等关键参数
 */
@Component
@ConfigurationProperties(prefix = "netty.server")
@Getter @Setter
public class NettyConfig {

    private Integer port;
    private Integer bossThreads;
    private Integer workerThreads;
    private Heartbeat heartbeat = new Heartbeat();

    @Getter @Setter
    public static class Heartbeat {
        private Integer readerIdle;
        private Integer writerIdle;
        private Integer allIdle;
    }
}