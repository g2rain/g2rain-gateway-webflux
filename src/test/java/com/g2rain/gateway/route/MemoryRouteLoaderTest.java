package com.g2rain.gateway.route;

import com.g2rain.gateway.client.InfraServiceClient;
import com.g2rain.gateway.model.route.RouteDefinitionVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.cloud.gateway.route.RouteDefinition;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyCollection;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("内存路由加载器测试")
public class MemoryRouteLoaderTest {

    private MemoryRouteLoader memoryRouteLoader;

    @Mock
    private MemoryRouteRepository memoryRouteRepository;

    @Mock
    private InfraServiceClient infraServiceClient;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        memoryRouteLoader = new MemoryRouteLoader(infraServiceClient, memoryRouteRepository);
        memoryRouteLoader.setApplicationEventPublisher(applicationEventPublisher);
    }

    @Test
    @DisplayName("测试构造函数")
    void testConstructor() {
        assertDoesNotThrow(() -> new MemoryRouteLoader(infraServiceClient, memoryRouteRepository));
    }

    @Test
    @DisplayName("测试设置应用事件发布器")
    void testSetApplicationEventPublisher() {
        assertDoesNotThrow(() -> memoryRouteLoader.setApplicationEventPublisher(applicationEventPublisher));
    }

    @Test
    @DisplayName("测试刷新路由")
    void testRefreshRoutes() {
        // 准备数据
        RouteDefinitionVo route1 = new RouteDefinitionVo();
        route1.setId(1L);
        route1.setName("test-route-1");
        route1.setEndpointHost("http://test-service-1");
        route1.setPath("/test1/**");

        RouteDefinitionVo route2 = new RouteDefinitionVo();
        route2.setId(2L);
        route2.setName("test-route-2");
        route2.setEndpointHost("http://test-service-2");
        route2.setPath("/test2/**");

        List<RouteDefinitionVo> routes = new ArrayList<>();
        routes.add(route1);
        routes.add(route2);

        // 模拟内存路由仓库行为
        when(infraServiceClient.routes()).thenReturn(Mono.just(routes));
        when(memoryRouteRepository.getRouteDefinitions()).thenReturn(Flux.empty());
        when(memoryRouteRepository.deleteAll(anyCollection())).thenReturn(Mono.empty());
        when(memoryRouteRepository.save(anyRouteDefinitionMono())).thenReturn(Mono.empty());

        // 执行测试
        assertDoesNotThrow(() -> memoryRouteLoader.refreshRoutes().block());

        // 验证调用
        verify(memoryRouteRepository).getRouteDefinitions();
        verify(memoryRouteRepository).deleteAll(anyCollection());
        verify(memoryRouteRepository, times(2)).save(anyRouteDefinitionMono());
    }

    @Test
    @DisplayName("测试单条路由新增或更新")
    void testUpsertRoute() {
        RouteDefinitionVo route = new RouteDefinitionVo();
        route.setId(100L);
        route.setName("test-route-upsert");
        route.setEndpointHost("http://test-service");
        route.setContext("api");
        route.setPath("/demo/**");

        when(memoryRouteRepository.save(anyRouteDefinitionMono())).thenReturn(Mono.empty());

        assertDoesNotThrow(() -> memoryRouteLoader.upsertRoute(route).block());
        verify(memoryRouteRepository).save(anyRouteDefinitionMono());
        verify(applicationEventPublisher).publishEvent(any());
    }

    @Test
    @DisplayName("测试单条路由删除")
    void testDeleteRoute() {
        when(memoryRouteRepository.delete(anyStringMono())).thenReturn(Mono.empty());

        assertDoesNotThrow(() -> memoryRouteLoader.deleteRoute(100L).block());
        verify(memoryRouteRepository).delete(anyStringMono());
        verify(applicationEventPublisher).publishEvent(any());
    }

    @SuppressWarnings("unchecked")
    private Mono<RouteDefinition> anyRouteDefinitionMono() {
        return any(Mono.class);
    }

    @SuppressWarnings("unchecked")
    private Mono<String> anyStringMono() {
        return any(Mono.class);
    }
}
