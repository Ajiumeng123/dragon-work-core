package com.btl.dragonwork.api.business;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.btl.dragonwork.agent.factory.BuilderHarnessAgent;
import com.btl.dragonwork.api.constant.StreamMsgConstant;
import com.btl.dragonwork.common.exception.BizException;
import com.btl.dragonwork.common.result.Result;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.*;
import io.agentscope.core.message.*;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.tool.ToolResultMessageBuilder;
import io.agentscope.harness.agent.HarnessAgent;
import jakarta.annotation.Resource;
import lombok.Builder;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@RestController
@RequestMapping("/agent-business")
public class AgentChatBusiness {
    @Resource
    private BuilderHarnessAgent builderHarnessAgent;

    private final Map<UserAndSession, RequireUserConfirmEvent> requireUserConfirmEventMap = new ConcurrentHashMap<>();

    private final Map<UserAndSession, HarnessAgent> harnessAgentMap = new ConcurrentHashMap<>();

    @Builder
    private record UserAndSession(String user, String session){
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(
            @RequestParam("question") String question,
            @RequestParam("sessionId") String sessionId,
            @RequestParam("userId") String userId,
            @RequestParam("enableThinking") boolean enableThinking,
            @RequestParam("enableSearch") boolean enableSearch,
            @RequestParam("enablePlanModel") boolean enablePlanModel,
            @RequestParam("isProject") Boolean isProject,
            @RequestParam(value = "projectPath",required = false) String projectPath,
            @RequestParam(value = "uploadFilePath",required = false) List<String> uploadFilePath,
            @RequestParam(value = "uploadFileImage",required = false) List<MultipartFile> uploadFileImage
    ) {
        HarnessAgent harnessAgent = builderHarnessAgent.builder(enableThinking, enableSearch, enablePlanModel, isProject, projectPath, sessionId,uploadFilePath);
        abortAskingTools(harnessAgent, userId, sessionId);
        harnessAgentMap.put(new UserAndSession(userId, sessionId), harnessAgent);
        RuntimeContext runtimeContext = RuntimeContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .build();
        List<ContentBlock> contentBlocks = new ArrayList<>();
        if (uploadFilePath != null){
            StringBuilder content = new StringBuilder();
            content.append("用户上传的文件列表:");
            for (String filePath : uploadFilePath) {
                content.append(filePath).append("\n");
            }
            contentBlocks.add(TextBlock.builder().text(question + "\n" + content).build());
        }else {
            contentBlocks.add(TextBlock.builder().text(question).build());
        }
        if (uploadFileImage != null){
            for (MultipartFile file : uploadFileImage) {
                try {
                    byte[] bytes = file.getBytes();
                    String originalFilename = file.getOriginalFilename();
                    String suffix = null;
                    if (originalFilename != null) {
                        suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
                    } else throw new BizException("文件名为空");

                    List<String> allowSuffix = List.of(".jpg",".jpeg",".png",".webp");
                    if (!allowSuffix.contains(suffix)) {
                        throw new BizException("上传图片格式错误");
                    }
                    String base64String = Base64.getEncoder().encodeToString(bytes);
                    String mediaType = "image/" + (suffix.equalsIgnoreCase(".jpg") ? "jpeg" : suffix.toLowerCase().split("\\.")[1]);
                    DataBlock imageBase64 = DataBlock.builder()
                            .source(
                                    Base64Source.builder()
                                            .data(base64String)
                                            .mediaType(mediaType)
                                            .build()
                            ).build();
                    contentBlocks.add(imageBase64);
                } catch (IOException e) {
                    throw new BizException("图片上传失败");
                }
            }
        }
        Flux<AgentEvent> agentEventFlux = harnessAgent.streamEvents(new UserMessage(contentBlocks), runtimeContext);
        return toStream(agentEventFlux, userId, sessionId);
    }

    @PostMapping(value = "/confirmTool")
    public Flux<String> confirmTool(
            @RequestParam("sessionId") String sessionId,
            @RequestParam("userId") String userId,
            @RequestParam("toolCallId") String toolCallId,
            @RequestParam("confirm") Boolean confirm,
            @RequestParam(value = "projectPath",required = false) String projectPath
    ) {
        RequireUserConfirmEvent requireUserConfirmEvent = requireUserConfirmEventMap.get(new UserAndSession(userId, sessionId));
        if (requireUserConfirmEvent == null) {
            throw new BizException("没有待确认的工具调用");
        }
        ToolUseBlock toolUseBlock = requireUserConfirmEvent.getToolCalls().stream()
                .filter(toolCall -> toolCall.getId().equals(toolCallId))
                .findFirst()
                .orElseThrow(() -> new BizException("未找到该需要确认的工具"));
        List<ConfirmResult> confirmResults = List.of(
                new ConfirmResult(confirm, toolUseBlock)
        );
        UserMessage confirmMes = UserMessage.builder()
                .metadata(Map.of(
                        UserMessage.METADATA_CONFIRM_RESULTS, confirmResults,
                        UserMessage.METADATA_CONFIRM_REQUEST_REPLY_ID, requireUserConfirmEvent.getReplyId()
                ))
                .build();
        HarnessAgent harnessAgent = harnessAgentMap.get(new UserAndSession(userId, sessionId));
        if (harnessAgent == null) throw new BizException("对话不存在");
        RuntimeContext runtimeContext = RuntimeContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .build();
        Flux<AgentEvent> agentEventFlux = harnessAgent.streamEvents(confirmMes, runtimeContext);
        return toStream(agentEventFlux, userId, sessionId);
    }

    @DeleteMapping(value = "/remove-agent")
    public Result<Void> removeAgent(
            @RequestParam("sessionId") String sessionId,
            @RequestParam("userId") String userId) {
        clearStateCache(userId, sessionId);
        return Result.ok(null);
    }


    @PostMapping(value = "/stop-chat")
    public Result<Void> stopChat(
            @RequestParam("sessionId") String sessionId,
            @RequestParam("userId") String userId,
            @RequestParam("isProject") Boolean isProject,
            @RequestParam(value = "projectPath",required = false) String projectPath) {
        HarnessAgent harnessAgent = harnessAgentMap.get(new UserAndSession(userId, sessionId));
        if (harnessAgent == null) throw new BizException("对话不存在");
        RuntimeContext runtimeContext = RuntimeContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .build();
        harnessAgent.getDelegate().interrupt(runtimeContext, new UserMessage("用户手动停止对话"));
        abortAskingTools(harnessAgent, userId, sessionId);
        clearStateCache(userId, sessionId);
        return Result.ok(null);
    }

    private void abortAskingTools(HarnessAgent harnessAgent, String userId, String sessionId) {
        if (harnessAgent == null) {
            return;
        }
        AgentState state = harnessAgent.getDelegate().getAgentState(userId, sessionId);
        if (state == null) {
            return;
        }
        List<Msg> ctx = state.contextMutable();
        if (ctx == null || ctx.isEmpty()) {
            return;
        }
        List<ToolUseBlock> asking = new ArrayList<>();
        for (int i = 0; i < ctx.size(); i++) {
            Msg msg = ctx.get(i);
            if (msg == null || msg.getRole() != MsgRole.ASSISTANT || msg.getContent() == null) {
                continue;
            }
            boolean hit = false;
            List<ContentBlock> rebuilt = new ArrayList<>();
            for (ContentBlock block : msg.getContent()) {
                if (block instanceof ToolUseBlock tool && tool.getState() == ToolCallState.ASKING) {
                    asking.add(tool);
                    rebuilt.add(tool.withState(ToolCallState.FINISHED));
                    hit = true;
                } else {
                    rebuilt.add(block);
                }
            }
            if (hit) {
                ctx.set(i, msg.withContent(rebuilt));
            }
        }
        if (asking.isEmpty()) {
            return;
        }
        for (ToolUseBlock denied : asking) {
            ToolResultBlock deniedResult = ToolResultBlock.text("用户停止对话，工具未确认")
                    .withIdAndName(denied.getId(), denied.getName())
                    .withState(ToolResultState.DENIED);
            ctx.add(ToolResultMessageBuilder.buildToolResultMsg(deniedResult, denied, harnessAgent.getName()));
        }
        harnessAgent.getDelegate().saveAgentState(userId, sessionId);
    }

    private void clearStateCache(String userId, String sessionId) {
        UserAndSession key = new UserAndSession(userId, sessionId);
        HarnessAgent harnessAgent = harnessAgentMap.get(key);
        if (harnessAgent != null) {
            abortAskingTools(harnessAgent, userId, sessionId);
            harnessAgent.clearStateCache(userId, sessionId);
        }
        harnessAgentMap.remove(key);
        requireUserConfirmEventMap.remove(key);
    }

    private Flux<String> toStream(Flux<AgentEvent> agentEventFlux, String userId, String sessionId) {
        UserAndSession key = new UserAndSession(userId, sessionId);
        return agentEventFlux
                .map(event -> toJson(event, key))
                .onErrorResume(e -> {
                    clearStateCache(userId, sessionId);
                    return Flux.just(JSONObject.of("type", "error", "content", e.getMessage()).toJSONString());
                });
    }

    private String toJson(AgentEvent event, UserAndSession key) {
        if (event instanceof AgentStartEvent startEvent) {
            JSONObject obj = base(StreamMsgConstant.AGENT_START);
            obj.put("name", nvl(startEvent.getName()));
            obj.put("role", nvl(startEvent.getRole()));
            return out(obj, event);
        }
        if (event instanceof AgentResultEvent resultEvent) {
            Msg result = resultEvent.getResult();
            return out(base(StreamMsgConstant.AGENT_RESULT, result == null ? "" : nvl(result.getTextContent())), event);
        }
        if (event instanceof AgentEndEvent) {
            return out(base(StreamMsgConstant.MODEL_END), event);
        }
        if (event instanceof ModelCallStartEvent) {
            return out(base(StreamMsgConstant.MODEL_CALL_START), event);
        }
        if (event instanceof ModelCallEndEvent modelCallEnd) {
            JSONObject obj = base(StreamMsgConstant.MODEL_CALL_END);
            ChatUsage usage = modelCallEnd.getUsage();
            if (usage != null) {
                obj.put("inputTokens", usage.getInputTokens());
                obj.put("outputTokens", usage.getOutputTokens());
                obj.put("cachedTokens", usage.getCachedTokens());
                obj.put("time", usage.getTime());
            }
            return out(obj, event);
        }
        if (event instanceof ThinkingBlockStartEvent) {
            return out(base(StreamMsgConstant.MODEL_THINK_START), event);
        }
        if (event instanceof ThinkingBlockDeltaEvent thinkEvent) {
            return out(base(StreamMsgConstant.MODEL_THINK, thinkEvent.getDelta()), event);
        }
        if (event instanceof ThinkingBlockEndEvent) {
            return out(base(StreamMsgConstant.MODEL_THINK_END), event);
        }
        if (event instanceof TextBlockStartEvent) {
            return out(base(StreamMsgConstant.MODEL_TEXT_START), event);
        }
        if (event instanceof TextBlockDeltaEvent textEvent) {
            return out(base(StreamMsgConstant.MODEL_TEXT, textEvent.getDelta()), event);
        }
        if (event instanceof TextBlockEndEvent) {
            return out(base(StreamMsgConstant.MODEL_TEXT_END), event);
        }
        if (event instanceof DataBlockStartEvent) {
            return out(base(StreamMsgConstant.DATA_BLOCK_START), event);
        }
        if (event instanceof DataBlockDeltaEvent dataDelta) {
            return out(base(StreamMsgConstant.DATA_BLOCK, dataDelta.getDelta()), event);
        }
        if (event instanceof DataBlockEndEvent) {
            return out(base(StreamMsgConstant.DATA_BLOCK_END), event);
        }
        if (event instanceof ToolCallStartEvent toolCallStart) {
            return out(toolMsg(StreamMsgConstant.TOOL_CALL_START, toolCallStart.getToolCallName(), ""), event);
        }
        if (event instanceof ToolCallDeltaEvent toolCallDelta) {
            return out(toolMsg(StreamMsgConstant.TOOL_CALL, toolCallDelta.getToolCallName(), toolCallDelta.getDelta()), event);
        }
        if (event instanceof ToolCallEndEvent toolCallEnd) {
            return out(toolMsg(StreamMsgConstant.TOOL_CALL_END, toolCallEnd.getToolCallName(), ""), event);
        }
        if (event instanceof ToolResultStartEvent toolResultStart) {
            return out(toolMsg(StreamMsgConstant.TOOL_RESULT_START, toolResultStart.getToolCallName(), ""), event);
        }
        if (event instanceof ToolResultTextDeltaEvent toolResultText) {
            return out(toolMsg(StreamMsgConstant.TOOL_RESULT, toolResultText.getToolCallName(), toolResultText.getDelta()), event);
        }
        if (event instanceof ToolResultDataDeltaEvent toolResultData) {
            return out(toolResultDataMsg(toolResultData), event);
        }
        if (event instanceof ToolResultEndEvent toolResultEnd) {
            JSONObject obj = toolMsg(StreamMsgConstant.TOOL_RESULT_END, toolResultEnd.getToolCallName(), "");
            obj.put("state", toolResultEnd.getState() == null ? "" : toolResultEnd.getState().name());
            return out(obj, event);
        }
        if (event instanceof ExceedMaxItersEvent exceed) {
            JSONObject obj = base(StreamMsgConstant.EXCEED_MAX_ITERS);
            obj.put("maxIters", exceed.getMaxIters());
            obj.put("currentIter", exceed.getCurrentIter());
            return out(obj, event);
        }
        if (event instanceof RequireUserConfirmEvent confirmEvent) {
            requireUserConfirmEventMap.put(key, confirmEvent);
            JSONObject obj = base(StreamMsgConstant.REQUIRE_USER_CONFIRM);
            obj.put("toolCalls", toolUseArray(confirmEvent.getToolCalls()));
            return out(obj, event);
        }
        if (event instanceof RequireExternalExecutionEvent externalEvent) {
            JSONObject obj = base(StreamMsgConstant.REQUIRE_EXTERNAL_EXECUTION);
            obj.put("toolCalls", toolUseArray(externalEvent.getToolCalls()));
            return out(obj, event);
        }
        if (event instanceof UserConfirmResultEvent confirmResultEvent) {
            JSONArray results = new JSONArray();
            for (ConfirmResult result : confirmResultEvent.getConfirmResults()) {
                JSONObject item = new JSONObject();
                item.put("confirm", result.isConfirmed());
                ToolUseBlock tool = result.getToolCall();
                if (tool != null) {
                    item.put("toolCallId", nvl(tool.getId()));
                    item.put("toolName", nvl(tool.getName()));
                    item.put("input", tool.getInput() == null ? Map.of() : tool.getInput());
                    item.put("content", nvl(tool.getContent()));
                    item.put("state", tool.getState() == null ? "" : tool.getState().name());
                }
                results.add(item);
            }
            JSONObject obj = base(StreamMsgConstant.USER_CONFIRM_RESULT);
            obj.put("confirmResults", results);
            return out(obj, event);
        }
        if (event instanceof ExternalExecutionResultEvent externalResultEvent) {
            JSONArray results = new JSONArray();
            for (ToolResultBlock result : externalResultEvent.getToolResults()) {
                JSONObject item = new JSONObject();
                item.put("toolCallId", nvl(result.getId()));
                item.put("toolName", nvl(result.getName()));
                item.put("content", extractText(result.getOutput()));
                item.put("state", result.getState() == null ? "" : result.getState().name());
                results.add(item);
            }
            JSONObject obj = base(StreamMsgConstant.EXTERNAL_EXECUTION_RESULT);
            obj.put("toolResults", results);
            return out(obj, event);
        }
        if (event instanceof RequestStopEvent stopEvent) {
            JSONObject obj = base(StreamMsgConstant.REQUEST_STOP, stopEvent.getReason());
            obj.put("generateReason", stopEvent.getGenerateReason() == null ? "" : stopEvent.getGenerateReason().name());
            return out(obj, event);
        }
        if (event instanceof SubagentExposedEvent subagent) {
            JSONObject obj = base(StreamMsgConstant.SUBAGENT_EXPOSED);
            obj.put("subagentId", nvl(subagent.getSubagentId()));
            obj.put("agentId", nvl(subagent.getAgentId()));
            obj.put("sessionId", nvl(subagent.getSessionId()));
            obj.put("label", nvl(subagent.getLabel()));
            return out(obj, event);
        }
        if (event instanceof HintBlockEvent hintEvent) {
            JSONObject obj = base(StreamMsgConstant.HINT_BLOCK, hintEvent.getHint());
            obj.put("hintSource", nvl(hintEvent.getHintSource()));
            return out(obj, event);
        }
        if (event instanceof AllToolsDeniedEvent deniedEvent) {
            JSONObject obj = base(StreamMsgConstant.ALL_TOOLS_DENIED);
            obj.put("toolCalls", toolUseArray(deniedEvent.getDeniedToolCalls()));
            return out(obj, event);
        }
        if (event instanceof CustomEvent customEvent) {
            JSONObject obj = base(StreamMsgConstant.CUSTOM);
            obj.put("name", nvl(customEvent.getName()));
            obj.put("value", customEvent.getValue() == null ? Map.of() : customEvent.getValue());
            return out(obj, event);
        }
        return out(base("null"), event);
    }

    private JSONObject base(String type) {
        return JSONObject.of("type", type, "content", "");
    }

    private JSONObject base(String type, String content) {
        return JSONObject.of("type", type, "content", nvl(content));
    }

    private String out(JSONObject obj, AgentEvent event) {
        if (event.getSource() != null && !event.getSource().isBlank()) {
            obj.put("source", event.getSource());
        }
        return obj.toJSONString();
    }

    private JSONArray toolUseArray(List<ToolUseBlock> tools) {
        JSONArray toolCalls = new JSONArray();
        if (tools == null) {
            return toolCalls;
        }
        for (ToolUseBlock tool : tools) {
            JSONObject item = new JSONObject();
            item.put("toolCallId", nvl(tool.getId()));
            item.put("toolName", nvl(tool.getName()));
            item.put("input", tool.getInput() == null ? Map.of() : tool.getInput());
            item.put("content", nvl(tool.getContent()));
            item.put("state", tool.getState() == null ? "" : tool.getState().name());
            toolCalls.add(item);
        }
        return toolCalls;
    }

    private JSONObject toolMsg(String type, String toolName, String content) {
        JSONObject obj = base(type, content);
        obj.put("toolName", nvl(toolName));
        return obj;
    }

    private String extractText(List<ContentBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (ContentBlock block : blocks) {
            if (block instanceof TextBlock textBlock) {
                text.append(nvl(textBlock.getText()));
            }
        }
        return text.toString();
    }

    private JSONObject toolResultDataMsg(ToolResultDataDeltaEvent event) {
        String mediaType = "";
        String data = "";
        String url = "";
        Source source = extractSource(event.getData());
        if (source instanceof Base64Source base64) {
            mediaType = nvl(base64.getMediaType());
            data = nvl(base64.getData());
        } else if (source instanceof URLSource urlSource) {
            url = nvl(urlSource.getUrl());
            mediaType = nvl(urlSource.getMimeType());
        }
        JSONObject obj = toolMsg(StreamMsgConstant.TOOL_RESULT_DATA, event.getToolCallName(), "");
        obj.put("mediaType", mediaType);
        obj.put("data", data);
        obj.put("url", url);
        return obj;
    }

    private Source extractSource(ContentBlock block) {
        if (block instanceof DataBlock dataBlock) {
            return dataBlock.getSource();
        }
        if (block instanceof ImageBlock imageBlock) {
            return imageBlock.getSource();
        }
        if (block instanceof AudioBlock audioBlock) {
            return audioBlock.getSource();
        }
        if (block instanceof VideoBlock videoBlock) {
            return videoBlock.getSource();
        }
        return null;
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }
}
