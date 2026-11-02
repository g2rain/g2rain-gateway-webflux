# 部署与 GHCR 发布

## 前提与边界

本服务是 G2rain（谷雨）开源智能体平台中的响应式统一入口网关实现，不是整个平台，也不拥有身份、权限、路由、Token 或领域数据的主事实。它当前以 `webflux` 实现类型试点中央 `gateway-service 1.0.0-draft`；实际部署仍依赖 Nacos、Redis、Basis、Infra、IAM 契约、同步消息和真实下游服务。

## 本地构建与运行

```bash
mvn clean package
java -jar target/g2rain-gateway-webflux-1.0.0.jar
```

这只构建和启动本地 Jar；不等同于容器构建、GHCR 推送、匿名拉取、在线演示或真实部署验证。运行时须通过环境变量、Nacos 或密钥管理服务覆盖 Nacos 地址、用户名、密码、命名空间、Token 密钥及生产配置。

## 本地容器构建与运行

本仓库以 `Dockerfile` 作为容器发布的唯一构建定义；`build.sh` 和 CI 复用其基础镜像、Jar、入口和构建参数。

```bash
mvn clean package
docker build \
  --build-arg JAR_FILE=target/g2rain-gateway-webflux-1.0.0.jar \
  --build-arg BUILD_VERSION=1.0.0 \
  --build-arg VCS_REF=$(git rev-parse HEAD) \
  -t ghcr.io/g2rain/g2rain-gateway-webflux:1.0.0 .
docker run --rm -p 8083:8083 \
  -e NACOS_SERVER_ADDR=nacos.example.internal:8848 \
  -e SPRING_CLOUD_NACOS_DISCOVERY_USERNAME="$NACOS_DISCOVERY_USERNAME" \
  -e SPRING_CLOUD_NACOS_DISCOVERY_PASSWORD="$NACOS_DISCOVERY_PASSWORD" \
  -e SPRING_CLOUD_NACOS_CONFIG_USERNAME="$NACOS_CONFIG_USERNAME" \
  -e SPRING_CLOUD_NACOS_CONFIG_PASSWORD="$NACOS_CONFIG_PASSWORD" \
  ghcr.io/g2rain/g2rain-gateway-webflux:1.0.0
```

或在已完成测试后运行 `./build.sh 1.0.0`。最终镜像基于 `eclipse-temurin:25.0.4.1_1-jre`，包含 `/app/app.jar`，入口为 `java -jar /app/app.jar`，声明端口为 `8083`。OCI 元数据为 source、Apache-2.0 license、title、version 和 revision；其中 `Apache-2.0` 描述本项目，并不改变 JRE 或 Maven 依赖的各自许可证与通知义务。正式对外分发前应生成并随制品提供第三方许可证清单或 SBOM。

`application.yml` 是已跟踪文件，其中包含 Nacos 开发默认用户名/密码；它会被打进 Jar，继而进入 `/app/app.jar`。这不是由 `.dockerignore` 控制的敏感信息边界。共享或生产环境必须以环境变量、Nacos 配置中心或密钥管理服务覆盖这些值；不要把生产 Token、DPoP 私钥、API Key 或密码加入镜像或 Git。

## CI 构建、测试与 GHCR 推送

`.github/workflows/publish-ghcr.yml` 仅在推送稳定 `vMAJOR.MINOR.PATCH` Git tag 时触发；分支推送、Pull Request 和手动触发均不发布。工作流严格校验 tag 格式、tag 提交位于 `origin/main` 历史、同版本发布说明存在且非空、tag 与 Maven `project.version` 一致，然后执行 `mvn -B -ntp clean verify`。

随后工作流用 `GITHUB_TOKEN` 登录 GHCR，使用同一 Dockerfile 构建一次并推送 `ghcr.io/g2rain/g2rain-gateway-webflux:1.0.0` 与 `ghcr.io/g2rain/g2rain-gateway-webflux:sha-<完整提交 SHA>`。不推送 `latest`，不创建 GitHub Release，不部署服务。仅当两个 push 都成功后才写入 Actions Summary；任一失败都会保留错误并令步骤失败。

CI 的 `clean verify` 是 Maven 生命周期中的构建和测试，不是 Docker 运行、匿名拉取、在线演示或外部系统部署验证。

## GHCR 可见性、权限与匿名拉取

首次成功推送后，维护者须在 GitHub Packages 的 **Package settings → Change visibility → Public** 将包设为公开，匿名用户才能拉取：

```bash
docker pull ghcr.io/g2rain/g2rain-gateway-webflux:1.0.0
```

若组织管理员禁止修改可见性，请联系管理员。若已有同名 GHCR 包返回 `denied`，先在 **Package settings → Actions access** 授予 `g2rain/g2rain-gateway-webflux` 写权限，并核对组织 Packages 策略；工作流的 `packages: write` 只是令牌声明，不能单独保证既有包或组织策略允许推送。

## 发布顺序

1. 将版本、Dockerfile、工作流和 `docs/releases/v1.0.0.md` 合入 `main`，并让前置检查通过。
2. 在该 `main` 提交创建并推送 `v1.0.0` tag。
3. 等待 GHCR 工作流完成，确认两个标签均出现后，再按需将包设为 Public。

本仓库只准备发布内容：工作流不会创建 GitHub Release、修改包可见性或部署服务。
