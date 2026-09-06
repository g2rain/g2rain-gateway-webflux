# 本地开发

## 环境要求

- JDK 25+
- Maven 3.9+
- Nacos
- 可访问的 `g2rain-basis` 与 `g2rain-infra`
- 当前 Redis Stream binder 所需基础设施
- 需要验证日志投递时再启用 Kafka

## 常用命令

```bash
mvn test
mvn spring-boot:run
mvn clean package
```

服务名为 `g2rain-gateway`，默认端口 `8083`，默认 Profile 为 `dev`。启动前通过环境变量覆盖 Nacos 地址、命名空间和凭据；不要依赖仓库内开发默认凭据。

本地启动成功不代表路由可用。至少确认 Nacos 配置/发现、Basis 路由与服务注册、Infra 错误消息、Redis 同步通道和一个真实下游服务可访问。
