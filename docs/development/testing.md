# 测试

## 已验证状态

2026-09-06 执行 `mvn test`：

- BUILD SUCCESS
- 121 个测试通过
- 0 failure、0 error、0 skipped
- Maven Enforcer 的 Java 版本与依赖上界规则通过
- JaCoCo 因缺少执行数据跳过报告

异常处理测试会故意产生 ERROR 级堆栈日志，这些日志不代表测试失败。

## 当前覆盖范围

测试覆盖编码/摘要工具、路由仓库与加载、请求/响应体缓存、DPoP、签名、主体透传、响应调整、日志、主体上下文、错误处理和部分模型。

未发现 `ApiKeyFilter`、`GatewayTokenAuthFilter`、`ApiPermissionFilter`、`EdgePrincipalContextScopeFilter`、`RouteSync`、`OpenApiController` 的直接测试类。外部 Nacos、Basis、Infra、Redis/Kafka 和下游服务未在本轮联调。

## 变更验证矩阵

| 变更 | 最低验证 |
| --- | --- |
| 任意 Java 代码 | `mvn test` |
| 过滤器或白名单 | 顺序、命中/未命中、无凭据、无效凭据、越权和异常响应测试 |
| API Key | 有效、错误、撤销、格式边界、缓存命中/失效和权限测试 |
| JWT/DPoP/签名 | issuer/audience/kid/过期、重放、摘要不符和密钥轮换测试 |
| 路由与同步 | 启动全量、增量 create/update/delete、重复/乱序消息和并发刷新测试 |
| 主体透传 | 外部伪造头被清理、可信字段正确写入、下游拒绝路径 |
| 日志 | Token/Cookie/DPoP/API Key/密码/Secret 与敏感正文脱敏测试 |
| 配置或部署 | Nacos、Basis、Infra、Redis binder、容器和真实下游联调 |

发布前还应运行 `mvn checkstyle:check`、`mvn pmd:check` 和 `mvn spotbugs:check`；本次初始化未执行这些目标。
