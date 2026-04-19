package com.g2rain.gateway.cache;


import com.g2rain.common.syncer.AbstractMessageStorage;
import com.g2rain.gateway.model.route.RouteDefinitionVo;
import com.g2rain.gateway.route.MemoryRouteLoader;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * @author alpha
 * @since 2026/4/16
 */
@Slf4j
@Service
@AllArgsConstructor
public class RouteSync extends AbstractMessageStorage<Long, RouteDefinitionVo, String> {
    private static final Scheduler ROUTE_SYNC_SCHEDULER = Schedulers.newSingle("route-sync");
    private final MemoryRouteLoader memoryRouteLoader;

    @Override
    protected @NonNull String dataSource() {
        return "ROUTE_DEFINE";
    }

    @Override
    protected @NonNull Class<RouteDefinitionVo> getValueType() {
        return RouteDefinitionVo.class;
    }

    @Override
    protected @NonNull Long getKey(@NonNull RouteDefinitionVo value) {
        return value.getId();
    }

    @Override
    protected void create(@NonNull Long key, RouteDefinitionVo value) {
        dispatch(memoryRouteLoader.upsertRoute(value), "新增", key);
    }

    @Override
    protected void delete(@NonNull Long key) {
        dispatch(memoryRouteLoader.deleteRoute(key), "删除", key);
    }

    @Override
    protected void update(@NonNull Long key, RouteDefinitionVo value) {
        dispatch(memoryRouteLoader.upsertRoute(value), "更新", key);
    }

    @Override
    protected String get(@NonNull Long key) {
        return null;
    }

    private void dispatch(Mono<Void> action, String operation, Long routeId) {
        action
            .subscribeOn(ROUTE_SYNC_SCHEDULER)
            .doOnError(e -> log.error("同步{}路由失败, routeId={}", operation, routeId, e))
            .onErrorResume(e -> Mono.empty())
            .subscribe();
    }
}
