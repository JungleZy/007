package com.nip.controller;

import com.nip.dao.EnteringKeyPointsDao;
import com.nip.dao.RoleDao;
import com.nip.dao.UserDao;
import com.nip.dao.UserRoleDao;
import com.nip.entity.RoleEntity;
import com.nip.entity.UserEntity;
import com.nip.entity.UserRoleEntity;
import com.nip.testsupport.Fixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static com.nip.common.constants.BaseConstants.DEVICE_ID;
import static com.nip.common.constants.BaseConstants.TOKEN;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class EnteringKeyPointsAuthorizationTest {
  @Inject UserDao userDao;
  @Inject RoleDao roleDao;
  @Inject UserRoleDao userRoleDao;
  @Inject EnteringKeyPointsDao pointsDao;

  private String noteId;

  @AfterEach
  void removeOwnedNote() {
    if (noteId != null) pointsDao.deleteById(noteId);
  }

  @Test
  void studentsCanReadButCannotCreateOrReplaceSharedNotes() {
    UserEntity student = user("notes-student");
    UserEntity admin = user("notes-admin");
    RoleEntity role = new RoleEntity();
    role.setTitle("notes-admin-" + UUID.randomUUID());
    role.setIsAdmin(0);
    role.setIsDefault(1);
    roleDao.saveAndFlush(role);
    UserRoleEntity membership = new UserRoleEntity();
    membership.setUserId(admin.getId());
    membership.setRoleId(role.getId());
    userRoleDao.saveAndFlush(membership);

    noteId = given().contentType(ContentType.JSON)
        .headers(TOKEN, admin.getToken(), DEVICE_ID, admin.getDeviceId())
        .body(Map.of("type", 0, "content", "original teaching note"))
        .post("/api/enteringKeyPoints/save")
        .then().statusCode(200).body("code", is(200)).extract().path("data.id");

    given().contentType(ContentType.JSON)
        .headers(TOKEN, student.getToken(), DEVICE_ID, student.getDeviceId())
        .body(Map.of("id", noteId, "type", 0, "content", "student replacement"))
        .post("/api/enteringKeyPoints/save")
        .then().statusCode(200).body("code", is(207));
    assertVisibleContent(student, "original teaching note");

    given().contentType(ContentType.JSON)
        .headers(TOKEN, student.getToken(), DEVICE_ID, student.getDeviceId())
        .body(Map.of("type", 1, "content", "student-created note"))
        .post("/api/enteringKeyPoints/save")
        .then().statusCode(200).body("code", is(207));
    given().contentType(ContentType.JSON)
        .headers(TOKEN, student.getToken(), DEVICE_ID, student.getDeviceId())
        .body(Map.of("type", 1)).post("/api/enteringKeyPoints/getByType")
        .then().statusCode(200).body("code", is(200)).body("data", nullValue());

    given().contentType(ContentType.JSON)
        .headers(TOKEN, admin.getToken(), DEVICE_ID, admin.getDeviceId())
        .body(Map.of("id", noteId, "type", 0, "content", "approved teaching note"))
        .post("/api/enteringKeyPoints/save")
        .then().statusCode(200).body("code", is(200));
    assertVisibleContent(student, "approved teaching note");
  }

  private UserEntity user(String prefix) {
    return Fixtures.user(userDao, prefix + "-" + UUID.randomUUID(), "notes-device-" + UUID.randomUUID());
  }

  private void assertVisibleContent(UserEntity viewer, String expected) {
    given().contentType(ContentType.JSON)
        .headers(TOKEN, viewer.getToken(), DEVICE_ID, viewer.getDeviceId())
        .body(Map.of("type", 0)).post("/api/enteringKeyPoints/getByType")
        .then().statusCode(200).body("code", is(200)).body("data.content", is(expected));
  }
}
