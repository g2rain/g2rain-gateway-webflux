package com.g2rain.gateway.filters;

import com.g2rain.common.enums.SessionType;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.gateway.cache.AnonymousPerm;
import com.g2rain.gateway.cache.DefaultPerm;
import com.g2rain.gateway.cache.MemberPerm;
import com.g2rain.gateway.cache.UserPerm;
import com.g2rain.gateway.config.MemberPermissionProperties;
import com.g2rain.gateway.enums.GatewayErrorCode;
import com.g2rain.gateway.exception.GatewayException;
import com.g2rain.gateway.model.context.EdgePrincipalContext;
import com.g2rain.gateway.model.context.EdgePrincipalContextHolder;
import com.g2rain.gateway.whitelist.WhiteListResolver;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ApiPermissionFilter ANONYMOUS 会话")
class ApiPermissionFilterAnonymousTest {

    private UserPerm userPerm;
    private AnonymousPerm anonymousPerm;
    private ApiPermissionFilter filter;
    private GatewayFilterChain chain;
    private MockServerWebExchange exchange;

    @BeforeEach
    void setUp() {
        userPerm = mock(UserPerm.class);
        anonymousPerm = mock(AnonymousPerm.class);
        WhiteListResolver whiteListResolver = mock(WhiteListResolver.class);
        when(whiteListResolver.shouldExclude(anyString(), any())).thenReturn(false);
        filter = new ApiPermissionFilter(mock(DefaultPerm.class), userPerm, mock(MemberPerm.class), anonymousPerm,
            new MemberPermissionProperties(), whiteListResolver);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/harness/v1/chat/completions").build());
        Route route = Route.async().id("42").uri("http://localhost").predicate(ex -> true).build();
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);
    }

    @Test
    @DisplayName("接入码启用且接口已开通时放行，不走员工角色链")
    void allowedWhenEntryActiveAndApiOpened() {
        when(anonymousPerm.hasApiPermission(10001L, 5L, 42L)).thenReturn(Mono.just(true));

        run(visitor());

        verify(chain).filter(exchange);
        verify(userPerm, never()).getApiPermission(anyLong(), any(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("接入码已停用或接口未开通时拒绝")
    void rejectedWhenNotAllowed() {
        when(anonymousPerm.hasApiPermission(10001L, 5L, 42L)).thenReturn(Mono.just(false));

        GatewayException ex = assertThrows(GatewayException.class, () -> run(visitor()));
        assertEquals(SystemErrorCode.UNAUTHORIZED.code(), ex.getErrorCode());
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("权限快照加载失败时 503，失败即关闭")
    void unavailableWhenSnapshotLoadFails() {
        when(anonymousPerm.hasApiPermission(10001L, 5L, 42L))
            .thenReturn(Mono.error(new IllegalStateException("basis down")));

        GatewayException ex = assertThrows(GatewayException.class, () -> run(visitor()));
        assertEquals(GatewayErrorCode.ANONYMOUS_PERM_UNAVAILABLE.code(), ex.getErrorCode());
        verify(chain, never()).filter(any());
    }

    private void run(EdgePrincipalContext context) {
        filter.filter(exchange, chain)
            .contextWrite(ctx -> EdgePrincipalContextHolder.put(ctx, context))
            .block();
    }

    private static EdgePrincipalContext visitor() {
        EdgePrincipalContext context = EdgePrincipalContext.of();
        context.setSessionType(SessionType.ANONYMOUS);
        context.setOrganId(10001L);
        context.setUserId(77L);
        context.setEntryId(5L);
        return context;
    }
}
