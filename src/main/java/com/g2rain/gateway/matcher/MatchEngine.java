package com.g2rain.gateway.matcher;


import com.g2rain.common.utils.Collections;
import com.g2rain.common.utils.Strings;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/**
 * 通用匹配引擎
 *
 * <p>该引擎协调三类能力：</p>
 * <ul>
 *     <li>持有当前生效的 {@link RuleTable} 快照</li>
 *     <li>按固定顺序执行 exact、bucket、global 匹配</li>
 *     <li>基于局部版本戳维护请求级缓存的一致性</li>
 * </ul>
 *
 * @param <T> 规则命中后返回的业务目标类型
 * @author alpha
 * @since 2026/4/16
 */
public class MatchEngine<T> {
    /**
     * 默认请求匹配缓存容量
     */
    private static final long DEFAULT_CACHE_SIZE = 10_000L;

    /**
     * 请求级匹配缓存
     *
     * <p>key 使用请求方法与标准化路径，value 记录命中的规则以及缓存建立时依赖的版本快照</p>
     */
    private final Cache<RequestKey, CacheEntry<T>> cache;

    /**
     * 当前运行期状态
     *
     * <p>状态包含规则快照与对应版本表，更新时整体替换，读取时无锁访问</p>
     */
    private final AtomicReference<State<T>> stateRef = new AtomicReference<>(new State<>(
        RuleTable.empty(), VersionRegistry.empty()
    ));

    /**
     * 使用默认缓存容量创建匹配引擎
     */
    public MatchEngine() {
        this(DEFAULT_CACHE_SIZE);
    }

    /**
     * 使用指定缓存容量创建匹配引擎
     *
     * @param maxCacheSize 最大缓存条数，非法值会回退到默认容量
     */
    public MatchEngine(long maxCacheSize) {
        this.cache = Caffeine.newBuilder()
            .maximumSize(maxCacheSize > 0 ? maxCacheSize : DEFAULT_CACHE_SIZE)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();
    }

    /**
     * 匹配请求并直接返回命中的运行期规则。
     *
     * <p>与 {@link #matchAndVisit} 共用同一套 exact / bucket / global 与缓存逻辑，访问器为 {@code Mono::just}。</p>
     */
    public Mono<MatchRule<T>> matchRule(HttpMethod method, String path) {
        return matchAndVisit(method, path, Mono::just);
    }

    /**
     * 匹配请求并访问命中规则（响应式）。
     *
     * <p>{@link CandidateVisitor} 返回 {@link Mono}：空表示不接受当前候选，引擎继续下一条；非空则取该结果并结束。</p>
     */
    public <R> Mono<R> matchAndVisit(HttpMethod method, String path, CandidateVisitor<T, R> visitor) {
        if (Objects.isNull(method) || Strings.isBlank(path)) {
            return Mono.empty();
        }

        // 缓存键：方法 + 标准化路径 + 方法位掩码（与 RuleTable 分桶一致）
        RequestKey key = new RequestKey(method, MatcherUtils.normalize(path), RuleTable.getMask(method));
        State<T> state = stateRef.get();
        String normalizedPath = key.path();

        // 请求级缓存命中：除 key 相等外，还须校验建立缓存时依赖的 exact / bucket / global 版本是否仍一致
        CacheEntry<T> cached = cache.getIfPresent(key);
        if (Objects.nonNull(cached) && cached.snapshot().matches(state.versions(), key.mask(), normalizedPath)) {
            return cached.rule().map(visitor::visit).orElse(Mono.empty());
        }

        // specIndex：当前 HTTP 方法对应的规则索引；anyIndex：匹配「任意方法」规则的索引
        RuleTable.PathIndex<T> specIndex = state.table().methodBuckets().getOrDefault(key.mask(), RuleTable.PathIndex.empty());
        RuleTable.PathIndex<T> anyIndex = state.table().anyMethod();
        // 将调用方 visitor 包一层：命中时同时带上 MatchRule，便于写缓存与下游 map 出 R
        CandidateVisitor<T, MatchResult<T, R>> matchDecider = rule -> visitor.visit(rule).map(v -> new MatchResult<>(rule, v));

        // 1) EXACT：完整路径精确匹配；仅在发出命中值时 doOnNext 写缓存（与 webmvc 同步语义）
        Mono<MatchResult<T, R>> exactMono = tryVisitMono(specIndex.exact().get(normalizedPath), matchDecider)
            .switchIfEmpty(Mono.defer(() -> tryVisitMono(anyIndex.exact().get(normalizedPath), matchDecider)))
            .doOnNext(mr -> cache.put(key, new CacheEntry<>(Optional.of(mr.rule()), VersionSnapshot.capture(
                state.versions(), MatchStage.EXACT, key.mask(), normalizedPath, null
            ))));

        // 2) BUCKET：真实路径前两段作为 key；defer 保证仅在前序阶段为空时才订阅、才访问桶表
        String requestBucketKey = MatcherUtils.requestBucketKey(normalizedPath);
        Mono<MatchResult<T, R>> bucketMono = Mono.defer(() -> tryVisitMono(specIndex.buckets().get(requestBucketKey), matchDecider)
            .switchIfEmpty(Mono.defer(() -> tryVisitMono(anyIndex.buckets().get(requestBucketKey), matchDecider)))
            .doOnNext(mr -> cache.put(key, new CacheEntry<>(Optional.of(mr.rule()), VersionSnapshot.capture(
                state.versions(), MatchStage.BUCKET, key.mask(), normalizedPath, requestBucketKey
            ))))
        );

        // 3) GLOBAL：无前缀兜底规则（先当前方法列表，再 ANY 列表）
        Mono<MatchResult<T, R>> globalMono = tryVisitMono(specIndex.global(), matchDecider)
            .switchIfEmpty(Mono.defer(() -> tryVisitMono(anyIndex.global(), matchDecider)))
            .doOnNext(mr -> cache.put(key, new CacheEntry<>(Optional.of(mr.rule()), VersionSnapshot.capture(
                state.versions(), MatchStage.GLOBAL, key.mask(), normalizedPath, requestBucketKey
            ))));

        // 按 EXACT → BUCKET → GLOBAL 顺序尝试；全空则写入 MISS 缓存后返回 empty
        return exactMono.switchIfEmpty(bucketMono).switchIfEmpty(globalMono).switchIfEmpty(
                Mono.fromRunnable(() -> cache.put(key, new CacheEntry<>(Optional.empty(), VersionSnapshot.capture(
                    state.versions(), MatchStage.MISS, key.mask(), normalizedPath, requestBucketKey
                )))).then(Mono.empty())
            )
            .map(MatchResult::value);
    }

    private <R> Mono<MatchResult<T, R>> tryVisitMono(MatchRule<T>[] rules, CandidateVisitor<T, MatchResult<T, R>> visitor) {
        if (Collections.isEmpty(rules)) {
            return Mono.empty();
        }

        return Flux.fromArray(rules).concatMap(visitor::visit).next();
    }

    /**
     * 全量替换规则表
     *
     * <p>该操作通常用于启动初始化或全量刷新，会同时重置版本表并清空缓存</p>
     *
     * @param newTable 新规则表
     */
    public void replace(RuleTable<T> newTable) {
        stateRef.set(new State<>(Objects.nonNull(newTable) ? newTable : RuleTable.empty(), VersionRegistry.empty()));
        cache.invalidateAll();
    }

    /**
     * 增量更新规则表
     *
     * <p>该操作对规则表执行原子更新，并仅递增受影响 scope 的版本号，
     * 从而让不相关缓存项继续复用</p>
     *
     * @param updater      规则表更新函数
     * @param versionBumps 需要递增版本的 scope 集合
     */
    public void update(UnaryOperator<RuleTable<T>> updater, Collection<ScopeVersion> versionBumps) {
        stateRef.updateAndGet(current -> {
            RuleTable<T> updated = updater.apply(current.table());
            VersionRegistry versions = current.versions().bump(versionBumps);
            return new State<>(Objects.nonNull(updated) ? updated : RuleTable.empty(), versions);
        });
    }

    /**
     * 返回当前缓存估算大小
     *
     * <p>该方法当前主要用于测试与调试</p>
     *
     * @return 缓存估算大小
     */
    long cacheSize() {
        return cache.estimatedSize();
    }

    /**
     * 候选规则访问器（响应式）
     *
     * @param <T> 规则业务目标类型
     * @param <R> 访问结果类型
     */
    @FunctionalInterface
    public interface CandidateVisitor<T, R> {
        /**
         * 访问单条候选规则
         *
         * @param rule 候选规则
         * @return 接受时返回含结果的 {@link Mono}；不接受时返回 {@link Mono#empty()}
         */
        Mono<R> visit(MatchRule<T> rule);
    }

    /**
     * 单个局部 scope 的版本标识
     *
     * @param type        scope 类型
     * @param scopeMethod 所属方法位，或 {@link RuleTable#ALL_METHOD_MASK}
     * @param key         路径或 bucket key；global scope 为空
     */
    public record ScopeVersion(ScopeType type, int scopeMethod, String key) {
        /**
         * 创建 exact scope
         *
         * @param scopeMethod 方法位
         * @param path        精确路径
         * @return exact scope
         */
        public static ScopeVersion exact(int scopeMethod, String path) {
            return new ScopeVersion(ScopeType.EXACT, scopeMethod, path);
        }

        /**
         * 创建 bucket scope
         *
         * @param scopeMethod 方法位
         * @param bucketKey   分桶 key
         * @return bucket scope
         */
        public static ScopeVersion bucket(int scopeMethod, String bucketKey) {
            return new ScopeVersion(ScopeType.BUCKET, scopeMethod, bucketKey);
        }

        /**
         * 创建 global scope
         *
         * @param scopeMethod 方法位
         * @return global scope
         */
        public static ScopeVersion global(int scopeMethod) {
            return new ScopeVersion(ScopeType.GLOBAL, scopeMethod, null);
        }
    }

    /**
     * 局部版本范围类型
     */
    public enum ScopeType {
        /**
         * 精确路径范围
         */
        EXACT,
        /**
         * 动态路径桶范围
         */
        BUCKET,
        /**
         * 全局兜底范围
         */
        GLOBAL
    }

    /**
     * 请求缓存键
     *
     * @param method 请求方法
     * @param path   标准化后的请求路径
     * @param mask   请求方法位掩码
     */
    private record RequestKey(HttpMethod method, String path, int mask) {

    }

    /**
     * 候选匹配成功后的中间结果
     *
     * @param rule  命中规则
     * @param value 访问结果
     * @param <T>   业务目标类型
     * @param <R>   访问结果类型
     */
    private record MatchResult<T, R>(MatchRule<T> rule, R value) {

    }

    /**
     * 缓存项
     *
     * @param rule     缓存的命中规则，空表示缓存的是 miss
     * @param snapshot 缓存建立时的版本快照
     * @param <T>      业务目标类型
     */
    private record CacheEntry<T>(Optional<MatchRule<T>> rule, VersionSnapshot snapshot) {

    }

    /**
     * 运行期状态
     *
     * @param table    当前规则快照
     * @param versions 当前版本表
     * @param <T>      业务目标类型
     */
    private record State<T>(RuleTable<T> table, VersionRegistry versions) {

    }

    /**
     * 缓存命中的阶段类型
     */
    private enum MatchStage {
        /**
         * 命中精确路径阶段
         */
        EXACT,
        /**
         * 命中分桶路径阶段
         */
        BUCKET,
        /**
         * 命中全局兜底阶段
         */
        GLOBAL,
        /**
         * 本次请求未命中任何规则
         */
        MISS
    }

    /**
     * 单个 bucket scope 在快照建立时刻的版本读数，用于缓存失效判断。
     */
    private record BucketVersionProbe(String key, long spec, long any) {

        private static BucketVersionProbe capture(VersionRegistry registry, int methodMask, String key) {
            return new BucketVersionProbe(
                key,
                registry.versionOf(ScopeVersion.bucket(methodMask, key)),
                registry.versionOf(ScopeVersion.bucket(RuleTable.ALL_METHOD_MASK, key))
            );
        }

        private boolean stillValid(VersionRegistry registry, int methodMask) {
            return spec == registry.versionOf(ScopeVersion.bucket(methodMask, key))
                && any == registry.versionOf(ScopeVersion.bucket(RuleTable.ALL_METHOD_MASK, key));
        }
    }

    /**
     * 缓存项依赖的版本快照
     *
     * @param stage       缓存建立时的命中阶段
     * @param exactSpec   当前方法 exact scope 版本
     * @param exactAny    全方法 exact scope 版本
     * @param bucketProbe 本次匹配依赖的 {@link MatcherUtils#requestBucketKey(String)} 对应 bucket scope 版本读数；EXACT 为 {@code null}
     * @param globalSpec  当前方法 global scope 版本
     * @param globalAny   全方法 global scope 版本
     */
    private record VersionSnapshot(MatchStage stage, long exactSpec, long exactAny, BucketVersionProbe bucketProbe, long globalSpec, long globalAny) {

        /**
         * 基于当前版本表构建缓存依赖快照。
         *
         * @param requestBucketKey 建立缓存时使用的真实请求分桶 key（{@link MatcherUtils#requestBucketKey(String)}）；
         *                         EXACT 阶段传 {@code null} 表示不依赖 bucket scope。
         */
        private static VersionSnapshot capture(VersionRegistry registry, MatchStage stage, int methodMask, String path, String requestBucketKey) {
            return new VersionSnapshot(
                stage,
                registry.versionOf(ScopeVersion.exact(methodMask, path)),
                registry.versionOf(ScopeVersion.exact(RuleTable.ALL_METHOD_MASK, path)),
                Strings.isBlank(requestBucketKey) ? null : BucketVersionProbe.capture(registry, methodMask, requestBucketKey),
                registry.versionOf(ScopeVersion.global(methodMask)),
                registry.versionOf(ScopeVersion.global(RuleTable.ALL_METHOD_MASK))
            );
        }

        /**
         * 判断快照是否仍然与当前版本表一致。
         */
        private boolean matches(VersionRegistry registry, int methodMask, String path) {
            if (exactSpec != registry.versionOf(ScopeVersion.exact(methodMask, path))
                || exactAny != registry.versionOf(ScopeVersion.exact(RuleTable.ALL_METHOD_MASK, path))) {
                return false;
            }

            if (stage == MatchStage.EXACT) {
                return true;
            }

            if (Objects.nonNull(bucketProbe) && !bucketProbe.stillValid(registry, methodMask)) {
                return false;
            }

            if (stage == MatchStage.BUCKET) {
                return true;
            }

            return globalSpec == registry.versionOf(ScopeVersion.global(methodMask))
                && globalAny == registry.versionOf(ScopeVersion.global(RuleTable.ALL_METHOD_MASK));
        }
    }

    /**
     * 局部版本表
     *
     * @param versions scope 到版本号的映射表
     */
    private record VersionRegistry(Map<ScopeVersion, Long> versions) {
        /**
         * 共享空版本表
         */
        private static final VersionRegistry EMPTY = new VersionRegistry(Map.of());

        /**
         * 返回共享空版本表
         *
         * @return 空版本表
         */
        private static VersionRegistry empty() {
            return EMPTY;
        }

        /**
         * 查询指定 scope 的版本号
         *
         * @param scope 目标 scope
         * @return 版本号；不存在时返回 0
         */
        private long versionOf(ScopeVersion scope) {
            return versions.getOrDefault(scope, 0L);
        }

        /**
         * 对一组 scope 执行版本递增
         *
         * @param scopes 需要失效的 scope 集合
         * @return 递增后的新版本表
         */
        private VersionRegistry bump(Collection<ScopeVersion> scopes) {
            if (Collections.isEmpty(scopes)) {
                return this;
            }

            Map<ScopeVersion, Long> next = new HashMap<>(versions);
            for (ScopeVersion scope : scopes) {
                if (Objects.nonNull(scope)) {
                    next.merge(scope, 1L, Long::sum);
                }
            }

            return new VersionRegistry(Map.copyOf(next));
        }
    }
}
