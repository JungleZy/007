package com.nip.service;


import cn.hutool.core.text.CharSequenceUtil;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.*;
import com.nip.dto.TelegramBaseTrainDto;
import com.nip.dto.TelegramTrainDto;
import com.nip.dto.TelegramTrainFloorDto;
import com.nip.dto.vo.TelegramTrainStatisticalVO;
import com.nip.entity.*;
import com.nip.ws.WebSocketService;
import com.nip.ws.model.ResponseModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static com.nip.common.constants.BaseConstants.ID;
import static com.nip.common.constants.CodeConstants.FLOOR_CONTENT_DATA;
import static com.nip.common.constants.CodeConstants.FLOOR_CONTENT_DATA_OVER;

/**
 * TelegramTrainService
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2021-12-20 11:33
 */
@Slf4j
@ApplicationScoped
public class TelegramTrainService {
  private final TelegramTrainDao telegramTrainDao;
  private final TelegramTrainFloorDao telegramTrainFloorDao;
  private final TelegramTrainFloorContentDao telegramTrainFloorContentDao;
  private final TelegramTrainSettingDao telegramTrainSettingDao;
  private final TelegramTrainLogDao telegramTrainLogDao;
  private final UserService userService;
  private final TelegramTrainStatisticalDao statisticalDao;

  @Inject
  public TelegramTrainService(TelegramTrainDao telegramTrainDao, TelegramTrainFloorDao telegramTrainFloorDao,
                              TelegramTrainFloorContentDao telegramTrainFloorContentDao,
                              TelegramTrainSettingDao telegramTrainSettingDao, TelegramTrainLogDao telegramTrainLogDao,
                              UserService userService, TelegramTrainStatisticalDao statisticalDao) {
    this.telegramTrainDao = telegramTrainDao;
    this.telegramTrainFloorDao = telegramTrainFloorDao;
    this.telegramTrainFloorContentDao = telegramTrainFloorContentDao;
    this.telegramTrainSettingDao = telegramTrainSettingDao;
    this.telegramTrainLogDao = telegramTrainLogDao;
    this.userService = userService;
    this.statisticalDao = statisticalDao;
  }

  private final String[] dotArray = new String[]{"E", "I", "S", "H", "5"};

  private final String[] lineArray = new String[]{"M", "T", "0", "O"};

  private final String[] dotLineArray = new String[]{"A", "B", "C", "D", "F", "G", "J", "K", "L", "N", "P", "Q", "R",
      "U", "V", "W", "X", "Y", "Z", "1", "2", "3", "4", "6", "7", "8", "9"};

  public Response<List<TelegramTrainEntity>> getAll(String token) {
    try {
      UserEntity userEntity = userService.getUserByToken(token);
      return ResponseResult.success(telegramTrainDao.findAllByCreateUserIdOrderByCreateTimeDesc(userEntity.getId()));
    } catch (Exception e) {
      return ResponseResult.error();
    }
  }

  public Response<TelegramTrainDto> getById(String id) {
    TelegramTrainEntity trainEntity = Optional.ofNullable(telegramTrainDao.findById(id))
        .orElseThrow(() -> new IllegalArgumentException("未查询该训练！"));
    TelegramTrainDto telegramTrainDto = new TelegramTrainDto();
    telegramTrainDto.setTrain(trainEntity);
    List<TelegramTrainFloorEntity> floorEntities = telegramTrainFloorDao.findAllByTrainIdOrderBySort(
        trainEntity.getId());
    List<TelegramTrainFloorDto> floorDtos = new ArrayList<>(floorEntities.size());
    List<TelegramTrainFloorContentEntity> floorContentEntities;
    if (trainEntity.getNowFloorId() == null) {
      floorContentEntities = telegramTrainFloorContentDao.findAllByFloorIdOrderBySort(floorEntities.getFirst().getId());
    } else {
      floorContentEntities = telegramTrainFloorContentDao.findAllByFloorIdOrderBySort(trainEntity.getNowFloorId());
    }
    for (int i = 0; i < floorEntities.size(); i++) {
      TelegramTrainFloorDto floorDto = new TelegramTrainFloorDto();
      floorDto.setFloor(floorEntities.get(i));
      if (trainEntity.getNowFloorId() == null && i == 0) {
        floorDto.setFloorContents(floorContentEntities);
      } else {
        if (Objects.equals(floorEntities.get(i).getId(), trainEntity.getNowFloorId())) {
          floorDto.setFloorContents(floorContentEntities);
        } else {
          floorDto.setFloorContents(new ArrayList<>());
        }
      }
      floorDtos.add(floorDto);
    }
    telegramTrainDto.setTrainFloors(floorDtos);
    return ResponseResult.success(telegramTrainDto);
  }

  public Response<Map<String, List<TelegramTrainFloorContentEntity>>> getFloorContentByFloorId(List<String> ids) {
    try {
      Map<String, List<TelegramTrainFloorContentEntity>> list = new HashMap<>();
      List<TelegramTrainFloorContentEntity> byFloorIdIn = telegramTrainFloorContentDao.findByFloorIdIn(ids);
      handleMaps(list, byFloorIdIn);
      return ResponseResult.success(list);
    } catch (Exception e) {
      log.error("getFloorContentByFloorId获取失败：{}", e.getMessage());
      return ResponseResult.error();
    }
  }

  public Response<Void> getFloorContentByFloorIdAsync(String token, String id) {
    try {
      UserEntity userEntity = userService.getUserByToken(token);
      asyncTask(userEntity.getId(), id);
      return ResponseResult.success();
    } catch (Exception e) {
      log.error("getFloorContentByFloorIdAsync获取失败：{}", e.getMessage());
      return ResponseResult.error();
    }
  }

  public Response<Void> getFloorContentByFloorIdAsync(String token, String id, Integer pageNumber) {
    try {
      UserEntity userEntity = userService.getUserByToken(token);
      TelegramTrainFloorEntity allByTrainIdAndPageNumber = telegramTrainFloorDao.findAllByTrainIdAndPageNumber(id, pageNumber);
      List<TelegramTrainFloorContentEntity> byFloorIdIn = telegramTrainFloorContentDao.findAllByFloorIdOrderBySort(allByTrainIdAndPageNumber.getId());
      Map<String, List<TelegramTrainFloorContentEntity>> list = new HashMap<>();
      list.put(allByTrainIdAndPageNumber.getId(), byFloorIdIn);
      WebSocketService.sendInfo(
          userEntity.getId(), new ResponseModel(FLOOR_CONTENT_DATA.getCode(), JSONUtils.toJson(list)));
      return ResponseResult.success();
    } catch (Exception e) {
      log.error("getFloorContentByFloorIdAsync获取失败：{}", e.getMessage());
      return ResponseResult.error();
    }
  }

  public void asyncTask(String userId, String id) {
    List<TelegramTrainFloorEntity> all = telegramTrainFloorDao.findAllByTrainIdOrderBySort(id);
    List<String> ls = new ArrayList<>();
    for (int j = 0; j < all.size(); j++) {
      ls.add(all.get(j).getId());
      if (ls.size() == 5 || (j + 1) == all.size()) {
        Map<String, List<TelegramTrainFloorContentEntity>> list = new HashMap<>();
        List<TelegramTrainFloorContentEntity> byFloorIdIn = telegramTrainFloorContentDao.findByFloorIdIn(ls);
        handleMaps(list, byFloorIdIn);
        WebSocketService.sendInfo(
            userId, new ResponseModel(FLOOR_CONTENT_DATA.getCode(), JSONUtils.toJson(list)));
        ls.clear();
      }
    }
    WebSocketService.sendInfo(userId, new ResponseModel(FLOOR_CONTENT_DATA_OVER.getCode()));
  }

  @Transactional
  public Response<TelegramTrainEntity> controlTelegramTrain(int type, TelegramTrainDto trainDto) {
    TelegramTrainEntity trainEntity = telegramTrainDao.findById(trainDto.getTrain().getId());
    assert trainEntity != null;
    boolean flag = false;
    switch (type) {
      case 0 -> {
        if (StringUtils.isEmpty(trainEntity.getStartTime())) {
          trainEntity.setStartTime(String.valueOf(new Date().getTime()));
        }
        trainEntity.setStatus(1);
      }
      case 1 -> {
        trainEntity.setPauseTime(String.valueOf(new Date().getTime()));
        trainEntity.setStatus(2);
        flag = true;
      }
      case 2 -> {
        trainEntity.setEndTime(String.valueOf(new Date().getTime()));
        trainEntity.setStatus(3);
        flag = true;
      }
      default -> throw new IllegalStateException("Unexpected value: " + type);
    }
    if (flag) {
      TelegramTrainEntity train = trainDto.getTrain();
      trainEntity.setErrorNumber(train.getErrorNumber());
      trainEntity.setAccuracy(train.getAccuracy());
      trainEntity.setSpeed(train.getSpeed());
      trainEntity.setSustainTime(train.getSustainTime());
      trainEntity.setTotalKnockNumber(train.getTotalKnockNumber());
      trainEntity.setNowFloorId(train.getNowFloorId());
    }
    //如状态是1
    trainEntity = telegramTrainDao.save(trainEntity);
    if (flag) {
      trainDto.getTrainFloors().forEach(floor -> {
        for (int i = 0; i < floor.getFloorContents().size(); i++) {
          telegramTrainFloorContentDao.update("moresValue=?1 where id =?2",
              floor.getFloorContents().get(i).getMoresValue(),
              floor.getFloorContents().get(i).getId()
          );
        }
      });
    }
    //如果是完成训练，需要进行统计
    if (trainEntity.getStatus().compareTo(3) == 0) {
      finishStatistical(trainEntity);
    }
    return ResponseResult.success(trainEntity);
  }

  /**
   * 训练完成统计
   *
   * @param: trainEntity
   */
  private void finishStatistical(TelegramTrainEntity trainEntity) {
    TelegramTrainStatisticalEntity statistical;
    TelegramTrainStatisticalEntity statisticalEntity;

    //0 1 2 - 11 12 13 14 - 21
    if (trainEntity.getType() < 10) {
      statistical = statisticalDao.findByUserIdAndType(trainEntity.getCreateUserId(), 0);
      if (statistical == null) {
        statistical = new TelegramTrainStatisticalEntity();
        statistical.setUserId(trainEntity.getCreateUserId());
        statistical.setType(0);
      }
      //查询单字训练
      Map<String, Object> wordTrain = telegramTrainDao.findWordTrain(trainEntity.getCreateUserId());
      statisticalEntity = JSONUtils.fromJson(
          JSONUtils.toJson(wordTrain), TelegramTrainStatisticalEntity.class);
    } else {
      statistical = statisticalDao.findByUserIdAndType(trainEntity.getCreateUserId(), 1);
      if (statistical == null) {
        statistical = new TelegramTrainStatisticalEntity();
        statistical.setUserId(trainEntity.getCreateUserId());
        statistical.setType(1);
      }
      //查询单字训练
      Map<String, Object> groupTrain = telegramTrainDao.findGroupTrain(trainEntity.getCreateUserId());
      statisticalEntity = JSONUtils.fromJson(JSONUtils.toJson(groupTrain), TelegramTrainStatisticalEntity.class);
    }
    statistical.setAvgSpeed(statisticalEntity.getAvgSpeed());
    statistical.setTotalCount(statisticalEntity.getTotalCount());
    statistical.setTotalTime(statisticalEntity.getTotalTime());
    statisticalDao.save(statistical);
  }

  @Transactional
  public Response<TelegramTrainEntity> save(String token, TelegramTrainDto trainDto) {
    try {
      UserEntity userEntity = userService.getUserByToken(token);
      TelegramTrainEntity trainEntity = trainDto.getTrain();
      trainEntity.setCreateUserId(userEntity.getId());

      //查询同类型同用户最后一次训练的状态，如状态是未开始0，则删除，如状态是暂停中2将状态设为已完成，且统计。
      TelegramTrainEntity lastTrain = telegramTrainDao.lastTrain(userEntity.getId(), trainDto.getTrain().getType());
      if (lastTrain != null) {
        if (lastTrain.getStatus().compareTo(2) == 0) {
          lastTrain.setStatus(3);
          lastTrain.setEndTime(String.valueOf(System.currentTimeMillis()));
          telegramTrainDao.save(lastTrain);
          finishStatistical(lastTrain);
        } else if (lastTrain.getStatus().compareTo(0) == 0) {
          telegramTrainDao.deleteById(lastTrain.getId());
        }
      }

      TelegramTrainEntity train = telegramTrainDao.save(trainEntity);
      for (int i = 0; i < trainDto.getTrainFloors().size(); i++) {
        TelegramTrainFloorEntity floor = trainDto.getTrainFloors().get(i).getFloor();
        floor.setTrainId(train.getId());
        floor.setSort(i);
        floor.setContentNumber(trainDto.getTrainFloors().get(i).getFloorContents().size());
        TelegramTrainFloorEntity save = telegramTrainFloorDao.save(floor);
        List<TelegramTrainFloorContentEntity> floorContents = trainDto.getTrainFloors().get(i).getFloorContents();
        for (int j = 0; j < floorContents.size(); j++) {
          TelegramTrainFloorContentEntity entity = new TelegramTrainFloorContentEntity();
          entity.setFloorId(save.getId());
          entity.setSort(j);
          entity.setMoresKey(floorContents.get(j).getMoresKey());
          entity.setMoresValue(floorContents.get(j).getMoresValue());
          entity.setMoresTime(CharSequenceUtil.isEmpty(floorContents.get(j).getMoresTime())
              ? "[]"
              : floorContents.get(j).getMoresTime());
          telegramTrainFloorContentDao.save(entity);
        }
      }
      return ResponseResult.success(train);
    } catch (Exception e) {
      return ResponseResult.error();
    }
  }

  @Transactional
  public Response<Void> saveFloorContent(Map<String, String> map) {
    try {
      telegramTrainFloorContentDao.update("mores_value=?1,morse_time=?2 where id = ?3", map.get("moresValue"),
          CharSequenceUtil.isEmpty(map.get("moresTime")) ? "[]" : map.get("moresTime"),
          map.get(ID)
      );
      return ResponseResult.success();
    } catch (Exception e) {
      log.error("saveFloorContent error", e);
      return ResponseResult.error();
    }
  }

  private void handleMaps(Map<String, List<TelegramTrainFloorContentEntity>> list,
                          List<TelegramTrainFloorContentEntity> maps) {
    for (TelegramTrainFloorContentEntity map : maps) {
      if (list.get(map.getFloorId()) == null) {
        List<TelegramTrainFloorContentEntity> list1 = new ArrayList<>(0);
        list1.add(map);
        list.put(map.getFloorId(), list1);
      } else {
        list.get(map.getFloorId()).add(map);
      }
    }
  }

  public Response<List<TelegramTrainLogEntity>> getTelegramTrainLogByTelegramTrainId(String telegramTrainId) {
    return ResponseResult.success(telegramTrainLogDao.findAllByTelegramTrainIdOrderByCreatTimeAsc(telegramTrainId));
  }

  public Response<List<TelegramTrainSettingEntity>> getSetting() {
    return ResponseResult.success(telegramTrainSettingDao.findAll().list());
  }

  @Transactional
  public Response<List<TelegramTrainSettingEntity>> saveSetting(List<TelegramTrainSettingEntity> list) {
    telegramTrainSettingDao.deleteAll();
    return ResponseResult.success(telegramTrainSettingDao.save(list));
  }

  /**
   * 保存基础练习，用户界面统计
   *
   * @param: dto
   */
  @Transactional
  public TelegramTrainEntity saveBaseTrain(TelegramBaseTrainDto dto, String token) {
    UserEntity userEntity = userService.getUserByToken(token);
    TelegramTrainEntity entity = PojoUtils.convertOne(dto, TelegramTrainEntity.class);
    entity.setType(21);
    entity.setStatus(3);
    entity.setCreateUserId(userEntity.getId());
    TelegramTrainEntity save = telegramTrainDao.save(entity);
    //统计基础训练
    TelegramTrainStatisticalEntity statisticalEntity = statisticalDao.findByUserIdAndType(userEntity.getId(), 2);
    if (statisticalEntity == null) {
      statisticalEntity = new TelegramTrainStatisticalEntity();
      statisticalEntity.setType(2);
      statisticalEntity.setUserId(userEntity.getId());
    }
    Map<String, Object> baseTrain = telegramTrainDao.findBaseTrain(userEntity.getId());
    TelegramTrainStatisticalEntity baseTrainEntity = JSONUtils.fromJson(
        JSONUtils.toJson(baseTrain),
        TelegramTrainStatisticalEntity.class
    );
    statisticalEntity.setAvgSpeed(baseTrainEntity.getAvgSpeed());
    statisticalEntity.setTotalCount(baseTrainEntity.getTotalCount());
    statisticalEntity.setTotalTime(baseTrainEntity.getTotalTime());
    statisticalDao.save(statisticalEntity);
    return save;
  }

  /**
   * 查询拍发训练统计页面
   *
   * @param: token
   */
  @Transactional
  public List<TelegramTrainStatisticalVO> statisticalPage(String token) {
    UserEntity userEntity = userService.getUserByToken(token);
    List<TelegramTrainStatisticalEntity> entities = statisticalDao.findByUserId(userEntity.getId());
    Map<Integer, List<TelegramTrainStatisticalEntity>> map = entities.stream().collect(
        Collectors.groupingBy(TelegramTrainStatisticalEntity::getType));
    for (int i = 0; i < 3; i++) {
      if (map.get(i) == null) {
        TelegramTrainStatisticalEntity entity = new TelegramTrainStatisticalEntity();
        entity.setType(i);
        entity.setUserId(userEntity.getId());
        entity.setTotalTime("0");
        entity.setTotalCount(0);
        entity.setAvgSpeed(new BigDecimal(0));
        TelegramTrainStatisticalEntity save = statisticalDao.save(entity);
        entities.add(save);
      }

    }
    List<TelegramTrainStatisticalVO> convert = PojoUtils.convert(entities, TelegramTrainStatisticalVO.class);
    convert.sort(Comparator.comparingInt(TelegramTrainStatisticalVO::getType));
    convert.addFirst(convert.getLast());
    convert.removeLast();
    return convert;
  }

  public TelegramTrainEntity lastTrain(String token, Integer type) {
    UserEntity userEntity = userService.getUserByToken(token);
    return telegramTrainDao.lastTrain(userEntity.getId(), type);
  }
}
