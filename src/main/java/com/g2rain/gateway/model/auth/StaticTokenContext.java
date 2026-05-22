package com.g2rain.gateway.model.auth;


import com.g2rain.common.enums.OrganType;
import com.g2rain.common.enums.SessionType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 静态 API Key 鉴权成功后，basis 返回的会话上下文在网关侧的承载类型。
 *
 * <p>
 * 由 {@link com.g2rain.gateway.cache.ApiKeyCache} 从
 * {@link StaticAccessTokenResolve} 中提取，经
 * {@link com.g2rain.gateway.filters.ApiKeyFilter} 映射到
 * {@link com.g2rain.gateway.model.context.EdgePrincipalContext}，
 * 供下游过滤器与 {@link com.g2rain.gateway.filters.PrincipalForwardFilter} 使用。
 * </p>
 *
 * @author alpha
 * @since 2026/5/22
 */
@Setter
@Getter
@NoArgsConstructor
public class StaticTokenContext {

    /** 会话类型，静态令牌场景固定为 {@link SessionType#USER}。 */
    private SessionType sessionType;

    /** 账号（通行证）标识。 */
    private Long passportId;

    /** 用户标识。 */
    private Long userId;

    /** 用户姓名，用于展示。 */
    private String name;

    /** 是否机构内管理员。 */
    private boolean adminUser;

    /** 用户所属机构标识。 */
    private Long organId;

    /** 用户所属机构名称。 */
    private String organName;

    /** 机构类型。 */
    private OrganType organType;

    /** 是否为平台管理组织。 */
    private boolean adminCompany;

    /** 令牌绑定的应用标识（调用方身份）。 */
    private Long applicationId;

    /** 令牌绑定的应用编码。 */
    private String applicationCode;

    /** 应用所属机构标识。 */
    private Long applicationOrganId;
}
