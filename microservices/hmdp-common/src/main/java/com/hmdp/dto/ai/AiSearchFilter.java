package com.hmdp.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class AiSearchFilter {
    private String rewrittenQuery;
    private Long typeId;
    private String typeName;
    private Integer maxAvgPrice;
    private Integer maxDistanceMeters;
    private Integer openAfterHour;
    private List<String> scenes;
}
