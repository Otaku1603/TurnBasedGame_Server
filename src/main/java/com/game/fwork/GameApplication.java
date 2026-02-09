package com.game.fwork;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 游戏服务器启动入口
 */
@SpringBootApplication
@EnableAsync    // 启用异步任务（用于战斗结算落库）
@EnableScheduling   // 启用定时任务（用于匹配队列轮询与超时检测）
public class GameApplication {
    public static void main(String[] args) {
        SpringApplication.run(GameApplication.class, args);

        System.out.println("===========================================");
        System.out.println("    游戏服务器启动成功！                  ");
        System.out.println("    HTTP接口端口: 8080                      ");
        System.out.println("    Netty服务端口: 9999                   ");
        System.out.println("===========================================");
    }
}