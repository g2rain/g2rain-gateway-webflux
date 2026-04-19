package com.g2rain.gateway.filters;

import com.g2rain.gateway.components.KafkaLogSender;
import com.g2rain.gateway.model.web.CachedBodyRequest;
import com.g2rain.gateway.model.web.CachedBodyResponse;
import com.g2rain.gateway.whitelist.WhiteListResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("跟踪日志过滤器测试")
public class TraceLoggingFilterTest {

    private TraceLoggingFilter traceLoggingFilter;

    @Mock
    private WhiteListResolver whiteListResolver;

    @Mock
    private KafkaLogSender kafkaLogSender;

    @Mock
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        traceLoggingFilter = new TraceLoggingFilter(kafkaLogSender, whiteListResolver);
    }

    @Test
    @DisplayName("测试过滤器顺序")
    void testGetOrder() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 200, traceLoggingFilter.getOrder());
    }

    @Test
    @DisplayName("测试白名单请求直接通过")
    void testWhiteListPassThrough() {
        // 准备请求
        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // 设置白名单匹配
        when(whiteListResolver.shouldExclude(anyString(), any(ServerWebExchange.class))).thenReturn(true);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // 执行测试
        Mono<Void> result = traceLoggingFilter.filter(exchange, chain);

        // 验证结果
        assertDoesNotThrow(() -> result.block());
        verify(chain).filter(exchange);
        verify(whiteListResolver).shouldExclude("TraceLoggingFilter", exchange);
    }

    @Test
    @DisplayName("测试过滤器执行")
    void testFilter() {
        // 准备请求
        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(whiteListResolver.shouldExclude(anyString(), any(ServerWebExchange.class))).thenReturn(false);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // 执行测试
        Mono<Void> result = traceLoggingFilter.filter(exchange, chain);

        // 验证结果
        assertDoesNotThrow(() -> result.block());
        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("测试JSON请求体处理")
    void testJsonRequestBodyProcessing() {
        // 准备JSON请求
        String jsonBody = "{\"name\":\"test\",\"value\":123}";
        MockServerHttpRequest request = MockServerHttpRequest.post("/test")
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .body(jsonBody);

        CachedBodyRequest cachedBodyRequest = new CachedBodyRequest(request, jsonBody.getBytes());

        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/test")
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(jsonBody)
        ).mutate().request(cachedBodyRequest).build();

        when(whiteListResolver.shouldExclude(anyString(), any(ServerWebExchange.class))).thenReturn(false);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // 执行测试
        Mono<Void> result = traceLoggingFilter.filter(exchange, chain);

        // 验证结果
        assertDoesNotThrow(() -> result.block());
    }

    @Test
    @DisplayName("测试表单URL编码请求体处理")
    void testFormUrlEncodedBodyProcessing() {
        // 准备表单请求
        String formBody = "name=test&value=123";
        MockServerHttpRequest request = MockServerHttpRequest.post("/test")
            .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .body(formBody);

        CachedBodyRequest cachedBodyRequest = new CachedBodyRequest(request, formBody.getBytes());

        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/test")
                .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .body(formBody)
        ).mutate().request(cachedBodyRequest).build();

        when(whiteListResolver.shouldExclude(anyString(), any(ServerWebExchange.class))).thenReturn(false);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // 执行测试
        Mono<Void> result = traceLoggingFilter.filter(exchange, chain);

        // 验证结果
        assertDoesNotThrow(() -> result.block());
    }

    @Test
    @DisplayName("测试响应日志处理")
    void testResponseLogging() {
        // 准备响应
        MockServerHttpResponse response = new MockServerHttpResponse();
        CachedBodyResponse cachedBodyResponse = new CachedBodyResponse(response);
        cachedBodyResponse.refresh("{\"status\":\"success\"}".getBytes());

        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/test").build()
        ).mutate().response(cachedBodyResponse).build();

        when(whiteListResolver.shouldExclude(anyString(), any(ServerWebExchange.class))).thenReturn(false);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // 执行测试
        Mono<Void> result = traceLoggingFilter.filter(exchange, chain);

        // 验证结果
        assertDoesNotThrow(() -> result.block());
    }

    @Test
    @DisplayName("测试空响应体日志处理")
    void testEmptyResponseBodyLogging() {
        // 准备空响应体
        MockServerHttpResponse response = new MockServerHttpResponse();
        CachedBodyResponse cachedBodyResponse = new CachedBodyResponse(response);

        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/test").build()
        ).mutate().response(cachedBodyResponse).build();

        when(whiteListResolver.shouldExclude(anyString(), any(ServerWebExchange.class))).thenReturn(false);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // 执行测试
        Mono<Void> result = traceLoggingFilter.filter(exchange, chain);

        // 验证结果
        assertDoesNotThrow(() -> result.block());
    }
}
