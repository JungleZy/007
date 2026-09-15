package com.nip.controller;

import com.nip.common.exception.TerminalStateException;
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
import com.nip.service.TheoryKnowledgeExamService;
import com.nip.testsupport.Fixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class TheoryExamStateTransitionsTest {
  @Inject UserDao users;
  @Inject RoleDao roles;
  @Inject UserRoleDao userRoles;
  @Inject TheoryKnowledgeExamDao exams;
  @Inject TheoryKnowledgeExamUserDao candidates;
  @Inject TheoryKnowledgeExamTestPaperDao papers;
  @Inject TheoryKnowledgeExamService service;
  private final List<String> examIds = new ArrayList<>();
  private final List<String> userIds = new ArrayList<>();
  private final List<String> roleIds = new ArrayList<>();

  @AfterEach
  void removeOwnedFixtures() {
    QuarkusTransaction.requiringNew().run(() -> {
      for (String examId : examIds) {
        papers.delete("examId", examId);
        candidates.delete("examId", examId);
        exams.deleteById(examId);
      }
      for (String userId : userIds) {
        userRoles.delete("userId", userId);
        users.deleteById(userId);
      }
      roleIds.forEach(roles::deleteById);
    });
  }

  @Test
  void entryLeaveReentryAndSubmissionPreserveClockAndConfirmedAnswers() {
    UserEntity student = user();
    TheoryKnowledgeExamEntity exam = exam(student, 1);
    TheoryKnowledgeExamUserEntity candidate = candidate(exam, student, 1, 1);
    change(student, exam, 2, "premature", 208);
    start(student, exam, 2, 200);
    change(student, exam, 2, "ignored", 200);
    QuarkusTransaction.requiringNew().run(() -> {
      exams.findById(exam.getId()).setStartTime("2026-01-01 00:00:00");
      candidates.findById(candidate.getId()).setStartTime("2026-01-01 00:01:00");
    });
    save(student, exam, "confirmed-live", 200);
    change(student, exam, 1, "confirmed-leave", 200);
    assertEquals(1, candidates.findById(candidate.getId()).getState());
    start(student, exam, 2, 200);
    change(student, exam, 2, "must-not-overwrite", 200);
    assertEquals("2026-01-01 00:00:00", exams.findById(exam.getId()).getStartTime());
    TheoryKnowledgeExamUserEntity reentered = candidates.findById(candidate.getId());
    assertEquals("2026-01-01 00:01:00", reentered.getStartTime());
    assertEquals("confirmed-leave", reentered.getContent());
    change(student, exam, 99, "invalid", 202);
    change(student, exam, 3, "submitted", 200);
    TheoryKnowledgeExamUserEntity submitted = candidates.findById(candidate.getId());
    for (int type : List.of(1, 2, 3)) change(student, exam, type, "late", 208);
    save(student, exam, "late", 208);
    assertEquals(submitted, candidates.findById(candidate.getId()));
  }

  @Test
  void hardEndKeepsLastConfirmedAnswerAndCannotUndoGrading() {
    UserEntity teacher = admin();
    UserEntity student = user();
    TheoryKnowledgeExamEntity exam = exam(teacher, 2);
    TheoryKnowledgeExamUserEntity candidate = candidate(exam, student, 1, 2);
    save(student, exam, "server-last", 200);
    start(teacher, exam, 3, 200);
    TheoryKnowledgeExamUserEntity ended = candidates.findById(candidate.getId());
    assertEquals(3, ended.getState());
    assertEquals("server-last", ended.getContent());
    for (int type : List.of(1, 2, 3)) change(student, exam, type, "unconfirmed-local", 208);
    save(student, exam, "unconfirmed-local", 208);
    start(student, exam, 2, 208);
    start(teacher, exam, 3, 200);
    assertEquals(ended, candidates.findById(candidate.getId()));
    grade(teacher, student, exam);
    TheoryKnowledgeExamUserEntity graded = candidates.findById(candidate.getId());
    start(teacher, exam, 3, 200);
    assertEquals(4, candidates.findById(candidate.getId()).getState());
    start(teacher, exam, 4, 200);
    start(teacher, exam, 3, 208);
    save(student, exam, "late-after-grade", 208);
    assertEquals(graded, candidates.findById(candidate.getId()));
    given().contentType(ContentType.JSON).headers("token", student.getToken(), "deviceId", student.getDeviceId())
        .body(Map.of("examId", exam.getId(), "userId", student.getId()))
        .post("/api/theoryKnowledgeExamUser/findExamUser")
        .then().statusCode(200).body("code", is(200)).body("data.score", is(73));
  }

  @Test
  void ordinaryExamCannotUseSelfFinishOrBeConvertedByItsOwner() {
    UserEntity student = user();
    TheoryKnowledgeExamEntity exam = exam(student, 2);
    TheoryKnowledgeExamUserEntity candidate = candidate(exam, student, 1, 2);
    finish(student, exam, "{}", 207);
    TheoryKnowledgeExamDto request = new TheoryKnowledgeExamDto();
    request.setId(exam.getId());
    request.setTitle("takeover");
    request.setDuration("60");
    request.setTestPaper(new TestPaperDto());
    given().contentType(ContentType.JSON).headers("token", student.getToken(), "deviceId", student.getDeviceId())
        .body(request).post("/api/theoryKnowledgeExam/saveTheoryKnowledgeExamSelfTesting")
        .then().statusCode(200).body("code", is(207));
    assertEquals(exam, exams.findById(exam.getId()));
    assertEquals(candidate, candidates.findById(candidate.getId()));
    assertEquals("original-paper", papers.findAllByExamId(exam.getId()).getName());
  }

  @Test
  void completedSelfTestAcceptsOnlySameAnswerRetryWithoutChangingResult() {
    UserEntity student = user();
    TheoryKnowledgeExamEntity exam = exam(student, 2);
    TheoryKnowledgeExamUserEntity candidate = candidate(exam, student, 0, 2);
    String answer = "{\"singleChoice\":[{\"id\":\"q1\",\"answer\":\"A\",\"teacherScore\":999}]}";
    finish(student, exam, answer, 200);
    TheoryKnowledgeExamUserEntity completed = candidates.findById(candidate.getId());
    TheoryKnowledgeExamEntity completedExam = exams.findById(exam.getId());
    assertEquals(10, completed.getScore());
    assertNotNull(completed.getEndTime());
    finish(student, exam, answer, 200);
    finish(student, exam, "{\"singleChoice\":[{\"id\":\"q1\",\"answer\":\"B\"}]}", 208);
    save(student, exam, "{}", 208);
    change(student, exam, 2, "{}", 208);
    assertEquals(completed, candidates.findById(candidate.getId()));
    assertEquals(completedExam, exams.findById(exam.getId()));
  }

  @Test
  void lateSaveRereadsGradedStateEvenWithOlderManagedEntities() throws Exception {
    UserEntity teacher = admin();
    UserEntity student = user();
    TheoryKnowledgeExamEntity exam = exam(teacher, 2);
    TheoryKnowledgeExamUserEntity candidate = candidate(exam, student, 1, 2);
    CountDownLatch loaded = new CountDownLatch(1);
    CountDownLatch graded = new CountDownLatch(1);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var lateSave = executor.submit(() -> assertThrows(TerminalStateException.class,
          () -> QuarkusTransaction.requiringNew().run(() -> {
            assertEquals(2, exams.findById(exam.getId()).getState());
            assertEquals(2, candidates.findById(candidate.getId()).getState());
            loaded.countDown();
            await(graded);
            service.saveUserRealTimeParam(student.getToken(), exam.getId(), "stale-answer");
          })));
      try {
        await(loaded);
        grade(teacher, student, exam);
      } finally {
        graded.countDown();
      }
      lateSave.get(20, TimeUnit.SECONDS);
    }
    TheoryKnowledgeExamUserEntity result = candidates.findById(candidate.getId());
    assertEquals(4, result.getState());
    assertEquals(73, result.getScore());
    assertEquals("{\"singleChoice\":[]}", result.getContent());
  }

  private static void await(CountDownLatch latch) {
    try {
      assertTrue(latch.await(15, TimeUnit.SECONDS), "lifecycle transaction barrier timed out");
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new AssertionError(interrupted);
    }
  }

  private UserEntity user() {
    UserEntity user = Fixtures.user(users, "theory-state-" + UUID.randomUUID(), "device-" + UUID.randomUUID());
    userIds.add(user.getId());
    return user;
  }

  private UserEntity admin() {
    UserEntity user = user();
    RoleEntity role = new RoleEntity();
    role.setTitle("theory-state-admin-" + UUID.randomUUID());
    role.setIsAdmin(0);
    role.setIsDefault(1);
    role = roles.saveAndFlush(role);
    roleIds.add(role.getId());
    UserRoleEntity link = new UserRoleEntity();
    link.setUserId(user.getId());
    link.setRoleId(role.getId());
    userRoles.saveAndFlush(link);
    return user;
  }

  private TheoryKnowledgeExamEntity exam(UserEntity owner, int state) {
    TheoryKnowledgeExamEntity exam = new TheoryKnowledgeExamEntity();
    exam.setTitle("theory-state-" + UUID.randomUUID());
    exam.setTeacher(owner.getId());
    exam.setCreateUserId(owner.getId());
    exam.setState(state);
    exam.setDuration("60");
    exam = exams.saveAndFlush(exam);
    examIds.add(exam.getId());
    TheoryKnowledgeExamTestPaperEntity paper = new TheoryKnowledgeExamTestPaperEntity();
    paper.setExamId(exam.getId());
    paper.setName("original-paper");
    paper.setSingleChoiceList("[{\"id\":\"q1\",\"type\":1,\"score\":10,\"answer\":\"\\\"A\\\"\"}]");
    paper.setMultipleChoiceList("[]");
    paper.setJudgeList("[]");
    paper.setCompletionList("[]");
    paper.setShortAnswer("[]");
    papers.saveAndFlush(paper);
    return exam;
  }

  private TheoryKnowledgeExamUserEntity candidate(TheoryKnowledgeExamEntity exam, UserEntity student, int self, int state) {
    TheoryKnowledgeExamUserEntity candidate = new TheoryKnowledgeExamUserEntity();
    candidate.setExamId(exam.getId());
    candidate.setUserId(student.getId());
    candidate.setState(state);
    candidate.setIsSelfTesting(self);
    candidate.setScore(0);
    return candidates.saveAndFlush(candidate);
  }

  private void change(UserEntity user, TheoryKnowledgeExamEntity exam, int type, String content, int code) {
    post(user, "studentChangeExamState", Map.of("examId", exam.getId(), "type", type, "content", content), code);
  }

  private void start(UserEntity user, TheoryKnowledgeExamEntity exam, int type, int code) {
    post(user, "teacherStartTheoryKnowledgeExam", Map.of("examId", exam.getId(), "type", type), code);
  }

  private void save(UserEntity user, TheoryKnowledgeExamEntity exam, String content, int code) {
    post(user, "studentSaveExamRealtimeContont", Map.of("examId", exam.getId(), "content", content), code);
  }

  private void finish(UserEntity user, TheoryKnowledgeExamEntity exam, String content, int code) {
    post(user, "finishSelfTesting", Map.of("examId", exam.getId(), "content", content), code);
  }

  private void post(UserEntity user, String action, Object body, int code) {
    given().contentType(ContentType.JSON).headers("token", user.getToken(), "deviceId", user.getDeviceId())
        .body(body).post("/api/theoryKnowledgeExam/" + action).then().statusCode(200).body("code", is(code));
  }

  private void grade(UserEntity teacher, UserEntity student, TheoryKnowledgeExamEntity exam) {
    given().contentType(ContentType.JSON).headers("token", teacher.getToken(), "deviceId", teacher.getDeviceId())
        .body(Map.of("examId", exam.getId(), "list", List.of(Map.of("user_id", student.getId(), "score", 73,
            "content", Map.of("singleChoice", List.of())))))
        .post("/api/theoryKnowledgeExamUser/teacherUploadScore").then().statusCode(200).body("code", is(200));
  }
}
