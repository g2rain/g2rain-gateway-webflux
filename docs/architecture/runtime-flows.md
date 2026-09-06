# 运行流程

## 启动与路由加载

1. Spring Boot 加载本地配置及可选 Nacos 配置。
2. `GatewayRouteLoader` 在 Web 服务器监听前，从 Basis 同时读取 API 路由和服务注册信息。
3. 两类数据合并为内存路由定义，生成 Spring Cloud Gateway 路由并重建匹配索引。
4. 缓存组件加载权限、名称、错误消息和其他入口事实；单项加载失败会记录警告。
5. 服务注册到 Nacos，默认端口为 `8083`。

## 请求过滤器链

| 顺序 | 过滤器 | 作用 |
| --- | --- | --- |
| +0 | `EdgePrincipalContextScopeFilter` | 建立请求级主体上下文 |
| +100 | `CachedBodyFilter` | 缓存可重复读取的请求体与响应体 |
| +200 | `TraceLoggingFilter` | 记录请求与响应信息 |
| +290 | `ApiKeyFilter` | 识别并校验静态 API Key |
| +300 | `GatewayTokenAuthFilter` | 校验登录 JWT；API Key 路径跳过 |
| +400 | `GatewayDPoPAuthFilter` | 校验 DPoP Proof；API Key 路径跳过 |
| +500 | `ApiPermissionFilter` | 按匹配路由检查 Passport/User API 权限 |
| +600 | `SignVerificationFilter` | 校验规范化 query 与 body 摘要 |
| +700 | `PrincipalForwardFilter` | 移除敏感认证头并写入可信主体头 |
| +800 | `ResponseAdjustFilter` | 调整成功 JSON 响应或转化业务错误 |

过滤器偏移值越小越先执行。任何顺序变化都可能改变认证或签名语义。

## 路由与缓存同步

路由 create 消息在约 1 秒窗口内聚合，单条使用 upsert，多条使用增量 refresh；update/delete 在先刷新待处理 create 后即时执行。同步数据源还包括静态访问令牌、默认权限、用户权限、应用名、机构名和内部路由。

## OpenAPI 目录

`GET /swagger-ui-config` 从 Basis 服务注册列表推导下游文档 URL，按名称排序并去重，供 Swagger UI 使用。它反映当前注册事实，不保存下游 OpenAPI 内容。
