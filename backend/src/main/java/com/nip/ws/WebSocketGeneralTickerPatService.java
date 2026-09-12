package com.nip.ws;

import com.google.gson.reflect.TypeToken;
import com.nip.common.constants.BaseConstants;
import com.nip.common.utils.JSONUtils;
import com.nip.dto.general.GeneralPatTrainUserDto;
import com.nip.entity.UserEntity;
import com.nip.service.general.GeneralTickerPatService;
import com.nip.ws.model.GeneralTickerPatTrainRoomUserModel;
import com.nip.ws.model.GeneralTickerPatTrainUserModel;
import com.nip.ws.model.SocketResponseModel;
import com.nip.ws.service.RoomLifecycleLocks;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.locks.Lock;
import static com.nip.common.constants.BaseConstants.*;

@ServerEndpoint(value = "/generalTickerPat/{uid}/{trainId}/{role}")
@ApplicationScoped
@Slf4j
@Tag(name = "综合组训-手键组训-WS")
public class WebSocketGeneralTickerPatService {
  @Inject
  GeneralTickerPatService generalTickerPatService;
  @Inject
  WebSocketHandshake handshake;
  public static final Map<Integer, GeneralTickerPatTrainRoomUserModel> PAT_ROOM = new ConcurrentHashMap<>();

  private record OpenTransition(
      String error,
      List<Session> replaced,
      List<Session> recipients,
      String notification) {}

  /**
   * 打开连接。
   *
   * <p>路径 {@code uid} 只作路由：身份一律取 query 凭据的握手校验结果（SEC-06）；
   * {@code role} 仍与库里的角色比对（本域是唯一带 role 的端点）。
   *
   * <p>{@code trainId}/{@code role} 声明成 {@code String} 而不是 {@code Integer} 是**握手门禁的前提**：
   * 容器在调用本方法前做 {@code @PathParam} 类型转换，转换失败时 {@code @OnOpen} 与
   * {@code @OnError} 都不会被调用 —— 连接于是既没鉴权也没人关，被无限保持
   * （实测：{@code /generalTickerPat/1/not-a-number/1} 不带凭据也 OPEN-HELD）。
   * 所以先无条件收字符串、先鉴权，再自己解析。
   */
  @OnOpen
  public void onOpen(@PathParam(TRAIN_ID) String rawTrainId,
      @PathParam("role") String rawRole, Session session) {
    UserEntity authenticated = handshake.authenticate(session);
    if (authenticated == null) {
      sendErrMessage(session, "登录凭据无效，拒绝建立连接", "", "");
      close(session);
      return;
    }
    String uid = authenticated.getId();
    WebSocketHandshake.bind(session, uid);
    Integer trainId = number(rawTrainId);
    Integer role = number(rawRole);
    if (trainId == null || role == null) {
      sendErrMessage(session, "训练id或角色无效", "", "");
      close(session);
      return;
    }
    OpenTransition transition;
    Lock lock = RoomLifecycleLocks.generalTickerRoom(trainId);
    lock.lock();
    try {
      transition = openLocked(uid, trainId, role, session);
    } finally {
      lock.unlock();
    }
    if (transition.error() != null) {
      sendErrMessage(session, transition.error(), "", "");
      close(session);
      return;
    }
    List<Session> failed = new ArrayList<>();
    for (Session recipient : transition.recipients()) {
      if (!sendMessage(recipient, transition.notification(), "", "")) {
        failed.add(recipient);
      }
    }
    removeFailedRecipients(trainId, failed);
    closeReplaced(transition.replaced(), session);
  }

  private OpenTransition openLocked(String uid, Integer trainId, Integer role, Session session) {
    GeneralPatTrainUserDto user;
    try {
      user = generalTickerPatService.getTrainUserInfo(uid, trainId);
      if (!Objects.equals(user.getRole(), role)) {
        throw new IllegalArgumentException("训练数据异常");
      }
    } catch (Exception e) {
      return new OpenTransition(e.getMessage(), List.of(), List.of(), "");
    }
    log.info("用户：{}，进入房间", uid);
    GeneralTickerPatTrainUserModel userModel = new GeneralTickerPatTrainUserModel();
    userModel.setSession(session);
    userModel.setStatus(1);
    userModel.setId(uid);
    userModel.setRole(role);
    userModel.setUserName(user.getUserName());
    userModel.setUserImg(user.getUserImg());
    List<Session> replaced = new ArrayList<>();
    List<Session> recipients = new ArrayList<>();
    Map<String, Object> msg = new HashMap<>();
    msg.put(TOPIC, ONLINE);
    msg.put(ID, uid);
    String notification = JSONUtils.toJson(msg);
    PAT_ROOM.compute(trainId, (key, existingRoom) -> {
      GeneralTickerPatTrainRoomUserModel room = existingRoom == null
          ? new GeneralTickerPatTrainRoomUserModel()
          : existingRoom;
      room.getJoinUser().removeIf(existing -> {
        if (!Objects.equals(existing.getId(), uid)) {
          return false;
        }
        replaced.add(existing.getSession());
        return true;
      });
      GeneralTickerPatTrainUserModel group = room.getGroupUser();
      if (group != null && (Objects.equals(group.getId(), uid) || role.compareTo(1) == 0)) {
        replaced.add(group.getSession());
        room.setGroupUser(null);
      }
      if (role.compareTo(0) == 0) {
        room.getJoinUser().add(userModel);
        if (room.getGroupUser() != null) {
          recipients.add(room.getGroupUser().getSession());
        }
        log.info("手键拍发学员uid:{},进入房间", uid);
      } else {
        log.info("手键拍发老师uid:{},进入房间", uid);
        room.setGroupUser(userModel);
        room.getJoinUser().stream().map(GeneralTickerPatTrainUserModel::getSession).forEach(recipients::add);
      }
      return room;
    });
    return new OpenTransition(null, List.copyOf(replaced), List.copyOf(recipients), notification);
  }

  private void removeFailedRecipients(Integer trainId, List<Session> failed) {
    if (failed.isEmpty()) {
      return;
    }
    PAT_ROOM.computeIfPresent(trainId, (key, room) -> {
      if (room.getGroupUser() != null && failed.contains(room.getGroupUser().getSession())) {
        room.setGroupUser(null);
      }
      room.getJoinUser().removeIf(user -> failed.contains(user.getSession()));
      return room.getGroupUser() == null && room.getJoinUser().isEmpty() ? null : room;
    });
  }

  @OnMessage
  public void onMessage(@PathParam(TRAIN_ID) String rawTrainId, String message, Session session) {
    if (WebSocketHeartbeat.respond(session, message)) return;
    String uid = WebSocketHandshake.authenticatedId(session);
    if (uid == null) {
      return;
    }
    Integer trainId = number(rawTrainId);
    if (trainId == null) {
      return;
    }
    GeneralTickerPatTrainRoomUserModel roomUser = PAT_ROOM.get(trainId);
    //房间可能已被 REST 删除（delete 只清 map 不关 session），判空短路
    if (roomUser == null) {
      sendErrMessage(session, "房间不存在", "", "");
      return;
    }
    GeneralTickerPatTrainUserModel sender = currentConnection(roomUser, uid, session);
    if (sender == null) {
      return;
    }
    Map<String, Object> msg = JSONUtils.fromJson(message, new TypeToken<>() {
    });
    String topic = msg.get(BaseConstants.TOPIC).toString();
    switch (topic) {
      case TRAIN_READY -> {
        sender.setStatus(2);
        if (roomUser.getGroupUser() != null) {
          sendMessage(roomUser.getGroupUser().getSession(), message, "", "");
        }
//        log.info("完成给==教员==推送==准备==消息:{}", message);
      }
      case TRAIN_PAT -> {
        //给老师推送
        if (roomUser.getGroupUser() != null) {
          sendMessage(roomUser.getGroupUser().getSession(), message, "", "");
        }
      }
      case TRAIN_FINISH -> {
        sender.setStatus(3);
        //给老师推送
        if (roomUser.getGroupUser() != null) {
          sendMessage(roomUser.getGroupUser().getSession(), message, "", "");
//          log.info("完成给==教员==推送==学员完成拍发==消息:{}", message);
        }
      }
      case TRAIN_BEGIN -> {
        //给学员推送
        roomUser.getJoinUser().forEach(item -> {
          sendMessage(item.getSession(), message, "", "");
        });
//        log.info("完成给==学员==推送==开始训练==消息:{},学员人数：{}", message, roomUser.getJoinUser().size());
      }
      case TRAIN_END ->
        //给学员推送
          roomUser.getJoinUser().forEach(item -> {
            sendMessage(item.getSession(), message, "", "");
//            log.info("完成给==学员==推送==结束训练==消息:{},学员人数：{}", message, roomUser.getJoinUser().size());
          });
      case null, default -> {
        sendErrMessage(session, "未知主题", "", "");
//        log.error("手键拍发位置主体：{}", msg);
      }
    }
  }

  @OnClose
  public void onClose(@PathParam(TRAIN_ID) String rawTrainId, Session session) {
    String uid = WebSocketHandshake.authenticatedId(session);
    if (uid == null) {
      //握手被拒的连接从未注册进房间，容器已在关闭它，无状态可清
      return;
    }
    Integer trainId = number(rawTrainId);
    if (trainId == null) {
      close(session);
      return;
    }
    Map<String, Object> msg = new HashMap<>();
    msg.put(BaseConstants.TOPIC, OFFLINE);
    msg.put(ID, uid);
    PAT_ROOM.computeIfPresent(trainId, (key, room) -> {
      GeneralTickerPatTrainUserModel group = room.getGroupUser();
      if (group != null && sameConnection(group, uid, session)) {
        room.getJoinUser().forEach(item ->
            sendMessage(item.getSession(), JSONUtils.toJson(msg), "", ""));
        room.setGroupUser(null);
      } else {
        GeneralTickerPatTrainUserModel current = room.getJoinUser().stream()
            .filter(user -> sameConnection(user, uid, session))
            .findFirst()
            .orElse(null);
        if (current != null) {
          if (group != null) {
            sendMessage(group.getSession(), JSONUtils.toJson(msg), "", "");
          }
          room.getJoinUser().remove(current);
        }
      }
      return room.getJoinUser().isEmpty() && room.getGroupUser() == null ? null : room;
    });
  }

  @OnError
  public void onError(@PathParam(TRAIN_ID) String rawTrainId, Session session, Throwable t) {
    log.error("ws error, session={}", session.getId(), t);
    onClose(rawTrainId, session);
    close(session);
  }

  /**
   * 解析路径里的数字段（{@code trainId}/{@code role}）。
   *
   * <p>不做 {@code @PathParam Integer} 的容器转换 —— 转换失败会让整个 {@code @OnOpen}/
   * {@code @OnError} 不被调用，连接既不鉴权也不关闭（见 {@link #onOpen} 注释）。
   *
   * @return 解析结果；非数字时返回 {@code null}，由调用方按自己的阶段决定关闭还是忽略
   */
  private static Integer number(String raw) {
    try {
      return Integer.valueOf(raw);
    } catch (NumberFormatException malformed) {
      return null;
    }
  }

  private static boolean sameConnection(GeneralTickerPatTrainUserModel user, String uid,
      Session session) {
    return Objects.equals(user.getId(), uid)
        && (user.getSession() == session
        || Objects.equals(user.getSession().getId(), session.getId()));
  }

  private static GeneralTickerPatTrainUserModel currentConnection(
      GeneralTickerPatTrainRoomUserModel room,
      String uid,
      Session session) {
    GeneralTickerPatTrainUserModel group = room.getGroupUser();
    if (group != null && sameConnection(group, uid, session)) {
      return group;
    }
    return room.getJoinUser().stream()
        .filter(user -> sameConnection(user, uid, session))
        .findFirst()
        .orElse(null);
  }

  private void closeReplaced(List<Session> replaced, Session current) {
    replaced.stream()
        .filter(old -> old != null && old != current)
        .distinct()
        .forEach(this::close);
  }

  private void close(Session session) {
    try {
      session.close();
    } catch (IOException e) {
      log.error("关闭socket出错", e);
    }
  }

  public static void closeRoomSessions(GeneralTickerPatTrainRoomUserModel room) {
    if (room == null) {
      return;
    }
    List<Session> sessions = new ArrayList<>();
    if (room.getGroupUser() != null) {
      sessions.add(room.getGroupUser().getSession());
    }
    room.getJoinUser().stream().map(GeneralTickerPatTrainUserModel::getSession).forEach(sessions::add);
    sessions.stream().filter(Objects::nonNull).distinct().forEach(session -> {
      try {
        if (session.isOpen()) {
          session.close();
        }
      } catch (IOException e) {
        log.error("关闭socket出错", e);
      }
    });
  }

  /**
   * 广播发送统一入口：async remote 避免并发 basic 写抛 IllegalStateException；
   * 返回 false 表示连接已关闭或提交失败，调用方据此清理死会话
   */
  public static boolean sendMessage(Session session, String message, String sendName, String receiveName) {
    try {
      if (session.isOpen()) {
        session.getAsyncRemote().sendText(JSONUtils.toJson(SocketResponseModel.success(message, sendName, receiveName)));
        return true;
      } else {
        return false;
      }
    } catch (Exception e) {
      return false;
    }
  }

  public static void sendErrMessage(Session session, String message, String sendName, String receiveName) {
    try {
      if (session.isOpen()) {
        session.getAsyncRemote().sendText(JSONUtils.toJson(SocketResponseModel.err(message, sendName, receiveName)));
      }
    } catch (Exception e) {
      log.error("WebSocketGeneralTickerPatService.sendErrMessage: 发送消息失败");
    }
  }
}
