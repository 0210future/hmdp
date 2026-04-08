package com.hmdp.dto.ai;

import lombok.Data;

@Data
public class AiBlogAssistRequest {
    private Long shopId;
    private String title;
    private String content;
    private String style;
    private String images;
}
