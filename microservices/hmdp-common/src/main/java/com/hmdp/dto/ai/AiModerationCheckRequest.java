package com.hmdp.dto.ai;

import lombok.Data;

@Data
public class AiModerationCheckRequest {
    private String type;
    private String content;
    private String images;
}
