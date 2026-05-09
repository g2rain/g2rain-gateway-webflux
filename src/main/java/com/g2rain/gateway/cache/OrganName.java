package com.g2rain.gateway.cache;


import com.g2rain.common.syncer.AbstractMessageStorage;
import com.g2rain.common.utils.Collections;
import com.g2rain.gateway.client.BasisServiceClient;
import com.g2rain.gateway.enums.SyncerEnum;
import com.g2rain.gateway.model.cache.OrganIdName;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author alpha
 * @since 2026/4/13
 */
@Slf4j
@Service
@AllArgsConstructor
public class OrganName extends AbstractMessageStorage<Long, OrganIdName, String> {

    /**
     * 机构客户端
     */
    private final BasisServiceClient basisServiceClient;

    /**
     * 相同「未命中 id 集合」并发回源时合并为单次 Basis 批量 RPC（键为排序后的 id 列表字符串）。
     */
    private final ConcurrentHashMap<String, Mono<Map<String, String>>> inFlightLoads = new ConcurrentHashMap<>();

    /**
     * 机构名称本地缓存（网关侧）。
     *
     * <p><b>数据来源：</b></p>
     * <ul>
     *   <li>正常路径：下游微服务（basis）发生变更后，通过 cache-sync 推送事件，触发本类的 create/update/delete，从而实时更新缓存</li>
     *   <li>兜底路径：网关在拼装响应时若缓存未命中，会批量回源查询并回填缓存（见 {@link #getNames(Set)})</li>
     * </ul>
     *
     * <p><b>过期策略（expireAfterAccess）：</b></p>
     * <ul>
     *   <li>网关读多写少，且命中率通常很高；按“访问续期”更贴近热数据常驻的使用模式</li>
     *   <li>即便 cache-sync 消息在极端情况下丢失/延迟，缓存也会在一段时间后自然淘汰并触发回源修正</li>
     * </ul>
     *
     * <p><b>负缓存：</b></p>
     * <ul>
     *   <li>对不存在的 id 写入空字符串，降低穿透带来的回源放大</li>
     *   <li>调用方写回响应时会跳过空字符串（见 {@code ResponseAdjustFilter} 对 name 的判空逻辑）</li>
     * </ul>
     *
     * <p><b>作用域：</b>static 单例缓存，全 JVM 共享一份，避免每次注入都创建新缓存实例。</p>
     */
    private static final Cache<Long, String> organCache = Caffeine.newBuilder()
        // 网关侧高频读取，按访问续期更贴近“热数据常驻”的使用模式；漏同步时仍会在较长时间后自然淘汰并触发回源。
        .maximumSize(100_000)
        .expireAfterAccess(6, TimeUnit.HOURS)
        .build();

    @Override
    public @NonNull String dataSource() {
        return SyncerEnum.ORGAN_NAME.name();
    }

    @Override
    public @NonNull Class<OrganIdName> getValueType() {
        return OrganIdName.class;
    }

    @Override
    public @NonNull Long getKey(@NonNull OrganIdName value) {
        return value.getOrganId();
    }

    @Override
    public void create(@NonNull Long key, OrganIdName value) {
        organCache.put(key, value.getOrganName());
    }

    /**
     * 防止缓存穿透
     *
     * @param key 消息键，不能为 {@code null}
     */
    @Override
    public void delete(@NonNull Long key) {
        organCache.put(key, "");
    }

    @Override
    public void update(@NonNull Long key, OrganIdName value) {
        organCache.put(key, value.getOrganName());
    }

    @Override
    public String get(@NonNull Long key) {
        return organCache.getIfPresent(key);
    }

    public Mono<Map<String, String>> getNames(Set<Long> ids) {
        if (Collections.isEmpty(ids)) {
            return Mono.just(Map.of());
        }

        // 1) 先批量命中本地缓存（包含空字符串的“负缓存”）
        Map<String, String> result = new HashMap<>(Math.max(16, ids.size()));
        Set<Long> missIds = new HashSet<>();
        for (Long id : ids) {
            if (Objects.isNull(id)) {
                continue;
            }

            String cached = organCache.getIfPresent(id);
            if (Objects.nonNull(cached)) {
                result.put(String.valueOf(id), cached);
                continue;
            }

            missIds.add(id);
        }

        if (Collections.isEmpty(missIds)) {
            return Mono.just(result);
        }

        Set<Long> frozenMiss = Set.copyOf(missIds);
        String batchKey = missBatchKey(frozenMiss);
        Mono<Map<String, String>> shared = inFlightLoads.computeIfAbsent(batchKey, k -> loadMissBatch(frozenMiss, k));

        return shared.map(namesForMiss -> {
            Map<String, String> merged = new HashMap<>(result);
            merged.putAll(namesForMiss);
            return merged;
        });
    }

    private Mono<Map<String, String>> loadMissBatch(Set<Long> frozenMiss, String batchKey) {
        return basisServiceClient.organIdNameMap(frozenMiss)
            .doOnError(e -> log.warn("批量查询机构名称失败, ids={}", frozenMiss, e))
            .onErrorResume(_ -> Mono.just(List.of()))
            .map(data -> materializeMissBatch(frozenMiss, data))
            .cache()
            .doFinally(_ -> inFlightLoads.remove(batchKey));
    }

    private static Map<String, String> materializeMissBatch(Set<Long> frozenMiss, List<OrganIdName> data) {
        Set<Long> stillMissing = new HashSet<>(frozenMiss);
        Map<String, String> namesForMiss = new HashMap<>(Math.max(16, frozenMiss.size()));
        if (!Collections.isEmpty(data)) {
            for (OrganIdName vo : data) {
                if (Objects.isNull(vo) || Objects.isNull(vo.getOrganId())) {
                    continue;
                }

                Long id = vo.getOrganId();
                String name = Objects.toString(vo.getOrganName(), "");
                organCache.put(id, name);
                namesForMiss.put(String.valueOf(id), name);
                stillMissing.remove(id);
            }
        }

        for (Long id : stillMissing) {
            organCache.put(id, "");
            namesForMiss.put(String.valueOf(id), "");
        }

        return namesForMiss;
    }

    private static String missBatchKey(Set<Long> missIds) {
        return missIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
    }
}
