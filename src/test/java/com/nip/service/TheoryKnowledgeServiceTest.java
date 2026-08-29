package com.nip.service;

import com.nip.common.response.Response;
import com.nip.dao.TheoryKnowledgeDao;
import com.nip.dao.TheoryKnowledgeExamTestPaperDao;
import com.nip.dao.TheoryKnowledgeExamUserDao;
import com.nip.dao.TheoryKnowledgeClassifyDao;
import com.nip.dao.TheoryKnowledgeSwfDao;
import com.nip.dao.TheoryKnowledgeSwfRecordDao;
import com.nip.dao.TheoryKnowledgeTestContentDao;
import com.nip.dao.TheoryKnowledgeTestDao;
import com.nip.dao.TheoryKnowledgeTestUserDao;
import com.nip.dao.UserDao;
import com.nip.dto.TheoryKnowledgesDto;
import com.nip.dto.vo.TheoryKnowledgeSwfVO;
import com.nip.dto.vo.TheoryKnowledgeTestVO;
import com.nip.entity.TheoryKnowledgeEntity;
import com.nip.entity.TheoryKnowledgeExamTestPaperEntity;
import com.nip.entity.TheoryKnowledgeExamUserEntity;
import com.nip.entity.TheoryKnowledgeSwfEntity;
import com.nip.entity.TheoryKnowledgeSwfRecordEntity;
import com.nip.entity.TheoryKnowledgeTestContentEntity;
import com.nip.entity.TheoryKnowledgeTestEntity;
import com.nip.entity.TheoryKnowledgeTestUserEntity;
import com.nip.entity.UserEntity;
import com.nip.testsupport.Fixtures;


import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest

class TheoryKnowledgeServiceTest {
  @Inject TheoryKnowledgeService service;
  @Inject TheoryKnowledgeSwfDao knowledgeSwfDao;
  @Inject UserDao userDao;
  @Inject TheoryKnowledgeExamUserDao examUserDao;
  @Inject TheoryKnowledgeExamTestPaperDao examTestPaperDao;
  // 真实 DAO 用于播种数据（CDI 事务提交/入会），计数用子类仅用于服务读路径
  @Inject UserService userService;
  @Inject TheoryKnowledgeClassifyDao classifyDao;
  @Inject TheoryKnowledgeDao knowledgeDao;
  @Inject TheoryKnowledgeTestDao testDao;
  @Inject TheoryKnowledgeTestContentDao contentDao;
  @Inject TheoryKnowledgeTestUserDao testUserDao;
  @Inject TheoryKnowledgeSwfRecordDao recordDao;

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

  // 为一场试卷落库若干考生成绩：新建试卷(passMark/total)，逐条写入 state=4、endTime 命中年份的考试记录
  private void seedExam(String userId, int passMark, int total, int... scores) {
    String examId = UUID.randomUUID().toString();
    TheoryKnowledgeExamTestPaperEntity paper = new TheoryKnowledgeExamTestPaperEntity();
    paper.setExamId(examId);
    paper.setPassMark(passMark);
    paper.setTotal(total);
    examTestPaperDao.save(paper);
    for (int score : scores) {
      TheoryKnowledgeExamUserEntity eu = new TheoryKnowledgeExamUserEntity();
      eu.setUserId(userId);
      eu.setExamId(examId);
      eu.setScore(score);
      eu.setState(4);
      eu.setEndTime("2099-01-01 10:00:00");
      examUserDao.save(eu);
    }
  }

  @Test
  void gradeDistributionUsesPerPaperThresholds() {
    UserEntity user = Fixtures.user(userDao, "t-grade-dist");
    // A卷 passMark=70 total=100 => goodBoundary=85；B卷 passMark=50 total=100 => goodBoundary=75
    // 唯一区分点：65(A) passMark=70 时动态落 "59"(<70)，固定 60/80 档却落 "60" —— 使 "59" 桶 2→3。
    // （65(B) passMark=50 时新旧都落 "60"，非区分点；good 计数新旧一致，仅证通过语义未变。）
    seedExam(user.getId(), 70, 100, 40, 65, 75, 90);
    seedExam(user.getId(), 50, 100, 40, 65, 85);

    Response<Object> response = service.gradeCount("t-grade-dist", "2099", "", 0);
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) response.getData();
    @SuppressWarnings("unchecked")
    Map<String, Integer> details = (Map<String, Integer>) data.get("down");
    @SuppressWarnings("unchecked")
    Map<String, Integer> result = (Map<String, Integer>) data.get("up");

    assertEquals(3, details.get("59"));
    assertEquals(2, details.get("60"));
    assertEquals(2, details.get("81"));
    assertEquals(4, result.get("good"));
  }

  // ---- Task 6: 批量装配 + 统计批处理 ----------------------------------------

  private TheoryKnowledgeService serviceWith(CountingKnowledgeDao k, CountingSwfDao s, CountingRecordDao r,
                                             CountingTestUserDao tu, CountingTestDao t, CountingContentDao c) {
    return new TheoryKnowledgeService(userService, k, s, r, tu, t, c, examUserDao, classifyDao);
  }

  @Test
  @TestTransaction
  void detailBatchesSwfTreeAndTokenMetadata() {
    UserEntity user = Fixtures.user(userDao, "t-detail-" + UUID.randomUUID());
    TheoryKnowledgeEntity knowledge = new TheoryKnowledgeEntity();
    knowledge.setTitle("k-detail");
    knowledge = knowledgeDao.save(knowledge);
    String kid = knowledge.getId();

    for (int i = 0; i < 20; i++) {
      TheoryKnowledgeSwfEntity swf = new TheoryKnowledgeSwfEntity();
      swf.setKnowledgeId(kid);
      swf.setTitle("swf" + i);
      swf.setSort(i);
      swf = knowledgeSwfDao.save(swf);
      String sid = swf.getId();
      for (int j = 0; j < 3; j++) {
        TheoryKnowledgeTestEntity t = new TheoryKnowledgeTestEntity();
        t.setKnowledgeId(kid);
        t.setKnowledgeSwfId(sid);
        t.setTitle("t" + i + "-" + j);
        t.setVersions(j == 0 ? 1 : 0); // 每个课件至少一个启用版本 => haveTest=true
        t.setCreateTime(String.valueOf(1000L + j));
        t = testDao.save(t);
        String tid = t.getId();
        for (int cIdx = 0; cIdx < 2; cIdx++) {
          TheoryKnowledgeTestContentEntity ct = new TheoryKnowledgeTestContentEntity();
          ct.setKnowledgeId(kid);
          ct.setKnowledgeSwfId(sid);
          ct.setKnowledgeTestId(tid);
          ct.setCreateTime(String.valueOf(2000L + cIdx));
          contentDao.save(ct);
        }
      }
      TheoryKnowledgeTestUserEntity ans = new TheoryKnowledgeTestUserEntity();
      ans.setUserId(user.getId());
      ans.setKnowledgeId(kid);
      ans.setKnowledgeSwfId(sid);
      ans.setScore(90);
      testUserDao.save(ans);

      TheoryKnowledgeSwfRecordEntity rec = new TheoryKnowledgeSwfRecordEntity();
      rec.setUserId(user.getId());
      rec.setKnowledgeId(kid);
      rec.setKnowledgeSwfId(sid);
      rec.setJoinTime("2099-01-01 10:00:00");
      rec.setExitTime("2099-01-01 10:02:00"); // 120s => 2.0 分钟
      recordDao.save(rec);
    }

    CountingKnowledgeDao ck = new CountingKnowledgeDao();
    CountingSwfDao cs = new CountingSwfDao();
    CountingRecordDao cr = new CountingRecordDao();
    CountingTestUserDao ctu = new CountingTestUserDao();
    CountingTestDao ct = new CountingTestDao();
    CountingContentDao cc = new CountingContentDao();
    TheoryKnowledgeService svc = serviceWith(ck, cs, cr, ctu, ct, cc);

    Response<TheoryKnowledgesDto> response = svc.getByIdAndToken(kid, user.getToken());
    TheoryKnowledgesDto dto = response.getData();

    // 完整 DTO 树：20 课件 × 3 测验 × 2 考题，以及 token 派生字段
    List<TheoryKnowledgeSwfVO> swfs = dto.getKnowledgeSwfs();
    assertEquals(20, swfs.size());
    for (TheoryKnowledgeSwfVO vo : swfs) {
      assertEquals(3, vo.getTest().size(), "每课件应有3个测验");
      for (TheoryKnowledgeTestVO tvo : vo.getTest()) {
        assertEquals(2, tvo.getKnowledgeTestContents().size(), "每测验应有2道考题");
      }
      assertTrue(vo.getHaveTest(), "存在启用版本 => haveTest=true");
      assertEquals(90, vo.getScore(), "score 来自批量答案");
      assertEquals(2.0, vo.getRecord(), "record=120s/60=2.0 分钟");
    }

    // 调用次数契约：每个批量 finder 恰好1次，单ID finder 0次
    assertEquals(1, ct.batchCalls);
    assertEquals(1, cc.batchCalls);
    assertEquals(1, ctu.batchCalls);
    assertEquals(1, cr.batchCalls);
    assertEquals(0, ct.singleSwfCalls);
    assertEquals(0, cc.singleTestCalls);
    assertEquals(0, ctu.singleSwfCalls);
    assertEquals(0, cr.singleSwfCalls);
  }

  @Test
  @TestTransaction
  void countBatchesKnowledgeSwfAndUserTestLookups() {
    UserEntity user = Fixtures.user(userDao, "t-count-" + UUID.randomUUID());
    TheoryKnowledgeEntity knowledge = new TheoryKnowledgeEntity();
    knowledge.setTitle("k-count");
    knowledge.setCredit(5.0);
    knowledge = knowledgeDao.save(knowledge);
    String kid = knowledge.getId();

    TheoryKnowledgeSwfEntity swfA = new TheoryKnowledgeSwfEntity();
    swfA.setKnowledgeId(kid);
    swfA.setTitle("A");
    swfA.setSort(0);
    swfA = knowledgeSwfDao.save(swfA);
    TheoryKnowledgeSwfEntity swfB = new TheoryKnowledgeSwfEntity();
    swfB.setKnowledgeId(kid);
    swfB.setTitle("B");
    swfB.setSort(1);
    swfB = knowledgeSwfDao.save(swfB);

    // A 两条记录求和 120s+180s=300s；B 一条 60s（均在 2099-03）
    saveRecord(user.getId(), kid, swfA.getId(), "2099-03-05 10:00:00", "2099-03-05 10:02:00"); // 120
    saveRecord(user.getId(), kid, swfA.getId(), "2099-03-05 11:00:00", "2099-03-05 11:03:00"); // 180
    saveRecord(user.getId(), kid, swfB.getId(), "2099-03-06 10:00:00", "2099-03-06 10:01:00"); // 60

    // 仅 A 有答案 => 已答(1) != 课件(2) => 完成判定失败 => 展示副本 credit 置 0
    TheoryKnowledgeTestUserEntity ans = new TheoryKnowledgeTestUserEntity();
    ans.setUserId(user.getId());
    ans.setKnowledgeId(kid);
    ans.setKnowledgeSwfId(swfA.getId());
    ans.setScore(100);
    testUserDao.save(ans);

    CountingKnowledgeDao ck = new CountingKnowledgeDao();
    CountingSwfDao cs = new CountingSwfDao();
    CountingRecordDao cr = new CountingRecordDao();
    CountingTestUserDao ctu = new CountingTestUserDao();
    CountingTestDao ct = new CountingTestDao();
    CountingContentDao cc = new CountingContentDao();
    TheoryKnowledgeService svc = serviceWith(ck, cs, cr, ctu, ct, cc);

    Response<Map<String, Object>> response = svc.recordStatistice(user.getToken(), "2099", "3", 0);
    @SuppressWarnings("unchecked")
    Map<Object, Object> down = (Map<Object, Object>) response.getData().get("down");

    assertEquals(1, down.size(), "down 应只有一个知识条目");
    Map.Entry<Object, Object> entry = down.entrySet().iterator().next();
    TheoryKnowledgeEntity display = (TheoryKnowledgeEntity) entry.getKey();
    assertEquals(0.0, display.getCredit(), "完成判定失败 => 展示副本 credit=0");
    @SuppressWarnings("unchecked")
    Map<String, Long> perSwf = (Map<String, Long>) entry.getValue();
    assertEquals(2, perSwf.size());
    assertEquals(300L, perSwf.get("A").longValue(), "A 课件 120s+180s=300s");
    assertEquals(60L, perSwf.get("B").longValue(), "B 课件 60s");

    // 未修改托管实体：重取知识条目 credit 仍为 5.0
    assertEquals(5.0, knowledgeDao.findById(kid).getCredit(), "托管实体不得被 count 修改");

    // 调用次数契约：批量各1次，循环内单ID 0次
    assertEquals(1, ck.batchCalls, "knowledge 批量 list 恰好1次");
    assertEquals(1, cs.batchCalls, "swf 批量 list 恰好1次");
    assertEquals(0, cs.singleKnowledgeCalls, "count 不得逐知识 findAllByKnowledgeIdOrderBySortAsc");
    assertEquals(1, ctu.batchCalls, "user-test 批量恰好1次");
    assertEquals(0, ctu.singleKnowledgeCalls, "count 不得逐知识 findAllByUserIdAndKnowledgeId");
  }

  @Test
  @TestTransaction
  void checkMonthAndDayBoundariesMatch() {
    UserEntity user = Fixtures.user(userDao, "t-boundary-" + UUID.randomUUID());
    TheoryKnowledgeEntity knowledge = new TheoryKnowledgeEntity();
    knowledge.setTitle("k-boundary");
    knowledge = knowledgeDao.save(knowledge);
    String kid = knowledge.getId();
    TheoryKnowledgeSwfEntity swf = new TheoryKnowledgeSwfEntity();
    swf.setKnowledgeId(kid);
    swf.setTitle("S");
    swf.setSort(0);
    swf = knowledgeSwfDao.save(swf);
    String sid = swf.getId();

    saveRecord(user.getId(), kid, sid, "2099-01-01 10:00:00", "2099-01-01 10:01:00"); // 1月1日 60s
    saveRecord(user.getId(), kid, sid, "2099-01-31 10:00:00", "2099-01-31 10:02:00"); // 1月31日 120s
    saveRecord(user.getId(), kid, sid, "2099-12-15 10:00:00", "2099-12-15 10:05:00"); // 12月 300s

    CountingKnowledgeDao ck = new CountingKnowledgeDao();
    CountingSwfDao cs = new CountingSwfDao();
    CountingRecordDao cr = new CountingRecordDao();
    CountingTestUserDao ctu = new CountingTestUserDao();
    CountingTestDao ct = new CountingTestDao();
    CountingContentDao cc = new CountingContentDao();
    TheoryKnowledgeService svc = serviceWith(ck, cs, cr, ctu, ct, cc);

    // 年查询 => 按月聚合，键为1..12：一月(60+120=180)，十二月(300)
    Response<Map<String, Object>> yearResp = svc.recordStatistice(user.getToken(), "2099", "", 0);
    @SuppressWarnings("unchecked")
    Map<String, Long> byMonth = (Map<String, Long>) yearResp.getData().get("up");
    assertEquals(180L, byMonth.get("1").longValue(), "一月边界 sum=180");
    assertEquals(300L, byMonth.get("12").longValue(), "十二月边界 sum=300");
    assertEquals(2, byMonth.size(), "仅一月与十二月");

    // 月查询 => 按日聚合，键为1..31：1日(60)，31日(120)
    Response<Map<String, Object>> monthResp = svc.recordStatistice(user.getToken(), "2099", "1", 0);
    @SuppressWarnings("unchecked")
    Map<String, Long> byDay = (Map<String, Long>) monthResp.getData().get("up");
    assertEquals(60L, byDay.get("1").longValue(), "1日边界=60");
    assertEquals(120L, byDay.get("31").longValue(), "31日边界=120");
    assertEquals(2, byDay.size(), "仅1日与31日");
  }

  private void saveRecord(String userId, String kid, String sid, String joinTime, String exitTime) {
    TheoryKnowledgeSwfRecordEntity rec = new TheoryKnowledgeSwfRecordEntity();
    rec.setUserId(userId);
    rec.setKnowledgeId(kid);
    rec.setKnowledgeSwfId(sid);
    rec.setJoinTime(joinTime);
    rec.setExitTime(exitTime);
    rec.setType(0);
    recordDao.save(rec);
  }

  // ---- 计数 DAO 子类：委托真实实现并累加计数器 --------------------------------

  static class CountingTestDao extends TheoryKnowledgeTestDao {
    int batchCalls;
    int singleSwfCalls;

    @Override
    public List<TheoryKnowledgeTestEntity> findAllByKnowledgeSwfIdInOrderByCreateTimeAsc(List<String> swfIds) {
      batchCalls++;
      return super.findAllByKnowledgeSwfIdInOrderByCreateTimeAsc(swfIds);
    }

    @Override
    public List<TheoryKnowledgeTestEntity> findAllByKnowledgeSwfIdOrderByCreateTimeAsc(String knowledgeSwfId) {
      singleSwfCalls++;
      return super.findAllByKnowledgeSwfIdOrderByCreateTimeAsc(knowledgeSwfId);
    }

    @Override
    public List<TheoryKnowledgeTestEntity> findAllByKnowledgeSwfIdAndVersions(String knowledgeId, Integer ver) {
      singleSwfCalls++;
      return super.findAllByKnowledgeSwfIdAndVersions(knowledgeId, ver);
    }
  }

  static class CountingContentDao extends TheoryKnowledgeTestContentDao {
    int batchCalls;
    int singleTestCalls;

    @Override
    public List<TheoryKnowledgeTestContentEntity> findAllByKnowledgeTestIdInOrderByCreateTimeAsc(List<String> testIds) {
      batchCalls++;
      return super.findAllByKnowledgeTestIdInOrderByCreateTimeAsc(testIds);
    }

    @Override
    public List<TheoryKnowledgeTestContentEntity> findAllByKnowledgeTestId(String knowledgeTestId) {
      singleTestCalls++;
      return super.findAllByKnowledgeTestId(knowledgeTestId);
    }
  }

  static class CountingTestUserDao extends TheoryKnowledgeTestUserDao {
    int batchCalls;
    int singleSwfCalls;
    int singleKnowledgeCalls;

    @Override
    public List<TheoryKnowledgeTestUserEntity> findAllByUserIdAndKnowledgeSwfIdIn(String userId, List<String> swfIds) {
      batchCalls++;
      return super.findAllByUserIdAndKnowledgeSwfIdIn(userId, swfIds);
    }

    @Override
    public TheoryKnowledgeTestUserEntity findFirstByUserIdAndKnowledgeSwfId(String userId, String knowledgeSwfId) {
      singleSwfCalls++;
      return super.findFirstByUserIdAndKnowledgeSwfId(userId, knowledgeSwfId);
    }

    @Override
    public List<TheoryKnowledgeTestUserEntity> findAllByUserIdAndKnowledgeId(String userId, String knowledgeId) {
      singleKnowledgeCalls++;
      return super.findAllByUserIdAndKnowledgeId(userId, knowledgeId);
    }
  }

  static class CountingRecordDao extends TheoryKnowledgeSwfRecordDao {
    int batchCalls;
    int singleSwfCalls;

    @Override
    public List<TheoryKnowledgeSwfRecordEntity> findAllByUserIdAndKnowledgeSwfIdIn(String userId, List<String> swfIds) {
      batchCalls++;
      return super.findAllByUserIdAndKnowledgeSwfIdIn(userId, swfIds);
    }

    @Override
    public List<TheoryKnowledgeSwfRecordEntity> findAllByUserIdAndKnowledgeSwfId(String userId, String swfId) {
      singleSwfCalls++;
      return super.findAllByUserIdAndKnowledgeSwfId(userId, swfId);
    }
  }

  static class CountingKnowledgeDao extends TheoryKnowledgeDao {
    int batchCalls;

    public List<TheoryKnowledgeEntity> list(String query, Object... params) {
      batchCalls++;
      return super.list(query, params);
    }
  }

  static class CountingSwfDao extends TheoryKnowledgeSwfDao {
    int batchCalls;
    int singleKnowledgeCalls;

    public List<TheoryKnowledgeSwfEntity> list(String query, Object... params) {
      batchCalls++;
      return super.list(query, params);
    }

    @Override
    public List<TheoryKnowledgeSwfEntity> findAllByKnowledgeIdOrderBySortAsc(String id) {
      singleKnowledgeCalls++;
      return super.findAllByKnowledgeIdOrderBySortAsc(id);
    }
  }
}
