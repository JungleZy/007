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

  private static final String SID = "sid";

  @Inject
  private UserDao userDao;

  /**
   * 单连接持有者：端点是单例，连接态必须挂在每个连接自己的 holder 上
   */
  private record Client(Session session, UserModel user) {}

  private static final ConcurrentHashMap<String, Client> webSocketClientSet = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, RoomModel> onlineRooms = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, UserModel> onlineUsers = new ConcurrentHashMap<>();

  /**
   * 连接建立成功调用的方法
   */
  @OnOpen
  public void onOpen(Session session, @PathParam("sid") String sid) throws IOException {
    Client existing = webSocketClientSet.get(sid);
    if (existing != null) {
      send(existing.session(),
        new ResponseModel(CodeConstants.CLOSE.getCode(), CodeConstants.CLOSE.getContent()));
      webSocketClientSet.remove(sid);
      onlineUsers.remove(sid);
    }

    UserEntity userEntity = userDao.findUserEntityById(sid);
    UserModel userModel = new UserModel();
    userModel.setId(sid);
    userModel.setName(userEntity.getUserName());
    userModel.setUserImg(userEntity.getUserImg());
    session.getUserProperties().put(SID, sid);
    Client me = new Client(session, userModel);
    webSocketClientSet.put(sid, me);
    onlineUsers.put(sid, userModel);
    log.info("有新客户端进入联合训练:" + sid + ",当前在线客户端数为:" + webSocketClientSet.size());
    userJoin(me);
  }

  /**
   * 连接关闭调用的方法
   */
  @OnClose
  public void onClose(Session session) {
    Client me = resolveClient(session);
    if (me == null) {
      return;
    }
    userExit(me);
  }

  /**
   * 收到客户端消息后调用的方法
   *
   * @param message 客户端发送过来的消息
   */
  @OnMessage
  public void onMessage(String message, Session session) {
    log.info("receive message :{}", message);
    Client me = resolveClient(session);
    if (me == null) {
      log.warn("收到未注册连接的消息，忽略:{}", message);
      return;
    }
    Map map = JSONUtils.fromJson(message, Map.class);
    int code = new BigDecimal(map.get("code").toString()).intValue();
    RequestModel msg = new RequestModel();
    msg.setCode(code);
    msg.setSendUser(Optional.ofNullable(map.get("sendUser")).map(Object::toString).orElse(""));
    msg.setReceiveUser(Optional.ofNullable(map.get("receiveUser")).map(Objects::toString).orElse(""));
    msg.setData(Optional.ofNullable(map.get("data")).map(JSONUtils::toJson).orElse(""));
    UnionConstants byCode = UnionConstants.getByCode(code);
    switch (byCode) {
      case GET_UNION_INFO:
        getUnionInfo(me);
        break;
      case GET_ROOM_INFO:
        getRoomInfo(me, msg);
        break;
      case ADD_ROOM:
        addRoom(me, msg);
        break;
      case UPDATE_ROOM:
        ur(me, msg);
        break;
      case UPDATE_ROOM_USER:
        updateRoomUser(msg);
        break;
      case REMOVE_ROOM:
        removeRoom(msg);
        break;
      case JOIN_ROOM:
        joinRoom(me, msg);
        break;
      case EXIT_ROOM:
        exitRoom(me, msg);
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
   * 当WebSocket会话中发生错误时调用的方法
   *
   * @param session 发生错误的连接会话
   * @param error 发生的错误对象
   */
  @OnError
  public void onError(Session session, Throwable error) {
    log.error("联合训练连接发生错误", error);
    Client me = resolveClient(session);
    if (me == null) {
      return;
    }
    userExit(me);
  }

  /**
   * 身份管道：由连接会话反查该连接的 holder
   */
  private static Client resolveClient(Session session) {
    Object sid = session.getUserProperties().get(SID);
    if (sid == null) {
      return null;
    }
    return webSocketClientSet.get(sid.toString());
  }

  /**
   * 用户加入
   */
  private void userJoin(Client me) {
    webSocketClientSet.forEach((s, client) -> {
      if (!Objects.equals(s, me.user().getId())) {
        send(client.session(),
          new ResponseModel(UnionConstants.USER_JOIN.getCode(), JSONUtils.toJson(me.user())));
      }
    });
  }

  /**
   * 用户退出
   */
  private void userExit(Client me) {
    for (RoomModel roomModel : onlineRooms.values()) {
      boolean b = roomModel.getUsers().removeIf(userModel -> userModel.getId().equals(me.user().getId()));
      if (b) {
        updateRoom(roomModel);
        Map jsonObject = new HashMap<>();
        jsonObject.put(TYPE, "exit");
        jsonObject.put("user", me.user());
        roomModel.getUsers().forEach(user -> sendInfo(user.getId(),
          new ResponseModel(UnionConstants.ROOM_USER_BROADCAST.getCode(), JSONUtils.toJson(jsonObject))));
      }
    }
    webSocketClientSet.remove(me.user().getId());
    onlineUsers.remove(me.user().getId());
    log.info("有客户端退出联合训练:" + me.user().getId() + ",当前在线客户端数为：" + onlineUsers.size());
    webSocketClientSet.forEach((s, client) -> send(client.session(),
      new ResponseModel(UnionConstants.USER_EXIT.getCode(), JSONUtils.toJson(me.user()))));
  }

  /**
   * 获取全部用户信息和房间信息
   */
  private void getUnionInfo(Client me) {
    send(me.session(), new ResponseModel(UnionConstants.USER_LIST.getCode(),
      JSONUtils.toJson(new ArrayList<>(onlineUsers.values()))));
    send(me.session(), new ResponseModel(UnionConstants.ROOM_LIST.getCode(),
      JSONUtils.toJson(new ArrayList<>(onlineRooms.values()))));
  }

  private void getRoomInfo(Client me, RequestModel msg) {
    RoomModel roomModel = onlineRooms.get(msg.getData());
    send(me.session(),
      new ResponseModel(UnionConstants.GET_ROOM_INFO.getCode(), JSONUtils.toJson(roomModel)));
  }

  /**
   * 新建房间
   *
   * @param msg
   */
  private void addRoom(Client me, RequestModel msg) {
    try {
      RoomModel room = JSONUtils.fromJson(msg.getData(), RoomModel.class);
      room.setId(StrUtil.toString(SnowflakeIdKit.getInstance().nextId()));
      room.setAdmin(me.user().getId());
      UserModel userModel = new UserModel();
      userModel.setId(me.user().getId());
      userModel.setName(me.user().getName());
      userModel.setUserImg(me.user().getUserImg());
      List<UserModel> user = new ArrayList<>();
      user.add(userModel);
      room.setUsers(user);
      onlineRooms.put(room.getId(), room);
      send(me.session(), new ResponseModel(UnionConstants.ADD_ROOM_SUCCESS.getCode(), JSONUtils.toJson(room)));
      updateRoom(room);
    } catch (Exception e) {
      send(me.session(), new ResponseModel(UnionConstants.ADD_ROOM_FAIL.getCode()));
    }
  }

  private void ur(Client me, RequestModel msg) {
    try {
      RoomModel newRoom = JSONUtils.fromJson(msg.getData(), RoomModel.class);
      RoomModel roomModel = onlineRooms.get(newRoom.getId());
      roomModel.setName(newRoom.getName());
      roomModel.setNnt(newRoom.getNnt());
      roomModel.setPassword(newRoom.getPassword());
      roomModel.setType(newRoom.getType());
      updateRoom(roomModel);
    } catch (Exception e) {
      send(me.session(), new ResponseModel(UnionConstants.ADD_ROOM_FAIL.getCode()));
    }
  }

  private void updateRoomUser(RequestModel msg) {
    String roomId = msg.getSendUser();
    String usrId = msg.getReceiveUser();
    String type = msg.getData();
    RoomModel room = onlineRooms.get(roomId);
    if (room == null) {
      log.warn("更新房间用户信息失败，房间不存在:{}", roomId);
      return;
    }
    for (UserModel user : room.getUsers()) {
      if (user.getId().equals(usrId)) {
        user.setType(Integer.parseInt(type));
        break;
      }
    }
    room.getUsers().forEach(user -> sendInfo(user.getId(),
      new ResponseModel(UnionConstants.UPDATE_ROOM_USER_BROADCAST.getCode(), JSONUtils.toJson(room))));
  }

  /**
   * 更新房间信息
   */
  private void updateRoom(RoomModel roomModel) {
    webSocketClientSet.forEach((s, client) -> send(client.session(),
      new ResponseModel(UnionConstants.UPDATE_ROOM_INFO.getCode(), JSONUtils.toJson(roomModel))));
  }

  /**
   * 解散房间
   *
   * 此方法用于解散一个房间，将房间从在线房间列表中移除，并通知房间内的所有用户
   *
   * @param msg 请求模型，包含要解散的房间的数据
   */
  private void removeRoom(RequestModel msg) {
    RoomModel roomModel = onlineRooms.get(msg.getData());
    if (roomModel == null) {
      log.warn("解散房间失败，房间不存在:{}", msg.getData());
      return;
    }
    onlineRooms.remove(msg.getData());
    roomModel.getUsers().forEach(user -> sendInfo(user.getId(),
      new ResponseModel(UnionConstants.REMOVE_ROOM_BROADCAST.getCode(), JSONUtils.toJson(roomModel))));
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
  private void joinRoom(Client me, RequestModel msg) {
    try {
      RoomModel roomModel = onlineRooms.get(msg.getData());
      AtomicReference<Integer> isIn = new AtomicReference<>(0);
      roomModel.getUsers().forEach(userModel -> {
        if (Objects.equals(userModel.getId(), me.user().getId())) {
          isIn.set(1);
        }
      });
      if (0 == isIn.get()) {
        UserModel um = new UserModel();
        um.setId(me.user().getId());
        um.setName(me.user().getName());
        um.setUserImg(me.user().getUserImg());
        roomModel.getUsers().add(um);
        send(me.session(),
          new ResponseModel(UnionConstants.JOIN_ROOM_SUCCESS.getCode(), JSONUtils.toJson(roomModel)));
        updateRoom(roomModel);
        Map jsonObject = new HashMap<>();
        jsonObject.put(TYPE, "join");
        jsonObject.put("user", um);
        roomModel.getUsers().forEach(user -> {
          if (!Objects.equals(user.getId(), um.getId())) {
            sendInfo(user.getId(),
              new ResponseModel(UnionConstants.ROOM_USER_BROADCAST.getCode(), JSONUtils.toJson(jsonObject)));
          }
        });
      }
    } catch (Exception e) {
      log.error("加入房间失败", e);
      send(me.session(),
        new ResponseModel(UnionConstants.JOIN_ROOM_FAIL.getCode(), UnionConstants.JOIN_ROOM_FAIL.getContent()));
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
  private void exitRoom(Client me, RequestModel msg) {
    RoomModel roomModel = onlineRooms.get(msg.getData());
    if (roomModel != null) {
      List<UserModel> users = roomModel.getUsers();
      AtomicReference<UserModel> um = new AtomicReference<>(new UserModel());
      users.removeIf(r -> {
        if (r.getId().equals(me.user().getId())) {
          um.set(r);
          return true;
        }
        return false;
      });
      updateRoom(roomModel);
      Map jsonObject = new HashMap<>();
      jsonObject.put(TYPE, "exit");
      jsonObject.put("user", um.get());
      users.forEach(user -> sendInfo(user.getId(),
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
      RoomModel roomModel = onlineRooms.get(msg.getReceiveUser());
      if (roomModel == null) {
        log.warn("房间消息投递失败，房间不存在:{}", msg.getReceiveUser());
        return;
      }
      Map<String, String> dataMap = new HashMap<>();
      dataMap.put("data", msg.getData());
      UserEntity userEntity = userDao.findUserEntityById(msg.getSendUser());
      String userName = userEntity.getUserName();
      dataMap.put("userName", userName);
      dataMap.put("userImg", userEntity.getUserImg());
      String data = JSONUtils.toJson(dataMap);
      roomModel.getUsers().forEach(user -> {
        if (!user.getId().equals(msg.getSendUser())) {
          sendInfo(user.getId(),
            new ResponseModel(UnionConstants.ROOM_MESSAGE.getCode(), msg.getSendUser(), msg.getReceiveUser(), data));
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
    if (roomModel == null) {
      log.warn("席位状态检测失败，房间不存在:{}", msg.getSendUser());
      return;
    }
    roomModel.setStatus(Integer.parseInt(msg.getData()));
    roomModel.getUsers().forEach(userModel -> userModel.setStatus(0));
    roomModel.getUsers().forEach(userModel -> {
      sendInfo(userModel.getId(),
        new ResponseModel(UnionConstants.UPDATE_ROOM_USER_BROADCAST.getCode(), JSONUtils.toJson(roomModel)));
      sendInfo(userModel.getId(),
        new ResponseModel(UnionConstants.SEAT_INSPECT_ACCEPT.getCode(), msg.getData()));
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
    if (roomModel == null) {
      log.warn("席位状态回执失败，房间不存在:{}", msg.getReceiveUser());
      return;
    }
    String sendUser = msg.getSendUser();
    roomModel.getUsers().forEach(userModel -> {
      if (userModel.getId().equals(sendUser)) {
        userModel.setStatus(Integer.parseInt(msg.getData()));
      }
      Map<String, String> jsonObject = new HashMap<>();
      jsonObject.put(USER_ID, sendUser);
      jsonObject.put("status", msg.getData());
      sendInfo(userModel.getId(),
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
    if (roomModel == null) {
      log.warn("改变房间状态失败，房间不存在:{}", msg.getReceiveUser());
      return;
    }
    roomModel.setStatus(Integer.valueOf(msg.getData()));
    updateRoom(roomModel);
  }

  /**
   * 向所有在线的WebSocket客户端发送当前在线的房间列表
   */
  private void sendOnlineRooms() {
    webSocketClientSet.forEach((s, client) -> send(client.session(),
      new ResponseModel(UnionConstants.ROOM_LIST.getCode(),
        JSONUtils.toJson(new ArrayList<>(onlineRooms.values())))));
  }

  /**
   * 按用户ID发送消息；连接不在线时丢弃并告警，不再 NPE
   *
   * @param sid 目标用户ID
   * @param message 要发送的消息
   */
  public static void sendInfo(String sid, ResponseModel message) {
    Client client = webSocketClientSet.get(sid);
    if (client == null) {
      log.warn("目标连接不在线，消息丢弃:{}", sid);
      return;
    }
    send(client.session(), message);
  }

  /**
   * 出站发送统一入口
   *
   * @param session 目标连接会话
   * @param message 要发送的消息
   */
  private static void send(Session session, ResponseModel message) {
    if (session == null) {
      return;
    }
    try {
      session.getAsyncRemote().sendText(JSONUtils.toJson(message));
    } catch (Exception e) {
      log.error("WebSocketUnionService.send:{}", e.getMessage());
    }
  }
}
