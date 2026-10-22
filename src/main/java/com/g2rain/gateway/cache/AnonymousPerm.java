package com.g2rain.gateway.cache;

import com.g2rain.common.enums.SessionType;
import com.g2rain.common.syncer.AbstractMessageStorage;
import com.g2rain.common.utils.Collections;
import com.g2rain.gateway.client.BasisServiceClient;
import com.g2rain.gateway.enums.SyncerEnum;
import com.g2rain.gateway.model.cache.AnonymousOrganPermission;
import com.g2rain.gateway.model.route.SessionApiPermissionVo;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * ANONYMOUS 接口权限缓存：按 organId 懒加载"可用接口 + 启用中的接入码"，{@code ANONYMOUS_PERM} 按机构失效。
 */
@Service
@AllArgsConstructor
public class AnonymousPerm extends AbstractMessageStorage<Long, Long, Long> {

    private final BasisServiceClient basisServiceClient;

    private static final Cache<Long, AnonymousOrganPermission> ANONYMOUS_PERMISSIONS = Caffeine
        .newBuilder()
        .maximumSize(50_000)
        .expireAfterAccess(6, TimeUnit.HOURS)
        .build();

    private final ConcurrentHashMap<Long, Mono<AnonymousOrganPermission>> inFlightLoads = new ConcurrentHashMap<>();

    @Override
    protected @NonNull String dataSource() {
        return SyncerEnum.ANONYMOUS_PERM.name();
    }

    @Override
    protected @NonNull Class<Long> getValueType() {
        return Long.class;
    }

    @Override
    protected @NonNull Long getKey(@NonNull Long value) {
        return value;
    }

    @Override
    protected void create(@NonNull Long key, Long value) {
        delete(key);
    }

    @Override
    protected void delete(@NonNull Long key) {
        ANONYMOUS_PERMISSIONS.invalidate(key);
    }

    @Override
    protected void update(@NonNull Long key, Long value) {
        delete(key);
    }

    @Override
    protected Long get(@NonNull Long key) {
        return key;
    }

    /**
     * 接入码仍启用且机构已开通该接口时放行；未命中缓存时回源 Basis。
     *
     * @return {@code true}/{@code false}；回源失败时 {@link Mono#error(Throwable)}
     */
    public Mono<Boolean> hasApiPermission(Long organId, Long entryId, Long apiId) {
        if (Objects.isNull(organId) || organId <= 0 || Objects.isNull(entryId) || Objects.isNull(apiId)) {
            return Mono.just(false);
        }

        return getOrLoad(organId).map(perm -> perm.allows(entryId, apiId));
    }

    /**
     * 读取或懒加载机构 ANONYMOUS 权限快照；同一机构并发回源只发一次请求。
     */
    public Mono<AnonymousOrganPermission> getOrLoad(Long organId) {
        return Mono.defer(() -> {
            AnonymousOrganPermission cached = ANONYMOUS_PERMISSIONS.getIfPresent(organId);
            if (Objects.nonNull(cached)) {
                return Mono.just(cached);
            }
            return inFlightLoads.computeIfAbsent(organId, this::buildSharedLoadMono);
        });
    }

    private Mono<AnonymousOrganPermission> buildSharedLoadMono(Long organId) {
        return basisServiceClient.getSessionApiPermissions(SessionType.ANONYMOUS.name(), organId)
            .map(vo -> toPermission(organId, vo))
            .doOnNext(perm -> ANONYMOUS_PERMISSIONS.put(organId, perm))
            .cache()
            .doFinally(_ -> inFlightLoads.remove(organId));
    }

    private static AnonymousOrganPermission toPermission(Long organId, SessionApiPermissionVo vo) {
        long version = Objects.nonNull(vo) && Objects.nonNull(vo.getVersion()) ? vo.getVersion() : 0L;
        return new AnonymousOrganPermission(
            organId,
            version,
            toSet(Objects.isNull(vo) ? null : vo.getApiIds()),
            toSet(Objects.isNull(vo) ? null : vo.getEntryIds())
        );
    }

    private static Set<Long> toSet(List<Long> ids) {
        if (Collections.isEmpty(ids)) {
            return Set.of();
        }
        return ids.stream().filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
    }
}
