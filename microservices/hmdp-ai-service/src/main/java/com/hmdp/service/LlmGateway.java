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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 统一封装外部大模型调用。
 * 业务层只关心 prompt 输入和文本输出，重试、超时、简单熔断和响应解析都在这里处理。
 */
@Slf4j
@Component
public class LlmGateway {

    /**
     * 当外部模型超时或配置错误时，短时间内直接跳过远程调用，避免每次请求都卡住。
     */
    private static final long UNAVAILABLE_COOLDOWN_MILLIS = 2 * 60 * 1000L;

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private AiProperties aiProperties;

    private volatile long unavailableUntil = 0L;

    /**
     * 发起一次文本补全请求。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return 模型输出内容；当未启用、请求失败或内容为空时返回 empty
     */
    public Optional<String> complete(String systemPrompt, String userPrompt) {
        if (!aiProperties.getLlm().isEnabled() || StrUtil.hasBlank(userPrompt, aiProperties.getLlm().getApiKey())) {
            return Optional.empty();
        }
        if (isUnavailable()) {
            return Optional.empty();
        }

        String url = buildCompletionUrl(aiProperties.getLlm().getBaseUrl());
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
                    unavailableUntil = 0L;
                    return Optional.of(((String) content).trim());
                }
            } catch (HttpClientErrorException e) {
                markUnavailable("client-error", url, e);
                return Optional.empty();
            } catch (ResourceAccessException e) {
                markUnavailable("network-timeout", url, e);
                return Optional.empty();
            } catch (Exception e) {
                log.warn("LLM completion failed on attempt {}, url={}", i + 1, url, e);
            }
        }
        return Optional.empty();
    }

    /**
     * 兼容不同风格的 OpenAI 兼容网关配置：
     * 1. baseUrl = https://host/api/llm
     * 2. baseUrl = https://host/api/llm/v1
     * 3. baseUrl = https://host/api/llm/v1/chat/completions
     */
    private String buildCompletionUrl(String baseUrl) {
        String normalized = StrUtil.removeSuffix(StrUtil.blankToDefault(baseUrl, ""), "/");
        if (StrUtil.isBlank(normalized)) {
            return "/v1/chat/completions";
        }
        if (StrUtil.endWithIgnoreCase(normalized, "/chat/completions")) {
            return normalized;
        }
        if (StrUtil.endWithIgnoreCase(normalized, "/v1")) {
            return normalized + "/chat/completions";
        }
        return normalized + "/v1/chat/completions";
    }

    /**
     * 判断当前是否处于短暂熔断窗口内。
     */
    private boolean isUnavailable() {
        return unavailableUntil > System.currentTimeMillis();
    }

    /**
     * 记录外部模型暂不可用，避免每个请求都重复等待超时。
     */
    private void markUnavailable(String reason, String url, Exception e) {
        unavailableUntil = System.currentTimeMillis() + UNAVAILABLE_COOLDOWN_MILLIS;
        log.warn("LLM temporarily unavailable, reason={}, cooldownMs={}, url={}",
                reason, UNAVAILABLE_COOLDOWN_MILLIS, url, e);
    }

    /**
     * 组装 OpenAI 兼容格式的消息体。
     */
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
