# 架构偏差与已知风险

本项目以 `webflux` 实现类型试点中央 `gateway-service 1.0.0-draft`。以下事实来自当前仓库；中央登记不代表这些偏差已经解决。

| 偏差或风险 | 证据 | 影响 | 状态 |
| --- | --- | --- | --- |
| 中央 Profile 尚未正式发布 | Draft 固定到 `feature/g2rain-architectur-init`，目标 Tag 为 `architecture-v1.3.0` | 当前只能标记 pilot，不能宣称 adopted | 等待评审与发布 |
| 开发默认凭据保存在版本化配置 | `application.yml` 的 Nacos用户名/密码默认值 | 未覆盖时可能使用公开可知凭据 | 高风险，待迁移 |
| 请求日志未脱敏 | `TraceLoggingFilter` 记录完整请求头、query、表单、JSON body 和成功响应体 | 可能泄露 Token、Cookie、DPoP、API Key、密码或业务敏感数据 | 高风险，待修复 |
| JaCoCo 未生成执行数据 | `mvn test` 显示 missing execution data；Surefire 固定 `argLine` | 121 个测试通过但没有覆盖率报告 | 待修复 |
| 部分安全链缺少直接测试 | 未发现 `ApiKeyFilter`、`GatewayTokenAuthFilter`、`ApiPermissionFilter`、`EdgePrincipalContextScopeFilter` 的测试类 | 关键分流和越权回归信心不足 | 待补充 |
| 路由控制面说明漂移 | README 描述从 Infra 加载路由，源码实际通过 `BasisServiceClient` 加载 | 运维和排障可能定位错误服务 | README 待单独 generate |
| 启动示例残留 IAM 包名 | `application.yml` 首行注释 | 复制命令会产生错误认知 | 待修复 |
| 备份 Java 文件被跟踪 | `filters/SignFilter.java.bak` | 旧安全实现可能造成维护混淆 | 待清理 |
| 镜像脚本跳过测试 | `build.sh` 使用 `-DskipTests=true` | 镜像成功不代表测试通过 | 发布流程需先测试 |
| 外部集成未验证 | 本次只执行 `mvn test` | Nacos、Basis、Infra、同步消息及真实下游链路仍未知 | 待环境验证 |
