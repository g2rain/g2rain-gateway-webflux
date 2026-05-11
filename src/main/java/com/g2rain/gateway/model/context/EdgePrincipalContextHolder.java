package com.g2rain.gateway.model.context;


import lombok.NonNull;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.Objects;

/**
 * EdgePrincipalContextHolder 是一个上下文持有者工具类，用于在 Reactor 流程中存储和获取 {@link EdgePrincipalContext}。
 * <p>
 * 它利用 Reactor 的 {@link reactor.util.context.Context} 机制，使 Edge 网关请求的上下文信息能够在异步流程中安全传递。
 * </p>
 *
 * <h2>主要功能</h2>
 * <ul>
 *     <li>延迟获取当前请求的 {@link EdgePrincipalContext}，直到 Reactor 流程订阅时才读取（{@link Mono#deferContextual}）。</li>
 *     <li>在 Context 中存储 {@link EdgePrincipalContext}，保证上下文信息可跨异步链路传递。</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * EdgePrincipalContextHolder.get()
 *     .flatMap(ctx -> {
 *         // 使用 ctx 信息处理业务逻辑
 *         return Mono.just(ctx.getLocale());
 *     })
 *     .contextWrite(context -> EdgePrincipalContextHolder.put(context, new EdgePrincipalContext()));
 * }</pre>
 *
 * @author alpha
 * @since 2025/10/6
 */
public final class EdgePrincipalContextHolder {
    /**
     * Reactor Context 中存储 EdgePrincipalContext 的键
     */
    private static final String EDGE_PRINCIPAL_CONTEXT = EdgePrincipalContextHolder.class.getName();

    private EdgePrincipalContextHolder() {
        // 私有构造，防止实例化
    }

    /**
     * 获取当前 Reactor 流程中的 EdgePrincipalContext。
     * 如果 Context 中不存在，则返回一个新的空上下文作为兜底。
     *
     * @return 包含 EdgePrincipalContext 的 Mono
     */
    public static Mono<@NonNull EdgePrincipalContext> get() {
        /*
         * 主要用于延迟(defer)地从Context中提取值并生成一个新的Publisher
         * defer表示"推迟"——直到订阅(subscribe)时才执行
         * contextual表示"带上下文的"——它允许你访问Reactor的Context
         * 需要访问上下文变量(如用户信息、请求 ID、租户 ID)时使用。
         * 延迟访问Context, 而不是在管道建立时就读取
         */
        return Mono.deferContextual(contextView -> {
            EdgePrincipalContext context = contextView.getOrDefault(
                EDGE_PRINCIPAL_CONTEXT, null
            );

            if (Objects.nonNull(context)) {
                return Mono.just(context);
            }

            return Mono.just(EdgePrincipalContext.of());
        });
    }

    /**
     * 获取当前 Reactor 流程中的 EdgePrincipalContext。
     * 如果 Context 中不存在，则返回错误，用于需要强约束上下文存在的链路。
     *
     * @return 包含 EdgePrincipalContext 的 Mono
     */
    public static Mono<@NonNull EdgePrincipalContext> require() {
        return Mono.deferContextual(contextView -> {
            EdgePrincipalContext context = contextView.getOrDefault(
                EDGE_PRINCIPAL_CONTEXT, null
            );

            if (Objects.nonNull(context)) {
                return Mono.just(context);
            }

            return Mono.error(new IllegalStateException("EdgePrincipalContext not found in Reactor Context"));
        });
    }

    /**
     * 将 EdgePrincipalContext 放入给定的 Reactor Context 中。
     *
     * @param ctx     Reactor Context
     * @param context 要存储的 EdgePrincipalContext
     * @return 新的 Context
     */
    public static Context put(Context ctx, EdgePrincipalContext context) {
        return ctx.put(EDGE_PRINCIPAL_CONTEXT, context);
    }
}
