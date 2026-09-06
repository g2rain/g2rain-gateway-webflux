# 中央 Gateway Service Profile 接入说明

当前版本：`gateway-service 1.0.0-draft`　实现类型：`webflux`　状态：pilot

本项目已登记为中央 [`gateway-service`](https://github.com/g2rain/g2rain/tree/feature/g2rain-architectur-init/docs/architecture/profiles/gateway-service) 首个 WebFlux 试点。`g2rain-gateway-webmvc` 后续按同一 Profile 接入并记录 Servlet 实现差异。Draft 固定到 `feature/g2rain-architectur-init`，目标正式版本为 `1.0.0`，目标快照为 `architecture-v1.3.0`。

## 本项目落地

1. 网关是平台统一入口，只承接路由、安全过滤、可信主体透传、响应适配和入口观测，不拥有领域数据。
2. WebFlux 请求链保持非阻塞；阻塞调用不得进入高并发请求路径。
3. 全局过滤器顺序、白名单语义、凭据分流和主体头属于安全契约，变更必须测试成功与失败路径。
4. 静态 API Key 与 JWT/DPoP 认证互斥分流，但两者都必须经过适用的路由与 API 权限边界。
5. Gateway 只能建立入口身份和 API 访问上下文；下游服务继续执行租户、对象、状态和数据级授权。
6. 路由、服务注册、API 权限及令牌上下文从数据所有者加载，通过同步消息刷新；网关内存缓存不是主数据源。
7. 外部请求中的身份头默认不可信，只能转发网关验证后重新构造的主体信息。
8. Token、Cookie、API Key、DPoP、密钥、密码和敏感正文不得进入代码仓库或普通日志。
9. 路由、安全协议、配置键、缓存数据源和部署方式变化必须同步项目文档。
10. 最低验证为 `mvn test`；发布还需真实基础设施联调和镜像验证。

中央 Profile 已吸收这些公共规则。本文件保留项目落地摘要；更细的过滤器顺序、依赖、配置和风险继续由项目专题文档维护，不复制中央规范全文。

## 转正式条件

- 关闭 `docs/architecture/deviations.md` 中的开发默认凭据和敏感请求日志高风险项；
- 修复 JaCoCo 执行数据并补充关键安全过滤器负向测试；
- 完成 Nacos、Basis、Infra、同步消息、IAM 契约和真实下游服务联调；
- 中央变更合并并发布 `architecture-v1.3.0`；
- 项目再显式将版本更新为 `1.0.0`、状态更新为 `adopted`。
