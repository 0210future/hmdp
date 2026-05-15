package com.hmdp.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class AiBlogAssistResponse {
    private List<String> titleSuggestions;
    private String rewrittenTitle;
    private String rewrittenBody;
    private String rewrittenContent;
    private List<String> highlights;
    private List<String> tags;
    private String summary;
    private Boolean llmEnhanced;
    private String generationMode;
}
