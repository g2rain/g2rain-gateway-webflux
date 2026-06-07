/**
 * 网关动态路由包（WebFlux / Spring Cloud Gateway Reactive）。
 *
 * <p>本包把「控制面下发的路由定义」与「数据面请求匹配」串起来：一侧负责把
 * {@link com.g2rain.gateway.model.route.RouteDefinitionVo} 等 VO 写入 Spring Cloud Gateway 的
 * {@link org.springframework.cloud.gateway.route.RouteDefinitionRepository} 并发布刷新事件；
 * 另一侧用 {@link com.g2rain.gateway.matcher.MatchEngine} 维护与 SCG 谓词一致的路径索引，
 * 在 {@link org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping}
 * 的 {@code lookupRoute} 路径上先做粗筛，再在候选上做谓词细判，避免每次全量拉路由。</p>
 *
 * <h2>核心类型与职责</h2>
 * <ul>
 *     <li>{@link com.g2rain.gateway.route.GatewayRouteLoader}：实现 {@code RouteDefinitionRepository}，
 *         负责 VO→{@link org.springframework.cloud.gateway.route.RouteDefinition} 的转换、内存路由表、
 *         与 {@link com.g2rain.gateway.route.RouteMatchHolder} 的索引同步，并在适当时机发布 {@link org.springframework.cloud.gateway.event.RefreshRoutesEvent}。</li>
 *     <li>{@link com.g2rain.gateway.route.RouteMatchHolder}：持有以 {@link java.lang.Long} 为业务目标的
 *         {@link com.g2rain.gateway.matcher.RuleDefinition} 与 {@link com.g2rain.gateway.matcher.MatchEngine}，
 *         对外提供按「方法 + 路径」的响应式匹配入口。</li>
 *     <li>{@link com.g2rain.gateway.route.IndexedRouteMapping}：继承 SCG 的 {@link org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping}，
 *         用索引匹配替代默认全量 {@code RouteLocator} 扫描，并与运行时 {@link org.springframework.cloud.gateway.route.Route} 快照组合完成 lookup。</li>
 * </ul>
 *
 * <h2>典型数据流</h2>
 * <ol>
 *     <li>控制面变更 → {@code GatewayRouteLoader} 更新内存 {@code RouteDefinition} 与 {@code RouteMatchHolder}。</li>
 *     <li>{@code publish()} 触发 {@code RefreshRoutesEvent} → {@code IndexedRouteMapping} 订阅并刷新 {@code Route} 快照。</li>
 *     <li>请求进入 → {@code IndexedRouteMapping.lookupRoute} → {@code RouteMatchHolder.matchRoute} 粗筛路由 id →
 *         对候选 {@link org.springframework.cloud.gateway.route.Route} 执行谓词 → 命中后写入 exchange 属性并校验。</li>
 * </ol>
 *
 * @author alpha
 * @since 2026/05/07
 */
package com.g2rain.gateway.route;
