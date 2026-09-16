package com.g2rain.gateway.cache;

import com.g2rain.common.enums.SessionType;
import com.g2rain.common.syncer.AbstractMessageStorage;
import com.g2rain.common.utils.Collections;
import com.g2rain.gateway.client.BasisServiceClient;
import com.g2rain.gateway.enums.SyncerEnum;
import com.g2rain.gateway.model.cache.MemberOrganPermission;
import com.g2rain.gateway.model.route.SessionApiPermissionVo;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * MEMBER 接口权限缓存：按 organId 懒加载，{@code MEMBER_PERM} 按 organ 失效。
 */
@Slf4j
@Service
@AllArgsConstructor
public class MemberPerm extends AbstractMessageStorage<Long, Long, Long> {

    private final BasisServiceClient basisServiceClient;

    private static final Cache<Long, MemberOrganPermission> MEMBER_API_PERMISSIONS = Caffeine
        .newBuilder()
        .maximumSize(50_000)
        .expireAfterAccess(6, TimeUnit.HOURS)
        .build();

    private final ConcurrentHashMap<Long, Mono<MemberOrganPermission>> inFlightLoads = new ConcurrentHashMap<>();

    @Override
    protected @NonNull String dataSource() {
        return SyncerEnum.MEMBER_PERM.name();
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
        MEMBER_API_PERMISSIONS.invalidate(key);
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
     * 判断机构是否具备给定 API 入口权限；未命中缓存时回源 Basis。
     *
     * @return {@code true}/{@code false}；回源失败时 {@link Mono#error(Throwable)}
     */
    public Mono<Boolean> hasApiPermission(Long organId, Long apiId) {
        if (Objects.isNull(organId) || organId <= 0 || Objects.isNull(apiId)) {
            return Mono.just(false);
        }

        return getOrLoad(organId).map(perm -> perm.apiIds().contains(apiId));
    }

    /**
     * 读取或懒加载机构 MEMBER 权限快照。
     */
    public Mono<MemberOrganPermission> getOrLoad(Long organId) {
        if (Objects.isNull(organId) || organId <= 0) {
            return Mono.error(new IllegalArgumentException("organId"));
        }

        return Mono.defer(() -> {
            MemberOrganPermission cached = MEMBER_API_PERMISSIONS.getIfPresent(organId);
            if (Objects.nonNull(cached)) {
                return Mono.just(cached);
            }

            Mono<MemberOrganPermission> shared = inFlightLoads.computeIfAbsent(organId, this::buildSharedLoadMono);
            return shared;
        });
    }

    private Mono<MemberOrganPermission> buildSharedLoadMono(Long organId) {
        return basisServiceClient.getSessionApiPermissions(SessionType.MEMBER.name(), organId)
            .map(vo -> toPermission(organId, vo))
            .doOnNext(perm -> MEMBER_API_PERMISSIONS.put(organId, perm))
            .cache()
            .doFinally(_ -> inFlightLoads.remove(organId));
    }

    private static MemberOrganPermission toPermission(Long organId, SessionApiPermissionVo vo) {
        Set<Long> apiIds = new HashSet<>();
        if (Objects.nonNull(vo) && Collections.isNotEmpty(vo.getApiIds())) {
            for (Long id : vo.getApiIds()) {
                if (Objects.nonNull(id)) {
                    apiIds.add(id);
                }
            }
        }
        long version = Objects.nonNull(vo) && Objects.nonNull(vo.getVersion()) ? vo.getVersion() : 0L;
        return new MemberOrganPermission(organId, version, Set.copyOf(apiIds));
    }
}
