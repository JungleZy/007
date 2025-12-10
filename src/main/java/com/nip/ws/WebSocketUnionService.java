package com.nip.ws;

import cn.hutool.core.util.StrUtil;
import com.nip.common.constants.CodeConstants;
import com.nip.common.constants.UnionConstants;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.SnowflakeIdKit;
import com.nip.dao.UserDao;
import com.nip.entity.UserEntity;
import com.nip.ws.model.RequestModel;
import com.nip.ws.model.ResponseModel;
import com.nip.ws.model.RoomModel;
import com.nip.ws.model.UserModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static com.nip.common.constants.BaseConstants.TYPE;
import static com.nip.common.constants.BaseConstants.USER_ID;

/**
 * 联合训练
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2022-05-31 18:03:11
 */
@ServerEndpoint(value = "/websocketUnion/{sid}")
@ApplicationScoped
@Slf4j
public class WebSocketUnionService {

  @Inject
  private UserDao userDao;

  private static final ConcurrentHashMap<String, WebSocketUnionService> webSocketClientSet = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, RoomModel> onlineRooms = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, UserModel> onlineUsers = new ConcurrentHashMap<>();

  /**
   * 与某个客户端的连接会话，需要通过它来给客户端发送数据
   */
  private Session session;

  /**
   * 接收sid
   */
  private  UserModel sUser;

  /**
   * 连接建立成功调用的方法
   */
  @OnOpen
  public void onOpen(Session session, @PathParam("sid") String sid) throws IOException {
    if (webSocketClientSet.get(sid) != null) {
      webSocketClientSet.get(sid).sendMessage(
        new ResponseModel(CodeConstants.CLOSE.getCode(), CodeConstants.CLOSE.getContent()));
      webSocketClientSet.remove(sid);
      onlineUsers.remove(sid);
    }

    CompletableFuture.runAsync(()->{
      UserEntity userEntity = userDao.findUserEntityById(sid);
      UserModel userModel = new UserModel();
      userModel.setId(sid);
      userModel.setName(userEntity.getUserName());
      userModel.setUserImg(userEntity.getUserImg());
      this.session = session;
      this.sUser = userModel;
      webSocketClientSet.put(sid, this);
      onlineUsers.put(sid, userModel);
      log.info("有新客户端进入联合训练:" + sid + ",当前在线客户端数为:" + webSocketClientSet.size());
      userJoin();
    }).join();


  }

  /**
   * 连接关闭调用的方法
   */
  @OnClose
  public void onClose() {
    userExit();
  }

  /**
   * 收到客户端消息后调用的方法
   *
   * @param message 客户端发送过来的消息
   */
  @OnMessage
  public void onMessage(String message, Session session) {
    log.info("receive message :{}",message);
    Map map = JSONUtils.fromJson(message, Map.class);
    int code = new BigDecimal(map.get("code").toString()).intValue();
    RequestModel msg = new RequestModel();
    msg.setCode(code);
    msg.setReceiveUser(Optional.ofNullable(map.get("sendUser")).map(Object::toString).orElse(""));
    msg.setReceiveUser(Optional.ofNullable(map.get("receiveUser")).map(Objects::toString).orElse(""));
    msg.setData(Optional.ofNullable(map.get("data")).map(JSONUtils::toJson).orElse(""));
    UnionConstants byCode = UnionConstants.getByCode(code);
    switch (byCode) {
      case GET_UNION_INFO:
        getUnionInfo(session);
        break;
      case GET_ROOM_INFO:
        getRoomInfo(msg);
        break;
      case ADD_ROOM:
        addRoom(msg);
        break;
      case UPDATE_ROOM:
        ur(msg);
        break;
      case UPDATE_ROOM_USER:
        updateRoomUser(msg);
        break;
      case REMOVE_ROOM:
        removeRoom(msg);
        break;
      case JOIN_ROOM:
        joinRoom(msg);
        break;
      case EXIT_ROOM:
        exitRoom(msg);
        break;
      case ROOM_MESSAGE:
        roomMessage(msg);
        break;
      case SEAT_INSPECT:
        seatInspect(msg);
        break;
      case SEAT_INSPECT_REPLY:
        seatInspectReply(msg);
        break;
      case ROOM_STATUS_CHANGE:
        roomStatusChange(msg);
        break;
      default:
        break;
    }
  }

  /**
   * 用户加入
   */
  private void userJoin() {
    webSocketClientSet.forEach((s, webSocketUnionService) -> {
      if (!Objects.equals(s, this.sUser.getId())) {
        webSocketUnionService.sendMessage(
          new ResponseModel(UnionConstants.USER_JOIN.getCode(), JSONUtils.toJson(this.sUser)));
      }
    });
  }

  /**
   * 用户退出
   */
  private void userExit() {
    for (RoomModel roomModel : onlineRooms.values()) {
      boolean b = roomModel.getUsers().removeIf(userModel -> userModel.getId().equals(this.sUser.getId()));
      if (b) {
        updateRoom(roomModel);
        Map jsonObject = new HashMap<>();
        jsonObject.put(TYPE, "exit");
        jsonObject.put("user", this.sUser);
        roomModel.getUsers().forEach(user -> {
          webSocketClientSet.get(user.getId()).sendMessage(
            new ResponseModel(UnionConstants.ROOM_USER_BROADCAST.getCode(), JSONUtils.toJson(jsonObject)));
        });
      }
    }
    webSocketClientSet.remove(this.sUser.getId());
    onlineUsers.remove(this.sUser.getId());
    log.info("有客户端退出联合训练:" + this.sUser.getId() + ",当前在线客户端数为：" + onlineUsers.size());
    webSocketClientSet.forEach((s, webSocketUnionService) -> {
      webSocketUnionService.sendMessage(
        new ResponseModel(UnionConstants.USER_EXIT.getCode(), JSONUtils.toJson(this.sUser)));
    });
  }

  /**
   * 获取全部用户信息和房间信息
   */
  private void getUnionInfo(Session session) {
    try {
      session.getAsyncRemote().sendText(JSONUtils.toJson(new ResponseModel(UnionConstants.USER_LIST.getCode(),
              JSONUtils.toJson(new ArrayList<>(onlineUsers.values()))
      )));
      session.getAsyncRemote().sendText(JSONUtils.toJson(new ResponseModel(UnionConstants.ROOM_LIST.getCode(),
              JSONUtils.toJson(
                      new ArrayList<>(onlineRooms.values()))
      )));
    } catch (Exception e) {
      e.printStackTrace();
    }

    //切换成工作线程
    CompletableFuture.runAsync(()->{

    }).join();
  }

  private void getRoomInfo(RequestModel msg) {
    RoomModel roomModel = onlineRooms.get(msg.getData());
    session.getAsyncRemote().sendText(
            JSONUtils.toJson(new ResponseModel(UnionConstants.GET_ROOM_INFO.getCode(), JSONUtils.toJson(roomModel))));
  }

  /**
   * 新建房间
   *
   * @param msg
   */
  private void addRoom(RequestModel msg) {
    try {
      RoomModel room = JSONUtils.fromJson(msg.getData(), RoomModel.class);
      room.setId(StrUtil.toString(SnowflakeIdKit.getInstance().nextId()));
      room.setAdmin(this.sUser.getId());
      UserModel userModel = new UserModel();
      userModel.setId(this.sUser.getId());
      userModel.setName(this.sUser.getName());
      userModel.setUserImg(this.sUser.getUserImg());
      List<UserModel> user = new ArrayList<>();
      user.add(userModel);
      room.setUsers(user);
      onlineRooms.put(room.getId(), room);
      sendInfo(session, new ResponseModel(UnionConstants.ADD_ROOM_SUCCESS.getCode(), JSONUtils.toJson(room)));
      updateRoom(room);
    } catch (Exception e) {
      sendInfo(session, new ResponseModel(UnionConstants.ADD_ROOM_FAIL.getCode()));
    }
  }

  private void ur(RequestModel msg) {
    try {
      RoomModel newRoom = JSONUtils.fromJson(msg.getData(), RoomModel.class);
      RoomModel roomModel = onlineRooms.get(newRoom.getId());
      roomModel.setName(newRoom.getName());
      roomModel.setNnt(newRoom.getNnt());
      roomModel.setPassword(newRoom.getPassword());
      roomModel.setType(newRoom.getType());
      updateRoom(roomModel);
    } catch (Exception e) {
      sendInfo(session, new ResponseModel(UnionConstants.ADD_ROOM_FAIL.getCode()));
    }
  }

  private void updateRoomUser(RequestModel msg) {
    String roomId = msg.getSendUser();
    String usrId = msg.getReceiveUser();
    String type = msg.getData();
    RoomModel room = onlineRooms.get(roomId);
    for (UserModel user : room.getUsers()) {
      if (user.getId().equals(usrId)) {
        user.setType(Integer.parseInt(type));
        break;
      }
    }
    room.getUsers().forEach(user -> {
      sendInfo(user.getId(),
               new ResponseModel(UnionConstants.UPDATE_ROOM_USER_BROADCAST.getCode(), JSONUtils.toJson(room))
      );
    });

  }

  /**
   * 更新房间信息
   */
  private void updateRoom(RoomModel roomModel) {
    webSocketClientSet.forEach((s, webSocketUnionService) -> {
      webSocketUnionService.sendMessage(
        new ResponseModel(UnionConstants.UPDATE_ROOM_INFO.getCode(), JSONUtils.toJson(roomModel)));
    });
  }

  /**
   * 解散房间
   *
   * 此方法用于解散一个房间，将房间从在线房间列表中移除，并通知房间内的所有用户
   * 它首先获取房间模型，然后从在线房间字典中移除该房间，接着通知房间内的所有用户，
   * 最后广播更新在线房间列表
   *
   * @param msg 请求模型，包含要解散的房间的数据
   */
  private void removeRoom(RequestModel msg) {
    RoomModel roomModel = onlineRooms.get(msg.getData());
    onlineRooms.remove(msg.getData());
    List<UserModel> users = roomModel.getUsers();
    users.forEach(user -> {
      WebSocketUnionService webSocketUnionService = webSocketClientSet.get(user.getId());

      sendInfo(webSocketUnionService.session,
               new ResponseModel(UnionConstants.REMOVE_ROOM_BROADCAST.getCode(), JSONUtils.toJson(roomModel))
      );

    });
    sendOnlineRooms();
  }

  /**
   * 加入房间
   *
   * 此方法用于处理用户加入房间的请求它首先检查用户是否已经在一个房间中，
   * 如果没有，则创建用户模型并将其添加到房间的用户列表中，然后通知所有房间内的其他用户
   *
   * @param msg 包含加入房间请求信息的模型
   */
  private void joinRoom(RequestModel msg) {
    try {
      RoomModel roomModel = onlineRooms.get(msg.getData());
      AtomicReference<Integer> isIn = new AtomicReference<>(0);
      roomModel.getUsers().forEach(userModel -> {
        if (Objects.equals(userModel.getId(), this.sUser.getId())) {
          isIn.set(1);
        }
      });
      if (0 == isIn.get()) {
        UserModel um = new UserModel();
        um.setId(this.sUser.getId());
        um.setName(this.sUser.getName());
        um.setUserImg(this.sUser.getUserImg());
        roomModel.getUsers().add(um);
        sendInfo(session, new ResponseModel(UnionConstants.JOIN_ROOM_SUCCESS.getCode(), JSONUtils.toJson(roomModel)));
        updateRoom(roomModel);
        Map jsonObject = new HashMap<>();
        jsonObject.put(TYPE, "join");
        jsonObject.put("user", um);
        roomModel.getUsers().forEach(user -> {
          if (!Objects.equals(user.getId(), um.getId())) {
            webSocketClientSet.get(user.getId()).sendMessage(
              new ResponseModel(UnionConstants.ROOM_USER_BROADCAST.getCode(), JSONUtils.toJson(jsonObject)));
          }
        });
      }
    } catch (Exception e) {
      e.printStackTrace();
      sendInfo(session,
               new ResponseModel(UnionConstants.JOIN_ROOM_FAIL.getCode(), UnionConstants.JOIN_ROOM_FAIL.getContent())
      );
    }
  }

  /**
   * 退出房间
   *
   * 此方法允许当前用户退出指定的房间它通过移除房间中的用户列表来实现，
   * 并通知房间内的其他用户该用户已退出
   *
   * @param msg 包含退出房间所需信息的请求模型，包括房间ID等
   */
  private void exitRoom(RequestModel msg) {
    RoomModel roomModel = onlineRooms.get(msg.getData());
    if (roomModel != null) {
      List<UserModel> users = roomModel.getUsers();
      AtomicReference<UserModel> um = new AtomicReference<>(new UserModel());
      users.removeIf(r -> {
        if (r.getId().equals(this.sUser.getId())) {
          um.set(r);
          return true;
        }
        return false;
      });
      updateRoom(roomModel);
      Map jsonObject = new HashMap<>();
      jsonObject.put(TYPE, "exit");
      jsonObject.put("user", um.get());
      users.forEach(user -> webSocketClientSet.get(user.getId()).sendMessage(
        new ResponseModel(UnionConstants.ROOM_USER_BROADCAST.getCode(), JSONUtils.toJson(jsonObject))));
    }
  }

  /**
   * 处理房间消息
   * 当接收到消息时，该方法会将消息发送给房间内的所有用户，除了发送者本身
   *
   * @param msg 消息对象，包含发送者ID，接收者ID，以及消息数据
   */
  private void roomMessage(RequestModel msg) {
    if (StringUtils.isNotEmpty(msg.getReceiveUser())) {
      Map<String,String> dataMap = new HashMap<>();
      dataMap.put("data",msg.getData());
      UserEntity userEntity = userDao.findUserEntityById(msg.getSendUser());
      String userName = userEntity.getUserName();
      dataMap.put("userName",userName);
      dataMap.put("userImg",userEntity.getUserImg());
      String data = JSONUtils.toJson(dataMap);
      onlineRooms.get(msg.getReceiveUser()).getUsers().forEach(user -> {
        if (!user.getId().equals(msg.getSendUser())) {
          sendInfo(user.getId(), new ResponseModel(UnionConstants.ROOM_MESSAGE.getCode(),msg.getSendUser(),msg.getReceiveUser(),data));

        }
      });
    }
  }

  /**
   * 发起席位状态检测
   *
   * 本函数主要用于更新房间内用户的席位状态根据接收到的消息数据更新房间状态，
   * 并重置所有用户的状态，最后通过WebSocket通知所有用户更新后的房间信息
   *
   * @param msg 包含更新信息的请求模型，包括发送用户和数据
   */
  private void seatInspect(RequestModel msg) {
    RoomModel roomModel = onlineRooms.get(msg.getSendUser());
    roomModel.setStatus(Integer.parseInt(msg.getData()));
    roomModel.getUsers().forEach(userModel -> userModel.setStatus(0));
    roomModel.getUsers().forEach(userModel -> {
      webSocketClientSet.get(userModel.getId()).sendMessage(
        new ResponseModel(UnionConstants.UPDATE_ROOM_USER_BROADCAST.getCode(), JSONUtils.toJson(roomModel)));
      webSocketClientSet.get(userModel.getId())
                        .sendMessage(new ResponseModel(UnionConstants.SEAT_INSPECT_ACCEPT.getCode(), msg.getData()));
    });
  }

  /**
   * 席位状态回执
   * SEAT_INSPECT_REPLY
   * 该方法用于处理席位状态的回执消息，根据消息更新用户状态，并广播给房间内所有用户
   *
   * @param msg 请求模型，包含发送用户、接收用户和状态数据
   */
  private void seatInspectReply(RequestModel msg) {
    RoomModel roomModel = onlineRooms.get(msg.getReceiveUser());
    String sendUser = msg.getSendUser();
    roomModel.getUsers().forEach(userModel -> {
      if (userModel.getId().equals(sendUser)) {
        userModel.setStatus(Integer.parseInt(msg.getData()));
      }
      Map<String,String> jsonObject = new HashMap<>();
      jsonObject.put(USER_ID, sendUser);
      jsonObject.put("status", msg.getData());
      webSocketClientSet.get(userModel.getId()).sendMessage(
        new ResponseModel(UnionConstants.SEAT_INSPECT_BROADCAST.getCode(), JSONUtils.toJson(jsonObject)));
    });
  }

  /**
   * 改变房间状态
   * 当收到改变房间状态的请求时，根据请求信息更新房间的状态
   *
   * @param msg 请求模型，包含改变状态所需的信息，如接收用户和新状态数据
   */
  private void roomStatusChange(RequestModel msg) {
    RoomModel roomModel = onlineRooms.get(msg.getReceiveUser());
    roomModel.setStatus(Integer.valueOf(msg.getData()));
    updateRoom(roomModel);
  }

  /**
   * 当WebSocket会话中发生错误时调用的方法
   * 该方法用于处理会话中的异常情况，确保错误被记录，并且可以进一步处理或通知相关人员
   *
   * @param session WebSocket会话对象，代表与客户端的连接在发生错误时的状态
   * @param error 发生的错误对象，提供了错误的详细信息，可用于调试或错误追踪
   */
  @OnError
  public void onError(Session session, Throwable error) {
    log.error("发生错误");
  }

  /**
   * 发送消息的方法
   * 该方法将给定的消息对象转换为JSON字符串并发送
   * 主要用途是封装消息的发送过程，使得发送消息更为简洁和一致
   *
   * @param message 要发送的消息对象，包含了需要发送的信息
   */
  public void sendMessage(ResponseModel message) {
    sendMessage(JSONUtils.toJson(message));
  }

  /**
   * 发送消息到WebSocket连接的另一端
   *
   * 此方法尝试将给定的字符串消息发送到通过当前session建立的WebSocket连接的另一端
   * 如果在发送过程中发生任何异常，它将捕获并记录错误信息
   *
   * @param message 要发送的字符串消息，不应为null
   */
  public void sendMessage(String message) {
    try {
      this.session.getBasicRemote().sendText(message);
    } catch (Exception e) {
      log.error("WebSocketUnionService.sendMessage:{}", e.getMessage());
    }
  }

  /**
   * 向所有在线的WebSocket客户端发送当前在线的房间列表
   * 此方法遍历所有WebSocket客户端，并发送一个包含当前所有在线房间信息的消息
   */
  private void sendOnlineRooms() {
    webSocketClientSet.forEach((s, webSocketUnionService) -> {
      webSocketUnionService.sendMessage(new ResponseModel(UnionConstants.ROOM_LIST.getCode(),
          JSONUtils.toJson(new ArrayList<>(onlineRooms.values()))
      ));
    });
  }

  /**
   * 群发自定义消息
   * 此方法用于向特定用户群发自定义消息它通过用户ID（sid）来识别目标用户集，
   * 并向这些用户发送一条消息
   *
   * @param sid 用户ID，用于识别目标用户群
   * @param message 要发送的消息内容，包含具体的消息信息
   */
  public static void sendInfo(@PathParam("sid") String sid, ResponseModel message) {
    webSocketClientSet.get(sid).sendMessage(message);
  }

  public static void sendInfo(Session session, ResponseModel message) {
    session.getAsyncRemote().sendText(JSONUtils.toJson(message));
  }
}
