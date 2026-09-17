package com.btl.dragonwork.agent.config.mcp;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class McpServerItem {

    private String name;
    private String type;
    private String command;
    private List<String> args;
    private Map<String, String> env;
    private String url;
    private Map<String, String> headers;
    private Map<String, String> queryParams;
}
