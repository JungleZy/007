package com.nip.service;

import com.nip.dao.TelegraphKeyPatSyntheticalDao;
import com.nip.dao.TelegraphKeyTrainStatisticalDao;
import com.nip.dao.UserDao;
import com.nip.dto.TelegraphKeyPatSyntheticalDto;
import com.nip.entity.TelegraphKeyPatSyntheticalEntity;
import com.nip.entity.UserEntity;
import com.nip.testsupport.Fixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ComprehensiveAuthorityTest {
  private static final String BASE = "/api/telegraphKeyPatTrainSynthetical/";
  private static final String SOURCE = "[{\"text\":\"1234\",\"value\":\"\",\"type\":true,\"isFocus\":false},{\"text\":\"5678\",\"value\":\"\",\"type\":true,\"isFocus\":false}]";
  private static final String ANSWER = "[{\"text\":\"1234\",\"value\":\"1234\",\"isFocus\":true},{\"text\":\"5678\",\"value\":\"\",\"isFocus\":false}]";
  @Inject UserDao users;
  @Inject TelegraphKeyPatSyntheticalDao trains;
  @Inject TelegraphKeyTrainStatisticalDao statistics;
  @Inject TelegraphKeyPatSyntheticalService service;

  @Test
  void creationFreezesSourceAndReadingDoesNotRequireWriteTransaction() {
    UserEntity owner = actor();
    String id = create(owner);
    given().headers("token", owner.getToken(), "deviceId", owner.getDeviceId()).contentType(ContentType.JSON)
        .body(Map.of("id", id)).post(BASE + "findById").then().statusCode(200).body("code", is(200))
        .body("data.totalNumber", is(2)).body("data.messageType", is(0));
    assertEquals(SOURCE, trains.findById(id).getSourceContent());
    UserEntity other = actor();
    given().headers("token", other.getToken(), "deviceId", other.getDeviceId()).contentType(ContentType.JSON)
        .body(Map.of("id", id, "title", "takeover")).post(BASE + "save").then().body("code", is(207));
    assertEquals(owner.getId(), trains.findById(id).getCreateUserId());
  }

  @Test
  void aggregateForgeriesCannotCreditUnenteredSourceAndRetriesDoNotResettle() {
    UserEntity owner = actor();
    String id = create(owner);
    service.begin(owner.getToken(), id);
    QuarkusTransaction.requiringNew().run(() -> trains.findById(id).setStartedAt(LocalDateTime.now().minusSeconds(60)));
    given().headers("token", owner.getToken(), "deviceId", owner.getDeviceId()).contentType(ContentType.JSON)
        .body(Map.of("id", id, "content", ANSWER, "speed", "99999", "duration", "1", "totalNumber", 99999))
        .post(BASE + "finish").then().body("code", is(200));
    TelegraphKeyPatSyntheticalEntity saved = trains.findById(id);
    assertEquals(2, saved.getTotalNumber());
    assertEquals(0, saved.getErrorNumber());
    assertEquals(100.0, saved.getAccuracy());
    assertTrue(Double.parseDouble(saved.getSpeed()) > 3 && Double.parseDouble(saved.getSpeed()) <= 4,
        "速率只计已输入的4个字符，不应把8个题面字符全算入");
    assertTrue(Long.parseLong(saved.getDuration()) >= 60);
    String duration = saved.getDuration();
    var request = new TelegraphKeyPatSyntheticalDto().setId(id).setContent(ANSWER);
    CompletableFuture.allOf(CompletableFuture.runAsync(() -> service.finish(owner.getToken(), request)),
        CompletableFuture.runAsync(() -> service.finish(owner.getToken(), request))).join();
    assertEquals(duration, trains.findById(id).getDuration());
    assertEquals(1, statistics.findByUserIdAndType(owner.getId(), 2).getTotalCount());
    given().headers("token", owner.getToken(), "deviceId", owner.getDeviceId()).contentType(ContentType.JSON)
        .body(Map.of("id", id, "content", ANSWER.replace("1234\",\"isFocus", "9999\",\"isFocus")))
        .post(BASE + "finish").then().body("code", is(208));
    assertThrows(com.nip.common.exception.TerminalStateException.class, () -> service.begin(owner.getToken(), id));
    assertThrows(com.nip.common.exception.TerminalStateException.class, () -> service.goTo(owner.getToken(), id));
  }

  @Test
  void pauseResumePreservesClockAndNewTrainingClosesPausedRecordOnce() {
    UserEntity owner = actor();
    String id = create(owner);
    service.begin(owner.getToken(), id);
    LocalDateTime started = trains.findById(id).getStartedAt();
    service.begin(owner.getToken(), id);
    assertEquals(started, trains.findById(id).getStartedAt());
    var request = new TelegraphKeyPatSyntheticalDto().setId(id).setContent(ANSWER);
    service.stop(owner.getToken(), request);
    long pausedMillis = trains.findById(id).getAccumulatedActiveMillis();
    service.stop(owner.getToken(), request);
    assertEquals(pausedMillis, trains.findById(id).getAccumulatedActiveMillis());
    service.goTo(owner.getToken(), id);
    LocalDateTime resumed = trains.findById(id).getStartedAt();
    service.goTo(owner.getToken(), id);
    assertEquals(resumed, trains.findById(id).getStartedAt());
    service.stop(owner.getToken(), request);
    String pausedDuration = trains.findById(id).getDuration();
    String next = create(owner);
    assertNotEquals(id, next);
    assertEquals(3, trains.findById(id).getStatus());
    assertEquals(pausedDuration, trains.findById(id).getDuration());
    assertEquals(1, statistics.findByUserIdAndType(owner.getId(), 2).getTotalCount());
    create(owner);
    assertEquals(1, statistics.findByUserIdAndType(owner.getId(), 2).getTotalCount());
  }

  @Test
  void substitutedSourceAndLegacyClockAreRejectedWithoutMutatingResults() {
    UserEntity owner = actor();
    String id = create(owner);
    service.begin(owner.getToken(), id);
    given().headers("token", owner.getToken(), "deviceId", owner.getDeviceId()).contentType(ContentType.JSON)
        .body(Map.of("id", id, "content", ANSWER.replace("5678", "0000")))
        .post(BASE + "finish").then().body("code", is(202));
    assertEquals(1, trains.findById(id).getStatus());
    assertEquals(SOURCE, trains.findById(id).getContent());
    QuarkusTransaction.requiringNew().run(() -> trains.findById(id).setProtocolVersion(null));
    given().headers("token", owner.getToken(), "deviceId", owner.getDeviceId()).contentType(ContentType.JSON)
        .body(Map.of("id", id, "content", ANSWER)).post(BASE + "finish").then().body("code", is(208));
    assertEquals(1, trains.findById(id).getStatus());
  }

  private UserEntity actor() {
    return Fixtures.user(users, "comprehensive-" + UUID.randomUUID(), "device-" + UUID.randomUUID());
  }

  private String create(UserEntity owner) {
    return service.save(owner.getToken(), new TelegraphKeyPatSyntheticalDto()
        .setTitle("runtime-test").setMessageType(0).setContent(SOURCE)).getId();
  }
}
