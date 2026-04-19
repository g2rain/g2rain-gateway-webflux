package com.g2rain.gateway.route;


import com.g2rain.common.utils.Strings;
import com.g2rain.gateway.client.InfraServiceClient;
import com.g2rain.gateway.model.route.RouteDefinitionVo;
import com.g2rain.gateway.utils.Constants;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.reactor.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;


/**
 * <h1>MemoryRouteLoader</h1>
 *
 * <p>
 * 网关动态路由加载器，负责在网关启动或刷新时从基础服务加载路由定义到内存中，
 * 并将其转换为 {@link RouteDefinition} 对象以供 Spring Cloud Gateway 使用。
 * </p>
 *
 * <h2>核心职责</h2>
 * <ul>
 *     <li>启动时自动加载路由数据（实现 {@link SmartInitializingSingleton} 接口）</li>
 *     <li>提供刷新路由能力 {@link #refreshRoutes()}</li>
 *     <li>构建路由谓词（Predicates）和过滤器（Filters）</li>
 *     <li>发布路由刷新事件以通知网关更新路由配置</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <ul>
 *     <li>微服务网关动态路由加载</li>
 *     <li>基于基础支撑路由列表配置</li>
 *     <li>启动时或路由更新时自动同步路由到内存</li>
 * </ul>
 *
 * <h2>执行流程</h2>
 * <ol>
 *     <li>{@link #customize(NettyReactiveWebServerFactory)}：在 Netty Web 服务器启动之前执行动Netty服务配置</li>
 *     <li>{@link #refreshRoutes()}：从基础支撑服务获取路由配置，删除内存中旧的路由，加载新的路由</li>
 *     <li>{@link #buildRouteDefinition(RouteDefinitionVo)}：将路由实体转换为 {@link RouteDefinition}</li>
 *     <li>{@link #publish()}：发布路由刷新事件</li>
 * </ol>
 *
 * <h2>异常处理</h2>
 * <p>在加载单个路由时，若发生异常，会记录日志并继续处理其它路由，避免因单条路由异常导致全部加载失败。</p>
 *
 * @author alpha
 * @since 2025/9/27
 */
@Slf4j
@Component
public class MemoryRouteLoader implements WebServerFactoryCustomizer<@NonNull NettyReactiveWebServerFactory>, ApplicationEventPublisherAware {
    /**
     * 路由匹配谓词的自动生成键（Path Predicate 使用）。
     */
    private static final String ROUTE_PREDICATE_KEY_0 = "_genkey_0";

    /**
     * 路由匹配谓词的自动生成键（Method Predicate 使用）。
     */
    private static final String ROUTE_PREDICATE_KEY_1 = "_genkey_1";

    /**
     * 内存路由仓库，保存当前网关可用的路由定义。
     */
    private final MemoryRouteRepository memoryRouteRepository;

    private final InfraServiceClient infraServiceClient;

    /**
     * Spring 应用事件发布器，用于发布路由刷新事件 {@link RefreshRoutesEvent}。
     */
    private ApplicationEventPublisher publisher;

    /**
     * 构造函数，注入内存仓库。
     *
     * @param memoryRouteRepository 内存路由仓库
     */
    public MemoryRouteLoader(InfraServiceClient infraServiceClient, MemoryRouteRepository memoryRouteRepository) {
        this.infraServiceClient = infraServiceClient;
        this.memoryRouteRepository = memoryRouteRepository;
    }

    /**
     * 注入 Spring 应用事件发布器。
     *
     * @param publisher Spring 应用事件发布器
     */
    @Override
    public void setApplicationEventPublisher(@NonNull ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * 自定义 NettyReactiveWebServerFactory 启动过程。
     * <p>
     * 在 Netty Web 服务器启动之前执行动态路由的全量加载，
     * 确保 Gateway 在对外提供服务之前已完成路由初始化。
     * 阻塞调用 refreshRoutes()，保证路由加载完成后才继续启动。
     *
     * @param factory NettyReactiveWebServerFactory，用于定制 Netty 服务器行为
     */
    @Override
    public void customize(NettyReactiveWebServerFactory factory) {
        // 在 Netty 服务器启动之前执行自定义逻辑
        // 此处用于在 Gateway 正式开放给客户端之前，阻塞加载所有动态路由
        // refreshRoutes() 返回 Mono，调用 block() 会阻塞当前线程直到路由加载完成
        factory.addServerCustomizers(httpServer -> {
            log.info("Gateway startup: synchronizing dynamic routes before Netty starts...");
            refreshRoutes().block(); // 阻塞直到加载完成
            log.info("Dynamic route loading complete.");
            return httpServer;
        });
    }

    /**
     * 刷新所有路由。
     *
     * <p>执行流程：</p>
     * <ol>
     *     <li>从基础服务加载所有路由定义</li>
     *     <li>删除内存中已有的路由</li>
     *     <li>将基础服务路由加载到内存中</li>
     *     <li>发布路由刷新事件</li>
     * </ol>
     *
     * @return {@link Mono<Void>} 路由刷新完成的信号
     */
    public Mono<@NonNull Void> refreshRoutes() {
        return infraServiceClient.routes()
            .flatMap(routes -> memoryRouteRepository.getRouteDefinitions()
                .map(route -> Objects.requireNonNull(route.getId()))
                .collectList()
                .flatMap(memoryRouteRepository::deleteAll)
                .then(loadRoute(routes))
            )
            .then(Mono.defer(this::publish));
    }

    /**
     * 新增或更新单条路由，并发布刷新事件。
     *
     * @param route 单条路由定义
     * @return {@link Mono<Void>} 操作完成信号
     */
    public Mono<@NonNull Void> upsertRoute(@NonNull RouteDefinitionVo route) {
        return doLoadRoute(route).then(Mono.defer(this::publish));
    }

    /**
     * 删除单条路由，并发布刷新事件。
     *
     * @param routeId 路由 ID
     * @return {@link Mono<Void>} 操作完成信号
     */
    public Mono<@NonNull Void> deleteRoute(@NonNull Long routeId) {
        return memoryRouteRepository.delete(Mono.just(String.valueOf(routeId)))
            .then(Mono.defer(this::publish));
    }

    /**
     * 将基础服务路由列表加载到内存仓库。
     *
     * @param routes 基础服务路由实体列表
     * @return {@link Mono<Void>} 加载完成的信号
     */
    private Mono<@NonNull Void> loadRoute(List<RouteDefinitionVo> routes) {
        return Flux.fromIterable(routes)
            .flatMap(this::doLoadRoute)
            .then();
    }

    /**
     * 加载单条路由。
     *
     * <p>步骤：</p>
     * <ol>
     *     <li>调用 {@link #buildRouteDefinition(RouteDefinitionVo)} 构建路由定义</li>
     *     <li>保存到 {@link MemoryRouteRepository}</li>
     * </ol>
     *
     * <p>如果路由构建失败，记录日志并返回错误 {@link Mono}。</p>
     *
     * @param route 路由实体
     * @return {@link Mono<Void>} 加载完成的信号
     */
    private Mono<@NonNull Void> doLoadRoute(RouteDefinitionVo route) {
        try {
            RouteDefinition routeDefinition = buildRouteDefinition(route);
            return memoryRouteRepository.save(Mono.just(routeDefinition)).then();
        } catch (Exception e) {
            log.error("Failed to load route: {}", route.getId(), e);
            return Mono.error(e);
        }
    }

    /**
     * 构建路由定义。
     *
     * @param route 路由实体
     * @return {@link RouteDefinition} 路由定义
     */
    private RouteDefinition buildRouteDefinition(RouteDefinitionVo route) {
        RouteDefinition definition = new RouteDefinition();
        definition.setId(String.valueOf(route.getId()));
        definition.setOrder(calculateOrder(route.getPath()));
        definition.setUri(UriComponentsBuilder.fromUriString(route.getEndpointHost()).build().toUri());
        // 关键：context 语义存 metadata
        definition.getMetadata().put(Constants.ROUTE_CONTEXT_PATH, route.getContext());
        definition.setPredicates(buildPredicates(route));
        definition.setFilters(buildFilters(route));
        return definition;
    }

    /**
     * 计算路由执行顺序。
     *
     * <p>路径越深，顺序值越小，优先级越高。</p>
     *
     * @param path 路由路径
     * @return 路由顺序值
     */
    private int calculateOrder(String path) {
        if (Strings.isBlank(path)) return 1000;
        int order = 1000;
        for (String part : path.split("/")) {
            if ("**".equals(part)) break;
            order--;
        }
        return order;
    }

    /**
     * 构建路由谓词（Predicates）。
     *
     * @param route 路由实体
     * @return {@link List}<{@link PredicateDefinition}> 路由谓词列表
     */
    private List<PredicateDefinition> buildPredicates(RouteDefinitionVo route) {
        List<PredicateDefinition> predicates = new LinkedList<>();
        PredicateDefinition pathPredicate = new PredicateDefinition();
        pathPredicate.setName("Path");
        pathPredicate.addArg(ROUTE_PREDICATE_KEY_0, "/" + route.getContext() + route.getPath());
        predicates.add(pathPredicate);

        if (Strings.isNotBlank(route.getMethod()) && !"all".equalsIgnoreCase(route.getMethod())) {
            PredicateDefinition methodPredicate = new PredicateDefinition();
            methodPredicate.setName("Method");
            methodPredicate.addArg(ROUTE_PREDICATE_KEY_0, route.getMethod());
            predicates.add(methodPredicate);
        }
        return predicates;
    }

    /**
     * 构建路由过滤器（Filters）。
     *
     * @param route 路由实体
     * @return {@link List}<{@link FilterDefinition}> 路由过滤器列表
     */
    private List<FilterDefinition> buildFilters(RouteDefinitionVo route) {
        List<FilterDefinition> filters = new ArrayList<>();

        // 移除第一个路径段
        FilterDefinition stripPrefix = new FilterDefinition();
        stripPrefix.setName("StripPrefix");
        stripPrefix.addArg("parts", "1");
        filters.add(stripPrefix);

        // 路径重写
        if (Strings.isNotBlank(route.getEndpointPath()) && !"/**".equals(route.getPath())) {
            FilterDefinition rewriteFilter = new FilterDefinition();
            rewriteFilter.setName("RewritePath");
            rewriteFilter.addArg("regexp", route.getPath());
            rewriteFilter.addArg("replacement", route.getEndpointPath());
            filters.add(rewriteFilter);
        }

        // 请求头添加
        if (Strings.isNotBlank(route.getHeaderParameters())) {
            filters.addAll(buildHeaderFilters(route.getHeaderParameters()));
        }

        return filters;
    }

    /**
     * 构建请求头部过滤器。
     *
     * <p>格式：key1=value1;key2=value2</p>
     *
     * @param headerParameters 请求头参数字符串
     * @return {@link List}<{@link FilterDefinition}> 请求头过滤器列表
     */
    private List<FilterDefinition> buildHeaderFilters(String headerParameters) {
        List<FilterDefinition> filters = new ArrayList<>();
        String[] entries = headerParameters.split(";");
        for (String entry : entries) {
            if (Strings.isBlank(entry)) continue;
            String[] kv = entry.split("=");
            if (kv.length == 2) {
                FilterDefinition headerFilter = new FilterDefinition();
                headerFilter.setName("AddRequestHeader");
                headerFilter.addArg(ROUTE_PREDICATE_KEY_0, kv[0]);
                headerFilter.addArg(ROUTE_PREDICATE_KEY_1, kv[1]);
                filters.add(headerFilter);
            }
        }
        return filters;
    }

    /**
     * 发布路由刷新事件。
     *
     * @return {@link Mono<Void>} 发布完成信号
     */
    private Mono<@NonNull Void> publish() {
        publisher.publishEvent(new RefreshRoutesEvent(this));
        return Mono.empty();
    }
}
