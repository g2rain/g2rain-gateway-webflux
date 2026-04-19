package com.g2rain.gateway.filters;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("缓存请求体过滤器测试")
public class CachedBodyFilterTest {

    private CachedBodyFilter cachedBodyFilter;

    @Mock
    private GatewayFilterChain chain;

    private DataBufferFactory bufferFactory;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        cachedBodyFilter = new CachedBodyFilter();
        // 单元测试未走 Spring 容器，@Value 不会注入，手动设置与默认配置一致的值
        ReflectionTestUtils.setField(cachedBodyFilter, "maxInMemorySize", DataSize.parse("256KB"));
        bufferFactory = new DefaultDataBufferFactory();
    }

    @Test
    @DisplayName("测试过滤器顺序")
    void testGetOrder() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 100, cachedBodyFilter.getOrder());
    }

    @Test
    @DisplayName("测试普通请求处理")
    void testNormalRequestHandling() {
        // 准备普通请求
        String requestBody = "{\"name\":\"test\"}";
        MockServerHttpRequest request = MockServerHttpRequest.post("/test")
            .header("Content-Type", "application/json")
            .body(requestBody);
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        MockServerHttpResponse response = new MockServerHttpResponse();
        ServerWebExchange exchangeMutated = exchange.mutate().response(response).build();

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // 执行测试
        Mono<Void> result = cachedBodyFilter.filter(exchangeMutated, chain);

        // 验证结果（由于Mock的限制，这里主要验证不抛出异常）
        assertDoesNotThrow(() -> result.block());
    }

    @Test
    @DisplayName("测试空请求体处理")
    void testEmptyRequestBodyHandling() {
        // 准备空请求体
        MockServerHttpRequest request = MockServerHttpRequest.post("/test")
            .header("Content-Type", "application/json")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        MockServerHttpResponse response = new MockServerHttpResponse();
        ServerWebExchange exchangeMutated = exchange.mutate().response(response).build();

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // 执行测试
        Mono<Void> result = cachedBodyFilter.filter(exchangeMutated, chain);

        // 验证结果
        assertDoesNotThrow(() -> result.block());
    }
}
