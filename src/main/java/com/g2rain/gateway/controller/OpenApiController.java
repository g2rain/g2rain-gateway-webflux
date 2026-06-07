package com.g2rain.gateway.controller;

import com.g2rain.gateway.client.BasisServiceClient;
import com.g2rain.gateway.model.route.ServiceRegistryVo;
import com.g2rain.gateway.route.GatewayRouteLoader;
import com.g2rain.gateway.utils.Constants;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 动态 OpenAPI 目录：数据源自 basis 服务注册（与内存路由定义由 {@link GatewayRouteLoader} 维护）。
 *
 * @author alpha
 * @since 2026/4/11
 */
@RestController
@AllArgsConstructor
public class OpenApiController {
    /**
     * 机构客户端
     */
    private final BasisServiceClient basisServiceClient;

    /**
     * Swagger UI {@code configUrl} 所需结构（含 {@code urls} 与默认选中的 {@code urls.primaryName}）。
     */
    @GetMapping(value = "/swagger-ui-config", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> swaggerUiConfig() {
        return listDocs().map(items -> {
            List<Map<String, String>> urls = new ArrayList<>(items.size());
            for (OpenApiDocItem item : items) {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("name", item.name());
                row.put("url", item.url());
                urls.add(row);
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("urls", urls);
            if (!items.isEmpty()) {
                body.put("urls.primaryName", items.getFirst().name());
            }

            return body;
        });
    }

    /**
     * 返回当前动态路由推导出的文档列表；同一 context 多条路由只保留一条。
     */
    private Mono<@NonNull List<@NonNull OpenApiDocItem>> listDocs() {
        return basisServiceClient.getServiceRegistry()
            .mapNotNull(this::toItem)
            .distinct(OpenApiDocItem::url)
            .sort(Comparator.comparing(OpenApiDocItem::name, String.CASE_INSENSITIVE_ORDER))
            .collectList();
    }

    private OpenApiDocItem toItem(ServiceRegistryVo registry) {
        return Optional.of(registry.getRoutePrefix())
            .map(ctx -> new OpenApiDocItem(ctx, String.format(Constants.DOC_PATH_FORMAT, ctx)))
            .orElse(null);
    }

    public record OpenApiDocItem(String name, String url) {
    }
}
