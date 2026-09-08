# 静默失败（吞异常）全仓扫描报告

- 范围：`src/main/java/com/nip/` 全部
- 日期：2026-08-26
- 方法：静态阅读（本机无 Java，未运行任何构建/测试）；每条均已 grep 到实际调用方，非推测

---

## 结论

**不通过。全仓有 7 处 P0 级静默失败，其中 6 处会造成数据永久损坏、1 处会造成训练成绩集体不结算。**

根本模式只有三个，全部可验证：

1. **`@Transactional` 方法内 `catch` 不重抛 = 事务照常提交。** Jakarta `@Transactional` 只在异常**逃出方法**时回滚。这些方法写成 `try { 先批量删除；再逐条重建 } catch (Exception e) { return error(); }`，异常被吞住不重抛 → 拦截器看到方法正常返回 → **提交**。删除已生效、重建未完成，数据不可逆丢失。
2. **触发源高度集中在 `UserService.getUserByToken`（UserService.java:479-481）。** 它直接返回 `UserDao.findUserEntityByToken`（UserDao.java:78-79 用 `.firstResult()`），**token 过期返回 null 而不是抛异常**，下游 `userEntity.getId()` 必 NPE。NPE 不是持久化异常，JTA 不会 `setRollbackOnly`，所以模式 1 的提交是真实发生的。
3. **全仓没有任何全局异常映射器。** grep `@Provider` / `ExceptionMapper` / `@ServerExceptionMapper` 零匹配。加上 `JWTInterceptor.java:83` 的日志**没有把异常对象传进去**，全站 52 个 Controller 的业务异常堆栈 100% 丢失。

---

## 统计

| 级别 | 条数 | 含义 |
|---|---|---|
| P0 | 7 | 数据永久损坏 / 功能不可用 |
| P1 | 20 | 严重缺陷 |
| P2 | 12 | 功能瑕疵 / 可观测性缺陷 |
| 明确排除 | 9 | 死代码、不可达 catch、正确传播、注释代码 |

---

## P0

### P0-1 · TickerPatUtils 四个空 catch 导致学员拍发记录被空数组永久覆盖

- **位置**：`src/main/java/com/nip/common/utils/TickerPatUtils.java:286` / `:303` / `:308` / `:313`（均为 `catch (Exception ignore) {}`，连一行日志都没有）
- **吞掉的异常**：`Exception`；实际是 `JSONUtils.fromJson` 内部 Gson 抛的 `JsonSyntaxException` / `JsonIOException`
- **调用方拿到什么**：`pk`/`logs`/`times`/`values` 保持 null → `:315-318` 兜底成空列表 → `:451-468` 把空列表补齐成与 `patKeys` 等长的**全空数组**（形状合法，下游 `!= null` 判断全部失效，因为值非 null 只是空）→ `:473-485` 重建对象
- **落库路径**：`PostTelegramTrainService.java:526` `handleMessageBody(...)` → `:527` `e.setMessageBody(...)` → `:534` `contentValueDao.deleteByTrainIdAndFloorNumber(...)` **先删旧行** → `:535` `saveAndFlush(valueEntity)` 存空数据。方法 `:508` 标注 `@Transactional(rollbackOn = Exception.class)`，但异常已在 TickerPatUtils 内被吞，**没有异常逃出 → 不回滚**
- **用户可见最坏症状**：学员提交报底后前端显示"保存成功"（`PostTelegramTrainController.java:131-133` 无条件 `ResponseResult.success()`），实际这一页的手键点划时长记录已被清空、旧记录已删除、**永久无法恢复**
- **附带**：`:288-296` 的兜底会把原始字符串**逐字符拆分**成 `["[", "\"", "1", ...]` 写回数据库

### P0-2 · TickerPatUtils:520 解析失败导致评分静默变满分

- **位置**：`src/main/java/com/nip/common/utils/TickerPatUtils.java:520-523` — `catch (Exception e) { p = new ArrayList<>(); }`
- **吞掉的异常**：`Exception`；实际 `JsonSyntaxException`（`patLogs` 串非法）
- **调用方拿到什么**：`checkDotLineGap` 返回 `void`，结果靠 out 参数 `scoreVO`/`statisticsVO` 累加。`p` 变空 → `:528` 条件不成立 → `logs` 保持空 `ArrayList` → **`:569` 的 `if (logs == null) continue;` 拦不住空列表** → `:573` 的 `for (k < logs.size())` **零次迭代** → dot/line/code/word/group 五项扣分一分不加
- **调用点（7 处）**：`MessageComparisonService.java:189` / `:209` / `:219` / `:230` / `:245`，`GroupDetector.java:174`，`LineDetector.java:223`
- **用户可见最坏症状**：`PostTelegramTrainService.java:704-727` 的 `applyDeductions` 是 `score -= dotScore` 系列，扣分全 0 → **拍发数据损坏的学员反而拿满分**，同时成绩单上"点虚/点粗/划虚/完美数"全为 0，教员看到自相矛盾的统计却无法察觉评分已失效

### P0-3 · TickerPatUtils:79 一个字段解析失败连坐清空另外两个

- **位置**：`src/main/java/com/nip/common/utils/TickerPatUtils.java:79-84`
- **吞掉的异常**：`Exception`；实际 `JsonSyntaxException`
- **特殊危害**：`:66-78` 把三次 `fromJson` 串在**同一个 try** 里。若 `patLogs` 已解析成功、`moresTime` 非法，`:81` 会把**已经解析好的 `patLogs` 一并重置成空**
- **调用方拿到什么**：`resolverMessage` 循环 `:94-108` 走 else 分支 → `logs.add(new ArrayList<>())`、`times.add("[]")`、`values.add("[]")` → `:257-260` 塞进 `PostTelegramTrainResolverVO` → `MessageComparisonService.java:90` 接收 → `:122-124` 复现 P0-2 的零扣分；同时 `PostTelegramTrainService.java:676` 与 `GeneralTickerPatService.java:800` 把空结果 `setResolver(...)` 落库
- **用户可见最坏症状**：结训成绩虚高，且"训练详情/回放"里整页拍发轨迹是空白，学员点开看不到自己的点划波形

### P0-4 · 结束训练时在线人员查询失败 → 全体学员成绩不结算且不可重来

- **位置**：`src/main/java/com/nip/service/general/GeneralTickerPatService.java:560-562` — `catch (Exception ex) { log.error("获取训练用户信息失败，训练ID: {}", dto.getTrainId(), ex); }`
- **吞掉的异常**：`Exception`（`:556-559` 手动 `new GeneralTickerPatTrainController()` 后调 `findUserInfo`，WS 房间不存在/内部 NPE 均会抛）
- **调用方拿到什么**：`userDto` 保持**空 List** → `:563` `userId` 空 → `:565-567` `filter(userId::contains)` 过滤出**空集合** → `:568-578` 的 `for` 循环**一次都不执行**，`finish(vo)` 从未被调用
- **不可逆点**：`:580` `trainDao.save(tickerPatTrain)` **无条件执行**，训练 `status` 已在 `:538` 置为 2（已结束）、`endTime`/`validTime` 已写入。该方法 `:535` 是 `@Transactional`，异常被吞 → 提交。之后 `status` 已是 2，不会再次进入 `:542` 分支
- **用户可见最坏症状**：教员点"结束训练"，接口正常返回，训练列表显示"已结束"，但**全班所有学员的拍发成绩都没有结算**（分数/正确率/速率全空），且无法重新触发结算，成绩永久丢失

### P0-5 · 保存试卷：先清空全部题目，再 NPE，事务照常提交

- **位置**：`src/main/java/com/nip/service/TestPaperService.java:89-92`（方法 `:59` 为 `@Transactional`）
- **吞掉的异常**：`NullPointerException`。路径：`:65` `testPaperQuestionDao.deleteAllByTestPaperId(id)` 已清空该试卷全部题目 → `:71` `userService.getUserByToken(token)` token 过期返回 **null** → `:72` `userEntity.getId()` NPE
- **为什么会提交**：NPE 不是持久化异常，Hibernate 不会 `setRollbackOnly`；catch 住不重抛 → 方法正常返回 → 提交
- **调用方拿到什么**：HTTP **200** + body `{code:500, data:null, message:"服务器错误"}`（`Response` 是普通 POJO，HTTP 状态码恒为 200）
- **用户可见最坏症状**：教员编辑一份已有试卷时登录恰好过期，页面提示"服务器错误"，试卷仍在列表里但**打开后题目全空，原题目永久丢失**
- **日志**：`:90` `log.error("保存试卷失败：{}", e.getMessage())`，无堆栈

### P0-6 · 保存理论知识点：先删课件与测验，再 NPE，事务照常提交

- **位置**：`src/main/java/com/nip/service/TheoryKnowledgeService.java:292-295`（方法 `:223` 为 `@Transactional`）
- **吞掉的异常**：`NullPointerException`。路径：`:231` `knowledgeSwfDao.deleteAllByKnowledgeId(...)` 删除全部课件 → `:276` `theoryKnowledgeTestContentDao.deleteByKnowledgeIdAnd...` 删除测验内容 → `:257-259` `findFirstByKnowledgeSwfIdAndVersions(...)` 返回 null 时 `.getId()` NPE
- **调用方拿到什么**：HTTP 200 + `{code:500, data:null}`
- **用户可见最坏症状**：编辑理论知识点保存报错，但**课件和随堂测验题已被删除且未完整写回，知识点内容永久残缺**
- **日志**：`:293` `log.error("保存失败:{}", e.getMessage())`，无堆栈

### P0-7 · 保存菜单：先删按钮权限，再 NPE，事务照常提交，零日志

- **位置**：`src/main/java/com/nip/service/MenusService.java:113-115` — `catch (Exception e) { return null; }`，**catch 体内完全没有日志**（方法 `:83` 为 `@Transactional`）
- **吞掉的异常**：`Exception`；最典型是 `NullPointerException`——请求体不带 `permissions` 时 `:104` `permissions.forEach` 直接 NPE
- **为什么会提交**：`:102` `menusButtonDao.deleteAllByMenusId(menus.getId())` 走 `MenusButtonDao.java:20-22` 的 Panache `delete("menusId", menusId)`，是**立即下发的批量 DML**；NPE 不触发 rollback-only；方法 `return null` 正常结束 → 提交
- **调用方拿到什么**：`null` → `MenusController.java:46-49` 判 null 返回 `SYSTEM_ERROR`
- **用户可见最坏症状**：编辑菜单时接口报"系统错误"，但该菜单下**所有按钮权限行已被永久删除且没有重建**，所有角色的这些按钮一起失效，重试无法恢复，**日志里没有任何记录**

---

## P1

### P1-1 · JWTInterceptor 丢弃全站所有业务异常堆栈

- **位置**：`src/main/java/com/nip/common/interceptor/JWTInterceptor.java:82-85`
- **证据**：`:83` `log.error("method error from {}.{}\n", 类名, 方法名)` —— 格式串两个占位符、两个参数**全被类名方法名占满，`exception` 根本没作为参数传入**，SLF4J 不会追加堆栈
- **影响面**：`@JWT` 是**类级注解**，`ComprehensiveController.java:26`、`MenusController.java:30` 等 **52 个 Controller** 全量套用；`:76` 的 `context.proceed()`（业务方法真正执行处）在 try 内，所以全站每个业务方法抛出的异常都落进这个 catch
- **调用方拿到什么**：`ResponseResult.error(SYSTEM_ERROR, e.getMessage(), e.getMessage())`，NPE 时 message 为 null → 前端收到 `message: null` 的"服务器错误"
- **用户可见最坏症状**：任意后台报错，用户只看到"服务器错误"，日志只有一行 `method error from XxxController.yyy`，**无法知道哪行出错**。这是全仓排障能力归零的单点。修复只需把 `exception` 加为末位参数

### P1-2 · 手键拍发 WS 发送失败完全无声（日志被注释掉）

- **位置**：`src/main/java/com/nip/ws/WebSocketGeneralTickerPatService.java:183-186`
- **证据**：
  ```java
  } catch (IOException e) {
  //      log.error("发送消息失败：{}", e.getMessage());
        return false;
  ```
  日志行**被注释掉**，`IOException` 产生**零输出**
- **调用方拿到什么**：`boolean`。9 个调用点中只有 `:52` 和 `:64` 检查了返回值；`:92` `:99` `:111` `:118` `:125` `:149` `:164` **七处全部丢弃返回值**。另外 `:180-182` 在 `session.isOpen()` 为 false 时也静默 `return false`
- **用户可见最坏症状**：手键拍发训练中，教员的"开始训练/结束训练"指令或学员的"准备/完成"回执因连接半关闭而发送失败时，**消息静默丢弃，无日志、无重试、无通知**；教员界面显示已开始，学员端毫无反应，双方都不知道发生了什么

### P1-3 · 联合训练 onError 把 Throwable 整个扔掉

- **位置**：`src/main/java/com/nip/ws/WebSocketUnionService.java:471-474` — `public void onError(Session session, Throwable error) { log.error("发生错误"); }`
- **证据**：`error` 形参**完全没用上**，无异常类型、无 message、无堆栈、无 session 标识
- **为什么这是"吞"而不是"日志差"**：这个 `@OnError` 是该端点所有未捕获异常的终点。`onMessage` 的分发路径上有大量无保护解引用——`:106-107` `JSONUtils.fromJson(...)` 后直接 `map.get("code").toString()`（`JSONUtils.java:26-28` 对空串返回 null）；`:204` `getRoomInfo`、`:246` `updateRoomUser`、`:301-303` `removeRoom`、`:365` `exitRoom`、`:420-421` `seatInspect`、`:437` `seatInspectReply`、`:465-466` `roomStatusChange` 全部是 `onlineRooms.get(...)` 后立即解引用，房间已解散时必 NPE
- **额外放大**：`onClose` → `userExit()` 的 `:180` `webSocketClientSet.get(user.getId()).sendMessage(...)`，房间内有人已断开时 `get` 返回 null → NPE → 中断整个 `for (RoomModel : onlineRooms.values())` 循环 → `:186-187` 的 `webSocketClientSet.remove(...)` / `onlineUsers.remove(...)` **永远不执行**
- **用户可见最坏症状**：用户已断开但联合训练在线列表里一直显示在线（幽灵用户），房间踢不掉、房间数持续增长；排障时日志里只有一行"发生错误"

### P1-4 · 综合组训 WS onOpen 错误分支缺 return，继续用 null 往下走

- **位置**：`src/main/java/com/nip/ws/WebSocketGeneralKeyPatService.java:51-57`；同型 `src/main/java/com/nip/ws/WebSocketGeneralTelexPatService.java:51-57`
- **证据**：
  ```java
  } catch (Exception e) {
    log.error("WebSocketGeneralKeyPatService.onOpen: 用户不存在");
    sendErrMessage(session, e.getMessage(), "", "");
    close(session);
  }                                    // <-- 没有 return
  GeneralPatTrainUserModelDto userModel = PojoUtils.convertOne(userDto, ...);  // userDto 仍是 null
  ```
- **两重吞异常**：(a) `:53` 的日志是**硬编码字符串"用户不存在"**，既不传 `e` 也不打堆栈——而 `GeneralKeyPatService.java:350-352` 的 catch 会把**数据库异常**包成 `RuntimeException` 抛上来，于是一次 DB 故障被记成"用户不存在"，诊断方向被彻底带偏；(b) 缺 `return` 导致 `:57` 用 `null` 调 `convertOne` 得到全 null 字段的对象，`:68` `userModel.getRole().compareTo(0)` 二次 NPE
- **该端点没有 `@OnError`**（全文件无 `@OnError`），二次 NPE 直接冲到容器
- **用户可见最坏症状**：数据库抖动时学员进不了组训房间，收到的错误提示是"用户不存在"，运维按"用户数据问题"排查数小时，真实原因（DB 连接）在日志里一个字都没有

### P1-5 · 电传训练统计任务 fire-and-forget，异常永久丢失

- **位置**：`src/main/java/com/nip/service/TelexPatTrainService.java:73-74`
- **证据**：`CompletableFuture.runAsync(() -> statisticalService.statistical(...), managedExecutor)` —— 返回的 future 被直接丢弃，**没有 `.exceptionally()` / `.whenComplete()` / `.join()`**。异常被存进 future 后**永远无人读取，零日志零告警**（这是全仓唯一一处连 catch 都没有、彻底不可观测的地方）
- **吞掉的异常**：`TelexPatTrainStatisticalService.java:74-75` 的 `Integer.valueOf(patTrainEntity.getDuration())` / `Integer.valueOf(statistical.getTotalTime())` 对 null/空串抛 `NumberFormatException`（暂停中的训练 duration 常为空）
- **额外缺陷**：`:73` `updateStatus(FINISH)` 还在外层事务中**未提交**，异步线程通过 `BaseRepository`（类级 `@Transactional`，默认 REQUIRED）走**独立新事务**，`TelexPatTrainDao.java:32 getAvgSpeed` 过滤 `status=3` 看不到这条 → 平均速率少算一次
- **用户可见最坏症状**：上一次暂停的训练被自动置为"完成"，但统计页的总次数/总时长/平均速率**永远不含这次**，用户以为系统漏记，运维日志里一个字都没有

### P1-6 · 手键 WS 日志入库任务异常无人观察

- **位置**：`src/main/java/com/nip/ws/WebSocketService.java:116-119` 与 `:123-126`
- **证据**：两处 `CompletableFuture.runAsync(() -> { webSocketEventService.saveTelegramTrainLog(...); log.info(...); })`，无 `.exceptionally()`，且用**默认 ForkJoinPool.commonPool**（未传 executor）
- **吞掉的异常**：`WebSocketEventService.java:35` `saveTelegramTrainLog` / `:43` `saveTelegramTrainFloorContentEntity` 均为 `@Transactional` 写库，约束冲突/连接失败抛出的 `PersistenceException` 全部落进被丢弃的 future
- **叠加**：`:115` / `:122` 的 `PojoUtils.convertOne(model.get("data"), ...)` 在 `data` 键缺失时传入 null，得到**空白 entity** 后照样入库
- **用户可见最坏症状**：手键训练过程中的点划日志和码组内容写库失败时**完全静默**，训练结束后回放数据缺失或为空，前端和日志都毫无痕迹

### P1-7 · 修改密码把异常包装成 success

- **位置**：`src/main/java/com/nip/service/UserService.java:450-452` — `catch (Exception e) { return ResponseResult.success(MessageConstants.DATA_EXCEPTION, false); }`，**零日志**
- **吞掉的异常**：`NullPointerException`。路径：`:436` `userDao.findById(id)` 对不存在的 id 返回 null → `:438` `user.getPassword()` NPE
- **调用方拿到什么**：`ResponseResult.java:20-22` 的 `success(message, data)` **一律使用 `ResponseCode.SUCCESS`**，即 `code=200 / data=false / message="数据异常"`。这与 `:439`（旧密码错）、`:443`（两次不一致）的正常业务失败响应**结构完全相同**，只按 code 分支的客户端会渲染成"修改成功"
- **升级为 P0 的条件**：`:446` `user.setPassword(MD5Util.encrypt(newPassword))` 已弄脏托管实体；若非持久化异常发生在 `:447` 之后，事务提交会把脏字段刷库 → **密码实际已改却告诉用户失败**
- **用户可见最坏症状**：改密码提示成功但密码没改（下次登录被锁在账号外），服务端零日志，完全无法定位

### P1-8 · 退出登录永远返回成功，token 可能仍然有效

- **位置**：`src/main/java/com/nip/dao/UserDao.java:95-97` — `catch (Exception e) { return false; }`，**完全无日志**；`src/main/java/com/nip/service/UserService.java:417`
- **证据**：`UserDao.updateUser` 只更新 token/deviceId；`UserService.userOut:417` 调用后**完全忽略返回值**，`:418` 无条件 `return true`，`UserController.java:55` 包成 success。两个调用方待遇不一致——`login:386` 检查了返回值（正确），`userOut:417` 没有
- **附加**：Panache `update(...)` 的**受影响行数被直接丢弃**，更新 0 行也返回 true
- **用户可见最坏症状**：点"退出登录"前端显示已退出并跳登录页，但库里 token/deviceId 未清除，**旧 token 仍能通过 `JWTInterceptor.java:75` 的校验继续访问全部接口**；服务端零日志

### P1-9 · 分配角色：先删光全部角色再逐条插入，异常被吞

- **位置**：`src/main/java/com/nip/service/UserService.java:348-350`（方法 `:334` 为 `@Transactional`）
- **证据**：`:340` `userRoleDao.delete(USER_ID, userId)` 是**立即执行的批量 DML**，先把该用户全部角色删光，`:341-346` 再循环插入
- **分两种情形（诚实区分）**：持久化异常（FK 冲突等）会被 Hibernate 标记 rollback-only → 提交阶段抛 `RollbackException` 逃出方法 → **不会部分提交**，但精心构造的 `false`/"数据异常"被丢弃，客户端拿到**原始 HTTP 500**（项目无 ExceptionMapper，响应体不是 `{code,data,message}` 结构，前端解析失败）；非持久化异常则删除已提交、插入未完成 → **用户角色归零**
- **角色归零的下游放大（已验证）**：`login:390` `roleDao.findRoleByUserId(...)` 返回 null → `:391` `role.getIsAdmin()` NPE → 被 `:398` 的 catch 吞成"数据异常" → **该用户永久无法登录**
- **用户可见最坏症状**：分配角色报错后该用户角色被清空，此后每次登录只回"数据异常"，需 DBA 手工补数据才能恢复
- **日志**：`:349` `log.error("addUserRole error:{}", e.getMessage())`，无堆栈

### P1-10 · 角色菜单查询失败降级成空列表，被管理员误判后清空真实权限

- **位置**：`src/main/java/com/nip/dao/MenusDao.java:27-30` — `catch (Exception e) { log.error("查询角色菜单失败，roleId: {}", roleId, e); return Collections.emptyList(); }`（日志本身写法正确，带堆栈）
- **吞掉的异常**：`Exception`（命名查询 `find_menus_by_role_id` 失败、连接池耗尽、DTO 投影不匹配）
- **调用链（已 grep 确认）**：唯一调用点 `RoleService.java:112`，向上入口 `getRoleAll:84` / `getRoleById:94` / `getRoleMenusInfo:104`。**不在登录路径上**（登录走 `MenusDao.getMenusByRoleId` + `MenusService.getMenusDtos`，均无 catch）
- **真正的放大路径**：`getRoleMenusInfo` → `menusChecked` 为空 → 管理员打开"角色权限编辑"页看到**所有菜单未勾选** → 点保存 → `RoleService.java:65` `roleMenusDao.deleteAllByRoleId(...)` **先清空再按空列表重建**
- **用户可见最坏症状**：一次数据库抖动导致管理员误判"该角色没配权限"，保存后**该角色所有人的菜单权限被永久清空**，现场只留一条"查询角色菜单失败"

### P1-11 · 登录主链路上的空菜单降级

- **位置**：`src/main/java/com/nip/service/MenusService.java:226-228` — `catch (Exception e) { return new MenusDto(); }`，**零日志**
- **吞掉的异常**：`Exception`；实际来源 `:198` `om.readValue(firstByRoleIdAndMenuId.getPer(), ...)` 解析 `t_role_menus.per` 的 JSON 失败，或 `:184` `menusEntity.getIsMenu()` 为 null 时拆箱 NPE
- **调用方拿到什么**：位于登录主链路 `UserService.login:393` → `MenusService.getMenusDtosById:65` → 本方法。返回的空 `MenusDto` 的 id/parentId/key/path/name/component/meta/permissions **全为 null**（仅 sort 有默认 0，`MenusDto.java:26`，故 `Collections.sort` 不会 NPE），随后 `:66` 仍把真实 children 挂到这个空壳上
- **用户可见最坏症状**：某条 `role_menus.per` 数据损坏后，用户仍能登录成功，但**侧边栏出现一个无标题、无图标、点不进去的空菜单项，其下所有子菜单一并无法路由**；服务端零日志

### P1-12 · 清除电传记录：记录已删，统计表未更新，两边永久对不上

- **位置**：`src/main/java/com/nip/service/TelexPatService.java:90-92`（方法 `:78` 为 `@Transactional`）
- **吞掉的异常**：`NullPointerException`。路径：`:82` `telexPatDao.deleteByUserIdAndType(...)` 已删除训练记录 → `:84` `statisticalDao.findByUserIdAndType(...)` 走 `.firstResult()`（`TelexPatTrainStatisticalDao.java:39`）返回 **null** → `:85` `statisticalEntity.setTotalTime("0")` NPE → 吞掉 → 提交
- **用户可见最坏症状**：用户点"清除记录"看到"服务器错误"，但**记录确实没了、统计页还显示原来的次数和时长**，两边永久对不上且无法再清（记录已不存在，重试仍走同一条 null 路径）

### P1-13 · 开始新训练：上一次已被强制结束并计入统计，新训练留下残缺记录，零日志

- **位置**：`src/main/java/com/nip/service/TelegramTrainService.java:298-300`（方法 `:257` 为 `@Transactional`），**catch 体内完全无日志**
- **吞掉的异常**：`Exception`。触发前 `:270` `save(lastTrain)`（上次训练置为已完成 3）、`:271` `finishStatistical(...)`（写统计表）、`:273` `deleteById(...)` 可能都已执行；随后 `:277` / `:283` / `:294` 三层保存中任一非持久化异常（如 `:291` `floorContents.get(j)` 越界/空指针）被吞 → 提交
- **用户可见最坏症状**：点"开始新训练"报"服务器错误"，上一次训练已被强制结束且统计已计入，新训练在库里留下**缺楼层的残缺记录**；重试时 lastTrain 状态已是 3 不再走删除分支，垃圾数据持续累积。零日志，运维完全无法定位根因

### P1-14 · 电传训练已入库但统计未更新，前端显示失败诱导重复提交

- **位置**：`src/main/java/com/nip/service/TelexPatTrainService.java:84-86`（方法 `:54` 为 `@Transactional`）
- **吞掉的异常**：`NumberFormatException`（同 P1-5 的 null duration）。路径：`:73` `updateStatus` / `:78` `save` 已执行 → `:81` 同步调用 `statistical(...)` 抛异常 → 吞掉 → 提交
- **用户可见最坏症状**：训练记录已入库但统计没更新；用户以为没保存**重新提交 → 同一次训练存两条，统计再加一次，成绩虚高**
- **日志**：`:85` `log.error("保存训练记录失败：{}", e.getMessage())`，无堆栈

### P1-15 · 报文比对入口静默返回空串，导致评分全错且永久落库

- **位置**：`src/main/java/com/nip/service/PostTelegramTrainService.java:908-910` — `catch (Exception e) { return ""; }`，**连 log 都没有**
- **吞掉的异常**：`JSONUtils.fromJson` 的 `JsonSyntaxException` / `JsonParseException`（`moresKey`、`patKeys` 字段存的是 JSON 数组字符串，脏数据/截断即触发）
- **调用方拿到什么**：`:665` 与 `:670` 两处，在 `processPageComparisons` 里把**标准答案 sources** 和**用户拍发 patKeys** 各自转成 `""`，`:674` 喂给 `MessageComparisonService.comparison(...)`。`MessageComparisonService.java:133` 是 `Objects.equals(patKey, source)` 直接比串——**空串必然不等于任何真实电码组**。`:676` `contentFloorValueEntity.setResolver(...)` 把错误对比结果写库，整条链在 `finish()`（`:485` `@Transactional`）内
- **用户可见最坏症状**：某一楼层报文 JSON 稍有损坏，**该页所有电码组被判全错**（或用户拍对却判错），训练成绩被永久写成远低于实际的分数，对比详情页每组都标红，用户无法申诉——日志里连一行错误都没有

### P1-16 · 电传隔页改错指令被静默丢弃，操作数当正文混入报文

- **位置**：`src/main/java/com/nip/service/PostTelexPatTrainService.java:1211-1213` — `catch (Exception e) { log.error("解析隔页修改出错，原内容是：{}", group); }` —— 异常后**既不 add 回 rowList 也不推进 z**
- **吞掉的异常**：`NumberFormatException`（`:1201` `Integer.parseInt(pMark)`、`:1203` `Integer.parseInt(groups[z+1])`）、`IndexOutOfBoundsException`（`:1208` `totalPageData.get(pageNum)`——用户若引用**当前页**页号，当前页此刻还没 add 进 `totalPageData`，必越界）、`ArrayIndexOutOfBoundsException`（`groups[z+2]`）
- **调用方拿到什么**：丢掉 `"1P"` 之后，`groups[z+1]`（"1"）和 `groups[z+2]`（"4321"）会在后续 z 迭代里被当成**普通报文内容** add 进 rowList；同时 `:1209` 的 `errorNumber++` 没执行。产出经 `convertCodeAll` → `:372` `parseCodeAll` → 参与 `:717-727` 正确率、`:683-690` 速率、`:737-765` 回写页面内容
- **用户可见最坏症状**：用户做隔页改错时改错指令被静默丢弃，"页号/组号/新内容"三个 token 反而当正文混进报文，该行凭空多出 2 组错组、改错次数少计，成绩里同时出现"多组"和"错组"**双重扣分**

### P1-17 · ADD/QTA 改错失败，字面量被当电码组写进报文

- **位置**：`src/main/java/com/nip/service/PostTelexPatTrainService.java:1173-1177` — `catch (Exception e) { log.error("解析ADD或QTA 失败:{}", e.getMessage()); rowList.add(group); }`
- **吞掉的异常**：`NumberFormatException`（`:1158` `Integer.parseInt(next)`）、`IndexOutOfBoundsException`（`:1161` `rowList.remove(index-1)`、`:1167` `rowList.add(index-1, data)`、`:1151` `pageData.get(rowNum).add(groupNum,...)`）、`ArrayIndexOutOfBoundsException`（`:1149`/`:1166` `groups[z+2]`）。日志只打 `e.getMessage()`，**丢掉堆栈**
- **调用方拿到什么**：字面量 `"ADD"` / `"QTA"` 被塞进 rowList 当报文内容，且 z 未推进 → 其后的组号、内容 token 也被当正文吸收
- **用户可见最坏症状**：一次删除/插入改错失败，报文里**凭空出现 `ADD`/`QTA` 三个假电码组**，正确率被拉低，且用户想删掉的错组仍留在成绩里被再扣一次分

### P1-18 · 军事术语训练列表把"登录失效"伪装成"暂无数据"

- **位置**：`src/main/java/com/nip/service/PostMilitaryTermTrainService.java:402-405` — `catch (Exception e) { log.error("获取训练失败", e); return Collections.emptyList(); }`
- **吞掉的异常**：`NullPointerException`（`:394` `userDao.findUserEntityByToken(token)` 走 `.firstResult()`，token 失效返回 null，`:395` `userEntity.getId()` 立刻 NPE）；以及 `:397` `JSONUtils.fromJson(e.getTypes())` 的 JSON 解析异常、DB 异常
- **调用方拿到什么**：`PostMilitaryTermTrainController.java:55` → `ResponseResult.success(空List)` → HTTP 200 + `code=SUCCESS` + `data:[]`。前端**没有任何错误信号**
- **用户可见最坏症状**：token 过期或某条记录的 types 字段是脏 JSON 时，用户的军事术语训练历史列表整体变成"暂无数据"，**用户以为自己所有训练记录被删除了**，而不是提示重新登录

### P1-19 · 删除报文/报文类型失败被 Controller 包装成成功

- **位置**：`src/main/java/com/nip/service/CableService.java:86-89` 与 `src/main/java/com/nip/service/CableTypeService.java:54-57` — `catch (RuntimeException e) { log.error("删除报文失败", e); return false; }`（日志带堆栈，写法正确），**问题在 Controller**
- **证据**：`CableController.java:69` `return ResponseResult.success(cableService.delete(id));`、`CableTypeController.java:52` 同构 → HTTP code **恒为 200**，失败只体现在 `data=false`，message 仍是 `"ok"`
- **用户可见最坏症状**：点删除后前端按 code 判成功、弹"删除成功"并从列表移除，**刷新后数据原样回来**
- **附加**：`CableTypeService.java:50-52` 三条 delete 顺序执行，若第 2 条抛异常被吞、事务仍提交 → 类型已删但 `t_cable` / `t_cable_floor` 残留 typeId 指向不存在类型的**孤儿数据**

### P1-20 · PojoUtils 把反射异常替换成无 message 的裸 NullPointerException

- **位置**：`src/main/java/com/nip/common/utils/PojoUtils.java:69-72` 与 `:80-83`
- **证据**：catch `InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException` 后 `throw new NullPointerException()` —— **无 message、无 cause**；日志 `log.error("convertOne error:{}", e.getMessage())` **未把 `e` 作为末位参数传入，堆栈完全丢失**
- **调用方拿到什么**：一个裸 NPE。已 grep 确认全仓 `catch (NullPointerException` **零匹配**（没人接），且全仓 `ExceptionMapper` / `@Provider` **零匹配**（无全局异常映射器）→ 直接变 RESTEasy 默认 HTTP 500，响应体无任何业务信息
- **影响面**：`PojoUtils.convertOne(` 分布在 **45+ 个文件、150+ 处**，覆盖 controller/general、service/\*、simulation/\*、`ws/WebSocketService.java:115` 与 `:122`
- **用户可见最坏症状**：任何 VO/Entity 缺无参构造器（或构造器抛异常）时接口返回 500 空响应，前端只显示"系统异常"；日志只有 `convertOne error:xxx` 一行、无堆栈，**定位不到是哪个类哪个字段**

---

## P2

### P2-1 · 电传拍发内容三层降级成空串并覆盖数据库

- **位置**：`src/main/java/com/nip/service/PostTelexPatTrainService.java:742-744` / `:750-752` / `:758-760`（三处 catch **全部无日志**）
- **吞掉的异常**：`IndexOutOfBoundsException` —— `parseCodeAll`（用户实际拍发，页/行/组数由用户输入决定）与 `convertText`（标准内容，页/行/组固定）长度不一致时必然触发；用户少拍一页或某行少拍几组就命中
- **调用方拿到什么**：`:761` `pageEntity.setValue(gGroup)` 把 `""` 写入 `PostTelexPatTrainPageEntity.value`（注释即"用户拍发的内容"），`:763` `pageDao.save(groups)` 落库；该字段经 `detail()`（`:184`）→ `PostTelexPatTrainPageVO.value` 返回前端。整段在 `finish()`（`:211` `@Transactional`）内，提交后不可逆
- **用户可见最坏症状**：训练结束后回看详情，**用户自己拍过的整页/整行内容显示为空白**，只剩标准答案，无法复盘，且库里原始拍发内容已被空串覆盖

### P2-2 · 标错页改错指令被当电码组参与比对

- **位置**：`src/main/java/com/nip/service/PostTelexPatTrainService.java:1191-1194` — `catch (Exception e) { log.error("解析标错页修改错误,原字符串内容：{}", group); rowList.add(group); }`（日志未带异常对象，无堆栈）
- **吞掉的异常**：`ArrayIndexOutOfBoundsException`（`:1184` `split("/")[1]`）、`StringIndexOutOfBoundsException`（`:1187` `substring(0, indexOf("-")+1)`）
- **用户可见最坏症状**：用户改页标失败时，改错指令本身（如 `"7890-2/1"`）被当成一个报文组参与比对，必然判为错组，**页标仍是错的，用户被双重扣分**

### P2-3 · 时间范围参数解析失败静默变成"全时段"

- **位置**：`src/main/java/com/nip/service/UserTrainStatisticsService.java:76-83`（两处 `catch (DateTimeParseException ignored) {}`）
- **吞掉的异常**：`DateTimeParseException`
- **调用方拿到什么**：`null` → `:52-53` 的 `start`/`end` 为 null → `within(...)`（`:167-175`）对 null 边界一律放行 → **等价于不过滤**
- **用户可见最坏症状**：用户在训练时长统计页选了时间范围，格式与两种预期都不匹配时，页面**静默返回全时段总时长**，数字看起来完全正常但口径是错的，用户据此做的训练量判断全部失真

### P2-4 · 训练时长累加中的空 catch 让脏数据静默计 0

- **位置**：`src/main/java/com/nip/service/UserTrainStatisticsService.java:107` / `:121` / `:130`（三处 `catch (Exception ignored) {}`）
- **吞掉的异常**：`NumberFormatException`（`Integer.parseInt(Objects.toString(e.getDuration(), "0"))` 等，字段是字符串列，存入非数字即触发）
- **调用方拿到什么**：该条记录静默贡献 0，`total` 继续累加
- **用户可见最坏症状**：电子键/收报训练时长统计**偏小且无任何日志**，学员认为系统漏记训练时长，运维无从查证少了哪几条

### P2-5 · 读接口零日志且"出错"与"没数据"不可区分

- **位置**：`src/main/java/com/nip/service/TelegramTrainService.java:74-76`、`src/main/java/com/nip/service/TelexPatTrainService.java:94-96` / `:102-104`（三处 catch **完全无日志**）
- **吞掉的异常**：`NullPointerException`（token 过期 → `userEntity.getId()`）
- **调用方拿到什么**：HTTP 200 + `{code:500, data:null}`；`Response<List<...>>` 的 data 是 **null**
- **用户可见最坏症状**：只判 HTTP 状态码的客户端会渲染成"暂无训练记录"，**用户以为自己所有训练记录被清空，实际只是登录过期**；服务端一行日志都没有

### P2-6 · 删除类接口零日志，且失败响应结构被丢弃

- **位置**：`src/main/java/com/nip/service/TestPaperService.java:255-257`、`src/main/java/com/nip/service/TheoryKnowledgeExamService.java:425-427`、`src/main/java/com/nip/service/GradingRuleService.java:122-124` / `:141-143`（四处 catch **零日志**）
- **吞掉的异常**：方法体只有 DAO 删除/计数，现实可抛的基本是 `PersistenceException`（外键、锁超时）。此时 JTA 已 `setRollbackOnly` → catch 吞掉后拦截器在提交阶段抛 `RollbackException` 逃出方法 → 项目无 ExceptionMapper → Quarkus 返回**原始 HTTP 500**，响应体根本不是 `{code,data,message}` 结构
- **判定**：**不是部分提交**，是"零日志 + 精心构造的 `error()` 被丢弃"
- **用户可见最坏症状**：点删除后前端解析响应失败而白屏（而不是弹"服务器错误"），服务端零线索。`GradingRuleService:141` 额外把外键约束这类可解释原因压成通用"服务器错误"，而 `:137` 明明已有"存在未开始或进行中的训练引用该评分规则"这种好文案

### P2-7 · 评分规则"设为默认"查不到记录时伪装成功

- **位置**：`src/main/java/com/nip/service/GradingRuleService.java:113` → `:121`，同型 `:105`
- **证据**：`Optional.ofNullable(gradingRuleDao.findById(id)).orElse(new GradingRuleEntity())` —— id 不存在时得到一个**临时空实体**，`entity.getType()` 为 null → `findByType(null)` 返回空 List → forEach 空转 → `:121` 返回 `ResponseResult.success()`
- **判定**：不是异常被吞，是"没找到"被吞成成功——同属静默失败
- **用户可见最坏症状**：管理员点"设为默认评分规则"提示成功，**刷新后默认项没变，反复操作也没用**

### P2-8 · 密码校验与用户搜索零日志

- **位置**：`src/main/java/com/nip/service/UserService.java:468-470` 与 `:534-536` — `catch (Exception e) { return ResponseResult.error(MessageConstants.DATA_EXCEPTION); }`
- **判定**：返回语义正确（error，code=500，会传播到前端），唯一问题是**零日志，异常彻底消失**
- **用户可见最坏症状**：密码校验/用户列表页报"数据异常"，服务端查不到任何线索

### P2-9 · 全仓 `log.error("...:{}", e.getMessage())` 无堆栈（30+ 处）

- **代表位置**：`UserService.java:349` / `:399`，`TelegramTrainService.java:118` / `:129` / `:145`，`TelexPatService.java:63` / `:73` / `:91`，`TelexPatTrainService.java:85`，`TestPaperService.java:90`，`TheoryKnowledgeExamService.java:150` / `:188` / `:209`，`TheoryKnowledgeService.java:293`，`SpecificationExecutor.java:136` / `:142`，`WebSocketService.java:180`，`WebSocketSimulationService.java:627` / `:637` / `:654`，`WebSocketUnionService.java:499`
- **证据**：SLF4J 只有把 `Throwable` 作为**末位额外参数**才会打印堆栈。这些调用把 `e.getMessage()` 填进占位符，堆栈全部丢失；NPE 及部分 `IndexOutOfBounds` 的 `getMessage()` 为 null，日志退化为 `login error:null`
- **整改范本（仓内已有）**：`TheoryKnowledgeExamService.java:119` `log.error("findTheoryKnowledgeExamById error", e)`，`MenusDao.java:28`，`GeneralTickerPatService.java:561`
- **用户可见最坏症状**：线上报错日志存在但等于没有，每次排障只能靠复现或猜

### P2-10 · 两个 WS 端点把连接错误降级到 INFO 且不打堆栈

- **位置**：`src/main/java/com/nip/ws/StartWebSocket.java:69-72` — `log.info("onError: {}, {}", sid, throwable)`；`src/main/java/com/nip/ws/StatusWebSocket.java:32-35` — `log.info("onError> : {}", String.valueOf(throwable))`
- **证据**：两处均用 **INFO 级别**；且 `throwable` 被占位符消费（`StatusWebSocket` 更是显式 `String.valueOf`），只输出 `toString()`，**没有堆栈**
- **用户可见最坏症状**：WS 连接异常在生产 INFO 日志海里被淹没，排查连接中断问题时找不到根因

### P2-11 · PojoUtils 逐字段拷贝失败被静默跳过

- **位置**：`src/main/java/com/nip/common/utils/PojoUtils.java:66` 与 `:78` — `CopyOptions.create().setIgnoreError(true)`
- **[INFERENCE]** 依据 hutool `CopyOptions.setIgnoreError` 的契约语义（"忽略字段注入错误"）：属性类型不兼容 / setter 抛异常时逐字段吞掉并继续，不上报。本机 `~/.m2/repository/cn/hutool` 不存在，未能读源码逐行验证实现
- **调用方拿到什么**：**部分填充对象**——拷贝失败的字段保持默认值（引用类型 null），外观正常、无任何失败标记。`PostTelegramTrainService.java:249` 直接返给前端；`DeviceService.java:43`、`GeneralGroupNetRuleService.java:24` 直接 `saveAndFlush` 入库
- **用户可见最坏症状**：DTO 与 Entity 字段类型改动不同步时，该字段静默写成 NULL 落库（例如训练准确率、速度丢失），接口仍返回 200
- **附带**：`convertOne` / `convert` 声明的 `String... ignoreProperties` 形参在 `PojoUtils.java:33-84` 中**从未传给 CopyOptions**，所有调用方的"忽略字段"意图静默失效

### P2-12 · 电键拍发详情页循环内吞异常导致少显示一行

- **位置**：`src/main/java/com/nip/service/PostTelegraphKeyPatTrainService.java:202-204` — `catch (Exception e) { log.error("details：{},index:{},i:{}", e.getMessage(), index, i); }`（循环内继续下一次迭代）
- **吞掉的异常**：`PojoUtils.convertOne` 的反射/转换异常（`IndexOutOfBoundsException` 已被 `:196-197` 的边界判断挡住，实际触发概率低）。只打了 `e.getMessage()`，无堆栈
- **调用方拿到什么**：`PostTelegraphKeyPatTrainController.java:77` → details VO 的 `content` 字段（`:207` `v.setContent(...)`）少一条 `PostTelegraphKeyPatTrainPageMessageVO`
- **用户可见最坏症状**：已完成的电键拍发训练详情页，前两页预览**少显示一行报文**，用户看到的复盘内容不完整

---

## 明确排除（核实后判定为非缺陷，不计入统计）

| 位置 | 排除理由 |
|---|---|
| `PostTelegramTrainService.java:566-580` | **不可达死代码**。`AsyncSavePostTelegramTrainService.java:31` 无 `@Asynchronous`、不走线程池，同步执行后 `return CompletableFuture.completedFuture(...)`。DAO 异常在 `:563` 就直接抛出并向上传播（`printBottomReport` 无 try，直穿 Controller `:90`），`get()` 作用在已完成 future 上永不阻塞，`InterruptedException`/`ExecutionException` 均不可能抛出。**不存在"少一页 → 返回残缺报文"的路径**。建议删掉这两个 catch 或改成真异步 |
| `MessageComparisonService.java:173-176` | **不可达 catch**。`:169` 的前置判断已覆盖 `sourceIndex >= sources.size()`；`sourceIndex` 单调不减（`LineDetector.java:135` / `:247` 传 `emptyGroupCount - 1` 且 `emptyGroupCount >= 10`，`GroupDetector.java:210` 传 `missingCount >= 0`）。`:170` 的"取最后一条源报文"是 `MessageComparisonConstants.java:31` 明确注释的设计行为，不是吞异常，不会造成"A 报文比 B 报文"的错配 |
| `MapTypeAdapter.java:66-69` | **死代码**。全仓 grep `MapTypeAdapter` 只命中该文件自身；`FACTORY` 从未注册（`JSONUtils.java:13-16` 用 `ToNumberPolicy.LAZILY_PARSED_NUMBER`，全仓无 `registerTypeAdapterFactory`）。且"字符串数字被误转"的担忧不成立：`case STRING`(`:58-59`) 与 `case NUMBER`(`:60`) 按 JSON token 类型分派 |
| `PingYinUtil.java:45-47` | **无业务调用方**。全 `src` grep `PingYinUtil` / `getPYIndexStr` 只命中该文件自身的 `main`(`:16`) 和方法声明(`:22`)，不存在 null 流入下游的实际路径 |
| `TelexPatUtils.java:574` / `:768` / `:902` / `:1056` / `:1070` | **正常语义**。这些是输入格式判定函数，`Integer.parseInt` 失败本身就等价于"不是有效数字"，返回 -1/null/false 是该函数的契约；且大部分前面已有显式前置校验（`:562-567`、`:756-758`、`:1048-1051`）。属于用异常做控制流，无信息丢失 |
| `SpecificationExecutor.java:135-137` / `:141-143` | **无调用方**。全仓 grep `nativeQuery(` 只命中定义本身与 DAO 注释里的 `nativeQuery = true` 字样。若将来启用需先修：`:135` 吞掉映射异常后 `:140` 会返回**部分填充对象**当作成功结果 |
| `LifecycleApplication.java:33` | banner 打印失败，纯装饰，无业务影响 |
| `PojoUtils.java:146-149`、`PostTelegraphKeyPatTrainService.java:138-140` / `:217-219` / `:311-313`、`GeneralTickerPatService.java:771-773`、`PostTelegramTrainService.java:502-504`、`PostTelexPatTrainService.java:1254-1256`、`GeneralKeyPatService.java:350-352`、`GeneralTelexPatService.java:202` 等 | **正确传播**（log 后重抛或包装保留 cause），不是吞异常 |
| `TheoryKnowledgeQuestionService.java:208-385`、`PostEnteringExerciseWordStockService.java:87-127`、`CharsetUtils.java:48-50` 中的 `printStackTrace` | **全部位于 `//` 注释块内，不是活代码** |

---

## 系统性成因（决定了上面的严重级判定）

### 一、`@Transactional` 的回滚只看"异常有没有逃出方法"

这些方法全是 `try { 多次写 } catch (Exception e) { return error(); }`——异常被 catch 住不重抛，拦截器看到方法正常返回 → **提交**。必须分两类：

- **非持久化异常**（NPE、NumberFormatException、越界、ClassCast）：JTA 不会 `setRollbackOnly` → 事务**真的提交** → 已执行的删除/写入永久落库。**P0-5/6/7、P1-9/12/13/14 全属此类**，且触发路径已逐条追到具体行。
- **持久化异常**（约束冲突、锁超时）：Hibernate 会 `markRollbackOnly` → catch 吞掉后拦截器在提交阶段抛 `RollbackException` 逃出方法 → **不会部分提交**，但精心构造的 `error()` 被丢弃。这类降级为 P2（见 P2-6）。

### 二、`getUserByToken` 返回 null 而非抛异常，是过半 NPE 的单一源头

`UserService.java:479-481` 直接返回 `UserDao.findUserEntityByToken`（`UserDao.java:78-79`，`.firstResult()`）。token 过期返回 null，没有任何守卫。下游一律 `userEntity.getId()`。**一处修复（改为抛业务异常并映射成 401 语义）可消除本报告过半的 NPE 触发源。**

### 三、响应约定被三种方式绕过

1. `Response` 是普通 POJO（`Response.java:15-23`），Controller 直接 return，**HTTP 状态码恒为 200**，`code=500` 只存在于 body。
2. `ResponseResult.success(message, data)`（`:20-22`）一律使用 `ResponseCode.SUCCESS`，`UserService.changePassword:451` 用它返回失败（P1-7）。
3. `Cable*Controller` 把 Service 的 boolean 失败直接塞进 `success` 的 data 里（P1-19）。

### 四、`JSONUtils.fromJson` 的双模态返回

`JSONUtils.java:26-28` / `:33-35` / `:40-42`：空串/null → **返回 null 且不记日志**；非法 JSON → Gson 抛 `JsonSyntaxException`。同一个方法两种失败模式，导致调用点一半检查 null（`WebSocketService.java:111`）、一半直接解引用（`WebSocketUnionService.java:106-107`，`WebSocketSimulationService.java:353` / `:405` / `:422` / `:452` / `:464` / `:532`）。

### 五、异常可观测性在三个层次同时归零

- `JWTInterceptor.java:83` 不传异常对象（全站 52 个 Controller）
- 全仓无 `ExceptionMapper`（grep `@Provider` / `ExceptionMapper` / `@ServerExceptionMapper` 零匹配）
- `WebSocketUnionService.java:473` 丢弃 Throwable；`WebSocketSimulationService`、`WebSocketGeneralKeyPatService`、`WebSocketGeneralTelexPatService`、`WebSocketGeneralTickerPatService` **四个 WS 端点完全没有 `@OnError`**

---

## 整改优先级建议（按投入产出，不含在统计内）

1. **一行改动、全站收益**：`JWTInterceptor.java:83` 把 `exception` 加为末位参数。
2. **一处修复、消除过半 NPE**：`UserService.getUserByToken` 对 null 直接抛业务异常。
3. **堵住不可逆的部分提交**：`TestPaperService:65`、`TheoryKnowledgeService:231`、`MenusService:102`、`UserService:340`、`TelexPatService:82`、`TelegramTrainService:270` 这些"先批量删除再重建"的方法，catch 必须重抛或显式 `setRollbackOnly()`。
4. **让解析失败可区分**：`TickerPatUtils` 的 `catch (Exception ignore) {}` 必须让 `saveContentValue` 事务回滚并返回失败，而不是静默降级成空集合——关键不是加 log，而是**让"空集合"和"解析失败"在类型上可区分**，否则下游所有 `!= null` 防御都失效。
5. **补异步任务的异常出口**：`TelexPatTrainService:74`、`WebSocketService:116` / `:123` 补 `.whenComplete((r, ex) -> { if (ex != null) log.error(...); })`。
6. **全仓日志整改**：把 `log.error("...:{}", e.getMessage())` 换成 `log.error("...", e)`，范本见 `TheoryKnowledgeExamService.java:119`。

---

## 附录：安全相关（内网部署，按已接受风险记录，不计入 P0/P1/P2）

- `JWTInterceptor.java:84` 把 `exception.getMessage()` 两次写进响应体返回给客户端，可能泄露 SQL 片段、内部类名、文件路径。
- `UserService.java:383` token = `AESUtil.encrypt(userAccount + "-" + password + "-" + deviceId, ...)`，token 本身是明文口令的可逆密文；`PasswordUtil.java:32` / `:50` 同样使用固定密钥 AES。
- `UserService.java:438` / `:446` 使用 `MD5Util.encrypt` 存储口令（无加盐）。
- `UserDao.java:95-97` 吞掉 `updateUser` 异常 + `UserService.java:417` 忽略返回值 → 登出后旧 token 可能仍能通过 `JWTInterceptor.java:75` 的校验（功能影响已按 P1-8 计入）。
