package com.hmdp.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class AiFeedRerankRequest {
    private List<Long> blogIds;
    private String query;
    private Double x;
    private Double y;
    private Integer pageSize;
}
