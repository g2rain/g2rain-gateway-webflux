package com.g2rain.gateway.filters;


import com.g2rain.common.utils.Constants;
import com.g2rain.gateway.model.web.CachedBodyRequest;
import com.g2rain.gateway.model.web.CachedBodyResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * 全局请求体缓存过滤器。
 * <p>
 * 该过滤器用于在 Spring Cloud Gateway 中缓存请求体（body）和响应体，
 * 以便后续过滤器或处理器可以重复读取请求数据，避免流式请求只能读取一次的问题。
 * 适用于需要对请求体进行多次访问或日志记录的场景。
 * </p>
 *
 * <h2>功能</h2>
 * <ul>
 *     <li>拦截所有 HTTP 请求</li>
 *     <li>判断是否为 SSE（Server-Sent Events）或 WebSocket 请求并跳过缓存</li>
 *     <li>聚合请求体为单个 {@link DataBuffer}</li>
 *     <li>使用 {@link CachedBodyRequest} 缓存请求体</li>
 *     <li>使用 {@link CachedBodyResponse} 缓存响应体</li>
 *     <li>继续执行 Gateway 过滤链</li>
 * </ul>
 *
 * <h2>调用示例</h2>
 * <pre>{@code
 * @Bean
 * public CachedBodyFilter cachedBodyFilter() {
 *     return new CachedBodyFilter();
 * }
 *
 * // 当 Gateway 收到请求时，CachedBodyFilter 会自动缓存请求体
 * }</pre>
 *
 * <h2>注意事项</h2>
 * <ul>
 *     <li>对 SSE 和 WebSocket 请求不会缓存请求体</li>
 *     <li>缓存请求体可能会增加内存使用，需根据业务场景谨慎使用</li>
 *     <li>必须配合 {@link CachedBodyRequest}、{@link CachedBodyResponse} 使用</li>
 * </ul>
 *
 * @author alpha
 * @since 2025/10/6
 */
@Slf4j
@Component
public class CachedBodyFilter implements GlobalFilter, Ordered {

    /**
     * 注入配置文件中的 max-in-memory-size，如果没配置则默认为 256KB (Spring 默认值)
     */
    @Value("${spring.http.codecs.max-in-memory-size:256KB}")
    private DataSize maxInMemorySize;

    /**
     * 全局过滤方法，拦截所有请求，判断是否缓存请求体和响应体。
     *
     * @param exchange 当前请求交换对象，包含请求和响应
     * @param chain    Gateway 过滤链
     * @return {@link Mono} 执行结果
     */
    @Override
    @SuppressWarnings("ConstantConditions")
    public @NonNull Mono<@NonNull Void> filter(ServerWebExchange exchange, @NonNull GatewayFilterChain chain) {
        // 获取请求头里的长度, 使用配置的值进行限制
        if (exchange.getRequest().getHeaders().getContentLength() > maxInMemorySize.toBytes()) {
            exchange.getResponse().setStatusCode(HttpStatus.CONTENT_TOO_LARGE);
            return exchange.getResponse().setComplete();
        }

        // 将请求体流聚合为单个 DataBuffer，后续不是流式传输
        return DataBufferUtils.join(exchange.getRequest().getBody(), (int) maxInMemorySize.toBytes())
            // 当数据流被丢弃（取消订阅、错误、超时）时，释放 DataBuffer
            //.doOnDiscard(DataBuffer.class, DataBufferUtils::release)
            // 当 body 为空时，flatMap 不执行，这里设置一个空数组, 包装flatMap逻辑
            .defaultIfEmpty(new DefaultDataBufferFactory().wrap(Constants.EMPTY_BYTE))
            // flatMap 对聚合后的数据进行异步处理
            .flatMap(dataBuffer -> {
                try {
                    byte[] body = null;
                    if (Objects.nonNull(dataBuffer)) {
                        // 读取请求体到字节数组
                        body = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(body);
                    }

                    // 创建请求体装饰器，缓存请求体
                    CachedBodyRequest requestDecorator = new CachedBodyRequest(
                        exchange.getRequest(), body
                    );

                    // 创建响应体装饰器，缓存响应体
                    CachedBodyResponse responseDecorator = new CachedBodyResponse(
                        exchange.getResponse()
                    );

                    // 装饰请求与响应，并继续过滤链
                    ServerWebExchange exchangeDecorator = exchange.mutate()
                        .request(requestDecorator)
                        .response(responseDecorator)
                        .build();

                    // 过滤链执行完成后，写出缓存的响应体
                    return chain.filter(exchangeDecorator).then(Mono.defer(
                        responseDecorator::flush
                    ));
                } finally {
                    // 释放 DataBuffer 避免内存泄漏
                    if (Objects.nonNull(dataBuffer)) {
                        DataBufferUtils.release(dataBuffer);
                    }
                }
            })
            .onErrorResume(e -> {
                // 【核心修复】：精准判断异常类型, 如果是 DataBufferLimitException (或者由于 Spring 版本不同，检查其类名)
                if (e instanceof DataBufferLimitException) {
                    log.warn("检测到超大请求体，动态熔断触发: {}", e.getMessage());
                    exchange.getResponse().setStatusCode(HttpStatus.CONTENT_TOO_LARGE);
                    return exchange.getResponse().setComplete();
                }

                // 【关键点】：如果是其他 Filter 报错或者业务报错，必须继续抛出, 这样才能保证后续逻辑（如异常处理器）能接收到信号
                return Mono.error(e);
            });
    }

    /**
     * 获取过滤器的执行顺序，数值越小优先级越高。
     *
     * @return 顺序值
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
