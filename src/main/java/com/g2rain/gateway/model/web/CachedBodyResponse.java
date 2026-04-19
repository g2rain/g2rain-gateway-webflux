package com.g2rain.gateway.model.web;


import com.g2rain.common.utils.Collections;
import com.g2rain.common.utils.Constants;
import lombok.Getter;
import lombok.NonNull;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.Optional;

/**
 * 装饰器响应，用于缓存 HTTP 响应体，以便在后续处理过程中可重复读取。
 * <p>
 * 本类继承自 {@link org.springframework.http.server.reactive.ServerHttpResponseDecorator}，
 * 在写入响应体时缓存其字节内容，并提供刷新和获取缓存的功能。
 * </p>
 *
 * <h2>功能</h2>
 * <ul>
 *     <li>缓存响应体，支持多次读取</li>
 *     <li>修正响应头，确保 Content-Length 与实际 body 长度一致</li>
 *     <li>仅缓存 JSON 响应，非 JSON 响应直接传递</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <p>
 * 在网关过滤器中使用 {@code CachedBodyResponse} 可实现对响应体的多次读取，
 * 例如日志记录、响应内容调整等场景。
 * </p>
 *
 * <h2>示例</h2>
 * <pre>{@code
 * ServerHttpResponse originalResponse = exchange.getResponse();
 * CachedBodyResponse cachedResponse = new CachedBodyResponse(originalResponse);
 * }</pre>
 *
 * @author alpha
 * @since 2025/10/6
 */
public class CachedBodyResponse extends ServerHttpResponseDecorator {

    /**
     * 数据缓冲工厂，用于创建 DataBuffer 对象
     */
    private final DataBufferFactory bufferFactory;

    /**
     * 缓存的响应体字节数组
     */
    @Getter
    private byte[] body = Constants.EMPTY_BYTE;

    /**
     * 构造器，装饰并缓存响应体。
     *
     * @param delegate 原始响应对象
     */
    public CachedBodyResponse(ServerHttpResponse delegate) {
        super(delegate);
        this.bufferFactory = delegate.bufferFactory();
    }

    /**
     * 写入响应体并缓存字节数据。
     * <p>
     * 非 JSON 响应直接传递，不缓存。
     * </p>
     *
     * @param body 数据流
     * @return {@link Mono} 表示写入完成
     */
    @Override
    public @NonNull Mono<@NonNull Void> writeWith(@NonNull Publisher<? extends DataBuffer> body) {
        // 非 JSON 响应直接传递，不缓存
        if (!isJsonResponse()) {
            return super.writeWith(body);
        }

        // 将响应体流式数据聚合为单个 DataBuffer
        return DataBufferUtils.join(body)
            // 当数据流被丢弃（取消订阅、错误、超时），执行释放资源
            .doOnDiscard(DataBuffer.class, DataBufferUtils::release)
            // flatMap 异步处理聚合的 DataBuffer
            .flatMap(dataBuffer -> {
                try {
                    // 读取响应体到 byte[]
                    this.body = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(this.body);
                    // 表示不发出任何元素，只关心副作用
                    return Mono.empty();
                } finally {
                    // 手动释放缓冲区
                    DataBufferUtils.release(dataBuffer);
                }
            });
    }

    /**
     * 刷新缓存的响应体并同步更新响应头。
     *
     * @param body 新的响应体字节数组
     */
    public void refresh(byte[] body) {
        this.body = Objects.nonNull(body) ? body : Constants.EMPTY_BYTE;

        // 同步更新 headers，确保 Content-Length 正确
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(super.getHeaders());
        headers.setContentLength(this.body.length);
        headers.remove(HttpHeaders.TRANSFER_ENCODING);
        super.getHeaders().putAll(headers);
    }

    /**
     * 将缓存的响应体写回输出流。
     *
     * @return {@link Mono} 表示写入完成
     */
    public @NonNull Mono<@NonNull Void> flush() {
        if (Collections.isEmpty(this.body)) {
            return Mono.empty();
        }

        // 直接写 body
        return super.writeWith(Mono.just(
            bufferFactory.wrap(this.body)
        ));
    }

    /**
     * 判断当前响应是否为 JSON 类型。
     *
     * @return {@code true} 如果响应是 JSON 类型，否则 {@code false}
     */
    private boolean isJsonResponse() {
        return Optional.ofNullable(getHeaders().getContentType())
            .map(MediaType.APPLICATION_JSON::isCompatibleWith)
            .orElse(false);
    }
}
