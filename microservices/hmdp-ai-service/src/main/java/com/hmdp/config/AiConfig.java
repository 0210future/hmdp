package com.hmdp.config;

import com.hmdp.ai.AiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;

/**
 * AI 模块基础配置，负责组装 LLM 调用客户端和异步线程池。
 */
@Configuration
public class AiConfig {

    /**
     * 为外部 LLM 调用提供统一超时控制。
     */
    @Bean
    public RestTemplate restTemplate(AiProperties aiProperties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(aiProperties.getLlm().getTimeoutMillis());
        factory.setReadTimeout(aiProperties.getLlm().getTimeoutMillis());
        return new RestTemplate(factory);
    }

    /**
     * AI 异步任务线程池，主要用于摘要预热等后台任务。
     */
    @Bean("aiExecutor")
    public Executor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-worker-");
        executor.initialize();
        return executor;
    }
}
