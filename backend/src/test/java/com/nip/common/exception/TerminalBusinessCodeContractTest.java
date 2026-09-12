package com.nip.common.exception;

import com.nip.common.constants.PostTelexPatTrainStatusEnum;
import com.nip.dao.PostTelexPatTrainDao;
import com.nip.dao.PostTelexPatTrainPageValueDao;
import com.nip.dao.UserDao;
import com.nip.entity.PostTelexPatTrainEntity;
import com.nip.entity.UserEntity;
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
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 业务终态码的对外契约：目标已进入不可逆状态（训练已完成、旧轮次）时必须是
 * HTTP 200 + {@code code:208}，而不是与「参数不合法、改正后可重试」同码的 202。
 *
 * <p>这条是消费者可见的：前端 {@code common/mixin/useConfirmedSubmission.js} 按 {@code code}
 * 决定弹「重试」还是「不可重试」。两者混码时，界面会引导用户反复重试一个永远不会成功的提交。
 *
 * <p>同一个端点上同时断言 202 分界：页码越界仍是 202。缺了这条，把 202 一锐全改成 208
 * 也能让上面的用例通过，而那会让真正可修正的参数错误丢掉重试入口。
 */
@QuarkusTest
class TerminalBusinessCodeContractTest {
  @Inject UserDao userDao;
  @Inject PostTelexPatTrainDao trainDao;
  @Inject PostTelexPatTrainPageValueDao valueDao;

  @Test
  void finishedTrainingPageSubmitIsTerminalWhileBadPageNumberStaysRetryable() {
    UserEntity actor = Fixtures.user(userDao, "terminal-code-" + UUID.randomUUID(), "device-terminal");
    PostTelexPatTrainEntity train = finishedTrain(actor);

    // 已完成训练改页：重试必然再失败 -> 208，且不得落下任何页值行
    given()
        .contentType(ContentType.JSON)
        .header("token", actor.getToken())
        .header("deviceId", actor.getDeviceId())
        .body(page(train.getId(), 1, 0))
        .when()
        .post("/api/postTelexPatTrain/finishPage")
        .then()
        .statusCode(200)
        .body("code", is(208))
        .body("message", is("训练已完成，不能修改"));

    assertEquals(List.of(), valueDao.findAllByTrainId(train.getId()), "终态拒绝不得写入页值");

    // 旧轮次同样是终态：必须重新读取训练，重发旧轮次永远不会被接受
    given()
        .contentType(ContentType.JSON)
        .header("token", actor.getToken())
        .header("deviceId", actor.getDeviceId())
        .body(page(train.getId(), 1, 7))
        .when()
        .post("/api/postTelexPatTrain/finishPage")
        .then()
        .statusCode(200)
        .body("code", is(208));

    // 分界：轮次对得上、只是页码越界 —— 参数可修正，必须仍是 202
    given()
        .contentType(ContentType.JSON)
        .header("token", actor.getToken())
        .header("deviceId", actor.getDeviceId())
        .body(page(train.getId(), 9, 0))
        .when()
        .post("/api/postTelexPatTrain/finishPage")
        .then()
        .statusCode(200)
        .body("code", is(202))
        .body("message", is("页码不正确"));

    assertEquals(List.of(), valueDao.findAllByTrainId(train.getId()), "被拒的提交都不得写入页值");
  }

  private PostTelexPatTrainEntity finishedTrain(UserEntity owner) {
    PostTelexPatTrainEntity train = new PostTelexPatTrainEntity();
    train.setName("terminal-code-telex");
    train.setCreateUser(owner.getId());
    train.setTrainType(0);
    train.setIsCable(0);
    train.setGroupNumber(100);
    train.setProtocolVersion(1);
    train.setAttempt(0);
    train.setStatus(PostTelexPatTrainStatusEnum.FINISH.getStatus());
    return trainDao.saveAndFlush(train);
  }

  private Map<String, Object> page(String trainId, int pageNumber, int attempt) {
    return Map.of("trainId", trainId, "pageNumber", pageNumber, "attempt", attempt,
        "patValue", "1234 5678", "captureIntervals",
        List.of(Map.of("startedMs", 0, "endedMs", 4000)));
  }
}
