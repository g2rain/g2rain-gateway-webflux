package com.g2rain.gateway.route;

import com.g2rain.gateway.client.BasisServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cloud.gateway.route.RouteDefinition;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GatewayRouteLoader} 作为 {@link org.springframework.cloud.gateway.route.RouteDefinitionRepository} 的存储语义测试。
 */
@DisplayName("RouteDefinitionRepository（MemoryRouteLoader）测试")
public class MemoryRouteRepositoryTest {

    private GatewayRouteLoader repository;

    @BeforeEach
    void setUp() {
        repository = new GatewayRouteLoader(
            Mockito.mock(BasisServiceClient.class),
            Mockito.mock(RouteMatchHolder.class)
        );
    }

    @Test
    @DisplayName("测试保存路由定义")
    void testSaveRouteDefinition() {
        RouteDefinition routeDefinition = new RouteDefinition();
        routeDefinition.setId("test-route");

        Mono<Void> result = repository.save(Mono.just(routeDefinition));

        assertDoesNotThrow(() -> result.block());
    }

    @Test
    @DisplayName("测试获取路由定义")
    void testGetRouteDefinitions() {
        RouteDefinition route1 = new RouteDefinition();
        route1.setId("route-1");

        RouteDefinition route2 = new RouteDefinition();
        route2.setId("route-2");

        repository.save(Mono.just(route1)).block();
        repository.save(Mono.just(route2)).block();

        Flux<RouteDefinition> routeFlux = repository.getRouteDefinitions();

        List<RouteDefinition> routes = new ArrayList<>();
        routeFlux.subscribe(routes::add);

        assertEquals(2, routes.size());
    }

    @Test
    @DisplayName("测试删除路由定义")
    void testDeleteRouteDefinition() {
        RouteDefinition routeDefinition = new RouteDefinition();
        routeDefinition.setId("test-route");

        repository.save(Mono.just(routeDefinition)).block();

        List<RouteDefinition> savedRoutes = new ArrayList<>();
        repository.getRouteDefinitions().subscribe(savedRoutes::add);
        assertEquals(1, savedRoutes.size());

        Mono<Void> result = repository.delete(Mono.just("test-route"));

        assertDoesNotThrow(() -> result.block());

        List<RouteDefinition> remainingRoutes = new ArrayList<>();
        repository.getRouteDefinitions().subscribe(remainingRoutes::add);
        assertTrue(remainingRoutes.isEmpty());
    }

    @Test
    @DisplayName("测试批量删除路由定义")
    void testDeleteAllRouteDefinitions() {
        RouteDefinition route1 = new RouteDefinition();
        route1.setId("route-1");

        RouteDefinition route2 = new RouteDefinition();
        route2.setId("route-2");

        RouteDefinition route3 = new RouteDefinition();
        route3.setId("route-3");

        repository.save(Mono.just(route1)).block();
        repository.save(Mono.just(route2)).block();
        repository.save(Mono.just(route3)).block();

        List<RouteDefinition> savedRoutes = new ArrayList<>();
        repository.getRouteDefinitions().subscribe(savedRoutes::add);
        assertEquals(3, savedRoutes.size());

        List<String> routeIds = Arrays.asList("route-1", "route-2");
        Mono<Void> result = repository.deleteAll(routeIds);

        assertDoesNotThrow(() -> result.block());

        List<RouteDefinition> remainingRoutes = new ArrayList<>();
        repository.getRouteDefinitions().subscribe(remainingRoutes::add);
        assertEquals(1, remainingRoutes.size());
    }

    @Test
    @DisplayName("测试删除空集合")
    void testDeleteAllWithEmptyCollection() {
        Mono<Void> result = repository.deleteAll(Arrays.asList());

        assertDoesNotThrow(() -> result.block());
    }
}
