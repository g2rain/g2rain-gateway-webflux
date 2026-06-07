package com.g2rain.gateway.model.auth;


import com.g2rain.gateway.enums.ApiKeyResolveOutcome;

/**
 * API Key 缓存层解析结果，供 {@link com.g2rain.gateway.filters.ApiKeyFilter} 分支处理。
 *
 * @param outcome 三态枚举
 * @param context 仅 {@link ApiKeyResolveOutcome#ACTIVE} 时非空
 * @author alpha
 * @since 2026/5/22
 */
public record ApiKeyResolveResult(ApiKeyResolveOutcome outcome, StaticTokenContext context) {

    /** 无效令牌（不存在或不可用的统一表示）。 */
    public static ApiKeyResolveResult invalid() {
        return new ApiKeyResolveResult(ApiKeyResolveOutcome.INVALID, null);
    }

    /** 已吊销令牌。 */
    public static ApiKeyResolveResult revoked() {
        return new ApiKeyResolveResult(ApiKeyResolveOutcome.REVOKED, null);
    }

    /**
     * 激活且上下文完整。
     *
     * @param context 会话上下文，不可为 null
     */
    public static ApiKeyResolveResult active(StaticTokenContext context) {
        return new ApiKeyResolveResult(ApiKeyResolveOutcome.ACTIVE, context);
    }
}
