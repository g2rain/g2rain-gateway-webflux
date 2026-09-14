package com.g2rain.gateway.filters;

import com.g2rain.common.enums.SessionType;
import com.g2rain.common.web.PrincipalHeaders;
import com.g2rain.gateway.model.context.EdgePrincipalContext;
import com.g2rain.gateway.model.context.EdgePrincipalContextHolder;
import com.g2rain.gateway.whitelist.WhiteListResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Principal转发过滤器测试")
public class PrincipalForwardFilterTest {

    private PrincipalForwardFilter principalForwardFilter;

    @Mock
    private WhiteListResolver whiteListResolver;

    @Mock
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        principalForwardFilter = new PrincipalForwardFilter(whiteListResolver);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("测试过滤器顺序")
    void testGetOrder() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 700, principalForwardFilter.getOrder());
    }

    @Test
    @DisplayName("白名单请求清除伪造主体头后放行")
    void testWhiteListStripsPrincipalHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
            .header(PrincipalHeaders.MEMBER_ID.getUpper(), "forged-member")
            .header(PrincipalHeaders.ORGAN_ID.getUpper(), "forged-organ")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(whiteListResolver.shouldExclude(anyString(), any(ServerWebExchange.class))).thenReturn(true);

        assertDoesNotThrow(() -> principalForwardFilter.filter(exchange, chain).block());

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        HttpHeaders headers = captor.getValue().getRequest().getHeaders();
        assertNull(headers.getFirst(PrincipalHeaders.MEMBER_ID.getUpper()));
        assertNull(headers.getFirst(PrincipalHeaders.ORGAN_ID.getUpper()));
        verify(whiteListResolver).shouldExclude("PrincipalForwardFilter", exchange);
    }

    @Test
    @DisplayName("伪造主体头被剥离后仅保留网关重建值")
    void testForgedPrincipalHeadersReplacedByTrustedContext() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
            .header(PrincipalHeaders.MEMBER_ID.getUpper(), "forged-member")
            .header(PrincipalHeaders.ORGAN_ID.getUpper(), "forged-organ")
            .header(PrincipalHeaders.USER_ID.getUpper(), "forged-user")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(whiteListResolver.shouldExclude(anyString(), any(ServerWebExchange.class))).thenReturn(false);

        EdgePrincipalContext context = EdgePrincipalContext.of();
        context.setSessionType(SessionType.MEMBER);
        context.setOrganId(10001L);
        context.setMemberId(9L);

        assertDoesNotThrow(() -> principalForwardFilter.filter(exchange, chain)
            .contextWrite(ctx -> EdgePrincipalContextHolder.put(ctx, context))
            .block());

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        HttpHeaders headers = captor.getValue().getRequest().getHeaders();
        assertEquals("9", headers.getFirst(PrincipalHeaders.MEMBER_ID.getLower()));
        assertEquals("10001", headers.getFirst(PrincipalHeaders.ORGAN_ID.getLower()));
        assertNull(headers.getFirst(PrincipalHeaders.USER_ID.getLower()));
        assertEquals(1, headers.get(PrincipalHeaders.MEMBER_ID.getLower()).size());
    }
}
