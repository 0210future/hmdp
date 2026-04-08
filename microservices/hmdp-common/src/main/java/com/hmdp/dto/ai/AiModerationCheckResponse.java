package com.hmdp.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class AiModerationCheckResponse {
    private Boolean pass;
    private String riskLevel;
    private List<String> labels;
    private String reason;
    private String suggestedAction;
}
