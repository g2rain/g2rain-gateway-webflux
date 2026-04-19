package com.g2rain.gateway.route;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.RouteDefinition;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("内存路由仓库测试")
public class MemoryRouteRepositoryTest {

    private MemoryRouteRepository memoryRouteRepository;

    @BeforeEach
    void setUp() {
        memoryRouteRepository = new MemoryRouteRepository();
    }

    @Test
    @DisplayName("测试保存路由定义")
    void testSaveRouteDefinition() {
        // 准备路由定义
        RouteDefinition routeDefinition = new RouteDefinition();
        routeDefinition.setId("test-route");

        // 执行保存操作
        Mono<Void> result = memoryRouteRepository.save(Mono.just(routeDefinition));

        // 验证结果
        assertDoesNotThrow(() -> result.block());
    }

    @Test
    @DisplayName("测试获取路由定义")
    void testGetRouteDefinitions() {
        // 准备路由定义
        RouteDefinition route1 = new RouteDefinition();
        route1.setId("route-1");

        RouteDefinition route2 = new RouteDefinition();
        route2.setId("route-2");

        // 保存路由定义
        memoryRouteRepository.save(Mono.just(route1)).block();
        memoryRouteRepository.save(Mono.just(route2)).block();

        // 执行获取操作
        Flux<RouteDefinition> routeFlux = memoryRouteRepository.getRouteDefinitions();

        List<RouteDefinition> routes = new ArrayList<>();
        routeFlux.subscribe(routes::add);

        assertEquals(2, routes.size());
    }

    @Test
    @DisplayName("测试删除路由定义")
    void testDeleteRouteDefinition() {
        // 准备路由定义
        RouteDefinition routeDefinition = new RouteDefinition();
        routeDefinition.setId("test-route");

        // 保存路由定义
        memoryRouteRepository.save(Mono.just(routeDefinition)).block();

        // 验证保存成功
        List<RouteDefinition> savedRoutes = new ArrayList<>();
        memoryRouteRepository.getRouteDefinitions().subscribe(savedRoutes::add);
        assertEquals(1, savedRoutes.size());

        // 执行删除操作
        Mono<Void> result = memoryRouteRepository.delete(Mono.just("test-route"));

        // 验证删除成功
        assertDoesNotThrow(() -> result.block());

        // 验证路由已删除
        List<RouteDefinition> remainingRoutes = new ArrayList<>();
        memoryRouteRepository.getRouteDefinitions().subscribe(remainingRoutes::add);
        assertTrue(remainingRoutes.isEmpty());
    }

    @Test
    @DisplayName("测试批量删除路由定义")
    void testDeleteAllRouteDefinitions() {
        // 准备路由定义
        RouteDefinition route1 = new RouteDefinition();
        route1.setId("route-1");

        RouteDefinition route2 = new RouteDefinition();
        route2.setId("route-2");

        RouteDefinition route3 = new RouteDefinition();
        route3.setId("route-3");

        // 保存路由定义
        memoryRouteRepository.save(Mono.just(route1)).block();
        memoryRouteRepository.save(Mono.just(route2)).block();
        memoryRouteRepository.save(Mono.just(route3)).block();

        // 验证保存成功
        List<RouteDefinition> savedRoutes = new ArrayList<>();
        memoryRouteRepository.getRouteDefinitions().subscribe(savedRoutes::add);
        assertEquals(3, savedRoutes.size());

        // 执行批量删除操作
        List<String> routeIds = Arrays.asList("route-1", "route-2");
        Mono<Void> result = memoryRouteRepository.deleteAll(routeIds);

        // 验证批量删除成功
        assertDoesNotThrow(() -> result.block());

        // 验证剩余路由
        List<RouteDefinition> remainingRoutes = new ArrayList<>();
        memoryRouteRepository.getRouteDefinitions().subscribe(remainingRoutes::add);
        assertEquals(1, remainingRoutes.size());
    }

    @Test
    @DisplayName("测试删除空集合")
    void testDeleteAllWithEmptyCollection() {
        // 执行删除空集操作
        Mono<Void> result = memoryRouteRepository.deleteAll(Arrays.asList());

        // 验证操作完成
        assertDoesNotThrow(() -> result.block());
    }
}
