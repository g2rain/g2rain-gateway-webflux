package com.g2rain.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * MEMBER 入口权限模式。
 *
 * <p>{@code enforce}（默认）：只认按 organ 的 MemberPerm。</p>
 * <p>{@code shadow}：仍按 DefaultPerm 决策，并对比 MemberPerm 打差异日志（仅迁移核对）。</p>
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "gateway.member-permission")
public class MemberPermissionProperties {

    /**
     * {@code shadow} 或 {@code enforce}。
     */
    private String mode = "enforce";

    public boolean isShadow() {
        return "shadow".equalsIgnoreCase(mode);
    }

    public boolean isEnforce() {
        return !isShadow();
    }
}
