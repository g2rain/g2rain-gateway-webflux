/**
 * g2rain 网关（Spring Cloud Gateway）全局过滤器包。
 *
 * <p>
 * 本包实现边缘侧完整请求处理链：建立请求级 Principal 上下文、缓存可重复读取的 HTTP body、
 * 记录链路日志、完成鉴权（静态 API Key / JWT / DPoP）、校验接口权限与请求摘要完整性，
 * 将身份信息安全转发至下游微服务，并对 JSON 响应做统一字段补全。
 * </p>
 *
 * <h2>设计要点</h2>
 * <ul>
 *     <li>全链路共享 {@link com.g2rain.gateway.model.context.EdgePrincipalContext}，由
 *         {@link com.g2rain.gateway.filters.EdgePrincipalContextScopeFilter} 创建，
 *         经 {@link com.g2rain.gateway.model.context.EdgePrincipalContextHolder} 在 Reactor 上下文中传递。</li>
 *     <li>支持两类 Bearer 凭证：OpenAI 风格静态 API Key（{@code sk-...}）与登录态 JWT；
 *         二者通过 {@link com.g2rain.gateway.utils.AuthScheme} 分流，避免重复校验。</li>
 *     <li>白名单由 {@link com.g2rain.gateway.whitelist.WhiteListResolver} 按过滤器类名配置，命中后跳过对应过滤器逻辑。</li>
 * </ul>
 *
 * <h2>过滤器一览（执行顺序）</h2>
 * <p>顺序值为 {@link org.springframework.core.Ordered#HIGHEST_PRECEDENCE} 加偏移量，数值越小越先执行。</p>
 * <ol>
 *     <li>{@link com.g2rain.gateway.filters.EdgePrincipalContextScopeFilter}（+0）— 创建并绑定
 *         {@link com.g2rain.gateway.model.context.EdgePrincipalContext}，预填 Accept-Language 等。</li>
 *     <li>{@link com.g2rain.gateway.filters.CachedBodyFilter}（+100）— 聚合并缓存请求体/响应体，
 *         供日志、签名校验、响应改写等多次读取 body 的场景使用。</li>
 *     <li>{@link com.g2rain.gateway.filters.TraceLoggingFilter}（+200）— 记录请求路径、方法、查询参数、
 *         请求头与 body；对 JSON 响应记录响应体。</li>
 *     <li>{@link com.g2rain.gateway.filters.ApiKeyFilter}（+290）— 静态 API Key 鉴权，见下文专节。</li>
 *     <li>{@link com.g2rain.gateway.filters.GatewayTokenAuthFilter}（+300）— 校验 {@code Authorization} 中的 JWT，
 *         解析 {@link com.g2rain.common.web.TokenJWTPayload} 并填充上下文。</li>
 *     <li>{@link com.g2rain.gateway.filters.GatewayDPoPAuthFilter}（+400）— 校验 {@code DPoP} 头中的 Proof JWT，
 *         写入签名算法与预期摘要，供 {@link com.g2rain.gateway.filters.SignVerificationFilter} 使用。</li>
 *     <li>{@link com.g2rain.gateway.filters.ApiPermissionFilter}（+500）— 按 Spring Cloud Gateway 路由 ID
 *         校验 Passport / User 是否具备接口访问权限（{@link com.g2rain.gateway.cache.UserPerm}、
 *         {@link com.g2rain.gateway.cache.DefaultPerm}）。</li>
 *     <li>{@link com.g2rain.gateway.filters.SignVerificationFilter}（+600）— 对规范化后的 query 与 body
 *         做摘要比对，验证请求未被篡改。</li>
 *     <li>{@link com.g2rain.gateway.filters.PrincipalForwardFilter}（+700）— 将 Principal 写入下游请求头，
 *         并移除 {@code Authorization}、{@code DPoP} 等敏感头。</li>
 *     <li>{@link com.g2rain.gateway.filters.ResponseAdjustFilter}（+800）— 拦截 JSON 成功响应，
 *         补全 id→name 等展示字段；非业务成功码转换为 {@link com.g2rain.gateway.exception.GatewayException}。</li>
 * </ol>
 *
 * <h2>静态 API Key 鉴权</h2>
 * <p>
 * 面向「个人静态访问令牌」场景：用户在控制台创建 API Key 后，以
 * {@code Authorization: Bearer sk-{applicationAuthorizationIdHex}-{base64url}} 调用开放 API（总长 64）。
 * </p>
 * <ol>
 *     <li>{@link com.g2rain.gateway.filters.ApiKeyFilter} 用 {@link com.g2rain.gateway.utils.AuthScheme#isApiKey(String)}
 *         判断凭证形态；非 API Key 则直接放行，由后续 JWT 过滤器处理。</li>
 *     <li>{@link com.g2rain.gateway.cache.ApiKeyCache} 以 {@link com.g2rain.gateway.utils.DigestUtils#sha256Hex(String)}
 *         哈希后的值作缓存键，回源 basis {@code GET /login_token/static_access_token_context}。</li>
 *     <li>解析成功后将 {@link com.g2rain.gateway.model.auth.StaticTokenContext} 映射进
 *         {@link com.g2rain.gateway.model.context.EdgePrincipalContext}，并设置 {@code staticTokenAuthenticated=true}。</li>
 *     <li>{@link com.g2rain.gateway.filters.GatewayTokenAuthFilter}、
 *         {@link com.g2rain.gateway.filters.GatewayDPoPAuthFilter}、
 *         {@link com.g2rain.gateway.filters.SignVerificationFilter} 发现 {@code staticTokenAuthenticated} 后跳过，
 *         因静态令牌不走 JWT/DPoP/客户端签名体系。</li>
 * </ol>
 * <p>失败时抛出 {@link com.g2rain.gateway.enums.GatewayErrorCode#API_KEY_INVALID} 或
 * {@link com.g2rain.gateway.enums.GatewayErrorCode#API_KEY_REVOKED}。</p>
 *
 * <h2>JWT + DPoP 鉴权</h2>
 * <p>
 * 未走 API Key 分支时，{@link com.g2rain.gateway.filters.GatewayTokenAuthFilter} 校验登录 JWT，
 * {@link com.g2rain.gateway.filters.GatewayDPoPAuthFilter} 校验 DPoP 证明并补充摘要上下文，
 * {@link com.g2rain.gateway.filters.SignVerificationFilter} 再校验 query+body 完整性。
 * </p>
 *
 * <h2>运行约束</h2>
 * <ul>
 *     <li>{@link com.g2rain.gateway.filters.EdgePrincipalContextScopeFilter} 必须最先执行。</li>
 *     <li>任何需要读取 body 的过滤器必须在 {@link com.g2rain.gateway.filters.CachedBodyFilter} 之后。</li>
 *     <li>{@link com.g2rain.gateway.filters.SignVerificationFilter} 依赖鉴权阶段写入的算法与预期摘要。</li>
 *     <li>{@link com.g2rain.gateway.filters.ResponseAdjustFilter} 依赖 {@link com.g2rain.gateway.filters.CachedBodyFilter}
 *         已缓存的响应体。</li>
 * </ul>
 *
 * @author alpha
 * @since 2025/10/6
 */
package com.g2rain.gateway.filters;
