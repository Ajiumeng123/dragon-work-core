package com.btl.dragonwork.agent.config;

import lombok.Data;

@Data
public class HarnessConfig {

    private String name = "";
    private Integer triggerMessages = 10;
    private Integer keepMessages = 5;
    private Integer maxIters = 30;
    private Boolean toolResultEviction = true;
    private Boolean enableSkillManageTool = true;
}
