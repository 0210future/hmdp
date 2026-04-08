package com.hmdp.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private Summary summary = new Summary();
    private Llm llm = new Llm();

    @Data
    public static class Summary {
        private boolean warmupEnabled = true;
        private long ttlMinutes = 720;
    }

    @Data
    public static class Llm {
        private boolean enabled = false;
        private String baseUrl = "https://api.openai.com";
        private String apiKey = "";
        private String model = "gpt-4o-mini";
        private int timeoutMillis = 5000;
        private int maxRetry = 1;
    }
}
