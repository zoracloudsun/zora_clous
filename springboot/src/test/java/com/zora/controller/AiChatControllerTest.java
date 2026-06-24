package com.zora.controller;

import com.zora.exception.GlobalExceptionHandler;
import com.zora.service.AiChatService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AiChatController 批量操作控制器测试
 * <p>
 * 使用 MockMvc standalone setup，测试 3 个批量端点的请求/响应契约。
 * 验证参数校验（ids 为空、超限）和正常流程。
 * </p>
 */
@DisplayName("AiChatController 批量操作控制器测试")
class AiChatControllerTest {

    private static final String TEST_EMAIL = "test@example.com";

    @Mock
    private AiChatService chatService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        AiChatController controller = new AiChatController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "chatService", chatService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ==================== 批量删除 ====================

    @Nested
    @DisplayName("POST /ai/conversations/batch-delete")
    class BatchDeleteEndpoint {

        @Test
        @DisplayName("正常批量删除：返回 200 + JSON 含 data 和 msg")
        void shouldReturn200WithSuccessCount() throws Exception {
            when(chatService.batchDeleteConversations(eq(TEST_EMAIL), anyList()))
                    .thenReturn(2);

            mockMvc.perform(post("/ai/conversations/batch-delete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ids\":[1,2]}")
                            .requestAttr("userEmail", TEST_EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(2))
                    .andExpect(jsonPath("$.msg").value("成功删除 2 个对话"));

            verify(chatService).batchDeleteConversations(eq(TEST_EMAIL), anyList());
        }

        @Test
        @DisplayName("ids 为空数组：返回 400")
        void shouldReturn400WhenIdsEmpty() throws Exception {
            mockMvc.perform(post("/ai/conversations/batch-delete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ids\":[]}")
                            .requestAttr("userEmail", TEST_EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("ids 超过 50 个：返回 400")
        void shouldReturn400WhenIdsExceedsLimit() throws Exception {
            // 构造 51 个 ID 的数组
            StringBuilder sb = new StringBuilder("{\"ids\":[");
            for (int i = 1; i <= 51; i++) {
                if (i > 1) sb.append(",");
                sb.append(i);
            }
            sb.append("]}");

            mockMvc.perform(post("/ai/conversations/batch-delete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(sb.toString())
                            .requestAttr("userEmail", TEST_EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("缺少 ids 字段：返回 400")
        void shouldReturn400WhenIdsMissing() throws Exception {
            mockMvc.perform(post("/ai/conversations/batch-delete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}")
                            .requestAttr("userEmail", TEST_EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }
    }

    // ==================== 批量恢复 ====================

    @Nested
    @DisplayName("POST /ai/conversations/batch-restore")
    class BatchRestoreEndpoint {

        @Test
        @DisplayName("正常批量恢复：返回 200 + JSON 含 data 和 msg")
        void shouldReturn200WithSuccessCount() throws Exception {
            when(chatService.batchRestoreConversations(eq(TEST_EMAIL), anyList()))
                    .thenReturn(3);

            mockMvc.perform(post("/ai/conversations/batch-restore")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ids\":[1,2,3]}")
                            .requestAttr("userEmail", TEST_EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(3))
                    .andExpect(jsonPath("$.msg").value("成功恢复 3 个对话"));

            verify(chatService).batchRestoreConversations(eq(TEST_EMAIL), anyList());
        }

        @Test
        @DisplayName("ids 为空数组：返回 400")
        void shouldReturn400WhenIdsEmpty() throws Exception {
            mockMvc.perform(post("/ai/conversations/batch-restore")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ids\":[]}")
                            .requestAttr("userEmail", TEST_EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }
    }

    // ==================== 批量永久删除 ====================

    @Nested
    @DisplayName("POST /ai/conversations/batch-permanent-delete")
    class BatchPermanentDeleteEndpoint {

        @Test
        @DisplayName("正常批量永久删除：返回 200 + JSON 含 data 和 msg")
        void shouldReturn200WithSuccessCount() throws Exception {
            when(chatService.batchPermanentDeleteConversations(eq(TEST_EMAIL), anyList()))
                    .thenReturn(2);

            mockMvc.perform(post("/ai/conversations/batch-permanent-delete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ids\":[1,2]}")
                            .requestAttr("userEmail", TEST_EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(2))
                    .andExpect(jsonPath("$.msg").value("成功永久删除 2 个对话"));

            verify(chatService).batchPermanentDeleteConversations(eq(TEST_EMAIL), anyList());
        }

        @Test
        @DisplayName("ids 为空数组：返回 400")
        void shouldReturn400WhenIdsEmpty() throws Exception {
            mockMvc.perform(post("/ai/conversations/batch-permanent-delete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ids\":[]}")
                            .requestAttr("userEmail", TEST_EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }
    }
}
