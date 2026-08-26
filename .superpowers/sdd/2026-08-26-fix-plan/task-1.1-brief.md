### Task 1.1: 试卷编辑丢题（P0#1，TestPaperService:59-93）

**Files:**
- Modify: `src/main/java/com/nip/service/TestPaperService.java:59-93`
- Test: `src/test/java/com/nip/service/TestPaperServiceTest.java`

**现行缺陷**：:65 编辑先 `deleteAllByTestPaperId`；:77-81 五个 `addAll(dto.getXxx())` 任一 null 即 NPE；:89-92 catch 吞掉后返回 error，但事务因正常返回而**提交**，题目永久丢失。

- [ ] **Step 1: 失败测试**（注意：题干字段是 `topic` 不是 title；`type` 必设——findAllTestPaper:130 对 null Integer 拆箱 NPE）

```java
package com.nip.service;

import com.nip.dao.*;
import com.nip.dto.TestPaperDto;
import com.nip.dto.TestPaperQuestionDto;
import com.nip.testsupport.*;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(MySqlResource.class)
class TestPaperServiceTest {
  @Inject TestPaperService service;
  @Inject TestPaperQuestionDao questionDao;
  @Inject UserDao userDao;

  private TestPaperDto paper(String name) {
    TestPaperDto dto = new TestPaperDto();
    dto.setName(name);
    dto.setTotal(100);
    dto.setPassMark(60);
    TestPaperQuestionDto q = new TestPaperQuestionDto();
    q.setTopic("1+1=?");
    q.setType(1); // 单选；null 会在 findAllTestPaper:130 拆箱 NPE
    dto.setSingleChoice(List.of(q));
    dto.setMultipleChoice(List.of());
    dto.setJudge(List.of());
    dto.setCompletion(List.of());
    dto.setShortAnswer(List.of());
    return dto;
  }

  @Test
  void updateWithNullTypeListKeepsExistingQuestions() {
    Fixtures.user(userDao, "t-paper");
    TestPaperDto dto = paper("p1");
    service.saveTestPaper("t-paper", dto);
    String paperId = service.findAllTestPaper().getData().get(0).getId();
    long before = questionDao.count("testPaperId", paperId);
    assertTrue(before > 0);

    TestPaperDto edit = paper("p1-edit");
    edit.setId(paperId);
    edit.setShortAnswer(null); // 缺一个题型列表

    try {
      service.saveTestPaper("t-paper", edit);
    } catch (RuntimeException expectedAfterFix) {
      // 修复后允许抛（null 归一后实际不抛）；关键契约在下一行
    }
    assertTrue(questionDao.count("testPaperId", paperId) > 0, "原题目不得被静默删除");
  }
}
```

- [ ] **Step 2: 运行确认失败** `$MVN test -Dtest=TestPaperServiceTest`，期望断言失败（count==0）。
- [ ] **Step 3: 修复**——归一在前、删除在后、catch 删除：

```java
@Transactional
public Response<Void> saveTestPaper(String token, TestPaperDto testPaperDto) {
  List<TestPaperQuestionDto> testPaperQuestionDtos = new ArrayList<>();
  testPaperQuestionDtos.addAll(ListUtils.nullToEmpty(testPaperDto.getSingleChoice()));
  testPaperQuestionDtos.addAll(ListUtils.nullToEmpty(testPaperDto.getMultipleChoice()));
  testPaperQuestionDtos.addAll(ListUtils.nullToEmpty(testPaperDto.getJudge()));
  testPaperQuestionDtos.addAll(ListUtils.nullToEmpty(testPaperDto.getCompletion()));
  testPaperQuestionDtos.addAll(ListUtils.nullToEmpty(testPaperDto.getShortAnswer()));

  TestPaperEntity entity = new TestPaperEntity();
  if (ObjectUtil.isNotEmpty(testPaperDto.getId())) {
    entity.setId(testPaperDto.getId());
    testPaperQuestionDao.deleteAllByTestPaperId(testPaperDto.getId()); // 新列表组装完成后才删
  }
  entity.setName(testPaperDto.getName());
  entity.setLevelId(testPaperDto.getLevelId());
  entity.setTotal(testPaperDto.getTotal());
  entity.setPassMark(testPaperDto.getPassMark());
  UserEntity userEntity = userService.getUserByToken(token);
  entity.setCreateUserId(userEntity.getId());
  entity.setCreateUserName(userEntity.getUserName());
  entity.setPassTheExamThan(testPaperDto.getPassTheExamThan());
  TestPaperEntity save = testPaperDao.save(entity);
  testPaperQuestionDtos.forEach(ques -> {
    TestPaperQuestionEntity q = PojoUtils.convertOne(ques, TestPaperQuestionEntity.class);
    q.setId(null);
    q.setTestPaperId(save.getId());
    testPaperQuestionDao.save(q);
  });
  return ResponseResult.success();
}
```

（try/catch 整体删除。异常冒出 → service 边界事务回滚 → @JWT 端点由 JWTInterceptor 兜成 HTTP 200 + SYSTEM_ERROR + 消息的结构化响应，非裸 500——见 Global Constraints。）

- [ ] **Step 4: 运行通过** 后 `git commit -m "fix(p0-1): 试卷编辑先删后写+吞异常导致丢题"`

