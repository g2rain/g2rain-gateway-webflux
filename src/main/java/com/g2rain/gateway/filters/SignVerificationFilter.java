package com.g2rain.gateway.filters;


import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.json.JsonCodecFactory;
import com.g2rain.common.utils.Constants;
import com.g2rain.common.utils.Strings;
import com.g2rain.gateway.enums.HashAlgorithm;
import com.g2rain.gateway.model.context.EdgePrincipalContextHolder;
import com.g2rain.gateway.model.web.CachedBodyRequest;
import com.g2rain.gateway.utils.ReqParamCodec;
import com.g2rain.gateway.whitelist.WhiteListResolver;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 全局请求摘要校验过滤器。
 *
 * <p>
 * 对 query 参数与请求 body 做规范化哈希，与 {@link EdgePrincipalContext} 中 DPoP 阶段写入的预期摘要比对，
 * 用于保证请求内容在传输过程中未被篡改。依赖 {@link CachedBodyFilter} 已缓存的 body。
 * </p>
 * <p>
 * 若 {@link com.g2rain.gateway.model.context.EdgePrincipalContext#isStaticTokenAuthenticated()} 为真，则跳过：
 * 静态 API Key 链路不使用 DPoP/客户端摘要体系。
 * </p>
 *
 * <p>支持的算法见 {@link com.g2rain.gateway.enums.HashAlgorithm}。</p>
 *
 * @author alpha
 * @since 2025/10/6
 */
@Slf4j
@Component
@AllArgsConstructor
public class SignVerificationFilter implements GlobalFilter, Ordered {

    /**
     * 白名单解析器，用于判断请求是否需要跳过摘要校验。
     */
    private final WhiteListResolver whiteListResolver;

    /**
     * 十六进制格式化工具，用于将字节数组转换为 hex 字符串。
     */
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    /**
     * 执行全局过滤逻辑。
     *
     * @param exchange 当前请求的 {@link ServerWebExchange} 对象
     * @param chain    网关过滤器链
     * @return {@link Mono} 表示异步执行结果
     */
    @Override
    public @NonNull Mono<Void> filter(ServerWebExchange exchange, @NonNull GatewayFilterChain chain) {
        // 当前请求对象
        ServerHttpRequest request = exchange.getRequest();

        // 没有缓存, 说明出现意外情况, 不进行hash, 直接忽略
        if (!(request instanceof CachedBodyRequest cached)) {
            return chain.filter(exchange);
        }

        // 检查当前请求是否命中白名单规则
        // 如果命中白名单，则跳过当前 Filter 的处理，直接进入下一个 Filter
        String filterName = this.getClass().getSimpleName();
        if (whiteListResolver.shouldExclude(filterName, exchange)) {
            return chain.filter(exchange);
        }

        return EdgePrincipalContextHolder.get().flatMap(principalContext -> {
            if (principalContext.isStaticTokenAuthenticated()) {
                return chain.filter(exchange);
            }

            String algorithm = principalContext.getHashAlgorithm();
            // hash 算法错误
            if (HashAlgorithm.isNotExist(algorithm)) {
                return Mono.error(new BusinessException(
                    SystemErrorCode.PARAM_VAL_INVALID, "ph_alg"
                ));
            }

            String expectedHash = principalContext.getParamHashStr();
            // 没有参数摘要直接报错
            if (Strings.isBlank(expectedHash)) {
                return Mono.error(new BusinessException(
                    SystemErrorCode.PARAM_REQUIRED, "pha"
                ));
            }

            // query 参数规范化
            String queryParams = ReqParamCodec.normalizeParams(request.getQueryParams());
            log.debug("request param:{}, sha256 param:{}", JsonCodecFactory.instance().obj2str(request.getQueryParams()), queryParams);
            // body 摘要处理
            String bodyHash = sha256(cached.asBytes(), algorithm);
            log.debug("queryParams:{}, bodyHash:{}", queryParams, bodyHash);
            // 拼接 query + body 进行哈希
            String finalHash = sha256(queryParams + "\n" + bodyHash, algorithm);
            // 签名不一致则拒绝
            if (!expectedHash.equals(finalHash)) {
                log.error("expectedHash:{}, finalHash:{}", expectedHash, finalHash);
                return Mono.error(new BusinessException(
                    SystemErrorCode.PARAM_VAL_INVALID, "pha"
                ));
            }

            // 签名正确，继续执行
            return chain.filter(exchange);
        });
    }

    /**
     * 对字符串进行摘要计算。
     *
     * @param input     待计算摘要的字符串
     * @param algorithm 摘要算法名称
     * @return 摘要值的十六进制字符串
     */
    private String sha256(String input, String algorithm) {
        return sha256(input.getBytes(StandardCharsets.UTF_8), algorithm);
    }

    /**
     * 对字节数组进行摘要计算。
     *
     * @param data      待计算摘要的字节数组
     * @param algorithm 摘要算法名称
     * @return 摘要值的十六进制字符串
     */
    private String sha256(byte[] data, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hash = digest.digest(Objects.nonNull(data) ? data : Constants.EMPTY_BYTE);
            return HEX_FORMAT.formatHex(hash);
        } catch (Exception e) {
            throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "ph_alg");
        }
    }

    /**
     * 指定过滤器执行顺序。
     *
     * @return 执行顺序值
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 600;
    }
}
