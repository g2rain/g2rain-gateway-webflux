# g2rain-gateway-webflux 工程文档

本目录记录响应式网关的项目事实、运行链路、开发约束、部署与安全边界。本项目以 `webflux` 实现类型试点中央 [`gateway-service 1.0.0-draft`](https://github.com/g2rain/g2rain/tree/feature/g2rain-architectur-init/docs/architecture/profiles/gateway-service)，目标正式快照为 `architecture-v1.3.0`。

## 架构

- [中央 Profile 接入说明](architecture/local-baseline.md)
- [架构总览](architecture/overview.md)
- [模块与职责](architecture/modules.md)
- [依赖与协作](architecture/dependencies.md)
- [运行流程](architecture/runtime-flows.md)
- [架构偏差](architecture/deviations.md)

## 开发

- [本地开发](development/local-development.md)
- [代码约定](development/code-conventions.md)
- [测试](development/testing.md)
- [完成定义](development/definition-of-done.md)
- [Git 工作流](development/git-workflow.md)

## 接口、运行与治理

- [网关契约](api/gateway-contracts.md)
- [配置](operations/configuration.md)
- [部署](operations/deployment.md)
- [故障排查](operations/troubleshooting.md)
- [安全边界](security/security-boundaries.md)
- [需求索引](requirements/README.md)
- [项目决策](decisions/README.md)
- [社区协作](community.md)
