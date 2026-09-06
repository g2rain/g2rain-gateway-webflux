# 依赖与协作

| 协作者 | 网关使用的事实 | 边界 |
| --- | --- | --- |
| `g2rain-basis` | 路由定义、服务注册、API 权限、Passport 权限、名称映射、静态访问令牌上下文 | Basis 是主数据所有者，网关只缓存和执行 |
| `g2rain-infra` | 国际化错误消息 | Infra 维护消息事实，网关用于错误响应本地化 |
| `g2rain-iam` | JWT、DPoP、客户端与密钥协议 | IAM 签发和定义身份语义，网关验证业务入口请求 |
| `g2rain-syncer` | 路由、权限、名称、令牌和内部路由变化消息 | 消息用于刷新内存状态，不替代启动时全量加载 |
| `g2rain-common` | Result、异常、Token 载荷、主体头与同步抽象 | 公共契约变化需要兼容性评估 |
| 下游服务 | 经验证的请求与主体上下文 | 服务仍负责业务、租户、对象和数据权限 |

应用通过 Nacos 完成配置加载、注册与服务发现，通过负载均衡 WebClient 访问 `g2rain-basis` 和 `g2rain-infra`。Redis 是当前 Spring Cloud Stream 默认 binder；Kafka 只在开关启用时作为日志发送目标。
