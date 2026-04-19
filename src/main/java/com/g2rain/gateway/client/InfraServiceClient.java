package com.g2rain.gateway.client;


import com.g2rain.common.exception.ExceptionConverter;
import com.g2rain.common.model.Result;
import com.g2rain.gateway.model.cache.I18nMessageVo;
import com.g2rain.gateway.model.route.RouteDefinitionVo;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * InfraServiceClient
 *
 * <p>
 * 该类用于调用 g2rain-infra 基础支撑服务的接口，主要职责是：
 * <ul>
 *     <li>获取并返回封装在 {@link Result}&lt;T&gt; 中的基础设施数据</li>
 *     <li>提供分布式唯一 ID（Snowflake）生成能力</li>
 *     <li>以异步、非阻塞方式供 Gateway / WebFlux 链路使用</li>
 * </ul>
 * </p>
 *
 * <p>
 * 生产级设计说明：
 * <ul>
 *     <li>依赖全局配置的 {@link WebClient.Builder}，统一连接池、超时、重试策略</li>
 *     <li>通过 Nacos 服务名调用 g2rain-infra，实现客户端负载均衡</li>
 *     <li>所有接口均采用响应式 {@link Mono} 返回，禁止阻塞调用</li>
 * </ul>
 * </p>
 *
 * @author alpha
 * @see WebClient
 * @see Result
 * @since 2025/12/30
 */
@Component
public class InfraServiceClient {

    /**
     * WebClient 实例
     *
     * <p>
     * 该实例用于发起 HTTP 请求到 g2rain-infra 基础支撑服务，具有以下特点：
     * <ul>
     *     <li>支持异步非阻塞调用，提高高并发场景下的吞吐量</li>
     *     <li>通过 Nacos 服务名实现负载均衡调用</li>
     *     <li>继承全局 WebClient.Builder 配置的生产级特性：连接池、超时、全局重试</li>
     * </ul>
     * </p>
     */
    private final WebClient webClient;

    /**
     * InfraServiceClient 构造函数
     *
     * <p>
     * 构造流程说明：
     * <ul>
     *     <li>接收已配置生产级特性的 WebClient.Builder</li>
     *     <li>设置 baseUrl 为 基础支撑服务在 Nacos 注册的服务名 <code>g2rain-infra</code></li>
     *     <li>调用 <code>build()</code> 方法构建 WebClient 实例</li>
     * </ul>
     * </p>
     *
     * @param webClientBuilder 已配置生产级特性的 WebClient.Builder，包含连接池、超时、负载均衡及全局重试
     * @see WebClient.Builder
     */
    public InfraServiceClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
            // 设置 Auth 服务的 baseUrl，支持通过服务名发现
            .baseUrl("http://g2rain-infra")
            .build(); // 构建 WebClient 实例
    }

    /**
     * 获取 RouteDefinitionVo 集合
     *
     * <p>
     * 调用流程说明：
     * <ol>
     *     <li>向 g2rain-infra /route_definition/list 发送 GET 请求。</li>
     *     <li>Infra 服务返回 JSON 格式的 {@link Result}&lt;{@link List}&lt;{@link RouteDefinitionVo}&gt;&gt;。</li>
     *     <li>WebClient 自动解析响应为 {@link Mono}&lt;{@link Result}&lt;{@link List}&lt;{@link RouteDefinitionVo}&gt;&gt;&gt;。</li>
     *     <li>若请求失败（网络异常、响应超时），全局 WebClient 配置的重试策略会生效。</li>
     * </ol>
     * </p>
     *
     * @return {@link Mono}&lt;{@link Result}&lt;{@link List}&lt;{@link RouteDefinitionVo}&gt;&gt;&gt; 异步返回封装在 Result 中的 RouteDefinitionVo
     * <p>
     * 返回约定：
     * <ul>
     *     <li>code = 0 表示验证成功，data 为 RouteDefinitionVo</li>
     *     <li>code != 0 表示验证失败，message 字段说明错误原因</li>
     * </ul>
     * </p>
     */
    public Mono<List<RouteDefinitionVo>> routes() {
        return webClient.get()                          // 发起 GET 请求
            .uri("/route_definition/list")              // 调用基础支撑服务的 route_definition/list 接口
            .retrieve()                                 // 获取响应体
            // 将 JSON 响应解析为 Result<RouteDefinitionVo>
            .bodyToMono(new ParameterizedTypeReference<Result<List<RouteDefinitionVo>>>() {
            })
            .flatMap(result -> {
                // status == 200
                if (result.isSuccess()) {
                    // 返回 TokenJWTPayload
                    return Mono.just(result.getData());
                } else { // status != 200
                    // 统一抛出业务异常，带错误信息
                    return Mono.error(ExceptionConverter.of(result));
                }
            })
            .onErrorMap(ExceptionConverter::findBusinessExceptionOrDefault);
    }

    public Mono<List<I18nMessageVo>> errorMessages() {
        return webClient.get()                          // 发起 GET 请求
            .uri("/i18n_message/list")                  // 调用基础支撑服务的 /i18n_message/list 接口
            .retrieve()                                 // 获取响应体
            // 将 JSON 响应解析为 Result<List<I18nMessageVo>>
            .bodyToMono(new ParameterizedTypeReference<Result<List<I18nMessageVo>>>() {
            })
            .flatMap(result -> {
                // status == 200
                if (result.isSuccess()) {
                    // 返回 TokenJWTPayload
                    return Mono.just(result.getData());
                } else { // status != 200
                    // 统一抛出业务异常，带错误信息
                    return Mono.error(ExceptionConverter.of(result));
                }
            })
            .onErrorMap(ExceptionConverter::findBusinessExceptionOrDefault);
    }
}
