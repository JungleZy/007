# 全项目整改实施计划（2026-09-15）

依据：[review](../reviews/2026-09-15-full-project-review.md)、[spec](../specs/2026-09-15-full-project-fix-spec.md)。Main 为集成、命令验证、提交和推送唯一负责人；子代理不得在共享树并发运行 formatter/linter/build/tests，不提交他人文件。

## A. 已完成的调查与运行基线

- [x] 核对 main 起点 a39e226、干净初始工作树及起点 Actions success。
- [x] 六路领域审查 + 理论边界、剩余服务两路补充审查；保留每路覆盖/排除项。
- [x] 后端基线442全绿、前端31全绿/生产构建、发布契约5全绿。
- [x] 隔离导出提交态模拟器，补齐其兄弟工程源码依赖后79项通过；撤回外部并发编辑造成的瞬时编译误报。
- [x] 独立复制数据库、应用已有迁移，生产validate启动；浏览器授权/登录/仪表盘、核心REST/页面验证，实测发现R01。
- [x] 编写本轮review/spec/plan初稿，不先修代码。

## B. 文档审阅门禁

- [x] 审阅者甲（ReviewDocsContracts）：R09模型、R10合法离场/全部写入口、R11全部试卷投影、R01单内容旁路五项阻断已修正，复审批准实施；R17/R18另获明确批准。
- [x] 审阅者乙（ReviewDocsPlan）：映射、证据、所有权及Actions闭环通过，补审R18通过。
- [x] Main已修正文档并记录结论；只批准实施设计，不宣称代码/未来验证完成。

## C. 独立修复与提交单元

| 编号 | 目标与所有权 | 验证与单独提交 |
|---|---|---|
| R01 | 经典手键：TelegramTrainService + 相关回归。受管内容、计时/ScoreMath、当前页及saveFloorContent的同锁/legacy/终态守卫 | 首次正确/错误提交与pause/finish、结束后不可改答案；原SQL1406隔离生产复现消失；独立 `fix(handkey)` |
| R02 | 组训手键：GeneralTickerPatService + GeneralTickerPatScoreTest | 整百/部分末页/跨页缺报，断言lack及扣分；独立 `fix(handkey)` |
| R03 | 岗位手键：PostTelegramTrainService.detail及必要复盘消费者/回归 | 前页缺后页有结果的正确页级展示；独立 `fix(handkey)` |
| R04 | 岗位电子键：PostTelegraphKeyPatTrainService.countScore + 相关回归 | 少码、多码、混合正确率；独立 `fix(electronic)` |
| R05 | 个人数据报前端：preJob/datagram/telexTrain/js/telexTrain.js及真实行为回归 | 首键成功、失败/终态不启动、重试/计时唯一；真实浏览器；独立 `fix(datagram)` |
| R06 | 收报前端：preJob/receive/train/js/receiveTrain.js | 120秒恢复、继续计时、小时边界；浏览器或执行原函数smoke；独立 `fix(receive)` |
| R07 | GeneralTelexPatService 分析时间与必要消费者/回归 | 持久采集区间与逐页毫秒/有限码率；独立 `fix(datagram)` |
| R08 | 仿真：SimulationRouterRoom*、SimulationDisturdController、SimulationRoomAccess调用与权限回归 | 外人/成员/控制者矩阵，拒绝后DB及内存不变；独立 `fix(simulation)` |
| R09 | 个人拼音/五笔码串：EnteringExercise链路、DTO/实体/DAO、共享规范码表、pinyin/practice、wubi/practiceTwo及文章键盘组件、迁移01 | 实际value而非正误布尔、条目/组计分、时钟/恢复、旧行策略及双快照；跨栈同一 `fix(entering)` |
| R10 | 理论生命周期：TheoryKnowledgeExamService/Controller、TheoryKnowledgeExamUserService/相关DAO、自测finish、startTest与自测/离场/计时消费者及回归 | 合法type1/2/3、硬结束UI、自测越界、全部写入口同锁；先跨栈独立 `fix(theory)` |
| R11 | 理论读边界：同一Exam文件、详情+type2 paper投影、各题型/教师及学员复盘消费者与回归 | **等待R10验证并提交后再改共享文件**；本人/他人矩阵、自测创建者身份、无泄题且可作答；独立 `fix(theory)` |
| R12 | VerifyLicense.vue授权布局 | 1440×1000及矮视口真实截图/授权；独立 `fix(frontend)` |
| R13 | .github/workflows/build-quarkus-native.yml | 独立Rust job，现有测试不跳过；最终Actions证据；独立 `ci(keysim)` |
| R14 | README.md、backend/README.md、docs/README.md、release runbook及本轮最终记录 | 最新入口、完整迁移、历史/当前证据区分；文档独立 `docs(review)` |
| R15 | TestPaperService 请求局部递归结果 + 相关回归 | 不同子树并发/交错及失败后隔离；独立 `fix(theory)` |
| R16 | EnteringKeyPointsController + 管理员授权回归 | 普通用户207且未改数据，管理员可保存；独立 `fix(auth)` |
| R17 | WebSocketSimulationService通报/收报教学控制主题 + WS授权回归 | 学员伪造结束拒绝且无广播/状态变更；教师生命周期正常；独立 `fix(simulation)` |
| R18 | TelegraphKeyPatSyntheticalDao/统计服务、TelegraphKeyPatTrainService、独立数据迁移及回归 | 混合协议毫秒汇总/单字平均码率/清空指标；真实2秒显示；独立 `fix(electronic)` |

### 并行合同

- 子任务只编辑明确拥有的文件；同文件边界由Main协调。R10/R11必须串行；R01与R02/R03服务文件不同可并行。
- R09独占其新增迁移；`backend/scripts/rehearse-migrations.sh`、部署清单由Main统一更新，避免两代理同时改迁移数组。
- R08只改REST/房间访问接口，R17独占WebSocketSimulationService；共享SimulationRoomAccess若需新API由R08先明确签名，R17优先复用现有方法避免双写。
- R18独占电子统计2026-09-15-02迁移（若采用回填），不得改R09迁移或Main的rehearsal清单；R04岗位电子评分文件与R18个人统计文件无共享编辑边界。
- 既有共享测试类需要先报Main，指定一个所有者；其他任务可创建有真实边界价值的独立回归类，不能为了避冲突堆实现细节测试。
- 验证阶段批次冻结修改。子任务不运行命令、也不提前提交；Main按依赖运行一次受影响测试组，再逐个stage明确路径并提交。全量测试串行，禁止引入并行Surefire。
- 不用 `git add -A`，不将keysim外部编辑纳入；不重写已推送历史。

## D. 行为验证与集成门禁

- [ ] 每个确认缺陷以修复前失败/修复后通过的回归或真实场景闭环；纯UI用真实surface，不写CSS/源码文本断言。
- [ ] 手键：经典首次提交即正确、暂停继续、当前页恢复、生产结束成功；组训缺报/扣分、岗位复盘错页边界。
- [ ] 电子键：单字/综合/岗位入口可用；综合暂停继续保持时钟；岗位少码/多码正确率；已有组训attempt与capture回归。
- [ ] 收报：基础/科式/综合页面、音频准备、开始暂停恢复结束；服务端时间与重入显示一致。
- [ ] 数据报：首键确认、重复开始/失败重试、逐页分析时间。路由未配置的组训UI不伪称已完成真实UI验收。
- [ ] 仿真、理论、汉字、要点管理的新增对象授权/终态/结果回归通过。
- [ ] `JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B clean verify` 完整通过，不使用skipTests交付。
- [ ] 前端 `npm run test && npm run build`，桌面发布契约 `node --test 'scripts/test/*.test.mjs'`，Rust `cargo test --locked && cargo build --locked`。
- [ ] 如R09改变实体：`EntitySchemaSnapshotRehearsal` + current/base双快照迁移、重复执行与历史协议保护通过。
- [ ] 代码质量独立review：最近改动的正确性/简化审阅、错误处理与边界。必要修改后重跑受影响验证，不用审阅意见替代运行证据。

## E. 提交、推送、Actions

- [ ] 按C逐项提交；跨栈契约修改正文记录实际前端模块/消费者与验证命令；保留每个修复可独立revert。
- [ ] 整体green后推送main，不发tag/不发布新版本。
- [ ] 查找最终提交SHA对应Actions；等待JVM、前端、Rust、三平台native所有必跑job完成。
- [ ] 若失败，读取准确job日志，单独修复真实原因、本地验证、再次提交/推送，直到最终SHA必跑job全部success。tag-only的desktop/release跳过不等于产品包实机验证。
- [ ] 回填review/spec/plan中实际commit、命令、测试数量、迁移证据目录、Actions run URL；文档再审阅并提交。如最后文档提交触发新run，继续观察该最终SHA。

## 执行记录

当前：两路文档审阅通过，D1–D5已闭合，R18补审通过；开始按C实施。完成后更新为准确证据与外部验收边界，不用“全项目绝无问题”作结论。
