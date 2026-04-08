package com.hmdp.util;

import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.ai.AiModerationCheckRequest;
import com.hmdp.dto.ai.AiModerationCheckResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ModerationRuleEngine {

    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern REPEAT_PATTERN = Pattern.compile("(.)\\1{5,}");

    private ModerationRuleEngine() {
    }

    public static AiModerationCheckResponse check(AiModerationCheckRequest request) {
        AiModerationCheckResponse response = new AiModerationCheckResponse();
        List<String> labels = new ArrayList<String>();
        String content = StrUtil.nullToEmpty(request == null ? null : request.getContent());
        String normalized = content.toLowerCase();

        if (containsAny(normalized, "vx", "微信", "加v", "加微", "兼职", "刷单", "代写", "引流", "代理", "返利", "赌博", "裸聊")) {
            labels.add("广告导流");
        }
        if (containsAny(normalized, "傻逼", "垃圾", "滚", "死全家", "废物", "脑残")) {
            labels.add("辱骂攻击");
        }
        if (containsAny(normalized, "测试测试", "111111", "aaaaaa") || REPEAT_PATTERN.matcher(normalized).find()) {
            labels.add("灌水重复");
        }
        if (content.trim().length() < 8) {
            labels.add("低信息量");
        }
        if (PHONE_PATTERN.matcher(normalized).find()) {
            labels.add("联系方式");
        }

        if (labels.contains("广告导流") || labels.contains("联系方式")) {
            fill(response, false, "high", labels, "内容包含导流或联系方式，建议直接拦截", "block");
            return response;
        }
        if (labels.contains("辱骂攻击") || labels.contains("灌水重复") || labels.contains("低信息量")) {
            fill(response, false, "medium", labels, "内容存在低质或攻击性风险，建议进入待审", "review");
            return response;
        }
        fill(response, true, "low", labels, "内容风险较低，可正常发布", "pass");
        return response;
    }

    private static boolean containsAny(String content, String... words) {
        for (String word : words) {
            if (content.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private static void fill(AiModerationCheckResponse response, boolean pass, String riskLevel, List<String> labels, String reason, String action) {
        response.setPass(pass);
        response.setRiskLevel(riskLevel);
        response.setLabels(labels);
        response.setReason(reason);
        response.setSuggestedAction(action);
    }
}
