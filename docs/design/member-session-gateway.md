# SessionType=MEMBER 入口处理



本文记录网关对客服会员会话（`SessionType=MEMBER`）的入口认证与转发契约。与员工 `USER`、账号 `PASSPORT` 分轨；签发方为 IAM（`POST /auth/member/token`），会员主数据在 Member。



入口 API 权限按租户开通 MEMBER 控制单元计算，详见 [member-api-permission-upgrade.md](member-api-permission-upgrade.md)。



## 1. 目标



| 能力 | 行为 |

| --- | --- |

| JWT 校验 | `GatewayTokenAuthFilter` 验签后写入 `sessionType`、`organId`、`memberId` 等；**不**把会员主体写入 `userId` |

| DPoP | `GatewayDPoPAuthFilter` 对 MEMBER **跳过**（服务端客服持 Bearer，不绑 DPoP） |

| 请求摘要 | `SignVerificationFilter` 对 MEMBER **跳过**（无 DPoP 摘要上下文） |

| 入口 API 权限 | `ApiPermissionFilter`：校验 MEMBER 主体；默认 `enforce` 下按 `MemberPerm(organId)`；**不**查 `UserPerm`，不回退 `DefaultPerm` |

| 下游透传 | `PrincipalForwardFilter` 先清全部 `PrincipalHeaders` 再 set 重建（含 `X-MEMBER-ID`）；白名单也清外部主体头；移除 `Authorization`/`DPoP` |



## 2. 过滤器分流（示意）



```text

JWT 验签成功

  → sessionType == MEMBER ?

       是 → 跳过 DPoP / Sign

            → MemberPerm(organId) ⊇ apiId

            → 转发 X-SESSION-TYPE / X-ORGAN-ID / X-MEMBER-ID …

       否 → 既有 USER / PASSPORT / 静态 Key 路径

```



## 3. 与其它仓库



| 仓库 | 职责 |

| --- | --- |

| `g2rain-common` | `SessionType.MEMBER`、`memberId`、`X-MEMBER-ID` 契约 |

| `g2rain-iam` | MEMBER Token 签发（claim 用 `memberId`，非 `userId`） |

| `g2rain-member` | 会员解析/创建；不经本网关直连 `/internal/...` |

| `g2rain-basis` | MEMBER 控制单元与**按 organ 的开通事实**；权限查询与 `MEMBER_PERM` 同步 |



## 4. 安全约束



- 外部传入的 `X-MEMBER-ID` 等主体头不可信；先移除全部 `PrincipalHeaders`，仅转发网关重建值；白名单路径同样清除外部主体头。

- MEMBER 不得按员工 `userId`/角色做入口鉴权。

- 入口权限只决定「该租户 MEMBER 能否进网关匹配的 API」；下游仍须做租户与会员级授权。

- 未开通相关 MEMBER 控制单元的租户，不得因平台已发布 MEMBER 控制单元而被放行。

- 审计事件（`GatewayEvent`）可携带 `sessionType`、`memberId`，不得记录原始 Token。



## 5. 实现锚点



- WebFlux：`GatewayTokenAuthFilter`、`GatewayDPoPAuthFilter`、`ApiPermissionFilter`、`MemberPerm`、`SignVerificationFilter`、`PrincipalForwardFilter`；`filters/package-info.java`

- WebMVC：同名过滤器（顺序见 `docs/project.yaml` / `runtime-flows`）

- 安全摘要：[security-boundaries.md](../security/security-boundaries.md)

- 权限方案：[member-api-permission-upgrade.md](member-api-permission-upgrade.md)


