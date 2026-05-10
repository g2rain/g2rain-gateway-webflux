package com.g2rain.gateway.route;

import com.g2rain.gateway.client.BasisServiceClient;
import com.g2rain.gateway.matcher.RuleDefinition;
import com.g2rain.gateway.model.route.RouteDefinitionVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("内存路由加载器测试")
public class MemoryRouteLoaderTest {

    private GatewayRouteLoader memoryRouteLoader;

    @Mock
    private BasisServiceClient basisServiceClient;

    @Mock
    private RouteMatchHolder routeMatchHolder;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        memoryRouteLoader = new GatewayRouteLoader(basisServiceClient, routeMatchHolder);
        memoryRouteLoader.setApplicationEventPublisher(applicationEventPublisher);
    }

    @Test
    @DisplayName("测试构造函数")
    void testConstructor() {
        assertDoesNotThrow(() -> new GatewayRouteLoader(basisServiceClient, routeMatchHolder));
    }

    @Test
    @DisplayName("测试设置应用事件发布器")
    void testSetApplicationEventPublisher() {
        assertDoesNotThrow(() -> memoryRouteLoader.setApplicationEventPublisher(applicationEventPublisher));
    }

    @Test
    @DisplayName("测试刷新路由")
    void testRefreshRoutes() {
        RouteDefinitionVo route1 = new RouteDefinitionVo();
        route1.setId(1L);
        route1.setName("test-route-1");
        route1.setEndpoint("http://test-service-1");
        route1.setRoutePrefix("svc1");
        route1.setPath("/test1/**");

        RouteDefinitionVo route2 = new RouteDefinitionVo();
        route2.setId(2L);
        route2.setName("test-route-2");
        route2.setEndpoint("http://test-service-2");
        route2.setRoutePrefix("svc2");
        route2.setPath("/test2/**");

        List<RouteDefinitionVo> routes = new ArrayList<>();
        routes.add(route1);
        routes.add(route2);

        when(basisServiceClient.selectRouteDefinitions()).thenReturn(Mono.just(routes));
        when(basisServiceClient.getServiceRegistry()).thenReturn(Flux.empty());

        assertDoesNotThrow(() -> memoryRouteLoader.load().block());

        assertEquals(2, memoryRouteLoader.getRouteDefinitions().collectList().block().size());
        verify(routeMatchHolder).replace(argThat(c -> c != null && c.size() == 2));
    }

    @Test
    @DisplayName("测试单条路由新增或更新")
    void testUpsertRoute() {
        RouteDefinitionVo route = new RouteDefinitionVo();
        route.setId(100L);
        route.setName("test-route-upsert");
        route.setEndpoint("http://test-service");
        route.setRoutePrefix("api");
        route.setPath("/demo/**");

        assertDoesNotThrow(() -> memoryRouteLoader.upsert(route).block());
        assertEquals(1, memoryRouteLoader.getRouteDefinitions().collectList().block().size());
        verify(routeMatchHolder).upsert(argThat((RuleDefinition<Long> r) -> r != null && Long.valueOf(100L).equals(r.id())));
        verify(applicationEventPublisher).publishEvent(any());
    }

    @Test
    @DisplayName("测试单条路由删除")
    void testDeleteRoute() {
        assertDoesNotThrow(() -> memoryRouteLoader.remove(100L).block());
        verify(routeMatchHolder).remove(100L);
        verify(applicationEventPublisher).publishEvent(any());
    }
}
