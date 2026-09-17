package com.btl.dragonwork.agent.factory;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.btl.dragonwork.agent.config.DashScopeModelConfig;
import com.btl.dragonwork.agent.config.HarnessConfig;
import com.btl.dragonwork.agent.config.ModelPlatform;
import com.btl.dragonwork.agent.config.ModelsConfig;
import com.btl.dragonwork.agent.config.mcp.McpConfigLoader;
import com.btl.dragonwork.agent.permission.ShellPermissionAsk;
import com.btl.dragonwork.agent.prompt.SystemPrompt;
import com.btl.dragonwork.common.exception.BizException;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.dashscope.EndpointType;
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.memory.compaction.ToolResultEvictionConfig;
import io.agentscope.harness.agent.tool.SkillManageConfig;
import io.agentscope.harness.agent.workspace.LocalFsMode;
import jakarta.annotation.Resource;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Component
public class BuilderHarnessAgent {

    @Resource
    private ShellPermissionAsk shellPermissionAsk;

    @Resource
    private McpConfigLoader mcpConfigLoader;

    private ModelPlatform selectedPlatform;

    public HarnessAgent builder(
            Boolean enableThinking,
            Boolean enableSearch,
            Boolean enablePlanModel,
            Boolean isProject,
            String projectPath,
            String sessionId,
            List<String> uploadFilePath
    ) {
        ModelsConfig modelsConfig = readModelsConfig();
        HarnessConfig harness = modelsConfig.getHarness();
        selectedPlatform = ModelPlatform.of(modelsConfig.getPlatform());
        HarnessAgent.Builder agentBuilder = HarnessAgent.builder();
        File file = FileUtil.file(System.getProperty("user.home"), ".dragon_work", "app_info.json");
        JSONObject appInfo = JSONUtil.parseObj(FileUtil.readUtf8String(file));
        Path workspacePath = FileUtil.file(appInfo.getStr("agentDir") + "\\.dragon_work_agent").toPath();
        agentBuilder.name(harness.getName())
                .workspace(workspacePath)
                .sysPrompt(SystemPrompt.SYSTEM_PROMPT)
                .stateStore(new JsonFileAgentStateStore(workspacePath))
                .maxIters(harness.getMaxIters())
                .permissionContext(shellPermissionAsk.buildShellPermission())
                .compaction(
                        CompactionConfig.builder()
                                .triggerMessages(harness.getTriggerMessages())
                                .keepMessages(harness.getKeepMessages())
                                .keepTokens(0)
                                .triggerTokens(0)
                                .build()
                )
                .enableAgentTracingLog(true);
        if (harness.getEnableSkillManageTool()) agentBuilder.enableSkillManageTool(SkillManageConfig.defaults());
        agentBuilder.filesystem(checkFileSystem(sessionId, isProject, projectPath, workspacePath, uploadFilePath));
        List<McpClientWrapper> mcpClientWrappers = mcpConfigLoader.buildMcpList();
        if (mcpClientWrappers != null && !mcpClientWrappers.isEmpty()){
            Toolkit toolkit = new Toolkit();
            mcpClientWrappers.forEach(mcp -> {
                toolkit.registerMcpClient(mcp).block();
            });
            agentBuilder.toolkit(toolkit);
        }
        if (harness.getToolResultEviction()) agentBuilder.toolResultEviction(ToolResultEvictionConfig.builder().build());
        if (enablePlanModel != null) agentBuilder.enablePlanMode(enablePlanModel);
        switch (selectedPlatform) {
            case DASHSCOPE -> agentBuilder.model(builderDashScopeChatModel(modelsConfig.getDashscope(), enableThinking, enableSearch));
        }
        return agentBuilder.build();
    }

    @SuppressWarnings("all")
    public DashScopeChatModel builderDashScopeChatModel(DashScopeModelConfig config, Boolean enableThinking, Boolean enableSearch) {
        if (config == null) throw new BizException("DashScope 配置缺失");
        if (StrUtil.isBlank(config.getApiKey())) throw new BizException("请填写 DashScope API Key");
        if (StrUtil.isBlank(config.getModelName())) throw new BizException("请填写 DashScope 模型名称");
        GenerateOptions.Builder optionsBuilder = GenerateOptions.builder();
        if (config.getTemperature() != null) optionsBuilder.temperature(config.getTemperature());
        if (config.getTopP() != null) optionsBuilder.topP(config.getTopP());
        if (isLimited(config.getTopK())) optionsBuilder.topK(config.getTopK());
        if (isLimited(config.getSeed())) optionsBuilder.seed(config.getSeed());
        if (isLimited(config.getMaxTokens())) optionsBuilder.maxTokens(config.getMaxTokens());
        if (isLimited(config.getMaxCompletionTokens())) optionsBuilder.maxCompletionTokens(config.getMaxCompletionTokens());
        if (isLimited(config.getThinkingBudget())) optionsBuilder.thinkingBudget(config.getThinkingBudget());
        if (config.getFrequencyPenalty() != null) optionsBuilder.frequencyPenalty(config.getFrequencyPenalty());
        if (config.getPresencePenalty() != null) optionsBuilder.presencePenalty(config.getPresencePenalty());
        if (config.getParallelToolCalls() != null) optionsBuilder.parallelToolCalls(config.getParallelToolCalls());
        ExecutionConfig.Builder executionBuilder = ExecutionConfig.builder();
        if (config.getTimeoutSeconds() != null) executionBuilder.timeout(Duration.ofSeconds(config.getTimeoutSeconds()));
        if (config.getMaxAttempts() != null) executionBuilder.maxAttempts(config.getMaxAttempts());
        optionsBuilder.executionConfig(executionBuilder.build());
        DashScopeChatModel.Builder modelBuilder = DashScopeChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .defaultOptions(optionsBuilder.build())
                .formatter(new DashScopeChatFormatter())
                .endpointType(EndpointType.MULTIMODAL);
        if (enableThinking != null) modelBuilder.enableThinking(enableThinking);
        if (config.getStream() != null) modelBuilder.stream(config.getStream());
        if (enableSearch != null) modelBuilder.enableSearch(enableSearch);
        return modelBuilder.build();
    }

    private LocalFilesystemSpec checkFileSystem(
            String session,
            Boolean isProject,
            String projectPath,
            Path workspacePath,
            List<String> uploadFilePath
    ){
        if (isProject && projectPath != null){
            File file = FileUtil.file(projectPath);
            if (!FileUtil.isDirectory(file)) throw new BizException("项目路径不存在!");
            LocalFilesystemSpec localFilesystemSpec = new LocalFilesystemSpec()
                    .project(file.toPath())
                    .projectWritable(true)
                    .mode(LocalFsMode.ROOTED)
                    .executeTimeoutSeconds(120)
                    .inheritEnv(true);
            if (uploadFilePath != null) localFilesystemSpec.additionalRoots(uploadFilePath.stream().map(Path::of).collect(Collectors.toList()));
            return localFilesystemSpec;
        }else if (!isProject){
            File localWorkspace = FileUtil.file(workspacePath.toFile(), "local_workspace");
            if (!FileUtil.isDirectory(localWorkspace)) FileUtil.mkdir(localWorkspace);
            File sessionDir = FileUtil.file(localWorkspace, session);
            if (!FileUtil.isDirectory(sessionDir)) FileUtil.mkdir(sessionDir);
            LocalFilesystemSpec localFilesystemSpec = new LocalFilesystemSpec()
                    .project(sessionDir.toPath())
                    .projectWritable(true)
                    .mode(LocalFsMode.ROOTED)
                    .executeTimeoutSeconds(120)
                    .inheritEnv(true);
            if (uploadFilePath != null) localFilesystemSpec.additionalRoots(uploadFilePath.stream().map(Path::of).collect(Collectors.toList()));
            return localFilesystemSpec;
        }
        throw new BizException("缺少必要参数,文件系统构建失败!");
    }


    private ModelsConfig readModelsConfig() {
        File file = FileUtil.file(System.getProperty("user.home"), ".dragon_work", "models.json");
        return JSONUtil.toBean(FileUtil.readUtf8String(file), ModelsConfig.class);
    }

    private boolean isLimited(Integer value) {
        return value != null && value > 0;
    }

    private boolean isLimited(Long value) {
        return value != null && value > 0;
    }
}
