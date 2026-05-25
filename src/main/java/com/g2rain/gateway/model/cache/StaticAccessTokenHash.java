package com.g2rain.gateway.model.cache;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 静态访问令牌 cache-sync 载荷，字段与 basis {@code StaticAccessTokenHashVo} 保持一致。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StaticAccessTokenHash {

    /**
     * 令牌哈希（SHA-256 hex），用作 {@link com.g2rain.gateway.cache.ApiKeyCache} 失效键
     */
    private String tokenHash;
}
