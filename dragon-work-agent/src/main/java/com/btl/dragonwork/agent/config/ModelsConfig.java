package com.btl.dragonwork.agent.config;

import lombok.Data;

@Data
public class ModelsConfig {

    private String platform;
    private HarnessConfig harness;
    private DashScopeModelConfig dashscope;
}
