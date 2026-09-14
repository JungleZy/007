package com.nip.controller;

import com.nip.dao.MastheadDao;
import com.nip.dao.PostEnteringExerciseWordStockDao;
import com.nip.dao.PostTickerTapeTrainDao;
import com.nip.dao.RoleDao;
import com.nip.dao.UserDao;
import com.nip.dao.UserRoleDao;
import com.nip.entity.MastheadEntity;
import com.nip.entity.PostTickerTapeTrainEntity;
import com.nip.entity.RoleEntity;
import com.nip.entity.UserEntity;
import com.nip.entity.UserRoleEntity;
import com.nip.testsupport.Fixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class MasterDataAuthorizationTest {
  @Inject UserDao userDao;
  @Inject RoleDao roleDao;
  @Inject UserRoleDao userRoleDao;
  @Inject MastheadDao mastheadDao;
  @Inject PostTickerTapeTrainDao trainDao;
  @Inject PostEnteringExerciseWordStockDao wordStockDao;

  @Test
  void ownerCanWriteAndForeignUserCannotReadOrWriteMasthead() {
    String ownerToken = "masthead-owner-" + UUID.randomUUID();
    String ownerDevice = "device-" + UUID.randomUUID();
    UserEntity owner = Fixtures.user(userDao, ownerToken, ownerDevice);
    UserEntity foreign = Fixtures.user(userDao, "masthead-foreign-" + UUID.randomUUID(), "device-" + UUID.randomUUID());
    PostTickerTapeTrainEntity train = new PostTickerTapeTrainEntity();
    train.setUserId(owner.getId());
    train.setName("owned train");
    train = trainDao.saveAndFlush(train);

    given().contentType(ContentType.JSON).headers("token", ownerToken, "deviceId", ownerDevice)
        .body(Map.of("trainId", train.getId(), "content", "owner"))
        .post("/api/masthead/save").then().statusCode(200).body("code", is(200));
    long count = mastheadDao.count();
    given().contentType(ContentType.JSON).headers("token", foreign.getToken(), "deviceId", foreign.getDeviceId())
        .body(Map.of("trainId", train.getId(), "content", "takeover"))
        .post("/api/masthead/save").then().statusCode(200).body("code", is(207));
    given().headers("token", foreign.getToken(), "deviceId", foreign.getDeviceId())
        .get("/api/masthead/findByTrainId?trainId=" + train.getId())
        .then().statusCode(200).body("code", is(207));
    assertEquals(count, mastheadDao.count());
  }

  @Test
  void existingHeaderIdCannotBeReassignedToAnotherOwnedTrain() {
    UserEntity owner = Fixtures.user(userDao, "masthead-reassign-" + UUID.randomUUID(), "device-" + UUID.randomUUID());
    PostTickerTapeTrainEntity first = new PostTickerTapeTrainEntity(); first.setUserId(owner.getId()); first.setName("first");
    PostTickerTapeTrainEntity second = new PostTickerTapeTrainEntity(); second.setUserId(owner.getId()); second.setName("second");
    first = trainDao.saveAndFlush(first); second = trainDao.saveAndFlush(second);
    MastheadEntity header = new MastheadEntity(); header.setTrainId(first.getId()); header.setContent("first");
    header = mastheadDao.saveAndFlush(header);
    given().contentType(ContentType.JSON).headers("token", owner.getToken(), "deviceId", owner.getDeviceId())
        .body(Map.of("id", header.getId(), "trainId", second.getId(), "content", "takeover"))
        .post("/api/masthead/save").then().statusCode(200).body("code", is(207));
    assertEquals(first.getId(), mastheadDao.findById(header.getId()).getTrainId());
  }

  @Test
  void administratorCanWriteWordStock() {
    String token = "master-admin-" + UUID.randomUUID();
    String device = "device-" + UUID.randomUUID();
    UserEntity user = Fixtures.user(userDao, token, device);
    attachAdmin(user);
    given().contentType(ContentType.JSON).headers("token", token, "deviceId", device)
        .body(Map.of("name", "admin-word", "type", 0, "content", "admin"))
        .post("/api/postEnteringExerciseWordStock/add").then().statusCode(200).body("code", is(200));
  }


  private void attachAdmin(UserEntity user) {
    RoleEntity role = new RoleEntity(); role.setTitle("master-admin-" + UUID.randomUUID()); role.setIsAdmin(0); role.setIsDefault(1);
    role = roleDao.saveAndFlush(role); UserRoleEntity link = new UserRoleEntity(); link.setUserId(user.getId()); link.setRoleId(role.getId()); userRoleDao.saveAndFlush(link);
  }
}
