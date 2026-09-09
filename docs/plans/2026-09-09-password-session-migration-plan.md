# 密码与会话协议迁移设计（Phase 9）

- 日期：2026-09-09
- 状态：PBKDF2 代码迁移第一步已完成；随机 token、过期字段和生产 schema migration 尚未执行。
- 目标：替换确定性 token 和全量 MD5，同时保持现有 HTTP 业务码、`token`/`deviceId` 客户端契约和渐进上线能力。

## 1. 当前事实

### 密码

- `UserService` 的注册、导入、登录 legacy 升级、改密、重置和当前用户密码校验已统一调用 `PasswordHasher`；生产代码不再调用 `MD5Util`。
- 登录比较新版本 PBKDF2；32 位 legacy MD5 仅作为兼容输入，成功后在同一事务内升级为 PBKDF2。
- `verifyPassword` 已改为按当前 token 所属用户校验，不能通过摘要命中其他用户。
- `t_user.password` 当前为可空 `varchar(255)`；存量摘要在当前实现下为 32 个十六进制字符。
- `resetPassword` 仍返回固定临时密码 `123456`，但数据库只写入该密码的 PBKDF2 哈希；固定临时密码风险由产品明确接受，后续可单独改为强制首次登录改密。

证据：

- `backend/src/main/java/com/nip/service/UserService.java`：`handleNewUser`、`importUser`、`login`、`changePassword`、`verifyPassword`、`resetPassword`。
- `backend/src/main/java/com/nip/common/security/PasswordHasher.java`：版本格式、salt、迭代策略、legacy MD5 验证。
- `backend/database/project006.sql:36413`、`backend/database/project006-base.sql:31906`：`t_user.password varchar(255)`，本步不改变 schema。

本步验证：`PasswordHasherTest`、`PasswordMigrationTest`、`AdminAuthorizationTest`、`TxnRollbackConsistencyTest` 合计 21 项通过；后端 `./mvnw -B clean verify` 全量 238 项通过，0 failures / 0 errors / 0 skipped。

### 会话

- 登录 token 为 `AESUtil.encrypt(userAccount + "-" + password + "-" + deviceId, ...)`，输入相同则结果可预测且可重复；没有过期时间、撤销时间或 token 版本。
- token 和 deviceId 写回 `t_user`，同一用户的新登录会覆盖旧会话。
- `JWTInterceptor` 先读 header，仍兼容 query 参数；鉴权实际是 `token + deviceId` 是否匹配 `t_user`。
- 前端把 token、deviceId 和用户资料放在 localStorage；自动登录资料另存 localforage。

证据：

- `backend/src/main/java/com/nip/service/UserService.java:445-455`
- `backend/src/main/java/com/nip/common/interceptor/JWTInterceptor.java:52-68`
- `backend/src/main/java/com/nip/dao/UserDao.java:60-63,91-93`
- `frontend/src/common/session/logout.js:7-34`

## 2. 密码迁移方案

### 2.1 算法选择

选择 **PBKDF2-HMAC-SHA-256**，原因：

- JDK 21 标准库可实现，不引入新的 native 依赖或运行时插件；
- 可用独立 salt 和版本/迭代次数编码；
- 迁移阶段能明确区分 legacy MD5 与新格式；
- 迭代次数可在目标设备上校准，避免桌面 Electron 和服务端出现不可接受的登录延迟。

建议格式：

```text
pbkdf2_sha256$1$<iterations>$<base64url-salt>$<base64url-derived-key>
```

初始参数建议：salt 16 bytes、derived key 32 bytes；iterations 不在代码中硬编码为不可调整的常量，部署前用基准校准并写入版本策略。任何低于当前策略的 PBKDF2 记录在成功登录后重新派生。

不选择直接替换为 bcrypt/Argon2id：它们是合理选项，但当前工程没有对应 JDK 依赖和跨平台构建验证；引入后必须额外验证 native/Electron 交付。若威胁模型要求 memory-hard 算法，另开 Argon2id 方案，不与本次渐进切换混做。

### 2.2 渐进迁移步骤

1. 已新增 `PasswordHasher`，只接受明文输入并返回版本化格式；业务层不再调用 `MD5Util`。
2. 登录按格式分派：新格式执行 PBKDF2 校验；32 位 legacy MD5 仅作为临时兼容分支。
3. legacy MD5 登录成功后，在同一事务内写入新的 PBKDF2 哈希；失败登录不得改写密码。
4. 注册、导入、改密、重置统一直接写 PBKDF2；管理员重置按现有产品契约返回固定 `123456`，但不保存明文或 MD5 摘要。
5. 迁移观察期结束后拒绝 legacy MD5 登录，并盘点仍为 MD5 的存量记录；无法登录迁移的用户走明确的密码重置流程。
6. `verifyPassword` 已移除摘要查库语义；`MD5Util` 仅剩测试夹具/历史工具，待最终观察期后删除。

### 2.3 数据库和回滚边界

- `varchar(255)` 足以容纳版本化 PBKDF2 格式；上线前仍必须以实际格式长度做 schema 检查。
- 不覆盖原始 MD5 列进行不可逆批量转换；迁移依赖用户成功登录或受控重置。
- 首次发布只新增代码和可选版本字段/策略，不删除 legacy 兼容路径；观察窗口关闭后再单独提交删除变更。
- 任何密码写入失败必须回滚，不得在 `@Transactional` 内吞异常后返回成功。

## 3. 会话 token 迁移方案

### 3.1 目标协议

- 使用 `SecureRandom` 生成至少 32 bytes 的随机 opaque token，以 Base64 URL-safe 无 padding 编码。
- 本阶段按已确认的单会话模型复用 `t_user.token`、`t_user.device_id`（现有字段）并新增 `token_issued_at`、`token_expires_at`、`token_revoked_at`；不新增 `user_session` 表。
- token 必须绑定 `userId`、`deviceId`、issuedAt、expiresAt 和 revokedAt；服务端每次请求检查未撤销、未过期和设备匹配。
- access token 初始有效期建议 8 小时；刷新/撤销策略在客户端迁移方案确认后实施，不通过延长固定 token 规避过期。
- 新登录替换该用户旧 token，保持当前单设备/单会话行为；登出、密码修改、密码重置和管理员禁用账号清理或撤销当前 token。

### 3.2 存储切换

采用单会话字段，迁移最小化：

```text
t_user.token, t_user.device_id, t_user.token_issued_at,
t_user.token_expires_at, t_user.token_revoked_at
```

要求：

- 新 token 为随机值，不再由账号、密码和设备拼接生成；登录响应仍返回原 token，数据库暂按现有字段保存以维持最小改动。
- 生产 schema 使用 `validate`，必须提供新增时间字段的前置 migration 并完成 current/base 双快照演练。
- 兼容期旧 deterministic token 只由明确的 legacy 分支读取；观察窗口结束后删除 legacy 分支和旧字段兼容逻辑。
- 单会话模型不支持多设备并行；若产品未来需要多设备，另开 `user_session` 表设计，不在本次迁移中隐式扩展。

### 3.3 客户端与兼容切换

1. 先保持响应 `LoginSessionDto.token/deviceId` 字段不变，前端继续把 token 放入 header；不改变业务码。
2. 所有 HTTP 调用完成 header 迁移后，增加过期/撤销响应的集中处理；不能把业务 `202/204` 误判为会话失效。
3. WebSocket 不自动继承 HTTP header；在统一握手方案确定前不把 URL token 当作长期替代。优先评估短期一次性 WS ticket，避免把 access token 写入 URL。
4. 浏览器 localStorage 和 Electron 安全存储分开验收；本阶段不声称 localStorage 已满足高安全存储要求。
5. 兼容期保留 query token/deviceId 仅为迁移回滚，记录命中日志且不记录凭据值；观察窗口结束后删除 query fallback。

## 4. 必须保留的契约

- HTTP 仍统一返回 `Response<T>`，业务错误仍使用现有业务码。
- 203/204/206 的含义和文案不变；密码参数错误不能伪装成鉴权失败。
- 登录响应不回传 `password`；token/deviceId 只作为登录会话字段。
- 修改密码成功后旧会话撤销行为必须在前后端联合验收中明确，不能静默改变客户端预期。

## 5. 实施前门禁

- [ ] 产品确认 access token 过期时长、刷新策略和强制下线范围；已确认本次不支持多设备并行会话。
- [ ] 部署确认 `t_user` 新增 token 时间字段的 migration、备份、回滚和观察窗口；本次不新增 `user_session` 表。
- [ ] 目标设备完成 PBKDF2 成本基准，记录 p95 登录耗时。
- [ ] 完成 header 调用面 grep、WebSocket ticket 方案和 Electron 安全存储设计。
当前已落地 PBKDF2 新写入和 legacy 登录升级第一步；仍保留 legacy MD5 验证、确定性 token、query 兼容和 localStorage 风险，直到密码观察窗口与后续会话门禁满足。不以“增加版本字段”或“随机化一处 token”冒充完整协议迁移。

