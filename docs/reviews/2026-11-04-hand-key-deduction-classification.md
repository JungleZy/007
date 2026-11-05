# 个人岗位与组训手键重复错码扣分修复

## 根因与范围

个人岗位 `PostTelegramTrainService.processPageComparisons` 与手键组训 `GeneralTickerPatService.processPageComparisons` 都调用 `MessageComparisonService.comparison`。比较器会先识别多少行、多少组、串组、间隔及多少码等具体错误，同时将不匹配计入 `PostTelegramTrainScoreVO.errorNumber`。两处结算原来都直接用这个总数计算 `errorWord`，因此已经按具体分类扣过分的错误会再次扣错码分。

修复新增内部计数 `errorCodeNumber`，只在检测类型为 `ERROR_CODE` 时增加。两处结算的 `errorWord`、`errorWordNumber` 改用该计数；`errorNumber` 保持原有总错误统计口径，正确组数及准确率计算没有改动。

补充改错去重：解析时记录每个解析组已累计的改错扣分；该组最终仍判为错码时，只保留错码扣分，去掉该组改错扣分。成功改错组按原规则扣改错分，不会被另一组失败改错影响。组内映射只用于当前页结算，不写入 JSON。

示例规则：错码每项扣 7 分、多少码每项扣 3 分，满分 100。

| 报底 / 拍发 | 修复前 | 修复后 |
| --- | --- | --- |
| 1234 / 123 | 错码7分 + 多少码3分，得90分 | 多少码3分，得97分 |
| 1234 / 12345 | 错码7分 + 多少码3分，得90分 | 多少码3分，得97分 |
| 1234 / 1294 | 错码7分，得93分 | 错码7分，得93分 |
| 两组分别发生长度错误与独立错码 | 得83分 | 分别扣3分与7分，得90分 |

## 调用面与数据边界

少行重复统计修复：LineDetector已识别的中间缺行会推进报底游标，比较结束后只按游标尚未覆盖的尾部补算少行，不再按原始拍发组数差重复累加。40组报底分别缺首行、中间行、末行均为1行，中间和末尾同时缺失为2行；两侧扣分方法一致。该次相关45项测试通过，旧的comparison-less-line快照由2行修正为1行。历史已结算成绩不自动改写。

补充多少码判定：长度不等不再直接等价于多少码。少码要求实际拍发为报底的子序列；多码要求报底为实际拍发的子序列，可跳过任意位置、多个位置的缺失或多余字符。保留字符仍不匹配的归为错码。例如1234对应234/134/124/123均为少码，91234/19234/12934/12394/12349均为多码，129/12945为错码。个人岗位和组训同步应用。相关后端测试现为53项通过。

- 前端个人岗位 `HandKeyTrainScore.vue` 与组训 `handkeyZuXun/train/student/score.vue` 的扣分表直接读取 `deductInfo.errorWordNumber/errorWord` 和 `quantoCodeNumber/quantoCode`；无需改变字段名或响应形态。
- API 调用面核对了 `common/api/TelegramApi.js` 与 `common/api/handkeyZuXun.js`，此次没有改变参数、端点、业务码或返回信封。
- 改动为内部统计对象、比较器及两处结算使用的计数；没有实体表结构变更。仓库快照中的 `t_post_telegram_train`、`general_ticker_pat_train_user`、`general_ticker_pat_train_user_value` 均为 InnoDB。
- 已结算记录不会自动重算：个人岗位 finish 在已完成时直接返回已有结果，组训 finishUser 对 isFinish=1 同样直接返回。旧数据库中的分数、扣分明细没有批量修改。

## 验证

JDK 21，backend 下执行：

```powershell
.\mvnw.cmd -o -B '-Dtest=HandKeyDeductionClassificationTest,PostTelegramTrainScoreTest,MessageComparisonBoundsTest,TickerPatUtilsTest,TickerPatUtilsCharacterizationTest,ScoringConsistencyTest' '-Dquarkus.log.file.enable=false' test
```

最终 34 项测试全部通过。新增 9 项覆盖多少码、标准错码、正确报文、独立错误组合、多少组、串组及分字间隔识别、失败改错只扣错码与成功改错独立计分，并同时执行个人岗位和组训的真实扣分计算方法。修复前长度错误用例得到90而非97、混合错误用例得到83而非90，已复现重复扣分。

旧快照只新增内部计数字段；快照比较统一 CRLF/LF，解决 Windows 检出格式造成的非业务差异。

本轮未运行依赖 Docker 的数据库/REST 全量套件，不能用上述单测结果替代真实训练结束、入库及浏览器联调验收。
