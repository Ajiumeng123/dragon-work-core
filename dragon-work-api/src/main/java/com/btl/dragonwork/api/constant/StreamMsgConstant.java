package com.btl.dragonwork.api.constant;

public final class StreamMsgConstant {
    public static final String AGENT_START = "agent_start";
    public static final String MODEL_END = "model_end";
    public static final String AGENT_RESULT = "agent_result";

    public static final String MODEL_CALL_START = "model_call_start";
    public static final String MODEL_CALL_END = "model_call_end";

    public static final String MODEL_THINK_START = "model_think_start";
    public static final String MODEL_THINK = "model_think";
    public static final String MODEL_THINK_END = "model_think_end";

    public static final String MODEL_TEXT_START = "model_text_start";
    public static final String MODEL_TEXT = "model_text";
    public static final String MODEL_TEXT_END = "model_text_end";

    public static final String DATA_BLOCK_START = "data_block_start";
    public static final String DATA_BLOCK = "data_block";
    public static final String DATA_BLOCK_END = "data_block_end";

    public static final String TOOL_CALL_START = "tool_call_start";
    public static final String TOOL_CALL = "tool_call";
    public static final String TOOL_CALL_END = "tool_call_end";

    public static final String TOOL_RESULT_START = "tool_result_start";
    public static final String TOOL_RESULT = "tool_result";
    public static final String TOOL_RESULT_DATA = "tool_result_data";
    public static final String TOOL_RESULT_END = "tool_result_end";

    public static final String EXCEED_MAX_ITERS = "exceed_max_iters";
    public static final String REQUIRE_USER_CONFIRM = "require_user_confirm";
    public static final String REQUIRE_EXTERNAL_EXECUTION = "require_external_execution";
    public static final String USER_CONFIRM_RESULT = "user_confirm_result";
    public static final String EXTERNAL_EXECUTION_RESULT = "external_execution_result";
    public static final String REQUEST_STOP = "request_stop";
    public static final String SUBAGENT_EXPOSED = "subagent_exposed";
    public static final String HINT_BLOCK = "hint_block";
    public static final String ALL_TOOLS_DENIED = "all_tools_denied";
    public static final String CUSTOM = "custom";
}
