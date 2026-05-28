package com.g2rain.gateway.filters;


import com.g2rain.common.json.JsonCodecFactory;
import com.g2rain.common.utils.Collections;
import com.g2rain.common.utils.Strings;
import com.g2rain.common.web.PrincipalHeaders;
import com.g2rain.gateway.components.KafkaLogSender;
import com.g2rain.gateway.model.context.EdgePrincipalContextHolder;
import com.g2rain.gateway.model.event.GatewayEvent;
import com.g2rain.gateway.model.web.CachedBodyRequest;
import com.g2rain.gateway.model.web.CachedBodyResponse;
import com.g2rain.gateway.utils.Constants;
import com.g2rain.gateway.utils.ReqParamCodec;
import com.g2rain.gateway.whitelist.WhiteListResolver;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.DefaultPartHttpMessageReader;
import org.springframework.http.codec.multipart.Part;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 请求与响应日志记录过滤器。
 * <p>
 * 本过滤器用于在 Spring Cloud Gateway 中记录请求和响应信息，
 * 主要功能包括：
 * </p>
 * <ul>
 *     <li>记录请求的路径、方法、查询参数、请求头等基础信息</li>
 *     <li>根据请求 Content-Type 处理并记录请求体内容</li>
 *     <li>记录响应体（仅 JSON 数据）</li>
 * </ul>
 *
 * <h2>支持的请求体类型</h2>
 * <ul>
 *     <li>{@code application/json}</li>
 *     <li>{@code application/x-www-form-urlencoded}</li>
 *     <li>{@code multipart/form-data}</li>
 *     <li>其他类型仅记录基础信息</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * @Bean
 * public TraceLoggingFilter traceLoggingFilter() {
 *     return new TraceLoggingFilter();
 * }
 * }</pre>
 *
 * <h2>注意事项</h2>
 * <ul>
 *     <li>需要配合 {@link CachedBodyRequest} 和 {@link CachedBodyResponse} 使用，以保证请求/响应体可重复读取</li>
 *     <li>对于非 JSON 响应，仅记录基础信息</li>
 * </ul>
 *
 * @author alpha
 * @since 2025/10/6
 */
@Slf4j
@Component
@AllArgsConstructor
public class TraceLoggingFilter implements GlobalFilter, Ordered {

    /**
     * 网关侧 Kafka 日志发送
     */
    private final KafkaLogSender kafkaLogSender;

    /**
     * {@code whiteListResolver} 用于判断当前请求是否命中白名单规则，
     * 如果命中则可以跳过当前 Filter 的执行。
     * <p>
     * 白名单规则包括全局规则和针对特定 Filter 的规则，匹配顺序为：
     * Filter 白名单 → 全局白名单，
     * 匹配方式包括 contextPath、exactPath、patternPath。
     * </p>
     */
    private final WhiteListResolver whiteListResolver;

    /**
     * 网关过滤器方法，记录请求和响应日志。
     *
     * @param exchange 当前请求的 {@link ServerWebExchange}
     * @param chain    网关过滤器链
     * @return {@link Mono} 完成信号
     */
    @Override
    public @NonNull Mono<@NonNull Void> filter(@NonNull ServerWebExchange exchange, @NonNull GatewayFilterChain chain) {
        // 检查当前请求是否命中白名单规则
        // 如果命中白名单，则跳过当前 Filter 的处理，直接进入下一个 Filter
        String filterName = this.getClass().getSimpleName();
        if (whiteListResolver.shouldExclude(filterName, exchange)) {
            return chain.filter(exchange);
        }

        return logRequest(exchange).then(chain.filter(exchange)).then(logResponse(exchange));
    }

    /**
     * 记录响应日志。
     *
     * @param exchange 当前请求上下文
     * @return {@link Mono} 完成信号
     */
    private Mono<@NonNull Void> logResponse(ServerWebExchange exchange) {
        return Mono.defer(() -> {
            ServerHttpResponse response = exchange.getResponse();

            if (!(response instanceof CachedBodyResponse cachedResponse)) {
                log.info("响应体大小:{}", response.getHeaders().getContentLength());
                log.info("打印非 JSON 数据日志1");
                return Mono.empty();
            }

            byte[] cachedBody = cachedResponse.getBody();
            log.info("响应体大小:{}", Collections.isNotEmpty(cachedBody) ? cachedBody.length : response.getHeaders().getContentLength());

            if (Collections.isEmpty(cachedBody)) {
                log.info("打印非 JSON 数据日志2");
                return Mono.empty();
            }

            log.info(new String(cachedBody, StandardCharsets.UTF_8));

            return EdgePrincipalContextHolder.get().doOnNext(ctx -> {
                try {
                    GatewayEvent gatewayEvent = GatewayEvent.builder()
                        .buildHeaders(exchange)
                        .buildPrincipal(ctx)
                        .buildPayload(exchange, cachedBody)
                        .build();

                    kafkaLogSender.send("gateway.exchange.event", gatewayEvent);
                } catch (Exception e) {
                    log.warn("构建或发送网关事件失败", e);
                }
            }).then();
        });
    }

    /**
     * 记录请求日志。
     *
     * @param exchange 当前请求上下文
     * @return {@link Mono} 完成信号
     */
    private Mono<@NonNull Void> logRequest(ServerWebExchange exchange) {
        return Mono.defer(() -> {
            ServerHttpRequest request = exchange.getRequest();
            Map<String, Object> logMap = new LinkedHashMap<>();

            try {
                collectBasicRequestInfo(exchange, logMap);
            } catch (Exception e) {
                log.warn("收集基础请求信息失败", e);
            }

            MediaType contentType = request.getHeaders().getContentType();
            if (Objects.isNull(contentType) || !(request instanceof CachedBodyRequest cached)) {
                return printRequest(logMap);
            }

            if (MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(contentType)) {
                return processFormUrlEncodedBody(exchange, cached, logMap);
            }

            if (MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
                return processJsonBody(exchange, cached, logMap);
            }

            if (MediaType.MULTIPART_FORM_DATA.isCompatibleWith(contentType)) {
                return processMultipartBody(exchange, cached, logMap);
            }

            return printRequest(logMap);
        });
    }

    /**
     * 收集基础请求信息。
     *
     * @param exchange 当前请求上下文
     * @param logMap   存储日志信息的 Map
     */
    private void collectBasicRequestInfo(ServerWebExchange exchange, Map<String, Object> logMap) {
        ServerHttpRequest request = exchange.getRequest();
        logMap.put("请求路径", request.getPath().pathWithinApplication().value());
        logMap.put("请求方法", request.getMethod().name());
        logMap.put("查询参数", request.getQueryParams());
        logMap.put("请求头", buildLoggableHttpHeaders(request.getHeaders()));
    }

    /**
     * 构造可 JSON 序列化的请求头视图。
     * <p>
     * 若直接序列化 {@link HttpHeaders}，Jackson 只会按 JavaBean 暴露少量标准头 getter，
     * {@code Authorization}、{@code DPoP} 等自定义头不会出现在 JSON 中。
     * </p>
     */
    private Map<String, List<String>> buildLoggableHttpHeaders(HttpHeaders headers) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        headers.forEach((name, values) -> {
            List<String> decoded = new ArrayList<>(values.size());
            for (String value : values) {
                decoded.add(safeDecode(name, value));
            }
            result.put(name, decoded);
        });
        return result;
    }

    private String safeDecode(String key, String value) {
        if (Strings.isBlank(value)) {
            return null;
        }

        String keyLower = key.toLowerCase();
        String nameLower = PrincipalHeaders.NAME.getLower();
        String organNameLower = PrincipalHeaders.ORGAN_NAME.getLower();
        if (!nameLower.equals(keyLower) && !organNameLower.equals(keyLower)) {
            return value;
        }

        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    /**
     * 处理 application/x-www-form-urlencoded 类型请求体。
     *
     * @param cachedRequest 缓存请求体
     * @param logMap        存储日志信息的 Map
     * @return {@link Mono} 完成信号
     */
    private Mono<@NonNull Void> processFormUrlEncodedBody(ServerWebExchange exchange, CachedBodyRequest cachedRequest, Map<String, Object> logMap) {
        var formParams = ReqParamCodec.processFormUrlEncodedBody(cachedRequest);
        exchange.getAttributes().put(Constants.REQ_BODY_ATTRIBUTE, formParams);
        logMap.put("表单参数", formParams);
        return printRequest(logMap);
    }

    /**
     * 处理 application/json 类型请求体。
     *
     * @param cachedRequest 缓存请求体
     * @param logMap        存储日志信息的 Map
     * @return {@link Mono} 完成信号
     */
    private Mono<@NonNull Void> processJsonBody(ServerWebExchange exchange, CachedBodyRequest cachedRequest, Map<String, Object> logMap) {
        var body = new String(cachedRequest.asBytes(), StandardCharsets.UTF_8);
        exchange.getAttributes().put(Constants.REQ_BODY_ATTRIBUTE, body);
        logMap.put("请求主体", body);
        return printRequest(logMap);
    }

    /**
     * 处理 multipart/form-data 类型请求体。
     *
     * @param cachedRequest 缓存请求体
     * @param logMap        存储日志信息的 Map
     * @return {@link Mono} 完成信号
     */
    private Mono<@NonNull Void> processMultipartBody(ServerWebExchange exchange, CachedBodyRequest cachedRequest, Map<String, Object> logMap) {
        return new DefaultPartHttpMessageReader().read(ResolvableType.forClass(Part.class), cachedRequest, Map.of())
            .cast(Part.class)
            .collectList()
            .map(parts -> {
                MultiValueMap<String, Part> multipartData = new LinkedMultiValueMap<>();
                parts.forEach(part -> multipartData.add(part.name(), part));
                return multipartData;
            })
            .flatMap(multipartData -> ReqParamCodec.processMultipartBody(
                multipartData, filePart -> Mono.just(filePart.filename())
            )).flatMap(multipartMap -> {
                exchange.getAttributes().put(Constants.REQ_BODY_ATTRIBUTE, multipartMap);
                logMap.put("表单参数", multipartMap);
                return printRequest(logMap);
            });
    }

    /**
     * 打印请求日志。
     *
     * @param logMap 存储日志信息的 Map
     * @return {@link Mono} 完成信号
     */
    private Mono<@NonNull Void> printRequest(Map<String, Object> logMap) {
        log.info("请求信息 - {}", JsonCodecFactory.instance().obj2str(logMap));
        return Mono.empty();
    }

    /**
     * 获取过滤器执行顺序。
     *
     * @return 过滤器顺序值
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 200;
    }
}
