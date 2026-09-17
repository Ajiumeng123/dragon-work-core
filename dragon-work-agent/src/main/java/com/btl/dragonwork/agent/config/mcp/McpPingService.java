package com.btl.dragonwork.agent.config.mcp;

import cn.hutool.core.util.StrUtil;
import com.btl.dragonwork.common.exception.BizException;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class McpPingService {

    private static final Duration PING_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration PING_INIT_TIMEOUT = Duration.ofSeconds(15);

    @Resource
    private McpConfigLoader mcpConfigLoader;

    public List<McpPingResult> ping(String name) {
        List<McpServerItem> servers = mcpConfigLoader.load();
        if (StrUtil.isNotBlank(name)) {
            servers = servers.stream()
                    .filter(item -> name.equals(item.getName()))
                    .toList();
            if (servers.isEmpty()) {
                throw new BizException("未找到 MCP 服务「" + name + "」");
            }
        }
        List<McpPingResult> results = new ArrayList<>();
        for (McpServerItem item : servers) {
            results.add(pingOne(item));
        }
        return results;
    }

    private McpPingResult pingOne(McpServerItem item) {
        McpPingResult result = new McpPingResult();
        result.setName(item.getName());
        result.setType(item.getType());
        result.setTools(List.of());
        long start = System.currentTimeMillis();
        McpClientWrapper client = null;
        try {
            client = mcpConfigLoader.buildClient(item, PING_TIMEOUT, PING_INIT_TIMEOUT);
            if (client == null) {
                throw new BizException("不支持的 MCP 类型");
            }
            client.initialize().block(PING_INIT_TIMEOUT);
            List<McpSchema.Tool> tools = client.listTools().block(PING_TIMEOUT);
            List<String> names = tools == null
                    ? List.of()
                    : tools.stream().map(McpSchema.Tool::name).toList();
            result.setOk(true);
            result.setToolCount(names.size());
            result.setTools(names);
        } catch (Exception e) {
            result.setOk(false);
            result.setError(rootMessage(e));
        } finally {
            result.setCostMs(System.currentTimeMillis() - start);
            if (client != null) {
                try {
                    client.close();
                } catch (Exception ignored) {
                }
            }
        }
        return result;
    }

    private String rootMessage(Throwable e) {
        Throwable current = e;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (StrUtil.isBlank(message)) {
            message = e.getMessage();
        }
        return StrUtil.blankToDefault(message, e.getClass().getSimpleName());
    }
}
