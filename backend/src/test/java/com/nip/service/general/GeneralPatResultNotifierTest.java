package com.nip.service.general;

import com.google.gson.reflect.TypeToken;
import com.nip.common.constants.CodeConstants;
import com.nip.common.utils.JSONUtils;
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

  /**
   * 契约一：AFTER_SUCCESS 语义。事务提交前一帧都不能出去（在事务体内即求证），
   * 回滚的事务永远不发，提交的事务恰发一帧且码值与载荷正确。
   */
  @Test
  void resultFrameLeavesOnlyAfterTheTransactionCommits() {
    String recipient = "notifier-commit-" + UUID.randomUUID();
    WebSocketSessionProbe probe = register(recipient);
    try {
      assertThrows(IllegalStateException.class,
          () -> QuarkusTransaction.requiringNew().run(() -> {
            notifier.publish("ticker", 9001, "student-rolled-back", List.of(recipient));
            assertTrue(probe.outbound().isEmpty(), "事务尚未提交，接收方不得收到任何帧");
            throw new IllegalStateException("rollback");
          }));
      assertTrue(probe.outbound().isEmpty(),
          "回滚的事务不得发出结果通知，实际收到：" + probe.outbound());

      QuarkusTransaction.requiringNew().run(() -> {
        notifier.publish("ticker", 9002, "student-committed", List.of(recipient));
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
      unregister(recipient, probe);
    }
  }

  /**
   * 契约二：接收方之间互不连坐。第一个接收方写出即抛，其后的接收方仍须收到完整帧，
   * 且异常不得反噬 publish 所在的事务。
   */
  @Test
  void oneBrokenRecipientDoesNotStopTheRest() {
    String broken = "notifier-broken-" + UUID.randomUUID();
    String healthy = "notifier-healthy-" + UUID.randomUUID();
    WebSocketSessionProbe brokenProbe = WebSocketSessionProbe.failing(broken);
    WebSocketSessionProbe healthyProbe = WebSocketSessionProbe.open(healthy);
    register(broken, brokenProbe);
    register(healthy, healthyProbe);
    try {
      assertDoesNotThrow(
          () -> QuarkusTransaction.requiringNew()
              .run(() -> notifier.publish("key", 9101, "student-fanout", List.of(broken, healthy))),
          "单个接收方发送失败不得让 publish 所在的事务失败");

      assertTrue(brokenProbe.outbound().isEmpty(),
          "写出即抛的接收方不可能留下成功帧，实际收到：" + brokenProbe.outbound());
      assertEquals(1, healthyProbe.outbound().size(),
          "前一个接收方发送失败后，后面的接收方仍须恰好收到一帧，实际收到：" + healthyProbe.outbound());
      Map<String, Object> payload = payload(frame(healthyProbe.outbound().getFirst()));
      assertEquals("student-fanout", payload.get("userId"), "后续接收方收到的必须是同一条结果通知");
      assertEquals(9101, intValue(payload.get("trainId")), "后续接收方收到的必须是同一条结果通知");
    } finally {
      unregister(broken, brokenProbe);
      unregister(healthy, healthyProbe);
    }
  }

  /**
   * 契约三：收件人列表里的离线用户被静默跳过——不抛异常，也不影响在线用户收帧。
   */
  @Test
  void offlineRecipientIsSkippedWithoutAffectingOnlineRecipient() {
    String offline = "notifier-offline-" + UUID.randomUUID(); // 从未 onOpen，不在在线连接表里
    String online = "notifier-online-" + UUID.randomUUID();
    WebSocketSessionProbe probe = register(online);
    try {
      assertDoesNotThrow(
          () -> QuarkusTransaction.requiringNew()
              .run(() -> notifier.publish("telex", 9201, "student-partial", List.of(offline, online))),
          "收件人不在线不得让 publish 所在的事务失败");

      assertEquals(1, probe.outbound().size(),
          "离线收件人必须被静默跳过，在线接收方仍须恰好收到一帧，实际收到：" + probe.outbound());
      Map<String, Object> payload = payload(frame(probe.outbound().getFirst()));
      assertEquals("student-partial", payload.get("userId"), "在线接收方收到的必须是本次发布的结果通知");
      assertEquals(9201, intValue(payload.get("trainId")), "在线接收方收到的必须是本次发布的结果通知");
    } finally {
      unregister(online, probe);
    }
  }

  /** 按生产注册路径把桩连接挂进端点在线表：sid 即收件人 userId。 */
  private static WebSocketSessionProbe register(String sid) {
    WebSocketSessionProbe probe = WebSocketSessionProbe.open(sid);
    register(sid, probe);
    return probe;
  }

  private static void register(String sid, WebSocketSessionProbe probe) {
    new WebSocketService().onOpen(probe.session(), sid);
  }

  /** 在线连接表是进程级 static，用例必须摘掉自己挂上的连接。 */
  private static void unregister(String sid, WebSocketSessionProbe probe) {
    new WebSocketService().onClose(sid, probe.session());
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
