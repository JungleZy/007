package com.nip.service.simulation;

import com.google.gson.reflect.TypeToken;
import com.nip.common.constants.SimulationRoomTypeEnum;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.PojoUtils;
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
      for (int i = 0; i < cableFloor.size(); i++) {
        for (int j = 0; j < cableFloor.get(i).size(); j++) {
          SimulationRouterRoomPageEntity pageEntity = new SimulationRouterRoomPageEntity();
          pageEntity.setKey(String.join("", cableFloor.get(i).get(j)));
          pageEntity.setPageNumber(i + 1);
          pageEntity.setSort(j);
          pageEntity.setRoomId(room.getId());
          pageDao.save(pageEntity);
        }
      }
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
    return JSONUtils.fromJson(JSONUtils.toJson(allByUserId), new TypeToken<>() {
    });
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
    SimulationReportRoomVO simulationReportRoomVO = JSONUtils.fromJson(JSONUtils.toJson(roomMap), SimulationReportRoomVO.class);
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

  /**
   * 生成报底
   *
   * @param generateNumber 报底
   * @param pageNumber     页码
   * @param index          上次位置
   * @param train          训练对象
   */
  public List<SimulationRouterRoomPageEntity> generateMessageBody(Integer generateNumber, Integer pageNumber, int index, SimulationRouterRoomContentEntity train) {
    List<SimulationRouterRoomPageEntity> ret = new ArrayList<>();
    int pageNum = pageNumber;
    Integer isAvg = train.getBdType();
    Integer isRandom = train.getIsRandom();
    Random random = new Random();
    List<String> avgB = new ArrayList<>();
    switch (train.getBwType()) {
      //字码报
      case 3:
        int charIndex = index;
        for (int i = 0; i < generateNumber; i++) {
          if (i % 100 == 0 && i != 0) {
            pageNumber += 1;
          }
          StringBuilder body = new StringBuilder();
          if (isAvg.compareTo(1) == 0) {
            for (int j = 0; j < 4; j++) {
              char c = (char) charIndex;
              if (charIndex == 90) {
                charIndex = 65;
              } else {
                charIndex++;
              }
              avgB.add(String.valueOf(c));
            }
          } else if (isRandom.compareTo(1) == 0) {
            for (int j = 0; j < 4; j++) {
              int i1 = random.nextInt(26) + 65;
              char a = (char) i1;
              body.append(a);
            }
          } else {
            for (int j = 0; j < 4; j++) {
              char c = (char) charIndex;
              if (charIndex == 90) {
                charIndex = 65;
              } else {
                charIndex++;
              }
              body.append(c);
            }
          }
          if (isAvg.compareTo(1) != 0) {
            SimulationRouterRoomPageEntity pageEntity = new SimulationRouterRoomPageEntity();
            pageEntity.setKey(body.toString());
            pageEntity.setPageNumber(pageNumber);
            pageEntity.setSort(i % 100);
            pageEntity.setRoomId(train.getRoomId());
            ret.add(pageEntity);
          }
        }
        break;
      //混合报
      case 4:
        int charAndNumberIndex = index;
        for (int i = 0; i < generateNumber; i++) {
          StringBuilder body = new StringBuilder();
          //List<String> avgB = new ArrayList<>();
          if (isAvg.compareTo(1) == 0) {
            for (int j = 0; j < 4; j++) {
              char c;
              if (charAndNumberIndex < 10) {
                c = (char) (charAndNumberIndex + 48);
              } else {
                c = (char) (charAndNumberIndex + 55);
              }
              if (charAndNumberIndex == 35) {
                charAndNumberIndex = 0;
              } else {
                charAndNumberIndex++;
              }
              avgB.add(String.valueOf(c));
            }

          } else if (isRandom.compareTo(1) == 0) {
            for (int j = 0; j < 4; j++) {
              int i1 = random.nextInt(36);
              if (i1 < 10) {
                i1 = (char) (i1 + 48);
              } else {
                i1 = (char) (i1 + 55);
              }
              char c = (char) i1;
              body.append(c);
            }
          } else {
            for (int j = 0; j < 4; j++) {
              char c;
              if (charAndNumberIndex < 10) {
                c = (char) (charAndNumberIndex + 48);
              } else {
                c = (char) (charAndNumberIndex + 55);
              }
              if (charAndNumberIndex == 35) {
                charAndNumberIndex = 0;
              } else {
                charAndNumberIndex++;
              }
              body.append(c);
            }
          }
          if (isAvg.compareTo(1) != 0) {
            SimulationRouterRoomPageEntity pageEntity = new SimulationRouterRoomPageEntity();
            pageEntity.setKey(body.toString());
            pageEntity.setPageNumber(pageNumber);
            pageEntity.setSort(i % 100);
            pageEntity.setRoomId(train.getRoomId());
            ret.add(pageEntity);
          }
        }
        break;
      //数字报
      default:
        int numberIndex = 0;
        for (int i = 0; i < generateNumber; i++) {
          if (i % 100 == 0 && i != 0) {
            pageNumber += 1;
          }
          StringBuilder body = new StringBuilder();
          if (isAvg.compareTo(1) == 0) {
            for (int j = 0; j < 4; j++) {
              avgB.add(String.valueOf(numberIndex));
              if (numberIndex == 9) {
                numberIndex = 0;
              } else {
                numberIndex++;
              }
            }
            //Collections.shuffle(avgB);
            //avgB.forEach(body::append);
          } else if (isRandom.compareTo(1) == 0) {
            for (int j = 0; j < 4; j++) {
              int i1 = random.nextInt(10);
              body.append(i1);
            }
          } else {
            for (int j = 0; j < 4; j++) {
              body.append(numberIndex);
              if (numberIndex == 9) {
                numberIndex = 0;
              } else {
                numberIndex++;
              }
            }
          }
          if (train.getBdType().compareTo(1) != 0) {
            SimulationRouterRoomPageEntity pageEntity = new SimulationRouterRoomPageEntity();
            pageEntity.setKey(body.toString());
            pageEntity.setPageNumber(pageNumber);
            pageEntity.setSort(i % 100);
            pageEntity.setRoomId(train.getRoomId());
            ret.add(pageEntity);
          }

        }
        break;
    }
    if (isAvg.compareTo(1) == 0) {
      if (isRandom.compareTo(1) == 0) {
        Collections.shuffle(avgB);
      }
      int sort = 0;
      StringBuilder body = new StringBuilder();
      for (int i = 0; i < avgB.size(); i++) {
        body.append(avgB.get(i));
        if (i != 0 && i % 400 == 0) {
          pageNum++;
        }
        if (body.length() == 4) {
          SimulationRouterRoomPageEntity pageEntity = new SimulationRouterRoomPageEntity();
          pageEntity.setKey(body.toString());
          pageEntity.setPageNumber(pageNum);
          pageEntity.setSort(sort % 100);
          pageEntity.setRoomId(train.getRoomId());
          ret.add(pageEntity);
          sort++;
          body = new StringBuilder();
        }
      }
    }
    return pageDao.save(ret);
  }
}
