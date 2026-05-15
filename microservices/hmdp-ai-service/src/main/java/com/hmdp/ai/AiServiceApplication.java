package com.hmdp.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI 微服务启动类。
 * 开启异步能力、Feign 调用和配置绑定，用于承载 AI 相关聚合与辅助逻辑。
 */
@EnableAsync
@EnableScheduling
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.hmdp.client")
@EnableConfigurationProperties(AiProperties.class)
@SpringBootApplication(scanBasePackages = "com.hmdp")
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
