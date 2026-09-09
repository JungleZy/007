# WebSocket 空闲超时与握手鉴权 Spike

- 日期：2026-09-09
- 范围：后端 `quarkus-websockets`（Jakarta WebSocket/Undertow）端点，以及前端现有 WebSocket URL 身份传递。
- 结论：本次只完成能力确认和安全决策，不把未实施的连接策略标记为已完成。

## 1. 现状证据

- `backend/pom.xml` 使用 `io.quarkus:quarkus-websockets`，不是 `quarkus-websockets-next`。
- 端点使用 `@ServerEndpoint`、`@OnOpen`、`@OnClose`、`@OnError`、`@OnMessage`，属于 Jakarta WebSocket 规范路径。
- 当前 `backend/src/main/resources/application.yml` 只有 `quarkus.websocket.dispatch-to-worker: true`，没有适用于这些 legacy endpoint 的 Quarkus WebSocket idle-timeout 配置。
- Quarkus legacy 官方指南展示的扩展和端点模型与本项目一致；官方文档没有为 `quarkus-websockets` 提供通用 `quarkus.websocket.idle-timeout` 配置。`quarkus.websockets-next.*` 配置不能套用于当前扩展。
- Jakarta WebSocket `Session` 提供 `setMaxIdleTimeout(long)`，因此若需要服务端空闲关闭，可在每个 `@OnOpen` 对当前 session 设置标准 API；这属于应用代码策略，不是当前配置文件已生效的全局 Quarkus 配置。

参考：

- Quarkus legacy WebSocket guide：<https://quarkus.io/guides/websockets>
- Quarkus WebSockets Next reference（仅用于区分扩展，不能直接用于本项目）：<https://quarkus.io/guides/websockets-next-reference>

## 2. 选定的 idle 策略

暂不直接给训练端点写固定超时。原因：训练连接可能长时间没有业务帧，但用户仍在等待、阅读或操作设备；粗暴 idle close 会误伤训练状态。

后续实施策略：

1. 先实现协议级 heartbeat：客户端和服务端每 30 秒发送/确认一次 ping/pong 或应用层 heartbeat。
2. 记录每个 session 的最后一次有效业务帧或 heartbeat 时间。
3. 连续 3 个周期未收到任何有效响应（约 90 秒）才判定连接失活，并关闭该 session。
4. 对训练端点使用 `Session#setMaxIdleTimeout` 作为最后一道资源保护，初始建议值 10 分钟；必须在真实训练最长静默时长验证后落地。
5. 连接关闭必须复用现有按 session 的移除逻辑，不能按用户 ID 无条件删除替换连接。

在 heartbeat 尚未实现前，不新增配置键，也不宣称 Quarkus 已提供可验证的 idle-timeout 行为。

## 3. 握手身份鉴权决策

当前多个 WebSocket URL 只携带路径身份，例如：

- `/websocket/{sid}`
- `/websocketUnion/{sid}`
- `/simulation/{id}/{roomId}`
- `/generalKeyPatTrain/{uid}/{trainId}`
- `/generalTelexPatTrain/{uid}/{trainId}`

当前握手没有统一校验 `token` + `deviceId`，路径中的用户 ID 主要用于定位连接和业务成员。前端把 token 放在 HTTP 请求头，不能假定它会自动成为 WebSocket 握手的服务端认证凭据。

决策：**本次不伪装完成握手鉴权，保留为已接受的安全风险并另立实施项。**

风险边界：

- 部分端点已在 `onOpen` 校验用户或房间成员，降低了“任意字符串直接入房”的风险；这不是会话认证，也不能证明调用者就是该用户。
- URL 身份可能进入代理、服务器或浏览器历史日志；不能迁移 query token 作为替代方案。
- 训练数据、房间控制和实时消息仍存在“已登录 HTTP 会话与 WS 身份脱钩”的风险。

后续实施前置：

1. 确定浏览器/Electron 共同支持的握手凭据传递方式（Cookie、受控子协议或一次性短期 WS ticket）。
2. 服务端在 `onOpen` 统一验证 token、deviceId、路径身份和房间/训练授权。
3. 失败握手统一拒绝并不写入任何房间容器。
4. 增加匿名、错用户、过期 token、替换连接和重放边界测试。
5. 完成客户端迁移与部署日志观察后，才删除旧路径兼容行为。

## 4. 本 Spike 的验收状态

- [x] 确认当前使用 legacy `quarkus-websockets`。
- [x] 确认没有可直接套用的项目级 `quarkus.websocket.idle-timeout` 配置。
- [x] 选定“heartbeat + 应用层失活判定 + 标准 Session 超时兜底”的实施方向。
- [x] 明确 WS 握手 token/deviceId 尚未完成，并记录为接受风险。
- [ ] heartbeat、10 分钟兜底和统一握手鉴权：另立实现任务，不在本 Spike 中虚报完成。
