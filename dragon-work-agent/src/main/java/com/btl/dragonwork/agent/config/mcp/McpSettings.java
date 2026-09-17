package com.btl.dragonwork.agent.config.mcp;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class McpSettings {

    private Map<String, McpServerEntry> mcpServers = new LinkedHashMap<>();
}
