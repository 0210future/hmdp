package com.hmdp.config;

import com.hmdp.ai.AiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;

@Configuration
public class AiConfig {

    @Bean
    public RestTemplate restTemplate(AiProperties aiProperties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(aiProperties.getLlm().getTimeoutMillis());
        factory.setReadTimeout(aiProperties.getLlm().getTimeoutMillis());
        return new RestTemplate(factory);
    }

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
