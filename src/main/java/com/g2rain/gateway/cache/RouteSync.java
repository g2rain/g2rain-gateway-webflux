package com.g2rain.gateway.cache;


import com.g2rain.common.syncer.AbstractMessageStorage;
import com.g2rain.gateway.model.route.RouteDefinitionVo;
import com.g2rain.gateway.route.GatewayRouteLoader;
import com.g2rain.gateway.enums.SyncerEnum;
import jakarta.annotation.PreDestroy;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 路由消息同步存储（与 g2rain-gateway-webmvc 侧 {@link com.g2rain.gateway.cache.RouteSync} 对齐）。
 *
 * <p>保留消息单条语义；对 create 做短窗口聚合，以应对约 1s 内上百条导入类流量。update / delete 仍即时生效。</p>
 *
 * @author alpha
 * @since 2026/4/16
 */
@Slf4j
@Service
public class RouteSync extends AbstractMessageStorage<Long, RouteDefinitionVo, String> {

    private static final long CREATE_WINDOW_MILLIS = 1_000L;

    private static final Scheduler ROUTE_SYNC_SCHEDULER = Schedulers.newSingle("route-sync");

    private final GatewayRouteLoader gatewayRouteLoader;

    private final Object createBufferMonitor = new Object();

    private final Map<Long, RouteDefinitionVo> pendingCreates = new ConcurrentHashMap<>();

    private final AtomicBoolean createFlushScheduled = new AtomicBoolean(false);

    private final ScheduledExecutorService createWindowScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "route-sync-create-window");
        thread.setDaemon(true);
        return thread;
    });

    public RouteSync(GatewayRouteLoader gatewayRouteLoader) {
        this.gatewayRouteLoader = gatewayRouteLoader;
    }

    @Override
    protected @NonNull String dataSource() {
        return SyncerEnum.API_ROUTE.name();
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
        if (Objects.isNull(value)) {
            return;
        }

        synchronized (createBufferMonitor) {
            pendingCreates.put(key, value);
        }

        scheduleCreateFlush();
    }

    @Override
    protected void delete(@NonNull Long key) {
        flushPendingCreates();
        gatewayRouteLoader.remove(key).subscribeOn(ROUTE_SYNC_SCHEDULER).block();
    }

    @Override
    protected void update(@NonNull Long key, RouteDefinitionVo value) {
        flushPendingCreates();
        gatewayRouteLoader.upsert(value).subscribeOn(ROUTE_SYNC_SCHEDULER).block();
    }

    @Override
    protected String get(@NonNull Long key) {
        return null;
    }

    @PreDestroy
    public void destroy() {
        createWindowScheduler.shutdown();
        ROUTE_SYNC_SCHEDULER.dispose();
    }

    private void scheduleCreateFlush() {
        if (!createFlushScheduled.compareAndSet(false, true)) {
            return;
        }

        createWindowScheduler.schedule(
            () -> Thread.ofVirtual().name("route-sync-create-flush").start(this::flushPendingCreatesSafely),
            CREATE_WINDOW_MILLIS,
            TimeUnit.MILLISECONDS
        );
    }

    private void flushPendingCreatesSafely() {
        try {
            flushPendingCreates();
        } catch (Exception e) {
            log.error("批量创建路由 flush 失败: {}", e.getMessage(), e);
        }
    }

    private void flushPendingCreates() {
        try {
            List<RouteDefinitionVo> batch = drainPendingCreates();
            if (batch.isEmpty()) {
                return;
            }

            if (batch.size() == 1) {
                gatewayRouteLoader.upsert(batch.getFirst()).subscribeOn(ROUTE_SYNC_SCHEDULER).block();
                log.info("创建路由窗口 flush 完成, mode=upsert, batchSize=1");
            } else {
                ConcurrentMap<Long, RouteDefinitionVo> newRoutes = batch.stream()
                    .filter(v -> Objects.nonNull(v) && Objects.nonNull(v.getId()))
                    .collect(Collectors.toConcurrentMap(
                        RouteDefinitionVo::getId,
                        Function.identity(),
                        (_, b) -> b
                    ));

                gatewayRouteLoader.refresh(newRoutes).subscribeOn(ROUTE_SYNC_SCHEDULER).block();
                log.info("创建路由窗口 flush 完成, mode=refresh(delta), batchSize={}", batch.size());
            }
        } finally {
            createFlushScheduled.set(false);

            synchronized (createBufferMonitor) {
                if (!pendingCreates.isEmpty()) {
                    scheduleCreateFlush();
                }
            }
        }
    }

    private List<RouteDefinitionVo> drainPendingCreates() {
        synchronized (createBufferMonitor) {
            if (pendingCreates.isEmpty()) {
                return List.of();
            }

            List<RouteDefinitionVo> batch = new ArrayList<>(pendingCreates.values());
            pendingCreates.clear();
            return batch;
        }
    }
}
