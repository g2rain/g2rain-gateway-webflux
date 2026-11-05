<p align="center">
  <img src="https://github.com/g2rain.png" alt="G2Rain" width="180" />
</p>

# g2rain-gateway-webflux

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-437291?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.2-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1.4-586069?logo=spring&logoColor=white)](https://spring.io/projects/spring-cloud)
[![Maven](https://img.shields.io/badge/build-Maven-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org/)

G2rain（谷雨）开源智能体平台中的响应式统一入口网关实现，不代表或单独交付整个平台能力。

统一网关入口服务，从 Basis 加载并刷新动态路由定义；在过滤器链中执行令牌、DPoP 与签名校验；向下游透传主体上下文与身份信息；通过同步消息增量刷新路由与相关缓存。

[官网](https://www.g2rain.com) · [完整文档](docs/index.md) · [中央架构 Profile](https://github.com/g2rain/g2rain/tree/feature/g2rain-architectur-init/docs/architecture/profiles/gateway-service) · [架构说明](docs/architecture/overview.md) · [代码规范](docs/development/code-conventions.md) · [Issues](https://github.com/g2rain/g2rain/issues) · [Discussions](https://github.com/g2rain/g2rain/discussions)

## 目录

- 项目简介
- 平台定位
- 业务域说明
- 功能概览
- 使用场景
- 核心流程
- 流程图
- 技术栈
- 环境要求
- 快速开始
- 配置说明
- 构建与镜像
- GHCR 镜像发布
- 代码质量与测试
- 接口示例
- 安全说明
- 与关联仓库的关系
- 模块说明
- 职责边界
- 主要 HTTP 路径
- 常见问题
- 关联仓库
- 参与贡献
- 许可证
- 联系我们
- 致谢

## 项目简介

统一网关入口服务，从 Basis 加载并刷新动态路由定义；在过滤器链中执行令牌、DPoP 与签名校验；向下游透传主体上下文与身份信息；通过同步消息增量刷新路由与相关缓存。

## 平台定位

该仓库位于 G2rain 后端平台的入口层，承担网关路由编排、请求校验和下游转发。它以 WebFlux 实现类型试点中央 `gateway-service 1.0.0-draft`：Basis 提供路由、权限、静态访问令牌上下文和服务注册事实，Infra 提供国际化错误消息，IAM 定义登录 Token 契约；本组件不拥有这些主数据，也不替代下游服务的业务、租户或数据级授权。

## 业务域说明

该仓库聚焦于 `网关路由与流量治理`。

核心对象包括：
- 访问令牌
- 路由
- 权限
- 应用
- 主体

主要流程包括：
- 启动时路由加载与内存注册流程
- 路由增量同步与刷新流程
- 网关过滤器链鉴权与签名校验流程
- 主体透传与统一响应调整流程

## 功能概览

| 能力 | 说明 |
| --- | --- |
| 动态路由加载 | 启动时从 Basis 加载路由定义，写入内存路由仓库并触发刷新。 |
| 路由增量同步 | 订阅同步消息，对路由定义执行增量更新并刷新网关内存状态。 |
| 网关安全过滤 | 在入口过滤器链中执行令牌校验、DPoP 校验与请求签名校验。 |
| 主体上下文透传 | 将认证后的主体信息透传给下游服务，保持链路中的身份上下文一致。 |
| 网关文档聚合 | 基于当前路由视图聚合并暴露下游服务的 OpenAPI 文档入口。 |
| JWT 与密钥管理 | 基于 Nimbus JOSE JWT 处理令牌与密钥，支持从配置中心加载并切换签名密钥。 |
| 会话与缓存 | 结合 Redis 或同步组件维护路由、鉴权与入口侧缓存状态。 |
| 可观测性 | 暴露 Actuator 健康与信息端点，并引入追踪能力以便接入平台观测体系。 |

## 项目文档

| 场景 | 入口 |
| --- | --- |
| 文档首页与架构 | [docs/index.md](docs/index.md) · [架构总览](docs/architecture/overview.md) · [中央接入与偏差](docs/architecture/local-baseline.md) |
| 本地开发与测试 | [本地开发](docs/development/local-development.md) · [测试策略](docs/development/testing.md) · [完成定义](docs/development/definition-of-done.md) |
| 运行与安全 | [配置](docs/operations/configuration.md) · [部署与 GHCR](docs/operations/deployment.md) · [安全边界](docs/security/security-boundaries.md) · [故障排查](docs/operations/troubleshooting.md) |
| 发布记录 | [v1.0.0](docs/releases/v1.0.0.md) |
| 需求与协作 | [需求索引](docs/requirements/README.md) · [社区协作](docs/community.md) |

## 使用场景

| 场景 | 说明 |
| --- | --- |
| 统一网关入口 | 当平台需要统一承接前端请求、匹配路由并转发到下游服务时使用。 |
| 入口安全控制 | 当请求进入业务服务前需要统一执行令牌、DPoP、签名或 API 权限校验时使用。 |
| 动态路由治理 | 当网关路由需要从 Basis 加载，并根据同步消息动态刷新时使用。 |

## 核心流程

| 流程 | 关键步骤 | 代码线索 |
| --- | --- | --- |
| 动态路由加载 | 服务启动后从 Basis 读取路由定义 → 写入内存路由仓库 → 刷新网关运行时路由视图 | MemoryRouteLoader、BasisServiceClient、GatewayRouteLoader |
| 入口过滤器链 | 请求进入网关过滤器链 → 执行令牌、DPoP、签名或 API 权限校验 → 校验通过后透传主体上下文并转发下游服务 | GatewayTokenAuthFilter、GatewayDPoPAuthFilter、SignVerificationFilter、PrincipalForwardFilter |

## 流程图

```mermaid
flowchart TD
  A[前端或客户端请求] --> B[g2rain-gateway-webflux]
  B --> C[加载并匹配动态路由]
  C --> D[令牌/DPoP/签名/API 权限校验]
  D --> E[主体上下文透传]
  E --> F[转发下游平台服务]
  G[g2rain-basis 路由定义] --> C
  H[g2rain-syncer 同步消息] --> C
```

## 技术栈

| 类别 | 说明 |
| --- | --- |
| 运行时 | Java 25、Spring Boot 4.1.2、Spring Cloud 2025.1.4 |
| 安全与令牌 | Nimbus JOSE JWT |
| 基础设施 | Redis、Nacos、Spring Cloud LoadBalancer |
| 协同服务 | g2rain-basis、g2rain-infra、g2rain-iam |
| 其他 | SpringDoc OpenAPI、Micrometer Tracing、OpenTelemetry、Lombok |

## 环境要求

- JDK 25+
- Maven 3.9+
- Redis
- Nacos
- 可访问的 g2rain-infra 服务
- 可访问的 g2rain-basis 服务

## 快速开始

| 步骤 | 命令或位置 | 说明 |
| --- | --- | --- |
| 准备运行环境 | JDK 25+、Maven 3.9+、Redis、Nacos | 后端服务启动前需要准备 Java 构建环境和平台依赖的基础设施。 |
| 调整配置 | `src/main/resources/application.yml` | 按需设置 SERVER_PORT、SPRING_PROFILES_ACTIVE、NACOS_SERVER_ADDR 等环境变量。 网关还需要保证 g2rain-infra、g2rain-basis 等下游服务可访问。 |
| 构建项目 | `mvn clean package` | 执行 Maven 构建并生成可执行 Jar。 |
| 本地启动 | `mvn spring-boot:run` | 以当前 profile 启动服务，默认端口以 application.yml 中的 SERVER_PORT 为准。 |
| 验证服务 | `GET /actuator/health` | 服务启动后可通过健康检查确认运行状态。 |

版本号以项目构建配置为准，当前识别为 `1.0.0`。

## 配置说明

### 运行配置

| 配置项 | 说明 |
| --- | --- |
| `SERVER_PORT` | 默认 8083 |
| `SPRING_PROFILES_ACTIVE` | 默认 profile 为 dev |

### 平台集成配置

| 配置项 | 说明 |
| --- | --- |
| `NACOS_SERVER_ADDR` | 默认指向 127.0.0.1:8848，用于服务发现与配置中心连接 |
| `spring.cloud.stream.bindings.input.destination` | 订阅 g2rain-syncer 消息通道，用于网关路由等状态同步。 |

### 敏感配置

| 配置项 | 说明 |
| --- | --- |
| `spring.config.import` | 可选导入 g2rain-token-keypair.yml，用于加载令牌密钥等敏感配置 |

### 观测配置

| 配置项 | 说明 |
| --- | --- |
| `management.endpoints.web.exposure.include` | 默认暴露 health、info 等基础观测端点 |

### 消息配置

| 配置项 | 说明 |
| --- | --- |
| `SPRING_KAFKA_ENABLED` | 控制 Kafka 相关能力是否启用，默认关闭。 |

## 构建与镜像

| 目标 | 命令 | 产物 | 说明 |
| --- | --- | --- | --- |
| 可执行 Jar | `mvn clean package` | `g2rain-gateway-webflux-1.0.0.jar` | 执行 Maven 标准构建，生成服务可执行产物。 |
| 本地运行 | `mvn spring-boot:run` | 本地 Spring Boot 进程 | 使用当前 profile 启动服务，便于本地联调。 |
| 容器镜像 | `./build.sh 1.0.0` | 本地 Docker 镜像 | 使用与 CI 相同的 Dockerfile，先打包 Jar，默认镜像地址为 GHCR。 |
| Dockerfile 镜像 | `docker build --build-arg JAR_FILE=target/g2rain-gateway-webflux-1.0.0.jar --build-arg BUILD_VERSION=1.0.0 --build-arg VCS_REF=$(git rev-parse HEAD) -t ghcr.io/g2rain/g2rain-gateway-webflux:1.0.0 .` | 本地 Docker 镜像 | 先执行 Maven 打包；容器声明端口 `8083`。 |

## GHCR 镜像发布

- 镜像地址：`ghcr.io/g2rain/g2rain-gateway-webflux`。
- 仅在推送已进入 `main` 的稳定 Git tag（`vMAJOR.MINOR.PATCH`）时发布；工作流同时校验 Maven 项目版本及 `docs/releases/v<版本>.md`。
- 成功时推送 `<版本>` 与 `sha-<完整提交 SHA>` 两个标签；不发布 `latest`。
- 拉取示例：`docker pull ghcr.io/g2rain/g2rain-gateway-webflux:1.0.0`。匿名拉取须在首次成功推送后将包设为 Public；完整的权限、构建、运行与发布说明见[部署文档](docs/operations/deployment.md)。
- 本仓库是单模块可执行网关，不含可独立消费的 API 模块；因此不发布 GitHub Maven Packages，也不提供 `publish-maven.yml`。

## 代码质量与测试

| 检查项 | 命令 | 说明 |
| --- | --- | --- |
| Maven Enforcer | `mvn validate` | 约束 JDK 版本、Maven 版本与依赖规则。 |
| Checkstyle | `mvn checkstyle:check` | 检查 Java 代码风格与组织规范。 |
| PMD | `mvn pmd:check` | 执行静态规则检查，识别潜在代码问题。 |
| SpotBugs | `mvn spotbugs:check` | 识别潜在缺陷和风险代码。 |
| JaCoCo | `mvn test jacoco:report` | 运行测试并生成覆盖率报告。 |

## 接口示例

| 示例 | 方法 | 路径 | 用途 | 调用示例 |
| --- | --- | --- | --- | --- |
| 查看聚合文档 | GET | `/swagger-ui-config` | 查看网关聚合后的 OpenAPI 文档配置。 | `curl http://localhost:8083/swagger-ui-config` |

## 安全说明

| 主题 | 说明 |
| --- | --- |
| 入口统一校验 | 网关承担入口令牌、DPoP、签名和权限过滤职责，下游服务不应假设未认证请求可信。 |
| 主体透传 | 主体上下文透传应只发生在可信链路内，生产环境需要配合网关、服务发现和网络边界控制。 |
| 密钥配置 | 令牌密钥和签名材料应通过配置中心或安全配置系统维护，不应写入公开仓库。 |

## 与关联仓库的关系

本仓库作为平台统一入口网关：Basis 是路由、权限、服务注册与静态访问令牌上下文的事实源；Infra 提供国际化错误消息；IAM 提供登录 Token 与 DPoP 契约；下游领域服务负责业务规则、租户和数据级授权。

## 模块说明

| 模块 | 职责说明 | 代码线索 |
| --- | --- | --- |
| 路由加载与同步 | 从 Basis 加载路由定义，并根据同步消息刷新网关内存路由。 | GatewayRouteLoader、BasisServiceClient、RouteSync |
| 入口安全过滤 | 在请求进入下游服务前执行令牌、DPoP、签名与 API 权限校验。 | GatewayTokenAuthFilter、GatewayDPoPAuthFilter、SignVerificationFilter、ApiPermissionFilter |
| 主体上下文透传 | 将认证后的主体上下文、身份信息与链路信息透传给下游服务。 | PrincipalForwardFilter、EdgePrincipalContextScopeFilter |
| 网关文档聚合 | 基于网关路由视图聚合并暴露平台服务的 OpenAPI 文档入口。 | OpenApiController |

## 职责边界

该仓库主要负责：
- 负责网关入口层的路由匹配、过滤器编排与下游转发
- 负责网关层认证、签名校验与主体上下文透传
- 负责路由定义加载、缓存刷新与入口观测能力

该仓库默认不负责：
- 不负责具体业务域的核心业务实现
- 不直接作为路由、权限、Token 或业务主数据的权威来源
- 不替代下游服务完成业务处理与持久化职责

## 主要 HTTP 路径

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | /swagger-ui-config | 对外暴露的服务接口 |

## 常见问题

| 问题 | 可能原因 | 处理建议 |
| --- | --- | --- |
| 路由未生效 | g2rain-basis 不可访问或同步消息未到达。 | 检查 Basis 服务、路由配置和 g2rain-syncer 消息通道。 |
| 请求被网关拒绝 | 令牌、DPoP、签名或 API 权限校验不通过。 | 检查请求头、签名材料、令牌有效性和应用权限配置。 |
| Swagger 文档不可见 | 下游服务路由或 OpenAPI 聚合配置不完整。 | 确认路由已加载，并检查下游服务文档端点。 |

## 关联仓库

| 仓库 | 协作关系 |
| --- | --- |
| g2rain-basis | 提供路由、权限、服务注册、静态访问令牌上下文及相关平台主数据事实。 |
| g2rain-common | 复用平台公共规范、通用模型、工具能力或基础依赖约束。 |
| g2rain-infra | 提供国际化错误消息主数据。 |

## 参与贡献

我们欢迎所有形式的贡献：Issue 反馈、文档改进、功能建议与代码提交。

推荐流程：

1. Fork 本仓库。
2. 创建特性分支：`git checkout -b feature/your-feature-name`。
3. 提交更改：`git commit -m "Add some feature"`。
4. 推送分支：`git push origin feature/your-feature-name`。
5. 提交 Pull Request。

代码贡献前请尽量补充必要的测试和文档，并确保构建、测试与静态检查通过。

## 许可证

本项目基于 [Apache License 2.0](LICENSE) 开源。镜像的 `Apache-2.0` OCI 标签描述本项目；镜像内的 JRE 与 Maven 依赖仍分别受其自身许可证和通知要求约束。正式对外分发前，应生成并随制品提供第三方许可证清单或 SBOM。

## 联系我们

- Issues: [GitHub Issues](https://github.com/g2rain/g2rain/issues)
- 讨论: [GitHub Discussions](https://github.com/g2rain/g2rain/discussions)
- 邮箱: g2rain_developer@163.com

## 致谢

感谢所有为 g2rain 项目提交 Issue、代码、文档、建议和使用反馈的开发者们！

