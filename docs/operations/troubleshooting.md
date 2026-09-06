# 故障排查

| 现象 | 优先检查 |
| --- | --- |
| 启动阶段路由加载失败 | Nacos 发现、Basis 可达性、`/resource_api/route_definitions` 与服务注册数据 |
| 路由新增未及时生效 | Redis binder、`g2rain-syncer` destination、`API_ROUTE` 消息和约 1 秒 create 聚合窗口 |
| 请求返回未认证 | Authorization 方案、JWT 有效期/密钥、API Key 状态、白名单和过滤器顺序 |
| DPoP 或签名失败 | Proof、请求方法/URI、规范化 query、body 字节、摘要算法和密钥轮换 |
| 请求返回无权限 | 路由 ID 匹配、Passport 默认权限、User 权限缓存及 Basis 权限事实 |
| 下游身份缺失 | `PrincipalForwardFilter` 是否执行、请求级主体上下文和可信网络边界 |
| Swagger 目录为空 | Basis 服务注册的 route prefix，以及 `/swagger-ui-config` 调用链 |
| 错误消息缺失 | Infra 可达性和国际化错误消息加载 |
| JaCoCo 无报告 | Surefire `argLine` 与 JaCoCo agent 参数合并方式；当前测试会跳过报告 |
| 镜像可启动但服务不可访问 | `SERVER_PORT` 与 Dockerfile `EXPOSE 8080` 是否一致 |

排查日志时不要复制 Authorization、Cookie、DPoP、API Key 或敏感正文到 Issue。
