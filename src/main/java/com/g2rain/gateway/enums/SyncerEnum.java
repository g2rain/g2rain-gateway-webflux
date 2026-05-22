package com.g2rain.gateway.enums;

/**
 * 与 g2rain-basis 侧 {@code BasisSyncerEnum} 名称保持一致，用于 cache-sync 通道标识，
 * 避免网关依赖 basis-api 模块。
 */
public enum SyncerEnum {
    ORGAN_NAME,
    APP_NAME,
    ORGAN_HIERARCHY,
    INTERNAL_ROUTE,
    API_ROUTE,
    PASSPORT_PERM,
    USER_PERM,
    /**
     * 个人静态访问令牌缓存失效：载荷为 tokenHash（SHA-256 hex），见 {@link com.g2rain.gateway.cache.ApiKeyCache}。
     */
    STATIC_ACCESS_TOKEN
}
