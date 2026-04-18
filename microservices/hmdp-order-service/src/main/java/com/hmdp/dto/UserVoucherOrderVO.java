package com.hmdp.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserVoucherOrderVO {
    private Long orderId;
    private Long voucherId;
    private Long shopId;
    private String title;
    private String subTitle;
    private String rules;
    private Long payValue;
    private Long actualValue;
    private Integer type;
    private Integer payType;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime useTime;
    private LocalDateTime refundTime;
    private LocalDateTime beginTime;
    private LocalDateTime endTime;
}
