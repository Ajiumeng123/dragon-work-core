package com.btl.dragonwork.agent.config.mcp;

import cn.hutool.core.util.StrUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class McpConfigConverter {

    private McpConfigConverter() {
    }

    public static List<McpServerItem> toList(McpSettings settings) {
        if (settings == null || settings.getMcpServers() == null) {
            return List.of();
        }
        List<McpServerItem> result = new ArrayList<>();
        for (Map.Entry<String, McpServerEntry> entry : settings.getMcpServers().entrySet()) {
            String name = trimToNull(entry.getKey());
            if (StrUtil.isBlank(name)) {
                continue;
            }
            McpServerEntry raw = entry.getValue() == null ? new McpServerEntry() : entry.getValue();
            String type = normalizeType(raw.getType());
            McpServerItem item = new McpServerItem();
            item.setName(name);
            item.setType(type);
            if ("http".equals(type)) {
                item.setUrl(trimToNull(raw.getUrl()));
                item.setHeaders(cleanMap(raw.getHeaders()));
                item.setQueryParams(cleanMap(raw.getQueryParams()));
            } else {
                item.setCommand(trimToNull(raw.getCommand()));
                item.setArgs(cleanArgs(raw.getArgs()));
                item.setEnv(cleanMap(raw.getEnv()));
            }
            result.add(item);
        }
        return result;
    }

    private static String normalizeType(String type) {
        return "http".equalsIgnoreCase(StrUtil.trim(type)) ? "http" : "stdio";
    }

    private static List<String> cleanArgs(List<String> args) {
        if (args == null) {
            return List.of();
        }
        return args.stream()
                .map(McpConfigConverter::trimToNull)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
    }

    private static Map<String, String> cleanMap(Map<String, String> map) {
        Map<String, String> next = new LinkedHashMap<>();
        if (map == null) {
            return next;
        }
        map.forEach((k, v) -> {
            String key = trimToNull(k);
            if (StrUtil.isNotBlank(key)) {
                next.put(key, v == null ? "" : v.trim());
            }
        });
        return next;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
