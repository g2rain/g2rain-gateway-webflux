# SessionType=MEMBER 入口处理

本文记录网关对客服会员会话（`SessionType=MEMBER`）的入口认证与转发契约。与员工 `USER`、账号 `PASSPORT` 分轨；签发方为 IAM（`POST /auth/member/token`），会员主数据在 Member。

入口 API 权限按租户开通 MEMBER 控制单元计算，详见 [member-api-permission-upgrade.md](member-api-permission-upgrade.md)。

协议对齐总方案见 IAM `docs/design/member-token-issuance-alignment.md`。

## 1. 目标

| 能力 | 行为 |
| --- | --- |
| JWT 校验 | `GatewayTokenAuthFilter` 验签后写入 `sessionType`、`organId`、`memberId`、scopes、绑钥等；**不**把会员主体写入 `userId`/`passportId`；须租户 `organType`、正数 `organId`/`memberId` |
| DPoP | `GatewayDPoPAuthFilter` 与其它会话**相同**：校验 Proof、绑钥、`acd`∈scopes，写入正数 `applicationId`/`applicationOrganId`；缺失或非法则拒绝 |
| 请求摘要 | `SignVerificationFilter` 与其它会话**相同** |
| 入口 API 权限 | `ApiPermissionFilter`：校验 MEMBER 主体；默认 `enforce` 下按 `MemberPerm(organId)`；**不**查 `UserPerm`，不回退 `DefaultPerm` |
| 下游透传 | `PrincipalForwardFilter` 先清全部 `PrincipalHeaders` 再 set 重建（含 `X-MEMBER-ID`）；白名单也清外部主体头；移除 `Authorization`/`DPoP` |

## 2. 过滤器分流（示意）

```text
JWT 验签成功（含 MEMBER 失败关闭校验）
  → DPoP（绑钥 + acd→applicationId）
  → Sign（query/body 摘要）
  → MemberPerm(organId) ⊇ apiId
  → 转发 X-SESSION-TYPE / X-ORGAN-ID / X-MEMBER-ID / 应用上下文 …
```

不再因 `SessionType=MEMBER` 跳过 DPoP 或摘要。

## 3. 与其它仓库

| 仓库 | 职责 |
| --- | --- |
| `g2rain-common` | `SessionType.MEMBER`、`memberId`、`X-MEMBER-ID` 契约；经 Gateway 的 MEMBER Token 须带合法应用上下文 |
| `g2rain-iam` | MEMBER Token 签发（Client+Application DPoP；claim 用 `memberId`，含 scopes/绑钥） |
| `g2rain-member` | 会员解析/创建；不经本网关直连 `/internal/...` |
| `g2rain-basis` | `fetchMemberTokenContext`；MEMBER 控制单元与按 organ 开通事实；`MEMBER_PERM` 同步 |

## 4. 安全约束

- 外部传入的 `X-MEMBER-ID` 等主体头不可信；先移除全部 `PrincipalHeaders`，仅转发网关重建值；白名单路径同样清除外部主体头。
- MEMBER 不得按员工 `userId`/角色做入口鉴权。
- 入口权限只决定「该租户 MEMBER 能否进网关匹配的 API」；下游仍须做租户与会员级授权。
- 未开通相关 MEMBER 控制单元的租户，不得因平台已发布 MEMBER 控制单元而被放行。
- 无完整应用上下文不得转发。
- 审计事件（`GatewayEvent`）可携带 `sessionType`、`memberId`，不得记录原始 Token。

## 5. 实现锚点

- WebFlux：`GatewayTokenAuthFilter`、`GatewayDPoPAuthFilter`、`ApiPermissionFilter`、`MemberPerm`、`SignVerificationFilter`、`PrincipalForwardFilter`；`filters/package-info.java`
- WebMVC：同名过滤器（顺序见 `docs/project.yaml` / `runtime-flows`）
- 安全摘要：[security-boundaries.md](../security/security-boundaries.md)
- 权限方案：[member-api-permission-upgrade.md](member-api-permission-upgrade.md)
