# 部署

## Jar

```bash
mvn clean package
java -jar target/g2rain-gateway-webflux-1.0.0.jar
```

发布环境通过环境或 Nacos 注入配置，避免把凭据和密钥放入镜像。启动后先检查 `/actuator/health`，再验证动态路由、认证失败/成功和一个真实下游请求。

## Jib

```bash
./build.sh <tag>
```

脚本使用 Jib 构建 `g2rain/g2rain-gateway-webflux:<tag>`，并显式跳过测试。执行脚本前必须先运行 `mvn test`。

## Dockerfile

Dockerfile 使用 Java 25 JRE Alpine，要求构建时提供 `JAR_FILE` 和 `BUILD_VERSION`，容器声明端口 `8080`。应用配置默认端口为 `8083`，部署时应显式统一 `SERVER_PORT`、容器端口和服务编排端口。

本次初始化未执行 Jib、Dockerfile 镜像构建或容器启动；这些不能由 Maven 单元测试结果代替。
