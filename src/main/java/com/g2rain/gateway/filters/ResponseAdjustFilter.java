package com.g2rain.gateway.filters;


import com.g2rain.common.exception.ExceptionConverter;
import com.g2rain.common.json.JsonCodec;
import com.g2rain.common.json.JsonCodecBuilder;
import com.g2rain.common.json.JsonCodecFactory;
import com.g2rain.common.json.SuccessIgnoreFieldMixIn;
import com.g2rain.common.model.Result;
import com.g2rain.common.utils.Collections;
import com.g2rain.common.utils.Strings;
import com.g2rain.gateway.cache.AppName;
import com.g2rain.gateway.cache.OrganName;
import com.g2rain.gateway.model.web.CachedBodyResponse;
import com.g2rain.gateway.whitelist.WhiteListResolver;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * 响应调整过滤器。
 * <p>
 * 本过滤器用于在 Spring Cloud Gateway 中拦截并调整响应，
 * 主要功能包括：
 * </p>
 * <ul>
 *     <li>捕获网关响应体（基于 {@link CachedBodyResponse}）</li>
 *     <li>解析响应为统一的 {@link Result} 对象</li>
 *     <li>判断业务状态码，非 200 抛出业务异常</li>
 *     <li>对响应 data 部分进行字段映射和调整</li>
 *     <li>将调整后的结果重新写回响应体</li>
 * </ul>
 *
 * <h2>字段映射规则</h2>
 * <pre>
 * "companyOrganId" → "companyOrganName"
 * "tenantId"        → "tenantName"
 * "organId"         → "organName"
 * "applicationId"   → "applicationName"
 * </pre>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * @Bean
 * public ResponseAdjustFilter responseAdjustFilter() {
 *     return new ResponseAdjustFilter();
 * }
 * }</pre>
 *
 * <h2>注意事项</h2>
 * <ul>
 *     <li>需要配合 {@link CachedBodyFilter} 使用，确保响应被缓存</li>
 *     <li>只处理 JSON 格式响应</li>
 *     <li>非业务成功响应会直接抛出业务异常</li>
 *     <li>字段映射可根据业务扩展修改 {@link #fieldMappings}</li>
 * </ul>
 *
 * @author alpha
 * @since 2025/10/6
 */
@Slf4j
@Component
public class ResponseAdjustFilter implements GlobalFilter, Ordered {

    /**
     * JSON 序列化器
     */
    private final JsonCodec jsonSerializer = JsonCodecBuilder.builder().withDefaults().withConfig(jsonMapper ->
        jsonMapper.addMixIn(Result.class, SuccessIgnoreFieldMixIn.class)
    ).build();

    /**
     * JSON 反序列化器
     */
    private final JsonCodec jsonDeserializer = JsonCodecFactory.instance();

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
     * 字段映射规则
     */
    private final List<FieldMapping> fieldMappings;

    public ResponseAdjustFilter(WhiteListResolver whiteListResolver, AppName appName, OrganName organName) {
        this.whiteListResolver = whiteListResolver;
        this.fieldMappings = List.of(
            new FieldMapping("tenantId", "tenantName", organName::getNames),
            new FieldMapping("organId", "organName", organName::getNames),
            new FieldMapping("applicationId", "applicationName", appName::getNames)
        );
    }

    /**
     * 网关过滤器方法，拦截并调整响应。
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

        /*
         * 延迟执行代码, 直到上一个 Mono 完成之后(响应写入完成之后), 才执行里面的逻辑。
         * 这意味着你可以在响应流已经被处理完之后, 安全地访问缓存的响应数据,
         * 因为整个响应已经被装饰器捕获完成。
         */
        return chain.filter(exchange).then(Mono.defer(() -> {
            ServerHttpResponse response = exchange.getResponse();

            // 如果不是 CachedBodyResponse，直接跳过
            if (!(response instanceof CachedBodyResponse cached)) {
                // 没有缓存响应体，直接跳过处理
                return Mono.empty();
            }

            byte[] cachedBody = cached.getBody();
            // 没有缓存响应体(比如非json格式[文件下载]不会缓存), 直接跳过处理
            if (Collections.isEmpty(cachedBody)) {
                return Mono.empty();
            }

            // 1. 将缓存 body 直接解析为 Result<JsonNode>
            Result<JsonNode> result = jsonDeserializer.byte2obj(
                cachedBody, new TypeReference<>() {
                }
            );

            // 如果没有值, 直接跳过处理
            if (Objects.isNull(result)) {
                return Mono.empty();
            }

            // 2. 判断业务状态码, 如果状态码不是 200 → 抛业务异常
            if (!result.isSuccess()) {
                return Mono.error(ExceptionConverter.of(result));
            }

            // 3. 状态码是 200 → 解析 data 部分
            JsonNode data = result.getData();
            if (Objects.isNull(data)) {
                // 将调整后的 Result 转回缓存 body
                cached.refresh(jsonSerializer.obj2byte(result));
                return Mono.empty();
            }

            // 4. 替换原始响应体，写回客户端
            return adjustData(data).doOnSuccess(ignore ->
                cached.refresh(jsonSerializer.obj2byte(result))
            ).then();
        }));
    }

    /**
     * 调整响应 {@code data} 节点（仅做“结构拆分 + 目标节点收集”）。
     * <p>
     * 网关返回体的 {@code data} 可能是：
     * </p>
     * <ul>
     *     <li>数组：{@code [ {...}, {...} ]}</li>
     *     <li>对象：{@code { ... }}</li>
     *     <li>分页对象：{@code { records: [ {...}, ... ], pageNum: 1, ... }}</li>
     * </ul>
     *
     * <p>
     * 本方法只负责把所有需要调整的 {@link ObjectNode} 收集到 {@code targets}，最后交给批量处理方法
     * {@link #doAdjustData(List)}。这样后续把“批量收集 id → 批量查询 → 批量回写”的逻辑放到批量方法中，
     * 就能避免在网关高并发下对每条记录进行单独 IO 查询。
     * </p>
     *
     * @param data JSON data 节点
     */
    private Mono<Void> adjustData(@NonNull JsonNode data) {
        // 统一收集需要处理的 ObjectNode（只收集对象节点；数组里若存在非对象元素会被忽略）
        List<ObjectNode> targets = new ArrayList<>();

        // 情况 1：data 本身是数组：把数组里的对象节点全部加入 targets
        if (data.isArray()) {
            addAllObjectNodes(data, targets);
            return doAdjustData(targets);
        }

        // 情况 2：非对象：不处理
        if (!data.isObject()) {
            return Mono.empty();
        }

        // 情况 3：对象但不是分页结构：把自身作为目标节点
        if (!data.has("records") || !data.has("pageNum")) {
            addIfObjectNode(data, targets);
            return doAdjustData(targets);
        }

        // 情况 4：分页结构：只处理 records 数组中的对象节点
        JsonNode records = data.get("records");
        if (Objects.isNull(records) || !records.isArray()) {
            return Mono.empty();
        }

        addAllObjectNodes(records, targets);
        return doAdjustData(targets);
    }

    /**
     * 将一个数组节点中的所有对象元素（{@link ObjectNode}）追加到 targets。
     * <p>
     * 这是一个“小工具方法”：让调用方不必重复写遍历与 instanceof 判断。
     * </p>
     *
     * @param arrayNode 可能为数组的节点（非数组或为 null 会被忽略）
     * @param targets   目标收集容器
     */
    private void addAllObjectNodes(JsonNode arrayNode, List<ObjectNode> targets) {
        // 调用方保证传入的是数组节点
        for (JsonNode item : arrayNode) {
            addIfObjectNode(item, targets);
        }
    }

    /**
     * 如果 node 是 {@link ObjectNode}，则加入 targets；否则忽略。
     *
     * @param node    待判断节点
     * @param targets 目标收集容器
     */
    private void addIfObjectNode(JsonNode node, List<ObjectNode> targets) {
        if (node instanceof ObjectNode objectNode) {
            targets.add(objectNode);
        }
    }

    /**
     * 批量处理版本：后续把“批量收集 id → 批量查询 → 批量回写”的逻辑放到这里。
     *
     * @param targets 需要调整的对象节点集合
     */
    private Mono<Void> doAdjustData(List<ObjectNode> targets) {
        if (Collections.isEmpty(targets)) {
            return Mono.empty();
        }

        // 1) 批量收集：只收集“缺失 name”的 id
        Map<FieldMapping, Set<Long>> idsByMapping = new HashMap<>();
        for (FieldMapping mapping : fieldMappings) {
            idsByMapping.put(mapping, new HashSet<>());
        }

        for (ObjectNode node : targets) {
            for (FieldMapping mapping : fieldMappings) {
                String idStr = idStrIfNeedFill(node, mapping);
                if (Objects.isNull(idStr)) {
                    continue;
                }

                try {
                    idsByMapping.get(mapping).add(Long.parseLong(idStr));
                } catch (Exception ignore) {
                    // id 不是数字，忽略
                }
            }
        }

        // 2) 批量查询：每个字段映射独立查询（不按类型合并）
        Map<FieldMapping, Map<String, String>> namesByMapping = new HashMap<>();
        return Flux.fromIterable(fieldMappings).flatMap(mapping -> {
            Set<Long> ids = idsByMapping.get(mapping);
            if (Collections.isEmpty(ids)) {
                namesByMapping.put(mapping, Map.of());
                return Mono.empty();
            }

            return mapping.resolver().apply(ids)
                .defaultIfEmpty(Map.of())
                .onErrorResume(e -> {
                    log.warn("批量查询名称失败, idField={} ids={}", mapping.idField(), ids, e);
                    return Mono.just(Map.of());
                })
                .doOnNext(map -> namesByMapping.put(mapping, map))
                .then();
        }).then(Mono.fromRunnable(() -> {
            // 3) 批量回写：只写入缺失 name 的字段
            for (ObjectNode node : targets) {
                for (FieldMapping mapping : fieldMappings) {
                    String idStr = idStrIfNeedFill(node, mapping);
                    if (Objects.isNull(idStr)) {
                        continue;
                    }

                    Map<String, String> map = namesByMapping.get(mapping);
                    if (Objects.isNull(map)) {
                        continue;
                    }

                    String name = map.get(idStr);
                    if (Strings.isBlank(name)) {
                        continue;
                    }

                    node.put(mapping.nameField(), name);
                }
            }
        }));
    }

    /**
     * 根据 ID 字段名 + NAME字段值 决定返回 ID字段值
     *
     * @param node    响应记录
     * @param mapping 属性映射对象
     * @return ID 字段值
     */
    private String idStrIfNeedFill(ObjectNode node, FieldMapping mapping) {
        JsonNode idNode = node.get(mapping.idField());
        if (Objects.isNull(idNode) || idNode.isNull() || !idNode.isValueNode()) {
            return null;
        }

        JsonNode nameNode = node.get(mapping.nameField());
        if (Objects.nonNull(nameNode) && !nameNode.isNull()) {
            return null;
        }

        return idNode.asString();
    }

    /**
     * 获取过滤器执行顺序，值越小优先级越高。
     *
     * @return 过滤器顺序值
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 800;
    }

    /**
     * 定义一个简单容器，包含 ID 字段名、NAME 字段名、批量解析函数。
     *
     * @param idField   ID 的属性名
     * @param nameField NAME  的属性名
     * @param resolver  批量解析：ids → (id->name)
     */
    private record FieldMapping(String idField, String nameField, Function<Set<Long>, Mono<Map<String, String>>> resolver) {
    }
}
