package com.hmdp.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 模块配置。
 * 包含店铺摘要缓存策略，以及外部 LLM 接口的启用和超时参数。
 */
@Data
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private Summary summary = new Summary();
    private Llm llm = new Llm();

    /**
     * 摘要缓存配置。
     */
    @Data
    public static class Summary {
        /**
         * 服务启动后是否异步预热店铺摘要缓存。
         */
        private boolean warmupEnabled = true;

        /**
         * 店铺摘要缓存过期时间，单位：分钟。
         */
        private long ttlMinutes = 720;
    }

    /**
     * 外部 LLM 网关配置。
     */
    @Data
    public static class Llm {
        private boolean enabled = true;
        private String baseUrl = "https://api.scnet.cn/api/llm/v1";
        private String apiKey = "sk-MjQ5LTIxMjY1ODI4MjAxLTE3NzUyMjc5MTIzMTM=";
        private String model = "Qwen3-30B-A3B";
        private int timeoutMillis = 5000;
        private int maxRetry = 1;
    }
}
