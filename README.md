# dragon-work-core

> **基于 [AgentScope Java 2.0](https://github.com/agentscope-ai/agentscope) 开发**（当前依赖 `2.0.2`：`agentscope-core`、`agentscope-harness`、DashScope 模型扩展）。智能体运行时、工具/MCP、上下文压缩与 Harness 事件流均建立在 AgentScope 2.0 Java API 之上，Spring Boot 层负责 HTTP/SSE 与本地配置对接。

Dragon Work 的 **Java 后端服务**，与配套桌面前端一起使用：前端负责 UI 与本机集成，本仓库负责 Agent 运行时与 HTTP/SSE API。读取本机 `~/.dragon_work` 配置，提供流式对话、人机确认（HITL）、MCP 连通检测与会话历史查询。

| | |
|---|---|
| **本仓库（后端）** | `dragon-work-core` — 你正在看的这个仓库 |
| **配套前端** |  [dragon-work](https://github.com/Ajiumeng123/dragon-work) |


默认只监听本机：`http://127.0.0.1:17623/dragon-work`（前端开发时直连该 Base，见 `docs/` 对接说明）。

License: [Apache-2.0](LICENSE)

## 技术栈

| 项 | 版本 / 说明 |
|---|---|
| Java | 21 |
| Spring Boot | 4.0.8 |
| AgentScope Java | **2.0.x**（Maven `2.0.2`：core / harness / extensions-model-dashscope） |
| 构建 | Maven（含 `dragon-work-start/mvnw`） |

## 模块结构

```
dragon-work-core/
├── dragon-work-common/   # 统一 Result、BizException
├── dragon-work-agent/    # Harness 组装、MCP 加载、会话历史读盘
├── dragon-work-api/      # REST / SSE 控制器
├── dragon-work-start/    # Spring Boot 启动与 application.yml
└── docs/                 # 前端对接说明
```

## 环境要求

- **JDK 21**（`JAVA_HOME` 必须指向有效 JDK，例如 Windows 上 `D:\jdk`）
- 与桌面端一致的本地配置目录：`%USERPROFILE%\.dragon_work\`

启动前至少需要：

| 文件 | 作用 |
|---|---|
| `app_info.json` | `agentDir`：Agent 工作区根路径 |
| `models.json` | 模型平台、DashScope、`harness`（名称、压缩、maxIters 等） |
| `mcp_server.json` | MCP 服务列表（可选，stdio / http） |

Agent 状态与会话文件落在：`{agentDir}\.dragon_work_agent\{userId}\`（含 `agent_state.json`、压缩卸载的 `sessions/*.jsonl`）。

## 构建与运行

在仓库根目录（需先设置 `JAVA_HOME`）：

```powershell
$env:JAVA_HOME="D:\jdk"   # 按本机路径修改
.\dragon-work-start\mvnw.cmd -f pom.xml package -DskipTests
```

运行可执行包：

```powershell
java -jar dragon-work-start\target\dragon-work-start-0.0.1-SNAPSHOT.jar
```

开发期也可在 IDE 中运行 `com.btl.dragonwork.start.DragonWorkStartApplication`。

> 请从**根 `pom.xml` 聚合构建**，不要单独只打包 `dragon-work-start`，否则本地模块 `dragon-work-api` 未 install 时会解析失败。

## HTTP 接口概览

Base：`http://127.0.0.1:17623/dragon-work`

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/agent-business/chat` | SSE 流式对话 |
| POST | `/agent-business/confirmTool` | 工具人机确认后继续流 |
| POST | `/agent-business/stop-chat` | 停止当前对话 |
| DELETE | `/agent-business/remove-agent` | 清除内存中的 Agent 缓存 |
| GET | `/agent-business/sessions` | 历史会话列表 |
| GET | `/agent-business/session-history` | 某会话消息详情 |
| GET | `/mcp/ping` | MCP 建连 + listTools 探测 |

JSON 接口统一响应：`{ "code": 0, "message": "ok", "data": ... }`，`code !== 0` 为业务失败。

SSE 每条为 JSON 字符串，`type` 见 `StreamMsgConstant`（如 `model_text`、`tool_call`、`require_user_confirm` 等）。

## 前端对接

- **配套前端仓库**：[dragon-work](https://github.com/Ajiumeng123/dragon-work)（
- 其它 SSE、MCP 配置等协议说明以前端仓库文档为准；后端接口 Base 为 `/dragon-work`

已启用 CORS（`allowedOriginPatterns: *`），便于本地 WebView / 开发服务器直连后端。

## 配置说明（摘要）

- **模型**：当前实现以 DashScope 多模态端点为主，参数来自 `models.json`（温度、maxTokens、thinking、并行工具调用等）。
- **MCP**：从 `mcp_server.json` 解析为 `List<McpServerItem>`，经 `McpClientBuilder` 注册到 Harness；`/mcp/ping` 仅做连通性探测，不调用业务工具。
- **压缩 / 记忆**：Harness 侧启用 compaction、Memory Flush 等时，完整聊天记录可能写入 `agents/{harness.name}/sessions/{sessionId}.jsonl`；历史 API 会合并 jsonl 与 `agent_state.json` 中尚未卸载的消息。

请勿将 API Key、数据库密码等写入仓库；敏感项只放在本机 `~/.dragon_work` 配置中。

## 许可证

本项目采用 **[Apache License 2.0](LICENSE)** 开源。允许商用；分发或修改时需保留版权与许可声明，并遵守 Apache 2.0 中的专利与贡献条款。
