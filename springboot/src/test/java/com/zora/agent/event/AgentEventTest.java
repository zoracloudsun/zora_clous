package com.zora.agent.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentEvent SSE 事件序列化测试
 * <p>
 * 验证各种事件类型的 JSON 序列化正确性。
 * </p>
 */
@DisplayName("AgentEvent SSE 事件测试")
class AgentEventTest {

    @Nested
    @DisplayName("thinking 思考事件")
    class ThinkingEvent {

        @Test
        @DisplayName("应生成包含 type=thinking 的 JSON")
        void shouldGenerateThinkingJson() {
            AgentEvent event = AgentEvent.thinking("正在分析问题...");
            String json = event.toJson();

            assertTrue(json.contains("\"type\":\"thinking\""));
            assertTrue(json.contains("正在分析问题..."));
            assertEquals("thinking", event.getType());
        }
    }

    @Nested
    @DisplayName("tool_call 工具调用事件")
    class ToolCallEvent {

        @Test
        @DisplayName("应生成包含工具名和参数的 JSON")
        void shouldGenerateToolCallJson() {
            Map<String, Object> args = new HashMap<>();
            args.put("expression", "sqrt(144)");

            AgentEvent event = AgentEvent.toolCall("calculate", args);
            String json = event.toJson();

            assertTrue(json.contains("\"type\":\"tool_call\""));
            assertTrue(json.contains("\"tool\":\"calculate\""));
            assertTrue(json.contains("sqrt(144)"));
            assertEquals("tool_call", event.getType());
        }

        @Test
        @DisplayName("应支持空参数")
        void shouldSupportEmptyArgs() {
            AgentEvent event = AgentEvent.toolCall("myTool", new HashMap<>());
            String json = event.toJson();

            assertTrue(json.contains("\"type\":\"tool_call\""));
            assertNotNull(json);
        }
    }

    @Nested
    @DisplayName("tool_result 工具结果事件")
    class ToolResultEvent {

        @Test
        @DisplayName("应生成包含工具名和结果的 JSON")
        void shouldGenerateToolResultJson() {
            AgentEvent event = AgentEvent.toolResult("calculate", "{\"result\":12.0}");
            String json = event.toJson();

            assertTrue(json.contains("\"type\":\"tool_result\""));
            assertTrue(json.contains("\"tool\":\"calculate\""));
            assertTrue(json.contains("12.0"));
            assertEquals("tool_result", event.getType());
        }
    }

    @Nested
    @DisplayName("token 文本事件")
    class TokenEvent {

        @Test
        @DisplayName("应生成包含文本内容的 JSON")
        void shouldGenerateTokenJson() {
            AgentEvent event = AgentEvent.token("你好，世界");
            String json = event.toJson();

            assertTrue(json.contains("\"type\":\"token\""));
            assertTrue(json.contains("你好，世界"));
            assertEquals("token", event.getType());
        }

        @Test
        @DisplayName("应正确处理换行符等特殊字符")
        void shouldHandleSpecialCharacters() {
            AgentEvent event = AgentEvent.token("第一行\n第二行\n\t缩进");
            String json = event.toJson();

            // JSON 序列化应正确 escape 特殊字符
            assertNotNull(json);
            // 能反序列化回来即表示序列化正确
            assertDoesNotThrow(() -> {
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            });
        }
    }

    @Nested
    @DisplayName("done 完成事件")
    class DoneEvent {

        @Test
        @DisplayName("应生成包含 conversationId 的 JSON")
        void shouldGenerateDoneJson() {
            AgentEvent event = AgentEvent.done(42L);
            String json = event.toJson();

            assertTrue(json.contains("\"type\":\"done\""));
            assertTrue(json.contains("\"conversationId\":42"));
            assertEquals("done", event.getType());
        }

        @Test
        @DisplayName("应支持 null conversationId")
        void shouldSupportNullConversationId() {
            AgentEvent event = AgentEvent.done(null);
            String json = event.toJson();

            assertTrue(json.contains("\"type\":\"done\""));
            assertNotNull(json);
        }
    }

    @Nested
    @DisplayName("error 错误事件")
    class ErrorEvent {

        @Test
        @DisplayName("应生成包含错误信息的 JSON")
        void shouldGenerateErrorJson() {
            AgentEvent event = AgentEvent.error("AI 服务异常");
            String json = event.toJson();

            assertTrue(json.contains("\"type\":\"error\""));
            assertTrue(json.contains("AI 服务异常"));
            assertEquals("error", event.getType());
        }
    }

    @Test
    @DisplayName("toJson 不应返回 null 或空字符串")
    void toJsonShouldNeverReturnNull() {
        AgentEvent event = AgentEvent.thinking("test");
        String json = event.toJson();

        assertNotNull(json);
        assertFalse(json.isEmpty());
    }
}
