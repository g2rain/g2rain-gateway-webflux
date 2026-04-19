package com.g2rain.gateway.filters;

import com.g2rain.common.json.JsonCodec;
import com.g2rain.common.json.JsonCodecBuilder;
import com.g2rain.common.model.Result;
import com.g2rain.gateway.cache.AppName;
import com.g2rain.gateway.cache.OrganName;
import com.g2rain.gateway.model.web.CachedBodyResponse;
import com.g2rain.gateway.whitelist.WhiteListResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@DisplayName("响应调整过滤器测试")
public class ResponseAdjustFilterTest {

    private ResponseAdjustFilter responseAdjustFilter;
    private final JsonCodec jsonSerializer = JsonCodecBuilder.builder().withDefaults().build();

    @Mock
    private WhiteListResolver whiteListResolver;

    @Mock
    private GatewayFilterChain chain;
    @Mock
    private AppName appName;
    @Mock
    private OrganName organName;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(appName.getNames(any())).thenReturn(Mono.just(Map.of()));
        when(organName.getNames(any())).thenReturn(Mono.just(Map.of()));
        responseAdjustFilter = new ResponseAdjustFilter(whiteListResolver, appName, organName);
    }

    @Test
    @DisplayName("测试过滤器顺序")
    void testGetOrder() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 800, responseAdjustFilter.getOrder());
    }

    @Test
    @DisplayName("测试白名单请求直接通过")
    void testWhiteListPassThrough() {
        // 准备普通响应
        MockServerHttpResponse mockResponse = new MockServerHttpResponse();
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/test").build()
        ).mutate().response(mockResponse).build();

        // 设置白名单匹配
        when(whiteListResolver.shouldExclude(anyString(), any(ServerWebExchange.class))).thenReturn(true);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // 执行测试
        Mono<Void> result = responseAdjustFilter.filter(exchange, chain);

        // 验证结果
        assertDoesNotThrow(() -> result.block());
        verify(chain).filter(exchange);
        verify(whiteListResolver).shouldExclude("ResponseAdjustFilter", exchange);
    }

    @Test
    @DisplayName("测试非缓存响应体直接跳过")
    void testNonCachedBodyResponse() {
        // 准备普通响应
        MockServerHttpResponse mockResponse = new MockServerHttpResponse();
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/test").build()
        ).mutate().response(mockResponse).build();

        when(whiteListResolver.shouldExclude(anyString(), any(ServerWebExchange.class))).thenReturn(false);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // 执行测试
        Mono<Void> result = responseAdjustFilter.filter(exchange, chain);

        // 验证结果
        assertDoesNotThrow(() -> result.block());
        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("测试空缓存响应体跳过处理")
    void testEmptyCachedBody() {
        // 准备缓存响应体
        CachedBodyResponse cachedResponse = new CachedBodyResponse(new MockServerHttpResponse());
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/test").build()
        ).mutate().response(cachedResponse).build();

        when(whiteListResolver.shouldExclude(anyString(), any(ServerWebExchange.class))).thenReturn(false);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // 执行测试
        Mono<Void> result = responseAdjustFilter.filter(exchange, chain);

        // 验证结果
        assertDoesNotThrow(() -> result.block());
    }

    @Test
    @DisplayName("测试成功响应的数据调整")
    void testSuccessResponseDataAdjustment() {
        // 准备测试数据
        ObjectNode dataNode = JsonNodeFactory.instance.objectNode();
        dataNode.put("companyOrganId", "123");
        dataNode.put("tenantId", "456");

        Result<tools.jackson.databind.JsonNode> result = Result.success(dataNode);
        byte[] body = jsonSerializer.obj2byte(result);

        CachedBodyResponse cachedResponse = new CachedBodyResponse(new MockServerHttpResponse());
        cachedResponse.refresh(body);

        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/test").build()
        ).mutate().response(cachedResponse).build();

        when(whiteListResolver.shouldExclude(anyString(), any(ServerWebExchange.class))).thenReturn(false);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // 执行测试
        Mono<Void> resultMono = responseAdjustFilter.filter(exchange, chain);

        // 验证结果
        assertDoesNotThrow(() -> resultMono.block());
    }

    @Test
    @DisplayName("测试失败响应转换为异常")
    void testErrorResponseConversion() {
        // 准备失败响应
        Result<tools.jackson.databind.JsonNode> result = Result.error("500", "Internal Server Error");
        byte[] body = jsonSerializer.obj2byte(result);

        CachedBodyResponse cachedResponse = new CachedBodyResponse(new MockServerHttpResponse());
        cachedResponse.refresh(body);

        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/test").build()
        ).mutate().response(cachedResponse).build();

        when(whiteListResolver.shouldExclude(anyString(), any(ServerWebExchange.class))).thenReturn(false);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // 执行测试
        Mono<Void> resultMono = responseAdjustFilter.filter(exchange, chain);

        // 验证结果 - 由于会抛出异常，这里验证不会正常完成
        assertThrows(Exception.class, () -> resultMono.block());
    }
}
