package com.nip.service.simulation;

import cn.hutool.core.util.ObjectUtil;
import com.google.gson.reflect.TypeToken;
import com.nip.common.constants.SimulationRoomTypeEnum;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.PojoUtils;
import com.nip.common.utils.SimulationMessageGenerator;
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
import com.nip.ws.service.simulation.SimulationGlobal;
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
    // 保存房间信息
    UserEntity userEntity = userService.getUserByToken(request.getHeader(TOKEN));
    SimulationRouterRoomEntity roomEntity = new SimulationRouterRoomEntity();
    roomEntity.setName(param.getRoomName());
    roomEntity.setIsCable(param.getIsCable());
    roomEntity.setCreateUserId(userEntity.getId());
    roomEntity.setStats(0);
    roomEntity.setRoomType(SimulationRoomTypeEnum.DISTURB.getType());
    SimulationRouterRoomEntity room = routerRoomDao.save(roomEntity);

    // 保存房间报底
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

    // 生成房间报底
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
      List<List<List<String>>> cableFloor = cableFloorService.findCableFloor(param.getCableId(), null,
          param.getStartPage());
      int totalPage = param.getBwCount() / 100;
      cableFloor = cableFloor.subList(0, totalPage);
      // 使用批量保存替代循环逐条保存，提升性能
      List<SimulationRouterRoomPageEntity> pageEntities = new ArrayList<>();
      for (int i = 0; i < cableFloor.size(); i++) {
        for (int j = 0; j < cableFloor.get(i).size(); j++) {
          SimulationRouterRoomPageEntity pageEntity = new SimulationRouterRoomPageEntity();
          pageEntity.setRoomId(room.getId());
          pageEntity.setKey(String.join("", cableFloor.get(i).get(j)));
          pageEntity.setPageNumber(i + 1);
          pageEntity.setSort(j);
          pageEntities.add(pageEntity);
        }
      }
      pageDao.save(pageEntities);
    }

    // 保存房间对应人员信息
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
    return PojoUtils.convert(allByUserId, SimulationRouterRoomContentVO.class);
  }

  public SimulationDisturdDetailVO findOne(HttpServerRequest request, Integer roomId) {
    String token = request.getHeader(TOKEN);
    UserEntity userEntity = userService.getUserByToken(token);

    if (ObjectUtil.isNotEmpty(roomId)) {
      long existPageNumber = pageValueDao.countByUserIdAndRoomId(userEntity.getId(), roomId);
      SimulationRouterRoomUserEntity roomUserEntity = roomUserDao.findByUserIdAndRoomId(userEntity.getId(), roomId);

      SimulationRouterRoomContentMessageDto allByUserId = roomContentDao.findMessage(roomId);
      SimulationDisturdDetailVO simulationDisturdDetailVO = PojoUtils.convertOne(allByUserId, SimulationDisturdDetailVO.class);
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
    List<SimulationDisturdTrainVO> ret = PojoUtils.convert(tranUser, SimulationDisturdTrainVO.class);
    ret.forEach(item -> {
      long existPageNumber = pageValueDao.countByUserIdAndRoomId(item.getId(), roomId);
      item.setExistPageNumber(existPageNumber);
    });
    return ret;

  }

  @Transactional
  public SimulationDisturdDetailVO uploadResult(HttpServerRequest request, SimulationDisturdUploadResultVO detailVO) {
    SimulationRouterRoomUserEntity roomUserEntity = roomUserDao.findByUserIdAndRoomId(detailVO.getUserId(),
        detailVO.getRoomId());
    if (!Objects.isNull(roomUserEntity)) {
      // roomUserEntity.setContentValue(detailVO.getContentValue());
      roomUserEntity.setUserStatus(1);
      roomUserDao.save(roomUserEntity);
    }
    List<SimulationRouterRoomPageValueEntity> pageValueEntityList = getSimulationRouterRoomPageValueEntities(detailVO);
    pageValueDao.save(pageValueEntityList);
    return findOne(request, detailVO.getRoomId());
  }

  @Transactional(rollbackOn = Exception.class)
  public boolean delete(Integer roomId) {
    pageValueDao.delete("roomId=?1", roomId);
    pageDao.delete("roomId=?1", roomId);
    roomUserDao.delete("roomId=?1", roomId);
    roomContentDao.delete("roomId=?1", roomId);
    SimulationGlobal.disturbRoom.remove(roomId);
    return routerRoomDao.deleteById(roomId);
  }

  private static List<SimulationRouterRoomPageValueEntity> getSimulationRouterRoomPageValueEntities(
      SimulationDisturdUploadResultVO detailVO) {
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
  private List<SimulationRouterRoomPageEntity> generateMessageBody(Integer generateNumber, Integer pageNumber,
      int index, SimulationRouterRoomContentEntity train) {
    return SimulationMessageGenerator.generateMessageBody(
        generateNumber, pageNumber, index, train, train.getRoomId(), entities -> entities);
  }
}
