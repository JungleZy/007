package com.nip.ws;

import com.google.gson.reflect.TypeToken;
import com.nip.common.constants.BaseConstants;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.PojoUtils;
import com.nip.dto.general.GeneralPatTrainRoomUserDto;
import com.nip.dto.general.GeneralPatTrainUserDto;
import com.nip.dto.general.GeneralPatTrainUserModelDto;
import com.nip.service.general.GeneralTelexPatService;
import com.nip.ws.model.SocketResponseModel;
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
import org.jose4j.json.internal.json_simple.JSONObject;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.nip.common.constants.BaseConstants.*;

@ServerEndpoint(value = "/generalTelexPatTrain/{uid}/{trainId}")
@ApplicationScoped
@Slf4j
@Tag(name = "综合组训-数据报组训-WS")
public class WebSocketGeneralTelexPatService {
  @Inject
  GeneralTelexPatService generalTelexPatService;
  public static final Map<String, GeneralPatTrainRoomUserDto> ROOM = new ConcurrentHashMap<>();

  /**
   * 打开连接
   *
   * @param uid     用户id
   * @param trainId 训练id
   * @param session 会话
   */
  @OnOpen
  public void onOpen(@PathParam("uid") String uid, @PathParam(TRAIN_ID) String trainId, Session session) {
    GeneralPatTrainUserDto userDto;
    try {
      userDto = generalTelexPatService.getTrainUserInfo(uid, trainId);
    } catch (Exception e) {
      log.error("WebSocketGeneralTelexPatService.onOpen: 用户不存在");
      sendErrMessage(session, e.getMessage(), "", "");
      close(session);
      return;
    }
    GeneralPatTrainUserModelDto userModel = PojoUtils.convertOne(userDto, GeneralPatTrainUserModelDto.class);
    userModel.setSession(session);
    userModel.setStatus(1);
    List<Session> replaced = new ArrayList<>();
    Map<String, String> data = new HashMap<>();
    data.put(ID, uid);
    data.put(TOPIC, BaseConstants.ONLINE);
    ROOM.compute(trainId, (key, existingRoom) -> {
      GeneralPatTrainRoomUserDto room = existingRoom == null
          ? new GeneralPatTrainRoomUserDto()
          : existingRoom;
      room.getJoinUser().removeIf(existing -> {
        if (!Objects.equals(existing.getId(), uid)) {
          return false;
        }
        replaced.add(existing.getSession());
        return true;
      });
      GeneralPatTrainUserModelDto group = room.getGroupUser();
      if (group != null && (Objects.equals(group.getId(), uid) || userModel.getRole().compareTo(1) == 0)) {
        replaced.add(group.getSession());
        room.setGroupUser(null);
      }
      if (userModel.getRole().compareTo(0) == 0) {
        room.getJoinUser().add(userModel);
        if (room.getGroupUser() != null) {
          sendMessage(room.getGroupUser().getSession(), JSONObject.toJSONString(data), "", "");
        }
      } else {
        room.setGroupUser(userModel);
        room.getJoinUser().forEach(user ->
            sendMessage(user.getSession(), JSONObject.toJSONString(data), "", ""));
      }
      return room;
    });
    closeReplaced(replaced, session);
  }

  @OnMessage
  public void onMessage(@PathParam("uid") String uid, @PathParam(TRAIN_ID) String trainId, String message, Session session) {
    GeneralPatTrainRoomUserDto trainRoomUser = ROOM.get(trainId);
    if (trainRoomUser == null) {
      sendErrMessage(session, "房间不存在", "", "");
      return;
    }
    Map<String, Object> msg = JSONUtils.fromJson(message, new TypeToken<>() {
    });
    if (Objects.equals("ready", msg.get(TOPIC).toString())) {
      //学员准备消息
      msg.put(ID, uid);
      //给教员发送消息
      if (trainRoomUser.getGroupUser() != null) {
        sendMessage(trainRoomUser.getGroupUser().getSession(), JSONObject.toJSONString(msg), "", "");
      }
      //将状态设置成已准备
      for (GeneralPatTrainUserModelDto userModel : trainRoomUser.getJoinUser()) {
        if (Objects.equals(userModel.getId(), uid)) {
          userModel.setStatus(2);
          break;
        }
      }
      return;
    } else if (Objects.equals("pat", msg.get(TOPIC).toString())) {
      //学员拍内容
      msg.put(ID, uid);
      //给教员发送消息
      if (trainRoomUser.getGroupUser() != null) {
        sendMessage(trainRoomUser.getGroupUser().getSession(), JSONObject.toJSONString(msg), "", "");
      }
      return;
    }
    //教员的消息需要给所有学员发送消息
    trainRoomUser.getJoinUser().forEach(userModel -> sendMessage(userModel.getSession(), message, "", ""));
  }

  @OnClose
  public void onClose(@PathParam("uid") String uid, @PathParam(TRAIN_ID) String trainId, Session session) {
    Map<String, String> data = new HashMap<>();
    data.put(TOPIC, OFFLINE);
    data.put(ID, uid);
    ROOM.computeIfPresent(trainId, (key, room) -> {
      GeneralPatTrainUserModelDto group = room.getGroupUser();
      if (group != null && sameConnection(group, uid, session)) {
        room.getJoinUser().forEach(item ->
            sendMessage(item.getSession(), JSONObject.toJSONString(data), "", ""));
        room.setGroupUser(null);
      } else {
        GeneralPatTrainUserModelDto current = room.getJoinUser().stream()
            .filter(user -> sameConnection(user, uid, session))
            .findFirst()
            .orElse(null);
        if (current != null) {
          if (group != null) {
            sendMessage(group.getSession(), JSONObject.toJSONString(data), "", "");
          }
          room.getJoinUser().remove(current);
        }
      }
      return room.getJoinUser().isEmpty() && room.getGroupUser() == null ? null : room;
    });
    close(session);
  }

  @OnError
  public void onError(@PathParam("uid") String uid, @PathParam(TRAIN_ID) String trainId, Session session, Throwable t) {
    log.error("ws error, session={}", session.getId(), t);
    //复用 onClose 清理该 session 对应的房间状态并关闭连接
    onClose(uid, trainId, session);
  }

  private static boolean sameConnection(GeneralPatTrainUserModelDto user, String uid, Session session) {
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

  /**
   * 广播发送统一入口：async remote 避免并发 basic 写抛 IllegalStateException；
   * catch Exception，单个接收方失败不中断循环
   */
  public static void sendMessage(Session session, String message, String sendName, String receiveName) {
    try {
      if (session.isOpen()) {
        session.getAsyncRemote().sendText(JSONUtils.toJson(SocketResponseModel.success(message, sendName, receiveName)));
      }
    } catch (Exception e) {
      log.error("WebSocketGeneralTelexPatService.sendMessage: 发送消息失败");
    }
  }

  /**
   * onOpen 拒接路径在 close 前调用：保持同步写确保错误帧先于关闭发出
   */
  public static void sendErrMessage(Session session, String message, String sendName, String receiveName) {
    try {
      if (session.isOpen()) {
        session.getBasicRemote().sendText(JSONUtils.toJson(SocketResponseModel.err(message, sendName, receiveName)));
      }
    } catch (Exception e) {
      log.error("WebSocketGeneralTelexPatService.sendErrMessage: 发送消息失败");
    }
  }

  private void close(Session session) {
    try {
      session.close();
    } catch (IOException e) {
      log.error("关闭socket出错", e);
    }
  }
}
