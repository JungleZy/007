# WebSocket 与实时训练层并发正确性审查

审查日期：2026-08-26　　审查范围：`src/main/java/com/nip/ws/`（含 `ws/service/`、`ws/model/`）、`src/main/java/com/nip/service/general/`、`service/simulation/`、`service/event/`、`service/enums/`

审查方式：纯静态阅读（本机无 Java 环境，未运行任何构建/测试）。

严重级口径沿用 `docs/reviews/2026-08-15-situation-display-orbit-placard-review.md` 第 1 节：P0=崩溃/数据永久损坏/功能不可用；P1=严重缺陷（数据完整性、并发损坏、资源泄漏）；P2=功能瑕疵、性能、可维护性。纯安全项为已接受风险，不计入本报告问题数（本次范围内未发现新增纯安全项，故无附录）。

---

## 结论

**共 23 个问题：P0 2 个，P1 13 个，P2 8 个。**

最要命的一条不是竞态，而是设计前提错了：所有 `@ServerEndpoint` 端点都标了 `@ApplicationScoped`。在 Quarkus classic `quarkus-websockets`（pom.xml:48-51）下，端点实例由 CDI 提供，`@ApplicationScoped` 意味着**全进程只有一个端点对象**，所有连接共用同一份 `this.session` / `this.sUser` / `this.userModel` 实例字段。

这一判断有仓库内自证：`WebSocketService.onOpen`（WebSocketService.java:79-82）和 `WebSocketSimulationService.onOpen`（WebSocketSimulationService.java:66,85-86）都刻意 `new` 出一个 `persistData` 对象再放进集合，而不是放 `this` —— 这个绕法只有在 `this` 被共享时才有意义。`WebSocketUnionService` 和 `StartWebSocket` 没有做这个绕法，直接把 `this` 塞进了集合，于是彻底坏掉。

---

## P0（2 个）

### P0-1　联合训练端点把所有人的消息发给最后一个连上的客户端

**位置**：`src/main/java/com/nip/ws/WebSocketUnionService.java:38-59`（`@ApplicationScoped` + 实例字段 `session`/`sUser`）、`:79-81`、`:160-191`、`:284-287`、`:344-345`、`:423-426`、`:497`、`:524`

**现象**：

```java
// :79-81
this.session = session;
this.sUser  = userModel;
webSocketClientSet.put(sid, this);   // 每个 sid 都指向同一个单例对象

// :496-500
public void sendMessage(String message) {
  this.session.getBasicRemote().sendText(message);  // 恒定发向最后一次 onOpen 的 session
}
```

`webSocketClientSet` 虽然是 `ConcurrentHashMap`，但里面**每一个 value 都是同一个单例引用**。因此 `webSocketClientSet.get(user.getId()).sendMessage(...)`（:180、:344、:423-426、:447）无论 key 是谁，实际都写进 `this.session` —— 也就是最近一次 `onOpen` 覆盖进去的那条连接。

**竞态窗口 / 触发条件**：不需要竞态，只要同时在线人数 >= 2 就必然发生。第 2 个人 `onOpen` 完成的瞬间，第 1 个人就永久失联。

**最坏后果**：
1. 除最后一个连上的席位外，所有席位收不到任何推送（加入房间、席位状态检测、房间消息、解散房间全部失效）—— 联合训练多人场景功能不可用。
2. 本应发给 A 的房间消息全部投递到 B 的屏幕上，等于席位串号。
3. `onClose()` -> `userExit()`（:169-192）用 `this.sUser` 判断谁退出。A 断开时 `this.sUser` 已经是 B，于是把 **B** 从所有房间里移除并向全场广播 B 退出，而 B 还连着。

**建议修复方向**：端点改 `@Dependent`（每会话一个实例），或者彻底不用实例字段 —— 把 `Session` 和 `UserModel` 一起封进独立的 `ClientHolder` 值对象存进 `ConcurrentHashMap<String, ClientHolder>`，所有发送/退出逻辑只从 map 取 holder，不碰 `this`。

---

### P0-2　仿真房间退出时按"最后连上者"的角色判定，一个学员掉线会把整房训练置为暂停

**位置**：`src/main/java/com/nip/ws/WebSocketSimulationService.java:55-56`、`:80-86`、`:184-198`、`:257-283`

**现象**：

```java
// :80-86  onOpen 把本次连接的用户写进共享实例字段
this.userModel = PojoUtils.convertOne(roomUserMap, SimulationUserModel.class);
this.session   = session;
persistData.setSession(session);      // persistData 才是真正入表的副本
persistData.setUserModel(userModel);

// :193 / :195  onClose 传的却是 this，不是 persistData
quitRoomReport(this, roomId, id);

// :261  用 this.userModel 判断退出者是学员还是教员
if (ws.getUserModel().getChannel().compareTo(1) == 0) { /* 学员退出分支 */ }
else { /* 教员退出分支：把房间 playStatus 置 0，并给所有学员广播 type=2 */ }
```

作者已经意识到要用 `persistData` 存快照（:66、:85-86），但 `onClose` 又退回去用了 `this`。`this.userModel` 是**最后一次 `onOpen` 写入的用户**，与正在关闭的这条连接无关。

**竞态窗口 / 触发条件**：教员先进房、学员后进房是正常时序，此时 `this.userModel` = 学员。但只要出现"学员 A 先连 -> 教员 T 后连 -> A 掉线"，`this.userModel` 就是 T（channel=0），A 的 `onClose` 走进教员退出分支。多人房间里这是常态而非边角情况。

**最坏后果**：一名学员断线 ->
- `roomEntity.setPlayStatus(0); roomDao.save(...)`（:268-272）把**整个房间**的训练状态改成暂停并落库；
- 向房内所有学员广播 `{"type":2}`（:273-280），全体学员的训练界面被结束/暂停。

反向同样成立：教员退出时若 `this.userModel` 是学员，则只给教员发一条学员下线通知，房间状态不落库，学员端毫无感知，训练卡死在进行中。

**建议修复方向**：`onClose` 不要依赖实例字段。用 `@PathParam` 拿到的 `id` 从 `SimulationGlobal.reportRoom.get(roomId)` 里查出对应的 `persistData`，用它的 `userModel` 做角色判定；`addRoomDisturd/addRoomReport/addRoomRouter` 的第一个入参 `ws` 同理应该传 `persistData` 而不是 `this`。


---

## P1（13 个）

### P1-1　多页评分用 parallelStream 并发写 ArrayList 和共享统计对象，随后删除原始记录

**位置**：`src/main/java/com/nip/service/general/GeneralKeyPatService.java:638-661`

**现象**：

```java
KeyPatStatisticalDto keyPatStatistics = new KeyPatStatisticalDto();   // :638 共享可变统计对象
List<KeyPatValueTransferDto> pageValueResult = new ArrayList<>();     // :643 非线程安全
pageNumbers.parallelStream().forEach(pageNumber -> {                  // :644
  ... trainPageDao.findByTrainIdAndPageNumberOrderBySort(...)          // :646 FJP 线程上访问 EntityManager
  ... userValueDao.findByTrainIdAndPageNumberAndUserIdOrderBySort(...) // :649
  handle(userId, pageResult, userPages, userPageValues, keyPatStatistics); // :652
  pageValueResult.addAll(pageResult);                                  // :653 并发 addAll
});
resolverDao.deleteByTrainIdAndUserId(entity.getId(), userId);          // :657 先删原始记录
resolverDao.saveAndFlush(resolverEntities);                            // :658
userValueDao.deleteByTrainIdAndUserId(entity.getId(), userId);         // :660
userValueDao.saveAndFlush(userValueEntities);                          // :661
```

三重问题叠在一起：

1. **`ArrayList.addAll` 并发调用**。`ArrayList` 的扩容是 `elementData = Arrays.copyOf(...)` + `size += n` 的非原子序列。两个 FJP 线程同时 `addAll` 会互相覆盖 `elementData` 引用、`size` 少加，结果是**元素静默丢失**，极端情况抛 `ArrayIndexOutOfBoundsException`。
2. **共享 `KeyPatStatisticalDto` 被并发读改写**。`KeyPatUtils.handle`（common/utils/KeyPatUtils.java:48、:81、:84 等）全是 `ks.setPat(ks.getPat() + n)` 这种 read-modify-write，无任何同步，累加结果必然偏小。
3. **跨线程使用 EntityManager**。`trainPageDao`/`userValueDao` 继承 `BaseRepository`（common/repository/BaseRepository.java:12），走 Quarkus Panache 的事务作用域 `Session`。`parallelStream` 在 `ForkJoinPool.commonPool` 线程上执行，那里既无事务上下文也无 CDI 请求上下文。[INFERENCE] 按 Quarkus Hibernate ORM 的实现，这会抛 `ContextNotActiveException`；若因单页训练全部在调用线程上执行而侥幸不抛，则退化为多线程共享同一 `Session` —— `Session` 本身就不是线程安全的。

**竞态窗口**：`pageNumbers.size() > 1` 且 commonPool 并行度 > 1 时的整个 forEach 执行期。

**最坏后果**：`pageValueResult` 残缺 -> 转成实体后，第 657/660 行**先把该学员该训练的全部原始拍发记录删光**，再写入残缺集合。学员拍发原始数据永久丢失且不可恢复；即使数据未丢，统计量少加也会让分数、正确率、速率全部算错并落库。这是本次范围内后果最重的数据完整性问题（按 P0 的"数据永久损坏"口径可升级为 P0，此处沿用上一轮 P1 定级）。

**建议修复方向**：
- 最省事且正确：删掉 `parallelStream()`，改成顺序 `forEach`。页数是 <=2 的量级（`totalNumber/100`），并行毫无收益。
- 若确要并行：先在调用线程一次性把所有页的 DB 数据取出，纯计算部分用 `stream().parallel().map(...).collect(...)` 收集（`collect` 有正确的合并语义），统计量改用 `LongAdder`/局部对象最后归并，且绝不在 FJP 线程里碰 DAO。
- 无论哪种，`delete` 应在确认 `pageValueResult.size()` 与预期页数一致后才执行，或改成同事务内先 insert 新数据再删旧数据。

**同型实例（跨分片，属 ServiceCore 范围，此处仅交叉引用）**：`src/main/java/com/nip/service/PostTelegraphKeyPatTrainService.java:340-361`，代码结构几乎逐行相同（共享 `ks`、共享 `pageValueResult`、`valueDao.deleteByTrainId` 后 `saveAndFlush`），问题与后果一致。全仓库 `parallelStream` 只有这两处（已 grep 全量确认）。

---

### P1-2　SimulationGlobal 三个房间表的 value 是 ArrayList，被 WS 线程与 REST 线程并发读写

**位置**：`src/main/java/com/nip/ws/service/simulation/SimulationGlobal.java:10-19`

**现象**：三个 `ConcurrentMap<Integer, List<WebSocketSimulationService>>` 的**外层**是线程安全的，**内层 value 全部是裸 `ArrayList`**（由 `Optional.ofNullable(...).orElseGet(ArrayList::new)` 产生，WebSocketSimulationService.java:100-101、:141-142、:162-163）。对这个 `ArrayList` 的操作分布在至少 4 类线程上：

| 操作 | 位置 | 线程 |
|---|---|---|
| `add` | WebSocketSimulationService.java:134、:157、:176 | WS onOpen |
| `remove` / `iterator.remove` | :286-295、:320、:654 | WS onClose |
| 增强 for 遍历广播 | :359、:366、:374、:382、:404、:429、:441、:485、:503、:511 | WS onMessage |
| stream 遍历（REST） | SimulationRouterRoomService.java:186-187、:229-234；SimulationReportRoomService.java:175-186；SimulationReceptRoomService.java:179-190 | HTTP 工作线程 |

**竞态窗口**：任意一次 `onOpen`/`onClose` 与任意一次广播或 REST 查询重叠的整个窗口。房间人数越多、消息越密（干扰训练是高频推送），窗口越大。

**最坏后果**：
- 遍历中被并发结构性修改 -> `ConcurrentModificationException`。发生在 `onMessage` 里会打断整条训练消息的转发；发生在 REST 的 `getRoomUserList` 里会让教员端房间人员列表接口直接报 500。
- 无异常时的静默损坏：`ArrayList.add` 的 `size++` 非原子，两个 `onOpen` 同时进来会**丢掉一整条会话**，该学员从此收不到任何广播且教员端看不到他在线。

**建议修复方向**：把 value 类型换成 `CopyOnWriteArrayList`。房间成员表是读多写少（进出房低频、广播高频），COW 语义正好匹配，且天然消除遍历期 CME。

---

### P1-3　addRoom* 的 get-修改-put 非原子，并发入房会丢失整条会话

**位置**：`src/main/java/com/nip/ws/WebSocketSimulationService.java:99-101 与 :134-135`、`:140-142 与 :157-158`、`:161-163 与 :176-177`

**现象**：

```java
List<WebSocketSimulationService> simulations =
    Optional.ofNullable(SimulationGlobal.reportRoom.get(roomId)).orElseGet(ArrayList::new);  // (1) 读
...
simulations.add(persistData);                                                                // (2) 改
SimulationGlobal.reportRoom.put(roomId, simulations);                                        // (3) 写回
```

典型 check-then-act。房间不存在时，两个并发 `onOpen` 在 (1) 处都拿到 `null` -> 各自 `new ArrayList`，(2) 各加一人，(3) 后写的 `put` 覆盖先写的。

**竞态窗口**：(1) 到 (3) 之间，中间还夹着一次 `roomDao.findByIdOptional(roomId)` 数据库往返（:118）和若干次 `getBasicRemote().sendText` 阻塞发送（:126、:132）—— 窗口是**毫秒级**，不是纳秒级，实际非常容易命中。

**最坏后果**：先入房的用户会话被整条丢弃。他自己的 WS 连着，`onMessage` 能进来，但他不在任何房间列表里，收不到广播、教员端看不到他；他 `onClose` 时 `quitRoom*` 也找不到自己，什么都不清理。

**建议修复方向**：改用 `computeIfAbsent`：`SimulationGlobal.reportRoom.computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>()).add(persistData);`，并删掉末尾的 `put`。`WebSocketGeneralKeyPatService.java:60-65`、`WebSocketGeneralTelexPatService.java:60-65`、`WebSocketGeneralTickerPatService.java:40-44` 是同一个反模式，一并改。

---

### P1-4　干扰房间退出时算出了 removeIndex 却从不移除，会话与房间条目永久泄漏

**位置**：`src/main/java/com/nip/ws/WebSocketSimulationService.java:201-254`，关键在 `:251-253`

**现象**：

```java
Integer removeIndex = null;
for (int i = 0; i < simulations.size(); i++) { ... removeIndex = i; ... }   // :214-224 / :234-243 只赋值
...
if (removeIndex != null && simulations.isEmpty()) {                          // :251
  SimulationGlobal.disturbRoom.remove(roomId);
}
```

全方法没有任何一处 `simulations.remove(...)`。而且 `removeIndex != null` 恰恰意味着退出者**仍在**列表里，此时 `simulations.isEmpty()` 永远为 `false` —— 这个 `if` 的两个条件在语义上互斥，分支体是不可达代码。

**最坏后果**：
- 干扰训练房间的 `WebSocketSimulationService` 对象（连同已关闭的 `Session`）永远留在 `SimulationGlobal.disturbRoom` 里，进程不重启就不释放 —— 内存泄漏，且随进出房次数线性增长。
- `disturbRoom` 的 key 永远不会被移除，房间数只增不减。
- 业务上：已下线的学员在教员端在线人员里一直显示在线；每次 `messageHandleDisturb` 广播都要遍历这些死会话（虽有 `session.isOpen()` 保护不会抛异常，但推送延迟随死会话数增长）；同一用户重连会走 `kickOutOld`，但见 P2-3，那个方法自己也漏。

**建议修复方向**：把 `removeIndex` 整套逻辑换成 `simulations.removeIf(s -> Objects.equals(s.getUserModel().getId(), userId));`，再判断 `simulations.isEmpty()` 决定是否 `disturbRoom.remove(roomId)`。

---

### P1-5　通报房间退出时先 remove 房间又无条件 put 回填，房间条目永远清不掉

**位置**：`src/main/java/com/nip/ws/WebSocketSimulationService.java:285-299`

**现象**：

```java
if (simulations.isEmpty()) {
  SimulationGlobal.reportRoom.remove(roomId);   // :291-293 刚删掉
}
break;
...
System.out.println(simulations.size());
SimulationGlobal.reportRoom.put(roomId, simulations);   // :298 立刻又把空列表塞回去
```

最后一个人退出时，`remove` 刚执行完，第 298 行无条件 `put` 又把这个空 `ArrayList` 写回 map。

**最坏后果**：`reportRoom` 的 key 只增不减，每个开过一次的通报/接收训练房间都会留下一个空列表条目，永久驻留。同时第 298 行的 `put` 与并发 `addRoomReport` 的 `put`（:158）互相覆盖：退出者的 `put` 可能把另一个刚入房用户的列表整个覆盖掉（P1-3 的另一个触发面）。

**建议修复方向**：删掉第 297-298 行。`simulations` 是从 map 里取出的同一个引用，原地修改后不需要写回；空房间的清理交给上面的 `remove`。


---

### P1-6　手键组训房间表 check-then-put + joinUser 裸 ArrayList

**位置**：`src/main/java/com/nip/ws/WebSocketGeneralTickerPatService.java:40-44`、`:57`、`:63-71`、`:113-123`、`:158-171`；`src/main/java/com/nip/ws/model/GeneralTickerPatTrainRoomUserModel.java:20`

**现象**：

```java
// :40-44  非原子
GeneralTickerPatTrainRoomUserModel roomUser = PAT_ROOM.get(trainId);
if (roomUser == null) {
  roomUser = new GeneralTickerPatTrainRoomUserModel();
  PAT_ROOM.put(trainId, roomUser);
}
```

`GeneralTickerPatTrainRoomUserModel.joinUser` 是 `new ArrayList<>()`（model:20），被并发 `add`（:57）、`remove`（:167）、`forEach` 广播（:63、:113、:118、:145）三方向操作。此外 `GeneralTickerPatTrainController.java:57-61` 在 HTTP 线程上对同一个 `joinUser` 做 `new ArrayList<>(trainRoomUser.getJoinUser())` 拷贝，拷贝构造内部走 `toArray()`，同样会撞上并发修改。

**竞态窗口**：两个学员几乎同时连入同一 `trainId`（组训场景下全班同时点进入是标准操作）。

**最坏后果**：
- `PAT_ROOM` 竞态：后写的 `put` 覆盖前一个房间对象，先进的学员进了一个孤儿房间，教员永远看不到他，他也收不到任何广播（后续 `onMessage` 取的是 `PAT_ROOM.get` 的胜者）。
- `joinUser` 竞态：遍历广播时抛 `ConcurrentModificationException`，或静默丢人。
- `onClose`（:168-171）判空后 `PAT_ROOM.remove(trainId)` 与并发 `onOpen` 的 `put` 之间也无同步，可能把刚建的房间删掉。

**建议修复方向**：`PAT_ROOM.computeIfAbsent(trainId, k -> new GeneralTickerPatTrainRoomUserModel())`；`joinUser` 字段初始化改 `new CopyOnWriteArrayList<>()`；房间销毁改 `PAT_ROOM.computeIfPresent(trainId, (k,v) -> (v.getJoinUser().isEmpty() && v.getGroupUser()==null) ? null : v)`。

---

### P1-7　电子键/数据报端点 onOpen 捕获异常后不 return，继续用空对象执行

**位置**：`src/main/java/com/nip/ws/WebSocketGeneralKeyPatService.java:47-68`；`src/main/java/com/nip/ws/WebSocketGeneralTelexPatService.java:47-68`

**现象**：

```java
GeneralPatTrainUserDto userDto = null;
try {
  userDto = generalKeyPatService.getTrainUserInfo(uid, trainId);   // 用户不在训练名单会抛 IllegalArgumentException
} catch (Exception e) {
  log.error(...);
  sendErrMessage(session, e.getMessage(), "", "");
  close(session);
}                                                                   // <- 没有 return，继续往下走
GeneralPatTrainUserModelDto userModel = PojoUtils.convertOne(userDto, GeneralPatTrainUserModelDto.class);
userModel.setSession(session);
userModel.setStatus(1);
...
if (userModel.getRole().compareTo(0) == 0) {   // :68 NPE
```

`getTrainUserInfo` 在用户或训练关联不存在时明确 `throw new IllegalArgumentException("训练数据异常")`（GeneralKeyPatService.java:475-477）。此时 `userDto` 为 `null`，`PojoUtils.convertOne`（common/utils/PojoUtils.java:75-79）先 `newInstance()` 再 `BeanUtil.copyProperties(null, entity, ...)` —— hutool 对 null 源直接返回，所以拿到的是**字段全 null 的新对象而非 null**，绕过了任何 null 判断，直到 `userModel.getRole()` 才炸。

**最坏后果**：非法 uid 连入即在 `onOpen` 抛 NPE，端点没有 `@OnError`，异常只落在容器日志里；紧接着 `onClose` 又因为 P1-8 再抛一次 NPE。同时因为异常发生在 `ROOM.put` 之前，房间没建起来，后续所有合法用户的 `onMessage` 会走到房间不存在分支。

**建议修复方向**：`catch` 块末尾加 `return;`。

---

### P1-8　电子键/数据报端点 onClose 未判空即解引用，且清理逻辑被整体包在"有教员"条件里

**位置**：`src/main/java/com/nip/ws/WebSocketGeneralKeyPatService.java:127-153`；`src/main/java/com/nip/ws/WebSocketGeneralTelexPatService.java:119-145`

**现象**：两个独立缺陷叠在同一段：

```java
GeneralPatTrainRoomUserDto keyPatTrainRoomUser = ROOM.get(trainId);
if (keyPatTrainRoomUser.getGroupUser() != null) {   // (1) ROOM.get 可能为 null -> NPE
  ...
  joinUser.remove(removeModel);                     // (2) 唯一的移除操作在 if 内部
}
close(session);                                      // (3) NPE 时不会执行
```

(1) `ROOM` 里没有该 `trainId` 时（P1-7 导致 onOpen 中途抛出，或训练已被 `GeneralKeyPatService.delete` 第 235 行 `ROOM.remove(trainId)` 清掉）直接 NPE。

(2) 教员未连入或已退出（`setGroupUser(null)`，:139）时，`groupUser == null`，整个 if 被跳过，**学员永远不会从 `joinUser` 里移除**。

(3) 两个文件的 `ROOM` map **在整个端点里没有任何一处 `remove`**（唯一的清理在 `GeneralKeyPatService.delete:235` / `GeneralTelexPatService.delete:402`，需要人工删训练才触发）。

**最坏后果**：
- 会话泄漏：教员不在线时学员的进出全部不清理，`joinUser` 单调增长；训练不删除则 `ROOM` 条目永不释放。
- 教员重新连入后 `roomUser.getJoinUser().forEach(...)`（:78）会向一堆已关闭的死会话发送（虽有 `isOpen()` 保护不抛异常，但教员端的在线学员列表 `GeneralKeyPatTrainController.java:31-35` 直接读这个表）会把早已离线的人显示为在线，直接影响组训点名。
- NPE 分支还会跳过 `close(session)`，连底层 Session 也不主动关闭。

**建议修复方向**：先 `if (roomUser == null) { close(session); return; }`；把 `joinUser.remove(...)` 和教员退出置空提到 `groupUser` 判空之外；`joinUser.isEmpty() && groupUser == null` 时 `ROOM.remove(trainId)`。

---

### P1-9　onlineId 是裸 ArrayList，被多线程增删且退出时重复删除

**位置**：`src/main/java/com/nip/ws/WebSocketService.java:50`、`:74`、`:83`、`:92-94`、`:148-154`；`src/main/java/com/nip/ws/StartWebSocket.java:22`、`:44`、`:50`、`:57`

**现象**：

```java
private static List<String> onlineId = new ArrayList<>();   // WebSocketService.java:50

@OnClose
public void onClose(@PathParam("sid") String sid) {
  removeClient(sid);          // :92 -> 内部已经 onlineId.remove(sid)（:152）
  webSocketClientSet.remove(this);
  onlineId.remove(this.sid);  // :94 又删一次，而 this.sid 是最后一次 onOpen 写入的值
}
```

两个问题：(1) `ArrayList` 被 `onOpen`/`onClose`（不同 IO 线程）并发 `add`/`remove`，无同步。(2) `removeClient(sid)` 已经删过一次，第 94 行用共享的 `this.sid`（P0 根因同款）**再删一个别人的 sid**。注意 `onlineId` 里可以有重复元素（`onOpen` 无条件 `add`），`remove(Object)` 只删第一个匹配项，所以计数长期失真。

**最坏后果**：并发下 `ArrayList` 内部 `elementData`/`size` 不一致，可能抛 `ArrayIndexOutOfBoundsException`，或静默丢/留元素；叠加重复删除，`onlineId` 与实际在线集合完全对不上。所幸 `webSocketServerSet` 恒为 null（见 P2-6），这份列表当前没有真正被广播出去，所以影响限于日志与内存，未升级为 P0。

**建议修复方向**：删掉 `onlineId` 这个冗余结构，在线 sid 直接从 `webSocketClientSet` 派生；若要保留，换 `ConcurrentHashMap.newKeySet()` 并去掉第 94 行的重复删除。

---

### P1-10　StartWebSocket 把单例 this 放进 Set，集合恒定只有一个元素

**位置**：`src/main/java/com/nip/ws/StartWebSocket.java:19-31`、`:47-51`、`:55-57`

**现象**：`StartWebSocket` 是 `@ApplicationScoped` 单例且**没有覆写 `equals`**（无 `@Data`），`webSocketClientSet.add(this)`（:48）走 identity 相等。第二次及以后的 `add` 全部返回 false。

**最坏后果**：
- `webSocketClientSet` 里永远只有 1 个元素，`log.info("Online Clients: {}", webSocketClientSet.size())`（:51）恒为 1。
- 任意一条连接 `onClose` 执行 `webSocketClientSet.remove(this)`（:56）就把唯一元素移走，此后所有仍在线的连接都不在集合中。
- `onOpen` 开头的踢掉同 sid 旧连接循环（:40-46）因此几乎永远匹配不到，重连不会踢旧连接。
- `this.session` 被每条新连接覆盖，`sendMessage`（:80）恒定发给最后一个。

**建议修复方向**：与 P0-1 相同 —— 存 holder 值对象而非 `this`，或端点改 `@Dependent`。

---

### P1-11　广播路径用 getBasicRemote() 同步写，同一 Session 会被多线程并发写入

**位置**：`WebSocketSimulationService.java:634`、`WebSocketGeneralTickerPatService.java:178/191/198`、`WebSocketGeneralKeyPatService.java:159/169`、`WebSocketGeneralTelexPatService.java:151/161`、`WebSocketUnionService.java:497`、`StartWebSocket.java:80`

**现象**：所有广播都走 `session.getBasicRemote().sendText(...)`。JSR-356 规定同一 `RemoteEndpoint.Basic` 上的写操作不可重入、不可并发；并发调用会抛 `IllegalStateException`（Undertow 的 The remote endpoint was in state [TEXT_FULL_WRITING]）。

**并发来源是真实存在的，不是理论风险**：同一个学员的 Session 会被
- 其他学员消息触发的 `messageHandleDisturb`/`messageHandleRouter` 广播（多个 WS IO/worker 线程，每人一条），
- `onOpen`/`onClose` 的上下线通知（另一条连接的线程），
- REST 线程（`SimulationRouterRoomService.changeChannel` 路径下的后续推送）

同时写入。`sendMessage` 里的 `try/catch` 只捕获 `IOException`（:636），`IllegalStateException` 是 `RuntimeException`，会直接冒出到广播循环，**打断整轮广播** —— 循环里后面的所有人都收不到这条消息。

**竞态窗口**：干扰训练的高频推送场景下，窗口等于一次 `sendText` 的网络写时间，命中概率随房间人数平方增长。

**最坏后果**：`IllegalStateException` 从广播循环里逃逸，导致开始训练/结束训练这类关键指令只送达了一半人，房间状态分裂（一半学员在训、一半已停）。

**建议修复方向**：改用 `getAsyncRemote().sendText(msg, result -> {...})`（Undertow 内部对 async 写做了排队），或者为每个 `Session` 持有一把锁在 `sendText` 外 `synchronized`。同时把 `catch (IOException e)` 放宽到 `catch (Exception e)`，保证单个接收方失败不中断整轮广播。

---

### P1-12　仿真消息处理未判空即遍历房间列表

**位置**：`src/main/java/com/nip/ws/WebSocketSimulationService.java:463`（`messageHandleReport`）、`:531`（`messageHandleRouter`）

**现象**：

```java
List<WebSocketSimulationService> socketSimulations = SimulationGlobal.reportRoom.get(roomId);  // :463 可能为 null
...
for (WebSocketSimulationService socketSimulation : socketSimulations) { ... }                    // :481 NPE
```

`messageHandleDisturb`（:348-351）做了判空，这两个没做。

**触发条件**：
- 教员在 REST 端调用 `SimulationReportRoomService.delete` / `SimulationRouterRoomService.delete`，其中 `SimulationGlobal.reportRoom.remove(roomId)`（SimulationReportRoomService.java:204、SimulationRouterRoomService.java:327）把房间条目删掉，但**没有关闭任何仍连着的 Session**；此时任一在线客户端发来的下一条消息就是 NPE。
- 或 `onOpen` 因 P1-3 竞态失败没入表。

另有 `messageHandleReport:497`、`:506`、`messageHandleRouter:557` 使用 `getUserModel().getChannel().compareTo(0)`，而 `onOpen` 在用户不在房间名单时把 `channel` 置为 -1 但仍可能为 `null`（`convertOne` 复制失败时），同样有解引用风险。

**最坏后果**：教员删除房间后，房内所有客户端的每一条消息都触发一次 NPE；端点无 `@OnError`，客户端表现为发出去石沉大海，无任何错误反馈。

**建议修复方向**：两处补上与 `messageHandleDisturb` 一致的判空 + `sendErrorMessage` 反馈；REST 侧的 `delete` 在 `remove` 后遍历原列表主动 `session.close()`。

---

### P1-13　把 ThreadLocalRandom.current() 缓存进 static 字段，不同线程生成完全相同的"随机"报文

**位置**：`src/main/java/com/nip/service/general/GeneralTelexPatService.java:71`，使用点 `:581`、`:587`、`:594`

**现象**：

```java
private static final ThreadLocalRandom RANDOM = ThreadLocalRandom.current();
```

`ThreadLocalRandom.current()` 的契约是每次用都现调：它返回单例，但 `localInit()` 只在**当前调用线程**上初始化该线程的 `threadLocalRandomSeed`/`probe` 字段；`nextSeed()` 读写的也是**当前线程**的 seed 字段。把返回值缓存到 static，等于让所有从未调用过 `current()` 的线程带着 `seed == 0` 去用它。

**最坏后果**：任何两个未初始化过 ThreadLocalRandom 的工作线程，seed 序列都从 0 开始按相同的 GAMMA 步进，产生**逐位相同的随机序列**。表现为：两名学员在不同工作线程上同时请求生成数据报报文，拿到的是**完全一样的报文内容**；训练内容失去随机性，且可预测。这不是概率性偶合，是确定性的。

**建议修复方向**：删掉 static 字段，在使用处直接写 `ThreadLocalRandom.current().nextInt(10)`。


---

## P2（8 个）

### P2-1　联合训练 onOpen 用 runAsync().join() 把异步又变回同步，且有一段空的 runAsync().join()

**位置**：`src/main/java/com/nip/ws/WebSocketUnionService.java:73-85`、`:210-213`

`CompletableFuture.runAsync(...).join()` 把 DB 查询丢到 `ForkJoinPool.commonPool`，然后立刻阻塞调用线程等它 —— 既没有异步收益，又把阻塞 JDBC 放进了 commonPool（该池是全 JVM 共享的、按 CPU 核数定容的计算池，被阻塞任务占满会拖累一切 `parallelStream`）。`getUnionInfo` 里的 `CompletableFuture.runAsync(()->{}).join();`（:211-213）注释写着切换成工作线程，但 lambda 是空的，纯粹是无副作用的死代码。此外 `join()` 会把 `userDao` 抛出的异常包成 `CompletionException` 抛出，与直接调用的异常语义不同。

**建议**：删掉 `runAsync/join` 包装，直接同步调用；:211-213 整段删除。

### P2-2　onMessage 把阻塞 JDBC 丢进 commonPool 且不处理异常

**位置**：`src/main/java/com/nip/ws/WebSocketService.java:116-120`、`:123-127`

`CompletableFuture.runAsync(() -> webSocketEventService.saveTelegramTrainLog(...))` 没有 `exceptionally`/`whenComplete`，任何持久化失败（约束冲突、连接超时）都被 `CompletableFuture` 静默吞掉，训练日志丢了也不会有任何记录。手键训练是高频消息，每条都往 commonPool 扔一个阻塞任务，会挤占该池。

**建议**：改用 `@Inject ManagedExecutor`（`quarkus-smallrye-context-propagation` 已在 pom.xml:129-132）并补 `.exceptionally(t -> { log.error(...); return null; })`。

### P2-3　kickOutOld 在索引 for 循环中删除元素，会漏踢

**位置**：`src/main/java/com/nip/ws/WebSocketSimulationService.java:647-657`

`for (int i = 0; ...)` 内部 `simulations.remove(...)` 会让后续元素前移，下一轮 `i++` 跳过一个。同一 userId 若因 P1-4 累积了多条残留会话，一次只能踢掉约一半。

**建议**：改 `removeIf`，或倒序遍历。

### P2-4　findMessageBody 的 synchronized(this) 把全应用的报文生成串行化

**位置**：`src/main/java/com/nip/service/general/GeneralTickerPatService.java:248`

该 bean 是 `@ApplicationScoped` 单例，`synchronized (this)` 等于一把全局锁，且锁内包含数据库查询、报文生成和批量落库。全班同时翻页时全部排队，锁持有时间是数据库往返级别。锁的目的（防止同一页被并发生成两份）是对的，但粒度错了，且**多实例部署时完全失效**。

**建议**：把锁粒度收到 `trainId + floorNumber`（如 `ConcurrentHashMap<String, Object>` 条带锁），或依赖数据库唯一约束 + 冲突重查。

### P2-5　多个 WS 端点缺少 @OnError

**位置**：`WebSocketSimulationService.java`（全文件无 `@OnError`）、`WebSocketGeneralTickerPatService.java`、`WebSocketGeneralKeyPatService.java`、`WebSocketGeneralTelexPatService.java`

本报告里 P1-7、P1-8、P1-12 提到的 NPE 全部发生在这些端点里。没有 `@OnError` 意味着异常只进容器日志，客户端拿不到任何错误码，排障时只能靠翻日志猜。

**建议**：每个端点补 `@OnError`，至少记录 sessionId + pathParam + 堆栈，并向客户端回一条错误帧。

### P2-6　webSocketServerSet 永远为 null，在线用户列表广播是死代码

**位置**：`src/main/java/com/nip/ws/WebSocketService.java:52`、`:95-98`；`src/main/java/com/nip/ws/StartWebSocket.java:26`、`:58-63`

`private static WebSocketService webSocketServerSet;` 在全仓库没有任何赋值语句（已 grep 确认），`if (null != webSocketServerSet)` 恒假。`USER_LIST` 广播从未发出过，`onlineId` 的全部维护成本（含 P1-9 的并发问题）都是白花的。

**建议**：连同 `onlineId` 一起删除，或补上真正的赋值逻辑。

### P2-7　StatusWebSocket 的 session 字段同样被跨连接共享

**位置**：`src/main/java/com/nip/ws/StatusWebSocket.java:14-30`

同为 `@ApplicationScoped` 单例，`onClose()`（:28-30）打印的是**共享字段**里最后一次 `onOpen` 写入的 session id，日志永远对不上真正断开的那条连接；若进程启动后第一条消息就是 `onClose`（异常路径），`session` 为 null 直接 NPE。该端点只做 ping/pong，影响有限。

**建议**：`onClose(Session session)` 直接声明形参，不用实例字段。

### P2-8　房间删除只清内存 map，不关闭仍连着的 Session；quitRoomReport 里残留 System.out.println

**位置**：`SimulationRouterRoomService.java:327`、`SimulationReportRoomService.java:204`、`SimulationReceptRoomService.java:208`、`SimulationRouterRoomContentService.java:209`、`GeneralKeyPatService.java:235`、`GeneralTelexPatService.java:402`、`GeneralTickerPatService.java:236`；`WebSocketSimulationService.java:297`

七处 `delete` 都只做 `XXX.remove(roomId/trainId)`，客户端 Session 仍然挂着。客户端不知道房间没了，继续发消息（触发 P1-12 的 NPE），且 `onClose` 时 `quitRoom*` 因为 map 里已无条目而直接 return，什么也不清理。另外 `WebSocketSimulationService.java:297` 的 `System.out.println(simulations.size())` 是调试残留，绕过日志框架直接写 stdout。

**建议**：`delete` 里先取出列表、逐个 `session.close(new CloseReason(...))` 再 `remove`；删掉第 297 行。

---

## 附：跨分片交叉引用

以下问题的根因在本分片，但受影响的调用方在其他 agent 的范围内，已在对应条目中标注，此处汇总便于合并：

- `GeneralTickerPatTrainController.java:31-42`、`:56-61` 与 `GeneralKeyPatTrainController.java:31-35`：在 HTTP 线程上直接读 WS 端点的静态房间表（P1-6、P1-8）。
- `SimulationWebSocketController.java:28-34`：在 HTTP 线程上遍历 `SimulationGlobal` 三张表（P1-2）。
- `PostTelegraphKeyPatTrainService.java:340-361`：与 P1-1 完全同型的 parallelStream + 删原始记录模式。

## 附："多页评分并行修改非线程安全集合后删除原始记录" 模式全量排查结果

全仓库 `parallelStream()` / `.parallel()` 共 **2 处**，两处都命中该模式，无遗漏：

| 位置 | 共享集合 | 共享累加对象 | 随后的删除 |
|---|---|---|---|
| `service/general/GeneralKeyPatService.java:643-661` | `pageValueResult` (ArrayList) | `keyPatStatistics` (KeyPatStatisticalDto) | `resolverDao.deleteByTrainIdAndUserId`:657 / `userValueDao.deleteByTrainIdAndUserId`:660 |
| `service/PostTelegraphKeyPatTrainService.java:344-356` | `pageValueResult` (ArrayList) | `ks` (KeyPatStatisticalDto) | `valueDao.deleteByTrainId`:359 |

两处都共用 `common/utils/KeyPatUtils.handle`（:20-24），该方法内部对传入的 `KeyPatStatisticalDto` 做无同步的 read-modify-write 累加（:48、:81、:84 等），是两处统计错误的共同根因。

## 附：已接受安全风险

本次范围内未发现需单独记录的新增纯安全项（WS 端点无认证、`{uid}` 可任意伪造属于内网部署已接受风险，沿用 2026-08-15 报告结论，不重复记录）。

