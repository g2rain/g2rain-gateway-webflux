package com.g2rain.gateway.cache;


import com.g2rain.common.json.JsonCodecFactory;
import com.g2rain.common.syncer.AbstractMessageStorage;
import com.g2rain.common.utils.Strings;
import com.g2rain.gateway.client.BasisServiceClient;
import com.g2rain.gateway.enums.SyncerEnum;
import com.g2rain.gateway.model.auth.ApiKeyResolveResult;
import com.g2rain.gateway.model.auth.StaticAccessTokenResolve;
import com.g2rain.gateway.model.auth.StaticTokenContext;
import com.g2rain.gateway.utils.DigestUtils;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 静态 API Key 解析缓存。
 *
 * <p>
 * 职责：在网关边缘缓存 basis 对个人静态访问令牌的解析结果，降低
 * {@code /login_token/static_access_token_context} 的 RPC 压力，并在令牌吊销/删除时通过 cache-sync 失效。
 * </p>
 *
 * <h2>缓存策略</h2>
 * <ul>
 *     <li>缓存键：{@link DigestUtils#sha256Hex(String) SHA-256(原始 apiKey)}，不落库明文 Key。</li>
 *     <li>激活态（{@code ACTIVATED}）：缓存完整 {@link StaticTokenContext}。</li>
 *     <li>吊销态（{@code REVOKED}）：仅缓存 {@link CachedApiKey#revokedMarker()}，不存用户明细。</li>
 *     <li>不存在：不写入缓存，每次回源均为 {@link ApiKeyResolveResult#invalid()}。</li>
 * </ul>
 *
 * <h2>并发</h2>
 * <p>
 * 同一 cacheKey 在 miss 时通过 {@code inFlightLoads} 合并为单次 {@link BasisServiceClient#fetchStaticTokenResolve(String)}，
 * 避免热点 Key 并发击穿。
 * </p>
 *
 * <h2>同步</h2>
 * <p>
 * 继承 {@link AbstractMessageStorage}，数据源为 {@link SyncerEnum#STATIC_ACCESS_TOKEN}；
 * basis 在令牌状态变更或删除时推送 tokenHash，本类收到后 {@code invalidate} 对应条目。
 * </p>
 *
 * @author alpha
 * @since 2026/5/22
 */
@Service
@Slf4j
@AllArgsConstructor
public class ApiKeyCache extends AbstractMessageStorage<String, String, String> {

    private final BasisServiceClient basisServiceClient;

    /**
     * 本地 Caffeine 缓存：key 为 tokenHash（SHA-256 hex），value 为激活主体或吊销标记。
     */
    private static final Cache<String, CachedApiKey> CACHE = Caffeine.newBuilder()
        .maximumSize(50_000).expireAfterAccess(30, TimeUnit.MINUTES).build();

    /**
     * 同一 tokenHash 并发 miss 时共享的加载 Mono。
     */
    private final ConcurrentHashMap<String, Mono<ApiKeyResolveResult>> inFlightLoads = new ConcurrentHashMap<>();

    @Override
    public @NonNull String dataSource() {
        return SyncerEnum.STATIC_ACCESS_TOKEN.name();
    }

    @Override
    public @NonNull Class<String> getValueType() {
        return String.class;
    }

    @Override
    public @NonNull String getKey(@NonNull String value) {
        return value;
    }

    @Override
    public void create(@NonNull String key, String value) {
        delete(key);
    }

    @Override
    public void delete(@NonNull String key) {
        log.info("before cache:{}", JsonCodecFactory.instance().obj2str(CACHE));
        CACHE.invalidate(key);
        log.info("after cache:{}", JsonCodecFactory.instance().obj2str(CACHE));
    }

    @Override
    public void update(@NonNull String key, String value) {
        delete(key);
    }

    @Override
    public String get(@NonNull String key) {
        return key;
    }

    /**
     * 解析原始 API Key。
     *
     * @param apiKey {@code Authorization} 中 Bearer 后的凭证（未经哈希）
     * @return 无效 / 吊销 / 激活三态；空白输入直接为 invalid
     */
    public Mono<ApiKeyResolveResult> resolve(String apiKey) {
        if (Strings.isBlank(apiKey)) {
            return Mono.just(ApiKeyResolveResult.invalid());
        }

        String cacheKey = DigestUtils.sha256Hex(apiKey);
        CachedApiKey hit = CACHE.getIfPresent(cacheKey);
        if (Objects.nonNull(hit)) {
            return Mono.just(hit.isRevoked() ? ApiKeyResolveResult.revoked() : ApiKeyResolveResult.active(hit.context()));
        }

        return inFlightLoads.computeIfAbsent(cacheKey, k ->
            basisServiceClient.fetchStaticTokenResolve(apiKey)
                .map(resolve -> applyLoad(cacheKey, resolve))
                .defaultIfEmpty(ApiKeyResolveResult.invalid())
                .cache()
                .doFinally(_ -> inFlightLoads.remove(k))
        );
    }

    private ApiKeyResolveResult applyLoad(String cacheKey, StaticAccessTokenResolve resolve) {
        if (Objects.isNull(resolve)) {
            return ApiKeyResolveResult.invalid();
        }

        if ("REVOKED".equals(resolve.getStatus())) {
            CACHE.put(cacheKey, CachedApiKey.revokedMarker());
            return ApiKeyResolveResult.revoked();
        }

        if (!"ACTIVATED".equals(resolve.getStatus())
            || Objects.isNull(resolve.getContext())) {
            return ApiKeyResolveResult.invalid();
        }

        StaticTokenContext context = resolve.getContext();
        CACHE.put(cacheKey, CachedApiKey.active(context));
        return ApiKeyResolveResult.active(context);
    }

    /**
     * 缓存条目：吊销标记与激活态上下文二选一。
     *
     * @param isRevoked 是否为吊销占位（此时 {@code context} 为 null）
     * @param context   激活态会话上下文
     */
    private record CachedApiKey(boolean isRevoked, StaticTokenContext context) {

        static CachedApiKey revokedMarker() {
            return new CachedApiKey(true, null);
        }

        static CachedApiKey active(StaticTokenContext context) {
            return new CachedApiKey(false, context);
        }
    }
}
