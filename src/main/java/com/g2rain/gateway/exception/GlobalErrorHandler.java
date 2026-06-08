package com.g2rain.gateway.exception;


import com.g2rain.common.exception.BaseError;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.ExceptionProcessor;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.json.FailIgnoreFieldMixIn;
import com.g2rain.common.json.JsonCodec;
import com.g2rain.common.json.JsonCodecBuilder;
import com.g2rain.common.model.Result;
import com.g2rain.gateway.model.context.EdgePrincipalContextHolder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * <p>{@code GlobalErrorHandler} 是全局异常处理器，用于统一处理 WebFlux 环境下的异常，
 * 并将异常信息以 JSON 格式返回给客户端。</p>
 * <p>
 * 它实现了 {@link ErrorWebExceptionHandler} 接口，可拦截所有未被捕获的异常，
 * 并通过 {@link ExceptionProcessor} 转换为标准化的 {@link Result} 响应。
 * </p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * @Bean
 * public GlobalErrorHandler globalErrorHandler(ExceptionProcessor exceptionProcessor) {
 *     return new GlobalErrorHandler(exceptionProcessor);
 * }
 * }</pre>
 * <p>
 * 功能说明：
 * <ul>
 *     <li>拦截全局未处理异常</li>
 *     <li>将业务异常 {@link BusinessException} 转换为 {@link Result} JSON 响应</li>
 *     <li>日志记录异常信息</li>
 *     <li>确保响应的 HTTP 状态码为 {@code 200 OK}，并设置 {@code Content-Type} 为 {@code application/json}</li>
 * </ul>
 * <p>
 * 示例日志：
 * <pre>{@code
 * log.error("全局异常处理: {}", ex.getMessage(), ex);
 * }</pre>
 *
 * @author alpha
 * @since 2025/9/26
 */
@Slf4j
public class GlobalErrorHandler implements ErrorWebExceptionHandler, Ordered {
    /**
     * JSON 编码器，定制了对象的序列化规则
     */
    private final JsonCodec jsonSerializer = JsonCodecBuilder.builder()
        .withDefaults()
        .withConfig(jsonMapper -> {
            jsonMapper.addMixIn(Result.class, FailIgnoreFieldMixIn.class);
            jsonMapper.addMixIn(BaseError.class, FailIgnoreFieldMixIn.class);
        })
        .build();

    /**
     * 异常处理器，用于将异常转换为统一的结果
     */
    private final ExceptionProcessor exceptionProcessor;

    public GlobalErrorHandler(ExceptionProcessor exceptionProcessor) {
        this.exceptionProcessor = exceptionProcessor;
    }

    /**
     * 全局异常处理方法。
     *
     * @param exchange 当前的 WebExchange 上下文
     * @param ex       待处理的异常
     * @return 处理完成的 Mono<Void>
     */
    @Override
    public @NonNull Mono<@NonNull Void> handle(@NonNull ServerWebExchange exchange, @NonNull Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        log.error("全局异常处理-{}: {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);

        // 响应已提交，无法处理
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        return EdgePrincipalContextHolder.get().flatMap(context -> {
            String requestAcceptLanguage = exchange.getRequest().getHeaders().getFirst(HttpHeaders.ACCEPT_LANGUAGE);
            String errorCode = ex instanceof BusinessException businessException
                ? businessException.getErrorCode()
                : null;
            log.warn("[GlobalErrorHandler] acceptLanguage={} | requestAcceptLanguage={} | errorCode={}",
                context.getAcceptLanguage(), requestAcceptLanguage, errorCode);

            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            // 设置 HTTP 状态码和 Content-Type
            response.setStatusCode(HttpStatus.OK);

            String json;
            if (ex instanceof BusinessException businessException) {
                json = jsonSerializer.obj2str(exceptionProcessor.process(
                    businessException, context.getAcceptLanguage()
                ));
            } else {
                BusinessException wrapped = new BusinessException(
                    SystemErrorCode.SYSTEM_INTERNAL_ERROR,
                    ex.getMessage()
                );
                json = jsonSerializer.obj2str(exceptionProcessor.process(
                    wrapped, context.getAcceptLanguage()
                ));
            }

            DataBuffer buffer = response.bufferFactory().wrap(
                json.getBytes(StandardCharsets.UTF_8)
            );
            return response.writeWith(Mono.just(buffer));
        });
    }

    /**
     * 返回该异常处理器的优先级，值越大优先级越低。
     *
     * @return 优先级值
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
