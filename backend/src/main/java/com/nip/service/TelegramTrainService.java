package com.nip.service;


import cn.hutool.core.text.CharSequenceUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nip.common.exception.ForbiddenException;
import com.nip.common.exception.UnauthorizedException;
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
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
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
  private final TransactionManager transactionManager;
  private final ObjectMapper objectMapper;

  @Inject
  public TelegramTrainService(TelegramTrainDao telegramTrainDao, TelegramTrainFloorDao telegramTrainFloorDao,
                              TelegramTrainFloorContentDao telegramTrainFloorContentDao,
                              TelegramTrainSettingDao telegramTrainSettingDao, TelegramTrainLogDao telegramTrainLogDao,
                              UserService userService, TelegramTrainStatisticalDao statisticalDao,
                              TransactionManager transactionManager, ObjectMapper objectMapper) {
    this.telegramTrainDao = telegramTrainDao;
    this.telegramTrainFloorDao = telegramTrainFloorDao;
    this.telegramTrainFloorContentDao = telegramTrainFloorContentDao;
    this.telegramTrainSettingDao = telegramTrainSettingDao;
    this.telegramTrainLogDao = telegramTrainLogDao;
    this.userService = userService;
    this.statisticalDao = statisticalDao;
    this.transactionManager = transactionManager;
    this.objectMapper = objectMapper;
  }

  private final String[] dotArray = new String[]{"E", "I", "S", "H", "5"};

  private final String[] lineArray = new String[]{"M", "T", "0", "O"};

  private final String[] dotLineArray = new String[]{"A", "B", "C", "D", "F", "G", "J", "K", "L", "N", "P", "Q", "R",
      "U", "V", "W", "X", "Y", "Z", "1", "2", "3", "4", "6", "7", "8", "9"};
  private static final Map<String, String> MORSE = Map.ofEntries(
      Map.entry("A", "01"), Map.entry("B", "1000"), Map.entry("C", "1010"), Map.entry("D", "100"),
      Map.entry("E", "0"), Map.entry("F", "0010"), Map.entry("G", "110"), Map.entry("H", "0000"),
      Map.entry("I", "00"), Map.entry("J", "0111"), Map.entry("K", "101"), Map.entry("L", "0100"),
      Map.entry("M", "11"), Map.entry("N", "10"), Map.entry("O", "111"), Map.entry("P", "0110"),
      Map.entry("Q", "1101"), Map.entry("R", "010"), Map.entry("S", "000"), Map.entry("T", "1"),
      Map.entry("U", "001"), Map.entry("V", "0001"), Map.entry("W", "011"), Map.entry("X", "1001"),
      Map.entry("Y", "1011"), Map.entry("Z", "1100"), Map.entry("0", "11111"), Map.entry("1", "01111"),
      Map.entry("2", "00111"), Map.entry("3", "00011"), Map.entry("4", "00001"), Map.entry("5", "00000"),
      Map.entry("6", "10000"), Map.entry("7", "11000"), Map.entry("8", "11100"), Map.entry("9", "11110"));

  public Response<List<TelegramTrainEntity>> getAll(String token) {
    try {
      UserEntity userEntity = userService.getUserByToken(token);
      return ResponseResult.success(telegramTrainDao.findAllByCreateUserIdOrderByCreateTimeDesc(userEntity.getId()));
    } catch (UnauthorizedException e) {
      throw e;
    } catch (Exception e) {
      return ResponseResult.error();
    }
  }

  public Response<TelegramTrainDto> getById(String id, String token) {
    requireOwner(id, token);
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

  public Response<Map<String, List<TelegramTrainFloorContentEntity>>> getFloorContentByFloorId(List<String> ids, String token) {
    requireFloorOwner(ids, token);
    try {
      Map<String, List<TelegramTrainFloorContentEntity>> list = new HashMap<>();
      List<TelegramTrainFloorContentEntity> byFloorIdIn = telegramTrainFloorContentDao.findByFloorIdIn(ids);
      handleMaps(list, byFloorIdIn);
      return ResponseResult.success(list);
    } catch (Exception e) {
      log.error("getFloorContentByFloorId获取失败", e);
      return ResponseResult.error();
    }
  }

  public Response<Void> getFloorContentByFloorIdAsync(String token, String id) {
    requireOwner(id, token);
    UserEntity userEntity = userService.getUserByToken(token);
    asyncTask(userEntity.getId(), id);
    return ResponseResult.success();
  }

  /**
   * 按页取报底内容并经 WebSocket 推给调用者。
   *
   * <p>「缺页」（trainId+pageNumber 查无报底）是<b>查无即为错</b>：目标不存在但参数可修正后重试，
   * 因此抛 {@code IllegalArgumentException} 走 {@code ValidationExceptionMapper} → HTTP 200 + 业务码 202，
   * 而不是 208（208 留给「权限与参数都没问题、目标已终结、重试无意义」）。
   *
   * <p>原先这里裹着一层 {@code catch (Exception)}，缺页触发的 NPE 被吞成 {@code ResponseResult.error()}
   * 的通用 500 信封，调用方只看到「服务器错误」，真因丢失。本方法不写库、无 {@code @Transactional}，
   * 也没有需要补偿的资源，所以宽 catch 整体删除而不是缩小：业务异常直通各自 mapper，
   * 其余意外异常交给 {@code GlobalExceptionMapper}（HTTP 500 + SYSTEM_ERROR，堆栈进日志）。
   */
  public Response<Void> getFloorContentByFloorIdAsync(String token, String id, Integer pageNumber) {
    TelegramTrainFloorEntity floor = Optional
        .ofNullable(telegramTrainFloorDao.findAllByTrainIdAndPageNumber(id, pageNumber))
        .orElseThrow(() -> new IllegalArgumentException("未查询该训练的第 " + pageNumber + " 页报底！"));
    requireOwner(floor.getTrainId(), token);
    UserEntity userEntity = userService.getUserByToken(token);
    List<TelegramTrainFloorContentEntity> byFloorIdIn =
        telegramTrainFloorContentDao.findAllByFloorIdOrderBySort(floor.getId());
    Map<String, List<TelegramTrainFloorContentEntity>> list = new HashMap<>();
    list.put(floor.getId(), byFloorIdIn);
    WebSocketService.sendInfo(
        userEntity.getId(), new ResponseModel(FLOOR_CONTENT_DATA.getCode(), JSONUtils.toJson(list)));
    return ResponseResult.success();
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
  public Response<TelegramTrainEntity> controlTelegramTrain(int type, TelegramTrainDto trainDto, String token) {
    if (trainDto == null || trainDto.getTrain() == null || trainDto.getTrain().getId() == null) {
      throw new IllegalArgumentException("训练信息不能为空");
    }
    String id = trainDto.getTrain().getId();
    requireOwner(id, token);
    TelegramTrainEntity train = Optional.ofNullable(telegramTrainDao.findByIdForUpdate(id))
        .orElseThrow(() -> new IllegalArgumentException("未查询该训练！"));
    int current = train.getStatus() == null ? 0 : train.getStatus();
    if (current == 3) throw new IllegalStateException("训练已结束");
    if (!Integer.valueOf(1).equals(train.getProtocolVersion()) && (type == 1 || type == 2)) {
      throw new IllegalStateException("历史训练不支持新的生命周期操作");
    }
    validateTrainingSettings(trainDto.getTrain());
    TelegramTrainEntity requested = trainDto.getTrain();
    train.setRateDotMinMs(requested.getRateDotMinMs());
    train.setRateDotMaxMs(requested.getRateDotMaxMs());
    train.setRateLineMinMs(requested.getRateLineMinMs());
    train.setRateLineMaxMs(requested.getRateLineMaxMs());
    train.setRateIntervalMinMs(requested.getRateIntervalMinMs());
    train.setRateIntervalMaxMs(requested.getRateIntervalMaxMs());
    train.setBigIntervalMinMs(requested.getBigIntervalMinMs());
    train.setBigIntervalMaxMs(requested.getBigIntervalMaxMs());
    long now = System.currentTimeMillis();
    if (type == 0) {
      if (current == 1) return ResponseResult.success(train);
      if (current != 0 && current != 2) throw new IllegalStateException("训练状态不可开始");
      if (train.getAccumulatedActiveMillis() == null) train.setAccumulatedActiveMillis(0L);
      train.setActiveSince(now);
      if (StringUtils.isEmpty(train.getStartTime())) train.setStartTime(String.valueOf(now));
      train.setStatus(1);
    } else if (type == 1) {
      if (current == 2) return ResponseResult.success(train);
      if (current != 1) throw new IllegalStateException("训练未开始");
      validateFloorContentsBelongToTrain(id, trainDto.getTrainFloors());
      updateSubmittedContent(trainDto.getTrainFloors());
      recalculateFromPersistedRaw(train);
      closeActiveSegment(train, now);
      train.setPauseTime(String.valueOf(now));
      train.setStatus(2);
    } else if (type == 2) {
      if (current != 1 && current != 2) throw new IllegalStateException("训练未开始");
      if (current == 1) closeActiveSegment(train, now);
      validateFloorContentsBelongToTrain(id, trainDto.getTrainFloors());
      updateSubmittedContent(trainDto.getTrainFloors());
      recalculateFromPersistedRaw(train);
      train.setEndTime(String.valueOf(now));
      train.setStatus(3);
      if (requested.getNowFloorId() != null) {
        TelegramTrainFloorEntity nowFloor = Optional.ofNullable(telegramTrainFloorDao.findById(requested.getNowFloorId()))
            .orElseThrow(() -> new IllegalArgumentException("未查询当前报底"));
        if (!Objects.equals(id, nowFloor.getTrainId())) throw new ForbiddenException("当前报底不属于训练");
      }
      train.setNowFloorId(requested.getNowFloorId());
    } else throw new IllegalStateException("Unexpected value: " + type);
    TelegramTrainEntity saved = telegramTrainDao.save(train);
    if (saved.getStatus() == 3) finishStatistical(saved);
    return ResponseResult.success(saved);
  }

  private void recalculateFromPersistedRaw(TelegramTrainEntity train) {
    List<TelegramTrainFloorEntity> persistedFloors = telegramTrainFloorDao.findAllByTrainIdOrderBySort(train.getId());
    List<TelegramTrainFloorDto> all = new ArrayList<>(persistedFloors.size());
    for (TelegramTrainFloorEntity floor : persistedFloors) {
      TelegramTrainFloorDto dto = new TelegramTrainFloorDto();
      dto.setFloor(floor);
      dto.setFloorContents(telegramTrainFloorContentDao.findAllByFloorIdOrderBySort(floor.getId()));
      all.add(dto);
    }
    recalculateFromRaw(train, all);
  }

  private static void closeActiveSegment(TelegramTrainEntity train, long now) {
    if (train.getActiveSince() != null) {
      long delta = Math.max(0, now - train.getActiveSince());
      train.setAccumulatedActiveMillis((train.getAccumulatedActiveMillis() == null ? 0 : train.getAccumulatedActiveMillis()) + delta);
      train.setActiveSince(null);
      train.setSustainTime(String.valueOf(train.getAccumulatedActiveMillis()));
    }
  }

  private void updateSubmittedContent(List<TelegramTrainFloorDto> floors) {
    if (floors == null) return;
    for (TelegramTrainFloorDto floor : floors) {
      if (floor == null || floor.getFloorContents() == null) throw new IllegalArgumentException("报底内容不能为空");
      for (TelegramTrainFloorContentEntity content : floor.getFloorContents()) {
        if (content == null || content.getId() == null) throw new IllegalArgumentException("未查询报文内容");
        telegramTrainFloorContentDao.update("moresValue=?1,moresTime=?2 where id=?3", content.getMoresValue(),
            CharSequenceUtil.isEmpty(content.getMoresTime()) ? "[]" : content.getMoresTime(), content.getId());
      }
    }
  }

  private void recalculateFromRaw(TelegramTrainEntity train, List<TelegramTrainFloorDto> floors) {
    int total = 0;
    int errors = 0;
    if (floors != null) {
      for (TelegramTrainFloorDto floor : floors) {
        if (floor == null || floor.getFloorContents() == null) throw new IllegalArgumentException("报底内容无效");
        for (TelegramTrainFloorContentEntity content : floor.getFloorContents()) {
          if (content == null || CharSequenceUtil.isEmpty(content.getMoresKey())) throw new IllegalArgumentException("报文原始内容无效");
          try {
            JsonNode keyNode = objectMapper.readTree(content.getMoresKey());
            String rawValue = content.getMoresValue();
            JsonNode valueNode = CharSequenceUtil.isEmpty(rawValue) ? null : objectMapper.readTree(rawValue);
            List<String> keys = new ArrayList<>();
            if (keyNode != null && keyNode.isArray()) keyNode.forEach(node -> keys.add(node.asText()));
            else if (keyNode != null && keyNode.isTextual()) keys.add(keyNode.asText());
            total += keys.size();
            if (valueNode == null) { errors += keys.size(); continue; }
            if (!valueNode.isArray()) throw new IllegalArgumentException("拍发值必须为数组");
            if (valueNode.size() > keys.size()) errors += valueNode.size() - keys.size();
            for (int i = 0; i < keys.size(); i++) {
              String expected = MORSE.get(keys.get(i).toUpperCase(Locale.ROOT));
              JsonNode actual = valueNode.get(i);
              if (expected == null || actual == null || !actual.isArray() || actual.size() != expected.length()) { errors++; continue; }
              for (int j = 0; j < expected.length(); j++) if (actual.get(j).asInt(-1) != expected.charAt(j) - '0') { errors++; break; }
            }
          } catch (JsonProcessingException | IllegalArgumentException ex) { throw new IllegalArgumentException("报文原始内容无效", ex); }
        }
      }
    }
    train.setTotalNumber(total);
    train.setErrorNumber(errors);
    train.setTotalKnockNumber(Math.max(0, total - errors));
    train.setAccuracy(total == 0 ? "0" : BigDecimal.valueOf(Math.max(0, total - errors) * 100.0 / total).stripTrailingZeros().toPlainString());
    train.setSpeed(train.getAccumulatedActiveMillis() == null || train.getAccumulatedActiveMillis() == 0 ? "0" : BigDecimal.valueOf(total * 60000.0 / train.getAccumulatedActiveMillis()).stripTrailingZeros().toPlainString());
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
      statisticalEntity = PojoUtils.convertOne(groupTrain, TelegramTrainStatisticalEntity.class);
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
      //楼层必须先校验后写，否则上一次训练的状态变更会被半量提交
      List<TelegramTrainFloorDto> trainFloors = trainDto.getTrainFloors();
      if (trainFloors == null || trainFloors.isEmpty()) {
        throw new IllegalArgumentException("训练楼层不能为空");
      }
      TelegramTrainEntity trainEntity = trainDto.getTrain();
      validateTrainingSettings(trainEntity);
      TelegramTrainEntity train = trainDto.getTrain();
      train.setId(null);
      train.setCreateUserId(userEntity.getId());
      train.setProtocolVersion(1);
      train.setAccumulatedActiveMillis(0L);
      train.setActiveSince(null);
      TelegramTrainEntity savedTrain = telegramTrainDao.save(train);
      for (int i = 0; i < trainFloors.size(); i++) {
        TelegramTrainFloorDto floorDto = trainFloors.get(i);
        TelegramTrainFloorEntity floor = floorDto.getFloor();
        floor.setId(null);
        floor.setTrainId(savedTrain.getId());
        floor.setSort(i);
        floor.setContentNumber(floorDto.getFloorContents().size());
        TelegramTrainFloorEntity savedFloor = telegramTrainFloorDao.save(floor);
        List<TelegramTrainFloorContentEntity> floorContents = floorDto.getFloorContents();
        for (int j = 0; j < floorContents.size(); j++) {
          TelegramTrainFloorContentEntity source = floorContents.get(j);
          TelegramTrainFloorContentEntity entity = new TelegramTrainFloorContentEntity();
          entity.setFloorId(savedFloor.getId());
          entity.setSort(j);
          entity.setMoresKey(source.getMoresKey());
          entity.setMoresValue(source.getMoresValue());
          entity.setMoresTime(CharSequenceUtil.isEmpty(source.getMoresTime()) ? "[]" : source.getMoresTime());
          telegramTrainFloorContentDao.save(entity);
        }
      }
      return ResponseResult.success(savedTrain);
    } catch (UnauthorizedException e) {
      throw e;
    } catch (IllegalArgumentException | IllegalStateException e) {
      //入参校验失败交由专用 Mapper 返回参数错误信封，不得降级成裸 error()
      throw e;
    } catch (Exception e) {
      try {
        transactionManager.setRollbackOnly();
      } catch (SystemException rollbackFailure) {
        e.addSuppressed(rollbackFailure);
        throw new IllegalStateException("无法标记手键训练保存事务回滚", e);
      }
      log.error("save", e);
      return ResponseResult.error();
    }
  }

  @Transactional
  public Response<Void> saveFloorContent(Map<String, String> map, String token) {
    requireOwnerByContent(map == null ? null : map.get(ID), token);
    try {
      telegramTrainFloorContentDao.update("moresValue=?1,moresTime=?2 where id = ?3", map.get("moresValue"),
          CharSequenceUtil.isEmpty(map.get("moresTime")) ? "[]" : map.get("moresTime"),
          map.get(ID)
      );
      return ResponseResult.success();
    } catch (Exception e) {
      try {
        transactionManager.setRollbackOnly();
      } catch (SystemException rollbackFailure) {
        e.addSuppressed(rollbackFailure);
        throw new IllegalStateException("无法标记楼层内容保存事务回滚", e);
      }
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

  public Response<List<TelegramTrainLogEntity>> getTelegramTrainLogByTelegramTrainId(String telegramTrainId, String token) {
    requireOwner(telegramTrainId, token);
    return ResponseResult.success(telegramTrainLogDao.findAllByTelegramTrainIdOrderByCreatTimeAsc(telegramTrainId));
  }

  public Response<List<TelegramTrainSettingEntity>> getSetting() {
    return ResponseResult.success(telegramTrainSettingDao.findAll().list());
  }

  @Transactional
  public Response<List<TelegramTrainSettingEntity>> saveSetting(List<TelegramTrainSettingEntity> list) {
    validateSettings(list);
    List<TelegramTrainSettingEntity> replacement = list.stream()
        .map(item -> new TelegramTrainSettingEntity(null, item.getType(), item.getKey(), item.getValue()))
        .toList();
    telegramTrainSettingDao.deleteAll();
    return ResponseResult.success(telegramTrainSettingDao.save(replacement));
  }

  private static void validateTrainingSettings(TelegramTrainEntity train) {
    if (train == null) throw new IllegalArgumentException("训练配置不能为空");
    validateTrainingRange(train.getRateDotMinMs(), train.getRateDotMaxMs());
    validateTrainingRange(train.getRateLineMinMs(), train.getRateLineMaxMs());
    validateTrainingRange(train.getRateIntervalMinMs(), train.getRateIntervalMaxMs());
    validateTrainingRange(train.getBigIntervalMinMs(), train.getBigIntervalMaxMs());
  }


  private static void validateTrainingRange(Integer min, Integer max) {
    if (min == null || max == null || min < 0 || max <= min) {
      throw new IllegalArgumentException("训练配置须为非负毫秒值，且上界大于下界");
    }
  }

  private void validateSettings(List<TelegramTrainSettingEntity> list) {
    if (list == null || list.isEmpty()) {
      throw new IllegalArgumentException("基础练习配置不能为空");
    }
    Map<String, Map<Integer, JsonNode>> groups = new HashMap<>();
    groups.put("0", new HashMap<>());
    groups.put("1", new HashMap<>());
    for (TelegramTrainSettingEntity item : list) {
      if (item == null || !Integer.valueOf(0).equals(item.getType()) || !groups.containsKey(item.getKey())) {
        throw new IllegalArgumentException("基础练习配置类型无效");
      }
      JsonNode value;
      try {
        value = objectMapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .readTree(item.getValue());
      } catch (JsonProcessingException | IllegalArgumentException e) {
        throw new IllegalArgumentException("基础练习配置JSON无效", e);
      }
      if (value == null || !value.isObject() || !value.path("type").isIntegralNumber()
          || !value.path("type").canConvertToInt() || value.path("type").intValue() < 0
          || !value.path("name").isTextual() || value.path("name").asText().isBlank()
          || !value.path("msg").isTextual() || value.path("msg").asText().isBlank()) {
        throw new IllegalArgumentException("基础练习分级名称、文案或类型无效");
      }
      int type = value.get("type").intValue();
      if (groups.get(item.getKey()).putIfAbsent(type, value) != null) {
        throw new IllegalArgumentException("基础练习存在重复分级");
      }
      BigDecimal min = settingBoundary(value.get("min"), type == 0 ? "<" : "");
      BigDecimal max = settingBoundary(value.get("max"), type == 0 ? ">" : "");
      if (max.compareTo(min) <= 0) throw new IllegalArgumentException("基础练习区间上界必须大于下界");
    }
    for (Map<Integer, JsonNode> group : groups.values()) {
      if (!group.containsKey(0) || group.size() < 2) throw new IllegalArgumentException("点和划均须包含异常区间及正区间");
      BigDecimal min = null, max = null;
      for (Map.Entry<Integer, JsonNode> entry : group.entrySet()) if (entry.getKey() != 0) {
        BigDecimal lower = settingBoundary(entry.getValue().get("min"), "");
        BigDecimal upper = settingBoundary(entry.getValue().get("max"), "");
        min = min == null ? lower : min.min(lower);
        max = max == null ? upper : max.max(upper);
      }
      if (settingBoundary(group.get(0).get("min"), "<").compareTo(min) != 0
          || settingBoundary(group.get(0).get("max"), ">").compareTo(max) != 0) throw new IllegalArgumentException("异常区间必须覆盖正区间的外边界");
    }
    if (!groups.get("0").keySet().equals(groups.get("1").keySet())) {
      throw new IllegalArgumentException("点和划的分级必须完整对应");
    }
  }

  private static BigDecimal settingBoundary(JsonNode value, String prefix) {
    if (value == null || (!value.isTextual() && !value.isNumber())) {
      throw new IllegalArgumentException("配置毫秒值必须为有限的非负数");
    }
    String text = value.asText().trim();
    if (!prefix.isEmpty()) {
      if (!value.isTextual() || !text.startsWith(prefix)) {
        throw new IllegalArgumentException("基础练习缺少合法异常区间");
      }
      text = text.substring(1).trim();
    }
    if (!text.matches("(?:\\d+(?:\\.\\d*)?|\\.\\d+)")) {
      throw new IllegalArgumentException("配置毫秒值必须为有限的非负数");
    }
    BigDecimal number = new BigDecimal(text);
    if (!Double.isFinite(number.doubleValue())) {
      throw new IllegalArgumentException("配置毫秒值必须为有限的非负数");
    }
    return number;
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
    return convert;
  }

  public TelegramTrainEntity lastTrain(String token, Integer type) {
    UserEntity userEntity = userService.getUserByToken(token);
    return telegramTrainDao.lastTrain(userEntity.getId(), type);
  }

  private void validateFloorContentsBelongToTrain(String trainId, List<TelegramTrainFloorDto> floors) {
    if (floors == null) throw new IllegalArgumentException("报底不能为空");
    for (TelegramTrainFloorDto floorDto : floors) {
      if (floorDto == null || floorDto.getFloorContents() == null) throw new IllegalArgumentException("报底内容不能为空");
      String declaredFloorId = floorDto.getFloor() == null ? null : floorDto.getFloor().getId();
      for (TelegramTrainFloorContentEntity content : floorDto.getFloorContents()) {
        if (content == null || content.getId() == null) throw new IllegalArgumentException("未查询报文内容");
        TelegramTrainFloorContentEntity persisted = Optional.ofNullable(telegramTrainFloorContentDao.findById(content.getId())).orElseThrow(() -> new IllegalArgumentException("未查询报文内容"));
        if (declaredFloorId != null && !Objects.equals(declaredFloorId, persisted.getFloorId())) throw new ForbiddenException("报文内容不属于提交报底");
        TelegramTrainFloorEntity ownerFloor = Optional.ofNullable(telegramTrainFloorDao.findById(persisted.getFloorId())).orElseThrow(() -> new IllegalArgumentException("未查询报底"));
        if (!Objects.equals(trainId, ownerFloor.getTrainId())) throw new ForbiddenException("报文内容不属于当前训练");
      }
    }
  }

  private void requireFloorOwner(String floorId, String token) {
    TelegramTrainFloorEntity floor = Optional.ofNullable(telegramTrainFloorDao.findById(floorId)).orElseThrow(() -> new IllegalArgumentException("未查询报底"));
    requireOwner(floor.getTrainId(), token);
  }
  private void requireFloorOwner(List<String> ids, String token) {
    if (ids == null || ids.isEmpty()) throw new IllegalArgumentException("报底不能为空");
    for (String id : ids) requireFloorOwner(id, token);
  }
  private void requireOwnerByContent(String contentId, String token) {
    TelegramTrainFloorContentEntity content = Optional.ofNullable(telegramTrainFloorContentDao.findById(contentId)).orElseThrow(() -> new IllegalArgumentException("未查询报文内容"));
    requireFloorOwner(content.getFloorId(), token);
  }
  private void requireOwnerByFloor(String floorId, String token) { requireFloorOwner(floorId, token); }
  private void requireOwner(String trainId, String token) {
    UserEntity actor = userService.getUserByToken(token);
    TelegramTrainEntity train = Optional.ofNullable(telegramTrainDao.findById(trainId))
        .orElseThrow(() -> new IllegalArgumentException("未查询该训练！"));
    if (!Objects.equals(actor.getId(), train.getCreateUserId())) {
      throw new ForbiddenException("无权访问他人训练");
    }
  }
}
