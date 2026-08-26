### Task 1.8: 军语干扰项死循环（P0#22，PostMilitaryTermTrainService:124-189）

**Files:**
- Modify: `src/main/java/com/nip/service/PostMilitaryTermTrainService.java:124-189`
- Test: `src/test/java/com/nip/service/PostMilitaryTermTrainServiceTest.java`

**现行缺陷**：:133/:147/:171 三处 `random.nextInt(size - 1)`（末元素永不选中）；:165-189 `while (flag <= 3)` 在候选值不足以凑 3 个唯一干扰项时永不退出。`generateTestPaper`（:124-126 签名已核实）:190-209 无 dao 调用，纯内存装配——可直接单测。

- [ ] **Step 1: 失败测试**

```java
@Test
void generateTestPaperTerminatesWithExactlyFourCandidates() {
  List<MilitaryTermDataEntity> four = /* 4 个同类型、value 互异的实体（new + setter） */;
  Map<String, List<MilitaryTermDataEntity>> dataMap = Map.of("type1", four);
  PostMilitaryTermTrainAddDto dto = new PostMilitaryTermTrainAddDto();
  dto.setTypes(List.of("type1"));
  dto.setTotalNumber(10);
  List<PostMilitaryTermTrainTestPaperEntity> out = new ArrayList<>();
  assertTimeoutPreemptively(Duration.ofSeconds(2),
      () -> service.generateTestPaper(dto, new PostMilitaryTermTrainEntity(), dataMap, out));
  // 每题 4 个选项且互异
  out.forEach(p -> { /* 断言选项 map size==4（读实体的选项字段结构后补齐） */ });
}
```

- [ ] **Step 3: 修复**
  1. 三处 `nextInt(size - 1)` → `nextInt(size)`（:133/:147/:171，`size==1` 分支保留）。
  2. 循环体前对该类型候选做 value 去重校验：`long distinct = militaryTermDataEntities.stream().map(MilitaryTermDataEntity::getValue).filter(ObjectUtil::isNotEmpty).distinct().count(); if (distinct < 4) throw new IllegalArgumentException("类型 " + dataId + " 有效题目不足4条，无法生成干扰项");`
  3. while 循环加护栏：`int attempts = 0;` 循环内超过 100 次即降级——从 distinct 值中顺序补足 `options` 到 4 个**互异**项（`if (!options.contains(v)) options.add(v)`，正确答案已在 options[0]，不会重复放入）并 `log.warn`，然后 break。
- [ ] Step 4-5：绿 → `git commit -m "fix(p0-22): 干扰项生成死循环与随机偏置"`

