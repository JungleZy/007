# 核心训练整改计划（2026-09-14）

对应评审：[`../reviews/2026-09-14-core-training-review.md`](../reviews/2026-09-14-core-training-review.md)；规格：[`../specs/2026-09-14-core-training-fix-spec.md`](../specs/2026-09-14-core-training-fix-spec.md)。集成者按阶段合并并验证；子任务不得运行全量 suite、formatter 或 linter。

## W0：证据与文档

- [x] 四路 agent team 完成手键、电子键、收报/报话、跨栈复核。
- [x] 真实 fast-jar + MySQL smoke：OpenAPI、203 envelope、登录和 query/body 对照。
- [x] review、spec、plan 写入仓库。
- [x] 独立 reviewer 发现并校正文档范围：经典旧域采集协议/迁移与服务端重算已纳入本轮；补入 Telegram 日志读面；修正 @JWT 与 GET wrapper 事实；增加端点级验收矩阵。

## W1：综合组训读授权

1. GeneralTickerPat：detail/statistic/online roster 统一引入 token，调用成员/创建者/组训人员/管理员读判定；socket 辅助读同步保护。
2. GeneralKeyPat：findPage/detail/patDetail/getPatValue/statistics/online 统一保护；per-user 目标判定；孤儿 getScore 明确删除或 train-scope 化。
3. 补行为测试：ticker/key 外人 207，合法 member/organizer/admin 200，不能泄露 foreign roster/content。

## W2：岗位收报与经典收报

1. PostTickerTape：线程 token 到 read/lifecycle/upload/reset；owner/writableTrain 判定；uploadResult 加终态、重复和值行幂等保护；补 REST 行为测试。
2. TickerTape：线程 token 到 get/begin/pause/goOn/finish；owner 判定；测试跨人结束/读拒绝与数据库不变。
3. 复核 ReceiveApi 真实调用 payload 和 207/208 页面处理，保留现有 envelope。

## W3：拍发、电传、电子键与手键正确性

1. TelegramTrain：控制、详情、`getTelegramTrainLog`、报底保存 owner-gate；补服务端采集事件/暂停时钟、迁移和服务端结果重算；全局 saveSetting 移到管理员门禁；补跨人和篡改测试。
2. TelexPatTrain：创建服务端生成 id；现有 id load-then-owner；详情 owner/member；补采集契约、迁移和服务端聚合重算；未知 id 不静默成功；补接管回归。
3. TickerTapeTrain：线程 token 到 get/begin/pause/goOn/finish；owner 判定；补服务端时钟/暂停累计或等价采集契约与迁移；禁止客户端 validTime/mark/schedule 作为权威。
4. TelegraphKeyPatTrain：更新 owner-gate；禁止跨人覆盖聚合统计。
5. TelegraphKeyPatSynthetical：legacy PAUSE/null protocol 新建路径不再永久阻断，旧记录不重算；补 fixture。
6. GeneralTicker：NULL score statistics 安全；传真实 avg timing；提交后通知；socket 辅助读授权；补 reset/statistics 和平均时长回归。
## W4：wire、诊断和文档同步

1. handkey/electron GET startTrain 显式拼 query、不发送 body；补前端 node contract test；确认 delete 等同类 GET。
2. 移除 `saveBaseTrain` catch 吞异常；坏数值输入按既有参数错误 envelope 处理。
3. 清理无效 `@RequestPass`/脆弱实例化/空 bean（若仍无消费者），更新 review/spec/plan 的完成状态。
4. 若实现与规格边界发生变化，先同步三份文档，再进入最终验证。

## W5：验证、提交、推送

- 迁移/实体变更时执行 rehearsal；本轮新增的经典域采集字段必须同步 migration，并对 current/base 双快照执行演练。
- 每个独立任务检查 `git status --short`，按 `fix(scope): 中文摘要` 单独提交；跨栈 wire 修复两侧同一提交。
- 集成后运行 `cd backend && JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B clean verify`，再运行前端完整 test/build。
- 推送 `main` 后查看 `Build Quarkus Native` 的 test/frontend/build；任何失败先按日志修复并重新验证，全部 job 完成前不宣称通过。

## 风险与决策

- 教员是否能操作学员训练：默认沿用当前 `GeneralTelexPatService` 的 creator ∪ role=1 organizer ∪ admin；纯个人训练默认 strict owner。
- legacy PAUSE：不重算缺少采集协议的历史成绩；关闭/废弃旧行必须保留审计可见性。
- `getScore` 和全房间 roster 当前无确认消费者；默认删除孤儿端点，若源码调用面证明仍需要则采用明确 train-scope 授权。
