package com.g2rain.gateway.model.web;


import com.g2rain.common.utils.Collections;
import com.g2rain.common.utils.Constants;
import com.g2rain.common.utils.Strings;
import lombok.NonNull;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;

/**
 * 装饰器请求，用于缓存 HTTP 请求体，以便在后续处理过程中可重复读取。
 * <p>
 * 本类继承自 {@link org.springframework.http.server.reactive.ServerHttpRequestDecorator}，
 * 在构造时将请求体缓存到内存中，并修正请求头信息（如 Content-Length），
 * 以保证后续读取请求体时不会丢失数据。
 * </p>
 *
 * <h2>功能</h2>
 * <ul>
 *     <li>缓存请求体，支持多次读取</li>
 *     <li>修正请求头，确保 Content-Length 与实际 body 长度一致</li>
 *     <li>避免 Transfer-Encoding 与 Content-Length 冲突</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <p>
 * 在网关过滤器中使用 {@code CachedBodyRequest} 可实现对请求体的多次读取，
 * 例如日志记录、签名校验、数据处理等场景。
 * </p>
 *
 * <h2>示例</h2>
 * <pre>{@code
 * ServerHttpRequest originalRequest = exchange.getRequest();
 * byte[] body = ...; // 获取请求体字节数组
 * CachedBodyRequest cachedRequest = new CachedBodyRequest(originalRequest, body);
 * }</pre>
 *
 * @author alpha
 * @since 2025/10/6
 */
public class CachedBodyRequest extends ServerHttpRequestDecorator {

    /**
     * 数据缓冲工厂，用于创建 DataBuffer 对象
     */
    private final DataBufferFactory factory = new DefaultDataBufferFactory();

    /**
     * 缓存的请求头，已修正 Content-Length
     */
    private final HttpHeaders httpHeaders;

    /**
     * 缓存的请求体字节数组
     */
    private final byte[] body;

    /**
     * 构造器，使用装饰器模式缓存请求体并修正请求头。
     *
     * @param delegate 原始请求对象
     * @param body     请求体字节数组，可能为 {@code null}
     */
    public CachedBodyRequest(ServerHttpRequest delegate, byte[] body) {
        super(delegate); // 装饰器模式，保存原始请求

        // 如果 body 为空，则使用共享常量，减少内存占用
        this.body = Objects.nonNull(body) ? body : Constants.EMPTY_BYTE;

        // 复制并缓存 headers，只执行一次
        HttpHeaders headers = new HttpHeaders();
        // 拷贝原始请求头
        headers.putAll(super.getHeaders());
        // 修正 Content-Length
        headers.setContentLength(this.body.length);
        // 避免 chunked 与 Content-Length 冲突
        headers.remove(HttpHeaders.TRANSFER_ENCODING);

        this.httpHeaders = headers;
    }

    /**
     * 获取修正后的请求头。
     *
     * @return 只读请求头
     */
    @Override
    public @NonNull HttpHeaders getHeaders() {
        return this.httpHeaders;
    }

    /**
     * 获取请求体的 {@link DataBuffer} 流。
     *
     * @return 请求体流
     */
    @Override
    public @NonNull Flux<@NonNull DataBuffer> getBody() {
        return Flux.defer(() -> {
            // 延迟执行直到订阅时，确保 Flux 是最新的
            if (Collections.isEmpty(this.body)) {
                // 如果 body 长度为 0，返回空 Flux
                return Flux.empty();
            }

            // 创建包含body的DataBuffer, 通过Flux.just发出
            return Flux.just(factory.wrap(this.body));
        });
    }

    /**
     * 获取请求体的字节数组。
     *
     * @return 请求体字节数组
     */
    public byte[] asBytes() {
        return this.body;
    }

    /**
     * 添加请求头。
     * <p>
     * 按 HTTP 规范，header 名必须非空；value 可为空，表示存在但无值的 header。
     *
     * @param name  header 名称，不能为空
     * @param value header 值，可以为空
     */
    public void addHeader(String name, String value) {
        if (Strings.isBlank(name)) {
            return;
        }

        this.httpHeaders.put(name, List.of(value));
    }

    /**
     * 移除请求头。
     * <p>
     * 会移除给定名称列表中的所有 header。
     *
     * @param names header 名称列表，不能为空且不包含空值
     */
    public void removeHeader(List<String> names) {
        if (Collections.isEmpty(names)) {
            return;
        }

        for (String name : names) {
            if (Strings.isBlank(name)) {
                continue;
            }

            this.httpHeaders.remove(name);
        }
    }
}
