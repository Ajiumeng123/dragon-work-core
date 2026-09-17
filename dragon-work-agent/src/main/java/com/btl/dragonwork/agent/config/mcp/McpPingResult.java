package com.btl.dragonwork.agent.config.mcp;

import lombok.Data;

import java.util.List;

@Data
public class McpPingResult {

    private String name;
    private String type;
    private boolean ok;
    private int toolCount;
    private List<String> tools;
    private String error;
    private long costMs;
}
