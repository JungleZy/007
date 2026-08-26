### Task 1.10: 军语 Excel 导入事务边界（改级#18 + P1-43，MilitaryTermDataService.saveBatch）

**Files:**
- Modify: `src/main/java/com/nip/service/MilitaryTermDataService.java:202-240`
- Test: `src/test/java/com/nip/service/MilitaryTermDataServiceTest.java`

**现行缺陷（评审修正定位）**：Excel/文件导入入口是 `MilitaryTermDataController:85-89` → `service.saveBatch`（**:202，无 @Transactional**）→ 自调用 `excelHanle`（:208-240，@Transactional 被自调用绕过）→ 前半落库不回滚。注意 `saveAll`（:47）是另一个"开发用 JSON 导入"端点、**已有** @Transactional，不要动它；入口不唯一（saveAll/saveBatch 两条）。

- [ ] **Step 1: 失败测试**：先读 `excelHanle`（:208-240）确认数据结构与 :227 的提前 `return`（首次新建父类型即退出）语义；构造"先有可成功子项落库、随后一条非法行触发异常"的导入数据调 `saveBatch`，断言库中该批**零行**（现状：前半行残留）。
- [ ] **Step 3: 修复**：`saveBatch`（:202）加 `@Transactional`——入口事务生效后，自调用 excelHanle 与跨 bean dao.save 均并入同一事务（机制已核实）。入口处校验：顶级集合与每个子级集合 null/empty → `throw new IllegalArgumentException("导入数据为空或格式不完整")`。
- [ ] Step 4-5：绿 → `git commit -m "fix(p1-18/43): 军语导入整批事务+空集合校验"`

