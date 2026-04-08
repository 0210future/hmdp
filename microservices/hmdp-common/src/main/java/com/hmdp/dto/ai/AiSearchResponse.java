package com.hmdp.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class AiSearchResponse {
    private String rewrittenQuery;
    private AiSearchFilter filters;
    private List<AiSearchShopVO> shops;
    private String answer;
}
