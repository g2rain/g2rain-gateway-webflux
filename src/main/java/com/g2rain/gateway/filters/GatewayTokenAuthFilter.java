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
import com.g2rain.gateway.utils.AuthScheme;
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
 * 网关层 Token（JWT）鉴权过滤器。
 *
 * <p>
 * 校验 {@code Authorization} 中的登录态 JWT（ECDSA），解析 {@link com.g2rain.common.web.TokenJWTPayload}
 * 并写入 {@link EdgePrincipalContext}。与 {@link ApiKeyFilter} 分流：若 {@link EdgePrincipalContext#isStaticTokenAuthenticated()}
 * 已为真，说明请求已走静态 API Key 鉴权，本过滤器直接放行。
 * </p>
 *
 * <h2>核心流程</h2>
 * <ul>
 *     <li>白名单命中 → 跳过</li>
 *     <li>{@code staticTokenAuthenticated} → 跳过</li>
 *     <li>提取 Bearer 凭证，校验 JWT 签名与有效期</li>
 *     <li>填充用户、机构、应用等 Principal 字段</li>
 * </ul>
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

        return EdgePrincipalContextHolder.get().flatMap(context -> {
            if (context.isStaticTokenAuthenticated()) {
                return chain.filter(exchange);
            }

            String authHeader = request.getHeaders().getFirst(Constants.AUTHORIZATION_HEADER);
            if (Strings.isBlank(authHeader)) {
                return Mono.error(new GatewayException(GatewayErrorCode.TOKEN_INVALID, "token"));
            }

            String credential = AuthScheme.credential(authHeader);
            if (Strings.isBlank(credential)) {
                return Mono.error(new GatewayException(GatewayErrorCode.TOKEN_INVALID, "token"));
            }

            buildPrincipalContext(context, inspectToken(credential));
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
        context.setDeptPath(tokenPayload.getDeptPath());
        context.setAdminCompany(tokenPayload.isAdminCompany());
        context.setApplicationScopes(tokenPayload.getApplicationScopes());
        context.setClientPublicKey(tokenPayload.getClientPublicKey());
        context.setRoleIds(tokenPayload.getRoleIds());
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
