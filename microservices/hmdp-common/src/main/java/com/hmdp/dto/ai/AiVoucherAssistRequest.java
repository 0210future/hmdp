package com.hmdp.dto.ai;

import lombok.Data;

@Data
public class AiVoucherAssistRequest {
    private Long shopId;
    private String goal;
    private String title;
    private String subTitle;
    private String rules;
}
