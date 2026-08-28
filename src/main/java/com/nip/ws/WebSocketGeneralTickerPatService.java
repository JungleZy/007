package com.nip.ws;

import com.google.gson.reflect.TypeToken;
import com.nip.common.constants.BaseConstants;
import com.nip.common.utils.JSONUtils;
import com.nip.ws.model.GeneralTickerPatTrainRoomUserModel;
import com.nip.ws.model.GeneralTickerPatTrainUserModel;
import com.nip.ws.model.SocketResponseModel;
import jakarta.enterprise.context.ApplicationScoped;
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

import static com.nip.common.constants.BaseConstants.*;

@ServerEndpoint(value = "/generalTickerPat/{uid}/{trainId}/{role}")
@ApplicationScoped
@Slf4j
@Tag(name = "综合组训-手键组训-WS")
public class WebSocketGeneralTickerPatService {
  public static final Map<Integer, GeneralTickerPatTrainRoomUserModel> PAT_ROOM = new ConcurrentHashMap<>();

  @OnOpen
  public void onOpen(@PathParam("uid") String uid, @PathParam(TRAIN_ID) Integer trainId,
      @PathParam("role") Integer role, Session session) {
    log.info("用户：{}，进入房间", uid);
    GeneralTickerPatTrainUserModel userModel = new GeneralTickerPatTrainUserModel();
    userModel.setSession(session);
    userModel.setStatus(1);
    userModel.setId(uid);
    userModel.setRole(role);
    List<Session> replaced = new ArrayList<>();
    Map<String, Object> msg = new HashMap<>();
    msg.put(TOPIC, ONLINE);
    msg.put(ID, uid);
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
        if (room.getGroupUser() != null
            && !sendMessage(room.getGroupUser().getSession(), JSONUtils.toJson(msg), "", "")) {
          log.error("学员：{},进入房间，通知教员失败,已清空教员", uid);
          room.setGroupUser(null);
        }
        log.info("手键拍发学员uid:{},进入房间", uid);
      } else {
        log.info("手键拍发老师uid:{},进入房间", uid);
        room.setGroupUser(userModel);
        room.getJoinUser().removeIf(item ->
            !sendMessage(item.getSession(), JSONUtils.toJson(msg), "", ""));
      }
      return room;
    });
    closeReplaced(replaced, session);
  }

  @OnMessage
  public void onMessage(@PathParam("uid") String uid, @PathParam(TRAIN_ID) Integer trainId, String message, Session session) {
//    log.info("收到{}训练：{}的消息：{}", trainId, uid, message);
    GeneralTickerPatTrainRoomUserModel roomUser = PAT_ROOM.get(trainId);
    //房间可能已被 REST 删除（delete 只清 map 不关 session），判空短路
    if (roomUser == null) {
      sendErrMessage(session, "房间不存在", "", "");
      return;
    }
    Map<String, Object> msg = JSONUtils.fromJson(message, new TypeToken<>() {
    });
    String topic = msg.get(BaseConstants.TOPIC).toString();
    switch (topic) {
      case TRAIN_READY -> {
        //给老师推送
        for (GeneralTickerPatTrainUserModel userModel : roomUser.getJoinUser()) {
          if (Objects.equals(userModel.getId(), uid)) {
            userModel.setStatus(2);
          }
        }
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
        for (GeneralTickerPatTrainUserModel userModel : roomUser.getJoinUser()) {
          if (userModel.getId().equals(uid)) {
            userModel.setStatus(3);
            break;
          }
        }
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
  public void onClose(@PathParam("uid") String uid, @PathParam(TRAIN_ID) Integer trainId,
      Session session) {
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
  public void onError(@PathParam("uid") String uid, @PathParam(TRAIN_ID) Integer trainId,
      Session session, Throwable t) {
    log.error("ws error, session={}", session.getId(), t);
    onClose(uid, trainId, session);
    close(session);
  }

  private static boolean sameConnection(GeneralTickerPatTrainUserModel user, String uid,
      Session session) {
    return Objects.equals(user.getId(), uid)
        && (user.getSession() == session
        || Objects.equals(user.getSession().getId(), session.getId()));
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
