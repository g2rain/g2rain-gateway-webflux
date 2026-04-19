package com.g2rain.gateway.config;


import com.g2rain.common.exception.DefaultExceptionProcessor;
import com.g2rain.common.exception.ErrorMessageRegistry;
import com.g2rain.common.exception.ExceptionProcessor;
import com.g2rain.common.syncer.MessageStorageRegistry;
import com.g2rain.gateway.client.InfraServiceClient;
import com.g2rain.gateway.exception.ErrorMessageStorage;
import com.g2rain.gateway.exception.GlobalErrorHandler;
import io.netty.channel.ConnectTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.PrematureCloseException;
import reactor.netty.resources.ConnectionProvider;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * 网关配置类，用于在微服务模块中注册全局异常处理相关的 Spring Bean。
 * <p>
 * 该配置主要注册以下 Bean：
 * <ul>
 *     <li>{@link ErrorMessageRegistry}：提供错误码到本地化消息的映射</li>
 *     <li>{@link ExceptionProcessor}：将 {@link com.g2rain.common.exception.BusinessException} 转换为统一 {@link com.g2rain.common.model.Result} 响应</li>
 *     <li>{@link GlobalErrorHandler}：全局异常处理器，拦截 WebFlux 异常并返回 JSON 响应</li>
 * </ul>
 * </p>
 * <p>
 * 所有 Bean 的依赖关系在此配置类中统一管理，保证 Spring 生命周期和依赖注入顺畅。
 * </p>
 *
 * @author alpha
 * @since 2025/10/15
 */
@Slf4j
@Configuration
public class GatewayConfig implements SmartInitializingSingleton {
    /**
     * 当 Spring 容器中所有单例 Bean 初始化完成后触发。
     *
     * <p>遍历 {@link com.g2rain.common.syncer.MessageStorageRegistry#getMessageStorages()}，
     * 对每个 {@link com.g2rain.common.syncer.AbstractMessageStorage} 执行 {@link com.g2rain.common.syncer.AbstractMessageStorage#load()} 方法，
     * 完成初始缓存加载。</p>
     *
     * <p>异常处理：若单个存储加载失败，仅记录日志，不抛出异常。</p>
     */
    @Override
    public void afterSingletonsInstantiated() {
        MessageStorageRegistry.getMessageStorages().forEach(storage -> {
            try {
                storage.load();
            } catch (Exception e) {
                log.warn("加载缓存数据失败", e);
            }
        });
    }

    /**
     * 创建一个支持负载均衡的 WebClient.Builder。
     * <p>
     * 该 Builder 使用 @LoadBalanced 注解，可以通过 Nacos 服务名调用微服务，
     * 并且结合自定义的 HttpClient 支持连接池、超时和超时异常重试策略。
     *
     * @return 配置好的 WebClient.Builder 实例，可直接注入使用
     */
    @Bean
    @LoadBalanced // 支持通过 Nacos 服务名调用，不依赖固定 IP
    public WebClient.Builder webClientBuilder() {
        // -----------------------------
        // 1️⃣ 连接池配置
        // -----------------------------
        ConnectionProvider connectionProvider = ConnectionProvider.builder("gateway-conn-pool")
            .maxConnections(200)                                                    // 最大连接数，防止高并发时耗尽连接
            .pendingAcquireTimeout(Duration.ofSeconds(10))                          // 获取连接超时时间，超过则报错
            .maxIdleTime(Duration.ofSeconds(30))                                    // 空闲连接最大存活时间，避免长时间占用
            .maxLifeTime(Duration.ofMinutes(5))                                     // 连接最大存活时间，到期自动释放
            .build();

        // -----------------------------
        // 2️⃣ HttpClient 配置
        // -----------------------------
        HttpClient httpClient = HttpClient.create(connectionProvider)
            .responseTimeout(Duration.ofSeconds(5))                                 // 响应超时，超过 5 秒认为失败
            .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000);   // 连接超时 2 秒

        // -----------------------------
        // 3️⃣ WebClient.Builder 配置
        // -----------------------------
        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))            // 使用自定义 HttpClient
            // 全局 filter 配置重试策略，只针对超时异常
            .filter((request, next) -> next.exchange(request)                       // 只重试超时相关异常
                .retryWhen(Retry.backoff(2, Duration.ofMillis(500))                 // 最多重试 2 次，每次间隔 500ms
                    .filter(throwable -> throwable instanceof TimeoutException      // 响应超时
                        || throwable instanceof ConnectTimeoutException             // 连接超时
                        || throwable instanceof PrematureCloseException             // 连接提前关闭
                    )
                )
            );
    }

    /**
     * 注册默认的 {@link ErrorMessageRegistry} Bean。
     * <p>
     * 使用微服务模块提供的 {@link ErrorMessageStorage} 实现，负责提供错误码与本地化消息映射。
     * </p>
     *
     * @return 默认的 {@link ErrorMessageRegistry} 实例
     */
    @Bean
    public ErrorMessageRegistry errorMessageRegistry(InfraServiceClient infraServiceClient) {
        return new ErrorMessageStorage(infraServiceClient);
    }

    /**
     * 注册默认的 {@link ExceptionProcessor} Bean。
     * <p>
     * 使用传入的 {@link ErrorMessageRegistry} 构造 {@link DefaultExceptionProcessor}，
     * 将 {@link com.g2rain.common.exception.BusinessException} 转换为统一 {@link com.g2rain.common.model.Result} 响应，
     * 并根据错误码和本地化信息解析错误消息。
     * </p>
     *
     * @param registry 注入的 {@link ErrorMessageRegistry} Bean
     * @return 默认的 {@link ExceptionProcessor} 实例
     */
    @Bean
    public ExceptionProcessor defaultExceptionProcessor(ErrorMessageRegistry registry) {
        return new DefaultExceptionProcessor(registry);
    }

    /**
     * 注册全局异常处理器 {@link GlobalErrorHandler} Bean。
     * <p>
     * 该 Bean 拦截所有 WebFlux 异常，通过注入的 {@link ExceptionProcessor} 将异常转换为标准 JSON 响应。
     * </p>
     *
     * @param processor 注入的 {@link ExceptionProcessor} Bean
     * @return 全局异常处理器 {@link GlobalErrorHandler} 实例
     */
    @Bean
    public GlobalErrorHandler globalErrorHandler(ExceptionProcessor processor) {
        return new GlobalErrorHandler(processor);
    }
}
