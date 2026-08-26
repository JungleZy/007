## Phase 7：长尾模式规则（最后执行，机械化）

每家族一次提交 + `clean verify`；位置清单以 `agent://SvcCoreP2` 判定表与审计 §6 为准。

- [ ] findById 裸解引用 → `findByIdOptional(...).orElseThrow(() -> new IllegalArgumentException(...))`（P2-30/49/70/71、RoleService:91-94、TheoryKnowledgeExamService:346-352）；抽 3 处补边界单测。
- [ ] Integer 拆箱（`==`/compareTo）→ `Objects.equals`/前置判空（P2-24/31/32/33/55/56）；抽 3 处补单测。
- [ ] `nextInt(size-1)` 其余站点（PostRadiotelephone:54 等）——**逐点确认后改，禁止盲替换**：若后续存在 +1 或成对索引使用，size-1 是正确写法。
- [ ] 循环内 `new ObjectMapper()` → 注入单例（MenusService:197/RoleService:116，已核实）；N+1 改批量 in 查（DeviceService:78/DeviceTypeService:57，已核实）。
- [ ] P2-A 缺 @Transactional 写路径清单逐方法补注解；自调用改入口事务（同 Task 1.10 模式）。
- [ ] 死代码：DemoService.test、AsyncSave :566-580、P2-84 注释 handler 删除；P2-13 守卫方法**接入**调用点而非删除。
- [ ] `MilitaryTermDataService.getKey:76-85` 改按明确列名取值；提示/变量用错（P2-62/63/64）、两套及格线（P2-77/78）统一。

