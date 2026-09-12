package com.nip.controller;

import com.nip.common.constants.SimulationRoomTypeEnum;
import com.nip.dao.UserDao;
import com.nip.dao.simulation.SimulationRouterRoomContentDao;
import com.nip.dao.simulation.SimulationRouterRoomDao;
import com.nip.entity.UserEntity;
import com.nip.entity.simulation.router.SimulationRouterRoomContentEntity;
import com.nip.entity.simulation.router.SimulationRouterRoomEntity;
import com.nip.testsupport.Fixtures;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.parsing.Parser;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 * Task 4.3 CA-P2-19：抄收/通播/线路三个房间的 getRoomDetail 把查询参数写成了 @RestQuery("roomgId")，
 * 前端传 roomId 永远绑定不上 → roomId 恒为 null。
 * 抄收/通播房（service 无 orElseThrow）表现为「200 + data:null 空详情」，
 * 线路房（SimulationRouterRoomService:258 有 orElseThrow）表现为错误信封。
 */
@QuarkusTest
class SimulationRoomDetailParamTest {
  private static final String TOKEN = "p43-room-" + UUID.randomUUID();
  private static final String DEVICE = "p43-device-" + UUID.randomUUID();

  @Inject
  UserDao userDao;
  @Inject
  SimulationRouterRoomDao roomDao;
  @Inject
  SimulationRouterRoomContentDao contentDao;
  @Inject com.nip.dao.simulation.SimulationRouterRoomUserDao memberDao;

  private String userId;

  @BeforeEach
  void seedUser() {
    RestAssured.defaultParser = Parser.JSON;
    UserEntity user = userDao.findUserEntityByToken(TOKEN);
    if (user == null) {
      user = Fixtures.user(userDao, TOKEN, DEVICE);
    }
    userId = user.getId();
  }

  @Test
  void receptRoomDetailBindsRoomIdAndReturnsRealRoom() {
    String name = "recept-" + UUID.randomUUID();
    Integer roomId = seedRoom(name, SimulationRoomTypeEnum.RECEPT.getType());

    given()
        .header("Origin", "http://localhost")
        .header("token", TOKEN)
        .header("deviceId", DEVICE)
        .queryParam("roomId", roomId)
        .when().get("/api/simulation/recept/getRoomDetail")
        .then().statusCode(200)
        .body("code", is(200))
        .body("data.name", equalTo(name))
        .body("data.createUserId", equalTo(userId));
  }

  @Test
  void reportRoomDetailBindsRoomIdAndReturnsRealRoom() {
    String name = "report-" + UUID.randomUUID();
    Integer roomId = seedRoom(name, SimulationRoomTypeEnum.REPORT.getType());

    given()
        .header("Origin", "http://localhost")
        .header("token", TOKEN)
        .header("deviceId", DEVICE)
        .queryParam("roomId", roomId)
        .when().get("/api/simulation/report/getRoomDetail")
        .then().statusCode(200)
        .body("code", is(200))
        .body("data.name", equalTo(name))
        .body("data.createUserId", equalTo(userId));
  }

  @Test
  void routerRoomDetailBindsRoomIdInsteadOfFailingLookup() {
    String name = "router-" + UUID.randomUUID();
    Integer roomId = seedRoom(name, SimulationRoomTypeEnum.ROUTER.getType());

    given()
        .header("Origin", "http://localhost")
        .header("token", TOKEN)
        .header("deviceId", DEVICE)
        .queryParam("roomId", roomId)
        .when().get("/api/simulation/router/getRoomDetail")
        .then().statusCode(200)
        .body("code", is(200))
        .body("data.createUserId", equalTo(userId))
        .body("data.currentUserId", equalTo(userId))
        .body("data.content", equalTo("[]"));
  }

  @Test
  void answerReadsRespectRoomMembershipAndRouterSenderIsNotTeacher() {
    String senderToken = UUID.randomUUID().toString();
    String receiverToken = UUID.randomUUID().toString();
    String outsiderToken = UUID.randomUUID().toString();
    UserEntity sender = Fixtures.user(userDao, senderToken, DEVICE);
    UserEntity receiver = Fixtures.user(userDao, receiverToken, DEVICE);
    Fixtures.user(userDao, outsiderToken, DEVICE);
    for (int roomType : new int[]{0, 2}) {
      Integer roomId = seedRoom("answer-access-" + UUID.randomUUID(), roomType);
      for (UserEntity member : new UserEntity[]{sender, receiver}) {
        var membership = new com.nip.entity.simulation.router.SimulationRouterRoomUserEntity();
        membership.setRoomId(roomId);
        membership.setUserId(member.getId());
        membership.setUserType(member == sender ? 0 : 1);
        membership.setChannel(member == sender ? 0 : 1);
        memberDao.saveAndFlush(membership);
      }
      assertPageAccess(TOKEN, roomId, receiver.getId(), 200);
      assertPageAccess(senderToken, roomId, receiver.getId(), roomType == 0 ? 202 : 200);
      assertPageAccess(receiverToken, roomId, receiver.getId(), 200);
      assertPageAccess(receiverToken, roomId, sender.getId(), 202);
      assertPageAccess(outsiderToken, roomId, receiver.getId(), 202);
      given().header("Origin", "http://localhost").header("token", outsiderToken).header("deviceId", DEVICE)
          .queryParam("roomId", roomId).when().get("/api/simulation/report/getRoomDetail")
          .then().statusCode(200).body("code", is(202));
    }
  }

  private void assertPageAccess(String token, Integer roomId, String targetUserId, int code) {
    given().header("Origin", "http://localhost").header("token", token).header("deviceId", DEVICE)
        .queryParam("roomId", roomId).queryParam("userId", targetUserId).queryParam("pageNumber", 1)
        .when().get("/api/simulation/router/findPage")
        .then().statusCode(200).body("code", is(code));
  }

  private Integer seedRoom(String name, int roomType) {
    SimulationRouterRoomEntity room = new SimulationRouterRoomEntity();
    room.setName(name);
    room.setIsCable(0);
    room.setCreateUserId(userId);
    room.setStats(0);
    room.setPlayStatus(0);
    room.setTotalTime(0);
    room.setRoomType(roomType);
    SimulationRouterRoomEntity saved = roomDao.saveAndFlush(room);

    SimulationRouterRoomContentEntity content = new SimulationRouterRoomContentEntity();
    content.setRoomId(saved.getId());
    content.setContent("[]");
    content.setMainSignal("100");
    content.setInterferenceSignal("");
    content.setBdType(1);
    content.setBwType(1);
    content.setBwCount(200);
    content.setIsRandom(0);
    contentDao.saveAndFlush(content);
    return saved.getId();
  }
}
