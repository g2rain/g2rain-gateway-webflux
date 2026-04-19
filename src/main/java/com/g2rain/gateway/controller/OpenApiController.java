package com.g2rain.gateway.controller;

import com.g2rain.gateway.route.MemoryRouteRepository;
import com.g2rain.gateway.utils.Constants;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
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
 * 动态 OpenAPI 目录：数据源自 {@link com.g2rain.gateway.route.MemoryRouteRepository} 当前路由。
 *
 * @author alpha
 * @since 2026/4/11
 */
@RestController
@AllArgsConstructor
public class OpenApiController {
    private final MemoryRouteRepository routeRepository;

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
        return routeRepository.getRouteDefinitions()
            .mapNotNull(this::toItem)
            .distinct(OpenApiDocItem::url)
            .sort(Comparator.comparing(OpenApiDocItem::name, String.CASE_INSENSITIVE_ORDER))
            .collectList();
    }

    private OpenApiDocItem toItem(RouteDefinition route) {
        return contextOf(route)
            .map(ctx -> new OpenApiDocItem(ctx, String.format("/%s/v3/api-docs", ctx)))
            .orElse(null);
    }

    private Optional<String> contextOf(RouteDefinition route) {
        Object raw = route.getMetadata().get(Constants.ROUTE_CONTEXT_PATH);
        if (!(raw instanceof String s) || !StringUtils.hasText(s)) {
            return Optional.empty();
        }

        String trimmed = s.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }

        // 与 Path 谓词中 context 段一致：去掉首尾 '/'
        String normalized = trimmed.replaceAll("^/+", "").replaceAll("/+$", "");
        if (!StringUtils.hasText(normalized)) {
            return Optional.empty();
        }

        return Optional.of(normalized);
    }

    public record OpenApiDocItem(String name, String url) {
    }
}
