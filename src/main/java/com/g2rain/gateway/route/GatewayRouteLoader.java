package com.g2rain.gateway.route;

import com.g2rain.common.json.JsonCodecFactory;
import com.g2rain.common.utils.Collections;
import com.g2rain.common.utils.Strings;
import com.g2rain.gateway.client.BasisServiceClient;
import com.g2rain.gateway.matcher.MatcherUtils;
import com.g2rain.gateway.matcher.RuleDefinition;
import com.g2rain.gateway.model.route.RouteDefinitionVo;
import com.g2rain.gateway.model.route.ServiceRegistryVo;
import com.g2rain.gateway.utils.Constants;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.reactor.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 网关动态路由加载器：同时扮演「SCG 路由定义仓库」与「控制面到运行时」的桥。
 *
 * <p>职责大致分为三块：</p>
 * <ul>
 *     <li><strong>RouteDefinitionRepository</strong>：向 Spring Cloud Gateway 提供内存中的
 *         {@link RouteDefinition}（{@link #getRouteDefinitions} / {@link #save} / {@link #delete}），
 *         供框架装配 {@link org.springframework.cloud.gateway.route.Route}。</li>
 *     <li><strong>与 Basis 控制面同步</strong>：通过 {@link BasisServiceClient} 拉取 API 路由与注册中心衍生路由，
 *         合并为 map 后走 {@link #refresh(Map)}（{@code putAll} + 对本批条目 {@link #doLoadRoute} + 按当前 {@link #routeDefinitions} 重建索引）；
 *         {@link #load()} 与 {@link com.g2rain.gateway.cache.RouteSync} 共用该方法。</li>
 *     <li><strong>生命周期钩子</strong>：实现 {@link WebServerFactoryCustomizer}，在 Netty 真正监听端口前
 *         {@link #load()} 一次，避免首个请求打到尚未就绪的路由表。</li>
 * </ul>
 *
 * <p>路由变更后通过 {@link ApplicationEventPublisher} 发布 {@link RefreshRoutesEvent}，
 * 触发 {@link IndexedRouteMapping} 等组件刷新运行时视图。</p>
 *
 * @author alpha
 * @since 2026/05/07
 */
@Slf4j
@Component
public class GatewayRouteLoader implements RouteDefinitionRepository, WebServerFactoryCustomizer<@NonNull NettyReactiveWebServerFactory>, ApplicationEventPublisherAware {

    /**
     * 每条动态路由统一挂载的一级路径裁剪过滤器，与路径前缀拼装策略配套。
     */
    private static final List<FilterDefinition> STRIP_PREFIX_FILTERS = List.of(new FilterDefinition("StripPrefix=1"));

    /**
     * Spring Cloud Gateway 框架读取的「路由定义」表：key 为路由 id 字符串，与 {@link RouteDefinition#getId} 一致。
     */
    private final Map<String, RouteDefinition> routes = new ConcurrentHashMap<>();

    /**
     * 控制面下发的路由 VO 真相源：key 为数值型路由 id，供增量 upsert/remove 与全量 replace 使用。
     */
    private final ConcurrentMap<Long, RouteDefinitionVo> routeDefinitions = new ConcurrentHashMap<>();

    /**
     * 与 {@link RouteDefinition} 并行的路径匹配索引；id 与 VO、SCG Route 一致。
     */
    private final RouteMatchHolder routeMatchHolder;

    /**
     * 拉取 Basis 侧路由与注册信息的 Feign/Reactive 客户端。
     */
    private final BasisServiceClient basisServiceClient;

    /**
     * 由 Spring 注入，用于在路由表变更末尾发布 {@link RefreshRoutesEvent}。
     */
    private ApplicationEventPublisher publisher;

    /**
     * @param basisServiceClient 控制面数据源
     * @param routeMatchHolder   路径索引持有器（本类写入，{@link IndexedRouteMapping} 读取）
     */
    public GatewayRouteLoader(BasisServiceClient basisServiceClient, RouteMatchHolder routeMatchHolder) {
        this.basisServiceClient = basisServiceClient;
        this.routeMatchHolder = routeMatchHolder;
    }

    /**
     * Netty 启动前同步阻塞加载全量路由，保证首个请求可见完整路由表。
     *
     * @param factory 响应式 WebServer 工厂
     */
    @Override
    public void customize(NettyReactiveWebServerFactory factory) {
        factory.addServerCustomizers(httpServer -> {
            log.info("Gateway startup: synchronizing dynamic routes before Netty starts...");
            load().block();
            log.info("Dynamic route loading complete.");
            return httpServer;
        });
    }

    /**
     * 从 Basis 拉取 API 路由与注册列表，合并去重后刷新 {@link #routeDefinitions}、{@link #routes}、{@link RouteMatchHolder}，
     * 并发布刷新事件。
     *
     * <p>合并策略：API 列表与注册中心列表各自转成 map 后 {@code putAll}；同一 id 时后一次 {@code putAll}（注册中心派生路由）覆盖前者。</p>
     *
     * @return 完成时发出空完成的 {@link Mono}
     */
    public Mono<@NonNull Void> load() {
        return Mono.zip(
            basisServiceClient.selectRouteDefinitions(),
            basisServiceClient.getServiceRegistry().collectList()
        ).flatMap(tuple -> {
            Map<Long, RouteDefinitionVo> merged = tuple.getT1().stream()
                .filter(vo -> Objects.nonNull(vo) && Objects.nonNull(vo.getId()))
                .collect(Collectors.toConcurrentMap(RouteDefinitionVo::getId, Function.identity(), (_, b) -> b));

            merged.putAll(tuple.getT2().stream()
                .filter(o -> Objects.nonNull(o.getId()))
                .map(this::registry2route)
                .collect(Collectors.toConcurrentMap(RouteDefinitionVo::getId, Function.identity(), (_, b) -> b)));
            return refresh(merged);
        });
    }

    /**
     * 将本批路由合并进 {@link #routeDefinitions}，写入 SCG 定义表，按当前完整 {@code routeDefinitions} 重建匹配索引并发布刷新事件。
     *
     * <p>仅 {@code putAll}，不 {@code clear}；启动时 {@link #load()} 在空表上写入即等价全量，消息批量创建则为增量合并。</p>
     *
     * @param routes key 为路由 id；空 map 时 {@link Mono#empty()}
     */
    public Mono<@NonNull Void> refresh(@NonNull Map<Long, RouteDefinitionVo> routes) {
        if (Collections.isEmpty(routes)) {
            return Mono.empty();
        }

        int batchSize = routes.size();
        return Mono.fromRunnable(() -> routeDefinitions.putAll(routes))
            .thenMany(Flux.fromIterable(routes.values()).flatMap(this::doLoadRoute))
            .then(Mono.fromRunnable(() -> routeMatchHolder.replace(toMatchRules(routeDefinitions.values()))))
            .then(Mono.fromRunnable(() ->
                log.info("路由刷新成功, 本批条数={}, 当前路由定义数={}, 路由列表:{}", batchSize, routeDefinitions.size(), JsonCodecFactory.instance().obj2str(routeDefinitions))
            ))
            .then(Mono.defer(this::publish));
    }

    /**
     * 单条 upsert：更新 SCG {@link RouteDefinition}、VO 缓存与 {@link RouteMatchHolder}，并发布事件。
     *
     * <p>若 VO 无法编译为有效匹配规则（路径非法等），则从 VO 表与索引中移除该 id，避免「半条」路由。</p>
     *
     * @param route 单条定义
     * @return 空完成的 {@link Mono}
     */
    public Mono<@NonNull Void> upsert(@NonNull RouteDefinitionVo route) {
        if (Objects.isNull(route.getId())) {
            log.warn("忽略无效路由 upsert 请求: {}", route);
            return Mono.empty();
        }

        return doLoadRoute(route)
            .then(Mono.fromRunnable(() -> toMatchRule(route).ifPresentOrElse(
                r -> {
                    routeDefinitions.put(route.getId(), route);
                    routeMatchHolder.upsert(r);
                },
                () -> {
                    routeDefinitions.remove(route.getId());
                    routeMatchHolder.remove(route.getId());
                }
            )))
            .then(Mono.fromRunnable(() ->
                log.info("单条路由更新完成, routeId={}, 当前路由定义数={}", route.getId(), routeDefinitions.size())
            ))
            .then(Mono.defer(this::publish));
    }

    /**
     * 删除单条：从仓库、VO 表、匹配索引中移除并发布事件。
     *
     * @param routeId 路由 id
     * @return 空完成的 {@link Mono}
     */
    public Mono<@NonNull Void> remove(@NonNull Long routeId) {
        return delete(Mono.just(String.valueOf(routeId)))
            .then(Mono.fromRunnable(() -> {
                routeDefinitions.remove(routeId);
                routeMatchHolder.remove(routeId);
            }))
            .then(Mono.fromRunnable(() ->
                log.info("单条路由删除完成, routeId={}, 当前路由定义数={}", routeId, routeDefinitions.size())
            ))
            .then(Mono.defer(this::publish));
    }

    /**
     * 将单条 VO 转为 {@link RouteDefinition} 并写入框架仓库（{@link #save}）。
     *
     * @param route 路由 VO
     * @return 成功时空完成；编译异常时返回 {@link Mono#error}
     */
    private Mono<@NonNull Void> doLoadRoute(RouteDefinitionVo route) {
        try {
            return save(Mono.just(toRouteDefinition(route))).then();
        } catch (Exception e) {
            log.error("Failed to load route: {}", route.getId(), e);
            return Mono.error(e);
        }
    }

    /**
     * 组装 SCG 所需的 {@link RouteDefinition}：URI、元数据、谓词与固定过滤器。
     *
     * @param route 源 VO
     * @return 可直接 {@link #save} 的定义
     */
    private RouteDefinition toRouteDefinition(RouteDefinitionVo route) {
        RouteDefinition definition = new RouteDefinition();
        definition.setId(String.valueOf(route.getId()));
        definition.setUri(UriComponentsBuilder.fromUriString(route.getEndpoint()).build().toUri());
        definition.getMetadata().put(Constants.ROUTE_CONTEXT_PATH, route.getRoutePrefix());
        definition.setPredicates(buildPredicates(route));
        definition.setFilters(STRIP_PREFIX_FILTERS);
        return definition;
    }

    /**
     * 构建 Gateway 谓词：必选 Path；若声明了 HTTP 方法则追加 Method。
     *
     * @param route 源 VO
     * @return 谓词列表（顺序可能影响 SCG 内部优化，一般 Path 在前）
     */
    private List<PredicateDefinition> buildPredicates(RouteDefinitionVo route) {
        List<PredicateDefinition> predicates = new ArrayList<>(2);
        String normalizePath = MatcherUtils.normalize("/" + route.getRoutePrefix() + "/" + route.getPath());
        predicates.add(new PredicateDefinition("Path=" + normalizePath));
        if (Strings.isNotBlank(route.getMethod())) {
            predicates.add(new PredicateDefinition("Method=" + route.getMethod()));
        }

        return predicates;
    }

    /**
     * 将多条 VO 转为 {@link RuleDefinition} 列表，供 {@link RouteMatchHolder#replace} 使用。
     *
     * @param routes 可迭代 VO 集合
     * @return 仅包含可编译为有效规则的项
     */
    private List<RuleDefinition<Long>> toMatchRules(Iterable<RouteDefinitionVo> routes) {
        List<RuleDefinition<Long>> out = new ArrayList<>();
        for (RouteDefinitionVo vo : routes) {
            toMatchRule(vo).ifPresent(out::add);
        }

        return out;
    }

    /**
     * 将单条 VO 转为匹配引擎用的 {@link RuleDefinition}：target 与 id 相同（均为 Long 型路由主键）。
     *
     * @param vo 源 VO
     * @return 路径或 id 无效时为空
     */
    private Optional<RuleDefinition<Long>> toMatchRule(RouteDefinitionVo vo) {
        if (Objects.isNull(vo) || Objects.isNull(vo.getId())) {
            return Optional.empty();
        }

        String normalizePath = MatcherUtils.normalize("/" + vo.getRoutePrefix() + "/" + vo.getPath());
        if (Strings.isBlank(normalizePath)) {
            return Optional.empty();
        }

        return Optional.of(new RuleDefinition<>(vo.getId(), vo.getMethod(), normalizePath, vo.getId()));
    }

    /**
     * 服务注册对象转路由定义实体对象
     * 如果后续存在多个特例的路由规则, 在考虑重构
     * 是否将 path 存入 nacos 等因素再进行考量
     *
     * @param registry 服务注册对象
     * @return 路由定义实体对象
     */
    public RouteDefinitionVo registry2route(ServiceRegistryVo registry) {
        RouteDefinitionVo route = new RouteDefinitionVo();
        route.setId(registry.getId());
        route.setMethod(HttpMethod.GET.name());
        route.setPath(Constants.DOC_PATH);
        route.setEndpoint(registry.getEndpoint());
        route.setRoutePrefix(registry.getRoutePrefix());
        return route;
    }

    /**
     * 通知网关框架重新装配 {@link org.springframework.cloud.gateway.route.Route}（见 {@link IndexedRouteMapping#onRefresh}）。
     *
     * @return 空 {@link Mono}
     */
    private Mono<@NonNull Void> publish() {
        publisher.publishEvent(new RefreshRoutesEvent(this));
        return Mono.empty();
    }

    /**
     * {@link RouteDefinitionRepository}：返回当前内存中全部路由定义。
     *
     * @return 路由定义流
     */
    @Override
    public @NonNull Flux<@NonNull RouteDefinition> getRouteDefinitions() {
        return Flux.fromIterable(routes.values());
    }

    /**
     * {@link RouteDefinitionRepository}：写入或覆盖单条定义。
     *
     * @param route 异步单条定义
     * @return 空完成的 {@link Mono}
     */
    @Override
    public @NonNull Mono<@NonNull Void> save(Mono<@NonNull RouteDefinition> route) {
        return route.flatMap(r -> {
            this.routes.put(r.getId(), r);
            return Mono.empty();
        });
    }

    /**
     * {@link RouteDefinitionRepository}：按 id 删除单条。
     *
     * @param routeId 路由 id（字符串）
     * @return 空完成的 {@link Mono}
     */
    @Override
    public @NonNull Mono<@NonNull Void> delete(Mono<@NonNull String> routeId) {
        return routeId.flatMap(id -> {
            this.routes.remove(id);
            return Mono.empty();
        });
    }

    /**
     * 批量从内存表移除（供全量 load 前清空旧路由）。
     *
     * @param routeIds 待删 id 集合
     * @return 空集合时立即完成
     */
    public Mono<@NonNull Void> deleteAll(Collection<String> routeIds) {
        if (Collections.isEmpty(routeIds)) {
            return Mono.empty();
        }

        routeIds.forEach(routes::remove);
        return Mono.empty();
    }

    /**
     * 注入事件发布器（Spring 回调）。
     *
     * @param publisher 应用事件总线
     */
    @Override
    public void setApplicationEventPublisher(@NonNull ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }
}
