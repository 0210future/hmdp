package com.hmdp.client;

import com.hmdp.dto.ai.AiModerationCheckRequest;
import com.hmdp.dto.ai.AiModerationCheckResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "hmdp-ai-service", url = "${AI_SERVICE_URL:http://127.0.0.1:8088}")
public interface AiClient {

    @PostMapping("/ai/moderation/check")
    AiModerationCheckResponse check(@RequestBody AiModerationCheckRequest request);
}
