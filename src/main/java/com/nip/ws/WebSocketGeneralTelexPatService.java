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
    //查询人员是否是组训人员
    GeneralPatTrainUserDto userDto = null;
    try {
      userDto = generalTelexPatService.getTrainUserInfo(uid, trainId);
    } catch (Exception e) {
      log.error("WebSocketGeneralTelexPatService.onOpen: 用户不存在");
      sendErrMessage(session, e.getMessage(), "", "");
      close(session);
      //P1-7：不 return 会带着全 null 的 userModel 继续执行到 getRole() NPE
      return;
    }
    GeneralPatTrainUserModelDto userModel = PojoUtils.convertOne(userDto, GeneralPatTrainUserModelDto.class);
    userModel.setSession(session);
    userModel.setStatus(1);
    GeneralPatTrainRoomUserDto roomUser = ROOM.computeIfAbsent(trainId, k -> new GeneralPatTrainRoomUserDto());
    Map<String, String> data = new HashMap<>();
    data.put(ID, uid);
    data.put(TOPIC, BaseConstants.ONLINE);
    if (userModel.getRole().compareTo(0) == 0) {
      roomUser.getJoinUser().add(userModel);
      //给教员推送消息
      if (roomUser.getGroupUser() != null) {
        sendMessage(roomUser.getGroupUser().getSession(), JSONObject.toJSONString(data), "", "");
      }
    } else {
      roomUser.setGroupUser(userModel);
      roomUser.getJoinUser().forEach(userModel1 -> sendMessage(userModel1.getSession(), JSONObject.toJSONString(data), "", ""));
    }
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
    GeneralPatTrainRoomUserDto keyPatTrainRoomUser = ROOM.get(trainId);
    if (keyPatTrainRoomUser == null) {
      close(session);
      return;
    }
    List<GeneralPatTrainUserModelDto> joinUser = keyPatTrainRoomUser.getJoinUser();
    Map<String, String> data = new HashMap<>();
    data.put(TOPIC, OFFLINE);
    data.put(ID, uid);
    GeneralPatTrainUserModelDto groupUser = keyPatTrainRoomUser.getGroupUser();
    //判断是否是组训人退出
    if (groupUser != null && Objects.equals(groupUser.getId(), uid)) {
      //给所有人发送退出消息
      joinUser.forEach(item -> sendMessage(item.getSession(), JSONObject.toJSONString(data), "", ""));
      keyPatTrainRoomUser.setGroupUser(null);
    } else {
      //学员退出：教员在线时通知教员；无论教员是否在线都必须移除（P1-8：原逻辑教员缺席时学员永不移除）
      if (groupUser != null) {
        sendMessage(groupUser.getSession(), JSONObject.toJSONString(data), "", "");
      }
      joinUser.removeIf(userModel -> Objects.equals(userModel.getId(), uid));
    }
    //房间清空后释放条目（原 ROOM 只增不减）
    ROOM.computeIfPresent(trainId, (k, v) -> (v.getJoinUser().isEmpty() && v.getGroupUser() == null) ? null : v);
    close(session);
  }

  @OnError
  public void onError(@PathParam("uid") String uid, @PathParam(TRAIN_ID) String trainId, Session session, Throwable t) {
    log.error("ws error, session={}", session.getId(), t);
    //复用 onClose 清理该 session 对应的房间状态并关闭连接
    onClose(uid, trainId, session);
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
