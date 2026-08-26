package com.nip.service;

import com.nip.dao.TheoryKnowledgeSwfDao;
import com.nip.dao.UserDao;
import com.nip.dto.TheoryKnowledgesDto;
import com.nip.dto.vo.TheoryKnowledgeSwfVO;
import com.nip.dto.vo.TheoryKnowledgeTestVO;
import com.nip.entity.TheoryKnowledgeEntity;
import com.nip.entity.UserEntity;
import com.nip.testsupport.Fixtures;
import com.nip.testsupport.MySqlResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(MySqlResource.class)
class TheoryKnowledgeServiceTest {
  @Inject TheoryKnowledgeService service;
  @Inject TheoryKnowledgeSwfDao knowledgeSwfDao;
  @Inject UserDao userDao;

  private TheoryKnowledgesDto knowledges(String title, String userId) {
    TheoryKnowledgeEntity knowledge = new TheoryKnowledgeEntity();
    knowledge.setTitle(title);
    knowledge.setCreateUserId(userId);
    TheoryKnowledgeSwfVO swf = new TheoryKnowledgeSwfVO();
    swf.setTitle("章节1");
    swf.setTest(List.of());
    TheoryKnowledgesDto dto = new TheoryKnowledgesDto();
    dto.setKnowledge(knowledge);
    dto.setKnowledgeSwfs(List.of(swf));
    return dto;
  }

  @Test
  void editWithNullSwfListKeepsExistingSwfs() {
    UserEntity user = Fixtures.user(userDao, "t-theory");
    TheoryKnowledgesDto create = knowledges("k1", user.getId());
    String knowledgeId = service.saveTheoryKnowledge(create).getData().getId();
    long before = knowledgeSwfDao.count("knowledgeId", knowledgeId);
    assertTrue(before > 0);

    TheoryKnowledgesDto edit = knowledges("k1-edit", user.getId());
    edit.getKnowledge().setId(knowledgeId);
    edit.setKnowledgeSwfs(null); // 缺课件列表的编辑请求

    try {
      service.saveTheoryKnowledge(edit);
    } catch (RuntimeException expectedAfterFix) {
      // 修复后允许抛（课件列表缺失）；关键契约在下一行
    }
    assertTrue(knowledgeSwfDao.count("knowledgeId", knowledgeId) > 0, "原课件不得被静默删除");
  }

  @Test
  void saveWithNullTestContentsDoesNotNpe() {
    UserEntity user = Fixtures.user(userDao, "t-theory-2");
    TheoryKnowledgesDto create = knowledges("k2", user.getId());
    TheoryKnowledgeTestVO test = new TheoryKnowledgeTestVO();
    test.setTitle("测验1");
    test.setVersions(1);
    test.setKnowledgeTestContents(null); // 测验缺 content 列表
    create.getKnowledgeSwfs().get(0).setTest(List.of(test));

    String knowledgeId = service.saveTheoryKnowledge(create).getData().getId();
    assertTrue(knowledgeSwfDao.count("knowledgeId", knowledgeId) > 0, "课件应落库且不抛 NPE");
  }
}
