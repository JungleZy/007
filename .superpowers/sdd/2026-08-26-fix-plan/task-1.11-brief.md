### Task 1.11: Phase 1 收尾门禁

- [ ] `$MVN clean verify` 全绿（预期 ≥ 13 个测试）。
- [ ] `grep -n "catch (Exception" src/main/java/com/nip/service/TestPaperService.java src/main/java/com/nip/service/TheoryKnowledgeService.java src/main/java/com/nip/service/MenusService.java` —— 确认三处吞异常 catch 已不存在。
- [ ] 提交遗留勘误：分片文档中 P0-14（死代码误报）、P2-25（credit 类型误报）加"审计更正"标注，`git commit -m "docs: 分片评审误报勘误"`。

