package com.nip.ws;

import com.google.gson.reflect.TypeToken;
import com.nip.common.constants.BaseConstants;
import com.nip.common.constants.SimulationDisturdTopicEnum;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.PojoUtils;
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
import com.nip.ws.model.SimulationSessionHolder;
import com.nip.ws.service.simulation.SimulationGlobal;
import com.nip.ws.service.simulation.SimulationRoomLifecycle;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
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
@Slf4j
@RegisterForReflection
public class WebSocketSimulationService {
  @Inject
  SimulationRouterRoomDao roomDao;
  @Inject
  UserDao userDao;
  @Inject
  SimulationRouterRoomUserDao roomUserDao;

  /**
   * @param session 会话
   * @param id      用户id
   */
  @OnOpen
  public void onOpen(Session session, @PathParam(ID) String id,
      @PathParam(ROOM_ID) Integer roomId) throws IOException {
    SimulationRouterRoomUserSimpDto roomUserMap = roomUserDao.findByUserIdAndRoomId2Map(id, roomId);
    Optional<SimulationRouterRoomEntity> optional = roomDao.findByIdOptional(roomId);
    if (optional.isEmpty()) {
      sendErrorMessage(session, "人员或房间信息未找到", id, id);
      session.close();
      return;
    }
    SimulationRouterRoomEntity roomEntity = optional.get();
    if (roomUserMap == null
        && (Objects.equals(REPORT.getType(), roomEntity.getRoomType())
        || Objects.equals(RECEPT.getType(), roomEntity.getRoomType()))) {
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
    SimulationUserModel userModel = PojoUtils.convertOne(roomUserMap, SimulationUserModel.class);
    userModel.setStatus(1);
    SimulationSessionHolder holder = new SimulationSessionHolder(session, userModel);
    if (Objects.equals(DISTURB.getType(), roomEntity.getRoomType())) {
      addRoomDisturd(roomId, holder);
    } else if (Objects.equals(REPORT.getType(), roomEntity.getRoomType())
        || Objects.equals(RECEPT.getType(), roomEntity.getRoomType())) {
      addRoomReport(roomId, holder);
    } else if (Objects.equals(ROUTER.getType(), roomEntity.getRoomType())) {
      addRoomRouter(roomId, holder);
    }
  }

  public void addRoomDisturd(Integer roomId, SimulationSessionHolder holder) {
    String id = holder.userModel().getId();
    SimulationDisturdWebscoketVO webscoketVO = new SimulationDisturdWebscoketVO();
    webscoketVO.setTopic(ONLINE);
    SimulationDisturdWebscoketBody body = new SimulationDisturdWebscoketBody();
    body.setId(id);
    body.setUserName(holder.userModel().getName());
    body.setUserImg(holder.userModel().getUserImg());
    body.setChannel(holder.userModel().getChannel());
    webscoketVO.setBody(body);
    Optional<SimulationRouterRoomEntity> optional = roomDao.findByIdOptional(roomId);
    if (optional.isEmpty()) {
      return;
    }
    SimulationRoomLifecycle.Replacement replacement =
        SimulationRoomLifecycle.replace(SimulationGlobal.disturbRoom, roomId, holder);
    closeReplaced(replacement.replaced());
    List<SimulationSessionHolder> simulations = replacement.members();
    SimulationRouterRoomEntity roomEntity = optional.get();
    if (!Objects.equals(roomEntity.getCreateUserId(), id)) {
      simulations.stream()
          .filter(member -> member != holder)
          .filter(member -> Objects.equals(member.userModel().getChannel(), -1))
          .findFirst()
          .ifPresent(member -> sendMessage(
              member.session(), JSONUtils.toJson(webscoketVO), "", ""));
    } else {
      simulations.stream()
          .filter(member -> member != holder)
          .filter(member -> Objects.equals(member.userModel().getUserType(), 1))
          .forEach(member -> sendMessage(
              member.session(), JSONUtils.toJson(webscoketVO), "", ""));
    }
  }

  public void addRoomReport(Integer roomId, SimulationSessionHolder holder) {
    SimulationRoomLifecycle.Replacement replacement =
        SimulationRoomLifecycle.replace(SimulationGlobal.reportRoom, roomId, holder);
    closeReplaced(replacement.replaced());
    if (Objects.equals(holder.userModel().getChannel(), 1)) {
      replacement.members().stream()
          .filter(member -> member != holder)
          .filter(member -> Objects.equals(member.userModel().getChannel(), 0))
          .findFirst()
          .ifPresent(member -> {
            Map<String, String> data = new HashMap<>();
            data.put(TYPE, "1");
            data.put(ID, holder.userModel().getId());
            sendMessage(member.session(), JSONObject.toJSONString(data),
                holder.userModel().getName(), "");
          });
    }
  }

  public void addRoomRouter(Integer roomId, SimulationSessionHolder holder) {
    SimulationRoomLifecycle.Replacement replacement =
        SimulationRoomLifecycle.replace(SimulationGlobal.routerRoom, roomId, holder);
    closeReplaced(replacement.replaced());
    Map<String, Object> msg = new HashMap<>();
    Map<String, String> body = new HashMap<>();
    body.put(ID, holder.userModel().getId());
    msg.put(TOPIC, ONLINE);
    msg.put(BODY, body);
    replacement.members().stream()
        .filter(member -> member != holder)
        .forEach(member -> sendMessage(
            member.session(), JSONObject.toJSONString(msg), "", ""));
  }

  /**
   * 关闭
   */
  @OnClose
  public void onClose(@PathParam(ID) String id, @PathParam(ROOM_ID) Integer roomId,
      Session session) {
    Optional<SimulationRouterRoomEntity> optional = roomDao.findByIdOptional(roomId);
    if (optional.isEmpty()) {
      SimulationRoomLifecycle.removeCurrent(SimulationGlobal.disturbRoom, roomId, id, session);
      SimulationRoomLifecycle.removeCurrent(SimulationGlobal.reportRoom, roomId, id, session);
      SimulationRoomLifecycle.removeCurrent(SimulationGlobal.routerRoom, roomId, id, session);
      return;
    }
    Integer roomType = optional.get().getRoomType();
    if (Objects.equals(DISTURB.getType(), roomType)) {
      quitRoomDisturb(roomId, id, session);
    } else if (Objects.equals(REPORT.getType(), roomType)
        || Objects.equals(RECEPT.getType(), roomType)) {
      quitRoomReport(roomId, id, session);
    } else if (Objects.equals(ROUTER.getType(), roomType)) {
      quitRoomRouter(roomId, id, session);
    }
  }

  @OnError
  public void onError(@PathParam(ID) String id, @PathParam(ROOM_ID) Integer roomId,
      Session session, Throwable t) {
    log.error("ws error, session={}", session.getId(), t);
    onClose(id, roomId, session);
  }

  @Transactional
  public void quitRoomDisturb(Integer roomId, String userId, Session session) {
    SimulationRoomLifecycle.Removal removal = SimulationRoomLifecycle.removeCurrent(
        SimulationGlobal.disturbRoom, roomId, userId, session);
    if (removal == null) {
      return;
    }
    SimulationSessionHolder holder = removal.holder();
    List<SimulationSessionHolder> recipients;
    if (Objects.equals(holder.userModel().getUserType(), 1)) {
      recipients = removal.remaining().stream()
          .filter(member -> Objects.equals(member.userModel().getUserType(), 0))
          .toList();
      roomDao.findByIdOptional(roomId).ifPresent(room -> {
        if (Objects.equals(room.getStats(), 0)) {
          roomUserDao.remove(roomId, userId);
        }
      });
    } else {
      recipients = removal.remaining().stream()
          .filter(member -> Objects.equals(member.userModel().getUserType(), 1))
          .toList();
    }
    Map<String, Object> message = new HashMap<>();
    Map<String, String> body = new HashMap<>();
    body.put(ID, userId);
    message.put(BaseConstants.TOPIC, BaseConstants.OFFLINE);
    message.put(BaseConstants.BODY, body);
    recipients.forEach(member -> sendMessage(
        member.session(), JSONObject.toJSONString(message), "", ""));
  }

  @Transactional
  public void quitRoomReport(Integer roomId, String userId, Session session) {
    SimulationRoomLifecycle.Removal removal = SimulationRoomLifecycle.removeCurrent(
        SimulationGlobal.reportRoom, roomId, userId, session);
    if (removal == null) {
      return;
    }
    SimulationSessionHolder holder = removal.holder();
    Integer userType = holder.userModel().getUserType();
    Integer channel = holder.userModel().getChannel();
    boolean knownRole = Objects.equals(userType, 0) || Objects.equals(userType, 1);
    boolean knownChannel = Objects.equals(channel, 0) || Objects.equals(channel, 1);
    if (!knownRole || !knownChannel) {
      return;
    }
    if (Objects.equals(channel, 1)) {
      removal.remaining().stream()
          .filter(member -> Objects.equals(member.userModel().getChannel(), 0))
          .findFirst()
          .ifPresent(member -> {
            Map<String, Object> data = new HashMap<>();
            data.put(TYPE, 0);
            data.put(ID, holder.userModel().getId());
            sendMessage(member.session(), JSONObject.toJSONString(data),
                holder.userModel().getName(), "");
          });
      return;
    }
    roomDao.findByIdOptional(roomId).ifPresent(room -> {
      room.setPlayStatus(0);
      roomDao.save(room);
    });
    removal.remaining().stream()
        .filter(member -> Objects.equals(member.userModel().getUserType(), 1))
        .forEach(member -> {
          Map<String, Integer> message = new HashMap<>();
          message.put(TYPE, 2);
          sendMessage(member.session(), JSONObject.toJSONString(message), "", "");
        });
  }

  public void quitRoomRouter(Integer roomId, String userId, Session session) {
    SimulationRoomLifecycle.Removal removal = SimulationRoomLifecycle.removeCurrent(
        SimulationGlobal.routerRoom, roomId, userId, session);
    if (removal == null) {
      return;
    }
    Map<String, Object> msg = new HashMap<>();
    Map<String, String> body = new HashMap<>();
    body.put(ID, userId);
    msg.put(TOPIC, OFFLINE);
    msg.put(BODY, body);
    removal.remaining().forEach(member -> sendMessage(
        member.session(), JSONObject.toJSONString(msg), "", ""));
  }

  /**
   * 消息处理
   *
   * @param
   * @param message 消息（JSON）
   */
  @OnMessage
  public void onMessage(@PathParam(ID) String id, @PathParam(ROOM_ID) Integer roomId,
      String message, Session session) {
    Optional<SimulationRouterRoomEntity> optional = roomDao.findByIdOptional(roomId);
    if (optional.isEmpty()) {
      return;
    }
    Integer roomType = optional.get().getRoomType();
    if (Objects.equals(DISTURB.getType(), roomType)
        && SimulationRoomLifecycle.isCurrent(SimulationGlobal.disturbRoom, roomId, id, session)) {
      messageHandleDisturb(message, roomId, id);
    } else if ((Objects.equals(REPORT.getType(), roomType)
        || Objects.equals(RECEPT.getType(), roomType))
        && SimulationRoomLifecycle.isCurrent(SimulationGlobal.reportRoom, roomId, id, session)) {
      messageHandleReport(message, roomId, id);
    } else if (Objects.equals(ROUTER.getType(), roomType)
        && SimulationRoomLifecycle.isCurrent(SimulationGlobal.routerRoom, roomId, id, session)) {
      messageHandleRouter(message, roomId, id);
    }
  }

  @Transactional
  public void messageHandleDisturb(String message, Integer roomId, String userId) {
    List<SimulationSessionHolder> simulations = SimulationGlobal.disturbRoom.get(roomId);
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
      for (SimulationSessionHolder simulation : simulations) {
        sendTraining(simulation, message, body, "0");
      }
    }
    //1路推送
    else if (TOPIC_ONE.getType().equals(topic)) {
      for (SimulationSessionHolder simulation : simulations) {
        Integer channel = simulation.userModel().getChannel();
        if (!Objects.isNull(channel) && channel.compareTo(1) == 0) {
          sendTraining(simulation, message, body, TOPIC_ONE.getType());
        }
      }
    }
    //2路推送
    else if (TOPIC_TWO.getType().equals(topic)) {
      for (SimulationSessionHolder simulation : simulations) {
        Integer channel = simulation.userModel().getChannel();
        if (!Objects.isNull(channel) && channel.compareTo(2) == 0) {
          sendTraining(simulation, message, body, TOPIC_TWO.getType());
        }
      }
    }
    //3路推送
    else if (TOPIC_THREE.getType().equals(topic)) {
      for (SimulationSessionHolder simulation : simulations) {
        Integer channel = simulation.userModel().getChannel();
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
          .filter(item -> !item.userModel().getId().equals(userId))
          .forEach(item -> WebSocketSimulationService.sendMessage(item.session(), JSONObject.toJSONString(jsonObject), "", ""));
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

      for (SimulationSessionHolder item : simulations) {
        if (!item.userModel().getId().equals(userId)) {
          WebSocketSimulationService.sendMessage(
              item.session(), JSONObject.toJSONString(jsonObject), "", "");
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
            for (SimulationSessionHolder simulation : simulations) {
              if (simulation.userModel().getUserType().compareTo(0) == 0) {
                WebSocketSimulationService.sendMessage(simulation.session(), message, "", "");
              } else if (Objects.equals(simulation.userModel().getId(), userId)) {
                simulation.userModel().setChannel(channel);
              }
            }
          });
    }
    //TOPIC_RESULT
    else if (TOPIC_RESULT.getType().equals(topic)) {
      for (SimulationSessionHolder simulation : simulations) {
        if (simulation.userModel().getUserType().compareTo(0) == 0) {
          WebSocketSimulationService.sendMessage(simulation.session(), message, "", "");
        }
      }
    }
  }

  private void sendTraining(SimulationSessionHolder simulation, String message, String body, String topic) {
    Map<String, Object> msg = JSONUtils.fromJson(message, new TypeToken<>() {
    });
    msg.put(TOPIC, topic);
    msg.put(BODY, JSONUtils.fromJson(body, new TypeToken<>() {
    }));
    WebSocketSimulationService.sendMessage(simulation.session(), JSONObject.toJSONString(msg), "", "");
  }

  @Transactional
  public void messageHandleReport(String message, Integer roomId, String userId) {
    //通过人员id获取消息管道号
    List<SimulationSessionHolder> socketSimulations = SimulationGlobal.reportRoom.get(roomId);
    //REST 删房只清 map 不关 session：客户端续发消息时房间列表可能已不存在，判空短路
    if (socketSimulations == null) {
      return;
    }
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
      for (SimulationSessionHolder socketSimulation : socketSimulations) {
        if (!Objects.equals(socketSimulation.userModel().getId(), userId)) {
          WebSocketSimulationService.sendMessage(socketSimulation.session(), message, "", "");
        }
      }
      return;
    }
    //学员准备消息
    else if (TOPIC_TRAIN_ONLINE.getType().equals(type)) {
      for (SimulationSessionHolder simulation : socketSimulations) {
        if (Objects.equals(simulation.userModel().getId(), userId)) {
          //将装备设置成已准备
          simulation.userModel().setStatus(2);
        } else if (simulation.userModel().getUserType().compareTo(0) == 0) {
          mesg.put(ID, userId);
          WebSocketSimulationService.sendMessage(
              simulation.session(), JSONObject.toJSONString(mesg), "", "");
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
        for (SimulationSessionHolder socketSimulation : socketSimulations) {
          if (socketSimulation.userModel().getChannel().compareTo(0) == 0) {
            WebSocketSimulationService.sendMessage(
                socketSimulation.session(), JSONObject.toJSONString(mesg), "", "");
            break;
          }
        }
      }
    }
    for (SimulationSessionHolder socketSimulation : socketSimulations) {
      if (!Objects.equals(socketSimulation.userModel().getId(), userId)) {
        WebSocketSimulationService.sendMessage(socketSimulation.session(), message, "", "");
      }
    }
  }

  @Transactional
  public void messageHandleRouter(String message, Integer roomId, String userId) {
    //通过人员id获取消息管道号
    List<SimulationSessionHolder> socketSimulations = SimulationGlobal.routerRoom.get(roomId);
    //REST 删房只清 map 不关 session：客户端续发消息时房间列表可能已不存在，判空短路
    if (socketSimulations == null) {
      return;
    }
    Map<String, Object> msg = JSONUtils.fromJson(message, new TypeToken<>() {
    });
    String topic = msg.get(TOPIC).toString();
    switch (topic) {
      case TRAIN_READY -> //状态修改为准备，且给所有人发送消息
          socketSimulations.forEach(webSocketSimulation -> {
            if (Objects.equals(webSocketSimulation.userModel().getId(), userId)) {
              webSocketSimulation.userModel().setStatus(2);
            } else {
              WebSocketSimulationService.sendMessage(webSocketSimulation.session(), message, "", "");
            }
          });
      // 推送给相同频道的人
      case TRAIN_PLAY -> {
        for (int i = 0; i < socketSimulations.size(); i++) {
          SimulationSessionHolder socketSimulation = socketSimulations.get(i);
          if (Objects.equals(socketSimulation.userModel().getId(), userId)) {
            List<SimulationSessionHolder> collect;
            //组训人员发送给所有人
            if (socketSimulation.userModel().getUserType() == null) {
              collect = socketSimulations.stream()
                  .filter(item -> !Objects.equals(item.userModel().getId(), userId))
                  .toList();
            } else {  //参训人员给对应频道人员
              Integer channel = socketSimulation.userModel().getChannel();
              collect = socketSimulations.stream()
                  .filter(item -> item.userModel().getChannel().compareTo(channel) == 0 &&
                      !Objects.equals(item.userModel().getId(), socketSimulation.userModel().getId()))
                  .toList();
            }

            collect.forEach(item -> WebSocketSimulationService.sendMessage(
                item.session(),
                message,
                socketSimulation.userModel().getName(),
                item.userModel().getName()
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
            if (!Objects.isNull(item.userModel().getUserType())) {
              WebSocketSimulationService.sendMessage(item.session(), message, "", "");
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
            if (!Objects.isNull(item.userModel().getUserType())) {
              WebSocketSimulationService.sendMessage(item.session(), message, "", "");
            }
          });
        }
      }
      // 训练结束
      case TRAIN_OVER -> //给所有人推送消息组训人
          socketSimulations.forEach(item -> WebSocketSimulationService.sendMessage(item.session(), message, "", ""));
      // 切换频道
      case TRAIN_CHANGE -> //给所有人推送消息组训人
          socketSimulations.forEach(item -> WebSocketSimulationService.sendMessage(item.session(), message, "", ""));
      case null, default -> log.error("未知主题：{}", msg);
    }
  }

  /**
   * 仅 onOpen 拒接路径使用：此刻 session 尚未入房、无并发写者，保持同步写，
   * 确保紧随其后的 session.close() 前错误帧已发出
   */
  private void sendErrorMessage(Session session, String errorMsg, String sendName, String receiveName) {
    try {
      if (session.isOpen()) {
        session.getBasicRemote().sendText(JSONUtils.toJson(SimulationResponseModel.err(errorMsg, sendName, receiveName)));
      }
    } catch (Exception e) {
      log.error("WebSocketSimulationService.sendErrorMessage", e);
    }
  }

  /**
   * 广播发送统一入口：async remote（Undertow 内部排队，避免多线程并发 basic 写抛
   * IllegalStateException 打断整轮广播）；catch Exception，单个接收方失败不中断循环
   */
  public static void sendMessage(Session session, String message, String sendName, String receiveName) {
    try {
      if (session.isOpen()) {
        session.getAsyncRemote().sendText(JSONUtils.toJson(SimulationResponseModel.success(message, sendName, receiveName)));
      }
    } catch (Exception e) {
      log.error("WebSocketSimulationService.sendMessage", e);
    }
  }

  private void closeReplaced(List<SimulationSessionHolder> replaced) {
    for (SimulationSessionHolder holder : replaced) {
      try {
        if (holder.session().isOpen()) {
          holder.session().close();
        }
      } catch (IOException e) {
        log.error("WebSocketSimulationService.closeReplaced", e);
      }
    }
  }

  /**
   * P2-8：REST 删房时向成员发送 CLOSE 帧并关闭全部 session，替代原「只清 map 留悬挂连接」
   *
   * @param holders 房间内的连接持有者（可为 null）
   * @param reason  关闭原因，随 CLOSE 帧下发
   */
  public static void closeRoomSessions(List<SimulationSessionHolder> holders, String reason) {
    if (holders == null) {
      return;
    }
    for (SimulationSessionHolder holder : holders) {
      try {
        Session session = holder.session();
        if (session.isOpen()) {
          session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, reason));
        }
      } catch (IOException e) {
        log.error("WebSocketSimulationService.closeRoomSessions", e);
      }
    }
  }
}
