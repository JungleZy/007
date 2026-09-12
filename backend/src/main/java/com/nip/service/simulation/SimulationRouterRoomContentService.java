package com.nip.service.simulation;

import cn.hutool.core.util.ObjectUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.nip.common.constants.SimulationRoomTypeEnum;
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
import com.nip.ws.model.SimulationSessionHolder;
import com.nip.ws.service.RoomDeletionTransaction;
import com.nip.ws.service.RoomLifecycleLocks;
import com.nip.dto.vo.simulation.disturd.SimulationDisturdTrainVO;
import com.nip.dto.vo.simulation.disturd.SimulationDisturdUploadResultVO;
import com.nip.dto.vo.simulation.router.SimulationRouterRoomContentVO;
import com.nip.entity.UserEntity;
import com.nip.entity.simulation.router.*;
import com.nip.service.CableFloorService;
import com.nip.service.UserService;
import com.nip.ws.WebSocketSimulationService;
import com.nip.ws.service.simulation.SimulationGlobal;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.*;

import java.util.concurrent.locks.Lock;
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
  RoomDeletionTransaction roomDeletionTransaction;
  @Inject SimulationResultNotifier resultNotifier;
  @Inject SimulationRoomAccess roomAccess;

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
    // Phase 7.4：isCable/bwCount/bwType 均为可空 Integer，下面 :96-:108 的裸拆箱会 NPE 成 500；
    // 入参校验前置到写库之前
    if (param.getIsCable() == null) {
      throw new IllegalArgumentException("是否使用电缆报底不能为空");
    }
    if (param.getBwCount() == null) {
      throw new IllegalArgumentException("报文组数不能为空");
    }
    if (Objects.equals(param.getIsCable(), 0) && param.getBwType() == null) {
      throw new IllegalArgumentException("报文类型不能为空");
    }
    // 保存房间信息
    UserEntity userEntity = userService.getUserByToken(request.getHeader(TOKEN));
    SimulationRouterRoomEntity roomEntity = new SimulationRouterRoomEntity();
    roomEntity.setName(param.getRoomName());
    roomEntity.setIsCable(param.getIsCable());
    roomEntity.setCreateUserId(userEntity.getId());
    roomEntity.setStats(0);
    roomEntity.setRoomType(SimulationRoomTypeEnum.DISTURB.getType());
    SimulationRouterRoomEntity room = routerRoomDao.save(roomEntity);
    routerRoomDao.lockRoom(room.getId());

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
    if (Objects.equals(param.getIsCable(), 0)) {
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
      if (totalPage <= 0) {
        throw new IllegalArgumentException("报文组数不足一页，无法建立房间");
      }
      if (totalPage > cableFloor.size()) {
        throw new IllegalArgumentException("所选电缆可用楼层不足");
      }
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
    SimulationRouterRoomEntity byId = routerRoomDao.lockRoom(param.getRoomId());
    UserEntity userEntity = userService.getUserByToken(request.getHeader(TOKEN));
    SimulationRouterRoomUserEntity user = roomUserDao.findByUserIdAndRoomId(userEntity.getId(), param.getRoomId());
    if (user == null) {
      SimulationRouterRoomUserEntity roomUser = new SimulationRouterRoomUserEntity();
      roomUser.setUserId(userEntity.getId());
      roomUser.setUserType(1);
      roomUser.setRoomId(param.getRoomId());
      roomUser.setUserStatus(0);
      if (Objects.equals(byId.getRoomType(), 1)) {
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
    boolean teacher = roomAccess.requireMember(request, roomId);
    String token = request.getHeader(TOKEN);
    UserEntity userEntity = userService.getUserByToken(token);

    if (ObjectUtil.isNotEmpty(roomId)) {
      long existPageNumber = pageValueDao.countByUserIdAndRoomId(userEntity.getId(), roomId);
      SimulationRouterRoomUserEntity roomUserEntity = roomUserDao.findByUserIdAndRoomId(userEntity.getId(), roomId);

      SimulationRouterRoomContentMessageDto allByUserId = roomContentDao.findMessage(roomId);
      SimulationDisturdDetailVO simulationDisturdDetailVO = PojoUtils.convertOne(allByUserId, SimulationDisturdDetailVO.class);
      if (simulationDisturdDetailVO != null) {
        simulationDisturdDetailVO.setTeacher(teacher);
        if (!Objects.isNull(roomUserEntity)) {
          simulationDisturdDetailVO.setContentValue(roomUserEntity.getContentValue());
        }
        simulationDisturdDetailVO.setExistPageNumber(existPageNumber);
        if (Objects.equals(simulationDisturdDetailVO.getIsCable(), 1)) {
          simulationDisturdDetailVO.setPageCount(pageDao.findMaxPageNumber(roomId));
          simulationDisturdDetailVO.setBwCount((int) pageDao.count("roomId", roomId));
        }
        return simulationDisturdDetailVO;
      }
    }
    return null;
  }

  public List<SimulationDisturdTrainVO> findTrainUser(HttpServerRequest request, Integer roomId) {
    boolean teacher = roomAccess.requireMember(request, roomId);
    String requester = userService.getUserByToken(request.getHeader(TOKEN)).getId();
    List<SimulationRouterRoomUserDto> tranUser = roomUserDao.findTranUser(roomId);
    List<SimulationDisturdTrainVO> ret = PojoUtils.convert(tranUser, SimulationDisturdTrainVO.class);
    ret.forEach(item -> {
      if (!teacher && !Objects.equals(requester, item.getId())) item.setContentValue(null);
      long existPageNumber = pageValueDao.countByUserIdAndRoomId(item.getId(), roomId);
      item.setExistPageNumber(existPageNumber);
    });
    return ret;

  }

  @Transactional(rollbackOn = Exception.class)
  public SimulationDisturdDetailVO uploadResult(HttpServerRequest request, SimulationDisturdUploadResultVO detailVO) {
    if (detailVO == null || detailVO.getContentValue() == null) {
      throw new IllegalArgumentException("请提交完整答案数组");
    }
    SimulationRouterRoomEntity room = routerRoomDao.lockRoom(detailVO.getRoomId());
    UserEntity user = userService.getUserByToken(request.getHeader(TOKEN));
    SimulationRouterRoomUserEntity roomUserEntity = roomUserDao.findByUserIdAndRoomId(user.getId(), room.getId());
    if (roomUserEntity == null || !Objects.equals(roomUserEntity.getUserType(), 1)) {
      throw new IllegalArgumentException("仅参训收报人员可提交答案");
    }
    if (!Objects.equals(room.getStats(), 1) && !Objects.equals(room.getStats(), 2)) {
      throw new IllegalArgumentException("训练尚未开始，不能提交答案");
    }
    for (String value : detailVO.getContentValue()) {
      if (value == null) {
        throw new IllegalArgumentException("答案页不能为空，请用空数组表示空页");
      }
      JsonElement page;
      try {
        page = JsonParser.parseString(value);
      } catch (JsonParseException e) {
        throw new IllegalArgumentException("答案页必须是字符串数组", e);
      }
      if (!page.isJsonArray()) {
        throw new IllegalArgumentException("答案页必须是字符串数组");
      }
      for (JsonElement group : page.getAsJsonArray()) {
        if (!group.isJsonPrimitive() || !group.getAsJsonPrimitive().isString()) {
          throw new IllegalArgumentException("答案页必须是字符串数组");
        }
      }
    }
    // A request replaces the whole answer, including pages omitted by a shorter retry.
    pageValueDao.deleteByRoomIdAndUserId(room.getId(), user.getId());
    List<SimulationRouterRoomPageValueEntity> pages = getSimulationRouterRoomPageValueEntities(detailVO, user.getId());
    pageValueDao.save(pages);
    pageValueDao.flush();
    roomUserEntity.setUserStatus(1);
    roomUserDao.saveAndFlush(roomUserEntity);
    resultNotifier.publish(room.getId(), user.getId(), room.getRoomType());
    return findOne(request, room.getId());
  }

  public boolean delete(Integer roomId) {
    Lock lock = RoomLifecycleLocks.simulationRoom(roomId);
    List<SimulationSessionHolder> removed;
    boolean deleted;
    lock.lock();
    try {
      deleted = roomDeletionTransaction.run(() -> {
        routerRoomDao.lockRoom(roomId);
        pageValueDao.delete("roomId=?1", roomId);
        pageDao.delete("roomId=?1", roomId);
        roomUserDao.delete("roomId=?1", roomId);
        roomContentDao.delete("roomId=?1", roomId);
        return routerRoomDao.deleteById(roomId);
      });
      removed = SimulationGlobal.disturbRoom.remove(roomId);
    } finally {
      lock.unlock();
    }
    WebSocketSimulationService.closeRoomSessions(removed, "房间已解散");
    return deleted;
  }

  private static List<SimulationRouterRoomPageValueEntity> getSimulationRouterRoomPageValueEntities(
      SimulationDisturdUploadResultVO detailVO, String userId) {
    List<SimulationRouterRoomPageValueEntity> pageValueEntityList = new ArrayList<>(detailVO.getContentValue().size());
    for (int i = 0; i < detailVO.getContentValue().size(); i++) {
      String value = detailVO.getContentValue().get(i);
      SimulationRouterRoomPageValueEntity pageValueEntity = new SimulationRouterRoomPageValueEntity();
      pageValueEntity.setPageNumber(i + 1);
      pageValueEntity.setRoomId(detailVO.getRoomId());
      pageValueEntity.setUserId(userId);
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
    SimulationRouterRoomEntity roomEntity = Optional.ofNullable(routerRoomDao.findById(vo.getRoomId()))
        .orElseThrow(() -> new IllegalArgumentException("未查询到该房间"));
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
