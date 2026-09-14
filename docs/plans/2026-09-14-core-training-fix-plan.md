# 核心训练整改计划（2026-09-14）

对应评审：[`../reviews/2026-09-14-core-training-review.md`](../reviews/2026-09-14-core-training-review.md)；规格：[`../specs/2026-09-14-core-training-fix-spec.md`](../specs/2026-09-14-core-training-fix-spec.md)。集成者按阶段合并并验证；子任务不得运行全量 suite、formatter 或 linter。

## W0：证据与文档

- [x] 四路 agent team 完成手键、电子键、收报/报话、跨栈复核。
- [x] 真实 fast-jar + MySQL smoke：OpenAPI、203 envelope、登录和 query/body 对照。
- [x] review、spec、plan 写入仓库。
- [x] 独立 reviewer 发现并校正文档范围：经典旧域采集协议/迁移与服务端重算已纳入本轮；补入 Telegram 日志读面；修正 @JWT 与 GET wrapper 事实；增加端点级验收矩阵。

## W1：综合组训读授权

- [x] GeneralTickerPat/GeneralKeyPat 的 train-scoped、per-user、roster 读面统一 actor 授权，外人 207；socket 辅助读同步保护。
- [x] 完成成员/创建者/组训人员/管理员矩阵及数据库无副作用回归。

## W2：岗位收报与经典收报

- [x] PostTickerTape、TickerTape 的读写、生命周期、上传、重置和终态按属主保护；服务端时钟与重复提交语义落地。
- [x] ReceiveApi payload 与 207/208 页面处理复核完成。

## W3：拍发、电传、电子键与手键正确性

- [x] TelegramTrain、TelexPatTrain、TickerTapeTrain 使用服务端 actor、采集协议、时钟和结果重算；经典旧行保留 protocol 0，新行 protocol 1。
- [x] 电子键 legacy PAUSE、单字 owner、综合手键 NULL 统计/平均时长、提交通知时序完成。

## W4：wire、诊断和文档同步

- [x] handkey/electron startTrain 显式 query；saveBaseTrain 吞异常移除；前端失败状态可观察；文档已按最终证据回写。

## W5：非核心后端与桌面边界

- [x] EquipmentDevice 管理员门禁、EquipmentTrain/EnteringExercise 属主边界及 Electron activate/linkPort 错误修复完成。
- [x] basic/Koch 使用服务端 session id/active clock，暂停不计时，创建/恢复/结束竞态有 disposal guard。

## W6：全量验证与交付

- [x] 后端 `./mvnw -B clean verify`：442 tests，0 failures，0 errors，0 skipped。
- [x] 前端 `npm run test`：31/31；`npm run build` 成功；变更 JavaScript 语法检查通过。
- [x] 实体 schema snapshot 通过；current/base 双快照迁移 rehearsal 通过全部断言（106 张表、0 MyISAM、schema 差分为空）。
- [ ] 推送 `main` 后观察 GitHub Actions；以 runner 实际日志处理失败，不提前宣称通过。

## 风险与决策

- 教员是否能操作学员训练：沿用 creator ∪ role=1 organizer ∪ admin；纯个人训练 strict owner。
- legacy 记录不重算、不提升为新协议；关闭/废弃策略保留审计可见性。
- `getScore` 和无确认消费者的全房间 roster 不作为授权替代；保留端点必须 train-scope 授权。

