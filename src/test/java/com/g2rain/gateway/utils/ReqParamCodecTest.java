package com.g2rain.gateway.utils;

import com.g2rain.gateway.model.web.CachedBodyRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("请求参数编解码器测试")
public class ReqParamCodecTest {

    @Test
    @DisplayName("测试处理表单URL编码体")
    void testProcessFormUrlEncodedBody() {
        // 准备测试数据
        String formData = "name=John%20Doe&age=30&city=New%20York";
        MockServerHttpRequest mockRequest = MockServerHttpRequest.post("/test").build();
        CachedBodyRequest request = new CachedBodyRequest(mockRequest, formData.getBytes(StandardCharsets.UTF_8));

        // 执行测试
        MultiValueMap<String, String> result = ReqParamCodec.processFormUrlEncodedBody(request);

        // 验证结果
        assertNotNull(result);
        assertEquals("John Doe", result.getFirst("name"));
        assertEquals("30", result.getFirst("age"));
        assertEquals("New York", result.getFirst("city"));
    }

    @Test
    @DisplayName("测试处理空表单URL编码体")
    void testProcessEmptyFormUrlEncodedBody() {
        // 准备测试数据
        String formData = "";
        MockServerHttpRequest mockRequest = MockServerHttpRequest.post("/test").build();
        CachedBodyRequest request = new CachedBodyRequest(mockRequest, formData.getBytes(StandardCharsets.UTF_8));

        // 执行测试
        MultiValueMap<String, String> result = ReqParamCodec.processFormUrlEncodedBody(request);

        // 验证结果
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("测试处理单个参数的表单URL编码体")
    void testProcessSingleParamFormUrlEncodedBody() {
        // 准备测试数据
        String formData = "username=admin";
        MockServerHttpRequest mockRequest = MockServerHttpRequest.post("/test").build();
        CachedBodyRequest request = new CachedBodyRequest(mockRequest, formData.getBytes(StandardCharsets.UTF_8));

        // 执行测试
        MultiValueMap<String, String> result = ReqParamCodec.processFormUrlEncodedBody(request);

        // 验证结果
        assertNotNull(result);
        assertEquals("admin", result.getFirst("username"));
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("测试构造函数私有性")
    void testConstructorIsPrivate() throws Exception {
        // 获取私有构造函数
        java.lang.reflect.Constructor<ReqParamCodec> constructor = ReqParamCodec.class.getDeclaredConstructor();
        assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));

        // 确保可以访问私有构造函数
        constructor.setAccessible(true);

        // 尝试创建实例应该成功（虽然不建议在实际代码中使用）
        assertDoesNotThrow(() -> constructor.newInstance());
    }
}
