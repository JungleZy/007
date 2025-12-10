package com.nip.service.simulation;

import com.google.gson.reflect.TypeToken;
import com.nip.common.constants.SimulationRoomTypeEnum;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.UserDao;
import com.nip.dao.simulation.*;
import com.nip.dto.SimulationRouterRoomSimpDto;
import com.nip.dto.vo.param.simulation.router.SimulationRoomRouterAddParam;
import com.nip.dto.vo.param.simulation.router.SimulationRoomRouterChangeParam;
import com.nip.dto.vo.simulation.SimulationRouterRoomPageInfoVO;
import com.nip.dto.vo.simulation.SimulationRouterRoomPageVo;
import com.nip.dto.vo.simulation.router.SimulationRouterRoomUserInfoVO;
import com.nip.dto.vo.simulation.router.SimulationRouterRoomUserVO;
import com.nip.dto.vo.simulation.router.SimulationRouterRoomVO;
import com.nip.entity.UserEntity;
import com.nip.entity.simulation.router.*;
import com.nip.service.CableFloorService;
import com.nip.service.UserService;
import com.nip.ws.WebSocketSimulationService;
import com.nip.ws.model.SimulationUserModel;
import com.nip.ws.service.simulation.SimulationGlobal;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.nip.common.constants.BaseConstants.TOKEN;

@ApplicationScoped
public class SimulationRouterRoomService {
  public static final String STRING = "未找到用户信息";
  private final SimulationRouterRoomDao routerRoomDao;
  private final SimulationRouterRoomUserDao roomUserDao;
  private final SimulationRouterRoomContentDao roomContentDao;
  private final UserService userService;
  private final UserDao userDao;
  private final SimulationRouterRoomPageDao pageDao;
  private final SimulationRouterRoomPageValueDao pageValueDao;
  private final CableFloorService cableFloorService;

  @Inject
  public SimulationRouterRoomService(SimulationRouterRoomDao routerRoomDao,
                                     SimulationRouterRoomUserDao roomUserDao,
                                     SimulationRouterRoomContentDao roomContentDao,
                                     UserService userService,
                                     UserDao userDao,
                                     SimulationRouterRoomPageDao pageDao,
                                     SimulationRouterRoomPageValueDao pageValueDao,
                                     CableFloorService cableFloorService) {
    this.routerRoomDao = routerRoomDao;
    this.roomUserDao = roomUserDao;
    this.roomContentDao = roomContentDao;
    this.userService = userService;
    this.userDao = userDao;
    this.pageDao = pageDao;
    this.pageValueDao = pageValueDao;
    this.cableFloorService = cableFloorService;
  }

  /**
   * 添加房间
   * <p>
   * 该方法用于处理添加模拟路由房间的请求，包括保存房间信息、房间报底内容，
   * 生成房间报底数据，以及保存房间对应人员信息
   *
   * @param request HTTP请求对象，用于获取请求头中的令牌信息
   * @param param   添加房间请求参数对象，包含房间名称、报底内容等信息
   */
  @Transactional(rollbackOn = Exception.class)
  public void addRoom(HttpServerRequest request, SimulationRoomRouterAddParam param) {
    //保存房间信息
    UserEntity userEntity = userService.getUserByToken(request.getHeader(TOKEN));
    SimulationRouterRoomEntity roomEntity = new SimulationRouterRoomEntity();
    roomEntity.setName(param.getRoomName());
    roomEntity.setIsCable(param.getIsCable());
    roomEntity.setCreateUserId(userEntity.getId());
    roomEntity.setStats(0);
    roomEntity.setRoomType(SimulationRoomTypeEnum.ROUTER.getType());
    SimulationRouterRoomEntity room = routerRoomDao.save(roomEntity);

    //保存房间报底
    SimulationRouterRoomContentEntity roomContentEntity = new SimulationRouterRoomContentEntity();
    roomContentEntity.setContent(param.getContent());
    roomContentEntity.setRoomId(room.getId());
    roomContentEntity.setBdType(param.getBdType());
    roomContentEntity.setBwCount(param.getBwCount());
    roomContentEntity.setBwType(param.getBwType());
    roomContentEntity.setIsRandom(param.getIsRandom());
    SimulationRouterRoomContentEntity save = roomContentDao.save(roomContentEntity);

    //生成房间报底
    if (param.getIsCable() == 0) {
      Integer bwCount = param.getBwCount();
      Integer generateNumber = 200;
      if (bwCount.compareTo(200) < 0) {
        generateNumber = bwCount;
      }
      int index = save.getBwType().compareTo(3) == 0 ? 65 : 0;
      List<SimulationRouterRoomPageEntity> simulationRouterRoomPageEntities = generateMessageBody(generateNumber, 1, index, save);
      pageDao.save(simulationRouterRoomPageEntities);
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

    // 发报人
    for (int i = 0; i < sendUserList.size(); i++) {
      String sendId = sendUserList.get(i);
      SimulationRouterRoomUserEntity sendUser = new SimulationRouterRoomUserEntity();
      sendUser.setRoomId(room.getId());
      sendUser.setUserId(sendId);
      sendUser.setChannel(i);
      sendUser.setUserType(0);
      sendUser.setUserStatus(0);
      roomUser.add(sendUser);
    }

    // 收报人
    for (int i = 0; i < receiveUserList.size(); i++) {
      String receiveId = receiveUserList.get(i);
      SimulationRouterRoomUserEntity receiveUser = new SimulationRouterRoomUserEntity();
      receiveUser.setRoomId(roomEntity.getId());
      receiveUser.setUserId(receiveId);
      receiveUser.setChannel(i);
      receiveUser.setUserType(1);
      receiveUser.setUserStatus(0);
      roomUser.add(receiveUser);
    }
    roomUserDao.save(roomUser);
  }

  /**
   * 查询该人员的所有房间
   *
   * @param request Http服务器请求，用于从请求头中获取令牌
   * @return 返回一个包含SimulationRouterRoomVO对象的列表，表示查询到的房间信息
   */
  public List<SimulationRouterRoomVO> findRoom(HttpServerRequest request) {
    UserEntity userEntity = userService.getUserByToken(request.getHeader(TOKEN));
    List<SimulationRouterRoomSimpDto> allByUserId = routerRoomDao.findAllByUserIdSimp(userEntity.getId());
    return Objects.requireNonNull(JSONUtils.fromJson(JSONUtils.toJson(allByUserId), new TypeToken<List<SimulationRouterRoomVO>>() {
        }))
        .stream()
        .distinct()
        .toList();
  }

  public SimulationRouterRoomUserVO getRoomUserList(Integer roomId) {
    List<SimulationRouterRoomUserEntity> allByRoomId = roomUserDao.findByRoomId(roomId);
    SimulationRouterRoomUserVO roomUserVO = new SimulationRouterRoomUserVO();
    List<WebSocketSimulationService> webSocketSimulationServices = Optional.ofNullable(SimulationGlobal.routerRoom.get(roomId))
        .orElseGet(ArrayList::new);
    Map<String, List<WebSocketSimulationService>> collect = webSocketSimulationServices.stream()
        .collect(Collectors.groupingBy(item -> item.getUserModel().getId()));
    for (SimulationRouterRoomUserEntity userEntity : allByRoomId) {
      String userId = userEntity.getUserId();
      userDao.findByIdOptional(userId).ifPresent(user -> {
        SimulationRouterRoomUserInfoVO userInfoVO = PojoUtils.convertOne(user, SimulationRouterRoomUserInfoVO.class, (e, v) -> v.setStatus(userEntity.getUserStatus()));
        userInfoVO.setChannel(userEntity.getChannel());
        //设置填报数量
        long existPageNumber = pageValueDao.countByUserIdAndRoomId(userId, roomId);
        userInfoVO.setExistPageNumber(existPageNumber);
        //设置 离线 在线 准备状态
        List<WebSocketSimulationService> ws = collect.get(userId);
        if (!Objects.isNull(ws)) {
          userInfoVO.setSocketStatus(ws.getFirst().getUserModel().getStatus());
        } else {
          userInfoVO.setSocketStatus(0);
        }
        if (userEntity.getUserType().compareTo(0) == 0) {
          roomUserVO.getSendUserList().add(userInfoVO);
        } else {
          roomUserVO.getReceiveUserList().add(userInfoVO);
        }
      });
    }
    return roomUserVO;
  }

  /**
   * 更改信道号
   *
   * @param param 包含用户ID、房间ID和新信道号的参数对象
   * @return 更新后的模拟路由器房间用户信息VO对象
   */
  @Transactional(rollbackOn = Exception.class)
  public SimulationRouterRoomUserVO changeChannel(SimulationRoomRouterChangeParam param) {
    SimulationRouterRoomUserEntity routerRoomUserEntity = roomUserDao.findByUserIdAndRoomId(param.getUserId(), param.getRoomId());
    routerRoomUserEntity = Optional.ofNullable(routerRoomUserEntity).orElseThrow(() -> new IllegalArgumentException(STRING));
    routerRoomUserEntity.setChannel(param.getChannel());
    roomUserDao.save(routerRoomUserEntity);
    //更改内存中的
    Map<Integer, List<WebSocketSimulationService>> routerRoom = SimulationGlobal.routerRoom;
    Optional.ofNullable(routerRoom.get(param.getRoomId()))
        .ifPresent(room -> {
          for (WebSocketSimulationService socketSimulation : room) {
            SimulationUserModel userModel = socketSimulation.getUserModel();
            if (Objects.equals(userModel.getId(), param.getUserId())) {
              userModel.setChannel(param.getChannel());
            }
          }
        });
    return getRoomUserList(param.getRoomId());
  }

  /**
   * 查询房间详情
   *
   * @param request Http请求对象，用于获取请求头中的Token信息
   * @param roomId  房间ID，用于查询房间详细信息
   * @return 返回房间详情对象SimulationRouterRoomVO
   * @throws Exception 如果未查询到房间信息，则抛出IllegalArgumentException异常
   */
  public SimulationRouterRoomVO getRoomDetail(HttpServerRequest request, Integer roomId) {
    String token = request.getHeader(TOKEN);
    UserEntity entity = userService.getUserByToken(token);
    SimulationRouterRoomEntity roomEntity = routerRoomDao.findByIdOptional(roomId)
        .orElseThrow(() -> new IllegalArgumentException("未查询到房间信息"));
    SimulationRouterRoomContentEntity byRoomId = roomContentDao.findByRoomIdRouter(roomId);
    SimulationRouterRoomUserEntity userRoomEntity;
    if (entity.getId().equals(roomEntity.getCreateUserId())) {
      userRoomEntity = new SimulationRouterRoomUserEntity();
    } else {
      userRoomEntity = Optional.ofNullable(roomUserDao.findByUserIdAndRoomId(entity.getId(), roomId))
          .orElseGet(SimulationRouterRoomUserEntity::new);
    }
    //查询已填报页数
    long existPageNumber = pageValueDao.countByUserIdAndRoomId(entity.getId(), roomId);
    //查询房间人员信息
    SimulationRouterRoomUserEntity finalUserRoomEntity = userRoomEntity;
    return PojoUtils.convertOne(byRoomId, SimulationRouterRoomVO.class, (e, v) -> {
      v.setExistPageNumber(existPageNumber);
      v.setIsCable(roomEntity.getIsCable());
      v.setCurrentUserChannel(finalUserRoomEntity.getChannel());
      v.setCurrentUserStatus(finalUserRoomEntity.getUserStatus());
      v.setCurrentUserId(entity.getId());
      v.setCreateUserId(roomEntity.getCreateUserId());
      v.setStats(roomEntity.getStats());
      if (roomEntity.getStats().compareTo(1) == 0) {
        long startTime = Timestamp.valueOf(roomEntity.getStartTime()).getTime();
        long currentTimeMillis = System.currentTimeMillis();
        long totalTime = (currentTimeMillis - startTime) / 1000;
        v.setTotalTime((int) totalTime);
      } else {
        v.setTotalTime(roomEntity.getTotalTime());
      }
      v.setStartTime(Optional.ofNullable(roomEntity.getStartTime())
          .map(item -> item.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
          .orElse(""));
      if (roomEntity.getIsCable() == 1) {
        v.setPageCount(pageDao.findMaxPageNumber(roomId));
        v.setBwCount((int) pageDao.count("roomId", roomId));
      }
    });
  }

  public List<Integer> getRoomChannels(Integer roomId) {
    return roomUserDao.findByRoomId(roomId)
        .stream()
        .filter(item -> item.getUserType().compareTo(0) == 0)
        .map(SimulationRouterRoomUserEntity::getChannel)
        .toList();
  }

  @Transactional(rollbackOn = Exception.class)
  public SimulationRouterRoomVO sendFinish(Integer roomId, HttpServerRequest request) {
    UserEntity userEntity = userService.getUserByToken(request.getHeader(TOKEN));
    //查询用户是否是发送放
    SimulationRouterRoomUserEntity roomUserEntity = roomUserDao.findByUserIdAndRoomId(userEntity.getId(), roomId);
    roomUserEntity = Optional.ofNullable(roomUserEntity).orElseThrow(() -> new IllegalArgumentException(STRING));
    if (roomUserEntity.getUserType().compareTo(0) != 0) {
      throw new IllegalArgumentException("该用户不是发报人员");
    }
    roomUserEntity.setUserStatus(1);
    roomUserDao.save(roomUserEntity);
    //查询出相同channel的接收方,将其状态更改未已完成
    List<SimulationRouterRoomUserEntity> receiveUserEntities = roomUserDao.findAllByRoomIdAndChannelAndUserType(
        roomId,
        roomUserEntity.getChannel(),
        1);
    for (SimulationRouterRoomUserEntity item : receiveUserEntities) {
      item.setUserStatus(1);
    }
    roomUserDao.save(receiveUserEntities);
    return getRoomDetail(request, roomId);
  }


  public SimulationRouterRoomPageInfoVO findPage(String userId, Integer roomId, Integer pageNumber) {
    SimulationRouterRoomContentEntity contentEntity = roomContentDao.findByRoomIdRouter(roomId);
    contentEntity = Optional.ofNullable(contentEntity).orElseThrow(() -> new IllegalArgumentException(STRING));
    if (pageNumber == null) {
      pageNumber = 1;
    }
    if (pageNumber <= 0) {
      throw new IllegalArgumentException("页码不正确");
    }
    Integer totalNumber = contentEntity.getBwCount();
    int totalPage = totalNumber / 100;
    if (totalNumber % 100 > 0) {
      totalPage += 1;
    }
    //查询页的内容
    List<SimulationRouterRoomPageEntity> pageEntity = pageDao.findByRoomIdAndPageNumberOrderBySort(roomId, pageNumber);
    //查询当前人员当前页填报内容
    SimulationRouterRoomPageValueEntity pageValueEntity = pageValueDao.findByRoomIdAndPageNumberAndUserId(roomId, pageNumber, userId);
    List<String> value = Optional.ofNullable(pageValueEntity)
        .map(SimulationRouterRoomPageValueEntity::getValue)
        .map(v -> JSONUtils.fromJson(v, new TypeToken<List<String>>() {
        }))
        .orElseGet(ArrayList::new);

    if (pageEntity.isEmpty() && pageNumber <= totalPage) {
      int generateNumber = 100;
      if (pageNumber.compareTo(totalPage) == 0) {
        generateNumber = totalNumber - ((pageNumber - 1) * 100);
      }
      int index = 0;
      if (contentEntity.getBwType().compareTo(3) == 0) {
        index = ((pageNumber - 1) * 400) % 26 + 65;
      } else if (contentEntity.getBwType().compareTo(4) == 0) {
        index = ((pageNumber - 1) * 400) % 36;
      }
      pageEntity = generateMessageBody(generateNumber, pageNumber, index, contentEntity);
    }
    SimulationRouterRoomPageInfoVO ret = new SimulationRouterRoomPageInfoVO();
    pageEntity.sort(Comparator.comparingInt(SimulationRouterRoomPageEntity::getSort));
    ret.setPageVos(PojoUtils.convert(pageEntity, SimulationRouterRoomPageVo.class));
    ret.setValue(value);
    return ret;
  }

  /**
   * 生成报底
   *
   * @param generateNumber 报底
   * @param pageNumber     页码
   * @param index          上次位置
   * @param train          训练对象
   */
  private List<SimulationRouterRoomPageEntity> generateMessageBody(Integer generateNumber, Integer pageNumber, int index, SimulationRouterRoomContentEntity train) {
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
          if (i % 100 == 0 && i != 0) {
            pageNumber += 1;
          }
          StringBuilder body = new StringBuilder();
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
