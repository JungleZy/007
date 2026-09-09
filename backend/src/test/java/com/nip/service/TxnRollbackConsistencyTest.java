package com.nip.service;

import com.nip.common.constants.MessageConstants;
import com.nip.common.constants.ResponseCode;
import com.nip.common.response.Response;
import com.nip.common.utils.MD5Util;
import com.nip.dao.TestPaperDao;
import com.nip.dao.TestPaperQuestionDao;
import com.nip.dao.TheoryKnowledgeExamDao;
import com.nip.dao.TheoryKnowledgeExamTestPaperDao;
import com.nip.dao.TheoryKnowledgeExamUserDao;
import com.nip.dao.UserDao;
import com.nip.dao.UserRoleDao;
import com.nip.dto.TestPaperDto;
import com.nip.dto.TestPaperQuestionDto;
import com.nip.dto.TheoryKnowledgeExamDto;
import com.nip.dto.LoginSessionDto;
import com.nip.entity.UserEntity;
import com.nip.entity.UserRoleEntity;
import com.nip.testsupport.Fixtures;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.AbstractList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 1：@Transactional 方法内吞异常收口后的事务一致性回归。
 * 契约：写路径失败时不得出现「部分写入已提交 + 对外返回错误」的组合，要么全写要么全不写。
 */
@QuarkusTest
class TxnRollbackConsistencyTest {
  @Inject UserService userService;
  @Inject UserDao userDao;
  @Inject UserRoleDao userRoleDao;
  @Inject TheoryKnowledgeExamService examService;
  @Inject TheoryKnowledgeExamDao examDao;
  @Inject TheoryKnowledgeExamTestPaperDao examTestPaperDao;
  @Inject TheoryKnowledgeExamUserDao examUserDao;
  @Inject TestPaperService testPaperService;
  @Inject TestPaperDao testPaperDao;
  @Inject TestPaperQuestionDao testPaperQuestionDao;

  private TestPaperDto paper(String name) {
    TestPaperDto paper = new TestPaperDto();
    paper.setName(name);
    paper.setTotal(100);
    paper.setPassMark(60);
    TestPaperQuestionDto q = new TestPaperQuestionDto();
    q.setTopic("1+1=?");
    q.setType(1);
    q.setAnswer("2");
    paper.setSingleChoice(List.of(q));
    paper.setMultipleChoice(List.of());
    paper.setJudge(List.of());
    paper.setCompletion(List.of());
    paper.setShortAnswer(List.of());
    return paper;
  }

  @Test
  void addUserRoleKeepsOldRolesWhenAssignmentFailsMidway() {
    UserEntity user = Fixtures.user(userDao, UUID.randomUUID().toString());
    String keptRoleId = UUID.randomUUID().toString();
    UserRoleEntity existing = new UserRoleEntity();
    existing.setUserId(user.getId());
    existing.setRoleId(keptRoleId);
    userRoleDao.save(existing); // 独立事务提交，作为「全在」基线

    String newRoleId = UUID.randomUUID().toString();
    // 第 1 个角色写入后迭代抛错：模拟 delete + save 循环中途失败
    List<String> explodingRoleIds = new AbstractList<>() {
      @Override
      public String get(int index) {
        if (index == 0) {
          return newRoleId;
        }
        throw new IllegalStateException("模拟角色写入中途失败");
      }

      @Override
      public int size() {
        return 2;
      }
    };

    assertFalse(userService.addUserRole(user.getId(), explodingRoleIds), "中途失败仍须返回 false 信封");
    assertEquals(1, userRoleDao.count("userId", user.getId()), "失败后该用户角色行数必须回到原状");
    assertEquals(1, userRoleDao.count("userId = ?1 and roleId = ?2", user.getId(), keptRoleId),
        "原有角色不得被删除后提交");
    assertEquals(0, userRoleDao.count("roleId", newRoleId), "半途写入的新角色不得提交");
  }

  @Test
  void deleteTheoryKnowledgeExamRemovesAllThreeTablesTogether() {
    String token = UUID.randomUUID().toString();
    UserEntity user = Fixtures.user(userDao, token);
    String title = "txn-exam-" + UUID.randomUUID();
    TheoryKnowledgeExamDto dto = new TheoryKnowledgeExamDto();
    dto.setTitle(title);
    dto.setDuration("60");
    dto.setTestPaper(paper("txn-exam-paper-" + UUID.randomUUID()));
    dto.setStuId(List.of(user.getId()));
    examService.saveTheoryKnowledgeExam(token, dto);

    String examId = examDao.find("title", title).firstResult().getId();
    assertEquals(1, examUserDao.count("examId", examId));
    assertEquals(1, examTestPaperDao.count("examId", examId));

    examService.deleteTheoryKnowledgeExam(examId);

    assertNull(examDao.findById(examId), "考试主表行必须删除");
    assertEquals(0, examUserDao.count("examId", examId), "考生行必须与考试同生共死");
    assertEquals(0, examTestPaperDao.count("examId", examId), "试卷快照必须与考试同生共死");
  }

  @Test
  void deleteTheoryKnowledgeExamWithMissingIdNoLongerReportsHandledError() {
    // 控制器传的是 map.get("examId")，字段缺失即为 null；旧代码 catch 掉 IllegalArgumentException
    // 后返回 error() 信封，把编程错误伪装成已处理的业务错误。
    assertThrows(IllegalArgumentException.class, () -> examService.deleteTheoryKnowledgeExam(null));
  }

  @Test
  void deleteTestPaperRemovesQuestionsAndPaperTogether() {
    String token = UUID.randomUUID().toString();
    Fixtures.user(userDao, token);
    TestPaperDto dto = paper("txn-paper-" + UUID.randomUUID());
    testPaperService.saveTestPaper(token, dto);

    String paperId = testPaperDao.find("name", dto.getName()).firstResult().getId();
    assertEquals(1, testPaperQuestionDao.count("testPaperId", paperId));

    testPaperService.deleteTestPaper(paperId);

    assertNull(testPaperDao.findById(paperId), "试卷主表行必须删除");
    assertEquals(0, testPaperQuestionDao.count("testPaperId", paperId), "题目必须与试卷同生共死");
  }

  @Test
  void deleteTestPaperWithMissingIdNoLongerReportsHandledError() {
    assertThrows(IllegalArgumentException.class, () -> testPaperService.deleteTestPaper(null));
  }

  @Test
  void failedLoginDoesNotCommitTokenUpdate() {
    // 账号密码都对但用户没有任何角色 => roleDao.findRoleByUserId 抛 NoResultException。
    // 旧代码 catch 后事务照常提交：登录对外报错，新 token 却已落库（失败登录留下可用凭证）。
    String account = "txn-login-" + UUID.randomUUID();
    String plainPassword = "pw-" + account;
    UserEntity u = new UserEntity();
    u.setUserName("tester");
    u.setUserAccount(account);
    u.setPassword(MD5Util.encrypt(plainPassword));
    u.setStatus(0);
    UserEntity saved = userDao.save(u);

    Response<LoginSessionDto> res = userService.login(account, plainPassword, UUID.randomUUID().toString());

    assertEquals(ResponseCode.SYSTEM_ERROR.getCode(), res.getCode(), "无角色用户登录必须返回错误信封");
    assertNull(userDao.findById(saved.getId()).getToken(), "登录失败不得提交 token 更新");
  }

  @Test
  void loginWithUnknownAccountKeepsBusinessEnvelope() {
    Response<LoginSessionDto> res = userService.login("txn-absent-" + UUID.randomUUID(), "x",
        UUID.randomUUID().toString());

    assertEquals(ResponseCode.SYSTEM_ERROR.getCode(), res.getCode());
    assertEquals(MessageConstants.LOGIN_USERACCOUNT_ERROR, res.getDescription(),
        "业务失败必须保留原有提示，不得退化成未分类异常");
  }
}
