package com.g2rain.gateway.exception;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.ExceptionProcessor;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.model.Result;
import com.g2rain.gateway.model.context.EdgePrincipalContext;
import com.g2rain.gateway.model.context.EdgePrincipalContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("全局错误处理器测试")
public class GlobalErrorHandlerTest {

    private GlobalErrorHandler globalErrorHandler;

    @Mock
    private ExceptionProcessor exceptionProcessor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        globalErrorHandler = new GlobalErrorHandler(exceptionProcessor);
    }

    @Test
    @DisplayName("测试获取处理顺序")
    void testGetOrder() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE, globalErrorHandler.getOrder());
    }

    @Test
    @DisplayName("测试处理业务异常")
    void testHandleBusinessException() {
        // 准备测试数据
        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        BusinessException exception = new BusinessException(SystemErrorCode.PARAM_REQUIRED, "testParam");

        // 模拟异常处理器行为
        Result<Void> result = Result.error(String.valueOf(SystemErrorCode.PARAM_REQUIRED.code()), "参数不允许为空");
        when(exceptionProcessor.process(any(BusinessException.class), any())).thenReturn(result);

        // 创建并配置上下文
        EdgePrincipalContext context = new EdgePrincipalContext();
        context.setAcceptLanguage("zh-CN");

        // 执行处理
        Mono<Void> monoResult = globalErrorHandler.handle(exchange, exception)
                .contextWrite(ctx -> EdgePrincipalContextHolder.put(ctx, context));

        // 验证结果（由于是异步操作，这里主要验证不抛出异常）
        assertDoesNotThrow(() -> monoResult.block());

        // 验证响应设置
        assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, exchange.getResponse().getHeaders().getContentType());

        // 验证方法调用
        verify(exceptionProcessor).process(any(BusinessException.class), any());
    }

    @Test
    @DisplayName("测试处理普通异常")
    void testHandleRegularException() {
        // 准备测试数据
        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Exception exception = new RuntimeException("普通异常");

        // 模拟异常处理器行为
        Result<Void> result = Result.error("500", "系统内部错误");
        when(exceptionProcessor.process(any(BusinessException.class), any())).thenReturn(result);

        // 创建并配置上下文
        EdgePrincipalContext context = new EdgePrincipalContext();
        context.setAcceptLanguage("zh-CN");

        // 执行处理
        Mono<Void> monoResult = globalErrorHandler.handle(exchange, exception)
                .contextWrite(ctx -> EdgePrincipalContextHolder.put(ctx, context));

        // 验证结果
        assertDoesNotThrow(() -> monoResult.block());

        // 验证响应设置
        assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, exchange.getResponse().getHeaders().getContentType());
        
        // 验证方法调用
        verify(exceptionProcessor).process(any(BusinessException.class), any());
    }

    @Test
    @DisplayName("测试处理已提交的响应")
    void testHandleCommittedResponse() {
        // 准备测试数据
        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // 模拟已提交的响应
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Exception exception = new RuntimeException("异常");

        // 创建并配置上下文
        EdgePrincipalContext context = new EdgePrincipalContext();
        context.setAcceptLanguage("zh-CN");
        
        // 执行处理
        Mono<Void> monoResult = globalErrorHandler.handle(exchange, exception)
                .contextWrite(ctx -> EdgePrincipalContextHolder.put(ctx, context));

        // 验证结果应该抛出异常，因为响应已提交
        assertThrows(RuntimeException.class, () -> monoResult.block());
    }
}