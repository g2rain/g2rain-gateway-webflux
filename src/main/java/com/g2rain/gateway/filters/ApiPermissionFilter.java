package com.g2rain.gateway.filters;


import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 接口权限校验过滤器（骨架）。
 *
 * <p>
 * 用于在网关侧根据请求 {@code URI + Method} 判断当前登录用户/客户端是否有权限访问该接口。
 * 具体的权限数据来源与匹配规则由你自行实现（例如查缓存/远端校验/本地规则表等）。
 * </p>
 */
@Slf4j
@Component
@AllArgsConstructor
public class ApiPermissionFilter implements GlobalFilter, Ordered {

    @Override
    public @NonNull Mono<@NonNull Void> filter(@org.jspecify.annotations.NonNull ServerWebExchange exchange, @NonNull GatewayFilterChain chain) {
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 500;
    }
}

