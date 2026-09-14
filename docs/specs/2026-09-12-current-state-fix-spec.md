# 当前复核整改规格（2026-09-12，最终回写）

对应复核报告：[`../reviews/2026-09-12-current-state-review.md`](../reviews/2026-09-12-current-state-review.md)。本规格的仓内任务已实施并通过最终本地验证；外部前置在 §4 保留。

## 1. 不可变契约

1. 业务失败继续 HTTP 200 + JSON `code`；203/204/206 文案和值不变；无权限统一 207；终态统一 208。
2. 跨栈请求字段与调用点必须同一提交切换；不得通过兼容别名掩盖旧协议。
3. 任何以用户/考试/训练 id 定位的写路径必须从 token 得到 actor，再执行管理员或对象归属判定；“创建”接口携带现有 id 时也必须先判定，不能覆盖属主后再保存。
4. 评分、速率和用时不得直接以客户端自报值作为权威；若域没有可重建采集时间轴，必须先补采集契约和迁移，不能只删除字段后静默降级。
5. 全局词库、评分基准、考试生命周期和主数据写操作必须 `@RequireAdmin`；训练自操作必须显式 owner/member 判定。
6. IPC 进入特权命令前必须 canonicalize 且拒绝 `..`、绝对路径外逸和多段路径绕过。

## 2. 修复范围

### S1：考试与评分基准授权（P1）

- [x] 考试删除/全场操作管理员授权；type=2 学员成员入场保留；现有 id takeover 防护和行为回归完成。
- [x] 收报速率配置写端点管理员授权；普通用户 207、管理员成功行为回归完成。

### S2：活跃个人训练域主体与评分（P1）

- [x] 汉字/报话 begin/finish/details token + owner；服务端从题面/答案/时钟计算，终态幂等；错误信封不清理输入。
- [x] English type=2、Pinyin type=4、尾部多余输入计错、恢复时钟和 accuracy=100 schema 已修复并回归。

### S3：其它活跃训练域完整授权与采集口径（P2/P3）

- [x] 军语 owner/锁/终态/试卷题目唯一性/答案脱敏完成；综合电子键 source snapshot + active clock + 服务端指标完成，迁移 04。
- [x] 报话统计改持久化 server session clock，重复/过期 session 不重复累计，迁移 05。

### S4：主数据方法级授权与架构守卫（P2）

- [x] 全局主数据写端点管理员授权；Masthead 按实际 PostTicker 训练 owner/admin 判定；行为回归覆盖 207/200。

### S5：Electron 特权路径与死函数（P3）

- [x] 串口路径 canonicalize + 真实设备校验 + 非 shell 提权参数；恶意路径测试通过。
- [x] 数据报不可达 reset 死函数删除。

### S6：交付与文档门禁（P2/P3）

- [x] 前端 CI/本地执行完整 `npm run test`，当前本地证据 31/31 + build。
- [x] 当前修复已同步综合电子键权威指标、报话持久化服务器时钟、军语完整性、Masthead owner authorization、English 类型/余量/时钟/schema；迁移 04/05/06 已加入 `backend/database/migrations/`。
- [x] 更新 backend README、AGENTS、docs README、release runbook 和当前 review 的数字/HEAD/WS/webSecurity 描述；历史 native 测试记录保留。
- [x] 运行态已验证项与外部前置分列；不把 `webSecurity:false`、Windows/ARM64、桌面后端 DB 环境写成已闭环。

## 3. 验收（已验证项）

- [x] 后端最终全量 `clean verify`：442 测试 / 102 suite，0 失败 0 错误 0 跳过。
- [x] 前端 31/31 + build；隔离 prod fast-jar 普通用户 207、管理员 200；真实浏览器登录/仪表盘/英文页。
- [x] migration rehearsal：17 个脚本（16 schema + 1 data）双快照通过；migration06 后 status=2、accuracy=100。
- [x] 其余行为验收有源码、专项回归或隔离 runtime 证据；外部前置按 §4 保留。

## 4. 非目标与外部前置

`webSecurity:false`、`app://` 改造、可信证书链、Windows/ARM64 native CI、桌面 native 后端 DB 凭据 provisioning、真实硬件串口、G4 客户环境、存量训练切换窗口和发布负责人签收仍是外部前置。浏览器 license 校验是 origin-scoped；本地证据不代表 GitHub Actions 已绿色。
