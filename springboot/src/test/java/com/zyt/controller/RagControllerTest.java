package com.zyt.controller;

import com.zyt.exception.GlobalExceptionHandler;
import com.zyt.service.RagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * RagController 控制器测试（Phase 2: RAG 知识库）
 * <p>
 * 使用 MockMvc standalone + Mockito，无 Spring 上下文加载。
 * 测试所有知识库 CRUD 和文档管理 API 的请求/响应契约。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RagController 控制器测试")
class RagControllerTest {

    private MockMvc mockMvc;

    @Mock
    private RagService ragService;

    private static final String TEST_EMAIL = "test@example.com";

    @BeforeEach
    void setUp() {
        RagController controller = new RagController();
        ReflectionTestUtils.setField(controller, "ragService", ragService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("知识库 CRUD")
    class KnowledgeBaseCrud {

        @Test
        @DisplayName("POST /rag/knowledge-bases：创建知识库 → 200 返回 id+name")
        void shouldCreateKnowledgeBase() throws Exception {
            Map<String, Object> result = new HashMap<>();
            result.put("id", 1L);
            result.put("name", "测试知识库");
            result.put("description", "描述");
            when(ragService.createKnowledgeBase(eq(TEST_EMAIL), eq("测试知识库"), eq("描述")))
                    .thenReturn(result);

            mockMvc.perform(post("/rag/knowledge-bases")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"测试知识库\",\"description\":\"描述\"}")
                            .requestAttr("userEmail", TEST_EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.name").value("测试知识库"));
        }

        @Test
        @DisplayName("GET /rag/knowledge-bases：列出知识库 → 200 返回数组")
        void shouldListKnowledgeBases() throws Exception {
            List<Map<String, Object>> kbs = new ArrayList<>();
            Map<String, Object> kb = new HashMap<>();
            kb.put("id", 1L);
            kb.put("name", "知识库1");
            kb.put("documentCount", 3);
            kbs.add(kb);
            when(ragService.listKnowledgeBases(TEST_EMAIL)).thenReturn(kbs);

            mockMvc.perform(get("/rag/knowledge-bases")
                            .requestAttr("userEmail", TEST_EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[0].name").value("知识库1"))
                    .andExpect(jsonPath("$.data[0].documentCount").value(3));
        }

        @Test
        @DisplayName("GET /rag/knowledge-bases/{id}：获取详情 → 200 返回完整信息")
        void shouldGetKnowledgeBase() throws Exception {
            Map<String, Object> kb = new HashMap<>();
            kb.put("id", 1L);
            kb.put("name", "知识库1");
            kb.put("documents", Collections.emptyList());
            when(ragService.getKnowledgeBase(TEST_EMAIL, 1L)).thenReturn(kb);

            mockMvc.perform(get("/rag/knowledge-bases/1")
                            .requestAttr("userEmail", TEST_EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.name").value("知识库1"));
        }

        @Test
        @DisplayName("PUT /rag/knowledge-bases/{id}：更新知识库 → 200")
        void shouldUpdateKnowledgeBase() throws Exception {
            doNothing().when(ragService).updateKnowledgeBase(eq(TEST_EMAIL), eq(1L), eq("新名称"), eq("新描述"));

            mockMvc.perform(put("/rag/knowledge-bases/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"新名称\",\"description\":\"新描述\"}")
                            .requestAttr("userEmail", TEST_EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("DELETE /rag/knowledge-bases/{id}：软删除知识库 → 200")
        void shouldDeleteKnowledgeBase() throws Exception {
            doNothing().when(ragService).deleteKnowledgeBase(TEST_EMAIL, 1L);

            mockMvc.perform(delete("/rag/knowledge-bases/1")
                            .requestAttr("userEmail", TEST_EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    @Nested
    @DisplayName("文档管理")
    class DocumentManagement {

        @Test
        @DisplayName("POST /rag/knowledge-bases/{id}/documents：上传文档 → 200")
        void shouldUploadDocument() throws Exception {
            Map<String, Object> result = new HashMap<>();
            result.put("id", 1L);
            result.put("filename", "test.pdf");
            result.put("status", "PENDING");
            when(ragService.uploadDocument(eq(TEST_EMAIL), eq(1L), any()))
                    .thenReturn(result);

            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "test content".getBytes()
            );

            mockMvc.perform(multipart("/rag/knowledge-bases/1/documents")
                            .file(file)
                            .requestAttr("userEmail", TEST_EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.msg").value("文档已上传，正在处理中"));
        }

        @Test
        @DisplayName("GET /rag/knowledge-bases/{id}/documents：列出文档 → 200")
        void shouldListDocuments() throws Exception {
            List<Map<String, Object>> docs = new ArrayList<>();
            Map<String, Object> doc = new HashMap<>();
            doc.put("id", 1L);
            doc.put("filename", "readme.md");
            doc.put("status", "COMPLETED");
            docs.add(doc);
            when(ragService.listDocuments(TEST_EMAIL, 1L)).thenReturn(docs);

            mockMvc.perform(get("/rag/knowledge-bases/1/documents")
                            .requestAttr("userEmail", TEST_EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].filename").value("readme.md"));
        }

        @Test
        @DisplayName("DELETE /rag/knowledge-bases/{id}/documents/{docId}：删除文档 → 200")
        void shouldDeleteDocument() throws Exception {
            doNothing().when(ragService).deleteDocument(TEST_EMAIL, 1L, 2L);

            mockMvc.perform(delete("/rag/knowledge-bases/1/documents/2")
                            .requestAttr("userEmail", TEST_EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    @Nested
    @DisplayName("检索查询")
    class QueryTests {

        @Test
        @DisplayName("POST /rag/knowledge-bases/{id}/query：检索知识库 → 200 返回相关块")
        void shouldQueryKnowledgeBase() throws Exception {
            Map<String, Object> kb = new HashMap<>();
            kb.put("id", 1L);
            kb.put("name", "测试");
            when(ragService.getKnowledgeBase(TEST_EMAIL, 1L)).thenReturn(kb);

            List<Map<String, Object>> results = new ArrayList<>();
            Map<String, Object> chunk = new HashMap<>();
            chunk.put("content", "Spring Boot 是一个框架...");
            chunk.put("filename", "readme.md");
            chunk.put("score", 0.85);
            results.add(chunk);
            when(ragService.searchChunks(eq(1L), eq("Spring Boot"), eq(5), eq(0.3)))
                    .thenReturn(results);

            mockMvc.perform(post("/rag/knowledge-bases/1/query")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"query\":\"Spring Boot\",\"maxResults\":5,\"minScore\":0.3}")
                            .requestAttr("userEmail", TEST_EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[0].score").value(0.85))
                    .andExpect(jsonPath("$.data[0].filename").value("readme.md"));
        }
    }
}
