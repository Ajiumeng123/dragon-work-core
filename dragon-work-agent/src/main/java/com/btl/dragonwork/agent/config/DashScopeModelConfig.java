package com.btl.dragonwork.agent.config;

import lombok.Data;

@Data
public class DashScopeModelConfig {

    private String apiKey;
    private String modelName;
    private Boolean stream;
    private Double temperature;
    private Double topP;
    private Integer topK;
    private Long seed;
    private Integer maxTokens;
    private Integer maxCompletionTokens;
    private Integer thinkingBudget;
    private Double frequencyPenalty;
    private Double presencePenalty;
    private Boolean parallelToolCalls;
    private Integer timeoutSeconds;
    private Integer maxAttempts;
}
