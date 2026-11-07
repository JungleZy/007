# 个人岗位电子键评分分类复核

## 实际调用链与复现

个人岗位电子键 `PostTelegraphKeyPatTrainService.countScore` 和电子键组训 `GeneralKeyPatService.countScore` 均调用 `KeyPatUtils.handle`；手键使用 MessageComparisonService，因此此前手键整改并没有自动修复电子键。

新增 ElectronicKeyClassificationTest 的第一轮16项用例在旧实现中有11项失败，证明问题可独立复现：

| 场景 | 旧行为 | 修改后 |
| --- | --- | --- |
| 1234拍成134、124 | 只用contains，判错码 | 少码，允许缺字在组内任意位置 |
| 1234拍成12934、12394 | 判错码 | 多码，跳过任意插入位置后需完整匹配报底 |
| 长度不等且同时有错字 | 可能受到后续结构对齐干扰 | 归为错码，不再加多少码 |
| 1111?1294对应1234 | 改错和错码同时计数 | 失败改错只按错码；成功改错保留改错项 |
| 两组互换 | 单侧上下行识别，另一端可成为错码或少组 | 同页成对识别，一对一次串组，两端均标记 |
| 一组拍成远处报文，下一组仍在原位 | 向后找到同值后连锁判少组，测试1处错误变9处错误 | 相邻正确位置优先，孤立错码不拖动后续 |
| 中间缺10组与尾部缺10组 | 分类不一致 | 完整10组按行、余数组按组，互斥扣分 |
| 尾部多1组 | 向上取整为多1行 | 多1组，不多计1行 |
| 多页累计少行 | 最后一页覆盖前页计数 | 逐页累计 |
| 整页未提交 | 同时加100少组与10少行 | 只按10行，部分页按完整行及余数组分别处理 |

说明：旧电子键普通多/少码与错码分支原本通过isGoon互斥，不能声称它与手键所有重复扣分机制完全相同。本轮确认的重复扣分主要包括失败改错和整页缺失。

## 实现与兼容性

- 比较器保持逐页输入，不跨翻页符匹配。先处理正确匹配和成对串组，再验证结构对齐，最后判多/少码或错码。
- 原始拍发记录不原地改写；合并组仅在确实等于连续报底组拼接时按少间隔拆分，避免任意8码内容被强行拆成两组。
- 正确率使用实际匹配正确组计数。一对串组扣一次分，但两组均不计正确；额外组不会被算成正确组。
- 个人与组训漏页均使用同一recordMissingGroups规则。
- 前端个人/组训成绩解析优先使用服务端bunchGroup/more/lack标记，避免改错历史覆盖分类；额外报文带改错时不再读取空报底造成异常。
- 前端调用面核对 TelegramApi.js 的 PostTelegraphKeyPatTrain 详情/取页/结算，以及 electronKeyZuXun.js 的 generalKeyPat 详情/取页/结算。端点、参数、信封、扣分字段名及结果中的数字索引/分类标记保持原形态，改变的是分类与计数结果。
- 表结构不变。project006.sql中t_post_telegraph_key_pat_train、其page/page_value/more表均为InnoDB。沿用现有事务和先校验再重建派生结果的流程。

## 验证与边界

JDK21、backend目录执行：

```powershell
.\mvnw.cmd -o -B '-Dtest=ElectronicKeyClassificationTest,ScoringConsistencyTest,HandKeyDeductionClassificationTest' '-Dquarkus.log.file.enable=false' test
```

65项通过，其中电子键专项22项。前端电子键分类与翻页测试10项通过；ESLint无错误，组训脚本3条原有未使用变量警告。

PostTelegraphKeyPatTrainServiceTest已补充任意位置多少码、失败改错和完整/部分页漏交的实际结算断言，并编译成功。但运行时Testcontainers找不到Docker，测试数据库启动失败，不能宣称数据库结算或真实浏览器联调通过。未运行全量clean verify。

后端需加载新代码后生效，历史已完成训练的finish接口继续直接返回既有结果，旧成绩不自动重算。本轮没有批量改写用户历史成绩。
