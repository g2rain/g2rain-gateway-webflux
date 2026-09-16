package com.g2rain.gateway.filters;

import com.g2rain.common.enums.SessionType;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.gateway.cache.DefaultPerm;
import com.g2rain.gateway.cache.MemberPerm;
import com.g2rain.gateway.cache.UserPerm;
import com.g2rain.gateway.config.MemberPermissionProperties;
import com.g2rain.gateway.enums.GatewayErrorCode;
import com.g2rain.gateway.exception.GatewayException;
import com.g2rain.gateway.model.context.EdgePrincipalContext;
import com.g2rain.gateway.model.context.EdgePrincipalContextHolder;
import com.g2rain.gateway.whitelist.WhiteListResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ApiPermissionFilter MEMBER 会话")
class ApiPermissionFilterMemberTest {

    private ApiPermissionFilter newFilter(DefaultPerm defaultPerm, UserPerm userPerm, MemberPerm memberPerm,
                                          MemberPermissionProperties props) {
        WhiteListResolver whiteListResolver = mock(WhiteListResolver.class);
        when(whiteListResolver.shouldExclude(anyString(), any())).thenReturn(false);
        return new ApiPermissionFilter(defaultPerm, userPerm, memberPerm, props, whiteListResolver);
    }

    private MemberPermissionProperties enforceProps() {
        MemberPermissionProperties props = new MemberPermissionProperties();
        props.setMode("enforce");
        return props;
    }

    @Test
    @DisplayName("MEMBER 在 organ 已开通且命中 MemberPerm 时放行")
    void memberAllowedWhenMemberPermHits() {
        DefaultPerm defaultPerm = mock(DefaultPerm.class);
        UserPerm userPerm = mock(UserPerm.class);
        MemberPerm memberPerm = mock(MemberPerm.class);
        when(memberPerm.hasApiPermission(10001L, 42L)).thenReturn(Mono.just(true));

        ApiPermissionFilter filter = newFilter(defaultPerm, userPerm, memberPerm, enforceProps());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        EdgePrincipalContext context = EdgePrincipalContext.of();
        context.setSessionType(SessionType.MEMBER);
        context.setOrganId(10001L);
        context.setMemberId(9L);

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/biz").build());
        Route route = Route.async().id("42").uri("http://localhost").predicate(ex -> true).build();
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);

        filter.filter(exchange, chain)
            .contextWrite(ctx -> EdgePrincipalContextHolder.put(ctx, context))
            .block();

        verify(chain).filter(exchange);
        verify(userPerm, never()).getApiPermission(anyLong(), any(), any(), any(), anyLong());
        verify(defaultPerm, never()).hasApiPermission(anyLong());
    }

    @Test
    @DisplayName("MEMBER 租户未开通时拒绝")
    void memberRejectedWhenOrganNotOpened() {
        DefaultPerm defaultPerm = mock(DefaultPerm.class);
        UserPerm userPerm = mock(UserPerm.class);
        MemberPerm memberPerm = mock(MemberPerm.class);
        when(memberPerm.hasApiPermission(10001L, 42L)).thenReturn(Mono.just(false));

        ApiPermissionFilter filter = newFilter(defaultPerm, userPerm, memberPerm, enforceProps());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        EdgePrincipalContext context = EdgePrincipalContext.of();
        context.setSessionType(SessionType.MEMBER);
        context.setOrganId(10001L);
        context.setMemberId(9L);

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/biz").build());
        Route route = Route.async().id("42").uri("http://localhost").predicate(ex -> true).build();
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);

        GatewayException ex = assertThrows(GatewayException.class, () ->
            filter.filter(exchange, chain)
                .contextWrite(ctx -> EdgePrincipalContextHolder.put(ctx, context))
                .block()
        );
        assertEquals(SystemErrorCode.UNAUTHORIZED.code(), ex.getErrorCode());
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("MEMBER 缺少 memberId 时拒绝")
    void memberRejectedWithoutMemberId() {
        DefaultPerm defaultPerm = mock(DefaultPerm.class);
        UserPerm userPerm = mock(UserPerm.class);
        MemberPerm memberPerm = mock(MemberPerm.class);

        ApiPermissionFilter filter = newFilter(defaultPerm, userPerm, memberPerm, enforceProps());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        EdgePrincipalContext context = EdgePrincipalContext.of();
        context.setSessionType(SessionType.MEMBER);
        context.setOrganId(10001L);

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/biz").build());
        Route route = Route.async().id("42").uri("http://localhost").predicate(ex -> true).build();
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);

        GatewayException ex = assertThrows(GatewayException.class, () ->
            filter.filter(exchange, chain)
                .contextWrite(ctx -> EdgePrincipalContextHolder.put(ctx, context))
                .block()
        );
        assertEquals(SystemErrorCode.UNAUTHENTICATED.code(), ex.getErrorCode());
        verify(chain, never()).filter(any());
        verify(memberPerm, never()).hasApiPermission(anyLong(), anyLong());
    }

    @Test
    @DisplayName("MEMBER 混入 userId 时拒绝")
    void memberRejectedWithUserId() {
        DefaultPerm defaultPerm = mock(DefaultPerm.class);
        UserPerm userPerm = mock(UserPerm.class);
        MemberPerm memberPerm = mock(MemberPerm.class);

        ApiPermissionFilter filter = newFilter(defaultPerm, userPerm, memberPerm, enforceProps());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        EdgePrincipalContext context = EdgePrincipalContext.of();
        context.setSessionType(SessionType.MEMBER);
        context.setOrganId(10001L);
        context.setMemberId(9L);
        context.setUserId(1L);

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/biz").build());
        Route route = Route.async().id("42").uri("http://localhost").predicate(ex -> true).build();
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);

        GatewayException ex = assertThrows(GatewayException.class, () ->
            filter.filter(exchange, chain)
                .contextWrite(ctx -> EdgePrincipalContextHolder.put(ctx, context))
                .block()
        );
        assertEquals(SystemErrorCode.UNAUTHENTICATED.code(), ex.getErrorCode());
    }

    @Test
    @DisplayName("MEMBER MemberPerm 回源失败时失败关闭")
    void memberFailsClosedWhenMemberPermUnavailable() {
        DefaultPerm defaultPerm = mock(DefaultPerm.class);
        UserPerm userPerm = mock(UserPerm.class);
        MemberPerm memberPerm = mock(MemberPerm.class);
        when(memberPerm.hasApiPermission(eq(10001L), eq(42L)))
            .thenReturn(Mono.error(new RuntimeException("basis down")));

        ApiPermissionFilter filter = newFilter(defaultPerm, userPerm, memberPerm, enforceProps());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        EdgePrincipalContext context = EdgePrincipalContext.of();
        context.setSessionType(SessionType.MEMBER);
        context.setOrganId(10001L);
        context.setMemberId(9L);

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/biz").build());
        Route route = Route.async().id("42").uri("http://localhost").predicate(ex -> true).build();
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);

        GatewayException ex = assertThrows(GatewayException.class, () ->
            filter.filter(exchange, chain)
                .contextWrite(ctx -> EdgePrincipalContextHolder.put(ctx, context))
                .block()
        );
        assertEquals(GatewayErrorCode.MEMBER_PERM_UNAVAILABLE.code(), ex.getErrorCode());
        verify(chain, never()).filter(any());
    }
}
