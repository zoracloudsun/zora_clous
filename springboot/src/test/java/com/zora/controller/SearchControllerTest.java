package com.zora.controller;

import com.zora.entity.dto.PageResult;
import com.zora.entity.dto.SearchResult;
import com.zora.exception.GlobalExceptionHandler;
import com.zora.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SearchController 全文搜索控制器测试
 * <p>Phase 4 Task A: 测试搜索端点的请求/响应契约和参数校验。</p>
 */
@DisplayName("SearchController 全文搜索控制器测试")
class SearchControllerTest {

    private static final String TEST_EMAIL = "test@example.com";

    @Mock
    private SearchService searchService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        SearchController controller = new SearchController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "searchService", searchService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("GET /search/messages")
    class SearchMessagesEndpoint {

        @Test
        @DisplayName("正常搜索：返回 200 + JSON 分页结果")
        void shouldReturn200WithPaginatedResults() throws Exception {
            PageResult<SearchResult> mockResult = new PageResult<>(Collections.emptyList(), 2L, 1, 20);

            when(searchService.searchMessages(eq(TEST_EMAIL), eq("Spring"), eq(1), eq(20)))
                    .thenReturn(mockResult);

            mockMvc.perform(get("/search/messages")
                            .param("q", "Spring")
                            .param("page", "1")
                            .param("size", "20")
                            .requestAttr("userEmail", TEST_EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(2))
                    .andExpect(jsonPath("$.data.page").value(1));
        }

        @Test
        @DisplayName("无结果搜索：返回 200 + 空列表")
        void shouldReturn200WithEmptyListWhenNoResults() throws Exception {
            PageResult<SearchResult> mockResult = new PageResult<>(Collections.emptyList(), 0L, 1, 20);

            when(searchService.searchMessages(eq(TEST_EMAIL), eq("不存在"), anyInt(), anyInt()))
                    .thenReturn(mockResult);

            mockMvc.perform(get("/search/messages")
                            .param("q", "不存在")
                            .requestAttr("userEmail", TEST_EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(0));
        }

        @Test
        @DisplayName("未认证访问：不传 userEmail → 空指针或 500（拦截器层面的风险）")
        void shouldHandleMissingUserEmail() throws Exception {
            PageResult<SearchResult> mockResult = new PageResult<>(Collections.emptyList(), 0L, 1, 20);

            when(searchService.searchMessages(isNull(), eq("test"), anyInt(), anyInt()))
                    .thenReturn(mockResult);

            mockMvc.perform(get("/search/messages").param("q", "test"))
                    .andExpect(status().isOk());
        }
    }
}
