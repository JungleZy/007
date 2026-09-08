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
