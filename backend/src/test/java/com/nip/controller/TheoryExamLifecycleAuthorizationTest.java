package com.nip.controller;

import com.nip.dao.RoleDao;
import com.nip.dao.TheoryKnowledgeExamDao;
import com.nip.dao.TheoryKnowledgeExamTestPaperDao;
import com.nip.dao.TheoryKnowledgeExamUserDao;
import com.nip.dao.UserDao;
import com.nip.dao.UserRoleDao;
import com.nip.dto.TestPaperDto;
import com.nip.dto.TheoryKnowledgeExamDto;
import com.nip.entity.RoleEntity;
import com.nip.entity.TheoryKnowledgeExamEntity;
import com.nip.entity.TheoryKnowledgeExamTestPaperEntity;
import com.nip.entity.TheoryKnowledgeExamUserEntity;
import com.nip.entity.UserEntity;
import com.nip.entity.UserRoleEntity;
import com.nip.testsupport.Fixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
class TheoryExamLifecycleAuthorizationTest {
  @Inject UserDao userDao;
  @Inject RoleDao roleDao;
  @Inject UserRoleDao userRoleDao;
  @Inject TheoryKnowledgeExamDao examDao;
  @Inject TheoryKnowledgeExamUserDao examUserDao;
  @Inject TheoryKnowledgeExamTestPaperDao paperDao;

  @Test
  void ordinaryUserCannotRebuildExistingExamWhileAdminCanCreateScheduledExam() {
    UserEntity admin = user();
    admin(admin);
    UserEntity student = user();
    TheoryKnowledgeExamEntity exam = exam(admin, 2);
    TheoryKnowledgeExamUserEntity row = member(exam, student, 1, 2);
    TheoryKnowledgeExamTestPaperEntity paper = snapshot(exam);
    TheoryKnowledgeExamDto request = selfTestRequest(student);
    request.setId(exam.getId());

    given().contentType(ContentType.JSON)
        .header("token", student.getToken()).header("deviceId", student.getDeviceId())
        .body(request).post("/api/theoryKnowledgeExam/savetheoryKnowledgeExam")
        .then().statusCode(200).body("code", is(207));
    assertEquals(exam, examDao.findById(exam.getId()));
    assertEquals(List.of(row), examUserDao.findAllByExamId(exam.getId()));
    assertEquals(paper, paperDao.findAllByExamId(exam.getId()));

    request.setId(null);
    given().contentType(ContentType.JSON)
        .header("token", admin.getToken()).header("deviceId", admin.getDeviceId())
        .body(request).post("/api/theoryKnowledgeExam/savetheoryKnowledgeExam")
        .then().statusCode(200).body("code", is(200));
    TheoryKnowledgeExamEntity created = examDao.find("title", request.getTitle()).firstResult();
    assertEquals(admin.getId(), created.getCreateUserId());
    assertEquals(1, created.getState());
    assertEquals(1, examUserDao.findAllByExamIdAndUserId(created.getId(), student.getId()).getIsSelfTesting());
    assertEquals("replacement paper", paperDao.findAllByExamId(created.getId()).getName());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1})
  void memberCanEnterBothSelfTestAndScheduledExamWithoutResettingStartTime(int selfTesting) {
    UserEntity owner = user();
    UserEntity student = user();
    TheoryKnowledgeExamEntity exam = exam(selfTesting == 0 ? student : owner, 1);
    TheoryKnowledgeExamUserEntity row = member(exam, student, selfTesting, 1);

    lifecycle(student, exam.getId(), 2, 200);
    TheoryKnowledgeExamEntity started = examDao.findById(exam.getId());
    assertEquals(2, started.getState());
    String startTime = "2026-01-01 00:00:00";
    started.setStartTime(startTime);
    examDao.saveAndFlush(started);
    given().contentType(ContentType.JSON)
        .header("token", student.getToken()).header("deviceId", student.getDeviceId())
        .body(Map.of("examId", exam.getId(), "type", "2"))
        .post("/api/theoryKnowledgeExam/teacherStartTheoryKnowledgeExam")
        .then().statusCode(200).body("code", is(200))
        .body("data.startTime", is(startTime)).body("data.startTime", notNullValue());
    assertEquals(row, examUserDao.findById(row.getId()));
  }

  @Test
  void outsiderCannotUseBodyIdentityOrTokenToEnterAnExam() {
    UserEntity owner = user();
    UserEntity member = user();
    UserEntity outsider = user();
    TheoryKnowledgeExamEntity exam = exam(owner, 1);
    member(exam, member, 1, 1);

    given().contentType(ContentType.JSON)
        .header("token", outsider.getToken()).header("deviceId", outsider.getDeviceId())
        .body(Map.of("examId", exam.getId(), "type", "2", "userId", member.getId(),
            "token", member.getToken()))
        .post("/api/theoryKnowledgeExam/teacherStartTheoryKnowledgeExam")
        .then().statusCode(200).body("code", is(207));
    assertEquals(exam, examDao.findById(exam.getId()));
  }

  @Test
  void studentCannotFinishGradeOrDeleteWholeExamButAdminCan() {
    UserEntity admin = user();
    admin(admin);
    UserEntity student = user();
    TheoryKnowledgeExamEntity exam = exam(admin, 2);
    TheoryKnowledgeExamUserEntity row = member(exam, student, 1, 2);
    TheoryKnowledgeExamTestPaperEntity paper = snapshot(exam);

    lifecycle(student, exam.getId(), 3, 207);
    lifecycle(student, exam.getId(), 4, 207);
    delete(student, exam.getId(), 207);
    assertEquals(exam, examDao.findById(exam.getId()));
    assertEquals(row, examUserDao.findById(row.getId()));
    assertEquals(paper, paperDao.findById(paper.getId()));

    lifecycle(admin, exam.getId(), 3, 200);
    assertEquals(3, examDao.findById(exam.getId()).getState());
    TheoryKnowledgeExamUserEntity finished = examUserDao.findById(row.getId());
    assertEquals(3, finished.getState());
    assertEquals(examDao.findById(exam.getId()).getEndTime(), finished.getEndTime());
    lifecycle(admin, exam.getId(), 4, 200);
    assertEquals(4, examDao.findById(exam.getId()).getState());
    lifecycle(student, exam.getId(), 2, 208);
    assertEquals(4, examDao.findById(exam.getId()).getState());
    delete(admin, exam.getId(), 200);
    assertNull(examDao.findById(exam.getId()));
    assertNull(examUserDao.findById(row.getId()));
    assertNull(paperDao.findById(paper.getId()));
  }

  @Test
  void selfTestTakeoverCannotOverwriteExamAnswersOrSnapshot() {
    UserEntity owner = user();
    UserEntity attacker = user();
    TheoryKnowledgeExamEntity exam = exam(owner, 2);
    TheoryKnowledgeExamUserEntity row = member(exam, owner, 0, 2);
    TheoryKnowledgeExamTestPaperEntity paper = snapshot(exam);
    TheoryKnowledgeExamDto request = selfTestRequest(attacker);
    request.setId(exam.getId());
    request.setCreateUserId(attacker.getId());

    saveSelfTest(attacker, request, 207);

    assertEquals(exam, examDao.findById(exam.getId()));
    assertEquals(List.of(row), examUserDao.findAllByExamId(exam.getId()));
    assertEquals(paper, paperDao.findAllByExamId(exam.getId()));
  }

  @Test
  void selfTestCreationUsesServerIdentityAndRejectsUnknownUpdateId() {
    UserEntity actor = user();
    UserEntity other = user();
    TheoryKnowledgeExamDto request = selfTestRequest(other);
    request.setId("client-chosen-" + UUID.randomUUID());
    saveSelfTest(actor, request, 202);
    assertNull(examDao.findById(request.getId()));

    request.setId(null);
    String id = saveSelfTest(actor, request, 200);
    TheoryKnowledgeExamEntity created = examDao.findById(id);
    assertEquals(actor.getId(), created.getCreateUserId());
    assertNotEquals(request.getStartTime(), created.getStartTime());
    TheoryKnowledgeExamUserEntity row = examUserDao.findAllByExamIdAndUserId(id, actor.getId());
    assertEquals(0, row.getIsSelfTesting());
    assertEquals(2, row.getState());
    assertEquals(created.getStartTime(), row.getStartTime());
    assertNull(examUserDao.findAllByExamIdAndUserId(id, other.getId()));
    assertEquals("replacement paper", paperDao.findAllByExamId(id).getName());
  }

  @Test
  void completedSelfTestCannotBeRebuiltByOwnerOrAdmin() {
    UserEntity owner = user();
    UserEntity admin = user();
    admin(admin);
    TheoryKnowledgeExamEntity exam = exam(owner, 4);
    TheoryKnowledgeExamUserEntity answer = member(exam, owner, 0, 4);
    TheoryKnowledgeExamTestPaperEntity paper = snapshot(exam);
    TheoryKnowledgeExamDto request = selfTestRequest(owner);
    request.setId(exam.getId());

    saveSelfTest(owner, request, 208);
    saveSelfTest(admin, request, 208);
    assertEquals(4, examDao.findById(exam.getId()).getState());
    assertEquals("original answer", examUserDao.findById(answer.getId()).getContent());
    assertEquals("original paper", paperDao.findById(paper.getId()).getName());
  }

  @Test
  void ownerAndAdminCanUpdateSelfTestWithoutTransferringOwnership() {
    UserEntity owner = user();
    UserEntity admin = user();
    admin(admin);
    TheoryKnowledgeExamEntity exam = exam(owner, 2);
    member(exam, owner, 0, 2);
    snapshot(exam);
    TheoryKnowledgeExamDto request = selfTestRequest(admin);
    request.setId(exam.getId());

    saveSelfTest(owner, request, 200);
    request.setTitle("admin-updated-self-test");
    saveSelfTest(admin, request, 200);

    TheoryKnowledgeExamEntity updated = examDao.findById(exam.getId());
    assertEquals(request.getTitle(), updated.getTitle());
    assertEquals(owner.getId(), updated.getCreateUserId());
    assertEquals(exam.getCreateTime(), updated.getCreateTime());
    assertEquals(owner.getId(), examUserDao.findByExamId(exam.getId()).getUserId());
    assertEquals("replacement paper", paperDao.findAllByExamId(exam.getId()).getName());
  }

  private void lifecycle(UserEntity actor, String examId, int type, int code) {
    given().contentType(ContentType.JSON)
        .header("token", actor.getToken()).header("deviceId", actor.getDeviceId())
        .body(Map.of("examId", examId, "type", String.valueOf(type)))
        .post("/api/theoryKnowledgeExam/teacherStartTheoryKnowledgeExam")
        .then().statusCode(200).body("code", is(code));
  }

  private void delete(UserEntity actor, String examId, int code) {
    given().contentType(ContentType.JSON)
        .header("token", actor.getToken()).header("deviceId", actor.getDeviceId())
        .body(Map.of("examId", examId))
        .post("/api/theoryKnowledgeExam/deleteTheoryKnowledgeExam")
        .then().statusCode(200).body("code", is(code));
  }

  private String saveSelfTest(UserEntity actor, TheoryKnowledgeExamDto request, int code) {
    return given().contentType(ContentType.JSON)
        .header("token", actor.getToken()).header("deviceId", actor.getDeviceId())
        .body(request)
        .post("/api/theoryKnowledgeExam/saveTheoryKnowledgeExamSelfTesting")
        .then().statusCode(200).body("code", is(code)).extract().path("data.id");
  }

  private UserEntity user() {
    return Fixtures.user(userDao, "exam-lifecycle-" + UUID.randomUUID(), "device-" + UUID.randomUUID());
  }

  private void admin(UserEntity actor) {
    RoleEntity role = new RoleEntity();
    role.setTitle("exam-admin-" + UUID.randomUUID());
    role.setIsAdmin(0);
    role.setIsDefault(1);
    role = roleDao.saveAndFlush(role);
    UserRoleEntity link = new UserRoleEntity();
    link.setUserId(actor.getId());
    link.setRoleId(role.getId());
    userRoleDao.saveAndFlush(link);
  }

  private TheoryKnowledgeExamEntity exam(UserEntity owner, int state) {
    TheoryKnowledgeExamEntity exam = new TheoryKnowledgeExamEntity();
    exam.setTitle("lifecycle-" + UUID.randomUUID());
    exam.setTeacher(owner.getId());
    exam.setCreateUserId(owner.getId());
    exam.setDuration("60");
    exam.setState(state);
    return examDao.saveAndFlush(exam);
  }

  private TheoryKnowledgeExamUserEntity member(TheoryKnowledgeExamEntity exam, UserEntity actor,
      int selfTesting, int state) {
    TheoryKnowledgeExamUserEntity row = new TheoryKnowledgeExamUserEntity();
    row.setExamId(exam.getId());
    row.setUserId(actor.getId());
    row.setState(state);
    row.setScore(20);
    row.setContent("original answer");
    row.setIsSelfTesting(selfTesting);
    return examUserDao.saveAndFlush(row);
  }

  private TheoryKnowledgeExamTestPaperEntity snapshot(TheoryKnowledgeExamEntity exam) {
    TheoryKnowledgeExamTestPaperEntity paper = new TheoryKnowledgeExamTestPaperEntity();
    paper.setExamId(exam.getId());
    paper.setName("original paper");
    paper.setTotal(100);
    paper.setPassMark(60);
    paper.setSingleChoiceList("[{\"id\":\"original-question\",\"score\":20}]");
    paper.setMultipleChoiceList("[]");
    paper.setJudgeList("[]");
    paper.setCompletionList("[]");
    paper.setShortAnswer("[]");
    return paperDao.saveAndFlush(paper);
  }

  private TheoryKnowledgeExamDto selfTestRequest(UserEntity claimedOwner) {
    TestPaperDto paper = new TestPaperDto();
    paper.setName("replacement paper");
    paper.setTotal(100);
    paper.setPassMark(60);
    TheoryKnowledgeExamDto request = new TheoryKnowledgeExamDto();
    request.setTitle("new-self-test-" + UUID.randomUUID());
    request.setCreateUserId(claimedOwner.getId());
    request.setTeacher(claimedOwner.getId());
    request.setStuId(List.of(claimedOwner.getId()));
    request.setDuration("60");
    request.setStartTime("2000-01-01 00:00:00");
    request.setTestPaper(paper);
    return request;
  }
}
