# 配置

| 配置 | 用途 |
| --- | --- |
| `SERVER_PORT` | 服务端口，默认 `8083` |
| `SPRING_PROFILES_ACTIVE` | Spring Profile，默认 `dev` |
| `NACOS_SERVER_ADDR` | Nacos 配置中心与注册中心地址 |
| `SPRING_CLOUD_NACOS_DISCOVERY_*` | 服务发现用户名、密码、命名空间 |
| `SPRING_CLOUD_NACOS_CONFIG_*` | 配置中心用户名、密码、命名空间 |
| `spring.config.import` | 导入网关配置及 Token 密钥配置 |
| `gateway-white-list` | 全局和按过滤器划分的 context/exact/pattern 白名单 |
| `token` | JWT/密钥相关属性，由安全配置源提供 |
| `spring.http.codecs.max-in-memory-size` | 请求体聚合上限，当前本地值为 `10MB` |
| `SPRING_KAFKA_ENABLED` | 是否启用 Kafka 日志发送，默认关闭 |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka broker 地址 |

当前 `application.yml` 为 Nacos 发现与配置设置了开发默认用户名和密码。生产、共享测试和公开部署必须通过受控环境覆盖，不得使用仓库默认值；后续应移除版本化凭据默认值。

Redis 是 Spring Cloud Stream 当前默认 binder，输入 destination 为 `g2rain-syncer`。Actuator 仅暴露 `health` 和 `info`；OpenTelemetry tracing 开启，但本地 OTLP 导出默认关闭。
