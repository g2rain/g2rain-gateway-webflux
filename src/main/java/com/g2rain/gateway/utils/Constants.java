package com.g2rain.gateway.utils;


/**
 * <p>{@code Constants} 类定义了系统中使用的常量值，主要用于 HTTP 请求头和认证类型的统一管理。</p>
 * <p>
 * 所有字段均为静态常量，方便在全局使用，避免硬编码。
 * </p>
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * String authHeader = Constants.AUTHORIZATION_HEADER;
 * String proofHeader = Constants.CLIENT_PROOF_HEADER;
 * String jwtType = Constants.CLIENT_PROOF_JWT_TYPE;
 * }</pre>
 *
 * @author alpha
 * @since 2025/10/6
 */
public final class Constants {

    /**
     * 私有构造，禁止实例化
     */
    private Constants() {

    }

    /**
     * {@code DEBUG_KEY_HEADER} 开启 DEBUG 模式的请求头KEY
     */
    public static final String DEBUG_KEY_HEADER = "X-DEBUG-KEY";

    /**
     * {@code AUTHORIZATION_HEADER} HTTP 请求头：Authorization
     */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    /**
     * Authorization 中 Bearer 方案前缀（与 OpenAI 一致：{@code Bearer sk-...}）。
     */
    public static final String BEARER_PREFIX = "Bearer ";

    /**
     * 静态 API Key 固定总长度（含前缀与两段负载）。
     */
    public static final int API_KEY_LENGTH = 64;

    /**
     * {@code CLIENT_PROOF_HEADER} HTTP 请求头：DPoP
     */
    public static final String CLIENT_PROOF_HEADER = "DPoP";

    /**
     * DPoP proof 类型标识。
     *
     * <p>用于区分普通 JWT 与  DPoP proof， DPoP proof 必须在 Header 中
     * 使用 {@code typ="dpop+jwt"} 来表明其类型，以便鉴权组件正确识别并进行
     * 防重放和请求绑定校验。</p>
     */
    public static final String CLIENT_PROOF_JWT_TYPE = "dpop+jwt";

    public static final String ROUTE_CONTEXT_PATH = "route.context.path";

    public static final String REQUEST_ID = "requestId";

    /**
     * 与 basis {@code AuthorizationStatus#ACTIVATED} 名称一致。
     */
    public static final String AUTHORIZATION_ACTIVATED = "ACTIVATED";

    /**
     * OpenAPI 文档默认路径
     */
    public static final String DOC_PATH = "/v3/api-docs";

    /**
     * 业务服务 OpenAPI 文档默认路径
     */
    public static final String DOC_PATH_FORMAT = "/%s" + DOC_PATH;

    /**
     * 请求参数缓存键
     */
    public static final String REQ_BODY_ATTRIBUTE = "g2rain.gateway.request.body";

    /**
     * 空响应结果
     */
    public static final String EMPTY_RSP_BODY = "{}";
}
