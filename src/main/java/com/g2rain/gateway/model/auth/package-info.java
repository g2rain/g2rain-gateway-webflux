/**
 * 网关鉴权领域模型（本地类型，不依赖 g2rain-basis-api）。
 *
 * <p>
 * basis 通过 HTTP 返回 {@code StaticAccessTokenResolveVo}；网关反序列化为
 * {@link com.g2rain.gateway.model.auth.StaticAccessTokenResolve}，激活态的会话字段落在
 * {@link com.g2rain.gateway.model.auth.StaticTokenContext}。
 * {@link com.g2rain.gateway.cache.ApiKeyCache} 将解析结果封装为
 * {@link com.g2rain.gateway.model.auth.ApiKeyResolveResult}，供
 * {@link com.g2rain.gateway.filters.ApiKeyFilter} 写入
 * {@link com.g2rain.gateway.model.context.EdgePrincipalContext}。
 * </p>
 *
 * <h2>类型说明</h2>
 * <ul>
 *     <li>{@link com.g2rain.gateway.model.auth.StaticAccessTokenResolve} — basis 回源原始结构（status + context）。</li>
 *     <li>{@link com.g2rain.gateway.model.auth.StaticTokenContext} — 激活态用户/机构/应用维度，映射为边缘 Principal。</li>
 *     <li>{@link com.g2rain.gateway.model.auth.ApiKeyResolveResult} — 缓存层对外的三态结果（无效 / 吊销 / 激活）。</li>
 * </ul>
 *
 * @author alpha
 * @since 2026/5/22
 */
package com.g2rain.gateway.model.auth;
