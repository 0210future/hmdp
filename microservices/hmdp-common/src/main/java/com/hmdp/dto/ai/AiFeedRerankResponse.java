package com.hmdp.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class AiFeedRerankResponse {
    private List<AiFeedItem> items;
    private String strategy;
}
