package com.nip.service.simulation;

import cn.hutool.core.util.ObjectUtil;
import com.google.gson.reflect.TypeToken;
import com.nip.common.constants.SimulationRoomTypeEnum;
import com.nip.common.utils.JSONUtils;
import com.nip.dao.UserDao;
import com.nip.dao.simulation.*;
import com.nip.dto.SimulationRouterRoomContentMessageDto;
import com.nip.dto.SimulationRouterRoomContentRecordDto;
import com.nip.dto.SimulationRouterRoomUserDto;
import com.nip.dto.vo.param.simulation.router.SimulationDisturdDetailParam;
import com.nip.dto.vo.param.simulation.router.SimulationRoomRouterContentAddParam;
import com.nip.dto.vo.simulation.disturd.SimulationDisturdDetailVO;
import com.nip.dto.vo.simulation.disturd.SimulationDisturdSettingVO;
import com.nip.dto.vo.simulation.disturd.SimulationDisturdTrainVO;
import com.nip.dto.vo.simulation.disturd.SimulationDisturdUploadResultVO;
import com.nip.dto.vo.simulation.router.SimulationRouterRoomContentVO;
import com.nip.entity.UserEntity;
import com.nip.entity.simulation.router.*;
import com.nip.service.CableFloorService;
import com.nip.service.UserService;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.*;

import static com.nip.common.constants.BaseConstants.TOKEN;

@ApplicationScoped
public class SimulationRouterRoomContentService {
  private final SimulationRouterRoomDao routerRoomDao;
  private final SimulationRouterRoomUserDao roomUserDao;
  private final SimulationRouterRoomContentDao roomContentDao;
  private final UserService userService;
  private final SimulationRouterRoomPageDao pageDao;
  private final SimulationRouterRoomPageValueDao pageValueDao;
  private final CableFloorService cableFloorService;

  @Inject
  public SimulationRouterRoomContentService(
      SimulationRouterRoomDao routerRoomDao,
      SimulationRouterRoomUserDao roomUserDao,
      SimulationRouterRoomContentDao roomContentDao,
      UserDao userDao,
      UserService userService,
      SimulationRouterRoomPageDao pageDao,
      SimulationRouterRoomPageValueDao pageValueDao,
      CableFloorService cableFloorService) {
    this.routerRoomDao = routerRoomDao;
    this.roomUserDao = roomUserDao;
    this.roomContentDao = roomContentDao;
    this.userService = userService;
    this.pageDao = pageDao;
    this.pageValueDao = pageValueDao;
    this.cableFloorService = cableFloorService;
  }

  @Transactional
  public Integer addRoomAndContent(HttpServerRequest request, SimulationRoomRouterContentAddParam param) {
    //保存房间信息
    UserEntity userEntity = userService.getUserByToken(request.getHeader(TOKEN));
    SimulationRouterRoomEntity roomEntity = new SimulationRouterRoomEntity();
    roomEntity.setName(param.getRoomName());
    roomEntity.setIsCable(param.getIsCable());
    roomEntity.setCreateUserId(userEntity.getId());
    roomEntity.setStats(0);
    roomEntity.setRoomType(SimulationRoomTypeEnum.DISTURB.getType());
    SimulationRouterRoomEntity room = routerRoomDao.save(roomEntity);

    //保存房间报底
    SimulationRouterRoomContentEntity roomContentEntity = new SimulationRouterRoomContentEntity();
    roomContentEntity.setMainSignal(param.getMainSignal());
    roomContentEntity.setInterferenceSignal(param.getInterferenceSignal());
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
      List<SimulationRouterRoomPageEntity> ret = generateMessageBody(generateNumber, 1, index, save);
      pageDao.save(ret);
    } else {
      List<List<List<String>>> cableFloor = cableFloorService.findCableFloor(param.getCableId(), null, param.getStartPage());
      int totalPage = param.getBwCount() / 100;
      cableFloor = cableFloor.subList(0, totalPage);
      for (int i = 0; i < cableFloor.size(); i++) {
        for (int j = 0; j < cableFloor.get(i).size(); j++) {
          SimulationRouterRoomPageEntity pageEntity = new SimulationRouterRoomPageEntity();
          pageEntity.setRoomId(room.getId());
          pageEntity.setKey(String.join("", cableFloor.get(i).get(j)));
          pageEntity.setPageNumber(i + 1);
          pageEntity.setSort(j);
          pageDao.save(pageEntity);
        }
      }
    }

    //保存房间对应人员信息
    SimulationRouterRoomUserEntity roomUser = new SimulationRouterRoomUserEntity();
    roomUser.setUserId(userEntity.getId());
    roomUser.setUserType(0);
    roomUser.setChannel(-1);
    roomUser.setRoomId(room.getId());
    roomUserDao.save(roomUser);
    return room.getId();
  }

  @Transactional
  public Integer addStudent(HttpServerRequest request, SimulationDisturdDetailParam param) {
    SimulationRouterRoomEntity byId = routerRoomDao.findById(param.getRoomId());
    UserEntity userEntity = userService.getUserByToken(request.getHeader(TOKEN));
    SimulationRouterRoomUserEntity user = roomUserDao.findByUserIdAndRoomId(userEntity.getId(), param.getRoomId());
    if (user == null) {
      SimulationRouterRoomUserEntity roomUser = new SimulationRouterRoomUserEntity();
      roomUser.setUserId(userEntity.getId());
      roomUser.setUserType(1);
      roomUser.setRoomId(param.getRoomId());
      roomUser.setUserStatus(0);
      if (byId.getRoomType() == 1) {
        roomUser.setChannel(-1);
      }
      roomUserDao.saveAndFlush(roomUser);
      return roomUser.getId();
    }
    return null;
  }

  public List<SimulationRouterRoomContentVO> findAlls(HttpServerRequest request) {
    List<SimulationRouterRoomContentRecordDto> allByUserId = roomContentDao.findAllRecord();
    return JSONUtils.fromJson(JSONUtils.toJson(allByUserId), new TypeToken<>() {
    });
  }

  public SimulationDisturdDetailVO findOne(HttpServerRequest request, Integer roomId) {
    String token = request.getHeader(TOKEN);
    UserEntity userEntity = userService.getUserByToken(token);

    if (ObjectUtil.isNotEmpty(roomId)) {
      long existPageNumber = pageValueDao.countByUserIdAndRoomId(userEntity.getId(), roomId);
      SimulationRouterRoomUserEntity roomUserEntity = roomUserDao.findByUserIdAndRoomId(userEntity.getId(), roomId);

      SimulationRouterRoomContentMessageDto allByUserId = roomContentDao.findMessage(roomId);
      SimulationDisturdDetailVO simulationDisturdDetailVO = JSONUtils.fromJson(JSONUtils.toJson(allByUserId), SimulationDisturdDetailVO.class);
      if (simulationDisturdDetailVO != null) {
        if (!Objects.isNull(roomUserEntity)) {
          simulationDisturdDetailVO.setContentValue(roomUserEntity.getContentValue());
        }
        simulationDisturdDetailVO.setExistPageNumber(existPageNumber);
        if (null != simulationDisturdDetailVO.getIsCable() && simulationDisturdDetailVO.getIsCable() == 1) {
          simulationDisturdDetailVO.setPageCount(pageDao.findMaxPageNumber(roomId));
          simulationDisturdDetailVO.setBwCount((int) pageDao.count("roomId", roomId));
        }
        return simulationDisturdDetailVO;
      }
    }
    return null;
  }

  public List<SimulationDisturdTrainVO> findTrainUser(HttpServerRequest request, Integer roomId) {
    List<SimulationRouterRoomUserDto> tranUser = roomUserDao.findTranUser(roomId);
    List<SimulationDisturdTrainVO> ret = JSONUtils.fromJson(JSONUtils.toJson(tranUser), new TypeToken<>() {
    });
    ret.forEach(item -> {
      long existPageNumber = pageValueDao.countByUserIdAndRoomId(item.getId(), roomId);
      item.setExistPageNumber(existPageNumber);
    });
    return ret;

  }

  @Transactional
  public SimulationDisturdDetailVO uploadResult(HttpServerRequest request, SimulationDisturdUploadResultVO detailVO) {
    SimulationRouterRoomUserEntity roomUserEntity = roomUserDao.findByUserIdAndRoomId(detailVO.getUserId(), detailVO.getRoomId());
    if (!Objects.isNull(roomUserEntity)) {
      //roomUserEntity.setContentValue(detailVO.getContentValue());
      roomUserEntity.setUserStatus(1);
      roomUserDao.save(roomUserEntity);
    }
    List<SimulationRouterRoomPageValueEntity> pageValueEntityList = getSimulationRouterRoomPageValueEntities(detailVO);
    pageValueDao.save(pageValueEntityList);
    return findOne(request, detailVO.getRoomId());
  }

  private static List<SimulationRouterRoomPageValueEntity> getSimulationRouterRoomPageValueEntities(SimulationDisturdUploadResultVO detailVO) {
    List<SimulationRouterRoomPageValueEntity> pageValueEntityList = new ArrayList<>();
    for (int i = 0; i < detailVO.getContentValue().size(); i++) {
      String value = detailVO.getContentValue().get(i);
      SimulationRouterRoomPageValueEntity pageValueEntity = new SimulationRouterRoomPageValueEntity();
      pageValueEntity.setPageNumber(i + 1);
      pageValueEntity.setRoomId(detailVO.getRoomId());
      pageValueEntity.setUserId(detailVO.getUserId());
      pageValueEntity.setValue(value);
      pageValueEntityList.add(pageValueEntity);
    }
    return pageValueEntityList;
  }

  /**
   * 保存配置
   *
   * @param vo
   */
  @Transactional
  public SimulationDisturdSettingVO saveSetting(SimulationDisturdSettingVO vo) {
    SimulationRouterRoomEntity roomEntity = routerRoomDao.findById(vo.getRoomId());
    roomEntity.setSetting(vo.getSetting());
    routerRoomDao.save(roomEntity);
    return vo;
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
        for (Integer i = 0; i < generateNumber; i++) {
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
      Collections.shuffle(avgB);
      int sort = 1;
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
    return ret;
  }
}
