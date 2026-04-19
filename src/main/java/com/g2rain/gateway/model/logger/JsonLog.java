package com.g2rain.gateway.model.logger;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @author alpha
 * @since 2026/4/13
 */
@Getter
@Setter
@NoArgsConstructor
public class JsonLog {
    // DPoP Authorization 不需要 Header不需要， 已将需要上送的Header拆分到具体的EdgePrincipalContext字段！！！！

    // kafka能否做成异步推送，不影响请求的响应时间!!!!
    // EdgePrincipalContext
    // Request queryString, body {"a":[1,2]} queryString 和 body 合并
    // user-Agent, Host x-forward-ip -x-real-ip referer
    // Response (Json)
    // traceId，requestId
    // {"payload":{"request":{}, "response":{}}}
    private String level;
    private String traceId;
    private String requestId;
    private long timestamp;
    private String message;
}
