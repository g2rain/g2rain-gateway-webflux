# 模块与职责

| 包 | 职责 |
| --- | --- |
| `filters` | 全局请求链、认证、权限、签名、主体透传、日志和响应调整 |
| `route`、`matcher` | 内存路由仓库、Spring Cloud Gateway 定义、路径规则编译与匹配索引 |
| `cache` | 路由、权限、应用/机构名称、内部路由和 API Key 摘要缓存及同步 |
| `client` | 通过负载均衡 WebClient 调用 Basis 与 Infra |
| `config`、`whitelist` | WebClient、异常处理、Token 密钥和动态白名单配置 |
| `controller` | `/swagger-ui-config` OpenAPI 文档目录 |
| `token`、`codec`、`utils` | Token 密钥、RFC 3986 编码、摘要和认证方案工具 |
| `model`、`enums`、`exception` | 路由、主体、缓存、事件、错误码和统一异常响应模型 |
| `components` | 可选 Kafka 请求/响应事件发送 |

`Application` 是唯一组合根。模块间共享 Reactor 上下文中的 `EdgePrincipalContext`；过滤器不得通过静态可变状态共享请求身份。
