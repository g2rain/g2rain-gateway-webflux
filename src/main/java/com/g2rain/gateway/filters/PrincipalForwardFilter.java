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
import java.util.ArrayList;
import java.util.List;

/**
 * Principal 转发过滤器。
 * <p>
 * 该过滤器用于在 Spring Cloud Gateway 中转发认证上下文信息，将 {@link EdgePrincipalContext}
 * 中的关键信息以 HTTP Header 形式注入到下游服务，并移除外部伪造的主体头与敏感认证头。
 * </p>
 *
 * <h2>功能</h2>
 * <ul>
 *     <li>先移除全部 {@link PrincipalHeaders}，再按上下文以 set/replace 写入可信值</li>
 *     <li>白名单请求也清除外部主体头，但不注入认证上下文</li>
 *     <li>对姓名相关 Header 值进行 URL 编码</li>
 *     <li>移除原始的 Authorization 和 DPoP Header，防止敏感信息泄露</li>
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
        ServerHttpRequest request = exchange.getRequest();
        String filterName = this.getClass().getSimpleName();

        if (whiteListResolver.shouldExclude(filterName, exchange)) {
            return chain.filter(stripPrincipalOnly(exchange, request));
        }

        return EdgePrincipalContextHolder.get().flatMap(ctx -> {
            if (request instanceof CachedBodyRequest cached) {
                List<String> debugKeys = cached.getHeaders().get(Constants.DEBUG_KEY_HEADER);
                applyHeaders(ctx, cached::addHeader, cached::removeHeader);
                //noinspection ConstantConditions
                if (Collections.isNotEmpty(debugKeys) && debugKeys.contains(DEBUG_KEY)) {
                    // 须在剥离 PrincipalHeaders 之后写入，否则会被 strip 清掉
                    cached.addHeader(PrincipalHeaders.DEBUG.getUpper(), Boolean.TRUE.toString());
                }
                return chain.filter(exchange);
            }

            ServerHttpRequest.Builder builder = request.mutate();
            List<String> debugKeys = request.getHeaders().get(Constants.DEBUG_KEY_HEADER);
            applyHeaders(ctx,
                (name, value) -> builder.headers(h -> h.set(name, value)),
                names -> removeHeader(builder, names)
            );
            //noinspection ConstantConditions
            if (Collections.isNotEmpty(debugKeys) && debugKeys.contains(DEBUG_KEY)) {
                builder.headers(h -> h.set(PrincipalHeaders.DEBUG.getUpper(), Boolean.TRUE.toString()));
            }

            return chain.filter(exchange.mutate().request(builder.build()).build());
        });
    }

    /**
     * 白名单：仅清除外部主体头，不注入网关上下文。
     */
    private ServerWebExchange stripPrincipalOnly(ServerWebExchange exchange, ServerHttpRequest request) {
        if (request instanceof CachedBodyRequest cached) {
            cached.removeHeader(principalHeaderNames());
            return exchange;
        }

        ServerHttpRequest.Builder builder = request.mutate();
        removeHeader(builder, principalHeaderNames());
        return exchange.mutate().request(builder.build()).build();
    }

    /**
     * 先剥离全部主体头，再写入可信值，最后移除敏感认证头。
     *
     * @param ctx     当前的 Principal 上下文，提供 header 值
     * @param setter  以 replace 语义写入 header
     * @param remover 移除 header 的回调
     */
    private void applyHeaders(EdgePrincipalContext ctx,
                              java.util.function.BiConsumer<String, String> setter,
                              java.util.function.Consumer<List<String>> remover) {
        remover.accept(principalHeaderNames());

        for (PrincipalHeaders headerKey : PrincipalHeaders.values()) {
            String value = ctx.getValue(headerKey);
            if (Strings.isBlank(value)) {
                continue;
            }
            setter.accept(headerKey.getLower(), encodeHeaderValue(headerKey, value));
        }

        remover.accept(List.of(
            Constants.AUTHORIZATION_HEADER,
            Constants.CLIENT_PROOF_HEADER,
            Constants.DEBUG_KEY_HEADER
        ));
    }

    private static List<String> principalHeaderNames() {
        List<String> names = new ArrayList<>(PrincipalHeaders.values().length * 2);
        for (PrincipalHeaders header : PrincipalHeaders.values()) {
            names.add(header.getLower());
            names.add(header.getUpper());
        }
        return names;
    }

    /**
     * 从请求头中移除指定名称的 Header。
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
     * 获取过滤器执行顺序，默认为 {@code 700}。
     *
     * @return 顺序值
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 700;
    }
}
