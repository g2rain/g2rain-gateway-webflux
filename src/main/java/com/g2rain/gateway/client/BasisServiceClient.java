package com.g2rain.gateway.client;


import com.g2rain.common.exception.ExceptionConverter;
import com.g2rain.common.model.Result;
import com.g2rain.common.utils.Strings;
import com.g2rain.gateway.model.auth.StaticAccessTokenResolve;
import com.g2rain.gateway.model.cache.AppIdName;
import com.g2rain.gateway.model.cache.OrganIdName;
import com.g2rain.gateway.model.route.BaseAuthorityApiVo;
import com.g2rain.gateway.model.route.RouteDefinitionVo;
import com.g2rain.gateway.model.route.ServiceRegistryVo;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * BasisServiceClient
 *
 * <p>
 * 该类用于调用 g2rain-basis 业务支撑服务的接口，主要职责是：
 * <ul>
 *     <li>获取并返回封装在 {@link Result}&lt;T&gt; 中的业务支持数据</li>
 *     <li>以异步、非阻塞方式供 Gateway / WebFlux 链路使用</li>
 * </ul>
 * </p>
 *
 * <p>
 * 生产级设计说明：
 * <ul>
 *     <li>依赖全局配置的 {@link WebClient.Builder}，统一连接池、超时、重试策略</li>
 *     <li>通过 Nacos 服务名调用 g2rain-basis，实现客户端负载均衡</li>
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
public class BasisServiceClient {

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
     * BasisServiceClient 构造函数
     *
     * <p>
     * 构造流程说明：
     * <ul>
     *     <li>接收已配置生产级特性的 WebClient.Builder</li>
     *     <li>设置 baseUrl 为 基础支撑服务在 Nacos 注册的服务名 <code>g2rain-basis</code></li>
     *     <li>调用 <code>build()</code> 方法构建 WebClient 实例</li>
     * </ul>
     * </p>
     *
     * @param webClientBuilder 已配置生产级特性的 WebClient.Builder，包含连接池、超时、负载均衡及全局重试
     * @see WebClient.Builder
     */
    public BasisServiceClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
            // 设置 Auth 服务的 baseUrl，支持通过服务名发现
            .baseUrl("http://g2rain-basis")
            .build(); // 构建 WebClient 实例
    }

    public Mono<List<OrganIdName>> organIdNameMap(Set<Long> ids) {
        if (Objects.isNull(ids) || ids.isEmpty()) {
            return Mono.just(Collections.emptyList());
        }

        return webClient.post()                         // 发起 POST 请求
            .uri("/organ/id_name_map")                  // 调用业务支撑服务的 /organ/id_name_map 接口
            .bodyValue(Map.of("ids", ids))              // 请求体参数 key 固定为 ids
            .retrieve()                                 // 获取响应体
            // 将 JSON 响应解析为 Result<List<OrganIdName>>
            .bodyToMono(new ParameterizedTypeReference<Result<List<OrganIdName>>>() {
            })
            .flatMap(result -> {
                // status == 200
                if (result.isSuccess()) {
                    // 返回 TokenJWTPayload
                    return Mono.just(result.getData());
                }

                // status != 200 统一抛出业务异常，带错误信息
                return Mono.error(ExceptionConverter.of(result));
            })
            .onErrorMap(ExceptionConverter::findBusinessExceptionOrDefault);
    }

    public Mono<List<AppIdName>> appIdNameMap(Set<Long> ids) {
        if (Objects.isNull(ids) || ids.isEmpty()) {
            return Mono.just(Collections.emptyList());
        }

        return webClient.post()                         // 发起 POST 请求
            .uri("/application/id_name_map")            // 调用业务支撑服务的 /application/id_name_map 接口
            .bodyValue(Map.of("ids", ids))              // 请求体参数 key 固定为 ids
            .retrieve()                                 // 获取响应体
            // 将 JSON 响应解析为 Result<List<AppIdName>>
            .bodyToMono(new ParameterizedTypeReference<Result<List<AppIdName>>>() {
            })
            .flatMap(result -> {
                // status == 200
                if (result.isSuccess()) {
                    // 返回 TokenJWTPayload
                    return Mono.just(result.getData());
                }

                // status != 200 统一抛出业务异常，带错误信息
                return Mono.error(ExceptionConverter.of(result));
            })
            .onErrorMap(ExceptionConverter::findBusinessExceptionOrDefault);
    }

    /**
     * 网关动态路由定义（与 WebMVC {@code RouteDefinitionClient}/{@code ResourceApiApi} 对齐）。
     */
    public Mono<List<RouteDefinitionVo>> selectRouteDefinitions() {
        return webClient.get()
            .uri("/resource_api/route_definitions")
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Result<List<RouteDefinitionVo>>>() {
            })
            .flatMap(result -> result.isSuccess()
                ? Mono.just(Objects.requireNonNullElse(result.getData(), Collections.<RouteDefinitionVo>emptyList()))
                : Mono.error(ExceptionConverter.of(result)))
            .onErrorMap(ExceptionConverter::findBusinessExceptionOrDefault);
    }

    /**
     * 用户在指定应用下的接口权限列表。
     */
    public Mono<List<BaseAuthorityApiVo>> getApiPermissions(Long userId, Long applicationId) {
        if (Objects.isNull(userId) || Objects.isNull(applicationId)) {
            return Mono.just(Collections.emptyList());
        }

        return webClient.get()
            .uri(uriBuilder -> uriBuilder.path("/authority/apis")
                .queryParam("userId", userId)
                .queryParam("applicationId", applicationId)
                .build())
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Result<List<BaseAuthorityApiVo>>>() {
            })
            .flatMap(result -> result.isSuccess()
                ? Mono.just(Objects.requireNonNullElse(result.getData(), Collections.<BaseAuthorityApiVo>emptyList()))
                : Mono.error(ExceptionConverter.of(result)))
            .onErrorMap(ExceptionConverter::findBusinessExceptionOrDefault);
    }

    /**
     * 账号（Passport）全局可访问的接口 ID 集合。
     */
    public Mono<List<Long>> getPassportApiPermissions() {
        return webClient.get()
            .uri("/authority/passport_api_permissions")
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Result<List<Long>>>() {
            })
            .flatMap(result -> {
                // status == 200
                if (result.isSuccess()) {
                    // 返回 TokenJWTPayload
                    return Mono.just(result.getData());
                }

                // status != 200 统一抛出业务异常，带错误信息
                return Mono.error(ExceptionConverter.of(result));
            })
            .onErrorMap(ExceptionConverter::findBusinessExceptionOrDefault);
    }

    /**
     * 向 basis 解析个人静态访问令牌。
     *
     * <p>调用 {@code GET /login_token/static_access_token_context?apiKey=}。</p>
     * <ul>
     *     <li>{@code data == null} — 令牌不存在，返回 {@link Mono#empty()}，由 {@link com.g2rain.gateway.cache.ApiKeyCache} 视为 invalid。</li>
     *     <li>{@code status == REVOKED} — 吊销，context 为空。</li>
     *     <li>{@code status == ACTIVATED} — 激活，携带 {@link StaticAccessTokenResolve#getContext()}。</li>
     * </ul>
     *
     * @param apiKey Bearer 后的原始凭证（明文，勿记录日志）
     * @return 解析 VO；空白 apiKey 返回 empty
     */
    public Mono<StaticAccessTokenResolve> fetchStaticTokenResolve(String apiKey) {
        if (Strings.isBlank(apiKey)) {
            return Mono.empty();
        }

        return webClient.get()
            .uri(uriBuilder -> uriBuilder.path("/login_token/static_access_token_context")
                .queryParam("apiKey", apiKey)
                .build())
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Result<StaticAccessTokenResolve>>() {
            })
            .flatMap(result -> {
                if (!result.isSuccess()) {
                    return Mono.error(ExceptionConverter.of(result));
                }
                return Mono.justOrEmpty(result.getData());
            })
            .onErrorMap(ExceptionConverter::findBusinessExceptionOrDefault);
    }

    public Flux<ServiceRegistryVo> getServiceRegistry() {
        return webClient.get()                          // 发起 GET 请求
            .uri("/service_registry/list")              // 调用业务支撑服务的 /application/id_name_map 接口
            .retrieve()                                 // 获取响应体
            // 将 JSON 响应解析为 Result<List<ServiceRegistryVo>>
            .bodyToMono(new ParameterizedTypeReference<Result<List<ServiceRegistryVo>>>() {
            })
            .flatMapMany(result -> {
                // status == 200
                if (result.isSuccess()) {
                    // 返回 TokenJWTPayload
                    return Flux.fromIterable(result.getData());
                } else { // status != 200
                    // 统一抛出业务异常，带错误信息
                    return Flux.error(ExceptionConverter.of(result));
                }
            })
            .onErrorMap(ExceptionConverter::findBusinessExceptionOrDefault);
    }
}
