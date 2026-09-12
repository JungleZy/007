package com.nip.controller;

import com.nip.dao.RoleDao;
import com.nip.dao.TheoryKnowledgeExamDao;
import com.nip.dao.TheoryKnowledgeExamTestPaperDao;
import com.nip.dao.TheoryKnowledgeExamUserDao;
import com.nip.dao.UserDao;
import com.nip.dao.UserRoleDao;
import com.nip.dao.general.ticker.GeneralTickerPatTrainUserDao;
import com.nip.entity.RoleEntity;
import com.nip.entity.TheoryKnowledgeExamEntity;
import com.nip.entity.TheoryKnowledgeExamTestPaperEntity;
import com.nip.entity.TheoryKnowledgeExamUserEntity;
import com.nip.entity.UserEntity;
import com.nip.entity.UserRoleEntity;
import com.nip.entity.simulation.ticker.GeneralTickerPatTrainUserEntity;
import com.nip.testsupport.Fixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 理论考试与个人统计端点的身份口径（SEC-09/SEC-10）对外契约。
 *
 * <p>覆盖四件消费者可见的事：
 * <ol>
 *   <li>「自操作」端点的考生身份只来自 token —— 请求体里再带 {@code userId} 也改不动别人的答卷；</li>
 *   <li>自测结算的分数由服务端按试卷快照重算 —— 客户端送任意 {@code score} 都不进库；</li>
 *   <li>{@code findExamUser} 仍允许教员/管理员读他人答卷（阅卷不得回归），无关学员读则 207；</li>
 *   <li>个人统计端点不再匿名可达，且统计对象与请求体无关。</li>
 * </ol>
 */
@QuarkusTest
class TheoryExamIdentityAuthorizationTest {
  @Inject UserDao userDao;
  @Inject RoleDao roleDao;
  @Inject UserRoleDao userRoleDao;
  @Inject TheoryKnowledgeExamDao examDao;
  @Inject TheoryKnowledgeExamUserDao examUserDao;
  @Inject TheoryKnowledgeExamTestPaperDao examTestPaperDao;
  @Inject GeneralTickerPatTrainUserDao tickerUserDao;

  @Test
  void bodyUserIdCannotRedirectStudentWritesToAnotherSheet() {
    UserEntity teacher = user("exam-teacher");
    UserEntity actor = user("exam-actor");
    UserEntity victim = user("exam-victim");
    TheoryKnowledgeExamEntity exam = exam(teacher, 2);
    TheoryKnowledgeExamUserEntity actorRow = examUserRow(exam, actor, "actor-原始答卷", 2);
    TheoryKnowledgeExamUserEntity victimRow = examUserRow(exam, victim, "victim-原始答卷", 2);

    // 交卷：请求体照旧带上受害者的 userId，服务端必须只认 token 所属考生
    given().contentType(ContentType.JSON)
        .header("token", actor.getToken()).header("deviceId", actor.getDeviceId())
        .body(Map.of("examId", exam.getId(), "userId", victim.getId(), "type", "3",
            "content", "actor-交卷答卷"))
        .post("/api/theoryKnowledgeExam/studentChangeExamState")
        .then().statusCode(200).body("code", is(200));

    // 实时答案上传：同样不得越过 token 写到别人行上
    given().contentType(ContentType.JSON)
        .header("token", actor.getToken()).header("deviceId", actor.getDeviceId())
        .body(Map.of("examId", exam.getId(), "userId", victim.getId(), "content", "actor-实时答案"))
        .post("/api/theoryKnowledgeExam/studentSaveExamRealtimeContont")
        .then().statusCode(200).body("code", is(200));

    TheoryKnowledgeExamUserEntity victimAfter = examUserDao.findById(victimRow.getId());
    assertEquals("victim-原始答卷", victimAfter.getContent(), "他人答卷内容不得被覆盖");
    assertEquals(2, victimAfter.getState(), "他人考试状态不得被提前置为已交卷");

    TheoryKnowledgeExamUserEntity actorAfter = examUserDao.findById(actorRow.getId());
    assertEquals("actor-实时答案", actorAfter.getContent(), "写入必须落在 token 所属考生行上");
    assertEquals(3, actorAfter.getState());
  }

  @Test
  void selfTestingScoreIsRecomputedFromSnapshotRegardlessOfSubmittedScore() {
    UserEntity student = user("self-correct");
    TheoryKnowledgeExamEntity correctExam = selfTestExam(student);
    TheoryKnowledgeExamUserEntity correctRow = selfTestRow(correctExam, student);

    // 单选答对 10 分 + 多选答对 20 分；请求体自报 9999 分必须被忽略
    given().contentType(ContentType.JSON)
        .header("token", student.getToken()).header("deviceId", student.getDeviceId())
        .body(Map.of("examId", correctExam.getId(), "score", 9999, "content",
            "{\"singleChoice\":[{\"id\":\"q1\",\"answer\":\"0\"}],"
                + "\"multipleChoice\":[{\"id\":\"q2\",\"answer\":[\"0\",\"2\"]}],"
                + "\"shortAnswer\":[{\"id\":\"q3\",\"answer\":\"胡说\",\"teacherScore\":30}]}"))
        .post("/api/theoryKnowledgeExam/finishSelfTesting")
        .then().statusCode(200).body("code", is(200));
    assertEquals(30, examUserDao.findById(correctRow.getId()).getScore(),
        "分数必须是快照重算值（10+20），不是请求体的 9999，也不含客户端自报的简答 teacherScore");

    UserEntity liar = user("self-wrong");
    TheoryKnowledgeExamEntity wrongExam = selfTestExam(liar);
    TheoryKnowledgeExamUserEntity wrongRow = selfTestRow(wrongExam, liar);
    given().contentType(ContentType.JSON)
        .header("token", liar.getToken()).header("deviceId", liar.getDeviceId())
        .body(Map.of("examId", wrongExam.getId(), "score", 9999, "content",
            "{\"singleChoice\":[{\"id\":\"q1\",\"answer\":\"1\"}],"
                + "\"multipleChoice\":[{\"id\":\"q2\",\"answer\":[\"0\"]}]}"))
        .post("/api/theoryKnowledgeExam/finishSelfTesting")
        .then().statusCode(200).body("code", is(200));
    assertEquals(0, examUserDao.findById(wrongRow.getId()).getScore(), "全错必须是 0 分");
  }

  @Test
  void finishingAnotherUsersSelfTestIsForbidden() {
    UserEntity owner = user("self-owner");
    UserEntity intruder = user("self-intruder");
    TheoryKnowledgeExamEntity exam = selfTestExam(owner);
    TheoryKnowledgeExamUserEntity row = selfTestRow(exam, owner);

    given().contentType(ContentType.JSON)
        .header("token", intruder.getToken()).header("deviceId", intruder.getDeviceId())
        .body(Map.of("examId", exam.getId(), "content",
            "{\"singleChoice\":[{\"id\":\"q1\",\"answer\":\"0\"}]}"))
        .post("/api/theoryKnowledgeExam/finishSelfTesting")
        .then().statusCode(200).body("code", is(207));

    TheoryKnowledgeExamUserEntity after = examUserDao.findById(row.getId());
    assertEquals(0, after.getScore(), "授权拒绝不得写分");
    assertEquals(2, after.getState(), "授权拒绝不得结算他人自测");
  }

  @Test
  void findExamUserStaysOpenToInvigilatorAndAdminButNotToStrangers() {
    UserEntity teacher = user("read-teacher");
    UserEntity student = user("read-student");
    UserEntity stranger = user("read-stranger");
    UserEntity admin = user("read-admin");
    attachRole(admin, 0);
    TheoryKnowledgeExamEntity exam = exam(teacher, 3);
    examUserRow(exam, student, "student-答卷", 3);
    Map<String, String> body = Map.of("examId", exam.getId(), "userId", student.getId());

    // 监考人：阅卷/实时监看读的正是他人 user_id，这条不能回归
    given().contentType(ContentType.JSON)
        .header("token", teacher.getToken()).header("deviceId", teacher.getDeviceId())
        .body(body).post("/api/theoryKnowledgeExamUser/findExamUser")
        .then().statusCode(200).body("code", is(200)).body("data.content", is("student-答卷"));
    // 系统管理员
    given().contentType(ContentType.JSON)
        .header("token", admin.getToken()).header("deviceId", admin.getDeviceId())
        .body(body).post("/api/theoryKnowledgeExamUser/findExamUser")
        .then().statusCode(200).body("code", is(200)).body("data.content", is("student-答卷"));
    // 本人
    given().contentType(ContentType.JSON)
        .header("token", student.getToken()).header("deviceId", student.getDeviceId())
        .body(body).post("/api/theoryKnowledgeExamUser/findExamUser")
        .then().statusCode(200).body("code", is(200)).body("data.content", is("student-答卷"));
    // 无关学员
    given().contentType(ContentType.JSON)
        .header("token", stranger.getToken()).header("deviceId", stranger.getDeviceId())
        .body(body).post("/api/theoryKnowledgeExamUser/findExamUser")
        .then().statusCode(200).body("code", is(207));
  }

  @Test
  void trainStatisticsEndpointsRejectAnonymousAndIgnoreBodyUserId() {
    UserEntity owner = user("stat-owner");
    UserEntity peeper = user("stat-peeper");
    seedFinishedHandKeyTrain(owner);

    for (String path : new String[] { "getUserTrainDurationStat", "getRecentHandKeyTrains",
        "getRecentElectronicKeyTrains" }) {
      given().contentType(ContentType.JSON)
          .body(Map.of("userId", owner.getId()))
          .post("/api/user/" + path)
          .then().statusCode(200).body("code", is(203));
    }

    // 已登录用户只能拿到自己的统计：请求体指向他人也只返回调用者本人的数据
    given().contentType(ContentType.JSON)
        .header("token", peeper.getToken()).header("deviceId", peeper.getDeviceId())
        .body(Map.of("userId", owner.getId()))
        .post("/api/user/getRecentHandKeyTrains")
        .then().statusCode(200).body("code", is(200)).body("data", hasSize(0));
    given().contentType(ContentType.JSON)
        .header("token", peeper.getToken()).header("deviceId", peeper.getDeviceId())
        .body(Map.of("userId", owner.getId()))
        .post("/api/user/getUserTrainDurationStat")
        .then().statusCode(200).body("code", is(200)).body("data.handKeyDuration", is(0));

    given().contentType(ContentType.JSON)
        .header("token", owner.getToken()).header("deviceId", owner.getDeviceId())
        .body(Map.of("userId", peeper.getId()))
        .post("/api/user/getRecentHandKeyTrains")
        .then().statusCode(200).body("code", is(200)).body("data", hasSize(1));
    given().contentType(ContentType.JSON)
        .header("token", owner.getToken()).header("deviceId", owner.getDeviceId())
        .body(Map.of("userId", peeper.getId()))
        .post("/api/user/getUserTrainDurationStat")
        .then().statusCode(200).body("code", is(200)).body("data.handKeyDuration", is(300));
  }

  private UserEntity user(String prefix) {
    return Fixtures.user(userDao, prefix + "-token-" + UUID.randomUUID(),
        prefix + "-device-" + UUID.randomUUID());
  }

  private void attachRole(UserEntity user, int isAdmin) {
    RoleEntity role = new RoleEntity();
    role.setTitle("role-" + UUID.randomUUID());
    role.setIsAdmin(isAdmin);
    role.setIsDefault(1);
    role = roleDao.saveAndFlush(role);
    UserRoleEntity link = new UserRoleEntity();
    link.setUserId(user.getId());
    link.setRoleId(role.getId());
    userRoleDao.saveAndFlush(link);
  }

  private TheoryKnowledgeExamEntity exam(UserEntity teacher, int state) {
    TheoryKnowledgeExamEntity exam = new TheoryKnowledgeExamEntity();
    exam.setTitle("exam-" + UUID.randomUUID());
    exam.setTeacher(teacher.getId());
    exam.setCreateUserId(teacher.getId());
    exam.setDuration("60");
    exam.setState(state);
    return examDao.saveAndFlush(exam);
  }

  private TheoryKnowledgeExamUserEntity examUserRow(TheoryKnowledgeExamEntity exam, UserEntity student,
      String content, int state) {
    TheoryKnowledgeExamUserEntity row = new TheoryKnowledgeExamUserEntity();
    row.setExamId(exam.getId());
    row.setUserId(student.getId());
    row.setContent(content);
    row.setState(state);
    row.setScore(0);
    row.setIsSelfTesting(1);
    return examUserDao.saveAndFlush(row);
  }

  /** 自测场次：一份三题快照（单选 10 分、多选 20 分、简答 30 分）。 */
  private TheoryKnowledgeExamEntity selfTestExam(UserEntity student) {
    TheoryKnowledgeExamEntity exam = exam(student, 2);
    TheoryKnowledgeExamTestPaperEntity paper = new TheoryKnowledgeExamTestPaperEntity();
    paper.setExamId(exam.getId());
    paper.setName("self-paper-" + UUID.randomUUID());
    paper.setTotal(60);
    paper.setPassMark(36);
    paper.setSingleChoiceList("[{\"id\":\"q1\",\"type\":1,\"score\":10,\"answer\":\"\\\"0\\\"\"}]");
    paper.setMultipleChoiceList("[{\"id\":\"q2\",\"type\":2,\"score\":20,\"answer\":\"[\\\"0\\\",\\\"2\\\"]\"}]");
    paper.setJudgeList("[]");
    paper.setCompletionList("[]");
    paper.setShortAnswer("[{\"id\":\"q3\",\"type\":5,\"score\":30,\"answer\":\"标准论述\"}]");
    examTestPaperDao.saveAndFlush(paper);
    return exam;
  }

  private TheoryKnowledgeExamUserEntity selfTestRow(TheoryKnowledgeExamEntity exam, UserEntity student) {
    TheoryKnowledgeExamUserEntity row = new TheoryKnowledgeExamUserEntity();
    row.setExamId(exam.getId());
    row.setUserId(student.getId());
    row.setState(2);
    row.setScore(0);
    row.setIsSelfTesting(0);
    return examUserDao.saveAndFlush(row);
  }

  private void seedFinishedHandKeyTrain(UserEntity owner) {
    LocalDateTime finish = LocalDateTime.now().withNano(0);
    GeneralTickerPatTrainUserEntity record = new GeneralTickerPatTrainUserEntity()
        .setUserId(owner.getId())
        .setRole(0)
        .setIsFinish(1)
        .setScore(BigDecimal.valueOf(88))
        .setSpeed("120")
        .setCreateTime(finish.minusSeconds(300))
        .setFinishTime(finish);
    tickerUserDao.saveAndFlush(record);
  }
}
