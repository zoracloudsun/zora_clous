package com.zyt.config;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Springfox → SpringDoc 兼容端点
 *
 * Knife4j 旧版前端会先请求 /swagger-resources 获取 API 文档组列表，
 * 再根据返回的 url 字段拉取具体的 OpenAPI spec。
 * SpringDoc 不提供此端点，这里手动返回兼容格式，指向 /v3/api-docs。
 */
@Hidden
@RestController
public class SwaggerCompatController {

    @GetMapping(value = "/swagger-resources", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, String>> swaggerResources() {
        return List.of(
                Map.of(
                        "name", "default",
                        "url", "/v3/api-docs",
                        "swaggerVersion", "3.0"
                )
        );
    }
}
