package com.g2rain.gateway.utils;


import com.g2rain.common.utils.Strings;

import java.util.regex.Pattern;

/**
 * {@code Authorization} 头解析与凭证形态识别。
 *
 * <p>
 * g2rain 网关在同一 {@code Bearer}  Scheme 下承载两类凭证：
 * </p>
 * <ul>
 *     <li><b>JWT</b> — 登录态访问令牌，由 {@link com.g2rain.gateway.filters.GatewayTokenAuthFilter} 校验。</li>
 *     <li><b>静态 API Key</b> — 个人静态访问令牌，格式 {@code sk-{applicationAuthorizationIdHex}-{base64url}}，
 *         总长度 {@link Constants#API_KEY_LENGTH}，由 {@link com.g2rain.gateway.filters.ApiKeyFilter} 校验。</li>
 * </ul>
 *
 * <p>{@link #credential(String)} 仅负责剥离 {@code Bearer } 前缀；具体走哪条鉴权链由 {@link #isApiKey(String)} 决定。</p>
 *
 * @author alpha
 * @since 2026/5/22
 */
public final class AuthScheme {

    /**
     * API Key 形态：{@code sk-} + 十六进制段 + {@code -} + base64url 段。
     */
    private static final Pattern API_KEY = Pattern.compile("^sk-[0-9A-Fa-f]+-[-_A-Za-z0-9]+$");

    private AuthScheme() {
    }

    /**
     * 从 {@code Authorization} 头解析 Bearer 凭证。
     *
     * @param authorization 原始头值，可为 {@code Bearer xxx} 或大小写混合的 bearer
     * @return 剥离前缀后的凭证；非 Bearer 或为空时返回 {@code null}
     */
    public static String credential(String authorization) {
        if (Strings.isBlank(authorization)) {
            return null;
        }

        String value = authorization.strip();
        if (!Strings.startsWith(value, Constants.BEARER_PREFIX)) {
            return null;
        }

        String cred = value.substring(Constants.BEARER_PREFIX.length()).strip();
        return cred.isEmpty() ? null : cred;
    }

    /**
     * 判断凭证是否为合法形态的静态 API Key（长度 + 正则）。
     *
     * @param credential {@link #credential(String)} 的返回值
     * @return {@code true} 表示应走 API Key 鉴权链
     */
    public static boolean isApiKey(String credential) {
        if (Strings.isBlank(credential)) {
            return false;
        }

        if (credential.length() != Constants.API_KEY_LENGTH) {
            return false;
        }

        return API_KEY.matcher(credential).matches();
    }
}
