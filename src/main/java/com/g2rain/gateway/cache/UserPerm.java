package com.g2rain.gateway.cache;


import com.g2rain.common.json.JsonCodecFactory;
import com.g2rain.common.syncer.AbstractMessageStorage;
import com.g2rain.common.utils.Collections;
import com.g2rain.gateway.client.BasisServiceClient;
import com.g2rain.gateway.enums.SyncerEnum;
import com.g2rain.gateway.model.cache.BaseAuthority;
import com.g2rain.gateway.model.route.BaseAuthorityApiVo;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 用户接口权限缓存（WebFlux）：命中走内存；未命中走 Basis 拉全量接口权限并回填，同一加载键并发合并为单次 RPC。
 *
 * @author alpha
 * @since 2026/5/5
 */
@Slf4j
@Service
@AllArgsConstructor
public class UserPerm extends AbstractMessageStorage<Long, Long, Long> {
    private static final String LOG_PREFIX = "[UserPerm]";

    private final BasisServiceClient basisServiceClient;

    /**
     * 用户的接口能力缓存
     * 外层 Key 是机构标识（便于按机构整棵失效）
     * 第二层 Key 是用户标识
     * 第三层 Key 是应用标识（与远端 {@code getApiPermissions(userId, applicationId)} 一致，避免同一用户多应用互相覆盖）
     * 最内层 Key 是接口标识
     */
    private static final Cache<Long, Map<Long, Map<Long, Map<Long, BaseAuthority>>>> USER_API_PERMISSIONS = Caffeine
        .newBuilder()
        .maximumSize(100_000)
        .expireAfterAccess(6, TimeUnit.HOURS)
        .build();

    /**
     * 同一 (机构, 用户, 应用) 在缓存未命中时，合并为单个 Basis RPC 与单个回填 Mono，避免并发风暴。
     */
    private final ConcurrentHashMap<LoadKey, Mono<Map<Long, BaseAuthority>>> inFlightLoads = new ConcurrentHashMap<>();

    @Override
    protected @NonNull String dataSource() {
        return SyncerEnum.USER_PERM.name();
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
        log.info("{} SYNC_CREATE | dataSource={} | key(organId)={} | value={} | action=invalidateOrgan | before={}",
            LOG_PREFIX, dataSource(), key, value, describeOrganCache(key));
        delete(key);
    }

    @Override
    protected void delete(@NonNull Long key) {
        String before = describeOrganCache(key);
        USER_API_PERMISSIONS.invalidate(key);
        log.info("{} SYNC_INVALIDATE | dataSource={} | key(organId)={} | before={} | after={} | overview={}",
            LOG_PREFIX, dataSource(), key, before, describeOrganCache(key), describeCacheOverview());
    }

    @Override
    protected void update(@NonNull Long key, Long value) {
        log.info("{} SYNC_UPDATE | dataSource={} | key(organId)={} | value={} | action=invalidateOrgan | before={}",
            LOG_PREFIX, dataSource(), key, value, describeOrganCache(key));
        delete(key);
    }

    @Override
    protected Long get(@NonNull Long key) {
        return key;
    }

    /**
     * 读取用户对某接口的权限（响应式）。
     *
     * <p>已加载过该用户在该应用下的权限表时，仅从内存取，不发起 RPC。</p>
     * <p>尚未加载时：对 {@code (organId, userId, appId)} 使用进程内 single-flight——多个并发请求共享同一次
     * {@link BasisServiceClient#getApiPermissions} 与一次缓存回填；完成后从 in-flight 表移除，便于后续失效再拉。</p>
     *
     * @param organId 机构 id
     * @param userId  用户 id
     * @param appId   应用 id
     * @param apiId   接口（路由）id
     * @return 有权限记录时发出该项；无记录或参数非法时 {@link Mono#empty()}
     */
    public Mono<BaseAuthority> getApiPermission(Long organId, Long userId, Long appId, Long apiId) {
        if (Objects.isNull(organId) || Objects.isNull(userId) || Objects.isNull(appId) || Objects.isNull(apiId)) {
            log.warn("{} CHECK_REJECT | organId={} userId={} appId={} apiId={} | reason=illegalArgument",
                LOG_PREFIX, organId, userId, appId, apiId);
            return Mono.empty();
        }

        log.info("{} CHECK_START | organId={} userId={} appId={} apiId={} | overview={} | organCache={} | appCache={}",
            LOG_PREFIX, organId, userId, appId, apiId,
            describeCacheOverview(), describeOrganCache(organId), describeAppPerms(organId, userId, appId));

        return Mono.defer(() -> {
            Map<Long, BaseAuthority> snapshot = getCachedSnapshot(organId, userId, appId);
            if (Objects.nonNull(snapshot)) {
                BaseAuthority hit = snapshot.get(apiId);
                log.info("{} CHECK_CACHE_HIT | organId={} userId={} appId={} apiId={} | appCache={} | result={}",
                    LOG_PREFIX, organId, userId, appId, apiId,
                    describeAppPerms(organId, userId, appId), describeAuthority(hit));
                return Mono.justOrEmpty(hit);
            }

            LoadKey loadKey = new LoadKey(organId, userId, appId);
            boolean reused = inFlightLoads.containsKey(loadKey);
            log.info("{} CHECK_CACHE_MISS | organId={} userId={} appId={} apiId={} | inFlightReuse={} | overview={}",
                LOG_PREFIX, organId, userId, appId, apiId, reused, describeCacheOverview());

            Mono<Map<Long, BaseAuthority>> shared = inFlightLoads.computeIfAbsent(loadKey, k -> {
                log.info("{} LOAD_REGISTER | loadKey={} | organCacheBefore={} | overview={}",
                    LOG_PREFIX, k, describeOrganCache(organId), describeCacheOverview());
                return buildSharedLoadMono(organId, userId, appId, k);
            });

            return shared
                .doOnNext(loaded -> log.info("{} CHECK_LOADED | organId={} userId={} appId={} apiId={} | loaded={} | appCacheAfter={} | result={}",
                    LOG_PREFIX, organId, userId, appId, apiId,
                    describeAuthorityMap(loaded), describeAppPerms(organId, userId, appId),
                    describeAuthority(loaded.get(apiId))))
                .doOnError(e -> log.warn("{} CHECK_LOAD_ERROR | organId={} userId={} appId={} apiId={} | overview={}",
                    LOG_PREFIX, organId, userId, appId, apiId, describeCacheOverview(), e))
                .map(m -> m.get(apiId));
        });
    }

    /**
     * 构造「单次 RPC + 单次写缓存」的共享 Mono，并对多订阅者去重上游（{@link Mono#cache()}）。
     */
    private Mono<Map<Long, BaseAuthority>> buildSharedLoadMono(Long organId, Long userId, Long appId, LoadKey loadKey) {
        log.info("{} LOAD_START | loadKey={} | organCacheBefore={}",
            LOG_PREFIX, loadKey, describeOrganCache(organId));

        return basisServiceClient.getApiPermissions(userId, appId)
            .defaultIfEmpty(List.of())
            .doOnNext(rows -> log.info("{} LOAD_BASIS_RESPONSE | loadKey={} | rowCount={} | rows={}",
                LOG_PREFIX, loadKey, rows.size(), describeAuthorityRows(rows)))
            .map(UserPerm::buildAuthorityMap)
            .doOnNext(loaded -> {
                log.info("{} MERGE_BEFORE | loadKey={} | loaded={} | organCache={}",
                    LOG_PREFIX, loadKey, describeAuthorityMap(loaded), describeOrganCache(organId));
                mergeAppPermissions(organId, userId, appId, loaded);
                log.info("{} MERGE_AFTER | loadKey={} | organCache={} | appCache={} | overview={}",
                    LOG_PREFIX, loadKey, describeOrganCache(organId),
                    describeAppPerms(organId, userId, appId), describeCacheOverview());
            })
            .cache()
            .doFinally(signal -> {
                inFlightLoads.remove(loadKey);
                log.info("{} LOAD_FINISH | loadKey={} | signal={} | overview={}",
                    LOG_PREFIX, loadKey, signal, describeCacheOverview());
            });
    }

    private static Map<Long, BaseAuthority> buildAuthorityMap(List<BaseAuthorityApiVo> rows) {
        Map<Long, BaseAuthority> loaded = new ConcurrentHashMap<>();
        if (Collections.isEmpty(rows)) {
            return loaded;
        }

        for (BaseAuthorityApiVo vo : rows) {
            if (Objects.isNull(vo) || Objects.isNull(vo.getId())) {
                continue;
            }

            loaded.put(vo.getId(), toBaseAuthority(vo));
        }

        return loaded;
    }

    /**
     * 与原先同步实现一致：在机构维度上 compute 合并，避免覆盖其他用户数据。
     */
    private static void mergeAppPermissions(Long organId, Long userId, Long appId, Map<Long, BaseAuthority> loaded) {
        USER_API_PERMISSIONS.asMap().compute(organId, (_, o) ->
            Objects.requireNonNullElseGet(o, ConcurrentHashMap::new)
        ).computeIfAbsent(userId, _ -> new ConcurrentHashMap<>()).put(appId, loaded);
    }

    /**
     * @return 已缓存的该用户在该应用下的权限表（可能为空 Map）；{@code null} 表示尚未加载过，需要请求远端。
     */
    @SuppressWarnings("ConstantConditions")
    private static Map<Long, BaseAuthority> getCachedSnapshot(Long organId, Long userId, Long appId) {
        var userPerms = USER_API_PERMISSIONS.getIfPresent(organId);

        if (Objects.isNull(userPerms)) {
            return null;
        }

        var appPerms = userPerms.get(userId);
        if (Objects.isNull(appPerms)) {
            return null;
        }

        return appPerms.get(appId);
    }

    private String describeCacheOverview() {
        return "organCount=" + USER_API_PERMISSIONS.asMap().size()
            + ", inFlight=" + inFlightLoads.keySet();
    }

    private static String describeOrganCache(Long organId) {
        var organ = USER_API_PERMISSIONS.getIfPresent(organId);
        if (Objects.isNull(organ)) {
            return "organId=" + organId + " MISSING";
        }
        return safeJson("organId=" + organId, organ);
    }

    private static String describeAppPerms(Long organId, Long userId, Long appId) {
        Map<Long, BaseAuthority> snapshot = getCachedSnapshot(organId, userId, appId);
        if (Objects.isNull(snapshot)) {
            return "organId=" + organId + " userId=" + userId + " appId=" + appId + " NOT_LOADED";
        }
        return safeJson("organId=" + organId + " userId=" + userId + " appId=" + appId, snapshot);
    }

    private static String describeAuthorityMap(Map<Long, BaseAuthority> map) {
        if (Collections.isEmpty(map)) {
            return "EMPTY";
        }
        return safeJson("size=" + map.size(), map);
    }

    private static String describeAuthorityRows(List<BaseAuthorityApiVo> rows) {
        if (Collections.isEmpty(rows)) {
            return "EMPTY";
        }
        return safeJson("size=" + rows.size(), rows);
    }

    private static String describeAuthority(BaseAuthority authority) {
        if (Objects.isNull(authority)) {
            return "MISSING";
        }
        return "id=" + authority.getId() + ",status=" + authority.getStatus();
    }

    private static String safeJson(String label, Object value) {
        try {
            return label + " " + JsonCodecFactory.instance().obj2str(value);
        } catch (Exception e) {
            return label + " (serialize failed: " + e.getMessage() + ")";
        }
    }

    private static BaseAuthority toBaseAuthority(BaseAuthorityApiVo vo) {
        BaseAuthority result = new BaseAuthority();
        result.setId(vo.getId());
        result.setStatus(vo.getStatus());
        return result;
    }

    private record LoadKey(Long organId, Long userId, Long appId) {

    }
}
