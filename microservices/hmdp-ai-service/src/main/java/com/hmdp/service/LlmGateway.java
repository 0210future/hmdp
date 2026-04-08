package com.hmdp.service;

import cn.hutool.core.util.StrUtil;
import com.hmdp.ai.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class LlmGateway {

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private AiProperties aiProperties;

    public Optional<String> complete(String systemPrompt, String userPrompt) {
        if (!aiProperties.getLlm().isEnabled() || StrUtil.hasBlank(userPrompt, aiProperties.getLlm().getApiKey())) {
            return Optional.empty();
        }
        String url = StrUtil.removeSuffix(aiProperties.getLlm().getBaseUrl(), "/") + "/v1/chat/completions";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(aiProperties.getLlm().getApiKey());

        Map<String, Object> body = new HashMap<String, Object>();
        body.put("model", aiProperties.getLlm().getModel());
        body.put("temperature", 0.4);
        body.put("max_tokens", 600);
        body.put("messages", buildMessages(systemPrompt, userPrompt));

        int attempts = Math.max(1, aiProperties.getLlm().getMaxRetry() + 1);
        for (int i = 0; i < attempts; i++) {
            try {
                ResponseEntity<Map> response = restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        new HttpEntity<Map<String, Object>>(body, headers),
                        Map.class
                );
                Object choicesObj = response.getBody() == null ? null : response.getBody().get("choices");
                if (!(choicesObj instanceof List)) {
                    continue;
                }
                List choices = (List) choicesObj;
                if (choices.isEmpty() || !(choices.get(0) instanceof Map)) {
                    continue;
                }
                Object messageObj = ((Map) choices.get(0)).get("message");
                if (!(messageObj instanceof Map)) {
                    continue;
                }
                Object content = ((Map) messageObj).get("content");
                if (content instanceof String && StrUtil.isNotBlank((String) content)) {
                    return Optional.of(((String) content).trim());
                }
            } catch (Exception e) {
                log.warn("LLM completion failed on attempt {}", i + 1, e);
            }
        }
        return Optional.empty();
    }

    private List<Map<String, String>> buildMessages(String systemPrompt, String userPrompt) {
        List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
        if (StrUtil.isNotBlank(systemPrompt)) {
            Map<String, String> system = new HashMap<String, String>();
            system.put("role", "system");
            system.put("content", systemPrompt);
            messages.add(system);
        }
        Map<String, String> user = new HashMap<String, String>();
        user.put("role", "user");
        user.put("content", userPrompt);
        messages.add(user);
        return messages;
    }
}
