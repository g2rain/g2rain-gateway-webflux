# g2rain-gateway-webflux Agent Instructions

本文件是 AI Coding 在本项目中的执行入口。项目事实位于 `docs/project.yaml`，文档入口位于 `docs/index.md`。

## 项目定位

- 类型：Spring Cloud Gateway WebFlux 平台统一入口
- 架构基线：中央 `gateway-service 1.0.0-draft`
- 实现类型：`webflux`
- 固定试点分支：`feature/g2rain-architectur-init`；目标正式快照 `architecture-v1.3.0`
- 采用状态：pilot，尚未转为正式 adopted
- 当前验证：`mvn test` 通过，121 个测试成功
- 已知偏差：`docs/architecture/deviations.md`

本项目负责动态路由、入口认证与请求完整性校验、API 权限、可信主体透传、响应调整及 OpenAPI 目录聚合。Basis 提供路由、权限、静态访问令牌上下文和服务注册事实；Infra 提供国际化错误消息；IAM 定义登录 Token 契约；下游领域服务仍负责业务与数据级授权。

## 开始前

读取 `docs/project.yaml`、中央 Profile、项目接入说明、架构偏差、过滤器链、测试规范、安全边界以及任务对应需求。

## 执行规则

- 保持 WebFlux/Reactor 链路非阻塞；在请求路径中引入 `block()` 必须有明确设计和验证。
- 全局过滤器顺序是安全协议的一部分；调整顺序、白名单、认证分流、主体头或签名算法时必须同步文档并增加成功/失败测试。
- 静态 API Key 与 JWT/DPoP 是两条不同认证路径，不得让一种凭据意外绕过另一条路径之外的 API 权限。
- 只信任网关自身验证后写入的主体上下文；转发前移除外部传入的认证和敏感主体头。
- Gateway 的 API 权限不能替代领域服务的租户、对象和数据级授权。
- 路由与权限事实由 Basis 及同步消息提供；改变数据源、消息类型或缓存语义时评估 `g2rain-basis`、`g2rain-syncer` 和下游服务兼容性。
- 请求日志不得输出 Token、Cookie、DPoP、API Key、密码、Secret 或敏感请求/响应体；当前实现存在已登记风险。
- 不在配置、文档、测试或日志中新增真实凭据；开发默认凭据必须由部署环境覆盖。
- 不修改 README；README 更新使用单独的 `generate` 命令。

## 完成前

至少运行 `mvn test`。按变更范围补充过滤器、路由、缓存、错误处理和客户端契约测试；涉及运行时配置时验证 Nacos、Basis、Infra、Redis binder 及下游服务联调，并按 `docs/development/definition-of-done.md` 报告未验证项。
