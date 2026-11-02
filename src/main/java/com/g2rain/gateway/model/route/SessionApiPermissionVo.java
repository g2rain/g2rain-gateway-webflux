package com.g2rain.gateway.model.route;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Basis {@code /authority/session_api_permissions} 响应体（Gateway 侧解耦模型）。
 */
@Setter
@Getter
@NoArgsConstructor
public class SessionApiPermissionVo {

    private String sessionType;

    private Long organId;

    private Long version;

    private List<Long> apiIds;

    /**
     * 启用中的匿名接入码 ID；仅 ANONYMOUS 快照携带。
     */
    private List<Long> entryIds;
}
