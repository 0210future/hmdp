package com.hmdp.dto.ai;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AiShopSummaryResponse {
    private Long shopId;
    private String summary;
    private List<String> tags;
    private List<String> audiences;
    private List<String> warnings;
    private LocalDateTime generatedAt;
}
