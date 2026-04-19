package com.g2rain.gateway.filters;


import com.g2rain.common.utils.Collections;
import com.g2rain.common.utils.Strings;
import com.g2rain.common.web.PrincipalHeaders;
import com.g2rain.gateway.model.context.EdgePrincipalContext;
import com.g2rain.gateway.model.context.EdgePrincipalContextHolder;
import com.g2rain.gateway.model.web.CachedBodyRequest;
import com.g2rain.gateway.utils.Constants;
import com.g2rain.gateway.whitelist.WhiteListResolver;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Principal 转发过滤器。
 * <p>
 * 该过滤器用于在 Spring Cloud Gateway 中转发认证上下文信息，将 {@link EdgePrincipalContext}
 * 中的关键信息以 HTTP Header 形式注入到下游服务，并移除敏感的认证头。
 * </p>
 *
 * <h2>功能</h2>
 * <ul>
 *     <li>读取当前请求的 {@link EdgePrincipalContext}</li>
 *     <li>将上下文中的 Principal 信息按 {@link PrincipalHeaders} 定义转发到请求头</li>
 *     <li>对转发的 Header 值进行 URL 编码</li>
 *     <li>移除原始的 Authorization 和 DPoP Header，防止敏感信息泄露</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * @Bean
 * public PrincipalForwardFilter principalForwardFilter() {
 *     return new PrincipalForwardFilter();
 * }
 * }</pre>
 *
 * <h2>注意事项</h2>
 * <ul>
 *     <li>确保 {@link EdgePrincipalContext} 已被初始化（通常由 GatewayAuthFilter 完成）</li>
 *     <li>URL 编码可能影响 Header 值，可根据需要调整 {@link #encodeHeaderValue} 实现</li>
 *     <li>本过滤器优先级默认为 {@code 30}</li>
 * </ul>
 *
 * @author alpha
 * @since 2025/10/6
 */
@Slf4j
@Component
@AllArgsConstructor
public class PrincipalForwardFilter implements GlobalFilter, Ordered {

    /**
     * {@code DEBUG_KEY} 开启 DEBUG 模式
     */
    private static final String DEBUG_KEY = "zQA730o1RORiKbcR";

    /**
     * {@code whiteListResolver} 用于判断当前请求是否命中白名单规则，
     * 如果命中则可以跳过当前 Filter 的执行。
     * <p>
     * 白名单规则包括全局规则和针对特定 Filter 的规则，匹配顺序为：
     * Filter 白名单 → 全局白名单，
     * 匹配方式包括 contextPath、exactPath、patternPath。
     * </p>
     */
    private final WhiteListResolver whiteListResolver;

    /**
     * 过滤器核心方法，处理请求头的 Principal 转发。
     *
     * @param exchange 当前请求交换对象
     * @param chain    Gateway 过滤链
     * @return {@link Mono} 执行结果
     */
    @Override
    public @NonNull Mono<@NonNull Void> filter(ServerWebExchange exchange, @NonNull GatewayFilterChain chain) {
        // 获取当前请求对象
        ServerHttpRequest request = exchange.getRequest();

        // 获取当前过滤器的类名（用于白名单判断）
        String filterName = this.getClass().getSimpleName();

        // 判断当前请求是否命中白名单规则
        // 命中则跳过本过滤器，直接进入下一个过滤器
        if (whiteListResolver.shouldExclude(filterName, exchange)) {
            return chain.filter(exchange);
        }

        // 异步获取上下文（如认证信息、租户信息等）
        return EdgePrincipalContextHolder.get().flatMap(ctx -> {
            // 如果请求已被包装为 CachedBodyRequest（可重复读取 Body）
            if (request instanceof CachedBodyRequest cached) {
                List<String> debugKeys = cached.getHeaders().get(Constants.DEBUG_KEY_HEADER);
                //noinspection ConstantConditions
                if (Collections.isNotEmpty(debugKeys) && debugKeys.contains(DEBUG_KEY)) {
                    cached.addHeader(PrincipalHeaders.DEBUG.getUpper(), Boolean.TRUE.toString());
                }

                // 基于上下文信息动态添加或移除 Header
                applyHeaders(ctx, cached::addHeader, cached::removeHeader);

                // 继续执行下一个过滤器
                return chain.filter(exchange);
            }

            // 否则创建可变的请求构建器
            ServerHttpRequest.Builder builder = request.mutate();
            List<String> debugKeys = request.getHeaders().get(Constants.DEBUG_KEY_HEADER);
            //noinspection ConstantConditions
            if (Collections.isNotEmpty(debugKeys) && debugKeys.contains(DEBUG_KEY)) {
                builder.header(PrincipalHeaders.DEBUG.getUpper(), Boolean.TRUE.toString());
            }

            // 根据上下文信息设置请求头（添加与移除）
            applyHeaders(ctx, builder::header, names -> removeHeader(builder, names));

            // 将修改后的请求重新构建并放入 exchange 中，继续过滤链
            return chain.filter(exchange.mutate().request(builder.build()).build());
        });
    }

    /**
     * 根据 Principal 上下文对请求头进行处理：添加需要转发的 Principal Headers，
     * 并移除敏感认证头。
     *
     * @param ctx     当前的 Principal 上下文，提供 header 值
     * @param adder   添加 header 的回调函数，接受 header 名称和值
     * @param remover 移除 header 的回调函数，接受要移除的 header 名称列表
     */
    private void applyHeaders(EdgePrincipalContext ctx, BiConsumer<String, String> adder, Consumer<List<String>> remover) {
        // 遍历所有定义的 PrincipalHeaders
        for (PrincipalHeaders headerKey : PrincipalHeaders.values()) {
            String value = ctx.getValue(headerKey);
            // 如果值为空则跳过
            if (Strings.isBlank(value)) {
                continue;
            }

            // 对 header 值进行 URL 编码并添加
            adder.accept(headerKey.getLower(), encodeHeaderValue(headerKey, value));
        }

        // 移除敏感认证头
        remover.accept(List.of(Constants.AUTHORIZATION_HEADER, Constants.CLIENT_PROOF_HEADER, Constants.DEBUG_KEY_HEADER));
    }

    /**
     * 从请求头中移除指定名称的 Header。
     *
     * <p>该方法会遍历给定的 header 名称列表，将其从 {@link ServerHttpRequest.Builder}
     * 的 headers 中删除。如果名称列表为空或包含空字符串，则跳过相应处理。</p>
     *
     * @param builder {@link ServerHttpRequest.Builder} 请求构建器，用于修改请求头
     * @param names   需要移除的 header 名称列表
     */
    private void removeHeader(ServerHttpRequest.Builder builder, List<String> names) {
        builder.headers(headers -> {
            if (Collections.isEmpty(names)) {
                return;
            }

            for (String name : names) {
                if (Strings.isBlank(name)) {
                    continue;
                }

                headers.remove(name);
            }
        });
    }

    /**
     * 对 Header 值进行 URL 编码。
     *
     * @param key   Principal Header 键
     * @param value 原始 Header 值
     * @return 编码后的 Header 值
     */
    private String encodeHeaderValue(PrincipalHeaders key, String value) {
        if (!PrincipalHeaders.NAME.equals(key) && !PrincipalHeaders.ORGAN_NAME.equals(key)) {
            return value;
        }

        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("编码请求头部值失败，header:{}, value:{}", key.getLower(), value, e);
            return value;
        }
    }

    /**
     * 获取过滤器执行顺序，默认为 {@code 30}。
     *
     * @return 顺序值
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 700;
    }
}
