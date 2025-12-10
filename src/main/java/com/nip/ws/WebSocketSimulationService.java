package com.nip.ws;

import com.google.gson.reflect.TypeToken;
import com.nip.common.constants.BaseConstants;
import com.nip.common.constants.SimulationDisturdTopicEnum;
import com.nip.common.utils.JSONUtils;
import com.nip.dao.UserDao;
import com.nip.dao.simulation.SimulationRouterRoomDao;
import com.nip.dao.simulation.SimulationRouterRoomUserDao;
import com.nip.dto.SimulationRouterRoomUserSimpDto;
import com.nip.dto.vo.simulation.disturd.SimulationDisturdWebscoketBody;
import com.nip.dto.vo.simulation.disturd.SimulationDisturdWebscoketVO;
import com.nip.entity.UserEntity;
import com.nip.entity.simulation.router.SimulationRouterRoomEntity;
import com.nip.entity.simulation.router.SimulationRouterRoomUserEntity;
import com.nip.ws.model.SimulationResponseModel;
import com.nip.ws.model.SimulationUserModel;
import com.nip.ws.service.simulation.SimulationGlobal;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jose4j.json.internal.json_simple.JSONObject;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

import static com.nip.common.constants.BaseConstants.*;
import static com.nip.common.constants.SimulationDisturdTopicEnum.*;
import static com.nip.common.constants.SimulationRoomTypeEnum.*;

@ServerEndpoint(value = "/simulation/{id}/{roomId}")
@ApplicationScoped
@Data
@Slf4j
@RegisterForReflection
public class WebSocketSimulationService {
  @Inject
  SimulationRouterRoomDao roomDao;
  @Inject
  UserDao userDao;
  @Inject
  SimulationRouterRoomUserDao roomUserDao;
  private Session session;
  private SimulationUserModel userModel;

  /**
   * @param session 会话
   * @param id      用户id
   */
  @OnOpen
  public void onOpen(Session session, @PathParam(ID) String id, @PathParam(ROOM_ID) Integer roomId) throws IOException {
    SimulationRouterRoomUserSimpDto roomUserMap = roomUserDao.findByUserIdAndRoomId2Map(id, roomId);
    Optional<SimulationRouterRoomEntity> optional = roomDao.findByIdOptional(roomId);
    WebSocketSimulationService persistData = new WebSocketSimulationService();
    if (optional.isEmpty()) {
      sendErrorMessage(session, "人员或房间信息未找到", id, id);
      session.close();
      return;
    }
    if (roomUserMap == null) {
      UserEntity userEntity = userDao.findById(id);
      roomUserMap = new SimulationRouterRoomUserSimpDto();
      roomUserMap.setId(userEntity.getId());
      roomUserMap.setName(userEntity.getUserAccount());
      roomUserMap.setUserImg(userEntity.getUserImg());
      roomUserMap.setChannel(-1);
    }
    this.userModel = JSONUtils.fromJson(JSONUtils.toJson(roomUserMap), SimulationUserModel.class);
    if (userModel != null) {
      userModel.setStatus(1);
    }
    this.session = session;
    persistData.setSession(session);
    persistData.setUserModel(userModel);
    SimulationRouterRoomEntity roomEntity = optional.get();
    if (Objects.equals(DISTURB.getType(), roomEntity.getRoomType())) {
      addRoomDisturd(this, roomId, persistData);
    } else if (Objects.equals(REPORT.getType(), roomEntity.getRoomType())) {
      addRoomReport(this, roomId, persistData);
    } else if (Objects.equals(RECEPT.getType(), roomEntity.getRoomType())) {
      addRoomReport(this, roomId, persistData);
    } else if (Objects.equals(ROUTER.getType(), roomEntity.getRoomType())) {
      addRoomRouter(this, roomId, persistData);
    }
  }

  public void addRoomDisturd(WebSocketSimulationService ws, Integer roomId, WebSocketSimulationService persistData) {
    List<WebSocketSimulationService> simulations = Optional.ofNullable(SimulationGlobal.disturbRoom.get(roomId))
        .orElseGet(ArrayList::new);
    //踢出连接
    kickOutOld(simulations, ws.getUserModel().getId());
    String id = ws.getUserModel().getId();
    //发给前端message
    SimulationDisturdWebscoketVO webscoketVO = new SimulationDisturdWebscoketVO();
    webscoketVO.setTopic(ONLINE);
    SimulationDisturdWebscoketBody body = new SimulationDisturdWebscoketBody();
    body.setId(ws.getUserModel().getId());
    body.setUserName(ws.getUserModel().getName());
    body.setUserImg(ws.getUserModel().getUserImg());
    body.setChannel(ws.getUserModel().getChannel());
    webscoketVO.setBody(body);
    Optional<SimulationRouterRoomEntity> optional = roomDao.findByIdOptional(roomId);
    if (optional.isPresent()) {
      SimulationRouterRoomEntity roomEntity = optional.get();
      if (!Objects.equals(roomEntity.getCreateUserId(), id)) {
        //学员通道选择不为null，给教员发消息该学员上线
        for (WebSocketSimulationService webSocketSimulationService : simulations) {
          if (webSocketSimulationService.getUserModel().getChannel().compareTo(-1) == 0) {
            WebSocketSimulationService.sendMessage(webSocketSimulationService.getSession(), JSONUtils.toJson(webscoketVO), "", "");
            break;
          }
        }
      } else {
        //教员给学生推送
        for (WebSocketSimulationService webSocketSimulationService : simulations) {
          if (webSocketSimulationService.getUserModel().getUserType().compareTo(1) == 0) {
            WebSocketSimulationService.sendMessage(webSocketSimulationService.getSession(), JSONUtils.toJson(webscoketVO), "", "");

          }
        }
      }
      simulations.add(persistData);
      SimulationGlobal.disturbRoom.put(roomId, simulations);
    }
  }

  public void addRoomReport(WebSocketSimulationService ws, Integer roomId, WebSocketSimulationService persistData) {
    //拿到房间信息
    List<WebSocketSimulationService> simulations = Optional.ofNullable(SimulationGlobal.reportRoom.get(roomId))
        .orElseGet(ArrayList::new);
    kickOutOld(simulations, ws.getUserModel().getId());
    if (ws.getUserModel().getChannel().compareTo(1) == 0) {
      simulations.stream()
          .filter(item -> item.getUserModel().getChannel() == 0)
          .findFirst()
          .ifPresent(wss -> {
            Map<String, String> data = new HashMap<>();
            data.put(TYPE, "1");
            data.put(ID, ws.getUserModel().getId());
            //学员上线给教员发送信息
            WebSocketSimulationService.sendMessage(wss.getSession(), JSONObject.toJSONString(data), ws.getUserModel().getName(), "");
          });
    }
    ws.getUserModel().setStatus(1);
    simulations.add(persistData);
    SimulationGlobal.reportRoom.put(roomId, simulations);
  }

  public void addRoomRouter(WebSocketSimulationService ws, Integer roomId, WebSocketSimulationService persistData) {
    List<WebSocketSimulationService> simulations = Optional.ofNullable(SimulationGlobal.routerRoom.get(roomId))
        .orElseGet(ArrayList::new);
    kickOutOld(simulations, ws.getUserModel().getId());
    //发送上线成功的消息
    Map<String, Object> msg = new HashMap<>();
    Map<String, String> body = new HashMap<>();
    body.put(ID, ws.getUserModel().getId());
    msg.put(TOPIC, ONLINE);
    msg.put(BODY, body);
    //添加到socket中
    for (WebSocketSimulationService simulation : simulations) {
      WebSocketSimulationService.sendMessage(simulation.getSession(), JSONObject.toJSONString(msg), "", "");
    }

    simulations.add(persistData);
    SimulationGlobal.routerRoom.put(roomId, simulations);
  }

  /**
   * 关闭
   */
  @OnClose
  public void onClose(@PathParam(ID) String id, @PathParam(ROOM_ID) Integer roomId) {
    Optional<SimulationRouterRoomEntity> optional = roomDao.findByIdOptional(roomId);
    if (optional.isEmpty()) {
      return;
    }
    SimulationRouterRoomEntity roomEntity = optional.get();
    if (Objects.equals(DISTURB.getType(), roomEntity.getRoomType())) {
      quitRoomDisturb(roomId, id);
    } else if (Objects.equals(REPORT.getType(), roomEntity.getRoomType())) {
      quitRoomReport(this, roomId, id);
    } else if (Objects.equals(RECEPT.getType(), roomEntity.getRoomType())) {
      quitRoomReport(this, roomId, id);
    } else if (Objects.equals(ROUTER.getType(), roomEntity.getRoomType())) {
      quitRoomRouter(roomId, id);
    }
  }

  @Transactional
  public void quitRoomDisturb(Integer roomId, String userId) {
    List<WebSocketSimulationService> simulations = SimulationGlobal.disturbRoom.get(roomId);
    if (Objects.isNull(simulations)) {
      return;
    }
    //查询用户角色
    SimulationRouterRoomUserEntity roomUserEntity = roomUserDao.findByUserIdAndRoomId(userId, roomId);
    List<WebSocketSimulationService> collect = new ArrayList<>();
    Integer removeIndex = null;
    //学员退出
    if (roomUserEntity.getUserType().compareTo(1) == 0) {
      for (int i = 0; i < simulations.size(); i++) {
        WebSocketSimulationService socketSimulation = simulations.get(i);
        if (socketSimulation.getUserModel().getUserType().compareTo(0) == 0) {
          collect.add(socketSimulation);
        }
        if (Objects.equals(socketSimulation.getUserModel().getId(), userId)) {
          removeIndex = i;
        }
      }
      Optional<SimulationRouterRoomEntity> optional = roomDao.findByIdOptional(roomId);
      if (optional.isPresent()) {
        SimulationRouterRoomEntity roomEntity = optional.get();
        if (roomEntity.getStats().compareTo(0) == 0) {
          roomUserDao.remove(roomId, userId);
        }
      }
    }
    //教员退出
    else {
      for (int i = 0; i < simulations.size(); i++) {
        WebSocketSimulationService webSocketSimulationService = simulations.get(i);
        if (webSocketSimulationService.getUserModel().getUserType().compareTo(1) == 0) {
          collect.add(webSocketSimulationService);
        }
        if (Objects.equals(webSocketSimulationService.getUserModel().getId(), userId)) {
          removeIndex = i;
        }
      }
    }
    collect.forEach(item -> {
      Map<String, Object> message = new HashMap<>();
      Map<String, String> body = new HashMap<>();
      body.put(ID, userId);
      message.put(BaseConstants.TOPIC, BaseConstants.OFFLINE);
      message.put(BaseConstants.BODY, body);
      WebSocketSimulationService.sendMessage(item.getSession(), JSONObject.toJSONString(message), "", "");
    });

    if (removeIndex != null && simulations.isEmpty()) {
      SimulationGlobal.disturbRoom.remove(roomId);
    }
  }

  @Transactional
  public void quitRoomReport(WebSocketSimulationService ws, Integer roomId, String userId) {
    List<WebSocketSimulationService> simulations = SimulationGlobal.reportRoom.get(roomId);
    if (Objects.isNull(simulations)) {
      return;
    }
    if (ws.getUserModel().getChannel().compareTo(1) == 0) {
      simulations.stream()
          .filter(item -> item.getUserModel().getChannel() == 0)
          .findFirst()
          .ifPresent(wss -> {
            Map<String, Object> data = new HashMap<>();
            data.put(TYPE, 0);
            data.put(ID, ws.getUserModel().getId());
            WebSocketSimulationService.sendMessage(wss.getSession(), JSONObject.toJSONString(data), ws.getUserModel().getName(), "");
          });
    } else {
      Optional<SimulationRouterRoomEntity> roomEntityOptional = roomDao.findByIdOptional(roomId);
      roomEntityOptional.ifPresent(roomEntity -> {
        roomEntity.setPlayStatus(0);
        roomDao.save(roomEntity);
      });
      for (WebSocketSimulationService simulation : simulations) {
        if (simulation.getUserModel().getUserType().compareTo(1) == 0) {
          Map<String, Integer> jb = new HashMap<>();
          jb.put(TYPE, 2);
          WebSocketSimulationService.sendMessage(simulation.getSession(), JSONObject.toJSONString(jb), "", "");
        }
      }
    }
    Iterator<WebSocketSimulationService> iterator = simulations.iterator();
    while (iterator.hasNext()) {
      WebSocketSimulationService simulation = iterator.next();
      if (Objects.equals(simulation.getUserModel().getId(), userId)) {
        iterator.remove(); // 安全地移除元素
        if (simulations.isEmpty()) {
          SimulationGlobal.reportRoom.remove(roomId);
        }
        break; // 找到并删除一个即可
      }
    }
    System.out.println(simulations.size());
    SimulationGlobal.reportRoom.put(roomId, simulations);
  }

  public void quitRoomRouter(Integer roomId, String userId) {
    List<WebSocketSimulationService> simulations = SimulationGlobal.routerRoom.get(roomId);
    if (Objects.isNull(simulations)) {
      return;
    }
    Map<String, Object> msg = new HashMap<>();
    Map<String, String> body = new HashMap<>();
    body.put(ID, userId);
    msg.put(TOPIC, OFFLINE);
    msg.put(BODY, body);
    WebSocketSimulationService removeObj = null;
    for (WebSocketSimulationService webSocketSimulationService : simulations) {
      if (webSocketSimulationService.getUserModel().getId().equals(userId)) {
        removeObj = webSocketSimulationService;
      } else {
        WebSocketSimulationService.sendMessage(webSocketSimulationService.getSession(), JSONObject.toJSONString(msg), "", "");
      }
    }
    simulations.remove(removeObj);
  }

  /**
   * 消息处理
   *
   * @param
   * @param message 消息（JSON）
   */
  @OnMessage
  public void onMessage(@PathParam(ID) String id, @PathParam(ROOM_ID) Integer roomId, String message) {
    Optional<SimulationRouterRoomEntity> optional = roomDao.findByIdOptional(roomId);
    if (optional.isEmpty()) {
      return;
    }
    SimulationRouterRoomEntity roomEntity = optional.get();
    if (Objects.equals(DISTURB.getType(), roomEntity.getRoomType())) {
      messageHandleDisturb(message, roomId, id);
    } else if (Objects.equals(REPORT.getType(), roomEntity.getRoomType())) {
      messageHandleReport(message, roomId, id);
    } else if (Objects.equals(RECEPT.getType(), roomEntity.getRoomType())) {
      messageHandleReport(message, roomId, id);
    } else if (Objects.equals(ROUTER.getType(), roomEntity.getRoomType())) {
      messageHandleRouter(message, roomId, id);
    }
  }

  @Transactional
  public void messageHandleDisturb(String message, Integer roomId, String userId) {
    List<WebSocketSimulationService> simulations = SimulationGlobal.disturbRoom.get(roomId);
    if (simulations == null || simulations.isEmpty()) {
      return;
    }
    //解析messagec
    Map<String, Object> jsonObject = JSONUtils.fromJson(message, new TypeToken<>() {
    });
    String topic = jsonObject.get(BaseConstants.TOPIC).toString();
    String body = JSONUtils.toJson(jsonObject.get(BaseConstants.BODY));
    //全局推送
    if (SimulationDisturdTopicEnum.TOPIC_ZERO.getType().equals(topic)) {
      for (WebSocketSimulationService simulation : simulations) {
        sendTraining(simulation, message, body, "0");
      }
    }
    //1路推送
    else if (TOPIC_ONE.getType().equals(topic)) {
      for (WebSocketSimulationService simulation : simulations) {
        Integer channel = simulation.getUserModel().getChannel();
        if (!Objects.isNull(channel) && channel.compareTo(1) == 0) {
          sendTraining(simulation, message, body, TOPIC_ONE.getType());
        }
      }
    }
    //2路推送
    else if (TOPIC_TWO.getType().equals(topic)) {
      for (WebSocketSimulationService simulation : simulations) {
        Integer channel = simulation.getUserModel().getChannel();
        if (!Objects.isNull(channel) && channel.compareTo(2) == 0) {
          sendTraining(simulation, message, body, TOPIC_TWO.getType());
        }
      }
    }
    //3路推送
    else if (TOPIC_THREE.getType().equals(topic)) {
      for (WebSocketSimulationService simulation : simulations) {
        Integer channel = simulation.getUserModel().getChannel();
        if (!Objects.isNull(channel) && channel.compareTo(3) == 0) {
          sendTraining(simulation, message, body, TOPIC_THREE.getType());
        }
      }
    }
    //开始训练
    else if (TOPIC_BEGIN.getType().equals(topic)) {
      jsonObject.put(TOPIC, 0);
      roomDao.findByIdOptional(roomId).ifPresent(item -> {
        item.setStats(1);
        roomDao.save(item);
      });
      simulations
          .stream()
          .filter(item -> !item.getUserModel().getId().equals(userId))
          .forEach(item -> WebSocketSimulationService.sendMessage(item.getSession(), JSONObject.toJSONString(jsonObject), "", ""));
    }
    //结束训练
    else if (TOPIC_END.getType().equals(topic)) {
      jsonObject.put(TOPIC, 0);
      Map<String, Object> map = JSONUtils.fromJson(body, new TypeToken<>() {
      });
      Integer totalTime = Integer.parseInt(map.get("totalTime").toString());
      roomDao.findByIdOptional(roomId).ifPresent(item -> {
        item.setStats(2);
        item.setTotalTime(totalTime);
        roomDao.save(item);
      });

      for (WebSocketSimulationService item : simulations) {
        if (!item.getUserModel().getId().equals(userId)) {
          WebSocketSimulationService.sendMessage(item.getSession(), JSONObject.toJSONString(jsonObject), "", "");
        }
      }
    }
    //TOPIC_SELECT
    else if (TOPIC_SELECT.getType().equals(topic)) {
      Map<String, Object> map = JSONUtils.fromJson(body, new TypeToken<>() {
      });
      Integer channel = Integer.parseInt(map.get("road").toString());
      SimulationRouterRoomUserEntity routerRoomUserEntity = roomUserDao.findByUserIdAndRoomId(userId, roomId);
      Optional.ofNullable(routerRoomUserEntity)
          .ifPresent(item -> {
            item.setChannel(channel);
            //保存该学员频道到数据库
            roomUserDao.save(routerRoomUserEntity);
            //给教员发送学员频道消息
            for (WebSocketSimulationService simulation : simulations) {
              if (simulation.getUserModel().getUserType().compareTo(0) == 0) {
                WebSocketSimulationService.sendMessage(simulation.getSession(), message, "", "");
              } else if (Objects.equals(simulation.getUserModel().getId(), userId)) {
                simulation.getUserModel().setChannel(channel);
              }
            }
          });
    }
    //TOPIC_RESULT
    else if (TOPIC_RESULT.getType().equals(topic)) {
      for (WebSocketSimulationService simulation : simulations) {
        if (simulation.getUserModel().getUserType().compareTo(0) == 0) {
          WebSocketSimulationService.sendMessage(simulation.getSession(), message, "", "");
        }
      }
    }
  }

  private void sendTraining(WebSocketSimulationService simulation, String message, String body, String topic) {
    Map<String, Object> msg = JSONUtils.fromJson(message, new TypeToken<>() {
    });
    msg.put(TOPIC, topic);
    msg.put(BODY, JSONUtils.fromJson(body, new TypeToken<>() {
    }));
    WebSocketSimulationService.sendMessage(simulation.getSession(), JSONObject.toJSONString(msg), "", "");
  }

  @Transactional
  public void messageHandleReport(String message, Integer roomId, String userId) {
    //通过人员id获取消息管道号
    List<WebSocketSimulationService> socketSimulations = SimulationGlobal.reportRoom.get(roomId);
    Map<String, String> mesg = JSONUtils.fromJson(message, new TypeToken<>() {
    });
    String type = mesg.get(TYPE);
    // 教员开始训练
    if (TOPIC_TRAIN_START.getType().equals(type)) {
      roomDao.updateStatsToGoing(roomId);
    }
    // 暂停训练
    else if (TOPIC_TRAIN_PAUSE.getType().equals(type)) {
      Optional<SimulationRouterRoomEntity> roomEntityOptional = roomDao.findByIdOptional(roomId);
      roomEntityOptional.ifPresent(roomEntity -> {
        roomEntity.setPlayStatus(0);
        roomDao.save(roomEntity);
      });
    }
    // 继续训练
    else if (TOPIC_TRAIN_GOON.getType().equals(type)) {
      roomDao.updateStatsToGoing(roomId);
    }
    // 结束训练
    else if (TOPIC_TRAIN_FINISH.getType().equals(type)) {
      Integer count = Integer.parseInt(mesg.get("count"));
      roomDao.updateStatsToFinish(roomId, count);
      for (WebSocketSimulationService socketSimulation : socketSimulations) {
        if (!Objects.equals(socketSimulation.getUserModel().getId(), userId)) {
          WebSocketSimulationService.sendMessage(socketSimulation.getSession(), message, "", "");
        }
      }
      return;
    }
    //学员准备消息
    else if (TOPIC_TRAIN_ONLINE.getType().equals(type)) {
      for (WebSocketSimulationService simulation : socketSimulations) {
        if (Objects.equals(simulation.getUserModel().getId(), userId)) {
          //将装备设置成已准备
          simulation.getUserModel().setStatus(2);
        } else if (simulation.getUserModel().getUserType().compareTo(0) == 0) {
          mesg.put(ID, userId);
          WebSocketSimulationService.sendMessage(simulation.getSession(), JSONObject.toJSONString(mesg), "", "");
        }
      }
      return;
    } else if (TOPIC_RESULT.getType().equals(type)) {
      mesg.put(ID, userId);
      //修改用户填报状态
      SimulationRouterRoomUserEntity roomUserEntity = roomUserDao.findByUserIdAndRoomId(userId, roomId);
      if (!Objects.isNull(roomUserEntity)) {
        roomUserEntity.setUserStatus(1);
        roomUserDao.save(roomUserEntity);
        for (WebSocketSimulationService socketSimulation : socketSimulations) {
          if (socketSimulation.getUserModel().getChannel().compareTo(0) == 0) {
            WebSocketSimulationService.sendMessage(socketSimulation.getSession(), JSONObject.toJSONString(mesg), "", "");
            break;
          }
        }
      }
    }
    for (WebSocketSimulationService socketSimulation : socketSimulations) {
      if (!Objects.equals(socketSimulation.getUserModel().getId(), userId)) {
        WebSocketSimulationService.sendMessage(socketSimulation.getSession(), message, "", "");
      }
    }
  }

  @Transactional
  public void messageHandleRouter(String message, Integer roomId, String userId) {
    //通过人员id获取消息管道号
    List<WebSocketSimulationService> socketSimulations = SimulationGlobal.routerRoom.get(roomId);
    Map<String, Object> msg = JSONUtils.fromJson(message, new TypeToken<>() {
    });
    String topic = msg.get(TOPIC).toString();
    switch (topic) {
      case TRAIN_READY -> //状态修改为准备，且给所有人发送消息
          socketSimulations.forEach(webSocketSimulation -> {
            if (Objects.equals(webSocketSimulation.getUserModel().getId(), userId)) {
              webSocketSimulation.getUserModel().setStatus(2);
            } else {
              WebSocketSimulationService.sendMessage(webSocketSimulation.getSession(), message, "", "");
            }
          });
      // 推送给相同频道的人
      case TRAIN_PLAY -> {
        for (int i = 0; i < socketSimulations.size(); i++) {
          WebSocketSimulationService socketSimulation = socketSimulations.get(i);
          if (Objects.equals(socketSimulation.getUserModel().getId(), userId)) {
            List<WebSocketSimulationService> collect;
            //组训人员发送给所有人
            if (socketSimulation.getUserModel().getUserType() == null) {
              collect = socketSimulations.stream()
                  .filter(item -> !Objects.equals(item.getUserModel().getId(), userId))
                  .toList();
            } else {  //参训人员给对应频道人员
              Integer channel = socketSimulation.getUserModel().getChannel();
              collect = socketSimulations.stream()
                  .filter(item -> item.getUserModel().getChannel().compareTo(channel) == 0 &&
                      !Objects.equals(item.getUserModel().getId(), socketSimulation.getUserModel().getId()))
                  .toList();
            }

            collect.forEach(item -> WebSocketSimulationService.sendMessage(
                item.getSession(),
                message,
                socketSimulation.getUserModel().getName(),
                item.getUserModel().getName()
            ));
            break;
          }
        }
      }
      // 将房间状态修改成进行中
      case TRAIN_BEGIN -> {
        Optional<SimulationRouterRoomEntity> optional = roomDao.findByIdOptional(roomId);
        if (optional.isPresent()) {
          SimulationRouterRoomEntity roomEntity = optional.get();
          //将状态修改成进行中
          roomEntity.setStats(1);
          //记录开始时间
          roomEntity.setStartTime(LocalDateTime.now());
          roomDao.save(roomEntity);
          //给所有人推送消息组训人
          socketSimulations.forEach(item -> {
            if (!Objects.isNull(item.getUserModel().getUserType())) {
              WebSocketSimulationService.sendMessage(item.getSession(), message, "", "");
            }
          });
        }
      }
      // 结束训练
      case TRAIN_END -> {
        Optional<SimulationRouterRoomEntity> optional = roomDao.findByIdOptional(roomId);
        if (optional.isPresent()) {
          SimulationRouterRoomEntity roomEntity = optional.get();
          long startTime = Timestamp.valueOf(roomEntity.getStartTime()).getTime();
          long currentTimeMillis = System.currentTimeMillis();
          int totalTime = (int) ((currentTimeMillis - startTime) / 1000);
          //将状态修改成进行中
          roomEntity.setStats(2);
          //记录开始时间
          roomEntity.setStartTime(LocalDateTime.now());
          roomEntity.setTotalTime(totalTime);
          roomDao.save(roomEntity);
          //给所有人推送消息组训人
          socketSimulations.forEach(item -> {
            if (!Objects.isNull(item.getUserModel().getUserType())) {
              WebSocketSimulationService.sendMessage(item.getSession(), message, "", "");
            }
          });
        }
      }
      // 训练结束
      case TRAIN_OVER -> //给所有人推送消息组训人
          socketSimulations.forEach(item -> WebSocketSimulationService.sendMessage(item.getSession(), message, "", ""));
      // 切换频道
      case TRAIN_CHANGE -> //给所有人推送消息组训人
          socketSimulations.forEach(item -> WebSocketSimulationService.sendMessage(item.getSession(), message, "", ""));
      case null, default -> log.error("未知主题：{}", msg);
    }
  }

  private void sendErrorMessage(Session session, String errorMsg, String sendName, String receiveName) {
    try {
      session.getBasicRemote().sendText(JSONUtils.toJson(SimulationResponseModel.err(errorMsg, sendName, receiveName)));
    } catch (IOException e) {
      log.error("WebSocketSimulationService.sendErrorMessage:{}", e.getMessage());
    }
  }

  public static void sendMessage(Session session, String message, String sendName, String receiveName) {
    try {
      if (session.isOpen()) {
        session.getBasicRemote().sendText(JSONUtils.toJson(SimulationResponseModel.success(message, sendName, receiveName)));
      }
    } catch (IOException e) {
      log.error("WebSocketSimulationService.sendMessage:{}", e.getMessage());
    }
  }

  /**
   * 踢出旧连接
   *
   * @param simulations 连接
   * @param userId      用户id
   */
  public void kickOutOld(List<WebSocketSimulationService> simulations, String userId) {
    for (int i = 0; i < simulations.size(); i++) {
      WebSocketSimulationService webSocketSimulationService = simulations.get(i);
      if (Objects.equals(webSocketSimulationService.getUserModel().getId(), userId)) {
        try {
          webSocketSimulationService.getSession().close();
        } catch (IOException e) {
          log.error("WebSocketSimulationService.kickOutOld:{}", e.getMessage());
        } finally {
          simulations.remove(webSocketSimulationService);
        }
      }
    }
  }
}
