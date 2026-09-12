package com.nip.controller;

import com.nip.dao.RoleDao;
import com.nip.dao.TheoryKnowledgeExamUserDao;
import com.nip.dao.UserDao;
import com.nip.dao.UserRoleDao;
import com.nip.entity.RoleEntity;
import com.nip.entity.TheoryKnowledgeExamUserEntity;
import com.nip.entity.UserEntity;
import com.nip.entity.UserRoleEntity;
import com.nip.testsupport.Fixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 理论考试上分端点 {@code POST /api/theoryKnowledgeExamUser/teacherUploadScore} 的对外契约。
 *
 * <p>库中只有「系统管理员」(is_admin=0) 与「普通人员」(is_admin=1) 两种角色，没有教员角色，
 * 因此上分权限只能收敛到系统管理员：普通人员必须得 {@code code:207} 且分数不动。
 *
 * <p>另一条消费者可见契约是批次原子性：只要列表里混入不属于该 examId 的考生，整批都不得落库——
 * 否则一次误操作会把部分分数写进库，且前端拿到失败码后无从得知哪些已经写入。
 */
@QuarkusTest
class TheoryKnowledgeExamUploadScoreAuthorizationTest {
  @Inject UserDao userDao;
  @Inject RoleDao roleDao;
  @Inject UserRoleDao userRoleDao;
  @Inject TheoryKnowledgeExamUserDao examUserDao;

  @Test
  void ordinaryUserCannotUploadScore() {
    UserEntity actor = Fixtures.user(userDao, "upload-ordinary-" + UUID.randomUUID(), "device-upload-ordinary");
    attachRole(actor, 1);

    String examId = UUID.randomUUID().toString();
    TheoryKnowledgeExamUserEntity examUser = seedExamUser(examId, 11);

    given()
        .contentType(ContentType.JSON)
        .header("token", actor.getToken())
        .header("deviceId", actor.getDeviceId())
        .body(Map.of("examId", examId,
            "list", List.of(Map.of("user_id", examUser.getUserId(), "score", 88))))
        .when()
        .post("/api/theoryKnowledgeExamUser/teacherUploadScore")
        .then()
        .statusCode(200)
        .body("code", is(207));

    TheoryKnowledgeExamUserEntity after = examUserDao.findById(examUser.getId());
    assertEquals(11, after.getScore(), "普通人员被拒后分数不得改动");
    assertEquals(2, after.getState(), "普通人员被拒后状态不得改动");
  }

  @Test
  void batchWithForeignStudentIsRejectedWholesale() {
    UserEntity actor = Fixtures.user(userDao, "upload-admin-foreign-" + UUID.randomUUID(), "device-upload-foreign");
    attachRole(actor, 0);

    String examId = UUID.randomUUID().toString();
    TheoryKnowledgeExamUserEntity mine = seedExamUser(examId, 11);
    // 另一场考试的考生：examId 不匹配
    TheoryKnowledgeExamUserEntity foreign = seedExamUser(UUID.randomUUID().toString(), 22);

    given()
        .contentType(ContentType.JSON)
        .header("token", actor.getToken())
        .header("deviceId", actor.getDeviceId())
        .body(Map.of("examId", examId,
            "list", List.of(
                Map.of("user_id", mine.getUserId(), "score", 88),
                Map.of("user_id", foreign.getUserId(), "score", 99))))
        .when()
        .post("/api/theoryKnowledgeExamUser/teacherUploadScore")
        .then()
        .statusCode(200)
        .body("code", not(200));

    assertEquals(11, examUserDao.findById(mine.getId()).getScore(), "同批合法考生的分数也必须回滚");
    assertEquals(2, examUserDao.findById(mine.getId()).getState(), "同批合法考生的状态也必须回滚");
    assertEquals(22, examUserDao.findById(foreign.getId()).getScore(), "非本场考生的分数不得被写入");
  }

  @Test
  void adminUploadsValidBatch() {
    UserEntity actor = Fixtures.user(userDao, "upload-admin-" + UUID.randomUUID(), "device-upload-admin");
    attachRole(actor, 0);

    String examId = UUID.randomUUID().toString();
    TheoryKnowledgeExamUserEntity first = seedExamUser(examId, 11);
    TheoryKnowledgeExamUserEntity second = seedExamUser(examId, 22);

    given()
        .contentType(ContentType.JSON)
        .header("token", actor.getToken())
        .header("deviceId", actor.getDeviceId())
        .body(Map.of("examId", examId,
            "list", List.of(
                Map.of("user_id", first.getUserId(), "score", 88),
                Map.of("user_id", second.getUserId(), "score", 99))))
        .when()
        .post("/api/theoryKnowledgeExamUser/teacherUploadScore")
        .then()
        .statusCode(200)
        .body("code", is(200));

    TheoryKnowledgeExamUserEntity afterFirst = examUserDao.findById(first.getId());
    assertEquals(88, afterFirst.getScore());
    assertEquals(4, afterFirst.getState(), "上分成功后状态应为 4（老师已阅卷）");
    assertEquals(99, examUserDao.findById(second.getId()).getScore());
  }

  // 播种一条考生行：state=2（学生考核中），便于断言上分是否真的改了状态
  private TheoryKnowledgeExamUserEntity seedExamUser(String examId, int score) {
    UserEntity student = Fixtures.user(userDao, "upload-student-" + UUID.randomUUID(), "device-student");
    TheoryKnowledgeExamUserEntity examUser = new TheoryKnowledgeExamUserEntity();
    examUser.setExamId(examId);
    examUser.setUserId(student.getId());
    examUser.setScore(score);
    examUser.setState(2);
    return examUserDao.save(examUser);
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
}
