package com.nip.controller;

import com.nip.dao.RoleDao;
import com.nip.dao.UserDao;
import com.nip.dao.UserRoleDao;
import com.nip.dao.simulation.SimulationRouterRoomDao;
import com.nip.dao.simulation.SimulationRouterRoomUserDao;
import com.nip.entity.RoleEntity;
import com.nip.entity.UserEntity;
import com.nip.entity.UserRoleEntity;
import com.nip.entity.simulation.router.SimulationRouterRoomEntity;
import com.nip.entity.simulation.router.SimulationRouterRoomUserEntity;
import com.nip.testsupport.Fixtures;
import com.nip.testsupport.WebSocketSessionProbe;
import com.nip.ws.model.SimulationSessionHolder;
import com.nip.ws.model.SimulationUserModel;
import com.nip.ws.service.simulation.SimulationGlobal;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@QuarkusTest
class SimulationRestAuthorizationTest {
  @Inject UserDao userDao;
  @Inject RoleDao roleDao;
  @Inject UserRoleDao userRoleDao;
  @Inject SimulationRouterRoomDao roomDao;
  @Inject SimulationRouterRoomUserDao memberDao;

  private final List<Integer> roomIds = new ArrayList<>();
  private UserEntity owner;
  private UserEntity receiver;
  private UserEntity sender;
  private UserEntity outsider;
  private UserEntity admin;

  @BeforeEach
  void createActors() {
    owner = user();
    receiver = user();
    sender = user();
    outsider = user();
    admin = user();
    role(outsider, 1);
    role(admin, 0);
  }

  @AfterEach
  void removeRooms() {
    for (Integer roomId : roomIds) {
      SimulationGlobal.routerRoom.remove(roomId);
      SimulationGlobal.disturbRoom.remove(roomId);
      QuarkusTransaction.requiringNew().run(() -> {
        memberDao.delete("roomId", roomId);
        roomDao.deleteById(roomId);
      });
    }
    roomIds.clear();
  }

  @Test
  void channelDenialsLeavePersistedAndOnlineChannelsUnchanged() {
    int roomId = room(0);
    SimulationSessionHolder onlineReceiver = online(roomId, receiver, false);
    SimulationSessionHolder onlineSender = online(roomId, sender, false);

    change(outsider, roomId, receiver, 9, 207);
    assertChannel(roomId, receiver, onlineReceiver, 1);
    change(receiver, roomId, sender, 9, 207);
    assertChannel(roomId, sender, onlineSender, 1);
    change(sender, roomId, receiver, 9, 207);
    assertChannel(roomId, receiver, onlineReceiver, 1);
  }

  @Test
  void receiverCanTuneSelfAndManagersCanConfigureMembers() {
    int roomId = room(0);
    SimulationSessionHolder onlineReceiver = online(roomId, receiver, false);
    change(receiver, roomId, receiver, 2, 200);
    assertChannel(roomId, receiver, onlineReceiver, 2);
    change(owner, roomId, receiver, 3, 200);
    assertChannel(roomId, receiver, onlineReceiver, 3);
    change(admin, roomId, receiver, 4, 200);
    assertChannel(roomId, receiver, onlineReceiver, 4);

    int controlledRoom = room(1);
    change(sender, controlledRoom, receiver, 5, 200);
    assertEquals(5, channel(controlledRoom, receiver));
  }

  @Test
  void channelReadsRequireMembershipOrganizationOrAdministration() {
    int roomId = room(0);
    request(outsider).queryParam("roomId", roomId).get("/api/simulation/router/getRoomChannels")
        .then().statusCode(200).body("code", is(207));
    for (UserEntity actor : List.of(receiver, sender, owner, admin)) {
      request(actor).queryParam("roomId", roomId).get("/api/simulation/router/getRoomChannels")
          .then().statusCode(200).body("code", is(200), "data", is(List.of(1)));
    }
  }

  @Test
  void settingsRejectNonControllersWithoutTouchingRoomOrOnlineState() {
    int roomId = room(1);
    SimulationSessionHolder online = online(roomId, receiver, true);
    List<SimulationSessionHolder> sessions = SimulationGlobal.disturbRoom.get(roomId);
    for (UserEntity actor : List.of(outsider, receiver)) {
      setting(actor, roomId, "denied", 207);
      assertEquals("original", setting(roomId));
      assertSame(sessions, SimulationGlobal.disturbRoom.get(roomId));
      assertEquals(List.of(online), sessions);
      assertChannel(roomId, receiver, online, 1);
      assertEquals(1, online.userModel().getStatus());
    }
    setting(sender, roomId, "controller", 200);
    assertEquals("controller", setting(roomId));
    setting(owner, roomId, "owner", 200);
    assertEquals("owner", setting(roomId));
    setting(admin, roomId, "admin", 200);
    assertEquals("admin", setting(roomId));
  }

  @Test
  void missingResourcesAndCrossRoomTargetsRemainParameterErrors() {
    int roomId = room(0);
    int otherRoom = room(0);
    QuarkusTransaction.requiringNew().run(() -> {
      SimulationRouterRoomUserEntity member = new SimulationRouterRoomUserEntity();
      member.setRoomId(otherRoom);
      member.setUserId(outsider.getId());
      member.setUserType(1);
      member.setChannel(7);
      memberDao.save(member);
    });
    change(owner, roomId, outsider, 8, 202);
    assertEquals(7, channel(otherRoom, outsider));
    change(owner, -1, receiver, 8, 202);
    setting(owner, -1, "missing", 202);
    request(owner).queryParam("roomId", -1).get("/api/simulation/router/getRoomChannels")
        .then().statusCode(200).body("code", is(202));
  }

  @Test
  void debugRoomInventoryIsAdministratorOnly() {
    for (UserEntity actor : List.of(outsider, receiver, owner)) {
      request(actor).get("/api/simulation/socket/getAllRoomInfo")
          .then().statusCode(200).body("code", is(207));
    }
    request(admin).get("/api/simulation/socket/getAllRoomInfo")
        .then().statusCode(200).body("code", is(200));
  }

  private int room(int type) {
    int roomId = QuarkusTransaction.requiringNew().call(() -> {
      SimulationRouterRoomEntity room = new SimulationRouterRoomEntity();
      room.setCreateUserId(owner.getId());
      room.setRoomType(type);
      room.setStats(0);
      room.setSetting("original");
      roomDao.save(room);
      for (UserEntity actor : List.of(sender, receiver)) {
        SimulationRouterRoomUserEntity member = new SimulationRouterRoomUserEntity();
        member.setRoomId(room.getId());
        member.setUserId(actor.getId());
        member.setUserType(actor == sender ? 0 : 1);
        member.setUserStatus(0);
        member.setChannel(1);
        memberDao.save(member);
      }
      return room.getId();
    });
    roomIds.add(roomId);
    return roomId;
  }

  private SimulationSessionHolder online(int roomId, UserEntity actor, boolean disturb) {
    SimulationUserModel model = new SimulationUserModel();
    model.setId(actor.getId());
    model.setChannel(1);
    model.setStatus(1);
    SimulationSessionHolder holder = new SimulationSessionHolder(
        WebSocketSessionProbe.bound("rest-" + roomId + "-" + actor.getId(), actor.getId()).session(), model);
    (disturb ? SimulationGlobal.disturbRoom : SimulationGlobal.routerRoom)
        .computeIfAbsent(roomId, ignored -> new ArrayList<>()).add(holder);
    return holder;
  }

  private void assertChannel(int roomId, UserEntity actor, SimulationSessionHolder online, int expected) {
    assertEquals(expected, channel(roomId, actor));
    assertEquals(expected, online.userModel().getChannel());
  }

  private int channel(int roomId, UserEntity actor) {
    return QuarkusTransaction.requiringNew().call(() ->
        memberDao.findByUserIdAndRoomId(actor.getId(), roomId).getChannel());
  }

  private String setting(int roomId) {
    return QuarkusTransaction.requiringNew().call(() -> roomDao.findById(roomId).getSetting());
  }

  private void change(UserEntity actor, int roomId, UserEntity target, int channel, int code) {
    request(actor).body(Map.of("roomId", roomId, "userId", target.getId(), "channel", channel))
        .post("/api/simulation/router/changeChannel").then().statusCode(200).body("code", is(code));
  }

  private void setting(UserEntity actor, int roomId, String setting, int code) {
    request(actor).body(Map.of("roomId", roomId, "setting", setting))
        .post("/api/simulation/routerRoomContent/saveSetting").then().statusCode(200).body("code", is(code));
  }

  private RequestSpecification request(UserEntity actor) {
    return given().header("token", actor.getToken()).header("deviceId", actor.getDeviceId())
        .contentType(ContentType.JSON);
  }

  private UserEntity user() {
    return Fixtures.user(userDao, UUID.randomUUID().toString(), UUID.randomUUID().toString());
  }

  private void role(UserEntity actor, int isAdmin) {
    QuarkusTransaction.requiringNew().run(() -> {
      RoleEntity role = new RoleEntity();
      role.setTitle("simulation-rest-" + UUID.randomUUID());
      role.setIsAdmin(isAdmin);
      role.setIsDefault(1);
      roleDao.save(role);
      UserRoleEntity link = new UserRoleEntity();
      link.setUserId(actor.getId());
      link.setRoleId(role.getId());
      userRoleDao.save(link);
    });
  }
}
