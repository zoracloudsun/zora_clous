package com.zora.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.zora.config.SwaggerCompatController;
import com.zora.exception.GlobalExceptionHandler;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SwaggerCompatController 单元测试
 * 验证 /swagger-resources 兼容端点返回正确的 Springfox 格式
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Swagger 兼容端点")
class SwaggerCompatControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SwaggerCompatController controller = new SwaggerCompatController();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("GET /swagger-resources")
    class GetSwaggerResources {

        @Test
        @DisplayName("返回 JSON 数组，包含一个文档组")
        void returnsArrayWithOneGroup() throws Exception {
            mockMvc.perform(get("/swagger-resources"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("application/json"))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @DisplayName("文档组 name 为 default")
        void groupNameIsDefault() throws Exception {
            mockMvc.perform(get("/swagger-resources"))
                    .andExpect(jsonPath("$[0].name").value("default"));
        }

        @Test
        @DisplayName("文档组 url 指向 /v3/api-docs")
        void groupUrlPointsToV3ApiDocs() throws Exception {
            mockMvc.perform(get("/swagger-resources"))
                    .andExpect(jsonPath("$[0].url").value("/v3/api-docs"));
        }

        @Test
        @DisplayName("文档组 swaggerVersion 为 3.0")
        void swaggerVersionIs3() throws Exception {
            mockMvc.perform(get("/swagger-resources"))
                    .andExpect(jsonPath("$[0].swaggerVersion").value("3.0"));
        }
    }
}
