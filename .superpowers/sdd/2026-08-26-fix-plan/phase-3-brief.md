## Phase 3：评分核心统一

**Files:** `common/utils/TickerPatUtils.java`、`PostTelexPatTrainService`、`PostTelegraphKeyPatTrainService`、`PostTickerTapeTrainService`、各 `*StatisticalService`、`dto/score/SpeedDeduct.java`。

- [ ] **Task 3.1**: characterization 测试先行——从 `docs/database/project006.sql` 提取 2-3 组真实 patKeys/patLogs/moresTime/moresValue 样本存入 `src/test/resources/scoring/`，对 TickerPatUtils 现输出写快照断言（锁行为）。
- [ ] **Task 3.2**: 修确定性缺陷并更新快照断言：列对调、列表错位、过滤不同步、groupScore 覆盖累加（service-core P1-01/02/06/20/21 位置）；`TickerPatUtils:640` `getCodePerfectNumber()+1` → `getWordPerfectNumber()+1`。
- [ ] **Task 3.3**: 新建 `common/utils/ScoreMath.java`：`static BigDecimal rate(long count, long totalTimeMillis)`（零除返 0，注释注明单位）；三套速率公式（P2-68）与守分子/守分母漂移（P2-15/51）全部迁移至此，旧实现删除（`lsp references` 确认零调用后）。
- [ ] **Task 3.4**: `dto/score/SpeedDeduct.java` r（:22 低于扣分）/l（:27 高于加分）在 `TickerPatUtils:633/:637` 用反——修正使用点；排序四写法（P2-69）统一为查询 `ORDER BY` 或显式 sort，删除 `TickerTapeTrainService:216` 的 `Collections.swap(convert,0,1)`（改按 type 排序，P2-17 审计确认串位）。
- [ ] **Task 3.5**: finish 幂等统一（P1-08/09/10）：三训练服务 finish 前置状态检查一致化；`PostTelegramTrainService:516-520` speedLog 改按 floorNumber upsert（与 :534 的 deleteByTrainIdAndFloorNumber 语义对齐）。
- [ ] 收尾：`$MVN clean verify`，提交 `fix(scoring-N)` 系列。

