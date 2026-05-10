package com.g2rain.gateway.model.route;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 与 basis {@code /authority/apis} 返回 JSON 对齐，仅供 WebClient 反序列化。
 */
@Setter
@Getter
@NoArgsConstructor
public class BaseAuthorityApiVo {

    private Long id;

    private String status;
}
