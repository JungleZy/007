# 全项目复核报告（2026-09-12 当前 HEAD）

## 1. 结论

本次 agent team 对当前 `main` 的后端安全/评分数据、前端 Electron、跨栈契约、CI 交付、测试文档与运行面完成复核、文档独立 review、代码修复与本地验证。**仓库内可验证的整改已闭合**；历史开放发现仍保留在 §3 作为取证，但不再代表当前未修复状态。

### 最终证据摘要

- 后端 `JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B clean verify`：**441 测试 / 94 suite，0 失败 0 错误 0 跳过**（最终 rerun 2026-09-14）。
- 前端完整测试 **30/30**，`npm run build` 成功；release manifest 契约 **5/5**。
- 当前 `backend/database/migrations/` 共 **17 个脚本**；其中 16 个 schema 脚本由 rehearsal 执行，菜单路径脚本为数据迁移并按 runbook 单独执行。current/base 双快照、实体 schema 差分、重复执行断言全部通过，证据见 `backend/database/rehearsal/2026-09-14-current-state/`。
- 隔离 `%prod` fast-jar 已验证普通用户 `code:207`、管理员 `code:200`；真实浏览器完成授权、登录、仪表盘和英语训练页；生产快照中 100% 正确率曾触发 SQL1264，迁移 06 后重试保存为 `status=2, accuracy=100`。
- 真实 REST 核验覆盖登录不透明 token、用户目录 207/200、速率配置 207/200、Masthead 属主 200/外人 207、英语训练服务端结算；真实浏览器截图已确认仪表盘可见。

### 外部验收边界

`webSecurity:false` 仍是明确残余；可信证书链、Windows/ARM64 native CI、桌面 native 后端凭据注入、真实硬件、客户现场和发布负责人签收未由本地证据覆盖。浏览器 license 校验为 origin-scoped，不能表述为跨 origin 通用授权。**GitHub Actions 尚未针对本次新提交运行，不能宣称 CI 已绿色。**

本轮已修复：考试生命周期与现有 id 越权、评分基准授权、个人训练主体与服务端成绩、综合电子键服务端权威指标（迁移 04）、报话持久化服务器时钟（迁移 05）、军语完整性、主数据/Masthead owner 授权、串口特权路径、前端错误信封成功化、英语类型/余量/时钟/schema（迁移 06）。

## 2. 已确认闭环的范围

以下结论有本次复核的源码/测试证据，不重复改造：

- `UserSyncDto` 不再承载 password/token/deviceId；训练同步导出按主体授权。
- token 为 SecureRandom 不透明串，数据库存 SHA-256 摘要；REST 只从请求头读 token/deviceId；WS 使用 query 凭据并以认证主体覆盖路径 uid。
- 非 free controller 类级 `@JWT` 架构守卫、`@RequireAdmin` 拦截器和 207/208 信封映射正确。
- `generalTelexPat` 从 `captureIntervals` 重算码率/用时，使用冻结满分、行锁、attempt 栅栏、幂等结算和提交后通知；回归测试覆盖篡改、并发、回滚和越权。
- 文件服务读取/上传路径约束在 fork 子进程资源根内并监听 127.0.0.1；Electron 使用 contextIsolation + preload 白名单，已移除忽略证书错误开关；但 `webSecurity:false` 仍是明确非目标。
- 已迁移调用点的前端 207/208 终态不重试、采集失败保留输入；未迁移页面的错误信封也已在本轮逐点收口。
- `%test` 调度器关闭；当前 rehearsal 执行 16 个 schema 脚本 + 1 个数据迁移独立脚本；WS 测试状态清理和通知测试有真实行为断言。

## 3. 历史发现及当前处置证据
 
以下条目保留原始发现和路径，状态以本节开头的最终证据摘要、当前源码和回归测试为准；不再代表当前未修复项。

### P1-NEW-01：理论考试生命周期授权与学员调用冲突

- `backend/src/main/java/com/nip/controller/TheoryKnowledgeExamController.java:92-96` 的 `teacherStartTheoryKnowledgeExam` 只有类级 `@JWT`。
- 同文件 `:135-140` 的 `deleteTheoryKnowledgeExam` 同样无 `@RequireAdmin`。
- `backend/src/main/java/com/nip/service/TheoryKnowledgeExamService.java:141-166` 在 type=3 时批量结束所有考生；`:583-587` 按 examId 删除答卷、试卷快照和考试。
- 当前已有 `teacherUploadScore` 管理员授权，但未覆盖这两个兄弟端点。
- **不能简单给 teacherStart 全端点加管理员门禁**：`bw-frontend/frontend/src/views/manage/basicTheory/test/test/startTest/js/startTest.js:123-126` 与 `studentStartTest/js/startTest.js:63-65` 都在学员流程调用 teacherStart(type=2)。必须区分学员合法入场启动与 type=3/4 的教师操作；delete 必须管理员。

### P1-NEW-02：收报速率配置授权

- `backend/src/main/java/com/nip/controller/TickerTapeTrainSettingController.java:23,42-47` 只有类级 `@JWT`，`addOrUpdate` 没有 `@RequireAdmin`。
- 同语义 `PostTickerTapeTrainSettingController.addOrUpdate` 已有管理员授权，形成明显漏项。

### P1-NEW-03：两个活跃域客户端成绩与对象归属

- `backend/src/main/java/com/nip/controller/PostEnteringExerciseController.java:66,74,90` 的 begin/finish/getById 不带 token；`service/PostEnteringExerciseService.java:122-133` 将 accuracy/speed/duration/content/errorNum/correctNum 直接写入。
- `bw-frontend/frontend/src/common/api/postHanZi.js:34` 仍消费 finish，故不是死代码。
- `backend/src/main/java/com/nip/controller/PostRadiotelephoneTrainController.java:79,87,95` 的 begin/finish/details 不带 token；`service/PostRadiotelephoneService.java:96-124` 无对象授权，finish 直接采信 score/passNumber/errorNumber/accuracy；`bw-frontend/frontend/src/common/api/postWording.js:34` 仍消费该 finish。

### P1-NEW-04：创建接口现有 id 绕过归属

- `backend/src/main/java/com/nip/dto/TheoryKnowledgeExamDto.java:19` 接受 id；`controller/TheoryKnowledgeExamController.java:50-55` 的自测保存仅有类级 JWT。
- `backend/src/main/java/com/nip/service/TheoryKnowledgeExamService.java:243-252` 复制 DTO、覆盖 createUserId、按传入 id merge，并无条件删除该 exam 的快照和考生行。
- `backend/src/main/java/com/nip/dto/TelegraphKeyPatSyntheticalDto.java:23-24` 接受 id；`service/TelegraphKeyPatSyntheticalService.java:75-96` 的 save 覆盖 createUserId 但未先调用 owner 判定；`controller/TelegraphKeyPatTrainSyntheticalController.java:50-55` 对普通登录用户开放。
- `common/utils/PojoUtils.java:74-78` 会复制 id，`common/repository/BaseRepository.java:15-22` 对非空 id 使用 merge。新建必须由服务端生成 id；保留更新时必须先加载旧对象再判属主。

### P2-NEW-01：军语域归属、答案复盘与重复计分

- `PostMilitaryTermTrainController.java:62,69,76` 与 service `:430-532` 缺 begin/finish/details 归属校验；服务端虽计算分数，但 details 可暴露 `correctAnswer`。
- 复盘页面确实依赖答案键：`bw-frontend/frontend/src/views/manage/postJob/ditto/militaryTermTrain/Index.vue:64-69,105-107` 与 `militaryTrain.js:170-181`。
- 处置必须是“未完成不返回答案键；完成后仅授权主体可查看复盘”，不能无条件删除字段。
- `PostMilitaryTermTrainFinishDto.java:24-25` 接受题目列表；`PostMilitaryTermTrainService.java:466-492,511-513` 按请求逐条计分而分母按数据库唯一题目数，重复题目 id 可能重复计分。必须校验题目属于当前试卷且 id 唯一，不能简单把结果截断到 100。

### P2-NEW-02：综合电子键客户端指标与创建覆盖

- `TelegraphKeyPatSyntheticalService.java:107-141` 直接保存客户端 speed/duration/accuracy/totalNumber；`:148-166` 汇总为教师可见统计；前端由 `common/api/examApi.js:64,72` 消费。
- 其 `save` 还接受现有 id 并覆盖 createUserId（见 P1-NEW-04）。迁移必须覆盖 save、stop、finish、自动结束上一条训练的路径，而非只改 finish。

### P2-NEW-03：方法级授权覆盖不足

- `MilitaryTermDataController.java:40-88`、`RadiotelephoneTermDataController.java:44-62`、`KeyPointsController.java:37-41`、`ReceiveKeyPointsController.java:39-43`、`DeviceTypeController.java:40-58`、`PostEnteringExerciseWordStockController.java:42-71`、`MastheadController.java:31-35` 的写端点缺管理员/对象授权。
- `ControllerJwtGuardArchitectureTest.java:35-58` 只扫描类级 `@JWT`，不检查变更端点。

### P2-NEW-04：前端错误信封成功化

- `bw-frontend/frontend/src/common/http/index.js:85-91` 对 207/208 等只显示消息后仍 resolve response.data。
- `views/manage/postJob/hanzi/english/practice/js/parctice.js:185-208` 与 `views/manage/postJob/ditto/wordingTrain/js/wordTrain.js:284-295` 不检查 `res.code` 就置完成、关闭页面或重拉详情。
- `useConfirmedSubmission.js` 只保护采用该提交边界的调用点，结论必须限定范围；修复时逐个迁移这些消费者。

### P3-NEW-02：串口特权路径

- `bw-frontend/electron/serial/nativeSerialPort.js:98-103` 的 `/^\\/dev\/[\w/.-]+$/` 接受含 `..` 的路径，随后拼入 `pkexec chmod 666`。
- 正常 UI 只传枚举到的 `/dev/ttyUSB*`，但 IPC 边界仍应 fail-closed。

### P3-NEW-03：死函数

- `bw-frontend/frontend/src/views/manage/organization/datagramZuXun/train/student/js/datagramTrain.js:282-286` 调用未导入的 `resetHandKeyZuXunTrain`，函数不在返回对象中且当前不可达。此次可删除；若保留必须在文档中明确“不修且不可达”。

### 文档/交付漂移

- `backend/README.md:8` 仍为 316/74；权威当前基线为 401/94。
- `AGENTS.md:52` 仍为 58/45，当前 DAO 统计为 55/42。
- `docs/README.md:39` 仍写 review HEAD 为 `0efbdf1`。
- `docs/guides/2026-09-12-release-runbook.md:144` 仍为 392 测试；该数字应注明为历史 native 记录，不能机械改成当前 native 证据。
- `docs/reviews/2026-09-12-full-project-review.md:228,285,351,481` 分别存在 WebSocket 数量和 `webSecurity` 结论漂移。
- `.github/workflows/build-quarkus-native.yml:64-68` 只执行题库测试，不执行完整前端测试脚本。
- `bw-frontend/package.json:3` 为 3.1.0，而 `backend/pom.xml:7`、`bw-frontend/frontend/package.json:2` 为 1.1.0；release gate 会拒绝任意不一致 tag。

## 4. 运行验证边界

本轮把仓内可执行路径跑到最终基线：441/94 后端、30/30 前端、17 脚本双快照 rehearsal、隔离 prod REST/浏览器 smoke。未运行的 Windows/ARM64 native CI、桌面 native 后端凭据 provisioning、真实硬件、可信证书链、客户现场和发布签收仍是外部前置；GitHub Actions 需在本次提交推送后继续观察。

## 5. 交付结论

仓库内整改已闭合；外部前置不在本次源码交付范围。
