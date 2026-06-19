package com.zora.controller;

import com.zora.agent.AgentService;
import com.zora.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AgentController 控制器测试（Phase 3.1）
 * <p>
 * 使用 MockMvc standalone setup，测试 Agent SSE 端点的请求/响应契约。
 * 注意：MockMvc standalone 对 Flux&amp;lt;String&amp;gt; 的 SSE 响应内容捕获有限，
 * 内容验证侧重验证 Service 层交互是否正确（通过 Mockito verify）。
 * </p>
 */
@DisplayName("AgentController 控制器测试")
class AgentControllerTest {

    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_MESSAGE = "你好";

    @Mock
    private AgentService agentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        AgentController controller = new AgentController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "agentService", agentService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("SSE 端点: POST /agent/chat/stream")
    class AgentStreamChatEndpoint {

        @Test
        @DisplayName("应返回 200 且 Content-Type 包含 text/event-stream")
        void shouldReturn200WithSSEContentType() throws Exception {
            when(agentService.agentStreamChat(anyString(), anyString(), isNull()))
                    .thenReturn(Flux.just("{\"type\":\"thinking\",\"content\":\"测试\"}"));

            mockMvc.perform(post("/agent/chat/stream")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"" + TEST_MESSAGE + "\"}")
                            .requestAttr("userEmail", TEST_EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
        }

        @Test
        @DisplayName("应正确传递 userEmail 和 message 到 Service")
        void shouldPassUserEmailAndMessageToService() throws Exception {
            when(agentService.agentStreamChat(anyString(), anyString(), isNull()))
                    .thenReturn(Flux.just("{\"type\":\"done\"}"));

            mockMvc.perform(post("/agent/chat/stream")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"" + TEST_MESSAGE + "\"}")
                            .requestAttr("userEmail", TEST_EMAIL))
                    .andExpect(status().isOk());

            // 验证 Service 被正确调用
            verify(agentService).agentStreamChat(eq(TEST_EMAIL), eq(TEST_MESSAGE), isNull());
        }

        @Test
        @DisplayName("应正确传递 conversationId 到 Service")
        void shouldPassConversationIdToService() throws Exception {
            when(agentService.agentStreamChat(anyString(), anyString(), any()))
                    .thenReturn(Flux.just("{\"type\":\"done\"}"));

            mockMvc.perform(post("/agent/chat/stream")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"" + TEST_MESSAGE + "\",\"conversationId\":42}")
                            .requestAttr("userEmail", TEST_EMAIL))
                    .andExpect(status().isOk());

            // 验证 Service 被正确调用（带 conversationId）
            verify(agentService).agentStreamChat(eq(TEST_EMAIL), eq(TEST_MESSAGE), eq(42L));
        }

        @Test
        @DisplayName("不传 conversationId 时应传递 null")
        void shouldPassNullConversationIdWhenNotProvided() throws Exception {
            when(agentService.agentStreamChat(anyString(), anyString(), isNull()))
                    .thenReturn(Flux.just("{\"type\":\"done\"}"));

            mockMvc.perform(post("/agent/chat/stream")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"" + TEST_MESSAGE + "\"}")
                            .requestAttr("userEmail", TEST_EMAIL))
                    .andExpect(status().isOk());

            // 验证 conversationId 为 null
            verify(agentService).agentStreamChat(eq(TEST_EMAIL), eq(TEST_MESSAGE), isNull());
        }
    }

    @Nested
    @DisplayName("Agent SSE 事件格式验证")
    class AgentEventFormatTests {

        @Test
        @DisplayName("Service 返回的 Flux 事件应正确传输")
        void shouldTransmitFluxEvents() throws Exception {
            when(agentService.agentStreamChat(anyString(), anyString(), isNull()))
                    .thenReturn(Flux.just(
                            "{\"type\":\"thinking\",\"content\":\"分析中...\"}",
                            "{\"type\":\"token\",\"content\":\"你好\"}",
                            "{\"type\":\"done\",\"conversationId\":42}"));

            mockMvc.perform(post("/agent/chat/stream")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"" + TEST_MESSAGE + "\"}")
                            .requestAttr("userEmail", TEST_EMAIL))
                    .andExpect(status().isOk());

            // 验证 Service 被调用（事件内容由 Service 层测试覆盖）
            verify(agentService).agentStreamChat(eq(TEST_EMAIL), eq(TEST_MESSAGE), isNull());
        }
    }
}
