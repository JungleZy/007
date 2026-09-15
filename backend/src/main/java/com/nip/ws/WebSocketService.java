package com.nip.ws;

import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.nip.common.constants.CodeConstants;
import com.nip.common.constants.ResponseCode;
import com.nip.common.exception.ForbiddenException;
import com.nip.common.exception.TerminalStateException;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.PojoUtils;
import com.nip.entity.TelegramTrainFloorContentEntity;
import com.nip.entity.TelegramTrainLogEntity;
import com.nip.entity.UserEntity;
import com.nip.service.TelegramTrainService;
import com.nip.service.simulation.SimulationRouterRoomUserService;
import com.nip.ws.model.ResponseModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * WebSocketService
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2018-12-14 11:24
 */
@ServerEndpoint(value = "/websocket/{sid}")
@ApplicationScoped
@Slf4j
public class WebSocketService {

  @Inject
  private TelegramTrainService telegramTrainService;
  @Inject
  private SimulationRouterRoomUserService roomUserService;
  @Inject
  WebSocketHandshake handshake;

  /**
   * 在线连接表：sid -> 该连接的 Session。
   * 端点是 @ApplicationScoped 单例，连接态一律挂在本表上；
   * 禁止实例字段（共享字段恒为最后连接者身份，P1-9/P1-10 根因）。
   */
  private static final ConcurrentMap<String, Session> CLIENTS = new ConcurrentHashMap<>();

  /**
   * 连接建立成功调用的方法。
   *
   * <p>路径 {@code sid} 只作路由：注册键一律取 query 凭据的握手校验结果（SEC-06），
   * 与 {@link #sendInfo(String, ResponseModel)} 的寻址口径（用户 id）保持一致。
   */
  @OnOpen
  public void onOpen(Session session) {
    UserEntity authenticated = handshake.authenticate(session);
    if (authenticated == null) {
      send(session, JSONUtils.toJson(
          new ResponseModel(CodeConstants.CLOSE.getCode(), "登录凭据无效，拒绝建立连接")));
      close(session);
      return;
    }
    String sid = authenticated.getId();
    WebSocketHandshake.bind(session, sid);
    Session old = CLIENTS.put(sid, session);
    if (old != null && old != session) {
      //踢掉同 sid 的旧连接：通知后由客户端自行断开
      send(old, JSONUtils.toJson(new ResponseModel(CodeConstants.CLOSE.getCode(), CodeConstants.CLOSE.getContent())));
    }
    log.info("Client Join: {},Online Clients: {}", sid, CLIENTS.size());
  }

  /**
   * 连接关闭调用的方法
   */
  @OnClose
  public void onClose(Session session) {
    String sid = WebSocketHandshake.authenticatedId(session);
    if (sid == null) {
      //握手被拒的连接从未进入在线表
      return;
    }
    //条件移除：同 sid 重连后，旧 session 的 onClose 不得摘掉新连接
    CLIENTS.remove(sid, session);
    log.info("Client Leave: {}; Online Clients: {}", sid, CLIENTS.size());
  }

  /**
   * 收到客户端消息后调用的方法
   *
   * @param message 客户端发送过来的消息
   */
  @OnMessage
  public void onMessage(String message, Session session) {
    if (WebSocketHeartbeat.respond(session, message)) return;
    String actorId = WebSocketHandshake.authenticatedId(session);
    if (actorId == null || CLIENTS.get(actorId) != session) {
      send(session, JSONUtils.toJson(new ResponseModel(CodeConstants.CLOSE.getCode(), "当前连接已失效")));
      close(session);
      return;
    }
    try {
      Map<String, Object> model = JSONUtils.fromJson(message, new TypeToken<>() {});
      if (model == null || model.get("code") == null) throw new IllegalArgumentException("消息类型不能为空");
      switch (new BigDecimal(model.get("code").toString()).intValueExact()) {
        case 2001 -> telegramTrainService.saveCapturedLog(actorId,
            PojoUtils.convertOne(model.get("data"), TelegramTrainLogEntity.class));
        case 3001 -> telegramTrainService.saveCapturedFloorContent(actorId,
            PojoUtils.convertOne(model.get("data"), TelegramTrainFloorContentEntity.class));
        default -> { }
      }
    } catch (ForbiddenException failure) {
      rejectCapture(session, actorId, ResponseCode.CODE_207, failure);
    } catch (TerminalStateException failure) {
      rejectCapture(session, actorId, ResponseCode.CODE_208, failure);
    } catch (JsonParseException | IllegalArgumentException | ArithmeticException failure) {
      rejectCapture(session, actorId, ResponseCode.PARAMS_ERROR, failure);
    } catch (RuntimeException failure) {
      log.error("保存手键采集失败，actor={}", actorId, failure);
      send(session, JSONUtils.toJson(new ResponseModel(ResponseCode.SYSTEM_ERROR.getCode(), ResponseCode.SYSTEM_ERROR.getMessage())));
    }
  }

  private static void rejectCapture(Session session, String actorId, ResponseCode code, RuntimeException failure) {
    log.warn("拒绝手键采集，actor={}, code={}, reason={}", actorId, code.getCode(), failure.getMessage());
    send(session, JSONUtils.toJson(new ResponseModel(code.getCode(), failure.getMessage())));
  }

  /**
   * @param session
   * @param error
   */
  @OnError
  public void onError(Session session, Throwable error) {
    log.error("WebSocketService onError", error);
  }

  /**
   * 按 sid 定向发送
   */
  public static void sendInfo(String sid, ResponseModel message) {
    Session session = CLIENTS.get(sid);
    if (session != null) {
      send(session, JSONUtils.toJson(message));
    }
  }

  /**
   * 按 sid 定向发送（历史上忽略 sid 对全体广播，属越界推送，已收敛为定向）
   */
  public static void sendInfoAll(String sid, ResponseModel message) {
    sendInfo(sid, message);
  }

  public static void sendInfo(String sid, String message) {
    Session session = CLIENTS.get(sid);
    if (session != null) {
      send(session, message);
    }
  }

  /**
   * 出站发送统一入口：async remote（Undertow 内部排队，避免并发 basic 写抛
   * IllegalStateException）；catch Exception，单个接收方失败不得中断调用方
   */
  private static void send(Session session, String message) {
    try {
      if (session.isOpen()) {
        session.getAsyncRemote().sendText(message);
      }
    } catch (Exception e) {
      log.error("WebSocketService send", e);
    }
  }

  /**
   * 握手被拒时关闭连接：拒因帧已先行发出
   */
  private static void close(Session session) {
    try {
      session.close();
    } catch (IOException e) {
      log.error("关闭socket出错", e);
    }
  }
}
