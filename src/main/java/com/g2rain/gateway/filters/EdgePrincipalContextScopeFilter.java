package com.g2rain.gateway.filters;


import com.g2rain.gateway.model.context.EdgePrincipalContext;
import com.g2rain.gateway.model.context.EdgePrincipalContextHolder;
import com.g2rain.gateway.utils.Constants;
import lombok.NonNull;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Locale;

/**
 * 请求级 Principal 上下文作用域过滤器。
 * <p>
 * 该过滤器在网关过滤链最外层创建同一个 {@link EdgePrincipalContext}，
 * 使后续认证、转发、响应日志等链路通过 Reactor Context 读取同一上下文；
 * {@link com.g2rain.gateway.exception.GlobalErrorHandler} 另起订阅链，从
 * {@link Constants#ACCEPT_LANGUAGE_ATTRIBUTE} 读取语言。
 * </p>
 *
 * @author alpha
 * @since 2026/5/11
 */
@Component
public class EdgePrincipalContextScopeFilter implements GlobalFilter, Ordered {

    @Override
    public @NonNull Mono<@NonNull Void> filter(@NonNull ServerWebExchange exchange, @NonNull GatewayFilterChain chain) {
        EdgePrincipalContext context = EdgePrincipalContext.of();
        String acceptLanguage = resolveAcceptLanguage(exchange.getRequest());
        context.setAcceptLanguage(acceptLanguage);
        exchange.getAttributes().put(Constants.ACCEPT_LANGUAGE_ATTRIBUTE, acceptLanguage);

        return Mono.defer(() -> chain.filter(exchange))
            .contextWrite(ctx -> EdgePrincipalContextHolder.put(ctx, context));
    }

    private String resolveAcceptLanguage(ServerHttpRequest request) {
        return request.getHeaders().getAcceptLanguage().stream()
            .findFirst()
            .map(Locale.LanguageRange::getRange)
            .map(Locale::forLanguageTag)
            .orElse(Locale.getDefault())
            .toLanguageTag();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
