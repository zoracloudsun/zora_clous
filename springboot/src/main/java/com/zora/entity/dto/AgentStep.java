package com.zora.entity.dto;

/**
 * Agent 推理步骤记录（瞬态 POJO，不持久化）
 * <p>
 * 用于记录 Agent 推理循环中的每一步操作，
 * 包括思考过程、工具调用和工具执行结果。
 * 仅在单次对话请求内存中使用，不写入数据库。
 * </p>
 */
public class AgentStep {

    /** 步骤类型：thinking / tool_call / tool_result */
    private String type;

    /** 步骤内容描述 */
    private String content;

    /** 工具名称（tool_call / tool_result 类型时使用） */
    private String tool;

    /** 工具参数 JSON（tool_call 类型时使用） */
    private String args;

    /** 创建时间戳 */
    private Long timestamp;

    public AgentStep() {
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 创建思考步骤
     */
    public static AgentStep thinking(String content) {
        AgentStep step = new AgentStep();
        step.type = "thinking";
        step.content = content;
        return step;
    }

    /**
     * 创建工具调用步骤
     */
    public static AgentStep toolCall(String tool, String args) {
        AgentStep step = new AgentStep();
        step.type = "tool_call";
        step.tool = tool;
        step.args = args;
        return step;
    }

    /**
     * 创建工具结果步骤
     */
    public static AgentStep toolResult(String tool, String content) {
        AgentStep step = new AgentStep();
        step.type = "tool_result";
        step.tool = tool;
        step.content = content;
        return step;
    }

    // ==================== getter / setter ====================

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getTool() { return tool; }
    public void setTool(String tool) { this.tool = tool; }
    public String getArgs() { return args; }
    public void setArgs(String args) { this.args = args; }
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
}
