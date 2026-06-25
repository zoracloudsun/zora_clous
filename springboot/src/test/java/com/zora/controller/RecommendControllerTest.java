package com.zora.controller;

import com.zora.exception.GlobalExceptionHandler;
import com.zora.service.RecommendService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * RecommendController 智能推荐控制器测试
 * <p>Phase 4 Task D: 测试推荐端点的响应结构和数据隔离。</p>
 */
@DisplayName("RecommendController 智能推荐控制器测试")
class RecommendControllerTest {

    private static final String TEST_EMAIL = "test@example.com";

    @Mock
    private RecommendService recommendService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        RecommendController controller = new RecommendController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "recommendService", recommendService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("GET /recommend/suggestions")
    class GetRecommendationsEndpoint {

        @Test
        @DisplayName("正常返回：200 + 三维推荐数据")
        void shouldReturn200WithRecommendations() throws Exception {
            Map<String, Object> mockData = new LinkedHashMap<>();
            mockData.put("relatedConversations", List.of(
                    Map.of("conversationId", 1, "title", "对话标题", "matchCount", 3)
            ));
            mockData.put("suggestedQuestions", List.of("建议问题1"));
            mockData.put("popularKnowledge", Collections.emptyList());

            when(recommendService.getRecommendations(eq(TEST_EMAIL))).thenReturn(mockData);

            mockMvc.perform(get("/recommend/suggestions")
                            .requestAttr("userEmail", TEST_EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.relatedConversations[0].title").value("对话标题"))
                    .andExpect(jsonPath("$.data.suggestedQuestions[0]").value("建议问题1"));
        }

        @Test
        @DisplayName("无推荐数据：返回 200 + 空数组")
        void shouldReturn200WithEmptyData() throws Exception {
            Map<String, Object> mockData = new LinkedHashMap<>();
            mockData.put("relatedConversations", Collections.emptyList());
            mockData.put("suggestedQuestions", Collections.emptyList());
            mockData.put("popularKnowledge", Collections.emptyList());

            when(recommendService.getRecommendations(eq(TEST_EMAIL))).thenReturn(mockData);

            mockMvc.perform(get("/recommend/suggestions")
                            .requestAttr("userEmail", TEST_EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.relatedConversations").isArray())
                    .andExpect(jsonPath("$.data.suggestedQuestions").isArray());
        }
    }
}
