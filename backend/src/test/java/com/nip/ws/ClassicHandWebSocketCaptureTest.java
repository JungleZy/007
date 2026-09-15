package com.nip.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nip.common.utils.JSONUtils;
import com.nip.dao.TelegramTrainDao;
import com.nip.dao.UserDao;
import com.nip.dto.TelegramTrainDto;
import com.nip.entity.TelegramTrainEntity;
import com.nip.entity.TelegramTrainLogEntity;
import com.nip.entity.UserEntity;
import com.nip.service.TelegramTrainService;
import com.nip.testsupport.Fixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.nip.common.constants.BaseConstants.DEVICE_ID;
import static com.nip.common.constants.BaseConstants.TOKEN;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ClassicHandWebSocketCaptureTest {
  @Inject UserDao users;
  @Inject TelegramTrainDao trains;
  @Inject TelegramTrainService service;
  @Inject ObjectMapper mapper;
  private final List<Session> clients = new ArrayList<>();

  @AfterEach
  void closeOwnConnections() throws IOException {
    for (Session session : clients) if (session.isOpen()) session.close();
  }

  @Test
  void capturesAndLogsUseHandshakeOwnerAndServerGeneratedLogIdentity() throws Exception {
    UserEntity owner = user();
    UserEntity outsider = user();
    TelegramTrainDto training = create(owner);
    String trainId = training.getTrain().getId();
    String contentId = training.getTrainFloors().getFirst().getFloorContents().getFirst().getId();
    var ownerProbe = new WebSocketHandshakeAuthorizationTest.Probe();
    var outsiderProbe = new WebSocketHandshakeAuthorizationTest.Probe();
    Session ownerClient = connect(owner, outsider.getId(), ownerProbe);
    Session outsiderClient = connect(outsider, owner.getId(), outsiderProbe);

    send(outsiderClient, 3001, Map.of("id", contentId, "moresValue", "[1]", "moresTime", "[]"));
    assertEquals(207, reply(outsiderProbe).get("code").asInt());
    assertEquals("[]", value(owner, trainId));
    send(outsiderClient, 2001, Map.of("telegramTrainId", trainId, "type", 0, "value", "[60]"));
    assertEquals(207, reply(outsiderProbe).get("code").asInt());
    assertTrue(logs(owner, trainId).isEmpty());

    send(ownerClient, 3001, Map.of("id", contentId, "moresValue", "[0,1]", "moresTime", "[60,180]"));
    awaitValue(owner, trainId, "[0,1]");
    send(ownerClient, 2001, Map.of("id", "client-selected-id", "telegramTrainId", trainId,
        "type", 0, "value", "[60]", "creatTime", "client-selected-time"));
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    List<TelegramTrainLogEntity> entries;
    do {
      entries = logs(owner, trainId);
      if (!entries.isEmpty()) break;
      Thread.sleep(20);
    } while (System.nanoTime() < deadline);
    assertEquals(1, entries.size());
    assertNotEquals("client-selected-id", entries.getFirst().getId());
    assertTrue(Long.parseLong(entries.getFirst().getCreatTime()) > 0);
  }

  @Test
  void captureSentDuringEndTransactionCannotChangeTheFinalSnapshot() throws Exception {
    UserEntity owner = user();
    TelegramTrainDto training = create(owner);
    String trainId = training.getTrain().getId();
    String contentId = training.getTrainFloors().getFirst().getFloorContents().getFirst().getId();
    var probe = new WebSocketHandshakeAuthorizationTest.Probe();
    Session client = connect(owner, owner.getId(), probe);
    service.controlTelegramTrain(0, training, owner.getToken());
    training.getTrainFloors().getFirst().getFloorContents().getFirst().setMoresValue("[0,1]");

    QuarkusTransaction.requiringNew().run(() -> {
      service.controlTelegramTrain(2, training, owner.getToken());
      send(client, 3001, Map.of("id", contentId, "moresValue", "[1]", "moresTime", "[180]"));
    });
    assertEquals(208, reply(probe).get("code").asInt());
    assertEquals("[0,1]", value(owner, trainId));
    TelegramTrainEntity result = details(owner, trainId).getTrain();
    assertEquals(3, result.getStatus());
    assertEquals(0, result.getErrorNumber());

    send(client, 2001, Map.of("telegramTrainId", trainId, "type", 0, "value", "[999]"));
    assertEquals(208, reply(probe).get("code").asInt());
    assertTrue(logs(owner, trainId).isEmpty());
  }

  @Test
  void historicalCapturesRemainReadOnlyOverWebSocket() throws Exception {
    UserEntity owner = user();
    TelegramTrainDto training = create(owner);
    String trainId = training.getTrain().getId();
    String contentId = training.getTrainFloors().getFirst().getFloorContents().getFirst().getId();
    QuarkusTransaction.requiringNew().run(() -> trains.findById(trainId).setProtocolVersion(0));
    var probe = new WebSocketHandshakeAuthorizationTest.Probe();
    Session client = connect(owner, owner.getId(), probe);
    send(client, 3001, Map.of("id", contentId, "moresValue", "[1]", "moresTime", "[]"));
    assertEquals(208, reply(probe).get("code").asInt());
    send(client, 2001, Map.of("telegramTrainId", trainId, "type", 0, "value", "[]"));
    assertEquals(208, reply(probe).get("code").asInt());
    assertEquals("[]", value(owner, trainId));
    assertTrue(logs(owner, trainId).isEmpty());
  }

  @Test
  void replacedConnectionCannotKeepWritingForTheSameOwner() throws Exception {
    UserEntity owner = user();
    TelegramTrainDto training = create(owner);
    String trainId = training.getTrain().getId();
    String contentId = training.getTrainFloors().getFirst().getFloorContents().getFirst().getId();
    var oldProbe = new WebSocketHandshakeAuthorizationTest.Probe();
    Session oldClient = connect(owner, owner.getId(), oldProbe);
    var currentProbe = new WebSocketHandshakeAuthorizationTest.Probe();
    Session currentClient = connect(owner, owner.getId(), currentProbe);
    assertEquals(1, reply(oldProbe).get("code").asInt());
    send(oldClient, 3001, Map.of("id", contentId, "moresValue", "[1]", "moresTime", "[]"));
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (oldClient.isOpen() && System.nanoTime() < deadline) Thread.sleep(20);
    assertFalse(oldClient.isOpen());
    assertEquals("[]", value(owner, trainId));
    send(currentClient, 3001, Map.of("id", contentId, "moresValue", "[0,1]", "moresTime", "[]"));
    awaitValue(owner, trainId, "[0,1]");
  }

  private UserEntity user() {
    return Fixtures.user(users, "ws-hand-" + UUID.randomUUID(), "ws-device-" + UUID.randomUUID());
  }

  private TelegramTrainDto create(UserEntity owner) {
    String id = given().contentType(ContentType.JSON).headers(TOKEN, owner.getToken(), DEVICE_ID, owner.getDeviceId())
        .body(Map.of("train", Map.of("type", 0), "trainFloors", List.of(Map.of(
            "floor", Map.of("type", 0, "numberType", 0),
            "floorContents", List.of(Map.of("moresKey", "A", "moresValue", "[]", "moresTime", "[]"))))))
        .post("/api/telegramTrain/saveTelegramTrain")
        .then().statusCode(200).body("code", is(200)).extract().path("data.id");
    return details(owner, id);
  }

  private TelegramTrainDto details(UserEntity owner, String id) {
    return given().contentType(ContentType.JSON).headers(TOKEN, owner.getToken(), DEVICE_ID, owner.getDeviceId())
        .body(Map.of("trainId", id)).post("/api/telegramTrain/getById")
        .then().statusCode(200).body("code", is(200)).extract().jsonPath().getObject("data", TelegramTrainDto.class);
  }

  private String value(UserEntity owner, String id) {
    return details(owner, id).getTrainFloors().getFirst().getFloorContents().getFirst().getMoresValue();
  }

  private List<TelegramTrainLogEntity> logs(UserEntity owner, String id) {
    return given().contentType(ContentType.JSON).headers(TOKEN, owner.getToken(), DEVICE_ID, owner.getDeviceId())
        .body(Map.of("id", id)).post("/api/telegramTrain/getTelegramTrainLog")
        .then().statusCode(200).body("code", is(200)).extract().jsonPath().getList("data", TelegramTrainLogEntity.class);
  }

  private Session connect(UserEntity user, String pathId, WebSocketHandshakeAuthorizationTest.Probe probe) throws Exception {
    String query = "?token=" + URLEncoder.encode(user.getToken(), StandardCharsets.UTF_8)
        + "&deviceId=" + URLEncoder.encode(user.getDeviceId(), StandardCharsets.UTF_8);
    Session session = ContainerProvider.getWebSocketContainer().connectToServer(probe,
        URI.create("ws://localhost:18081/websocket/" + pathId + query));
    clients.add(session);
    String marker = "registered:" + UUID.randomUUID();
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      WebSocketService.sendInfo(user.getId(), marker);
      if (marker.equals(probe.received.poll(100, TimeUnit.MILLISECONDS))) return session;
    }
    throw new AssertionError("Authenticated capture connection did not become reachable");
  }

  private JsonNode reply(WebSocketHandshakeAuthorizationTest.Probe probe) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      String frame = probe.received.poll(100, TimeUnit.MILLISECONDS);
      if (frame == null || frame.startsWith("registered:")) continue;
      return mapper.readTree(frame);
    }
    throw new AssertionError("Expected capture refusal was not delivered");
  }

  private void awaitValue(UserEntity owner, String trainId, String expected) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    String actual;
    do {
      actual = value(owner, trainId);
      if (expected.equals(actual)) break;
      Thread.sleep(20);
    } while (System.nanoTime() < deadline);
    assertEquals(expected, actual);
  }

  private static void send(Session session, int code, Map<String, Object> data) {
    try {
      session.getBasicRemote().sendText(JSONUtils.toJson(Map.of("code", code, "data", data)));
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }
}
