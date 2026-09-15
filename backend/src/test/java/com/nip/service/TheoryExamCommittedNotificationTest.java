package com.nip.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nip.common.constants.CodeConstants;
import com.nip.dao.RoleDao;
import com.nip.dao.TheoryKnowledgeExamDao;
import com.nip.dao.TheoryKnowledgeExamUserDao;
import com.nip.dao.UserDao;
import com.nip.dao.UserRoleDao;
import com.nip.entity.RoleEntity;
import com.nip.entity.TheoryKnowledgeExamEntity;
import com.nip.entity.TheoryKnowledgeExamUserEntity;
import com.nip.entity.UserEntity;
import com.nip.entity.UserRoleEntity;
import com.nip.testsupport.Fixtures;
import com.nip.testsupport.WebSocketSessionProbe;
import com.nip.ws.WebSocketService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TheoryExamCommittedNotificationTest {
  @Inject TheoryKnowledgeExamService service;
  @Inject TheoryKnowledgeExamDao exams;
  @Inject TheoryKnowledgeExamUserDao candidates;
  @Inject UserDao users;
  @Inject RoleDao roles;
  @Inject UserRoleDao userRoles;
  @Inject WebSocketService webSocket;

  private UserEntity teacher;
  private UserEntity student;
  private String roleId;
  private String examId;
  private String candidateId;
  private WebSocketSessionProbe probe;

  @BeforeEach
  void createExamAndConnectCandidate() {
    teacher = Fixtures.user(users, "theory-notify-teacher-" + UUID.randomUUID(), "device-" + UUID.randomUUID());
    student = Fixtures.user(users, "theory-notify-student-" + UUID.randomUUID(), "device-" + UUID.randomUUID());
    QuarkusTransaction.requiringNew().run(() -> {
      RoleEntity role = new RoleEntity();
      role.setTitle("theory-notify-admin-" + UUID.randomUUID());
      role.setIsAdmin(0);
      role.setIsDefault(1);
      roleId = roles.save(role).getId();
      UserRoleEntity link = new UserRoleEntity();
      link.setUserId(teacher.getId());
      link.setRoleId(roleId);
      userRoles.save(link);

      TheoryKnowledgeExamEntity exam = new TheoryKnowledgeExamEntity();
      exam.setTitle("theory-notify-" + UUID.randomUUID());
      exam.setTeacher(teacher.getId());
      exam.setCreateUserId(teacher.getId());
      exam.setState(2);
      exam.setDuration("60");
      examId = exams.save(exam).getId();
      TheoryKnowledgeExamUserEntity candidate = new TheoryKnowledgeExamUserEntity();
      candidate.setExamId(examId);
      candidate.setUserId(student.getId());
      candidate.setIsSelfTesting(1);
      candidate.setState(2);
      candidate.setContent("last confirmed answer");
      candidate.setScore(0);
      candidateId = candidates.save(candidate).getId();
    });
    probe = WebSocketSessionProbe.open(student.getId(), student.getToken(), student.getDeviceId());
    webSocket.onOpen(probe.session());
  }

  @AfterEach
  void removeOwnedExamAndConnection() {
    if (probe != null) webSocket.onClose(probe.session());
    QuarkusTransaction.requiringNew().run(() -> {
      if (examId != null) {
        candidates.delete("examId", examId);
        exams.deleteById(examId);
      }
      if (teacher != null) {
        userRoles.delete("userId", teacher.getId());
        users.deleteById(teacher.getId());
      }
      if (student != null) users.deleteById(student.getId());
      if (roleId != null) roles.deleteById(roleId);
    });
  }

  @Test
  void rolledBackHardEndSendsNoFrameAndCommittedEndSendsOnlyAfterCommit() {
    assertThrows(IllegalStateException.class, () -> QuarkusTransaction.requiringNew().run(() -> {
      service.teacherStartExam(teacher.getToken(), examId, 3);
      throw new IllegalStateException("force hard-end transaction rollback");
    }));
    assertTrue(probe.outbound().isEmpty(), "rolled-back hard end must not freeze the candidate page");
    assertEquals(2, exams.findById(examId).getState());
    assertEquals(2, candidates.findById(candidateId).getState());
    assertNull(candidates.findById(candidateId).getEndTime());
    assertEquals("last confirmed answer", candidates.findById(candidateId).getContent());

    QuarkusTransaction.requiringNew().run(() -> {
      service.teacherStartExam(teacher.getToken(), examId, 3);
      assertTrue(probe.outbound().isEmpty(), "hard-end frame must wait for the outer transaction to commit");
    });
    assertEquals(1, probe.outbound().size());
    JsonObject event = frame(0);
    assertEquals(CodeConstants.TEACHERCHANGEEXAMSTATE.getCode(), event.get("code").getAsInt());
    JsonObject exam = event.getAsJsonObject("map").getAsJsonObject("exam");
    assertEquals(examId, exam.get("id").getAsString());
    assertEquals(3, exam.get("state").getAsInt());
    assertEquals(exams.findById(examId).getEndTime(), exam.get("endTime").getAsString());
    assertEquals(3, candidates.findById(candidateId).getState());
  }

  @Test
  void queuedStartAndEndFramesKeepTheirOwnStateSnapshots() {
    QuarkusTransaction.requiringNew().run(() -> {
      exams.findById(examId).setState(1);
      candidates.findById(candidateId).setState(1);
    });
    QuarkusTransaction.requiringNew().run(() -> {
      service.teacherStartExam(student.getToken(), examId, 2);
      exams.flush();
      service.teacherStartExam(teacher.getToken(), examId, 3);
      assertTrue(probe.outbound().isEmpty(), "neither transition can escape before commit");
    });
    assertEquals(2, probe.outbound().size());
    assertEquals(List.of(2, 3), probe.outbound().stream()
        .map(JsonParser::parseString)
        .map(json -> json.getAsJsonObject().getAsJsonObject("map").getAsJsonObject("exam").get("state").getAsInt())
        .toList(), "committed transition frames must preserve occurrence order and their own snapshots");
    assertEquals(3, exams.findById(examId).getState());
  }

  @Test
  void requiresNewCommitDoesNotPublishSuspendedTransactionsRolledBackEnd() {
    String nestedExamId = QuarkusTransaction.requiringNew().call(() -> {
      TheoryKnowledgeExamEntity exam = new TheoryKnowledgeExamEntity();
      exam.setTitle("theory-notify-nested-" + UUID.randomUUID());
      exam.setTeacher(teacher.getId());
      exam.setCreateUserId(teacher.getId());
      exam.setState(1);
      exam.setDuration("60");
      exams.save(exam);
      TheoryKnowledgeExamUserEntity candidate = new TheoryKnowledgeExamUserEntity();
      candidate.setExamId(exam.getId());
      candidate.setUserId(student.getId());
      candidate.setIsSelfTesting(1);
      candidate.setState(1);
      candidate.setScore(0);
      candidates.save(candidate);
      return exam.getId();
    });
    try {
      assertThrows(IllegalStateException.class, () -> QuarkusTransaction.requiringNew().run(() -> {
        service.teacherStartExam(teacher.getToken(), examId, 3);
        assertTrue(probe.outbound().isEmpty());
        // Starting the independent exam avoids a second candidate-table locking scan
        // while the suspended outer transaction still owns its end-of-exam row locks.
        QuarkusTransaction.requiringNew().run(() -> service.teacherStartExam(teacher.getToken(), nestedExamId, 2));
        assertEquals(1, probe.outbound().size(), "only the inner committed transaction may notify");
        assertEquals(nestedExamId, frame(0).getAsJsonObject("map").getAsJsonObject("exam").get("id").getAsString());
        assertEquals(2, frame(0).getAsJsonObject("map").getAsJsonObject("exam").get("state").getAsInt());
        throw new IllegalStateException("rollback suspended outer exam end");
      }));
      assertEquals(1, probe.outbound().size(), "outer rollback must discard only its own notification queue");
      assertEquals(2, exams.findById(examId).getState());
      assertEquals(2, exams.findById(nestedExamId).getState());
    } finally {
      QuarkusTransaction.requiringNew().run(() -> {
        candidates.delete("examId", nestedExamId);
        exams.deleteById(nestedExamId);
      });
    }
  }

  private JsonObject frame(int index) {
    return JsonParser.parseString(probe.outbound().get(index)).getAsJsonObject();
  }
}
