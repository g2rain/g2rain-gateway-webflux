# 网关契约

## 公开端点

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/swagger-ui-config` | 从 Basis 服务注册信息生成 Swagger UI `urls` 配置 |
| GET | `/actuator/health` | 健康检查 |
| GET | `/actuator/info` | 应用信息 |

其余业务路径来自运行时动态路由，不在本仓库静态声明。

## 上游凭据

- 静态 API Key：由 `ApiKeyFilter` 识别，通过摘要缓存和 Basis 上下文解析完成认证。
- 登录 Token：由 `GatewayTokenAuthFilter` 校验 JWT。
- DPoP：由 `GatewayDPoPAuthFilter` 校验 Proof，并把算法与预期摘要交给签名过滤器。
- 白名单：可按全局或过滤器配置跳过指定逻辑；变更属于安全配置变更。

不要在文档、日志或问题单中粘贴真实凭据格式样例。

## 下游主体契约

`PrincipalForwardFilter` 在认证后构造下游主体头，并移除 `Authorization`、`DPoP` 等敏感认证头。下游只能在受控网络和明确网关信任边界内接受这些主体头，且仍需执行领域和数据级授权。

## 控制面依赖

Basis 提供路由定义、服务注册、应用 API、Passport/User 权限、名称映射和静态访问令牌上下文；Infra 提供错误消息。上述路径是内部协作契约，变更需同步数据所有者和网关测试。
