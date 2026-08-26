## Phase 2：WebSocket 并发家族（批 1 完成后，与 Phase 3-6 可并行）

**Files:** `src/main/java/com/nip/ws/**`、`SimulationGlobal.java`。依据：ws 分片 23 条 + 审计新发现 5。

- [ ] **Task 2.1**: `SimulationGlobal` 三张房间表 value 改 `CopyOnWriteArrayList`，所有 `Optional.ofNullable(map.get(roomId)).orElseGet(ArrayList::new)` + 尾部 `put` 的写法改 `map.computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>())` 原子化（addRoomDisturd:100-101、addRoomReport、addRoomRouter:161-177 等全部房间加入点）。
- [ ] **Task 2.2**: 三个 General 端点（WebSocketGeneralKeyPatService/TelexPatService/TickerPatService）补 `@OnError`：`log.error("ws error, session={}", session.getId(), t)` + 清理该 session 对应状态（复用各自 onClose 清理逻辑）。
- [ ] **Task 2.3**: P1-12 主缺陷——REST 删房后房间列表遍历 NPE：WebSocketSimulationService :463/:487/:521/:531/:537/:546（全部核实存在）对 `get(roomId)` 判空短路。
- [ ] **Task 2.4**: `WebSocketService.sendInfoAll:185-189` 改按 `sid` 定向（新发现 5）；同文件其余广播点核对入参使用。
- [ ] **Task 2.5**: 依 ws 分片剩余 P1/P2 清单逐条修（成员泄漏、onClose 日志身份、极端 onClose-先于-onOpen 防护），每条对照 `agent://WsAudit` 判定表位置。
- [ ] **Task 2.6**: 扩展 WebSocketUnionTest：50 次并发进出房间循环，断言结束后 `webSocketClientSet`/房间表 size 归零；`grep -n "private Session\|private .*UserModel" src/main/java/com/nip/ws/*.java` 输出仅剩 WebSocketSimulationService 的 holder 字段（带禁读注释）作为静态门禁。
- [ ] 提交：每 Task 一次 `fix(ws-N)`，收尾 `$MVN clean verify`。

