package com.hmdp.dto.ai;

import lombok.Data;

@Data
public class AiFeedItem {
    private Long blogId;
    private Long shopId;
    private String title;
    private String summary;
    private Double score;
    private String reason;
}
