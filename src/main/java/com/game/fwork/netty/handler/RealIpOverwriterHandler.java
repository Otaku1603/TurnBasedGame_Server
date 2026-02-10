package com.game.fwork.netty.handler;

import io.netty.channel.AbstractChannel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * 真实IP覆盖处理器
 * 利用反射强行修改 Netty Channel 内部的 remoteAddress 字段
 * 使得后续所有 channel.remoteAddress() 的调用直接返回真实用户 IP
 */
public class RealIpOverwriterHandler extends SimpleChannelInboundHandler<HAProxyMessage> {

    private static final Logger logger = LoggerFactory.getLogger(RealIpOverwriterHandler.class);
    private static Field remoteAddressField;

    static {
        try {
            // 通过反射获取 AbstractChannel 的 remoteAddress 字段
            // 这个字段存储了 Netty 认为的“远程地址”
            remoteAddressField = AbstractChannel.class.getDeclaredField("remoteAddress");
            remoteAddressField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            logger.error("无法获取 remoteAddress 字段，IP 劫持失败", e);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HAProxyMessage msg) throws Exception {
        if (msg.sourceAddress() != null) {
            try {
                // 1. 构造真实的 SocketAddress 对象
                // 使用 HAProxy 协议里解出来的 真实IP 和 真实端口
                SocketAddress realAddress = new InetSocketAddress(msg.sourceAddress(), msg.sourcePort());

                // 2. 替换 Channel 内部的地址
                if (remoteAddressField != null) {
                    remoteAddressField.set(ctx.channel(), realAddress);
                    logger.debug("IP 劫持成功: Nginx[{}] -> 真实IP[{}]", ctx.channel().remoteAddress(), realAddress);
                }
            } catch (Exception e) {
                logger.warn("设置真实IP失败", e);
            }
        }
        // Netty 会自动释放 msg 资源
    }
}