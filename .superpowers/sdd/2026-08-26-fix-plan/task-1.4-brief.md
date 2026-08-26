### Task 1.4: 考试编辑抹掉答卷 + 快照串场 + 快照空列表（P0#4 + #19 + #20，TheoryKnowledgeExamService）

三条缺陷同一方法，一个 Task 修完。

**Files:**
- Modify: `src/main/java/com/nip/service/TheoryKnowledgeExamService.java:65-96`（saveTheoryKnowledgeExam）与 **:325-339**（examineAnalyse 读取快照的 5 处 `fromJson(...List())` 后 addAll——评审核实位置，非 :344-407）
- Test: `src/test/java/com/nip/service/TheoryKnowledgeExamServiceTest.java`

**现行缺陷**（:73-84）：快照实体经 `convertOne(testPaper, ...)` **复制源试卷 id 作为快照主键**；:74 `deleteById(testPaper.getId())` 会删掉*其他考试*的同试卷快照（create 与 edit 路径都触发——#19 确定性跨场丢失）；:75 无状态守卫删全部考生行后 :86-94 重建 score=0/state=1（#4）；:80-84 null 列表 → `toJson(null)=""` → 读回 null → addAll NPE（#20）。

- [ ] **Step 1: 失败测试**（三个用例）

```java
@Test
void twoExamsOnSamePaperKeepBothSnapshots() {
  // 同一 TestPaperDto（同 id）建考试 A、B
  // 断言：examTestPaperDao.count("examId", examA) == 1 且 examB == 1
}

@Test
void editExamWithAnsweredUsersIsRejected() {
  // 建考试，手工把一个 examUser 的 state 置 2（进行中）
  // 再次 saveTheoryKnowledgeExam 编辑，断言抛出/返回错误，且该 examUser 行未被重建（score/state 不变）
}

@Test
void analyseWithMissingTypeListDoesNotNPE() {
  // 建考试时 testPaper.setShortAnswer(null)，调 examineAnalyse，断言不抛
}
```

- [ ] **Step 3: 修复**（:72-94 替换；`count(query,params)`/`delete(query,params)` 对 PanacheRepositoryBase 合法、examId/state/score 字段存在——已核实）

```java
TestPaperDto testPaper = dto.getTestPaper();
// #4 状态守卫：已有作答/进行中/已交卷的考生存在时禁止重建
long touched = theoryKnowledgeExamUserDao
    .count("examId = ?1 and (state <> 1 or score > 0)", save.getId());
if (touched > 0) {
  throw new IllegalStateException("考试已有作答记录，禁止编辑重建考生名单");
}
// #19 只删本考试自己的旧快照，绝不按试卷 id 删
theoryKnowledgeExamTestPaperDao.delete("examId", save.getId());
theoryKnowledgeExamUserDao.deleteAllByExamId(save.getId());

TheoryKnowledgeExamTestPaperEntity snap = PojoUtils.convertOne(testPaper,
    TheoryKnowledgeExamTestPaperEntity.class);
snap.setId(null); // #19 快照永远新建，不复用源试卷主键
snap.setExamId(save.getId());
// #20 五列表 null 归一后再序列化（实体第 5 个字段名是 shortAnswer，非 shortAnswerList）
snap.setSingleChoiceList(JSONUtils.toJson(ListUtils.nullToEmpty(testPaper.getSingleChoice())));
snap.setMultipleChoiceList(JSONUtils.toJson(ListUtils.nullToEmpty(testPaper.getMultipleChoice())));
snap.setJudgeList(JSONUtils.toJson(ListUtils.nullToEmpty(testPaper.getJudge())));
snap.setCompletionList(JSONUtils.toJson(ListUtils.nullToEmpty(testPaper.getCompletion())));
snap.setShortAnswer(JSONUtils.toJson(ListUtils.nullToEmpty(testPaper.getShortAnswer())));
theoryKnowledgeExamTestPaperDao.save(snap);
```

读取侧（**:325-339**，5 处）：每个 `JSONUtils.fromJson(snapshot.getXxxList(), ...)` 包 `ListUtils.nullToEmpty(...)` 再 addAll。原 :73-76 的 `if (!StringUtils.isEmpty(testPaper.getId()))` 分支整体移除。

- [ ] Step 4-5：绿 → `git commit -m "fix(p0-4/19/20): 考试编辑守卫+快照独立主键+空列表归一"`

