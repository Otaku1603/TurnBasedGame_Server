package com.game.fwork.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 游戏核心数值配置
 * 对应 application.properties 中 game.* 开头的配置
 */
@Component
@ConfigurationProperties(prefix = "game")
@Getter @Setter
public class GameConfig {

    // ELO配置
    private Elo elo = new Elo();

    // 静态内部类：ELO
    @Getter @Setter
    public static class Elo {
        private Integer kFactor; // K值权重
    }
}