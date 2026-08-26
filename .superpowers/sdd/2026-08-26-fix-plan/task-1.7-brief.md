### Task 1.7: 模拟房间断线错身份 + router 泄漏（P0#9 + 新发现 3，WebSocketSimulationService）

**Files:**
- Modify: `src/main/java/com/nip/ws/WebSocketSimulationService.java`
- Test: `src/test/java/com/nip/ws/WebSocketSimulationTest.java`

**现行缺陷**：onOpen 同时写单例字段（:80/:84 `this.userModel`/`this.session`）和局部 `persistData`（:66,85-86）；onClose(:183-198) 把 `this` 传给 `quitRoomReport`（:193/:195），`this.userModel` 是**最后连接者** → 学员断线可按教员身份暂停整房并落库。quitRoomRouter(:301-320) 清成员后不 remove roomId key。

**重要（评审修正）**：`session`/`userModel` 字段**保留**——房间列表里的 `persistData` holder 就是本类 new 实例，@Data 生成的 getSession/getUserModel 在 :264/:270/:279/:282/:289/:313/:316/:432-433 约 10 处被调用，删字段即编译失败。holder 的 @Inject dao 字段为 null 无害（仅 getter 被调用，已核实）。要删的是**共享 bean 上的写入和读取**。

- [ ] **Step 1: 失败测试**：教员+学员（`Fixtures.user(...).getId()` 两个 UUID）连 REPORT 房间，关学员连接，断言 `roomDao.findById(roomId)` 状态未变为暂停、房间列表不含学员、教员连接仍在。房间与成员 fixture 写前核对 `SimulationRouterRoomEntity`/`SimulationRouterRoomUserEntity` 必填列。
- [ ] **Step 3: 修复**
  1. 删除 onOpen :80/:84 两行对 `this.userModel`/`this.session` 的赋值（persistData 已在 :85-86 持有连接态）；字段保留供 holder 使用，并在字段上加注释 `// 仅 per-connection holder(persistData) 使用；共享 bean 上恒为 null，禁止读取`。
  2. onClose 签名不变（已有 `@PathParam id`）；`quitRoomReport(this, roomId, id)`（:193/:195）改 `quitRoomReport(roomId, id)`，方法内从 `SimulationGlobal.reportRoom.get(roomId)` 按 `getUserModel().getId().equals(id)` 解析该连接的 holder，**教员/学员判定用 holder 的 userType 或 roomUserDao 按 (id, roomId) 查询，绝不读单例字段**。
  3. quitRoomRouter 末尾：`if (simulations.isEmpty()) SimulationGlobal.routerRoom.remove(roomId); else SimulationGlobal.routerRoom.put(roomId, simulations);`
  4. 完成后静态检查：除 persistData/holder 路径外 `this.userModel`/`this.session` 读取点清零。
- [ ] Step 4-5：绿 → `git commit -m "fix(p0-9): 断线身份按连接解析+router房间键泄漏"`

