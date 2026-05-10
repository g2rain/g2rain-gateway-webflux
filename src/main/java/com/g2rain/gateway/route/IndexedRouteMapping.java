package com.g2rain.gateway.route;

import com.g2rain.common.json.JsonCodecFactory;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.handler.FilteringWebHandler;
import org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于路径索引的 {@link RoutePredicateHandlerMapping} 实现：粗筛用 {@link RouteMatchHolder}，细判用 SCG 路由谓词。
 *
 * <p>Spring Cloud Gateway 默认的 {@code lookupRoute} 往往依赖 {@link RouteLocator} 提供的路由序列做匹配，
 * 在路由数量很大时成本偏高。本类通过 {@link #lookupRoute(ServerWebExchange)}：</p>
 * <ol>
 *     <li>用请求方法与「应用内路径」在 {@link RouteMatchHolder} 中做与 Gateway 规则一致的路径级粗筛，得到候选路由 id；</li>
 *     <li>用 {@link #routesById} 中缓存的不可变快照取出完整 {@link Route}；</li>
 *     <li>对候选执行 {@link org.springframework.cloud.gateway.handler.AsyncPredicate#apply}（包装为 {@link Mono}），
 *         仅当谓词为真时认定命中；</li>
 *     <li>命中后调用父类 {@link RoutePredicateHandlerMapping#validateRoute} 做框架级校验；
 *         父类 {@link RoutePredicateHandlerMapping#getHandlerInternal} 随后会移除
 *         {@link ServerWebExchangeUtils#GATEWAY_PREDICATE_ROUTE_ATTR} 并设置 {@link ServerWebExchangeUtils#GATEWAY_ROUTE_ATTR}，
 *         下游过滤器应以 {@code GATEWAY_ROUTE_ATTR} 为准。</li>
 * </ol>
 *
 * <p>标为 {@link Primary} 以便在存在多个 {@code RoutePredicateHandlerMapping} Bean 时优先本实现。</p>
 *
 * <p><strong>与 {@link RefreshRoutesEvent}：</strong>{@link RouteLocator#getRoutes()} 在刷新后可能变化，
 * 本类监听刷新事件并异步重建 {@code routesById}，保证 lookup 使用的 {@link Route} 与当前 Gateway 状态一致。</p>
 *
 * @author alpha
 * @since 2026/05/07
 */
@Slf4j
@Primary
@Component
public class IndexedRouteMapping extends RoutePredicateHandlerMapping {

    /**
     * 路径 → 路由 id 的匹配索引；与 {@link GatewayRouteLoader} 写入的 {@link com.g2rain.gateway.matcher.RuleDefinition} 同源。
     */
    private final RouteMatchHolder routeMatchHolder;

    /**
     * 官方 {@link RouteLocator}，用于在刷新时拉取全量 {@link Route} 以构建 id 映射（本类不用于每次 lookup 全量遍历）。
     */
    private final RouteLocator routeLocator;

    /**
     * 最近一次刷新得到的「路由 id → Route」只读快照；替换引用即可，无需在热路径上同步。
     *
     * <p>初始为 {@link Map#of()} 空表，避免 NPE；首次事件到达前 lookup 可能无命中。</p>
     */
    private final AtomicReference<Map<String, Route>> routesById = new AtomicReference<>(Map.of());

    /**
     * 构造并注册到 Spring：将父类所需的 WebHandler、{@code RouteLocator}、CORS 与环境注入父类构造。
     *
     * @param webHandler           SCG 过滤链 WebHandler
     * @param routeLocator         用于订阅路由流与刷新快照
     * @param globalCorsProperties 全局 CORS 配置（透传父类）
     * @param environment          环境（透传父类）
     * @param routeMatchHolder     路径粗筛索引
     */
    public IndexedRouteMapping(FilteringWebHandler webHandler, RouteLocator routeLocator, GlobalCorsProperties globalCorsProperties,
                               Environment environment, RouteMatchHolder routeMatchHolder) {
        super(webHandler, routeLocator, globalCorsProperties, environment);
        this.routeLocator = routeLocator;
        this.routeMatchHolder = routeMatchHolder;
    }

    /**
     * 响应控制面或本地 {@link GatewayRouteLoader} 发布的刷新事件，重建路由 id 快照。
     *
     * <p>使用 {@code collectMap} 将流聚合为 Map；失败时记录告警并保留旧快照，避免将映射清空导致全网关不可用。</p>
     *
     * @param event Spring 应用事件（此处仅作触发，不读取载荷）
     */
    @EventListener
    public void onRefresh(RefreshRoutesEvent event) {
        routeLocator.getRoutes()
            .collectMap(Route::getId)
            .doOnNext(m -> routesById.set(Collections.unmodifiableMap(m)))
            .onErrorResume(e -> {
                log.warn("刷新路由运行时快照失败", e);
                return Mono.empty();
            })
            .subscribe();
    }

    /**
     * 解析当前请求对应的 {@link Route}：索引粗筛 + 谓词细判 + 框架校验。
     *
     * <p>不调用 {@code super.lookupRoute}，因此<strong>不会</strong>在索引未命中时回退到全量
     * {@code RouteLocator} 扫描；这是刻意的性能取舍，需保证索引与 {@code RouteDefinition} 同步完整。</p>
     *
     * @param exchange 当前交换对象
     * @return 命中且通过校验的单个路由；否则 {@link Mono#empty()}
     */
    @Override
    protected @NonNull Mono<Route> lookupRoute(ServerWebExchange exchange) {
        HttpMethod method = exchange.getRequest().getMethod();
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        Map<String, Route> snapshot = routesById.get();

        log.info("routes:{}", JsonCodecFactory.instance().obj2str(routesById));

        return routeMatchHolder.matchRoute(method, path, routeId -> {
                Route route = snapshot.get(String.valueOf(routeId));

                log.info("route:{}", JsonCodecFactory.instance().obj2str(route));

                if (Objects.isNull(route)) {
                    // 索引侧有 id 但快照中尚无对应 Route（刷新延迟或配置不一致）
                    return Mono.empty();
                }

                // 与 SCG 默认行为对齐：谓词可能返回 Publisher<Boolean>，空序列按 false 处理
                return Mono.from(route.getPredicate().apply(exchange))
                    .defaultIfEmpty(false)
                    .flatMap(ok -> ok ? Mono.just(route) : Mono.empty())
                    .onErrorResume(e -> {
                        log.debug("索引候选谓词执行失败 routeId={}", routeId, e);
                        return Mono.empty();
                    });
            })
            .map(route -> {
                log.info("routeId:{}", route.getId());
                validateRoute(route, exchange);
                return route;
            });
    }

    /**
     * 用于日志与诊断的简短名称。
     *
     * @return 固定返回 {@code "IndexedRouteMapping"}
     */
    @Override
    protected @NonNull String getSimpleName() {
        return "IndexedRouteMapping";
    }
}
