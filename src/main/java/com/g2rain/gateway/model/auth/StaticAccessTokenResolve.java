package com.g2rain.gateway.model.auth;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * basis {@code GET /login_token/static_access_token_context} 的 JSON 反序列化模型。
 *
 * <p>语义约定（与 basis {@link com.g2rain.basis.vo.StaticAccessTokenResolveVo} 一致）：</p>
 * <ul>
 *     <li>HTTP 成功且 {@code data == null} — 令牌不存在，网关视为 invalid。</li>
 *     <li>{@code status == REVOKED} — 令牌已吊销，{@link #context} 为空。</li>
 *     <li>{@code status == ACTIVATED} 且 {@link #context} 非空 — 可继续鉴权。</li>
 * </ul>
 *
 * @author alpha
 * @since 2026/5/22
 */
@Setter
@Getter
@NoArgsConstructor
public class StaticAccessTokenResolve {

    /**
     * 令牌状态字符串：{@code ACTIVATED} 或 {@code REVOKED}。
     */
    private String status;

    /**
     * 激活态会话上下文；吊销或数据不完整时为 {@code null}。
     */
    private StaticTokenContext context;
}
