package com.zora.controller;

import com.zora.exception.GlobalExceptionHandler;
import com.zora.service.StatisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * StatisticsController 数据统计控制器测试
 * <p>Phase 4 Task B: 测试各统计端点的正常响应和数据格式。</p>
 */
@DisplayName("StatisticsController 数据统计控制器测试")
class StatisticsControllerTest {

    private static final String TEST_EMAIL = "test@example.com";

    @Mock
    private StatisticsService statisticsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        StatisticsController controller = new StatisticsController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "statisticsService", statisticsService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("GET /statistics/overview")
    class OverviewEndpoint {

        @Test
        @DisplayName("返回 200 + 含摘要数据")
        void shouldReturn200WithOverviewData() throws Exception {
            Map<String, Object> mockData = new LinkedHashMap<>();
            mockData.put("totalConversations", 10L);
            mockData.put("totalMessages", 50L);
            mockData.put("activeDaysThisWeek", 3);
            mockData.put("aiUsageRate", 60.0);

            when(statisticsService.getOverview(eq(TEST_EMAIL))).thenReturn(mockData);

            mockMvc.perform(get("/statistics/overview")
                            .requestAttr("userEmail", TEST_EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.totalConversations").value(10))
                    .andExpect(jsonPath("$.data.aiUsageRate").value(60.0));
        }
    }

    @Nested
    @DisplayName("GET /statistics/message-trend")
    class MessageTrendEndpoint {

        @Test
        @DisplayName("默认 30 天：返回 200 + 趋势数据")
        void shouldReturn200WithDefaultDays() throws Exception {
            Map<String, Object> mockData = new LinkedHashMap<>();
            mockData.put("dates", List.of("2025-01-01"));
            mockData.put("userCounts", List.of(5L));
            mockData.put("aiCounts", List.of(5L));

            when(statisticsService.getMessageTrend(eq(TEST_EMAIL), eq(30)))
                    .thenReturn(mockData);

            mockMvc.perform(get("/statistics/message-trend")
                            .requestAttr("userEmail", TEST_EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("自定义天数：返回 200")
        void shouldReturn200WithCustomDays() throws Exception {
            Map<String, Object> mockData = new LinkedHashMap<>();
            mockData.put("dates", Collections.emptyList());
            mockData.put("userCounts", Collections.emptyList());
            mockData.put("aiCounts", Collections.emptyList());

            when(statisticsService.getMessageTrend(eq(TEST_EMAIL), eq(7)))
                    .thenReturn(mockData);

            mockMvc.perform(get("/statistics/message-trend")
                            .param("days", "7")
                            .requestAttr("userEmail", TEST_EMAIL))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /statistics/active-hours")
    class ActiveHoursEndpoint {

        @Test
        @DisplayName("返回 200 + 24 小时数据")
        void shouldReturn200With24Hours() throws Exception {
            Map<String, Object> mockData = new LinkedHashMap<>();
            mockData.put("hours", List.of(0, 1, 2));
            mockData.put("counts", List.of(0L, 5L, 3L));

            when(statisticsService.getActiveHours(eq(TEST_EMAIL))).thenReturn(mockData);

            mockMvc.perform(get("/statistics/active-hours")
                            .requestAttr("userEmail", TEST_EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    @Nested
    @DisplayName("GET /statistics/action-ranking")
    class ActionRankingEndpoint {

        @Test
        @DisplayName("返回 200 + 功能排行数据")
        void shouldReturn200WithRanking() throws Exception {
            Map<String, Object> mockData = new LinkedHashMap<>();
            mockData.put("ranking", List.of(
                    Map.of("action", "message_send", "label", "发送消息", "count", 20L)
            ));

            when(statisticsService.getActionRanking(eq(TEST_EMAIL))).thenReturn(mockData);

            mockMvc.perform(get("/statistics/action-ranking")
                            .requestAttr("userEmail", TEST_EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.ranking[0].count").value(20));
        }
    }
}
