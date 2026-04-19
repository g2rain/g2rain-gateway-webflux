/**
 * Spring Cloud Gateway 核心过滤器包。
 *
 * <p>
 * 本包包含网关中用于请求处理的核心过滤器，实现了请求体缓存、鉴权、Principal 转发、
 * 请求/响应日志记录、签名验证以及响应调整等功能，形成完整的网关处理链。
 * </p>
 *
 * <h2>核心过滤器</h2>
 * <ul>
 *     <li><b>CachedBodyFilter</b> — 全局请求体与响应体缓存，确保请求体可重复读取；优先级 {@code 100}。</li>
 *     <li><b>TraceLoggingFilter</b> — 请求与响应日志记录，记录请求路径、方法、查询参数、请求头、请求体以及响应体（JSON）；优先级 {@code 200}。</li>
 *     <li><b>GatewayTokenAuthFilter</b> — Token JWT 鉴权，验证 Authorization Header 中的 Token 并构建 {@link com.g2rain.gateway.model.context.EdgePrincipalContext}；优先级 {@code 300}。</li>
 *     <li><b>GatewayDPoPAuthFilter</b> — DPoP Proof 鉴权，验证客户端携带的 DPoP JWT，构建鉴权上下文；优先级 {@code 400}。</li>
 *     <li><b>ApiPermissionFilter</b> — 请求接口鉴权，用户登陆后需要确认用户的接口权限是否合法；优先级 {@code 500}。</li>
 *     <li><b>SignVerificationFilter</b> — 请求签名/摘要校验，对 query 参数和 body 进行规范化哈希并验证签名，确保请求完整性；优先级 {@code 600}。</li>
 *     <li><b>PrincipalForwardFilter</b> — Principal 信息转发，将 {@link com.g2rain.gateway.model.context.EdgePrincipalContext} 中关键信息注入下游请求头，并移除敏感认证头；优先级 {@code 700}。</li>
 *     <li><b>ResponseAdjustFilter</b> — 响应调整，拦截 JSON 响应，将返回数据字段映射调整为统一结构，处理非业务成功响应抛出异常；优先级 {@code 800}。</li>
 * </ul>
 *
 * <h2>过滤器执行顺序</h2>
 * <ol>
 *
 *     <li>{@link com.g2rain.gateway.filters.CachedBodyFilter} — 请求体缓存，优先级 {@code 100}</li>
 *     <li>{@link com.g2rain.gateway.filters.TraceLoggingFilter} — 请求/响应日志记录，优先级 {@code 200}</li>
 *     <li>{@link com.g2rain.gateway.filters.GatewayTokenAuthFilter} — Token JWT 鉴权，优先级 {@code 300}</li>
 *     <li>{@link com.g2rain.gateway.filters.GatewayDPoPAuthFilter} — DPoP Proof 鉴权，优先级 {@code 400}</li>
 *     <li>{@link com.g2rain.gateway.filters.ApiPermissionFilter} — 接口权限校验，优先级 {@code 500}</li>
 *     <li>{@link com.g2rain.gateway.filters.SignVerificationFilter} — 签名验证，优先级 {@code 600}</li>
 *     <li>{@link com.g2rain.gateway.filters.PrincipalForwardFilter} — Principal 转发，优先级 {@code 700}</li>
 *     <li>{@link com.g2rain.gateway.filters.ResponseAdjustFilter} — 响应调整，优先级 {@code 800}</li>
 * </ol>
 *
 * <h2>注意事项</h2>
 * <ul>
 *     <li>{@link com.g2rain.gateway.filters.CachedBodyFilter} 必须在其他需要读取请求体的过滤器之前执行。</li>
 *     <li>Token JWT 鉴权必须在 Principal 转发和签名验证之前完成。</li>
 *     <li>DPoP Proof 鉴权依赖请求携带 DPoP JWT，必须在签名验证之前完成。</li>
 *     <li>PrincipalForwardFilter 会移除敏感认证头，确保下游服务无法直接获取 Token 或 DPoP。</li>
 *     <li>TraceLoggingFilter 会增加 I/O 开销，可根据实际情况调整优先级或日志级别。</li>
 *     <li>SignVerificationFilter 依赖 {@link com.g2rain.gateway.model.context.EdgePrincipalContext} 中的签名信息，必须在鉴权之后执行。</li>
 *     <li>ResponseAdjustFilter 仅处理 JSON 响应，必须配合 {@link com.g2rain.gateway.filters.CachedBodyFilter} 使用以缓存响应体。</li>
 * </ul>
 *
 * @author alpha
 * @since 2025/10/6
 */
package com.g2rain.gateway.filters;
