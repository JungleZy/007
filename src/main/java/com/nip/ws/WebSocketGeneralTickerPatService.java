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
  public void onOpen(@PathParam("uid") String uid, @PathParam(TRAIN_ID) Integer trainId, @PathParam("role") Integer role, Session session) {
    log.info("用户：{}，进入房间", uid);
    GeneralTickerPatTrainUserModel userModel = new GeneralTickerPatTrainUserModel();
    userModel.setSession(session);
    userModel.setStatus(1);
    userModel.setId(uid);
    userModel.setRole(role);
    GeneralTickerPatTrainRoomUserModel roomUser =
        PAT_ROOM.computeIfAbsent(trainId, k -> new GeneralTickerPatTrainRoomUserModel());
    Map<String, Object> msg = new HashMap<>();
    msg.put(TOPIC, ONLINE);
    msg.put(ID, uid);
    if (userModel.getRole().compareTo(0) == 0) { //学员
      roomUser.getJoinUser().add(userModel);
      //通知教员
      if (roomUser.getGroupUser() != null) {
        boolean b = sendMessage(roomUser.getGroupUser().getSession(), JSONUtils.toJson(msg), "", "");
        if (!b) {
          log.error("学员：{},进入房间，通知教员失败,已清空教员", uid);
          roomUser.setGroupUser(null);
        }
        log.info("手键拍发学员uid:{},进入房间", uid);
      }
    } else { //学员 教员
      log.info("手键拍发老师uid:{},进入房间", uid);
      roomUser.setGroupUser(userModel);
      List<GeneralTickerPatTrainUserModel> failSession = new ArrayList<>();
      roomUser.getJoinUser().forEach(item -> {
        boolean sendResult = sendMessage(item.getSession(), JSONUtils.toJson(msg), "", "");
        if (!sendResult) {
          failSession.add(item);
        }
      });
      //移除异常socket
      for (GeneralTickerPatTrainUserModel model : failSession) {
        roomUser.getJoinUser().remove(model);
      }
    }
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
  public void onClose(@PathParam("uid") String uid, @PathParam(TRAIN_ID) Integer trainId) {
    Map<String, Object> msg = new HashMap<>();
    msg.put(BaseConstants.TOPIC, OFFLINE);
    msg.put(ID, uid);
    GeneralTickerPatTrainRoomUserModel roomUser = PAT_ROOM.get(trainId);
    if (roomUser == null) {
      return;
    }
    String groupId = Optional.ofNullable(roomUser.getGroupUser())
        .map(GeneralTickerPatTrainUserModel::getId)
        .orElse("n_id");

    if (Objects.equals(groupId, uid)) {
      roomUser.getJoinUser().forEach(item -> sendMessage(item.getSession(), JSONUtils.toJson(msg), "", ""));
      if (roomUser.getGroupUser() != null) {
//        log.info("手键拍发教员：{} 退出", roomUser.getGroupUser().getId());
      }
      roomUser.setGroupUser(null);
    } else {
      GeneralTickerPatTrainUserModel remove = null;
      for (GeneralTickerPatTrainUserModel userModel : roomUser.getJoinUser()) {
        if (Objects.equals(userModel.getId(), uid)) {
//          log.info("手键拍发学员：{} 退出", userModel.getId());
          remove = userModel;
          break;
        }
      }
      if (roomUser.getGroupUser() != null) {
        sendMessage(roomUser.getGroupUser().getSession(), JSONUtils.toJson(msg), "", "");
      }
      roomUser.getJoinUser().remove(remove);
    }
    //原子清房：与并发 onOpen 的 computeIfAbsent 互斥，避免删掉刚建的房间（P1-6）
    PAT_ROOM.computeIfPresent(trainId, (k, v) -> (v.getJoinUser().isEmpty() && v.getGroupUser() == null) ? null : v);
//    log.info("房间信息：{}", PAT_ROOM);
  }

  @OnError
  public void onError(@PathParam("uid") String uid, @PathParam(TRAIN_ID) Integer trainId, Session session, Throwable t) {
    log.error("ws error, session={}", session.getId(), t);
    //复用 onClose 清理该 session 对应的房间状态，并关闭连接
    onClose(uid, trainId);
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
