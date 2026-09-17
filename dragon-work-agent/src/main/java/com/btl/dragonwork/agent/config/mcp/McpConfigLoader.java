package com.btl.dragonwork.agent.config.mcp;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.btl.dragonwork.common.exception.BizException;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class McpConfigLoader {

    public List<McpClientWrapper> buildMcpList(){
        List<McpServerItem> mcpItemList = load();
        if (mcpItemList == null || mcpItemList.isEmpty()) return null;
        return mcpItemList.stream()
                .map(this::buildClient)
                .filter(item -> item != null)
                .toList();
    }

    public McpClientWrapper buildClient(McpServerItem item) {
        return buildClient(item, null, null);
    }

    public McpClientWrapper buildClient(McpServerItem item, Duration timeout, Duration initializationTimeout) {
        if (item == null || StrUtil.isBlank(item.getName()) || StrUtil.isBlank(item.getType())) {
            return null;
        }
        McpClientBuilder mcpClientBuilder = McpClientBuilder.create(item.getName());
        if (timeout != null) {
            mcpClientBuilder.timeout(timeout);
        }
        if (initializationTimeout != null) {
            mcpClientBuilder.initializationTimeout(initializationTimeout);
        }
        switch (item.getType()) {
            case "stdio" -> {
                if (StrUtil.isBlank(item.getCommand())) {
                    throw new BizException("MCP 服务「" + item.getName() + "」缺少 command");
                }
                mcpClientBuilder.stdioTransport(item.getCommand(), item.getArgs(), item.getEnv());
            }
            case "http" -> {
                if (StrUtil.isBlank(item.getUrl())) {
                    throw new BizException("MCP 服务「" + item.getName() + "」缺少 url");
                }
                mcpClientBuilder.streamableHttpTransport(item.getUrl())
                        .headers(item.getHeaders())
                        .queryParams(item.getQueryParams());
            }
            default -> {
                return null;
            }
        }
        return mcpClientBuilder.buildAsync().block();
    }

    public List<McpServerItem> load() {
        File file = FileUtil.file(System.getProperty("user.home"), ".dragon_work", "mcp_server.json");
        if (!FileUtil.exist(file)) {
            return List.of();
        }
        String json = FileUtil.readUtf8String(file);
        if (StrUtil.isBlank(json)) {
            return List.of();
        }
        try {
            return McpConfigConverter.toList(parseSettings(json));
        } catch (Exception e) {
            throw new BizException("MCP 配置解析失败");
        }
    }

    private McpSettings parseSettings(String json) {
        JSONObject root = JSONUtil.parseObj(json);
        JSONObject servers = root.getJSONObject("mcpServers");
        McpSettings settings = new McpSettings();
        if (servers == null || servers.isEmpty()) {
            return settings;
        }
        Map<String, McpServerEntry> map = new LinkedHashMap<>();
        for (String key : servers.keySet()) {
            JSONObject item = servers.getJSONObject(key);
            map.put(key, item == null ? new McpServerEntry() : item.toBean(McpServerEntry.class));
        }
        settings.setMcpServers(map);
        return settings;
    }
}
