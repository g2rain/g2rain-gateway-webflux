package com.g2rain.gateway.exception;


import com.g2rain.common.exception.ErrorMessageRegistry;
import com.g2rain.common.exception.LocalizedErrorMessage;
import com.g2rain.common.json.JsonCodecFactory;
import com.g2rain.common.utils.Collections;
import com.g2rain.common.utils.Strings;
import com.g2rain.gateway.client.InfraServiceClient;
import com.g2rain.gateway.model.cache.I18nMessageVo;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>{@code ErrorMessageStorage} 是 {@link ErrorMessageRegistry} 的默认实现类，
 * 用于管理和缓存系统的本地化错误消息。</p>
 * <p>
 * 该类使用内存缓存 {@link #MESSAGE_CACHE} 存储错误码与对应本地化消息的映射，支持按完整 locale 或语言前缀进行查找。
 * </p>
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * ErrorMessageStorage registry = new ErrorMessageStorage();
 * registry.load();
 * String msg = registry.getMessage("ERR001", "zh_CN");
 * System.out.println(msg);
 * }</pre>
 *
 * @author alpha
 * @since 2025/10/6
 */
@RequiredArgsConstructor
@Slf4j
public class ErrorMessageStorage extends ErrorMessageRegistry {

    private final InfraServiceClient infraServiceClient;

    /**
     * 错误码与本地化消息的缓存映射
     * 外层 `key` : 错误编码
     * 内层 `key` : 区域语言编码
     * 内层 `val` : 错误信息
     */
    private static final Map<String, Map<String, String>> MESSAGE_CACHE = new ConcurrentHashMap<>();

    /**
     * 加载所有错误消息，默认实现为空（NOP）。
     */
    @Override
    public void load() {
        // load() 由启动期同步回调触发，这里阻塞等待一次远程加载结果，失败则降级为空列表。
        List<I18nMessageVo> messages = infraServiceClient.errorMessages()
            .doOnError(e -> log.warn("加载错误码国际化消息失败", e))
            .onErrorReturn(List.of())
            .defaultIfEmpty(List.of())
            .block();

        if (Collections.isEmpty(messages)) {
            log.warn("[ErrorMessageStorage] load finished | messageCount=0 | cache={}", describeCache());
            return;
        }

        // noinspection ConstantConditions
        for (I18nMessageVo message : messages) {
            String locale = new Locale.Builder()
                .setLanguage(message.getLanguageCode())
                .setRegion(message.getRegionCode())
                .build()
                .toLanguageTag();

            MESSAGE_CACHE.computeIfAbsent(
                message.getMessageCode(),
                _ -> new ConcurrentHashMap<>()
            ).put(locale, message.getMessageText());
        }

        log.warn("[ErrorMessageStorage] load finished | messageCount={} | cache={}",
            messages.size(), describeCache());
    }

    /**
     * 根据错误码和 locale 获取对应的错误消息。
     * 如果 locale 对应的完整消息不存在，则尝试使用 locale 的语言前缀查找。
     * 搞的过于复杂的原因是Locale可能保持变体zh_CN_#Hans
     *
     * @param errorCode 错误码
     * @param locale    本地化标识，例如 "zh_CN", 如果为 {@code null} 则使用 {@link Locale#getDefault()}
     * @return 对应的错误消息，找不到返回空字符串
     */
    @Override
    public String getMessage(String errorCode, String locale) {
        String inputLocale = locale;
        // 错误编码不存在, 直接返回
        if (Strings.isBlank(errorCode)) {
            log.warn("[ErrorMessageStorage] getMessage | errorCode=blank | inputLocale={} | cache={}",
                inputLocale, describeCache());
            return null;
        }

        Map<String, String> innerMap = MESSAGE_CACHE.get(errorCode);
        if (Collections.isEmpty(innerMap)) {
            log.warn("[ErrorMessageStorage] getMessage | errorCode={} | inputLocale={} | resolvedLocale=n/a"
                    + " | localeDefault={} | hit=null | errorLocales=MISSING | cache={}",
                errorCode, inputLocale, Locale.getDefault().toLanguageTag(), describeCache());
            return null;
        }

        Locale raw;
        if (Strings.isBlank(locale)) {
            raw = Locale.getDefault();
        } else {
            raw = Locale.forLanguageTag(locale.trim().replace('_', '-'));
        }

        String resolvedLocale = new Locale.Builder()
            .setLanguage(raw.getLanguage())
            .setRegion(raw.getCountry())    // 无地区时要有兜底策略
            .build().toLanguageTag();       // 作为缓存内层 key

        // 获取语言-区域对应的错误信息
        String result = innerMap.get(resolvedLocale);

        // 获取语言对应的错误信息
        String fallbackLocale = null;
        if (Objects.isNull(result)) {
            fallbackLocale = raw.getLanguage();
            result = innerMap.get(fallbackLocale);
        }

        log.warn("[ErrorMessageStorage] getMessage | errorCode={} | inputLocale={} | resolvedLocale={}"
                + " | localeDefault={} | fallbackLocale={} | hit={} | errorLocales={} | cache={}",
            errorCode, inputLocale, resolvedLocale, Locale.getDefault().toLanguageTag(),
            fallbackLocale, result, safeJson(innerMap), describeCache());

        return result;
    }

    private static String describeCache() {
        int localeEntryCount = MESSAGE_CACHE.values().stream()
            .mapToInt(Map::size)
            .sum();
        return "errorCodeCount=" + MESSAGE_CACHE.size()
            + ", localeEntryCount=" + localeEntryCount
            + ", snapshot=" + safeJson(MESSAGE_CACHE);
    }

    private static String safeJson(Object value) {
        try {
            return JsonCodecFactory.instance().obj2str(value);
        } catch (Exception e) {
            return "(serialize failed: " + e.getMessage() + ")";
        }
    }

    @Override
    protected @NonNull String dataSource() {
        return "ERROR_MSG";
    }

    @Override
    protected @NonNull Class<LocalizedErrorMessage> getValueType() {
        return LocalizedErrorMessage.class;
    }

    @Override
    protected @NonNull String getKey(@NonNull LocalizedErrorMessage value) {
        return value.getErrorCode() + "#" + value.getLocale();
    }

    @Override
    protected void create(@NonNull String key, LocalizedErrorMessage value) {
        MESSAGE_CACHE.computeIfAbsent(value.getErrorCode(), _ -> new ConcurrentHashMap<>())
            .put(value.getLocale(), value.getMessageTemplate());
    }

    @Override
    protected void delete(@NonNull String key) {
        int sep = key.indexOf('#');
        if (sep < 0) {
            return;
        }

        String errorCode = key.substring(0, sep);
        String locale = key.substring(sep + 1);
        MESSAGE_CACHE.computeIfPresent(errorCode, (_, inner) -> {
            inner.remove(locale);
            return inner.isEmpty() ? null : inner;
        });
    }

    @Override
    protected void update(@NonNull String key, LocalizedErrorMessage value) {
        create(key, value);
    }

    @Override
    protected String get(@NonNull String key) {
        int sep = key.indexOf('#');
        if (sep < 0) {
            return null;
        }

        Map<String, String> inner = MESSAGE_CACHE.get(key.substring(0, sep));
        if (Objects.isNull(inner)) {
            return null;
        }

        return inner.get(key.substring(sep + 1));
    }
}
