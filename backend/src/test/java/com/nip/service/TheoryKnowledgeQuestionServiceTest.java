package com.nip.service;

import com.nip.common.response.Response;
import com.nip.dao.TheoryKnowledgeQuestionDao;
import com.nip.dao.TheoryKnowledgeQuestionLevelDao;
import com.nip.dao.UserDao;
import com.nip.dto.TheoryKnowledgeQuestionAllDto;
import com.nip.entity.TheoryKnowledgeQuestionEntity;
import com.nip.entity.UserEntity;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@QuarkusTest
class TheoryKnowledgeQuestionServiceTest {
  @Inject TheoryKnowledgeQuestionDao questionDao;
  @Inject TheoryKnowledgeQuestionLevelDao levelDao;
  @Inject UserDao userDao;
  @Inject UserService userService;

  private UserEntity user(String name) {
    UserEntity u = new UserEntity();
    u.setUserName(name);
    u.setUserAccount(name + "-" + UUID.randomUUID());
    return userDao.save(u);
  }

  private TheoryKnowledgeQuestionEntity question(String topic, String levelId, String creatorId) {
    TheoryKnowledgeQuestionEntity q = new TheoryKnowledgeQuestionEntity();
    q.setTopic(topic);
    q.setLevelId(levelId);
    q.setType(1);
    q.setCreateUserId(creatorId);
    return questionDao.save(q);
  }

  @Test
  @TestTransaction
  void creatorNamesResolvedViaBatchQueryingOnlyCreatorIds() {
    UserEntity creatorOne = user("creator-one");
    UserEntity creatorTwo = user("creator-two");
    UserEntity unrelated = user("unrelated");

    String levelId = "level-" + UUID.randomUUID();
    question("q-one", levelId, creatorOne.getId());
    question("q-two", levelId, creatorTwo.getId());

    CountingUserDao countingUserDao = new CountingUserDao();
    TheoryKnowledgeQuestionService svc =
        new TheoryKnowledgeQuestionService(questionDao, userService, levelDao, countingUserDao);

    Response<List<TheoryKnowledgeQuestionAllDto>> response =
        svc.findAllQuestionByLevelId(levelId, "", "");
    List<TheoryKnowledgeQuestionAllDto> dtos = response.getData();

    // 完整 DTO 内容：每题的创建人名称按各自 createUserId 解析
    Map<String, String> nameByTopic = new HashMap<>();
    for (TheoryKnowledgeQuestionAllDto dto : dtos) {
      nameByTopic.put(dto.getTopic(), dto.getCreateUserName());
    }
    assertEquals(2, dtos.size());
    assertEquals("creator-one", nameByTopic.get("q-one"));
    assertEquals("creator-two", nameByTopic.get("q-two"));

    // N+1 契约：仅一次批量查询，且只查两个创建人，绝不加载无关用户
    assertEquals(1, countingUserDao.queryByIdInCalls, "createUserName 应由一次批量 queryByIdIn 解析");
    assertEquals(0, countingUserDao.findAllCalls, "不得 findAll 全表加载用户");
    assertEquals(Set.of(creatorOne.getId(), creatorTwo.getId()), countingUserDao.lastIds,
        "只查询两个创建人 id");
    assertFalse(countingUserDao.lastIds.contains(unrelated.getId()), "无关用户不得被查询");
  }

  static class CountingUserDao extends UserDao {
    int queryByIdInCalls;
    int findAllCalls;
    Set<String> lastIds;

    @Override
    public List<UserEntity> queryByIdIn(Set<String> ids) {
      queryByIdInCalls++;
      lastIds = ids;
      return super.queryByIdIn(ids);
    }

    @Override
    public PanacheQuery<UserEntity> findAll() {
      findAllCalls++;
      return super.findAll();
    }
  }
}
