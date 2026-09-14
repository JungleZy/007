# 核心训练整改规格（2026-09-14）

对应评审：[`../reviews/2026-09-14-core-training-review.md`](../reviews/2026-09-14-core-training-review.md)。本规格覆盖本轮确认的仓内问题；不把上一轮已闭合项目重新实现。

## 1. 不可变契约

1. 业务失败继续 HTTP 200 + JSON 信封；203/204/206 值和文案不变；授权拒绝为 207；不可重试终态为 208。
2. 所有以训练 id、成员 id、报底 id 定位的读写路径必须从 token 得到 actor，再做 owner/member/organizer/admin 判定；前端 `v-per` 不是边界。
3. 创建不得用客户端既有 id 接管记录；更新先加载旧记录、判属主，再写入允许字段。
4. 成绩、速率、用时和完成状态不得采信客户端自报聚合值；已有采集时间轴的域从服务端原始采集记录重算。经典旧域若没有采集时间轴，必须补采集契约与迁移，不能以 owner-only 自报字段作为最终完成状态。
5. 全局收报/拍发配置只允许管理员写；普通用户仍可按现有契约读取。
6. GET 的 `trainId/attempt` 必须显式成为 query；不依赖 Axios wrapper 的隐式 data→params 转换。

## 2. 修复任务

### S1：综合组训读授权（P1）

- 为 GeneralTickerPat 的 detail/statistic、单房间/全房间 roster 增加 actor 解析和成员/创建者/组训人员/管理员读判定。
- 为 GeneralKeyPat 的 findPage/detail/patDetail/getPatValue/statistics/getScore/online 增加相同判定；per-user 查询只能访问被授权训练中的目标成员。
- `getScore`、全房间 roster 若无活跃消费者，删除孤儿端点；若保留，必须改为 train-scope 且授权。不得通过无条件返回全局数据维持旧行为。
- 所有拒绝回归为 207，不能返回 202 或 HTTP 403。

### S2：经典岗位收报（P1）

- `PostTickerTapeTrain` 的 getById/begin/finish/reset/findPage/uploadResult 全部传 token 并 owner-gate；如产品需教员控制，使用已有 writableTrain 语义并写明成员范围。
- uploadResult 必须检查状态、attempt/重复提交和既有值行，终态重复返回 208，不追加重复值；findPage 的答案键只向授权主体返回。
- 保持服务端评分逻辑，不以删除字段代替授权。

### S3：经典拍发/电传/收报主体与结果（P1）

- `TelegramTrain` 控制、详情、日志、报底详情和报底保存传 token 并 owner-gate；禁止请求体 userId/trainId 把 actor 重定向到他人；补服务端采集时间轴/迁移并从原始事件重算结果。
  `TelexPatTrain` 现有 id 更新先 owner-gate，创建由服务端生成 id；详情 owner/member；补采集契约后服务端重算聚合字段，未知 id 不得静默成功。
  `TickerTapeTrain` getById/begin/pause/goOn/finish 传 token 并 owner-gate；补服务端时钟/暂停累计或等价采集契约，finish 不接受客户端 validTime/mark/schedule 作为权威。

### S4：电子键与手键边界修复（P1/P2）

- 综合电子键历史 PAUSE 且 `protocol_version=NULL` 时，自动创建新训练不能被 208 永久阻断；旧历史记录不得伪造重算，按明确的关闭/废弃策略处理。
- 单字电子键 update 先 owner-gate，禁止客户端覆盖不属于 actor 的统计行。
- 手键 reset 后 statistic 对 NULL score 安全；综合手键 `statisticsAllAvg` 传入真实点/划/码/字/组用时；目标读面完成后，清理无效 `@RequestPass` 依赖并让 socket 辅助读也走授权。
- 手键新建通知仅在事务提交成功后发送。

### S5：配置、wire 和诊断（P2/P3）

- `TickerTapeTrainStageSetting.add`、`TelegramTrain.saveSetting` 加 `@RequireAdmin`，行为断言普通用户 207、管理员 200。
- handkey/electron `startTrainUser` 统一显式 query；加最小前端契约测试确保 `trainId/attempt` 在 query 中。
- 移除 `saveBaseTrain` controller 吞异常；坏数值输入映射为既有参数错误信封，不产生未诊断 500。
- 删除或替换综合手键/电子键脆弱死代码，但不得把清理当作授权修复。

## 3. 验收标准

- 每个授权端点：owner/member/organizer/admin 成功范围明确，外人和普通无关用户得到 `code:207`，数据库无副作用。
- 授权矩阵：综合手键/电子键 train-scoped 读的外人 207、member/creator/organizer/admin 200；per-user 读的非目标 207；岗位收报、TickerTape、TelegramTrain（含日志/报底）、Telex 的跨人读写均为 207 且数据库无副作用；两个全局配置普通用户 207、管理员 200。
- 状态与输入矩阵：无凭据 203；不存在/可修正参数 202；终态或轮次过期 208；Telex 未知既有 id 不得静默 200，必须服务端生成新 id 或返回 202。
- 有采集时间轴的成绩/时钟篡改不能改变结果，经典旧域完成采集协议迁移后同样必须服务端重算；重复提交不重复计分，终态返回 208。
- legacy PAUSE fixture 可创建后续综合电子键训练，且旧记录不会被错误结算；reset 后手键统计返回有效信封，真实平均用时与原始时间轴一致；新建通知只在事务成功提交后可见。
- handkey/electron GET startTrain 只发送 query、不发送 body；三类主要训练的开始、采集、提交、结束、结果查看和网络失败/207/208 页面状态可观察。
- 受影响后端专项测试、前端完整 test/build、迁移/现有回归和最终 GitHub Actions 全部通过。

## 4. 非目标

不更换认证协议、不修改 203/204/206、不改变 HTTP 200 信封、不引入兼容别名；真实硬件、客户现场、可信证书、Windows/ARM64 native 仍是外部验收。
