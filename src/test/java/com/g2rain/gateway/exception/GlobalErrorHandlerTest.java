package com.g2rain.gateway.exception;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.ExceptionProcessor;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.model.Result;
import com.g2rain.gateway.utils.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(Constants.ACCEPT_LANGUAGE_ATTRIBUTE, "zh-CN");

        BusinessException exception = new BusinessException(SystemErrorCode.PARAM_REQUIRED, "testParam");

        Result<Void> result = Result.error(String.valueOf(SystemErrorCode.PARAM_REQUIRED.code()), "参数不允许为空");
        when(exceptionProcessor.process(any(BusinessException.class), eq("zh-CN"))).thenReturn(result);

        Mono<Void> monoResult = globalErrorHandler.handle(exchange, exception);

        assertDoesNotThrow(() -> monoResult.block());
        assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, exchange.getResponse().getHeaders().getContentType());
        verify(exceptionProcessor).process(any(BusinessException.class), eq("zh-CN"));
    }

    @Test
    @DisplayName("测试处理普通异常")
    void testHandleRegularException() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(Constants.ACCEPT_LANGUAGE_ATTRIBUTE, "zh-CN");

        Exception exception = new RuntimeException("普通异常");

        Result<Void> result = Result.error("500", "系统内部错误");
        when(exceptionProcessor.process(any(BusinessException.class), eq("zh-CN"))).thenReturn(result);

        Mono<Void> monoResult = globalErrorHandler.handle(exchange, exception);

        assertDoesNotThrow(() -> monoResult.block());
        assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, exchange.getResponse().getHeaders().getContentType());
        verify(exceptionProcessor).process(any(BusinessException.class), eq("zh-CN"));
    }

    @Test
    @DisplayName("测试处理已提交的响应")
    void testHandleCommittedResponse() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(Constants.ACCEPT_LANGUAGE_ATTRIBUTE, "zh-CN");

        MockServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.setComplete();

        Exception exception = new RuntimeException("异常");

        Mono<Void> monoResult = globalErrorHandler.handle(exchange, exception);

        assertThrows(RuntimeException.class, () -> monoResult.block());
    }
}
