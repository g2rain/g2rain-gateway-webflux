package com.g2rain.gateway.cache;


import com.g2rain.common.syncer.AbstractMessageStorage;
import com.g2rain.gateway.client.BasisServiceClient;
import com.g2rain.gateway.enums.SyncerEnum;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Passport（账号）维度可访问的接口 id 集合缓存。
 *
 * @author alpha
 * @since 2026/5/5
 */
@Slf4j
@Service
@AllArgsConstructor
public class GlobalPerm extends AbstractMessageStorage<Long, Long, String> {

    private final BasisServiceClient basisServiceClient;

    /**
     * 账号的接口能力缓存（进程内 Set；由 {@link #load()} 与 cache-sync 推送共同维护）。
     */
    private static final Set<Long> PASSPORT_API_PERMISSIONS = new HashSet<>();

    @Override
    public void load() {
        List<Long> permissions = basisServiceClient.getPassportApiPermissions().block();
        if (Objects.isNull(permissions) || permissions.isEmpty()) {
            return;
        }

        PASSPORT_API_PERMISSIONS.addAll(permissions);
    }

    @Override
    protected @NonNull String dataSource() {
        return SyncerEnum.PASSPORT_PERM.name();
    }

    @Override
    protected @NonNull Class<Long> getValueType() {
        return Long.class;
    }

    @Override
    protected @NonNull Long getKey(@NonNull Long value) {
        return 0L;
    }

    @Override
    protected void create(@NonNull Long key, Long value) {
        PASSPORT_API_PERMISSIONS.add(value);
    }

    @Override
    protected void delete(@NonNull Long key) {
        PASSPORT_API_PERMISSIONS.remove(key);
    }

    @Override
    protected void update(@NonNull Long key, Long value) {
        PASSPORT_API_PERMISSIONS.add(value);
    }

    @Override
    protected String get(@NonNull Long key) {
        return "";
    }

    /**
     * 判断给定资源接口 id 是否在 Passport 全局白名单内（响应式，无远程调用，仅读内存 Set）。
     *
     * @param apiId 路由/资源接口 id；{@code null} 时视为无权限
     * @return 包含时 {@code true}，否则 {@code false}
     */
    public Mono<Boolean> hasApiPermission(Long apiId) {
        if (Objects.isNull(apiId)) {
            return Mono.just(false);
        }

        return Mono.fromCallable(() -> PASSPORT_API_PERMISSIONS.contains(apiId));
    }
}
