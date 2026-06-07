package com.g2rain.gateway.filters;


import com.g2rain.common.utils.Moments;
import com.g2rain.common.utils.Strings;
import com.g2rain.gateway.cache.ApiKeyCache;
import com.g2rain.gateway.enums.ApiKeyResolveOutcome;
import com.g2rain.gateway.enums.GatewayErrorCode;
import com.g2rain.gateway.exception.GatewayException;
import com.g2rain.gateway.model.auth.StaticTokenContext;
import com.g2rain.gateway.model.context.EdgePrincipalContext;
import com.g2rain.gateway.model.context.EdgePrincipalContextHolder;
import com.g2rain.gateway.utils.AuthScheme;
import com.g2rain.gateway.utils.Constants;
import com.g2rain.gateway.whitelist.WhiteListResolver;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 静态 API Key 鉴权过滤器（Spring Cloud Gateway {@link GlobalFilter}）。
 *
 * <p>
 * 处理 {@code Authorization: Bearer sk-...} 形态的个人静态访问令牌（OpenAI 风格，总长 64）。
 * 与 {@link GatewayTokenAuthFilter} 互斥：本过滤器只认领 {@link AuthScheme#isApiKey(String)} 为真的凭证，
 * 其余 Bearer 凭证留给 JWT 链路。
 * </p>
 *
 * <h2>处理流程</h2>
 * <ol>
 *     <li>白名单命中 → 直接放行。</li>
 *     <li>无 Authorization 或凭证非 API Key → 放行（不报错）。</li>
 *     <li>调用 {@link ApiKeyCache#resolve(String)}：
 *         {@link ApiKeyResolveOutcome#INVALID} → {@link GatewayErrorCode#API_KEY_INVALID}；
 *         {@link ApiKeyResolveOutcome#REVOKED} → {@link GatewayErrorCode#API_KEY_REVOKED}。</li>
 *     <li>激活态：将 {@link StaticTokenContext} 写入 {@link EdgePrincipalContext}，置 {@code staticTokenAuthenticated=true}，
 *         后续 JWT / DPoP / 签名校验过滤器据此跳过。</li>
 * </ol>
 *
 * <h2>顺序</h2>
 * <p>{@link Ordered#HIGHEST_PRECEDENCE} + 290，位于 {@link TraceLoggingFilter} 之后、
 * {@link GatewayTokenAuthFilter} 之前。</p>
 *
 * @author alpha
 * @since 2026/5/22
 */
@Component
@AllArgsConstructor
public class ApiKeyFilter implements GlobalFilter, Ordered {

    private final WhiteListResolver whiteListResolver;

    private final ApiKeyCache apiKeyCache;

    /**
     * 用于在上下文中填充 traceId（无活跃 Span 时生成随机 ID）。
     */
    private final Tracer tracer;

    @Override
    public @NonNull Mono<@NonNull Void> filter(@NonNull ServerWebExchange exchange, @NonNull GatewayFilterChain chain) {
        String filterName = getClass().getSimpleName();
        if (whiteListResolver.shouldExclude(filterName, exchange)) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String authHeader = request.getHeaders().getFirst(Constants.AUTHORIZATION_HEADER);
        if (Strings.isBlank(authHeader)) {
            return chain.filter(exchange);
        }

        String credential = AuthScheme.credential(authHeader);
        if (Objects.isNull(credential) || !AuthScheme.isApiKey(credential)) {
            return chain.filter(exchange);
        }

        return apiKeyCache.resolve(credential).flatMap(result -> {
            if (result.outcome() == ApiKeyResolveOutcome.INVALID) {
                return Mono.error(new GatewayException(GatewayErrorCode.API_KEY_INVALID, "apiKey"));
            }

            if (result.outcome() == ApiKeyResolveOutcome.REVOKED) {
                return Mono.error(new GatewayException(GatewayErrorCode.API_KEY_REVOKED, "apiKey"));
            }

            return EdgePrincipalContextHolder.get().flatMap(principal -> {
                applyContext(principal, result.context(), credential);
                return chain.filter(exchange);
            });
        });
    }

    /**
     * 将静态令牌会话写入边缘 Principal，并标记 API Key 鉴权已完成。
     */
    private void applyContext(EdgePrincipalContext principal, StaticTokenContext ctx, String apiKey) {
        String traceId = Optional.ofNullable(tracer.currentSpan())
            .map(Span::context).map(TraceContext::traceId)
            .orElseGet(() ->
                UUID.randomUUID().toString().replace("-", "")
            );

        principal.setApiKey(apiKey);
        principal.setStaticTokenAuthenticated(true);
        principal.setTraceId(traceId);
        principal.setRequestId(UUID.randomUUID().toString());
        principal.setRequestTime(Moments.format(Moments.now()));
        principal.setSessionType(ctx.getSessionType());
        principal.setPassportId(ctx.getPassportId());
        principal.setUserId(ctx.getUserId());
        principal.setName(ctx.getName());
        principal.setAdminUser(ctx.isAdminUser());
        principal.setOrganType(ctx.getOrganType());
        principal.setOrganId(ctx.getOrganId());
        principal.setOrganName(ctx.getOrganName());
        principal.setAdminCompany(ctx.isAdminCompany());
        principal.setApplicationId(ctx.getApplicationId());
        principal.setApplicationCode(ctx.getApplicationCode());
        principal.setApplicationOrganId(ctx.getApplicationOrganId());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 290;
    }
}
