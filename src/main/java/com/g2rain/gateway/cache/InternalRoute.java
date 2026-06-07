package com.g2rain.gateway.cache;


import com.g2rain.common.syncer.AbstractMessageStorage;
import com.g2rain.gateway.enums.SyncerEnum;
import com.g2rain.gateway.model.route.ServiceRegistryVo;
import com.g2rain.gateway.route.GatewayRouteLoader;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * 服务注册驱动的内部文档路由（OpenAPI）同步，与 WebMVC 侧逻辑对齐，落到底层为 SCG {@link GatewayRouteLoader}。
 */
@Slf4j
@Service
@AllArgsConstructor
public class InternalRoute extends AbstractMessageStorage<Long, ServiceRegistryVo, String> {

    private static final Scheduler INTERNAL_ROUTE_SCHEDULER = Schedulers.newSingle("internal-route-sync");

    private final GatewayRouteLoader gatewayRouteLoader;

    @Override
    protected @NonNull String dataSource() {
        return SyncerEnum.INTERNAL_ROUTE.name();
    }

    @Override
    protected @NonNull Class<ServiceRegistryVo> getValueType() {
        return ServiceRegistryVo.class;
    }

    @Override
    protected @NonNull Long getKey(@NonNull ServiceRegistryVo value) {
        return value.getId();
    }

    @Override
    protected void create(@NonNull Long key, ServiceRegistryVo value) {
        dispatch(gatewayRouteLoader.upsert(gatewayRouteLoader.registry2route(value)), "新增", key);
    }

    @Override
    protected void delete(@NonNull Long key) {
        dispatch(gatewayRouteLoader.remove(key), "删除", key);
    }

    @Override
    protected void update(@NonNull Long key, ServiceRegistryVo value) {
        create(key, value);
    }

    @Override
    protected String get(@NonNull Long key) {
        return null;
    }

    private void dispatch(Mono<Void> action, String operation, Long routeId) {
        action.subscribeOn(INTERNAL_ROUTE_SCHEDULER)
            .doOnError(e -> log.error("同步{}内部路由失败, routeId={}", operation, routeId, e))
            .onErrorResume(_ -> Mono.empty())
            .subscribe();
    }
}
