package com.hmdp.util;

import com.hmdp.dto.ai.AiModerationCheckRequest;
import com.hmdp.dto.ai.AiModerationCheckResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ModerationRuleEngineTest {

    @Test
    void shouldMarkHighRiskForAds() {
        AiModerationCheckRequest request = new AiModerationCheckRequest();
        request.setType("blog");
        request.setContent("加微信领取福利，手机号13812345678");

        AiModerationCheckResponse response = ModerationRuleEngine.check(request);
        Assertions.assertEquals("high", response.getRiskLevel());
        Assertions.assertEquals("block", response.getSuggestedAction());
    }

    @Test
    void shouldMarkMediumRiskForLowQuality() {
        AiModerationCheckRequest request = new AiModerationCheckRequest();
        request.setType("comment");
        request.setContent("11111111");

        AiModerationCheckResponse response = ModerationRuleEngine.check(request);
        Assertions.assertEquals("medium", response.getRiskLevel());
        Assertions.assertEquals("review", response.getSuggestedAction());
    }
}
