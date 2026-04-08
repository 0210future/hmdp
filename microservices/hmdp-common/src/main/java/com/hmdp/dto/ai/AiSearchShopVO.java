package com.hmdp.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class AiSearchShopVO {
    private Long id;
    private String name;
    private Long typeId;
    private String typeName;
    private String area;
    private String address;
    private Long avgPrice;
    private Integer sold;
    private Integer comments;
    private Double score;
    private String openHours;
    private Double distance;
    private String matchReason;
    private List<String> matchedTags;
}
