### Task 1.9: 同类型训练查重断言写反（改级#16，EnteringTelexPatService:49）

**Files:**
- Modify: `src/main/java/com/nip/service/EnteringTelexPatService.java:49`
- Test: `src/test/java/com/nip/service/EnteringTelexPatServiceTest.java`

**现行缺陷**：外层 `if (Objects.isNull(param.getId()))`（:47，仅创建路径）内 `Assert.notNull(check, "您已存在相同类型的训练...")`——check==null（首次创建，正常）反而抛"已存在"；check!=null（真重复）静默放行。`Assert.isNull(Object,String)` 重载存在（Assert.java:55-59），语义标准。

- [ ] **Step 1: 失败测试**：首次创建同类型训练，断言成功（现状：抛 IllegalArgumentException）；再建同类型第二条，断言被拒。
- [ ] **Step 3: 修复**：`Assert.isNull(check, "您已存在相同类型的训练，不能再添加同类的训练！");`
- [ ] **Step 4: 同类误用清扫**：`grep -rn "Assert.notNull\|Assert.isNull" src/main/java` 逐点核对方向语义（评审证据仅此一处写反，清扫确认无残留即可，不改无问题的点）。
- [ ] Step 5：绿 → `git commit -m "fix(p1-16): 同类型训练查重断言方向写反"`

