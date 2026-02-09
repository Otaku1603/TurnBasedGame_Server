package com.game.fwork.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 安全配置类
 * 仅提供基础的加解密组件
 */
@Configuration  // 表示这是一个配置类，Spring会在启动时加载
public class SecurityConfig {

    /**
     * 创建密码加密器Bean
     * @return BCryptPasswordEncoder实例
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
