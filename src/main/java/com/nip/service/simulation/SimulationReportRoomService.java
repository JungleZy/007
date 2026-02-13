package com.nip.service.simulation;

import com.google.gson.reflect.TypeToken;
import com.nip.common.constants.SimulationRoomTypeEnum;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.PojoUtils;
import com.nip.common.utils.SimulationMessageGenerator;
import com.nip.dao.UserDao;
import com.nip.dao.simulation.*;
import com.nip.dto.SimulationRouterRoomContentDto;
import com.nip.dto.SimulationRouterRoomDto;
import com.nip.dto.vo.param.simulation.report.SimulationRoomReportAddParam;
import com.nip.dto.vo.simulation.report.SimulationReportRoomUserVO;
import com.nip.dto.vo.simulation.report.SimulationReportRoomVO;
import com.nip.entity.UserEntity;
import com.nip.entity.simulation.router.SimulationRouterRoomContentEntity;
import com.nip.entity.simulation.router.SimulationRouterRoomEntity;
import com.nip.entity.simulation.router.SimulationRouterRoomPageEntity;
import com.nip.entity.simulation.router.SimulationRouterRoomUserEntity;
import com.nip.service.CableFloorService;
import com.nip.service.UserService;
import com.nip.ws.WebSocketSimulationService;
import com.nip.ws.service.simulation.SimulationGlobal;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.*;

import static com.nip.common.constants.BaseConstants.TOKEN;

@ApplicationScoped
public class SimulationReportRoomService {
  private final SimulationRouterRoomDao reportRoomDao;
  private final SimulationRouterRoomUserDao roomUserDao;
  private final SimulationRouterRoomContentDao roomContentDao;
  private final UserService userService;
  private final UserDao userDao;
  private final SimulationRouterRoomPageDao pageDao;
  private final SimulationRouterRoomPageValueDao pageValueDao;
  private final CableFloorService cableFloorService;

  @Inject
  public SimulationReportRoomService(SimulationRouterRoomDao reportRoomDao,
                                     SimulationRouterRoomUserDao roomUserDao,
                                     SimulationRouterRoomContentDao roomContentDao,
                                     UserService userService,
                                     UserDao userDao,
                                     SimulationRouterRoomPageDao pageDao,
                                     SimulationRouterRoomPageValueDao pageValueDao,
                                     CableFloorService cableFloorService) {
    this.reportRoomDao = reportRoomDao;
    this.roomUserDao = roomUserDao;
    this.roomContentDao = roomContentDao;
    this.userService = userService;
    this.userDao = userDao;
    this.pageDao = pageDao;
    this.pageValueDao = pageValueDao;
    this.cableFloorService = cableFloorService;
  }

  @Transactional
  public SimulationRouterRoomEntity addRoom(HttpServerRequest request, SimulationRoomReportAddParam param) {
    //保存房间信息
    UserEntity userEntity = userService.getUserByToken(request.getHeader(TOKEN));
    SimulationRouterRoomEntity roomEntity = new SimulationRouterRoomEntity();
    roomEntity.setName(param.getRoomName());
    roomEntity.setIsCable(param.getIsCable());
    roomEntity.setCreateUserId(userEntity.getId());
    roomEntity.setStats(0);
    roomEntity.setRoomType(SimulationRoomTypeEnum.REPORT.getType());
    SimulationRouterRoomEntity room = reportRoomDao.save(roomEntity);

    //保存房间报底
    SimulationRouterRoomContentEntity roomContentEntity = new SimulationRouterRoomContentEntity();
    roomContentEntity.setContent(param.getContent());
    roomContentEntity.setRoomId(room.getId());
    roomContentEntity.setBdType(param.getBdType());
    roomContentEntity.setBwCount(param.getBwCount());
    roomContentEntity.setBwType(param.getBwType());
    roomContentEntity.setMainSignal(param.getMainSignal());
    roomContentEntity.setIsRandom(param.getIsRandom());
    SimulationRouterRoomContentEntity save1 = roomContentDao.save(roomContentEntity);

    //生成房间报底
    if (param.getIsCable() == 0) {
      Integer bwCount = param.getBwCount();
      Integer generateNumber = 200;
      if (bwCount.compareTo(200) < 0) {
        generateNumber = bwCount;
      }
      int index = save1.getBwType().compareTo(3) == 0 ? 65 : 0;
      generateMessageBody(generateNumber, 1, index, save1);
    } else {
      List<List<List<String>>> cableFloor = cableFloorService.findCableFloor(param.getCableId(), null, param.getStartPage());
      int totalPage = param.getBwCount() / 100;
      cableFloor = cableFloor.subList(0, totalPage);
      // 使用批量保存替代循环逐条保存，提升性能
      List<SimulationRouterRoomPageEntity> pageEntities = new ArrayList<>();
      for (int i = 0; i < cableFloor.size(); i++) {
        for (int j = 0; j < cableFloor.get(i).size(); j++) {
          SimulationRouterRoomPageEntity pageEntity = new SimulationRouterRoomPageEntity();
          pageEntity.setKey(String.join("", cableFloor.get(i).get(j)));
          pageEntity.setPageNumber(i + 1);
          pageEntity.setSort(j);
          pageEntity.setRoomId(room.getId());
          pageEntities.add(pageEntity);
        }
      }
      pageDao.save(pageEntities);
    }

    //保存房间对应人员信息
    List<String> receiveUserList = param.getReceiveUserList();
    List<String> sendUserList = param.getSendUserList();
    List<SimulationRouterRoomUserEntity> roomUser = new ArrayList<>();
    for (String sendId : sendUserList) {
      // 发报人
      SimulationRouterRoomUserEntity sendUser = new SimulationRouterRoomUserEntity();
      sendUser.setRoomId(room.getId());
      sendUser.setUserId(sendId);
      sendUser.setChannel(0);
      sendUser.setUserType(0);
      roomUser.add(sendUser);
    }
    for (String receiveId : receiveUserList) {
      // 收报人
      SimulationRouterRoomUserEntity receiveUser = new SimulationRouterRoomUserEntity();
      receiveUser.setRoomId(room.getId());
      receiveUser.setUserId(receiveId);
      receiveUser.setChannel(1);
      receiveUser.setUserType(1);
      roomUser.add(receiveUser);
    }
    roomUserDao.save(roomUser);
    return room;
  }

  /**
   * 查询该人员的所有房间
   *
   * @param request
   */
  public List<SimulationReportRoomVO> findRoom(HttpServerRequest request) {
    UserEntity userEntity = userService.getUserByToken(request.getHeader(TOKEN));
    List<SimulationRouterRoomDto> allByUserId = reportRoomDao.findAllByUserId(userEntity.getId());
    return PojoUtils.convert(allByUserId, SimulationReportRoomVO.class);
  }

  /**
   * 查询该房间详情
   *
   * @param roomId
   */
  public SimulationReportRoomVO getRoomDetail(Integer roomId, HttpServerRequest request) {
    String token = request.getHeader(TOKEN);
    UserEntity userEntity = userService.getUserByToken(token);

    SimulationRouterRoomContentDto roomMap = roomContentDao.findByRoomIdReport(roomId);
    List<SimulationRouterRoomUserEntity> allByRoomId = roomUserDao.findByRoomId(roomId);
    List<SimulationReportRoomUserVO> userEntities = new ArrayList<>();

    for (SimulationRouterRoomUserEntity simulationRouterRoomUserEntity : allByRoomId) {
      if (simulationRouterRoomUserEntity.getChannel() == 1) {
        String userId = simulationRouterRoomUserEntity.getUserId();
        SimulationReportRoomUserVO userVO = PojoUtils.convertOne(userDao.findUserEntityById(userId), SimulationReportRoomUserVO.class);
        userVO.setContentValue(simulationRouterRoomUserEntity.getContentValue());
        userVO.setUserStatus(simulationRouterRoomUserEntity.getUserStatus());
        userVO.setExistPageNumber(pageValueDao.countByUserIdAndRoomId(simulationRouterRoomUserEntity.getUserId(), roomId));
        userEntities.add(userVO);
      }
    }
    SimulationReportRoomVO simulationReportRoomVO = PojoUtils.convertOne(roomMap, SimulationReportRoomVO.class);
    List<WebSocketSimulationService> webSocketSimulationServices = Optional
        .ofNullable(SimulationGlobal.reportRoom.get(roomId))
        .orElseGet(ArrayList::new);
    userEntities.forEach(item -> {
      for (WebSocketSimulationService simulation : webSocketSimulationServices) {
        if (Objects.equals(item.getId(), simulation.getUserModel().getId())) {
          item.setStatus(simulation.getUserModel().getStatus());
          break;
        }
      }
    });
    long existPageNumber = pageValueDao.countByUserIdAndRoomId(userEntity.getId(), roomId);
    if (simulationReportRoomVO != null) {
      simulationReportRoomVO.setReceiveUser(userEntities);
      simulationReportRoomVO.setExistPageNumber(existPageNumber);
      if (roomMap.getIsCable() == 1) {
        simulationReportRoomVO.setPageCount(pageDao.findMaxPageNumber(roomId));
        simulationReportRoomVO.setBwCount((int) pageDao.count("roomId", roomId));
      }
    }
    return simulationReportRoomVO;
  }

  @Transactional(rollbackOn = Exception.class)
  public boolean delete(Integer roomId) {
    pageValueDao.delete("roomId=?1", roomId);
    pageDao.delete("roomId=?1", roomId);
    roomUserDao.delete("roomId=?1", roomId);
    roomContentDao.delete("roomId=?1", roomId);
    SimulationGlobal.reportRoom.remove(roomId);
    return reportRoomDao.deleteById(roomId);
  }

  /**
   * 生成报底
   *
   * @param generateNumber 报底
   * @param pageNumber     页码
   * @param index          上次位置
   * @param train          训练对象
   */
  public List<SimulationRouterRoomPageEntity> generateMessageBody(Integer generateNumber, Integer pageNumber, int index, SimulationRouterRoomContentEntity train) {
    return SimulationMessageGenerator.generateMessageBody(
        generateNumber, pageNumber, index, train, train.getRoomId(), pageDao::save);
  }
}
