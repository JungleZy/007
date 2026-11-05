package com.nip.controller;

import com.nip.dao.MastheadDao;
import com.nip.dao.UserDao;
import com.nip.dao.simulation.SimulationRouterRoomDao;
import com.nip.dao.simulation.SimulationRouterRoomUserDao;
import com.nip.entity.MastheadEntity;
import com.nip.entity.UserEntity;
import com.nip.entity.simulation.router.SimulationRouterRoomEntity;
import com.nip.entity.simulation.router.SimulationRouterRoomUserEntity;
import com.nip.testsupport.Fixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class RoomMastheadTest {
  @Inject UserDao users;
  @Inject SimulationRouterRoomDao rooms;
  @Inject SimulationRouterRoomUserDao members;
  @Inject MastheadDao headers;

  @ParameterizedTest
  @ValueSource(ints = {2, 3})
  void roomHeaderSupportsEntryAndEnforcesRoomPermissions(int roomType) {
    UserEntity teacher = user();
    UserEntity student = user();
    UserEntity stranger = user();
    SimulationRouterRoomEntity room = new SimulationRouterRoomEntity();
    room.setName("room masthead regression");
    room.setRoomType(roomType);
    room.setStats(0);
    room.setCreateUserId(teacher.getId());
    room = rooms.saveAndFlush(room);
    SimulationRouterRoomUserEntity member = new SimulationRouterRoomUserEntity();
    member.setRoomId(room.getId());
    member.setUserId(student.getId());
    member.setUserType(1);
    members.saveAndFlush(member);
    String readUrl = "/api/masthead/findByRoomId?roomId=" + room.getId();
    String writeUrl = "/api/masthead/saveRoom?roomId=" + room.getId();

    for (UserEntity actor : new UserEntity[]{teacher, student}) {
      given().headers("token", actor.getToken(), "deviceId", actor.getDeviceId())
          .get(readUrl).then().statusCode(200).body("code", is(200)).body("data", nullValue());
    }

    // 已有房间报头继续按原来的整数编号读取。
    MastheadEntity existing = new MastheadEntity();
    existing.setTrainId(room.getId().toString());
    existing.setContent("NR11 CK100");
    existing = headers.saveAndFlush(existing);
    given().headers("token", student.getToken(), "deviceId", student.getDeviceId())
        .get(readUrl).then().statusCode(200).body("code", is(200)).body("data.content", is("NR11 CK100"));

    given().contentType(ContentType.JSON)
        .headers("token", teacher.getToken(), "deviceId", teacher.getDeviceId())
        .body(Map.of("content", "NR12 CK100")).post(writeUrl).then().statusCode(200)
        .body("code", is(200)).body("data.id", is(existing.getId()));
    assertEquals(1, headers.count("trainId", room.getId().toString()));

    for (UserEntity actor : new UserEntity[]{student, stranger}) {
      given().contentType(ContentType.JSON)
          .headers("token", actor.getToken(), "deviceId", actor.getDeviceId())
          .body(Map.of("content", "unauthorized")).post(writeUrl)
          .then().statusCode(200).body("code", is(207));
    }
    given().headers("token", stranger.getToken(), "deviceId", stranger.getDeviceId())
        .get(readUrl).then().statusCode(200).body("code", is(207));
    given().headers("token", student.getToken(), "deviceId", student.getDeviceId())
        .get(readUrl).then().statusCode(200).body("code", is(200)).body("data.content", is("NR12 CK100"));
    given().headers("token", teacher.getToken(), "deviceId", teacher.getDeviceId())
        .get("/api/masthead/findByRoomId?roomId=0").then().statusCode(200).body("code", is(202));
  }

  private UserEntity user() {
    return Fixtures.user(users, "room-header-" + UUID.randomUUID(), "device-" + UUID.randomUUID());
  }
}
