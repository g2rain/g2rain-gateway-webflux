package com.g2rain.gateway.model.cache;

import java.util.Set;

/**
 * 某机构 MEMBER 入口 API 权限快照。
 *
 * @param organId 机构 ID
 * @param version Basis 返回的版本（可为 0）
 * @param apiIds  不可变 API ID 集合；空集表示租户未开通任何 MEMBER 能力
 */
public record MemberOrganPermission(
    long organId,
    long version,
    Set<Long> apiIds
) {
}
