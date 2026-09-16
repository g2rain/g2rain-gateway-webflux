# MEMBER 接口权限校验升级

## 1. 文档状态

- 状态：Gateway 已落地（默认 `enforce`）；`shadow` 仅迁移核对
- 目标仓库：`g2rain-gateway-webflux`（`g2rain-gateway-webmvc` 同语义）
- 权限事实来源：`g2rain-basis`
- 前置契约：`g2rain-common` 的 `SessionType.MEMBER`、`memberId`、`X-MEMBER-ID`
- 协作设计：Basis《控制单元 SessionType 权限模型升级》

本文描述 Gateway 对已验签 `SessionType=MEMBER` Token 的接口入口权限校验，不描述会员创建、身份绑定和 Token 换票流程。

入口认证分流、DPoP/Sign 跳过与主体转发见 [member-session-gateway.md](member-session-gateway.md)；本文专注权限来源与缓存同步。

实现锚点：`MemberPerm`、`ApiPermissionFilter` MEMBER 分支、`SyncerEnum.MEMBER_PERM`、`gateway.member-permission.mode`（默认 `enforce`）。

## 2. 当前状态与目标状态

当前 `ApiPermissionFilter` 要求 MEMBER 主体具有 `organId` 和 `memberId`，但接口权限复用 `DefaultPerm`。`DefaultPerm` 是 Passport 默认可访问 API 的**平台全局集合**，与租户是否开通无关，且 Passport 权限变化会间接改变 MEMBER 权限。

目标状态：

```text
PASSPORT → DefaultPerm / PassportPerm（平台全局集合）
USER     → UserPerm（按 organ / user / role / app）
MEMBER   → MemberPerm（按 organ：该租户已开通的 MEMBER 控制单元 → API）
```

核心语义：

1. MEMBER **不是**平台全局白名单，也**不是** Passport `DefaultPerm`。
2. 只有 Token 中的 `organId` 对应租户**已开通**相关 MEMBER 控制单元时，该会员才能通过网关进入对应 API。
3. MEMBER 不创建虚拟用户、不进入员工角色授权链，不调用 `UserPerm`，不把 `memberId` 映射为 `userId`。
4. 「租户已开通」复用 Basis 既有应用授权 / 机构侧控制单元激活事实（见第 3 节），不是会员个人角色。

## 3. Basis 依赖契约

### 3.1 按租户查询

Gateway 从 Basis 按租户拉取 MEMBER 可访问 API：

```http
GET /authority/session_api_permissions?sessionType=MEMBER&organId={organId}
```

建议响应：

```json
{
  "sessionType": "MEMBER",
  "organId": 10001,
  "version": 15,
  "apiIds": [101, 102, 108]
}
```

`apiIds` 必须使用 `resource_api.id`，与 Gateway 动态路由 `Route.id` 一致。

### 3.2 计算规则（摘要）

某 `organId` 的 MEMBER 入口 API = 同时满足：

1. `control_unit.session_type = MEMBER` 且已发布；
2. 该控制单元已通过该机构的**应用授权 / 控制单元激活**对租户生效（机构侧开通事实，通常落在 ADMIN 角色的 `role_control_unit_relation` + `application_authorization`，`status=ACTIVATED`）；
3. 控制单元已关联对应 `API_ENDPOINT`。

不满足第 2 条时，即使平台已发布 MEMBER 控制单元，该租户的 MEMBER Token 也不得进入相关 API。

详细 SQL、触发条件与既有全局查询的修正见 Basis《控制单元 SessionType 权限模型升级》。

### 3.3 与 USER / PASSPORT 的边界

| 类型 | Gateway 缓存维度 | 是否看租户开通 |
| --- | --- | --- |
| PASSPORT | 全局 apiIds | 否（平台默认能力） |
| USER | organ + user + app（角色链） | 是（角色已获控制单元） |
| MEMBER | **organ → apiIds** | **是（租户已开通 MEMBER 控制单元）** |

MEMBER 与 USER 共用「按 organ 失效、按 organ 回源」的同步形态，但不共用 `UserPerm`，也不使用 `userId` / `roleIds`。

## 4. Gateway 缓存设计

### 4.1 独立 MemberPerm（按 organ）

新增 `MemberPerm`，不得继续委托 `DefaultPerm`，也不得并入 `UserPerm`：

```java
public record MemberOrganPermission(
    long organId,
    long version,
    Set<Long> apiIds
) {
}
```

推荐结构（示意）：

```text
Cache<Long /* organId */, MemberOrganPermission>
+ 同一 organId 未命中时的 single-flight 回源（避免并发风暴）
```

约束：

- 权限刷新先完整构造不可变 `apiIds`，再写入缓存；不得在并发读期间逐项改共享可变集合。
- 空集合是合法结果（租户未开通任何 MEMBER 能力），必须与「加载失败」区分。
- 无对应路由的 apiId 记录不含敏感信息的告警，并按配置决定忽略或整批拒绝；生产默认建议整批拒绝。

### 4.2 加载策略

采用与 `UserPerm` 相近的**按 organ 懒加载**，不做「全平台 organ 启动全量预热」：

```text
请求进入 ApiPermissionFilter（MEMBER）
  ├─ 校验 organId / memberId 主体
  ├─ 读 MemberPerm(organId)
  │     命中 → 判断 apiId ∈ apiIds
  │     未命中 → single-flight 调 Basis(?sessionType=MEMBER&organId=…)
  │               ├─ 成功 → 写入缓存后判定
  │               └─ 失败 → 本次拒绝（该 organ 失败关闭），不回退 DefaultPerm
  └─ 继续或 403
```

启动时只需保证路由仓库与 MemberPerm 组件就绪；不要求已持有全部租户快照。

## 5. 请求授权流程

### 5.1 处理顺序

```text
EdgePrincipalContextScopeFilter
  ↓
GatewayTokenAuthFilter：JWT 验签并构建主体
  ↓
GatewayDPoPAuthFilter：与其它会话相同校验 DPoP（MEMBER 不跳过）
  ↓
ApiPermissionFilter：按 organId 执行 MemberPerm 校验
  ↓
SignVerificationFilter：与其它会话相同校验摘要（MEMBER 不跳过）
  ↓
PrincipalForwardFilter：重建可信主体头
```

接口权限检查必须发生在主体转发之前。

### 5.2 MEMBER 主体前置条件

进入 MemberPerm 前必须满足：

```text
sessionType == MEMBER
organId != null 且 organId > 0
memberId != null 且 memberId > 0
userId == null
passportId == null
```

缺少 MEMBER 必填 claim 或同时携带员工/Passport 主体时，属于 Token 主体无效，返回未认证错误，而不是按 USER 或 PASSPORT 降级处理。

### 5.3 API 标识

接口权限必须使用 Spring Cloud Gateway 已匹配的 `Route.id`：

```java
Route route = exchange.getAttribute(
    ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR
);
Long apiId = route == null ? null : parseLong(route.getId());
```

Basis 动态路由必须保持 `Route.id == resource_api.id`。不得在权限过滤器中重新按原始 URL 字符串匹配。

### 5.4 授权算法

```java
if (SessionType.isMember(context.getSessionType())) {
    validateMemberPrincipal(context);
    Long organId = context.getOrganId();

    MemberOrganPermission perm = memberPerm.getOrLoad(organId);
    // getOrLoad：缓存命中直接返回；未命中回源；回源失败抛权限缓存不可用

    if (apiId == null || !perm.apiIds().contains(apiId)) {
        throw unauthorized();
    }

    return chain.filter(exchange);
}
```

决策矩阵：

| 条件 | 处理 |
| --- | --- |
| JWT 缺失、签名错误或过期 | 401 / 未认证 |
| MEMBER claim 不完整或混入 USER/PASSPORT 主体 | 401 / 未认证 |
| 路由没有合法 API ID | 拒绝，不回退到路径判断 |
| 该 organ 的 MemberPerm 回源失败 | 503 或专用权限缓存不可用错误 |
| API ID 不在该 organ 的 MemberPerm | 403 / 无权限（含「租户未开通」） |
| API ID 在该 organ 的 MemberPerm | 继续过滤器链 |

稳态路径只读本地 organ 缓存；仅缓存未命中时回源 Basis，禁止每次请求无条件同步打 Basis。

## 6. 主体转发与下游边界

授权通过后，Gateway 重建并转发：

```http
X-SESSION-TYPE: MEMBER
X-ORGAN-ID: <organId>
X-MEMBER-ID: <memberId>
```

转发前必须清除外部请求携带的同名主体头，并移除 Authorization、DPoP 等敏感认证头。

Gateway 的 MemberPerm 只表示：「该租户已开通的 MEMBER 能力允许进入该 API」。下游仍需校验：

- 请求 `organId` 与业务记录租户一致。
- 请求 `memberId` 与目标会员或资源归属一致。
- 会员和业务对象状态允许当前操作。

## 7. 动态同步

### 7.1 通道与形态

Gateway 订阅独立数据源：

```text
MEMBER_PERM
```

与 `USER_PERM` **共用 cache-sync 基础设施**，**不共用** `UserPerm` 缓存与 payload 语义。

推荐对齐 `USER_PERM` 的按机构失效：

```text
收到 MEMBER_PERM 失效(organId)
  ↓
invalidate MemberPerm(organId)
  ↓
后续该租户 MEMBER 请求未命中缓存时再拉 Basis
```

示意载荷：

```json
{
  "dataSource": "MEMBER_PERM",
  "organId": 10001
}
```

当 MEMBER 控制单元目录或资源关系变化且影响多个租户时，Basis 应枚举受影响 `organId` 分别失效，或发送约定的「全量 organ 失效」信号；Gateway 不得在未失效的情况下继续使用陈旧 organ 缓存。

### 7.2 必须触发失效的变更

- 租户应用授权开通、关停、控制域控制单元同步。
- 机构侧 MEMBER 控制单元激活 / 关停。
- MEMBER 控制单元发布、停用、删除，或 `session_type` 变入/变出 MEMBER。
- MEMBER 控制单元 API_ENDPOINT 关系增删；关联 API 删除或失效。

刷新失败时：已缓存的 organ 快照可暂时保留并告警；从未成功加载的 organ 在请求路径上保持失败关闭。不能用加载异常覆盖为「空集合已开通」。

## 8. 白名单规则

MEMBER 业务 API 不得加入以下白名单：

- `GatewayTokenAuthFilter` 白名单。
- `ApiPermissionFilter` 白名单。
- `PrincipalForwardFilter` 白名单。

换取 MEMBER Token 的前置接口可以按认证协议进入专项白名单，但不能因此扩大同路径前缀下的业务接口。建议启动时检查已知 MEMBER 业务 API 是否同时命中认证或权限白名单，并将冲突视为配置错误。

## 9. 灰度升级

### 9.1 发布顺序

1. Basis 完成按 `organId` 的 MEMBER 权限查询与开通事实接入；修正仅按 `session_type` 的全局清单语义。
2. Basis 在租户开通/关停与 MEMBER 资源变更时发布 `MEMBER_PERM(organId)`。
3. Gateway 增加按 organ 的 `MemberPerm`，先影子比较（记录「全局 DefaultPerm 命中 vs 租户 MemberPerm 命中」差异），不改变授权结果。
4. 逐项确认：未开通租户即使 API 在旧 DefaultPerm 中也应在 enforce 后拒绝。
5. 受控切换 `enforce`。
6. 观察 401、403、按 organ 回源失败、未知 route ID 指标。
7. 稳定后删除 MEMBER 对 `DefaultPerm` 的兼容分支。

建议临时配置：

```yaml
gateway:
  member-permission:
    mode: shadow # shadow | enforce
```

`shadow` 只用于迁移核对，不能长期运行。`enforce` 下只认按 organ 的 MemberPerm。

### 9.2 回滚

- 代码回滚时保留 Basis 的 `session_type` 字段和 MEMBER 控制单元 / 开通数据。
- 紧急情况下可短期切回旧权限源，但必须高优告警（恢复 Passport/MEMBER 耦合，且**丢失租户开通门禁**）。
- 单个 organ 缓存失效或回源失败不需要回滚整个 Gateway。

## 10. 测试与验收

至少覆盖：

1. 租户已开通 MEMBER 控制单元 + 有效 MEMBER Token + 已覆盖 apiId：通过。
2. 平台已发布 MEMBER 控制单元，但该租户**未开通**：403。
3. 租户 A 已开通、租户 B 未开通：仅 A 的 Token 可通过同一 apiId。
4. 有效 MEMBER Token + 未覆盖 apiId：403。
5. `organId` 或 `memberId` 缺失：未认证。
6. MEMBER Token 携带 `userId` 或 `passportId`：未认证。
7. 相同 API 只加入 USER 控制单元：MEMBER 仍然拒绝。
8. 相同 API 同时加入 USER 和 MEMBER 控制单元，且租户已开通 MEMBER 单元：MEMBER 链生效。
9. 租户关停应用授权 / MEMBER 控制单元：同步失效后对应 API 被拒绝。
10. 该 organ 首次回源失败：失败关闭，不回退 DefaultPerm。
11. 重复、乱序失效事件：不导致错误放行；再次请求可正确回源。
12. 外部伪造 `X-MEMBER-ID`：下游只能收到 Token 中的 memberId。
13. MEMBER 业务 API 与认证/权限白名单冲突：启动检查失败或健康状态异常。

完成标准：MEMBER 入口权限可同时由「MEMBER 控制单元配置」与「租户开通事实」解释；Gateway 不再通过 Passport `DefaultPerm` 推断会员权限，也不得仅凭平台已发布 MEMBER 控制单元对未开通租户放行。
