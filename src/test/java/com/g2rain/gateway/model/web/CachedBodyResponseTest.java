package com.g2rain.gateway.model.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("缓存响应体测试")
public class CachedBodyResponseTest {

    private ServerHttpResponse mockResponse;
    private DefaultDataBufferFactory bufferFactory;

    @BeforeEach
    void setUp() {
        mockResponse = new MockServerHttpResponse();
        bufferFactory = new DefaultDataBufferFactory();
    }

    @Test
    @DisplayName("测试构造函数")
    void testConstructor() {
        CachedBodyResponse cachedBodyResponse = new CachedBodyResponse(mockResponse);

        assertNotNull(cachedBodyResponse);
        assertArrayEquals(new byte[0], cachedBodyResponse.getBody());
    }

    @Test
    @DisplayName("测试获取请求头")
    void testGetHeaders() {
        CachedBodyResponse cachedBodyResponse = new CachedBodyResponse(mockResponse);

        HttpHeaders headers = cachedBodyResponse.getHeaders();
        assertNotNull(headers);
    }

    @Test
    @DisplayName("测试刷新空响应体")
    void testFlushEmptyBody() {
        CachedBodyResponse cachedBodyResponse = new CachedBodyResponse(mockResponse);

        Mono<Void> result = cachedBodyResponse.flush();

        // 使用传统方式验证完成
        assertDoesNotThrow(() -> result.block());
    }

    @Test
    @DisplayName("测试刷新非空响应体")
    void testFlushNonEmptyBody() {
        CachedBodyResponse cachedBodyResponse = new CachedBodyResponse(mockResponse);
        byte[] testData = "test response".getBytes(StandardCharsets.UTF_8);
        cachedBodyResponse.refresh(testData);

        Mono<Void> result = cachedBodyResponse.flush();

        // 因为MockServerHttpResponse的writeWith方法实现不完整，这里只验证不抛出异常
        assertDoesNotThrow(() -> result.block());
        assertArrayEquals(testData, cachedBodyResponse.getBody());
    }

    @Test
    @DisplayName("测试刷新方法")
    void testRefresh() {
        CachedBodyResponse cachedBodyResponse = new CachedBodyResponse(mockResponse);
        byte[] testData = "test data".getBytes(StandardCharsets.UTF_8);

        cachedBodyResponse.refresh(testData);

        assertArrayEquals(testData, cachedBodyResponse.getBody());
        assertEquals(testData.length, cachedBodyResponse.getHeaders().getContentLength());
    }

    @Test
    @DisplayName("测试刷新空数据")
    void testRefreshWithNull() {
        CachedBodyResponse cachedBodyResponse = new CachedBodyResponse(mockResponse);

        cachedBodyResponse.refresh(null);

        assertArrayEquals(new byte[0], cachedBodyResponse.getBody());
    }

    @Test
    @DisplayName("测试非JSON响应不缓存")
    void testNonJsonResponse() {
        CachedBodyResponse cachedBodyResponse = new CachedBodyResponse(mockResponse);
        mockResponse.getHeaders().setContentType(MediaType.TEXT_PLAIN);

        DataBuffer dataBuffer = bufferFactory.wrap("test".getBytes(StandardCharsets.UTF_8));
        Mono<Void> result = cachedBodyResponse.writeWith(Mono.just(dataBuffer));

        // 使用传统方式验证完成
        assertDoesNotThrow(() -> result.block());

        // 非JSON响应不应该缓存body
        assertArrayEquals(new byte[0], cachedBodyResponse.getBody());
    }
}
