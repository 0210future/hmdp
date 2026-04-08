package com.hmdp.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BlogRecord {
    private Long id;
    private Long shopId;
    private Long userId;
    private String icon;
    private String name;
    private Boolean isLike;
    private String title;
    private String images;
    private String content;
    private Integer liked;
    private Integer comments;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
