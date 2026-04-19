/**
 * <h1>动态路由管理模块</h1>
 *
 * <p>
 * 本包提供了网关动态路由的完整实现，包括从基础服务加载路由定义、
 * 缓存到内存、路由刷新以及路由构建的功能。
 * 它是基于 Spring Cloud Gateway 的动态路由扩展模块，
 * 实现了网关在运行时加载和刷新路由配置的能力。
 * </p>
 *
 * <h2>核心功能</h2>
 * <ul>
 *     <li>通过 webclient 查询路由列表</li>
 *     <li>将路由缓存到内存仓库以提升性能</li>
 *     <li>提供路由刷新机制，支持运行时更新路由</li>
 *     <li>构建路由谓词（Predicates）和过滤器（Filters）</li>
 *     <li>发布路由刷新事件，通知网关更新路由</li>
 * </ul>
 *
 * <h2>主要类</h2>
 * <ul>
 *     <li>{@link com.g2rain.gateway.route.MemoryRouteLoader} — 动态路由加载器，负责启动时和刷新时加载路由</li>
 *     <li>{@link com.g2rain.gateway.route.MemoryRouteRepository} — 内存路由仓库，存储当前可用的路由定义</li>
 *     <li>{@link com.g2rain.gateway.client.InfraServiceClient} — 通过 webclient 查询路由</li>
 *     <li>{@link com.g2rain.gateway.model.route.RouteDefinitionVo} — 路由定义基础服务实体类，映射路由配置信息</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <ul>
 *     <li>需要动态加载路由的网关系统</li>
 *     <li>路由定义存储在基础服务中，需要在运行时刷新路由</li>
 *     <li>希望提升路由加载性能，将路由缓存到内存</li>
 * </ul>
 *
 * <h2>执行流程</h2>
 * <ol>
 *     <li>应用启动后，{@link com.g2rain.gateway.route.MemoryRouteLoader#customize(NettyReactiveWebServerFactory)} 自动触发加载</li>
 *     <li>{@link com.g2rain.gateway.client.InfraServiceClient} 通过 webclient 查询路由列表</li>
 *     <li>{@link com.g2rain.gateway.route.MemoryRouteRepository} 清空旧路由并加载新路由</li>
 *     <li>{@link com.g2rain.gateway.route.MemoryRouteLoader} 构建路由谓词和过滤器并保存到内存仓库</li>
 *     <li>发布 {@link org.springframework.cloud.gateway.event.RefreshRoutesEvent}，触发网关路由刷新</li>
 * </ol>
 *
 * <h2>设计目标</h2>
 * <ul>
 *     <li>支持运行时动态路由刷新，无需重启网关</li>
 *     <li>保证路由加载的高性能和低延迟</li>
 *     <li>结构清晰，便于扩展和维护</li>
 * </ul>
 *
 * <p>
 * 典型的业务场景包括 API 网关、微服务路由管理、动态权限路由等。
 * </p>
 *
 * @author alpha
 * @since 2025/9/27
 */
package com.g2rain.gateway.route;

import org.springframework.boot.reactor.netty.NettyReactiveWebServerFactory;
