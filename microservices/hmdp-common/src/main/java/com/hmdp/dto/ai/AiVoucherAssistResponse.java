package com.hmdp.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class AiVoucherAssistResponse {
    private String title;
    private String subTitle;
    private String rules;
    private List<String> highlights;
    private String suggestion;
}
