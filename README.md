# g2rain-gateway-webflux

## 1. 徽标与状态标识

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-437291?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1.1-586069?logo=spring&logoColor=white)](https://spring.io/projects/spring-cloud)
[![Maven](https://img.shields.io/badge/build-Maven-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org/)

## 2. 项目简介

`g2rain-gateway-webflux` 是 G2rain 平台边缘入口层的 WebFlux 网关实现，负责统一接入、动态路由、边缘认证、权限控制、请求签名校验、主体透传、响应治理与 OpenAPI 聚合。

## 3. 平台定位

在 G2rain“企业级 AI 原生开源 SaaS 平台”体系中，`g2rain-gateway-webflux` 位于边缘入口层，是平台 API 请求进入内部服务体系的统一入口实现之一。

它主要服务以下场景：
- 为主壳、子应用与外部系统提供响应式统一入口与路由分发
- 为平台统一身份链路提供 JWT、DPoP、API Key 与签名校验落点
- 为下游服务提供主体透传、敏感头处理与响应补全能力
- 为平台联调与服务发现提供 OpenAPI 聚合与动态文档目录

它与 `g2rain-infra`、`g2rain-basis`、`g2rain-iam`、`g2rain-main-shell` 协同，共同构成平台统一接入、安全与交互链路。

## 4. 核心能力

本章回答“这个仓库在平台里提供什么能力、解决什么问题”。

- 动态路由加载与内存刷新能力：解决路由定义如何在运行期被加载、刷新并稳定匹配的问题，通过 `GatewayRouteLoader`、`MemoryRouteRepository`、`RouteMatchHolder` 把平台路由保持在响应式网关内存中。
- 路由增量同步能力：解决路由变更如何不用重启网关即时生效的问题，通过 `RouteSync` 订阅 `g2rain-syncer` 消息并执行单条路由增删改。
- 多层边缘安全能力：解决入口层对请求进行统一身份与完整性校验的问题，通过 `GatewayTokenAuthFilter`、`GatewayDPoPAuthFilter`、`ApiKeyFilter`、`SignVerificationFilter` 形成多层安全检查链。
- 主体透传与响应治理能力：解决下游服务如何拿到统一身份上下文以及响应如何统一调整的问题，通过 `EdgePrincipalContextScopeFilter`、`PrincipalForwardFilter`、`ResponseAdjustFilter` 完成主体透传与响应补全。
- 入口日志与文档聚合能力：解决边缘入口调试与多服务接口查看的问题，通过 `TraceLoggingFilter` 与 `OpenApiController` 提供链路日志和 Swagger UI 配置聚合。
- 白名单与扩展治理能力：解决不同过滤器在接入场景下如何灵活配置放行的问题，通过 `GatewayWhiteList` 与 `WhiteListResolver` 提供统一白名单解析能力。

## 5. 技术栈

- 语言与运行时：`Java 25`
- 后端框架：`Spring Boot 4.0.5`、`Spring Cloud 2025.1.1`
- 网关实现：`Spring Cloud Gateway Server WebFlux`
- 响应式基础：`Project Reactor`、`WebFlux`
- 服务治理：`Nacos Discovery`、`Nacos Config`
- 远程调用：`WebClient`、`LoadBalancer`
- 缓存与同步：`Caffeine`、`Spring Cloud Stream`（Redis Binder）
- 安全与签名：`Nimbus JOSE JWT`、DPoP、请求摘要签名
- 可观测：`Actuator`、`OpenTelemetry`、`Micrometer Tracing`
- 构建与交付：`Maven`、`Jib`、`Dockerfile`、`build.sh`

## 6. 快速开始

### 环境要求

- `JDK 25`
- `Maven 3.9+`
- 可用的 `Nacos`
- 可用的 `Redis`
- 可访问的 `g2rain-infra` 与 `g2rain-basis`

### 关键配置

当前仓库关键运行配置主要来自 `src/main/resources/application.yml` 与 Nacos 配置中心。

| 变量名 | 说明 | 典型用途 |
| --- | --- | --- |
| `SERVER_PORT` | 服务端口 | 默认 `8083` |
| `SPRING_PROFILES_ACTIVE` | 启动环境 | 区分 `dev` 等 profile |
| `NACOS_SERVER_ADDR` | Nacos 地址 | 服务发现与配置中心 |
| `SPRING_CLOUD_NACOS_DISCOVERY_*` | 注册中心认证与命名空间 | 服务注册 |
| `SPRING_CLOUD_NACOS_CONFIG_*` | 配置中心认证与命名空间 | 外部配置拉取 |
| `SPRING_KAFKA_ENABLED` | 是否启用 Kafka | 日志等可选扩展 |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka 地址 | 启用 Kafka 时生效 |

建议：
- `spring.application.name` 为 `g2rain-gateway`，排查 Nacos 注册列表时要注意与仓库名不同。
- `gateway-white-list` 应与各过滤器的接入策略一并维护。
- `Dockerfile` 默认暴露 `8080`，实际监听端口仍以运行配置为准。

### 本地构建

```bash
mvn clean package -DskipTests
```

### 本地运行

```bash
mvn spring-boot:run
```

或：

```bash
java -jar target/g2rain-gateway-webflux-1.0.0.jar
```

### 镜像构建

```bash
./build.sh
./build.sh latest
```

或：

```bash
mvn clean compile jib:dockerBuild -DskipTests=true
```

## 7. 项目结构

本章回答“代码与模块是如何组织的、排查和扩展时应该先看哪里”。

```text
g2rain-gateway-webflux/
├── build.sh
├── Dockerfile
├── pom.xml
└── src/
    ├── main/java/com/g2rain/gateway/
    │   ├── route
    │   ├── filters
    │   ├── cache
    │   ├── client
    │   ├── matcher
    │   ├── config
    │   ├── whitelist
    │   ├── controller
    │   ├── token
    │   └── utils
    ├── main/resources/
    └── test/java/com/g2rain/gateway/
```

### 结构说明

- `route`：承载动态路由加载、仓库、匹配上下文与内存刷新能力。
- `filters`：承载响应式 `GlobalFilter` 链，是 WebFlux 网关的核心治理入口。
- `cache`：承载路由同步与侧车缓存逻辑。
- `client`：承载对 `g2rain-infra`、`g2rain-basis` 的 WebClient 协作接口。
- `matcher`：承载方法与路径规则编译、匹配能力。
- `config` / `whitelist`：承载白名单、密钥、运行时与过滤器配置。
- `controller`：承载 OpenAPI 聚合入口。
- `test`：覆盖 `filters`、`route`、`model`、`codec` 等核心模块测试。

### 代码查阅指引

- 查看动态路由加载与刷新时，优先看 `GatewayRouteLoader`、`MemoryRouteRepository`、`RouteMatchHolder`。
- 查看边缘安全链时，优先看 `GatewayTokenAuthFilter`、`GatewayDPoPAuthFilter`、`ApiKeyFilter`、`SignVerificationFilter`。
- 查看主体透传与响应补全时，优先看 `EdgePrincipalContextScopeFilter`、`PrincipalForwardFilter`、`ResponseAdjustFilter`。
- 查看路由增量同步时，优先看 `RouteSync`。
- 查看回源客户端时，优先看 `InfraServiceClient`、`BasisServiceClient`。
- 查看文档聚合时，优先看 `OpenApiController`。

## 8. 核心业务流程

本章回答“这些能力在运行时是如何串起来工作的”。

#### 1. 动态路由初始化主线

- 服务启动后，`GatewayRouteLoader` 会通过 `InfraServiceClient` 从 `g2rain-infra` 拉取平台定义的路由清单。
- 路由定义写入 `MemoryRouteRepository`，并发布 `RefreshRoutesEvent`。
- `RouteMatchHolder` 维护运行时命中所需的匹配上下文。
- 这一主线解决的是响应式网关如何在不依赖本地数据库的前提下稳定持有平台路由的问题。

#### 2. 路由增量同步主线

- `RouteSync` 订阅 `g2rain-syncer` 中的路由变更消息。
- 当单条路由发生新增、更新或删除时，系统只增量处理该条定义并刷新内存仓库。
- 这一主线解决的是平台路由如何在运行期快速生效的问题。

#### 3. 响应式全局过滤主线

- 请求进入后，依次经过 `CachedBodyFilter`、日志、JWT、DPoP、权限、签名、透传、响应治理等 `GlobalFilter`。
- 整个链路基于 Reactor 与 `ServerWebExchange` 运行，不再区分 Servlet 级和路由级两套过滤机制。
- 这一主线是 WebFlux 实现区别于 WebMVC 实现的重要边界。

#### 4. 边缘安全与主体透传主线

- `GatewayTokenAuthFilter` 负责 JWT 校验，`GatewayDPoPAuthFilter` 负责 DPoP 校验。
- `ApiKeyFilter`、`SignVerificationFilter` 负责 API Key 与请求签名完整性校验。
- 校验通过后，主体上下文写入 `EdgePrincipalContext`，并由 `PrincipalForwardFilter` 透传到下游。
- 这一主线解决的是边缘入口如何统一承接平台身份与安全链路的问题。

#### 5. 文档聚合与服务目录主线

- `OpenApiController` 会基于当前路由与服务注册列表生成 Swagger UI 配置。
- 网关据此为多服务提供统一文档切换入口。
- 这一主线解决的是多服务联调时文档入口分散的问题。

## 9. 常用命令

```bash
mvn clean package
mvn spring-boot:run
mvn test
mvn jacoco:report
./build.sh
./build.sh latest
mvn clean compile jib:dockerBuild -DskipTests=true
```

## 10. 质量与测试

- `pom.xml` 已集成 Enforcer、Checkstyle、PMD、SpotBugs、JaCoCo。
- 当前已识别 `filters`、`route`、`model`、`codec` 等相关测试。
- 建议后续继续补齐 JWT/DPoP、签名校验、OpenAPI 聚合等关键链路测试。
- `ApiPermissionFilter` 当前仍是直通骨架，测试与文档需明确这是预留扩展点。

## 11. 相关仓库

- `g2rain-infra`：路由定义与部分基础数据权威来源
- `g2rain-basis`：业务支撑 API 与主体数据协作来源
- `g2rain-iam`：统一身份认证与令牌服务
- `g2rain-main-shell`：主壳与统一交互入口
- `g2rain-gateway-webmvc`：边缘入口层的 Servlet / RouterFunction 实现

## 12. 使用建议

- 适合作为平台统一边缘入口独立部署，而不是与业务服务混合部署。
- 适合需要纯响应式过滤链与 `GlobalFilter` 治理模型的场景。
- 生产环境请重点核对白名单、签名策略、JWT 密钥来源与下游服务发现配置。
- 若需要增强 `ApiPermissionFilter`，应同步补齐规则来源与测试覆盖。

## 13. 贡献指南

欢迎通过文档改进、Issue 反馈、测试补充、代码优化、功能增强等形式参与贡献。

建议流程：
1. Fork 本仓库
2. 创建特性分支
3. 提交修改
4. 推送分支
5. 提交 Pull Request

提交前请尽量确保：
- 遵循现有技术栈与代码规范
- 更新相关文档
- 如涉及认证、签名、路由刷新语义，补充必要测试

## 14. 许可证

本项目基于 [Apache 2.0许可证](LICENSE) 开源。

## 15. 联系我们

- **站点**: https://www.g2rain.com/
- **Issues**: [GitHub Issues](https://github.com/g2rain/g2rain/issues)
- **讨论**: [GitHub Discussions](https://github.com/g2rain/g2rain/discussions)
- **邮箱**: g2rain_developer@163.com

## 16. 致谢

感谢所有为这个项目做出贡献的开发者们。

如果这个项目对您有帮助，欢迎 Star 支持。
