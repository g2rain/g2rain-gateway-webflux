package com.g2rain.gateway.filters;


import com.g2rain.common.json.JsonCodec;
import com.g2rain.common.json.JsonCodecFactory;
import com.g2rain.common.utils.Strings;
import com.g2rain.common.web.TokenJWTPayload;
import com.g2rain.gateway.enums.GatewayErrorCode;
import com.g2rain.gateway.exception.GatewayException;
import com.g2rain.gateway.model.context.EdgePrincipalContext;
import com.g2rain.gateway.model.context.EdgePrincipalContextHolder;
import com.g2rain.gateway.token.TokenKeyManager;
import com.g2rain.gateway.utils.Constants;
import com.g2rain.gateway.whitelist.WhiteListResolver;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
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

import java.security.interfaces.ECPublicKey;
import java.text.ParseException;
import java.time.Instant;
import java.util.Objects;

/**
 * 网关层 Token 鉴权过滤器。
 * <p>
 * 负责验证客户端请求中的 Token JWT（通常位于 {@code Authorization} Header 中），
 * 并在验证通过后构建并注入 {@link EdgePrincipalContext} 鉴权上下文。
 * </p>
 * <p>
 * 核心流程：
 * <ul>
 *     <li>检查白名单请求，若命中则跳过校验</li>
 *     <li>提取并验证 Token JWT</li>
 *     <li>调用认证服务进行 Token 验证</li>
 *     <li>构建并写入鉴权上下文</li>
 * </ul>
 * </p>
 *
 * @author alpha
 * @since 2025/10/6
 */
@Slf4j
@Component
@AllArgsConstructor
public class GatewayTokenAuthFilter implements GlobalFilter, Ordered {

    /**
     * 白名单解析器
     */
    private final WhiteListResolver whiteListResolver;

    /**
     * Token 密钥管理器，用于获取签名公钥
     */
    private final TokenKeyManager tokenKeyManager;

    private static final JsonCodec jsonCodec = JsonCodecFactory.instance();

    /**
     * 全局过滤入口。
     * <p>
     * 若命中白名单则直接放行，否则提取并验证 Token，
     * 验证通过后构建鉴权上下文并继续过滤链。
     * </p>
     *
     * @param exchange 当前请求上下文
     * @param chain    网关过滤链
     * @return 过滤结果
     */
    @Override
    public @NonNull Mono<@NonNull Void> filter(ServerWebExchange exchange, @NonNull GatewayFilterChain chain) {
        // 当前请求对象
        ServerHttpRequest request = exchange.getRequest();

        // 检查当前请求是否命中白名单规则
        // 如果命中白名单，则跳过当前 Filter 的处理，直接进入下一个 Filter
        String filterName = this.getClass().getSimpleName();
        if (whiteListResolver.shouldExclude(filterName, exchange)) {
            return chain.filter(exchange);
        }

        // 1. 提取 Token JWT
        String authHeader = request.getHeaders().getFirst(Constants.AUTHORIZATION_HEADER);
        if (Strings.isBlank(authHeader)) {
            throw new GatewayException(GatewayErrorCode.TOKEN_INVALID, "token");
        }

        // 去掉 "Bearer "
        if (Objects.nonNull(authHeader) && Strings.startsWith(authHeader, "Bearer ")) {
            authHeader = authHeader.substring(7);
        }

        // 2. 验证 Token JWT
        TokenJWTPayload payload = inspectToken(authHeader);

        // 3. 构建鉴权上下文并继续过滤链
        return EdgePrincipalContextHolder.get().flatMap(context -> {
            buildPrincipalContext(context, payload);
            return chain.filter(exchange);
        });
    }

    /**
     * 验证 Token JWT。
     *
     * @param jwt Token JWT 字符串
     * @return Token Payload 对象
     */
    private TokenJWTPayload inspectToken(String jwt) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(jwt);
            JWSHeader header = signedJWT.getHeader();
            ECPublicKey publicKey = tokenKeyManager.getKey(header.getKeyID());
            if (Objects.isNull(publicKey)) {
                throw new GatewayException(GatewayErrorCode.TOKEN_INVALID, "token");
            }

            JWSVerifier verifier = new ECDSAVerifier(publicKey);
            if (!signedJWT.verify(verifier)) {
                throw new GatewayException(GatewayErrorCode.TOKEN_INVALID, "token");
            }

            String payloadStr = signedJWT.getJWTClaimsSet().toString();
            TokenJWTPayload payload = jsonCodec.str2obj(payloadStr, TokenJWTPayload.class);

            Long expireAt = payload.getExpireAt();
            if (Objects.isNull(expireAt)) {
                throw new GatewayException(GatewayErrorCode.TOKEN_INVALID, "token");
            }
            if (Instant.now().getEpochSecond() > expireAt) {
                throw new GatewayException(GatewayErrorCode.TOKEN_EXPIRED, "token");
            }

            return payload;
        } catch (JOSEException | ParseException e) {
            throw new GatewayException(GatewayErrorCode.TOKEN_INVALID, "token");
        }
    }

    /**
     * 构建并填充鉴权上下文。
     *
     * @param context      当前请求上下文中的鉴权信息容器
     * @param tokenPayload Token 解析后的载荷
     */
    private void buildPrincipalContext(EdgePrincipalContext context, TokenJWTPayload tokenPayload) {
        context.setClientId(tokenPayload.getClientId());
        context.setSessionType(tokenPayload.getSessionType());
        context.setPassportId(tokenPayload.getPassportId());
        context.setUserId(tokenPayload.getUserId());
        context.setName(tokenPayload.getName());
        context.setAdminUser(tokenPayload.isAdminUser());
        context.setOrganType(tokenPayload.getOrganType());
        context.setOrganId(tokenPayload.getOrganId());
        context.setOrganName(tokenPayload.getOrganName());
        context.setAdminCompany(tokenPayload.isAdminCompany());
        context.setApplicationScopes(tokenPayload.getApplicationScopes());
    }

    /**
     * 定义过滤器执行顺序。
     *
     * @return 执行优先级
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 300;
    }
}
