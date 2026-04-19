package com.g2rain.gateway.filters;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.gateway.model.context.EdgePrincipalContext;
import com.g2rain.gateway.model.context.EdgePrincipalContextHolder;
import com.g2rain.gateway.model.web.CachedBodyRequest;
import com.g2rain.gateway.whitelist.WhiteListResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DisplayName("签名验证过滤器测试")
public class SignVerificationFilterTest {

    private SignVerificationFilter signVerificationFilter;

    @Mock
    private GatewayFilterChain chain;

    @Mock
    private WhiteListResolver whiteListResolver;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        signVerificationFilter = new SignVerificationFilter(whiteListResolver);
    }

    @Test
    @DisplayName("测试白名单直接通过")
    void testWhiteListPassThrough() {
        // 准备请求
        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // 设置白名单匹配
        when(whiteListResolver.shouldExclude(anyString(), any(ServerWebExchange.class))).thenReturn(true);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // 执行和验证
        assertDoesNotThrow(() -> {
            signVerificationFilter.filter(exchange, chain).block();
        });
    }

    @Test
    @DisplayName("测试不支持的哈希算法")
    void testUnsupportedHashAlgorithm() {
        // 准备请求
        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // 创建缓存请求体
        CachedBodyRequest cachedBodyRequest = new CachedBodyRequest(request, new byte[0]);
        ServerWebExchange mutatedExchange = exchange.mutate().request(cachedBodyRequest).build();

        when(whiteListResolver.shouldExclude(anyString(), any(ServerWebExchange.class))).thenReturn(false);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // 创建一个不支持的哈希算法
        EdgePrincipalContext context = EdgePrincipalContext.of();
        context.setHashAlgorithm("MD5"); // MD5不被支持

        // 验证抛出BusinessException异常，错误码应该是PARAM_INVALID(40008)
        Mono<Void> result = signVerificationFilter.filter(mutatedExchange, chain)
            .contextWrite(ctx -> EdgePrincipalContextHolder.put(ctx, context));
        BusinessException exception = assertThrows(BusinessException.class, () -> result.block());
        assertEquals(String.valueOf(SystemErrorCode.PARAM_VAL_INVALID.code()), exception.getErrorCode());
    }

    @Test
    @DisplayName("测试不支持的哈希算法")
    void testUnsupportedHashAlgorithm2() {
        // 准备请求
        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // 创建缓存请求体
        CachedBodyRequest cachedBodyRequest = new CachedBodyRequest(request, new byte[0]);
        ServerWebExchange mutatedExchange = exchange.mutate().request(cachedBodyRequest).build();

        when(whiteListResolver.shouldExclude(anyString(), any(ServerWebExchange.class))).thenReturn(false);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // 创建一个不支持的哈希算法
        EdgePrincipalContext context = EdgePrincipalContext.of();
        context.setHashAlgorithm("MD5"); // MD5不被支持

        // 验证抛出BusinessException异常，错误码应该是PARAM_INVALID(40008)
        Mono<Void> result = signVerificationFilter.filter(mutatedExchange, chain)
            .contextWrite(ctx -> EdgePrincipalContextHolder.put(ctx, context));
        BusinessException exception = assertThrows(BusinessException.class, () -> result.block());
        assertEquals(String.valueOf(SystemErrorCode.PARAM_VAL_INVALID.code()), exception.getErrorCode());
    }

    @Test
    @DisplayName("测试缺少参数哈希值时抛出异常")
    void testMissingParamHash() {
        // 准备请求
        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // 创建缓存请求体
        CachedBodyRequest cachedBodyRequest = new CachedBodyRequest(request, new byte[0]);
        ServerWebExchange mutatedExchange = exchange.mutate().request(cachedBodyRequest).build();

        when(whiteListResolver.shouldExclude(anyString(), any(ServerWebExchange.class))).thenReturn(false);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // 创建一个没有参数哈希值的上下文
        EdgePrincipalContext context = EdgePrincipalContext.of();
        context.setHashAlgorithm("SHA-256");
        context.setParamHashStr(null);

        // 验证抛出BusinessException异常
        Mono<Void> result = signVerificationFilter.filter(mutatedExchange, chain)
            .contextWrite(ctx -> EdgePrincipalContextHolder.put(ctx, context));
        BusinessException exception = assertThrows(BusinessException.class, () -> result.block());
        assertEquals(String.valueOf(SystemErrorCode.PARAM_REQUIRED.code()), exception.getErrorCode());
    }
}
