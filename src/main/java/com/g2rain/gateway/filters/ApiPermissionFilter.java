package com.g2rain.gateway.filters;


import com.g2rain.common.enums.SessionType;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.utils.Strings;
import com.g2rain.gateway.cache.DefaultPerm;
import com.g2rain.gateway.cache.UserPerm;
import com.g2rain.gateway.enums.GatewayErrorCode;
import com.g2rain.gateway.exception.GatewayException;
import com.g2rain.gateway.model.context.EdgePrincipalContext;
import com.g2rain.gateway.model.context.EdgePrincipalContextHolder;
import com.g2rain.gateway.utils.Constants;
import com.g2rain.gateway.whitelist.WhiteListResolver;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * 接口权限校验：基于已匹配的 {@link Route#getId()}（与 {@link ServerWebExchangeUtils#GATEWAY_ROUTE_ATTR} 一致）做 Passport / User 鉴权
 *
 * @author alpha
 * @since 2026/05/07
 */
@Slf4j
//@Component
@AllArgsConstructor
public class ApiPermissionFilter implements GlobalFilter, Ordered {

    private final DefaultPerm defaultPerm;

    private final UserPerm userPerm;

    private final WhiteListResolver whiteListResolver;

    @Override
    public @NonNull Mono<@NonNull Void> filter(@NonNull ServerWebExchange exchange, @NonNull GatewayFilterChain chain) {
        String filterName = this.getClass().getSimpleName();
        if (whiteListResolver.shouldExclude(filterName, exchange)) {
            return chain.filter(exchange);
        }

        return EdgePrincipalContextHolder.get().flatMap(context ->
            authorize(exchange, chain, context)
        );
    }

    @SuppressWarnings("ConstantConditions")
    private Mono<Void> authorize(ServerWebExchange exchange, GatewayFilterChain chain, EdgePrincipalContext context) {
        Route gatewayRoute = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeId = gatewayRoute != null ? gatewayRoute.getId() : null;
        Long applicationId = context.getApplicationId();
        Long apiId = null;
        if (Strings.isNotBlank(routeId)) {
            try {
                apiId = Long.valueOf(routeId);
            } catch (Exception _) {

            }
        }

        if (Objects.isNull(apiId)) {
            return Mono.error(new GatewayException(SystemErrorCode.UNAUTHORIZED, applicationId));
        }

        if (SessionType.isPassport(context.getSessionType())) {
            return defaultPerm.hasApiPermission(apiId).flatMap(ok -> {
                if (!ok) {
                    return Mono.error(new GatewayException(SystemErrorCode.UNAUTHORIZED, applicationId));
                }

                return chain.filter(exchange);
            });
        }

        return userPerm.getApiPermission(context.getOrganId(), context.getUserId(), applicationId, apiId)
            .switchIfEmpty(Mono.error(new GatewayException(SystemErrorCode.UNAUTHORIZED, applicationId)))
            .flatMap(userApiPermission -> {
                if (!Strings.equals(Constants.AUTHORIZATION_ACTIVATED, userApiPermission.getStatus())) {
                    return Mono.error(new GatewayException(GatewayErrorCode.SUBSCRIPTION_EXPIRED));
                }

                return chain.filter(exchange);
            });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 600;
    }
}
