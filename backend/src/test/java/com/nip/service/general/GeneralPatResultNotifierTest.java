package com.nip.service.general;

import com.google.gson.reflect.TypeToken;
import com.nip.common.constants.CodeConstants;
import com.nip.common.utils.JSONUtils;
import com.nip.dao.UserDao;
import com.nip.entity.UserEntity;
import com.nip.testsupport.Fixtures;
import com.nip.testsupport.WebSocketSessionProbe;
import com.nip.ws.WebSocketService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GeneralPatResultNotifier} 的「事务后通知」契约。
 *
 * <p>断言对象一律是**接收方 WebSocket 实际收到的出站帧**：接收方按真实注册路径
 * （{@code WebSocketService.onOpen}）挂进端点的在线连接表，通知走生产的
 * {@code WebSocketService.sendInfo} 定向发送。因此这里守的是消费者可见的三件事：
 * 提交才送达、单个接收方失败不连坐、不在线的收件人被静默跳过。
 */
@QuarkusTest
class GeneralPatResultNotifierTest {

  @Inject
  GeneralPatResultNotifier notifier;
  @Inject
  UserDao userDao;
  @Inject
  WebSocketService webSocket;

  /**
   * 契约一：AFTER_SUCCESS 语义。事务提交前一帧都不能出去（在事务体内即求证），
   * 回滚的事务永远不发，提交的事务恰发一帧且码值与载荷正确。
   */
  @Test
  void resultFrameLeavesOnlyAfterTheTransactionCommits() {
    Recipient recipient = register();
    WebSocketSessionProbe probe = recipient.probe();
    try {
      assertThrows(IllegalStateException.class,
          () -> QuarkusTransaction.requiringNew().run(() -> {
            notifier.publish("ticker", 9001, "student-rolled-back", List.of(recipient.userId()));
            assertTrue(probe.outbound().isEmpty(), "事务尚未提交，接收方不得收到任何帧");
            throw new IllegalStateException("rollback");
          }));
      assertTrue(probe.outbound().isEmpty(),
          "回滚的事务不得发出结果通知，实际收到：" + probe.outbound());

      QuarkusTransaction.requiringNew().run(() -> {
        notifier.publish("ticker", 9002, "student-committed", List.of(recipient.userId()));
        assertTrue(probe.outbound().isEmpty(), "事务尚未提交，接收方不得收到任何帧");
      });

      assertEquals(1, probe.outbound().size(),
          "提交后接收方必须恰好收到一帧结果通知，实际收到：" + probe.outbound());
      Map<String, Object> frame = frame(probe.outbound().getFirst());
      assertEquals(CodeConstants.NOTIFICATION_TRAIN_RESULT.getCode(), intValue(frame.get("code")),
          "结果通知的业务码必须是 NOTIFICATION_TRAIN_RESULT");
      Map<String, Object> payload = payload(frame);
      assertEquals("ticker", payload.get("type"), "载荷必须带回发布时的训练类型");
      assertEquals("student-committed", payload.get("userId"), "载荷必须带回已提交那次的学员ID");
      assertEquals(9002, intValue(payload.get("trainId")), "载荷必须带回已提交那次的训练ID");
    } finally {
      unregister(recipient);
    }
  }

  /**
   * 契约二：接收方之间互不连坐。第一个接收方写出即抛，其后的接收方仍须收到完整帧，
   * 且异常不得反噬 publish 所在的事务。
   */
  @Test
  void oneBrokenRecipientDoesNotStopTheRest() {
    Recipient broken = register(true);
    Recipient healthy = register();
    try {
      assertDoesNotThrow(
          () -> QuarkusTransaction.requiringNew()
              .run(() -> notifier.publish("key", 9101, "student-fanout",
                  List.of(broken.userId(), healthy.userId()))),
          "单个接收方发送失败不得让 publish 所在的事务失败");

      assertTrue(broken.probe().outbound().isEmpty(),
          "写出即抛的接收方不可能留下成功帧，实际收到：" + broken.probe().outbound());
      assertEquals(1, healthy.probe().outbound().size(),
          "前一个接收方发送失败后，后面的接收方仍须恰好收到一帧，实际收到：" + healthy.probe().outbound());
      Map<String, Object> payload = payload(frame(healthy.probe().outbound().getFirst()));
      assertEquals("student-fanout", payload.get("userId"), "后续接收方收到的必须是同一条结果通知");
      assertEquals(9101, intValue(payload.get("trainId")), "后续接收方收到的必须是同一条结果通知");
    } finally {
      unregister(broken);
      unregister(healthy);
    }
  }

  /**
   * 契约三：收件人列表里的离线用户被静默跳过——不抛异常，也不影响在线用户收帧。
   */
  @Test
  void offlineRecipientIsSkippedWithoutAffectingOnlineRecipient() {
    String offline = "notifier-offline-" + UUID.randomUUID(); // 从未 onOpen，不在在线连接表里
    Recipient online = register();
    try {
      assertDoesNotThrow(
          () -> QuarkusTransaction.requiringNew()
              .run(() -> notifier.publish("telex", 9201, "student-partial",
                  List.of(offline, online.userId()))),
          "收件人不在线不得让 publish 所在的事务失败");

      assertEquals(1, online.probe().outbound().size(),
          "离线收件人必须被静默跳过，在线接收方仍须恰好收到一帧，实际收到：" + online.probe().outbound());
      Map<String, Object> payload = payload(frame(online.probe().outbound().getFirst()));
      assertEquals("student-partial", payload.get("userId"), "在线接收方收到的必须是本次发布的结果通知");
      assertEquals(9201, intValue(payload.get("trainId")), "在线接收方收到的必须是本次发布的结果通知");
    } finally {
      unregister(online);
    }
  }

  /** 一个已注册的收件人：在线表的键是该用户的真实 id（握手校验结果），不是路径参数。 */
  private record Recipient(String userId, WebSocketSessionProbe probe) {}

  private Recipient register() {
    return register(false);
  }

  /**
   * 按生产注册路径把桩连接挂进端点在线表。
   *
   * <p>握手鉴权（SEC-06）后 {@code onOpen} 不再接受路径 sid：连接必须带一对真实的
   * {@code token}/{@code deviceId}，在线表的键由校验出的用户 id 决定。
   */
  private Recipient register(boolean failOnSend) {
    String token = "notifier-" + UUID.randomUUID();
    String deviceId = "device-" + UUID.randomUUID();
    UserEntity user = Fixtures.user(userDao, token, deviceId);
    WebSocketSessionProbe probe = failOnSend
        ? WebSocketSessionProbe.failing(user.getId(), user.getToken(), deviceId)
        : WebSocketSessionProbe.open(user.getId(), user.getToken(), deviceId);
    webSocket.onOpen(probe.session());
    return new Recipient(user.getId(), probe);
  }

  /** 在线连接表是进程级 static，用例必须摘掉自己挂上的连接。 */
  private void unregister(Recipient recipient) {
    webSocket.onClose(recipient.probe().session());
  }

  private static Map<String, Object> frame(String raw) {
    return JSONUtils.fromJson(raw, new TypeToken<Map<String, Object>>() {
    });
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> payload(Map<String, Object> frame) {
    Object map = frame.get("map");
    assertTrue(map instanceof Map, "结果通知必须带 map 载荷，实际帧：" + frame);
    return (Map<String, Object>) map;
  }

  private static int intValue(Object jsonNumber) {
    assertTrue(jsonNumber instanceof Number, "期望数字字段，实际：" + jsonNumber);
    return ((Number) jsonNumber).intValue();
  }
}
