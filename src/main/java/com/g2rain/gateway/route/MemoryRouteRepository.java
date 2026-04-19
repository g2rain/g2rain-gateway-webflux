package com.g2rain.gateway.route;


import com.g2rain.common.utils.Collections;
import lombok.NonNull;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <h1>MemoryRouteRepository</h1>
 *
 * <p>
 * 内存路由仓库，用于在内存中存储和管理路由定义。
 * 实现 {@link RouteDefinitionRepository} 接口，提供响应式 API 操作路由。
 * </p>
 *
 * <h2>职责</h2>
 * <ul>
 *     <li>在内存中保存路由定义</li>
 *     <li>提供路由定义的增、删、查操作</li>
 *     <li>支持批量删除路由</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <p>
 * 适用于路由定义较少、变化不频繁，或者仅用于测试与临时存储场景。
 * </p>
 *
 * @author alpha
 * @since 2025/9/27
 */
@Component("memoryRouteRepository")
public class MemoryRouteRepository implements RouteDefinitionRepository {
    /**
     * 内存存储路由定义。
     *
     * <p>
     * key 为路由 ID，value 为 {@link RouteDefinition} 对象。
     * </p>
     */
    private final Map<String, RouteDefinition> routes = new ConcurrentHashMap<>();

    /**
     * 获取所有路由定义。
     *
     * @return {@link Flux}<{@link RouteDefinition}> 包含所有当前存储的路由定义
     */
    @Override
    public @NonNull Flux<@NonNull RouteDefinition> getRouteDefinitions() {
        return Flux.fromIterable(routes.values());
    }

    /**
     * 保存路由定义。
     *
     * <p>如果路由 ID 已存在，则会覆盖原有路由定义。</p>
     *
     * @param route {@link Mono}<{@link RouteDefinition}> 待保存的路由定义
     * @return {@link Mono<Void>} 保存完成的信号
     */
    @Override
    public @NonNull Mono<@NonNull Void> save(Mono<@NonNull RouteDefinition> route) {
        return route.flatMap(r -> {
            this.routes.put(r.getId(), r);
            return Mono.empty();
        });
    }

    /**
     * 删除指定 ID 的路由定义。
     *
     * @param routeId {@link Mono}<{@link String}> 待删除的路由 ID
     * @return {@link Mono<Void>} 删除完成的信号
     */
    @Override
    public @NonNull Mono<@NonNull Void> delete(Mono<@NonNull String> routeId) {
        return routeId.flatMap(id -> {
            this.routes.remove(id);
            return Mono.empty();
        });
    }

    /**
     * 批量删除路由定义。
     *
     * <p>适用于一次性清理多个路由定义的场景。</p>
     *
     * @param routeIds {@link Collection}<{@link String}> 待删除的路由 ID 集合
     * @return {@link Mono<Void>} 删除完成的信号
     *
     * <pre>{@code
     * List<String> ids = Arrays.asList("route1", "route2");
     * memoryRouteRepository.deleteAll(ids).subscribe();
     * }</pre>
     */
    public Mono<@NonNull Void> deleteAll(Collection<String> routeIds) {
        if (Collections.isEmpty(routeIds)) {
            return Mono.empty();
        }

        routeIds.forEach(routes::remove);
        return Mono.empty();
    }
}
