package com.g2rain.gateway.model.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("缓存请求体测试")
public class CachedBodyRequestTest {

    private ServerHttpRequest mockRequest;
    private byte[] testBody;

    @BeforeEach
    void setUp() {
        mockRequest = MockServerHttpRequest.get("/test").build();
        testBody = "test body content".getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("测试构造函数和基本属性")
    void testConstructor() {
        CachedBodyRequest cachedBodyRequest = new CachedBodyRequest(mockRequest, testBody);

        assertNotNull(cachedBodyRequest);
        assertArrayEquals(testBody, cachedBodyRequest.asBytes());
    }

    @Test
    @DisplayName("测试空请求体")
    void testEmptyBody() {
        CachedBodyRequest cachedBodyRequest = new CachedBodyRequest(mockRequest, null);

        assertNotNull(cachedBodyRequest);
        assertArrayEquals(new byte[0], cachedBodyRequest.asBytes());
    }

    @Test
    @DisplayName("测试获取请求头")
    void testGetHeaders() {
        CachedBodyRequest cachedBodyRequest = new CachedBodyRequest(mockRequest, testBody);

        HttpHeaders headers = cachedBodyRequest.getHeaders();
        assertNotNull(headers);
        assertEquals(testBody.length, headers.getContentLength());
        assertFalse(headers.containsHeader(HttpHeaders.TRANSFER_ENCODING));
    }

    @Test
    @DisplayName("测试获取请求体")
    void testGetBody() {
        CachedBodyRequest cachedBodyRequest = new CachedBodyRequest(mockRequest, testBody);

        Flux<DataBuffer> bodyFlux = cachedBodyRequest.getBody();

        // 收集 Flux 中的所有数据
        List<String> results = new ArrayList<>();
        bodyFlux.subscribe(dataBuffer -> {
            byte[] bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            results.add(new String(bytes, StandardCharsets.UTF_8));
        });

        // 验证结果
        assertEquals(1, results.size());
        assertEquals("test body content", results.getFirst());
    }

    @Test
    @DisplayName("测试空请求体的Flux")
    void testGetEmptyBody() {
        CachedBodyRequest cachedBodyRequest = new CachedBodyRequest(mockRequest, null);

        Flux<DataBuffer> bodyFlux = cachedBodyRequest.getBody();

        // 收集 Flux 中的所有数据
        List<String> results = new ArrayList<>();
        bodyFlux.subscribe(dataBuffer -> {
            byte[] bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            results.add(new String(bytes, StandardCharsets.UTF_8));
        });

        // 验证结果为空
        assertTrue(results.isEmpty());
    }
}
