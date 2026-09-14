# 当前复核整改计划（2026-09-12，最终回写）

对应规格：[`../specs/2026-09-12-current-state-fix-spec.md`](../specs/2026-09-12-current-state-fix-spec.md)。本计划记录 agent team 复核、文档复审、实施和最终本地验证；历史开放项仍见当前 review §3，但不再代表当前未修复状态。

## W0：复核与文档

- [x] 8 路分域源码复核：后端安全/评分、前端桌面、跨栈、CI、测试文档、运行面。
- [x] 新 review/spec/plan 经独立 code-reviewer 两轮复审，第二轮 APPROVED。
- [x] 当前 review/spec/plan、docs README、根 README、AGENTS、backend README、runbook 已回写最终证据边界。

## W1：安全与授权止血

### T1 考试生命周期与现有 id

- [x] `deleteTheoryKnowledgeExam` 管理员授权；teacherStart 区分成员 type=2 入场与管理员 type=3/4 全场操作。
- [x] 自测及正式考试保存路径禁止通过现有 id 覆盖他人属主、答卷或快照；新建由服务端生成 id。
- [x] `TheoryExamLifecycleAuthorizationTest` 与既有考试回归覆盖普通用户 207、管理员成功、合法学员入场、无副作用 takeover。

### T2 评分基准与主数据授权

- [x] `TickerTapeTrainSettingController.addOrUpdate` 加 `@RequireAdmin`，普通用户 207 且配置不变，管理员成功。
- [x] 军语/报话词库、要点、设备类型、汉字字库等全局写端点完成管理员授权；Masthead 改为真实收报训练 owner/admin 判定。
- [x] 主数据/Masthead/Ticker 行为测试覆盖 owner、foreign、ordinary 207、admin 200。

## W2：训练主体与服务端权威结果

### T3 个人汉字与报话训练

- [x] begin/finish/details 全部 token + owner；终态幂等，跨人访问 207。
- [x] 结果从服务端保存题面/提交答案重算，客户端 score/speed/duration 等伪造字段不再权威；页面检查 `res.code`，失败保留输入。
- [x] English 类型使用 type=2；Pinyin 文章保持字面标点；超出答案尾部的字符/词计入错误；服务器时钟返回 resumed duration；正确率列迁移 06 支持 100%。
- [x] `PersonalTrainingScoringContractTest` 8 项通过；隔离 prod 页面抓取到旧 `double(2,0)` 的 SQL1264，迁移 06 后同类重试保存 `status=2, accuracy=100`。

### T4 军语训练

- [x] begin/finish/details owner + 行锁 + 终态 208；答案键未完成脱敏、完成且属主可复盘。
- [x] submitted question id 必须属于当前试卷且唯一；选项/答案键校验；重复/外场/损坏输入不写库；并发同结果只结算一次。
- [x] 专项行为测试及真实前端错误保留路径已纳入最终验证。

### T5 综合电子键训练

- [x] save/stop/goTo/finish 及暂停训练隐式完成均使用 owner、行锁、状态机和服务端时钟。
- [x] sourceContent 固化题面；speed/accuracy/count/duration 从答案和 active clock 重算；客户端聚合字段移除；历史缺协议/快照的未完成训练拒绝 208，不重算历史成绩。
- [x] 迁移 04 幂等；`ComprehensiveAuthorityTest` 与既有 controller 回归通过。

### T6 报话统计学习时钟

- [x] `/radiotelephone/begin|pause|resume|finish` 使用服务端 sessionId 和持久化 active clock；重复/过期 session 不重复累计；数据库历史 totalTime 不被客户端直接增加。
- [x] 迁移 05 幂等；并发首建、begin、finish 回归通过；前端 military/wording 调用面改用 session contract，菜单显示单位保持秒。

## W3：桌面、CI、迁移与交付文档

- [x] 串口 grantAccess 只接受真实枚举 `/dev/ttyUSBn`/`/dev/ttyACMn` 字符设备，拒绝穿越/符号链接/注入，前端专项测试通过。
- [x] 前端 CI 改执行完整 `npm run test`；本地 `31/31 + build`；release contract `5/5`。
- [x] 桌面、前端、后端版本统一为 `3.1.1`；已有 v1.1.0 tag 不重用，不创建新 tag。
- [x] 迁移脚本共 17 个：16 个 schema 脚本进入 rehearsal，`2026-09-12-03-menu-telex-component-path.sql` 为独立数据迁移；current/base 双快照通过，实体差分为空。
- [x] 删除数据报不可达 reset 死函数；当前 review、spec、runbook 和入口文档已同步。

## W4：最终验证与外部前置

- [x] `cd backend && JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B clean verify`：442/102 全绿。
- [x] `cd bw-frontend/frontend && npm run test && npm run build`：31/31 全绿、build 成功。
- [x] `cd backend && REHEARSAL_OUT_NAME=2026-09-14-current-state-final2 ./scripts/rehearse-migrations.sh`：current/base 全绿。
- [x] 隔离 `%prod` fast-jar：普通用户目录 207、管理员目录 200、登录 token 43 字符；浏览器登录、dashboard、英语训练页可见。
- [x] release manifest contract 5/5；版本一致性 gate 已锁定。
- [ ] Windows/ARM64 native CI、真实 Electron `--dir` 对应本次最终 commit、桌面 native DB 凭据 provisioning、可信证书、真实硬件、客户现场和发布负责人签收：外部前置。
- [ ] 推送本次提交后等待 GitHub Actions；在 workflow 所有 job 完成前不宣称 CI 绿色。

## 当前交付记录

| 项 | 最终结果 | 证据 |
|---|---|---|
| 后端 | 442 tests / 102 suite 全绿 | Maven 输出 artifact |
| 前端 | 31/31 + build | npm 输出 artifact |
| 迁移 | 17 files，16 schema + 1 data；双快照通过 | `backend/database/rehearsal/2026-09-14-current-state-final2/` |
| REST/浏览器 | 207/200、登录、dashboard、English、accuracy=100 | 隔离 prod + browser smoke |
| release contracts | 5/5 | `bw-frontend/scripts/test/artifact-manifest.test.mjs` |
| GitHub Actions | 待 push 后观察 | 不提前宣称 |
