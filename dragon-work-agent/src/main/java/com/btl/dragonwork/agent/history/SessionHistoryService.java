package com.btl.dragonwork.agent.history;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.btl.dragonwork.agent.config.HarnessConfig;
import com.btl.dragonwork.agent.config.ModelsConfig;
import com.btl.dragonwork.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class SessionHistoryService {

    public List<SessionHistoryItem> list(String userId) {
        checkId(userId, "userId");
        File userDir = userDir(userId);
        if (!FileUtil.isDirectory(userDir)) {
            return List.of();
        }
        Set<String> sessionIds = new LinkedHashSet<>();
        File[] children = userDir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory() && FileUtil.isFile(FileUtil.file(child, "agent_state.json"))) {
                    sessionIds.add(child.getName());
                }
            }
        }
        File sessionsDir = sessionsDir(userId);
        if (FileUtil.isDirectory(sessionsDir)) {
            File[] logs = sessionsDir.listFiles();
            if (logs != null) {
                for (File log : logs) {
                    String name = log.getName();
                    if (name.endsWith(".jsonl") && !name.endsWith(".log.jsonl")) {
                        sessionIds.add(name.substring(0, name.length() - ".jsonl".length()));
                    }
                }
            }
        }
        List<SessionHistoryItem> items = new ArrayList<>();
        for (String sessionId : sessionIds) {
            SessionHistoryItem item = new SessionHistoryItem();
            item.setSessionId(sessionId);
            item.setUpdatedAt(updatedAt(userId, sessionId));
            item.setPreview(preview(userId, sessionId));
            items.add(item);
        }
        items.sort(Comparator.comparing(SessionHistoryItem::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return items;
    }

    public SessionHistory detail(String userId, String sessionId) {
        checkId(userId, "userId");
        checkId(sessionId, "sessionId");
        List<SessionHistoryMessage> messages = loadMessages(userId, sessionId);
        if (messages.isEmpty() && !FileUtil.isFile(jsonlFile(userId, sessionId)) && !FileUtil.isFile(stateFile(userId, sessionId))) {
            throw new BizException("会话不存在");
        }
        SessionHistory history = new SessionHistory();
        history.setUserId(userId);
        history.setSessionId(sessionId);
        history.setMessages(messages);
        return history;
    }

    private List<SessionHistoryMessage> loadMessages(String userId, String sessionId) {
        Map<String, SessionHistoryMessage> byId = new LinkedHashMap<>();
        File jsonl = jsonlFile(userId, sessionId);
        if (!FileUtil.isFile(jsonl)) {
            jsonl = logJsonlFile(userId, sessionId);
        }
        if (FileUtil.isFile(jsonl)) {
            for (String line : FileUtil.readLines(jsonl, StandardCharsets.UTF_8)) {
                if (StrUtil.isBlank(line)) {
                    continue;
                }
                JSONObject obj = JSONUtil.parseObj(line);
                SessionHistoryMessage message = fromJsonl(obj);
                if (isCompaction(message)) {
                    continue;
                }
                byId.put(key(message), message);
            }
        }
        File state = stateFile(userId, sessionId);
        if (FileUtil.isFile(state)) {
            JSONObject root = JSONUtil.parseObj(FileUtil.readUtf8String(state));
            JSONArray context = root.getJSONArray("context");
            if (context != null) {
                for (int i = 0; i < context.size(); i++) {
                    SessionHistoryMessage message = fromState(context.getJSONObject(i));
                    if (isCompaction(message)) {
                        continue;
                    }
                    byId.putIfAbsent(key(message), message);
                }
            }
        }
        return new ArrayList<>(byId.values());
    }

    private SessionHistoryMessage fromJsonl(JSONObject obj) {
        SessionHistoryMessage message = new SessionHistoryMessage();
        message.setId(obj.getStr("id"));
        message.setRole(obj.getStr("role"));
        message.setContent(obj.getStr("content"));
        message.setToolCallId(obj.getStr("toolCallId"));
        Object timestamp = obj.get("timestamp");
        message.setTimestamp(timestamp == null ? null : String.valueOf(timestamp));
        return message;
    }

    private SessionHistoryMessage fromState(JSONObject obj) {
        SessionHistoryMessage message = new SessionHistoryMessage();
        message.setId(obj.getStr("id"));
        message.setRole(obj.getStr("role"));
        message.setTimestamp(obj.getStr("timestamp"));
        message.setContent(extractContent(obj.getJSONArray("content")));
        JSONArray content = obj.getJSONArray("content");
        if (content != null) {
            for (int i = 0; i < content.size(); i++) {
                JSONObject block = content.getJSONObject(i);
                String type = block.getStr("type");
                if ("tool_use".equals(type) || "tool_result".equals(type)) {
                    message.setToolCallId(block.getStr("id"));
                    break;
                }
            }
        }
        return message;
    }

    private String extractContent(JSONArray content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < content.size(); i++) {
            JSONObject block = content.getJSONObject(i);
            String type = block.getStr("type");
            if ("thinking".equals(type)) {
                continue;
            }
            if ("text".equals(type)) {
                append(text, block.getStr("text"));
            } else if ("tool_use".equals(type)) {
                String name = StrUtil.blankToDefault(block.getStr("name"), "tool");
                Object input = block.get("input");
                String args = input == null ? "{}" : JSONUtil.toJsonStr(input);
                append(text, "[tool_call: " + name + "(" + args + ")]");
            } else if ("tool_result".equals(type)) {
                String name = StrUtil.blankToDefault(block.getStr("name"), "tool");
                append(text, "[tool_result: " + name + "] " + extractOutput(block.getJSONArray("output")));
            }
        }
        return text.toString();
    }

    private String extractOutput(JSONArray output) {
        if (output == null || output.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < output.size(); i++) {
            JSONObject block = output.getJSONObject(i);
            if ("text".equals(block.getStr("type"))) {
                append(text, block.getStr("text"));
            }
        }
        return text.toString();
    }

    private void append(StringBuilder text, String part) {
        if (StrUtil.isBlank(part)) {
            return;
        }
        if (!text.isEmpty()) {
            text.append("\n");
        }
        text.append(part);
    }

    private boolean isCompaction(SessionHistoryMessage message) {
        if (message == null) {
            return true;
        }
        if (StrUtil.startWith(message.getId(), "__compaction_summary__")) {
            return true;
        }
        return StrUtil.startWith(message.getContent(), "You are in the middle of a conversation that has been summarized.");
    }

    private String key(SessionHistoryMessage message) {
        if (StrUtil.isNotBlank(message.getId())) {
            return message.getId();
        }
        return message.getRole() + "|" + message.getTimestamp() + "|" + message.getContent();
    }

    private String preview(String userId, String sessionId) {
        for (SessionHistoryMessage message : loadMessages(userId, sessionId)) {
            if ("USER".equals(message.getRole()) && StrUtil.isNotBlank(message.getContent())) {
                return StrUtil.maxLength(message.getContent().replace("\n", " ").trim(), 80);
            }
        }
        return "";
    }

    private Long updatedAt(String userId, String sessionId) {
        long latest = 0L;
        File jsonl = jsonlFile(userId, sessionId);
        if (FileUtil.isFile(jsonl)) {
            latest = Math.max(latest, jsonl.lastModified());
        }
        File log = logJsonlFile(userId, sessionId);
        if (FileUtil.isFile(log)) {
            latest = Math.max(latest, log.lastModified());
        }
        File state = stateFile(userId, sessionId);
        if (FileUtil.isFile(state)) {
            latest = Math.max(latest, state.lastModified());
        }
        return latest == 0L ? null : latest;
    }

    private File workspace() {
        File file = FileUtil.file(System.getProperty("user.home"), ".dragon_work", "app_info.json");
        JSONObject appInfo = JSONUtil.parseObj(FileUtil.readUtf8String(file));
        return FileUtil.file(appInfo.getStr("agentDir") + "\\.dragon_work_agent");
    }

    private String agentName() {
        File file = FileUtil.file(System.getProperty("user.home"), ".dragon_work", "models.json");
        ModelsConfig modelsConfig = JSONUtil.toBean(FileUtil.readUtf8String(file), ModelsConfig.class);
        HarnessConfig harness = modelsConfig.getHarness();
        if (harness == null || StrUtil.isBlank(harness.getName())) {
            throw new BizException("缺少 harness.name");
        }
        return harness.getName();
    }

    private File userDir(String userId) {
        return FileUtil.file(workspace(), userId);
    }

    private File sessionsDir(String userId) {
        return FileUtil.file(userDir(userId), "agents", agentName(), "sessions");
    }

    private File jsonlFile(String userId, String sessionId) {
        return FileUtil.file(sessionsDir(userId), sessionId + ".jsonl");
    }

    private File logJsonlFile(String userId, String sessionId) {
        return FileUtil.file(sessionsDir(userId), sessionId + ".log.jsonl");
    }

    private File stateFile(String userId, String sessionId) {
        return FileUtil.file(userDir(userId), sessionId, "agent_state.json");
    }

    private void checkId(String value, String name) {
        if (StrUtil.isBlank(value)) {
            throw new BizException(name + " 不能为空");
        }
        if (value.contains("..") || value.contains("/") || value.contains("\\")) {
            throw new BizException(name + " 非法");
        }
    }
}
