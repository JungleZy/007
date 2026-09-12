package com.nip.service.general;

import cn.hutool.core.text.CharSequenceUtil;
import com.google.gson.reflect.TypeToken;
import com.nip.common.PageInfo;
import com.nip.common.constants.CodeConstants;
import com.nip.common.constants.PostTelegramTrainEnum;
import com.nip.common.constants.PostTelegramTrainTypeEnum;
import com.nip.common.constants.TrainConstants;
import com.nip.common.exception.ForbiddenException;
import com.nip.common.response.Response;
import com.nip.common.utils.ArraysSafeUtils;
import com.nip.common.utils.GlobalMessageGeneratedUtil;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.Page;
import com.nip.common.utils.PojoUtils;
import com.nip.common.utils.ScoreMath;
import com.nip.controller.general.GeneralKeyPatTrainController;
import com.nip.dao.GradingRuleDao;
import com.nip.dao.UserDao;
import com.nip.dao.general.key.GeneralKeyPatDao;
import com.nip.dao.general.key.GeneralKeyPatPageDao;
import com.nip.dao.general.key.GeneralKeyPatTrainMoreDao;
import com.nip.dao.general.key.GeneralKeyPatUserDao;
import com.nip.dao.general.key.GeneralKeyPatUserValueDao;
import com.nip.dao.general.key.GeneralKeyPatUserValueResolverDao;
import com.nip.dto.KeyPatPageTransferDto;
import com.nip.dto.KeyPatStatisticalDto;
import com.nip.dto.KeyPatValueTransferDto;
import com.nip.dto.PostKeyPatTrainRuleDto;
import com.nip.dto.CaptureInterval;
import com.nip.dto.general.CapturedPage;
import com.nip.dto.score.TrainingRateUnit;
import com.nip.common.utils.CaptureTimeline;
import com.nip.common.utils.ScoreMath;
import com.nip.common.utils.ScoringRuleValidation;
import com.nip.dto.general.AvgResult;
import com.nip.dto.general.GeneralKeyPatAddParamDto;
import com.nip.dto.general.GeneralKeyPatFinishDto;
import com.nip.dto.general.GeneralKeyPatPageDetailDto;
import com.nip.dto.general.GeneralKeyPatPageDto;
import com.nip.dto.general.GeneralKeyPatPageParamDto;
import com.nip.dto.general.GeneralKeyPatPageSubmitDto;
import com.nip.dto.general.GeneralKeyPatPageSyncDto;
import com.nip.dto.general.GeneralKeyPatSyncDto;
import com.nip.dto.general.GeneralKeyPatTrainDto;
import com.nip.dto.general.GeneralKeyPatTrainMoreSyncDto;
import com.nip.dto.general.GeneralKeyPatTrainUserValueVO;
import com.nip.dto.general.GeneralKeyPatTrainVO;
import com.nip.dto.general.GeneralKeyPatUserInfoVO;
import com.nip.dto.general.GeneralKeyPatUserSyncDto;
import com.nip.dto.general.GeneralKeyPatUserValueSyncDto;
import com.nip.dto.general.GeneralPatTrainUserDto;
import com.nip.dto.general.GeneralPatTrainRoomUserDto;
import com.nip.dto.general.UserSyncDto;
import com.nip.dto.general.statistic.GeneralKeyPatTrainErrorCollect;
import com.nip.dto.general.statistic.GeneralKeyPatTrainStatisticVO;
import com.nip.dto.vo.PostTelegraphKeyPatResolverDetailVO;
import com.nip.dto.vo.PostTelegraphKeyPatTrainPageAnalyzeVO;
import com.nip.dto.vo.PostTelegraphKeyPatTrainPageMessageVO;
import com.nip.dto.vo.PostTelegraphKeyPatTrainPageVO;
import com.nip.entity.GradingRuleEntity;
import com.nip.entity.UserEntity;
import com.nip.entity.simulation.key.GeneralKeyPatEntity;
import com.nip.entity.simulation.key.GeneralKeyPatPageEntity;
import com.nip.entity.simulation.key.GeneralKeyPatTrainMoreEntity;
import com.nip.entity.simulation.key.GeneralKeyPatUserEntity;
import com.nip.entity.simulation.key.GeneralKeyPatUserValueEntity;
import com.nip.entity.simulation.key.GeneralKeyPatUserValueResolverEntity;
import com.nip.service.CableFloorService;
import com.nip.service.TrainWriteAccess;
import com.nip.service.UserService;
import com.nip.ws.WebSocketGeneralKeyPatService;
import com.nip.ws.WebSocketService;
import com.nip.ws.service.RoomDeletionTransaction;
import com.nip.ws.service.RoomLifecycleLocks;
import com.nip.ws.model.ResponseModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.locks.Lock;

import com.nip.common.utils.PatTrainStatisticsBuilder;
import static com.nip.common.utils.KeyPatUtils.handle;

@Slf4j
@ApplicationScoped
public class GeneralKeyPatService {
  public static final String REGEX = "[],\"]";
  private final GeneralKeyPatDao trainDao;
  private final GeneralKeyPatPageDao trainPageDao;
  private final GeneralKeyPatUserDao trainUserDao;
  private final GeneralKeyPatUserValueDao userValueDao;
  private final GeneralKeyPatTrainMoreDao moreEntityDao;
  private final GeneralKeyPatUserValueResolverDao resolverDao;
  private final GradingRuleDao gradingRuleDao;
  private final UserService userService;
  private final UserDao userDao;
  private final CableFloorService cableFloorService;
  @Inject
  RoomDeletionTransaction roomDeletionTransaction;

  @Inject GeneralPatResultNotifier resultNotifier;

  /**
   * 写/导出授权的唯一口径。构造器已被 GeneralSettlementRecoveryTest 以固定实参列表调用，
   * 这里用字段注入避免改动构造器签名。
   */
  @Inject TrainWriteAccess trainWriteAccess;

  @Inject
  public GeneralKeyPatService(GeneralKeyPatDao trainDao,
      GeneralKeyPatPageDao trainPageDao,
      GeneralKeyPatUserDao trainUserDao,
      GeneralKeyPatUserValueDao userValueDao,
      GeneralKeyPatTrainMoreDao moreEntityDao,
      GeneralKeyPatUserValueResolverDao resolverDao,
      GradingRuleDao gradingRuleDao,
      UserService userService,
      UserDao userDao,
      CableFloorService cableFloorService) {
    this.trainDao = trainDao;
    this.trainPageDao = trainPageDao;
    this.trainUserDao = trainUserDao;
    this.userValueDao = userValueDao;
    this.moreEntityDao = moreEntityDao;
    this.resolverDao = resolverDao;
    this.gradingRuleDao = gradingRuleDao;
    this.userService = userService;
    this.userDao = userDao;
    this.cableFloorService = cableFloorService;
  }

  /**
   * 添加训练
   */
  @Transactional
  public GeneralKeyPatTrainVO add(GeneralKeyPatAddParamDto param, String token) {
    // Phase 7.4：isCable/totalNumber/messageType/isAverage/isRandom 均为可空 Integer，
    // 下面 :170-:211 的裸拆箱会 NPE 成 500；入参校验前置到写库之前
    if (param.getIsCable() == null) {
      throw new IllegalArgumentException("是否使用电缆报底不能为空");
    }
    if (param.getTotalNumber() == null) {
      throw new IllegalArgumentException("训练总组数不能为空");
    }
    if (Objects.equals(param.getIsCable(), 0)
        && (param.getMessageType() == null || param.getIsAverage() == null || param.getIsRandom() == null)) {
      throw new IllegalArgumentException("报文类型与生成方式不能为空");
    }
    UserEntity currentUser = userService.getUserByToken(token);
    GeneralKeyPatEntity trainEntity = PojoUtils.convertOne(param, GeneralKeyPatEntity.class);
    trainEntity.setCreateUser(currentUser.getId());
    // 默认状态为未开始
    trainEntity.setStatus(0);
    // 有效时间设置为0
    trainEntity.setValidTime(0L);
    // 获取ruleContent
    GradingRuleEntity ruleOp = Optional.ofNullable(gradingRuleDao.findById(trainEntity.getRuleId()))
        .orElseThrow(() -> new IllegalArgumentException("未查询到评分规则"));
    ScoringRuleValidation.electronic(ruleOp.getContent());
    if (ruleOp.getScore() == null || ruleOp.getScore() <= 0) {
      throw new IllegalArgumentException("评分规则满分不合法");
    }
    trainEntity.setProtocolVersion(1);
    trainEntity.setRuleContent(JSONUtils.toJson(ruleOp));
    GeneralKeyPatEntity save = trainDao.save(trainEntity);

    // 保存参训人员信息
    List<GeneralKeyPatUserEntity> trainUserEntityList = new ArrayList<>();
    for (String id : param.getUserId()) {
      GeneralKeyPatUserEntity trainUser = new GeneralKeyPatUserEntity();
      trainUser.setAccuracy("0.00");
      trainUser.setUserId(id);
      trainUser.setTrainId(save.getId());
      trainUser.setErrorNumber(0);
      trainUser.setRole(0);
      trainUser.setIsFinish(0);
      trainUser.setScore(BigDecimal.ZERO);
      trainUserEntityList.add(trainUser);
      WebSocketService.sendInfo(id, new ResponseModel(CodeConstants.NOTIFICATION_NEW_TRAIN.getCode(),
          Map.of(
              "type", "key",
              "id", save.getId(),
              "title", save.getTitle())));
    }
    GeneralKeyPatUserEntity groupUser = new GeneralKeyPatUserEntity();
    groupUser.setTrainId(save.getId());
    groupUser.setUserId(currentUser.getId());
    groupUser.setRole(1);
    trainUserEntityList.add(groupUser);
    trainUserDao.save(trainUserEntityList);

    // 生成报文begin
    if (Objects.equals(param.getIsCable(), 0)) {
      Integer messageNumber = param.getTotalNumber();
      int generate = Math.min(messageNumber, TrainConstants.MAX_GENERATE_MESSAGE_COUNT);
      Integer type = param.getMessageType();

      List<GeneralKeyPatPageEntity> ret = new ArrayList<>();
      List<String> messageBody;
      // 生成对应的报文
      if (type.compareTo(PostTelegramTrainTypeEnum.NUMBER_MESSAGE.getType()) == 0) {
        messageBody = GlobalMessageGeneratedUtil.generatedNumber(generate, param.getIsAverage().equals(1),
            param.getIsRandom().equals(1));
      } else if (type.compareTo(PostTelegramTrainTypeEnum.STRING_MESSAGE.getType()) == 0) {
        messageBody = GlobalMessageGeneratedUtil.generatedWord(generate, param.getIsAverage().equals(1),
            param.getIsRandom().equals(1));
      } else {
        messageBody = GlobalMessageGeneratedUtil.generatedMingle(generate, param.getIsAverage().equals(1),
            param.getIsRandom().equals(1));
      }
      int floorNumber = 0;
      for (int i = 0; i < messageBody.size(); i++) {
        if (i % 100 == 0) {
          floorNumber++;
        }
        String group = messageBody.get(i);
        List<String> keys = new ArrayList<>();
        for (int j = 0; j < group.length(); j++) {
          keys.add(String.valueOf(group.charAt(j)));
        }
        GeneralKeyPatPageEntity pageEntity = new GeneralKeyPatPageEntity();
        pageEntity.setTrainId(save.getId());
        pageEntity.setTime("[]");
        pageEntity.setKey(JSONUtils.toJson(keys));
        pageEntity.setPageNumber(floorNumber);
        pageEntity.setSort(i % 100);
        pageEntity.setValue("[]");
        ret.add(pageEntity);
      }
      trainPageDao.save(ret);
    } else {
      List<List<List<String>>> cableFloor = cableFloorService.findCableFloor(param.getCableId(), null,
          param.getStartPage());
      int totalPage = param.getTotalNumber() / 100;
      if (totalPage <= 0) {
        throw new IllegalArgumentException("报文组数不足一页，无法建立房间");
      }
      if (totalPage > cableFloor.size()) {
        throw new IllegalArgumentException("所选电缆可用楼层不足");
      }
      cableFloor = cableFloor.subList(0, totalPage);
      // 使用批量保存替代循环逐条保存，提升性能
      List<GeneralKeyPatPageEntity> pageEntities = new ArrayList<>();
      for (int i = 0; i < cableFloor.size(); i++) {
        for (int j = 0; j < cableFloor.get(i).size(); j++) {
          GeneralKeyPatPageEntity pageEntity = new GeneralKeyPatPageEntity();
          pageEntity.setTrainId(save.getId());
          pageEntity.setTime("[]");
          pageEntity.setKey(JSONUtils.toJson(cableFloor.get(i).get(j)));
          pageEntity.setPageNumber(i + 1);
          pageEntity.setSort(j);
          pageEntity.setValue("[]");
          pageEntities.add(pageEntity);
        }
      }
      trainPageDao.save(pageEntities);
    }

    // 生成报文end

    return PojoUtils.convertOne(save, GeneralKeyPatTrainVO.class);
  }

  /**
   * 解散电子键组训。口径 = 创建者 ∪ 该训练内 {@code role=1} 组训人 ∪ 管理员，见 {@link TrainWriteAccess}。
   * 属主字段是 {@code createUser}（各域字段名不同，这里显式传入）。
   */
  public boolean delete(Integer trainId, String token) {
    String actorId = userService.getUserByToken(token).getId();
    GeneralKeyPatEntity train = Optional.ofNullable(trainDao.findById(trainId))
        .orElseThrow(() -> new IllegalArgumentException("未查询到训练"));
    trainWriteAccess.requireWritableTrain(actorId, train.getCreateUser(), () -> organizer(trainId, actorId),
        "电子键组训 " + trainId);
    Lock lock = RoomLifecycleLocks.generalKeyRoom(trainId);
    GeneralPatTrainRoomUserDto removed;
    boolean deleted;
    lock.lock();
    try {
      deleted = roomDeletionTransaction.run(() -> {
        userValueDao.delete("trainId=?1", trainId);
        resolverDao.delete("trainId=?1", trainId);
        moreEntityDao.delete("trainId=?1", trainId);
        trainPageDao.delete("trainId=?1", trainId);
        trainUserDao.delete("trainId=?1", trainId);
        return trainDao.deleteById(trainId);
      });
      removed = WebSocketGeneralKeyPatService.ROOM.remove(trainId);
    } finally {
      lock.unlock();
    }
    WebSocketGeneralKeyPatService.closeRoomSessions(removed);
    return deleted;
  }

  private List<GeneralKeyPatPageEntity> generateAndSavePatKey(Integer generateNumber, Integer pageNumber, int trainId,
      Integer type, Integer isAvg, Integer isRandom) {
    // 生成报文begin
    List<GeneralKeyPatPageEntity> ret = new ArrayList<>();
    List<String> messageBody;
    // 生成对应的报文
    if (type.compareTo(PostTelegramTrainTypeEnum.NUMBER_MESSAGE.getType()) == 0) {
      messageBody = GlobalMessageGeneratedUtil.generatedNumber(generateNumber, isAvg.equals(1), isRandom.equals(1));
    } else if (type.compareTo(PostTelegramTrainTypeEnum.STRING_MESSAGE.getType()) == 0) {
      messageBody = GlobalMessageGeneratedUtil.generatedWord(generateNumber, isAvg.equals(1), isRandom.equals(1));
    } else {
      messageBody = GlobalMessageGeneratedUtil.generatedMingle(generateNumber, isAvg.equals(1), isRandom.equals(1));
    }

    for (int i = 0; i < messageBody.size(); i++) {
      String group = messageBody.get(i);
      List<String> keys = new ArrayList<>();
      for (int j = 0; j < group.length(); j++) {
        keys.add(String.valueOf(group.charAt(j)));
      }
      GeneralKeyPatPageEntity pageEntity = new GeneralKeyPatPageEntity();
      pageEntity.setTrainId(trainId);
      pageEntity.setTime("[]");
      pageEntity.setKey(JSONUtils.toJson(keys));
      pageEntity.setValue("[]");
      pageEntity.setPageNumber(pageNumber);
      pageEntity.setSort(i % 100);
      ret.add(pageEntity);
    }
    // 生成报文end
    trainPageDao.save(ret);
    return ret;
  }

  /**
   * 查询指定tarinId的报底
   */
  public GeneralKeyPatPageDto findMessageBody(GeneralKeyPatPageParamDto param) {
    // 查询出该训练对应页码的报底
    final List<GeneralKeyPatPageEntity> trainPageList = trainPageDao
        .findByPageNumberAndTrainIdOrderBySort(param.getPageNumber(), param.getTrainId());
    GeneralKeyPatPageDto dto = new GeneralKeyPatPageDto();
    dto.setMessageContent(PojoUtils.convert(trainPageList, GeneralKeyPatPageDetailDto.class));
    return dto;
  }

  /**
   * 查询该用户的所有训练
   */
  public PageInfo<GeneralKeyPatTrainVO> findAll(Page page, String token) {
    UserEntity currentUser = userService.getUserByToken(token);
    List<GeneralKeyPatUserEntity> userEntityList = trainUserDao.findByUserId(currentUser.getId());
    PageInfo<GeneralKeyPatEntity> all = trainDao.findPage((root, criteriaQuery, criteriaBuilder) -> {
      CriteriaBuilder.In<Integer> id = criteriaBuilder.in(root.get("id").as(Integer.class));
      userEntityList.stream().map(GeneralKeyPatUserEntity::getTrainId)
          .forEach(id::value);
      criteriaQuery.where(id);
      criteriaQuery.orderBy(criteriaBuilder.desc(root.get("createTime").as(LocalDateTime.class)));
      return criteriaQuery;
    }, page.getPage() - 1, page.getRows());
    List<GeneralKeyPatTrainVO> convert = PojoUtils.convert(
        all.getData(),
        GeneralKeyPatTrainVO.class,
        (e, v) -> {
          v.setUserInfoList(
              PojoUtils.convert(trainUserDao.findByTrainIdToMap(e.getId()), GeneralKeyPatUserInfoVO.class));
          v.setRuleContent(null);
        });
    PageInfo<GeneralKeyPatTrainVO> pageInfo = new PageInfo<>();
    pageInfo.setCurrentPage(all.getCurrentPage());
    pageInfo.setPageSize(all.getPageSize());
    pageInfo.setTotalPage(all.getTotalPage());
    pageInfo.setTotalNumber(all.getTotalNumber());
    pageInfo.setData(convert);
    return pageInfo;
  }

  /**
   * 查询训练详情
   * param trainId
   */
  public GeneralKeyPatTrainVO detail(GeneralKeyPatPageParamDto param) {
    try {
      // 查询该训练信息
      GeneralKeyPatEntity keyPatEntity = Optional.ofNullable(trainDao.findById(param.getTrainId()))
          .orElseThrow(() -> new IllegalArgumentException("未查询到训练"));
      GeneralKeyPatTrainVO patTrainVO = PojoUtils.convertOne(keyPatEntity, GeneralKeyPatTrainVO.class);
      if (Objects.equals(keyPatEntity.getIsCable(), 1)) {
        patTrainVO.setTotalNumber((int) trainPageDao.count("trainId", param.getTrainId()));
        patTrainVO.setPageCount(trainPageDao.findMaxPageNumber(param.getTrainId()));
      }
      // 查询该训练的所有参与用户信息
      List<GeneralKeyPatUserInfoVO> userInfoList = PojoUtils.convert(
          trainUserDao.findByTrainIdToMap(param.getTrainId()), GeneralKeyPatUserInfoVO.class);
      patTrainVO.setUserInfoList(userInfoList);

      // 查询每个用户在线状态
      List<GeneralPatTrainUserDto> userDto = new ArrayList<>(findUserInfo(param.getTrainId()));
      Map<String, List<GeneralPatTrainUserDto>> collect = userDto.stream()
          .collect(Collectors.groupingBy(GeneralPatTrainUserDto::getId));
      for (GeneralKeyPatUserInfoVO item : userInfoList) {
        List<GeneralPatTrainUserDto> keyPatTrainUserDto = collect.get(item.getUserId());
        if (keyPatTrainUserDto != null && !keyPatTrainUserDto.isEmpty()) {
          Integer status = keyPatTrainUserDto.stream().findFirst().map(GeneralPatTrainUserDto::getStatus).orElse(0);
          item.setUserStatus(status);
        } else {
          // 没有默认为0（离线状态）
          item.setUserStatus(0);
        }
        // 统计信息
        GeneralKeyPatUserEntity member = trainUserDao.findByUserIdAndTrainId(item.getUserId(), param.getTrainId());
        if (Objects.equals(keyPatEntity.getProtocolVersion(), 1)) {
          List<Integer> pages = capturedPages(member).keySet().stream().sorted().toList();
          item.setExistNumber(pages);
          item.setExistPageNumber(pages.size());
          item.setActiveMillis(member.getActiveMillis());
        }
        item.setPageAnalyzeVOS(generatePageAnalyze(param.getTrainId(), item.getUserId()));
      }
      return patTrainVO;
    } catch (IllegalArgumentException | IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      log.error("查询训练详情失败，训练ID: {}", param.getTrainId(), e);
      throw new RuntimeException(e);
    }
  }

  /**
   * 生成统计信息
   */
  private List<PostTelegraphKeyPatTrainPageAnalyzeVO> generatePageAnalyze(Integer trainId, String userId) {
    // 统计每页拍发时长和个数
    List<GeneralKeyPatUserValueEntity> pageValueEntities = userValueDao
        .findByTrainIdAndUserIdOrderByPageNumberAscSortAsc(trainId, userId);
    Map<Integer, List<GeneralKeyPatUserValueEntity>> collect = pageValueEntities.stream()
        .collect(Collectors.groupingBy(GeneralKeyPatUserValueEntity::getPageNumber));
    List<PostTelegraphKeyPatTrainPageAnalyzeVO> analyzeVOS = new ArrayList<>();
    GeneralKeyPatEntity train = trainDao.findById(trainId);
    if (Objects.equals(train.getProtocolVersion(), 1)) {
      GeneralKeyPatUserEntity member = trainUserDao.findByUserIdAndTrainId(userId, trainId);
      Map<Integer, CapturedPage> pages = capturedPages(member);
      for (Integer pageNumber : pages.keySet().stream().sorted().toList()) {
        PostTelegraphKeyPatTrainPageAnalyzeVO analysis = new PostTelegraphKeyPatTrainPageAnalyzeVO();
        analysis.setPageNumber(pageNumber);
        analysis.setPatNumber(Math.toIntExact(collect.getOrDefault(pageNumber, List.of()).stream()
            .mapToLong(value -> countCharacters(value.getValue())).sum()));
        analysis.setTotalTime(CaptureTimeline.durationMillis(pages.get(pageNumber).intervals(), Long.MAX_VALUE));
        analyzeVOS.add(analysis);
      }
      return analyzeVOS;
    }
    collect.forEach((key, value) -> {
      PostTelegraphKeyPatTrainPageAnalyzeVO analyzeVO = new PostTelegraphKeyPatTrainPageAnalyzeVO();
      analyzeVO.setPageNumber(key);
      int totalTime = 0;
      int patNumber = 0;
      for (GeneralKeyPatUserValueEntity valueEntity : value) {
        String time = valueEntity.getTime();
        String patValue = valueEntity.getValue() == null ? "[]" : valueEntity.getValue();
        List<String> timeArray = JSONUtils.fromJson(time, new TypeToken<>() {
        });
        if (timeArray != null) {
          totalTime += timeArray.stream().map(Integer::valueOf).reduce(Integer::sum).orElse(0);
        }
        List<String> patValueArray = JSONUtils.fromJson(patValue, new TypeToken<>() {
        });
        patNumber += patValueArray.size();
      }
      analyzeVO.setPatNumber(patNumber);
      analyzeVO.setTotalTime(totalTime);
      analyzeVOS.add(analyzeVO);
    });
    return analyzeVOS;
  }

  @Transactional
  public void saveContentValue(GeneralKeyPatPageSubmitDto dto, String token) {
    LocalDateTime receivedAt = LocalDateTime.now();
    String userId = userService.getUserByToken(token).getId();
    GeneralKeyPatEntity train = lockedTrain(dto.getTrainId());
    GeneralKeyPatUserEntity member = student(train.getId(), userId);
    requireProtocol(train);
    requireAttempt(dto.getAttempt(), member);
    if (dto.getPageNumber() == null || dto.getPageNumber() < 1
        || dto.getPageNumber() > (train.getTotalNumber() - 1) / 100 + 1 || dto.getPageValue() == null) {
      throw new IllegalArgumentException("页码和拍发记录不合法");
    }
    Map<Integer, CapturedPage> pages = capturedPages(member);
    CapturedPage previous = pages.get(dto.getPageNumber());
    if (previous != null) {
      if (Objects.equals(previous.intervals(), dto.getCaptureIntervals())) {
        if (samePage(dto.getPageValue(), userValueDao.findByTrainIdAndPageNumberAndUserIdOrderBySort(
            train.getId(), dto.getPageNumber(), userId))) {
          return;
        }
        throw new IllegalStateException("该页采集区间已确认，但拍发内容不一致");
      }
      CaptureTimeline.requireExtension(previous.intervals(), dto.getCaptureIntervals());
    }
    if (Objects.equals(member.getIsFinish(), 1)) {
      throw new IllegalStateException("已结算的训练不能上传");
    }
    long activeMillis = CaptureTimeline.durationMillis(dto.getCaptureIntervals(), captureBound(train, member, receivedAt));
    long characters = 0;
    double rawMillis = 0;
    Integer previousSort = null;
    for (GeneralKeyPatPageDetailDto group : dto.getPageValue()) {
      if (group == null || group.getSort() == null || group.getSort() < 0
          || (previousSort != null && group.getSort() <= previousSort)) {
        throw new IllegalArgumentException("拍发组必须按页内位置有序且不能重复");
      }
      previousSort = group.getSort();
      characters += countCharacters(group.getValue());
      List<Double> times = JSONUtils.fromJson(group.getTime(), new TypeToken<>() {});
      if (times == null) throw new IllegalArgumentException("缺少原始拍发时长");
      for (Double milliseconds : times) {
        if (milliseconds == null || !Double.isFinite(milliseconds) || milliseconds < 0) {
          throw new IllegalArgumentException("原始拍发时长必须为有限非负数");
        }
        rawMillis += milliseconds;
      }
    }
    if ((characters > 0 && activeMillis == 0) || rawMillis > activeMillis + dto.getCaptureIntervals().size()) {
      throw new IllegalArgumentException("原始拍发时长与采集区间不一致");
    }
    pages.put(dto.getPageNumber(), new CapturedPage(dto.getAttempt(), dto.getCaptureIntervals(),
        receivedAt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()));
    CaptureTimeline.requireNoOverlap(pages.values().stream().map(CapturedPage::intervals).toList());
    List<GeneralKeyPatUserValueEntity> values = PojoUtils.convert(dto.getPageValue(), GeneralKeyPatUserValueEntity.class,
        (source, target) -> {
          target.setId(null);
          target.setUserId(userId);
          target.setTrainId(train.getId());
          target.setPageNumber(dto.getPageNumber());
        });
    userValueDao.deleteByTrainIdAndPageNumberAndUserId(train.getId(), dto.getPageNumber(), userId);
    userValueDao.saveAndFlush(values);
    member.setCapturePages(JSONUtils.toJson(pages));
  }

  @Transactional
  public List<GeneralKeyPatUserInfoVO> finish(GeneralKeyPatFinishDto dto, String token) {
    String userId = userService.getUserByToken(token).getId();
    GeneralKeyPatEntity entity = lockedTrain(dto.getTrainId());
    GeneralKeyPatUserEntity user = student(entity.getId(), userId);
    requireAttempt(dto.getAttempt(), user);
    if (!Objects.equals(user.getIsFinish(), 1)) {
      requireProtocol(entity);
      if (Objects.equals(entity.getStatus(), 3)) {
        if (LocalDateTime.now().isBefore(entity.getEndTime().plusSeconds(60))) {
          captureBound(entity, user, LocalDateTime.now());
        } else {
          settleClosing(entity);
        }
      } else {
        requireUnderway(entity);
      }
    }
    GeneralKeyPatUserInfoVO result = finishUser(entity, user);
    if (Objects.equals(entity.getStatus(), 3)
        && trainUserDao.count("trainId = ?1 and role = 0 and (isFinish is null or isFinish <> 1)", entity.getId()) == 0) {
      entity.setStatus(2);
    }
    return List.of(result);
  }

  private GeneralKeyPatUserInfoVO finishUser(GeneralKeyPatEntity entity, GeneralKeyPatUserEntity user) {
    if (Objects.equals(user.getIsFinish(), 1)) {
      return PojoUtils.convertOne(user, GeneralKeyPatUserInfoVO.class);
    }
    GeneralKeyPatUserEntity result = countScore(entity, user.getUserId());
    result.setIsFinish(1).setFinishTime(LocalDateTime.now());
    trainUserDao.flush();
    resultNotifier.publish("key", entity.getId(), user.getUserId(),
        trainUserDao.findRoleAdminByUserId(entity.getId()).stream()
            .map(GeneralKeyPatUserEntity::getUserId).toList());
    return PojoUtils.convertOne(result, GeneralKeyPatUserInfoVO.class);
  }

  /**
   * 开放给socket的接口。
   *
   * <p>写口径统一为「创建者 ∪ 该训练内 {@code role=1} 组训人 ∪ 管理员」（{@link TrainWriteAccess}）：
   * 组训人常常不是建训人，收窄到「仅创建者」会让他开不了自己带的训练；拒绝码由旧的 202 改为 207。
   */
  @Transactional
  public void updateStatus(Integer trainId, Integer status, String token) {
    String userId = userService.getUserByToken(token).getId();
    GeneralKeyPatEntity keyPatTrain = lockedTrain(trainId);
    trainWriteAccess.requireWritableTrain(userId, keyPatTrain.getCreateUser(), () -> organizer(trainId, userId),
        "电子键组训 " + trainId);
    requireProtocol(keyPatTrain);
    if (!Objects.equals(status, 1) && !Objects.equals(status, 2)) {
      throw new IllegalArgumentException("训练状态不合法");
    }
    if (Objects.equals(keyPatTrain.getStatus(), status)
        || (Objects.equals(keyPatTrain.getStatus(), 3) && Objects.equals(status, 2))) {
      return;
    }
    if (Objects.equals(status, 1)) {
      if (!Objects.equals(keyPatTrain.getStatus(), 0)) {
        throw new IllegalStateException("训练不能重新开始");
      }
      GradingRuleEntity snapshot = JSONUtils.fromJson(keyPatTrain.getRuleContent(), GradingRuleEntity.class);
      if (snapshot == null || snapshot.getScore() == null || snapshot.getScore() <= 0) {
        throw new IllegalStateException("训练缺少有效的冻结规则满分");
      }
      ScoringRuleValidation.electronic(snapshot.getContent());
      LocalDateTime now = LocalDateTime.now();
      keyPatTrain.setStatus(1).setStartTime(now);
      for (GeneralKeyPatUserEntity participant : trainUserDao.findByTrainIdAndRole(trainId, 0)) {
        participant.setCaptureStartedAt(now);
      }
    } else {
      requireUnderway(keyPatTrain);
      keyPatTrain.setStatus(3).setEndTime(LocalDateTime.now());
      keyPatTrain.setValidTime(Duration.between(keyPatTrain.getStartTime(), keyPatTrain.getEndTime()).toSeconds());
      if (trainUserDao.count("trainId = ?1 and role = 0 and (isFinish is null or isFinish <> 1)", trainId) == 0) {
        keyPatTrain.setStatus(2);
      }
    }
  }

  @Transactional
  public List<Integer> closingTrainIds() {
    // 只投影主键：该查询每 5 秒由收尾定时器执行一次，取整行会把 ruleContent 等 longtext 一并载入持久化上下文后立刻丢弃。
    return trainDao.getEntityManager().createQuery(
            "select id from general_key_pat where protocolVersion = 1 and status = 3 and endTime <= :deadline",
            Integer.class)
        .setParameter("deadline", LocalDateTime.now().minusSeconds(60))
        .getResultList();
  }

  @Transactional
  public void settleExpired(Integer trainId) {
    GeneralKeyPatEntity train = lockedTrain(trainId);
    if (Objects.equals(train.getProtocolVersion(), 1) && Objects.equals(train.getStatus(), 3)
        && !LocalDateTime.now().isBefore(train.getEndTime().plusSeconds(60))) {
      settleClosing(train);
    }
  }

  private void settleClosing(GeneralKeyPatEntity train) {
    for (GeneralKeyPatUserEntity participant : trainUserDao.findByTrainIdAndRole(train.getId(), 0)) {
      finishUser(train, participant);
    }
    train.setStatus(2);
    trainDao.flush();
  }

  public GeneralPatTrainUserDto getTrainUserInfo(String uid, Integer trainId) {
    GeneralKeyPatUserEntity userTrainEntity = trainUserDao.findByUserIdAndTrainId(uid, trainId);
    UserEntity userEntity = userService.getUserByIdNew(uid);
    if (userEntity == null || userTrainEntity == null) {
      throw new IllegalArgumentException("训练数据异常");
    }
    GeneralPatTrainUserDto dto = new GeneralPatTrainUserDto();
    dto.setId(userEntity.getId());
    dto.setUserName(userEntity.getUserName());
    dto.setUserImg(userEntity.getUserImg());
    dto.setRole(userTrainEntity.getRole());
    return dto;
  }

  @Transactional
  public PostTelegraphKeyPatTrainPageVO getPage(Integer trainId, Integer pageNumber, String userId, String token) {
    try {
      PostTelegraphKeyPatTrainPageVO ret = new PostTelegraphKeyPatTrainPageVO();
      GeneralKeyPatEntity entity = lockedTrain(trainId);
      GeneralKeyPatUserEntity member = readableMember(entity, userId, token);
      userId = member.getUserId();
      Map<Integer, CapturedPage> captured = capturedPages(member);
      CapturedPage savedCapture = captured.get(pageNumber);
      ret.setProtocolVersion(entity.getProtocolVersion());
      ret.setAttempt(member.getAttempt());
      ret.setServerElapsedMs(elapsedMillis(member.getCaptureStartedAt()));
      ret.setSubmitted(savedCapture != null || (!Objects.equals(entity.getProtocolVersion(), 1)
          && userValueDao.count("trainId = ?1 and userId = ?2 and pageNumber = ?3", trainId, userId, pageNumber) > 0));
      ret.setSavedCaptureIntervals(savedCapture == null ? List.of() : savedCapture.intervals());
      List<GeneralKeyPatPageEntity> messageVO = null;
      int generateNumber = 100;
      // 页码是否正确
      // Phase 7.4：pageNumber/totalNumber 均为可空 Integer，裸拆箱会 NPE
      if (pageNumber == null) {
        throw new IllegalArgumentException("页码不能为空");
      }
      if (Objects.equals(entity.getIsCable(), 0)) {
        if (entity.getTotalNumber() == null) {
          throw new IllegalArgumentException("训练总组数缺失，无法取页");
        }
        int totalNumber = entity.getTotalNumber();
        int totalPage = totalNumber / 100;
        if (totalNumber % 100 > 0) {
          totalPage += 1;
        }
        if (pageNumber.compareTo(totalPage) > 0 || pageNumber < 1) {
          throw new IllegalArgumentException("页码不正确，页码需大于0小于" + totalNumber);
        }
        if (pageNumber == totalPage) {
          generateNumber = totalNumber - ((pageNumber - 1) * 100);

        }
      }

      // 用户拍发内容
      List<GeneralKeyPatUserValueEntity> userPage = userValueDao.findByTrainIdAndPageNumberAndUserIdOrderBySort(trainId,
          pageNumber, userId);
      // 生成的内容
      List<GeneralKeyPatPageEntity> pageDaoAll = trainPageDao.findByTrainIdAndPageNumberOrderBySort(trainId,
          pageNumber);
      if (!pageDaoAll.isEmpty()) {
        messageVO = pageDaoAll;
      } else {
        if (Objects.equals(entity.getIsCable(), 1)) {
          throw new IllegalStateException("固定报底页不存在");
        }
        messageVO = generateAndSavePatKey(generateNumber, pageNumber, entity.getId(), entity.getMessageType(),
            entity.getIsAverage(), entity.getIsRandom());
      }
      // 用户未拍发本页内容，则获取生成的内容
      if (!ret.isSubmitted()) {
        ret.setMessageVO(PojoUtils.convert(messageVO, PostTelegraphKeyPatTrainPageMessageVO.class));
      } else {
        ret.setMessageVO(PojoUtils.convert(userPage, PostTelegraphKeyPatTrainPageMessageVO.class));
      }

      // 获取到本页的多组多行信息
      GeneralKeyPatTrainMoreEntity trainMoreEntity = moreEntityDao.findByTrainIdAndPageNumberAndUserId(trainId,
          pageNumber, userId);
      if (!Objects.isNull(trainMoreEntity)) {
        String moreLine = trainMoreEntity.getMoreLine();
        String moreGroup = trainMoreEntity.getMoreGroup();

        List<PostTelegraphKeyPatResolverDetailVO> moreGroupDetail = JSONUtils.fromJson(moreGroup, new TypeToken<>() {
        });
        List<PostTelegraphKeyPatResolverDetailVO> moreLineDetail = JSONUtils.fromJson(moreLine, new TypeToken<>() {
        });
        ret.setMoreGroup(moreGroupDetail);
        ret.setMoreLine(moreLineDetail);
      } else {
        ret.setMoreLine(new ArrayList<>());
        ret.setMoreGroup(new ArrayList<>());
      }
      return ret;
    } catch (IllegalArgumentException | IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      log.error("获取训练页面失败，训练ID: {}, 页码: {}", trainId, pageNumber, e);
      throw new RuntimeException(e);
    }
  }

  /**
   * 查询指定用户拍的内容
   *
   * @param param
   * @return
   */
  public List<GeneralKeyPatTrainUserValueVO> getPatValue(GeneralKeyPatPageParamDto param) {
    List<GeneralKeyPatUserValueEntity> patUserValueEntities = userValueDao
        .findByPageNumberAndTrainIdAndUserId(param.getPageNumber(), param.getTrainId(), param.getUserId());
    return PojoUtils.convert(patUserValueEntities, GeneralKeyPatTrainUserValueVO.class);
  }

  /**
   * 获取统计信息
   *
   * @param trainId
   * @return
   */
  public GeneralKeyPatTrainStatisticVO statistic(Integer trainId) {
    // role-0,学员
    List<GeneralKeyPatUserEntity> trainUserEntities = trainUserDao.findByTrainIdAndRole(trainId, 0);
    return statisticsScoreAndDotLineGapRate(trainUserEntities);
  }

  private GeneralKeyPatTrainStatisticVO statisticsScoreAndDotLineGapRate(
      List<GeneralKeyPatUserEntity> trainUserEntities) {
    // 结果集
    GeneralKeyPatTrainStatisticVO ret = new GeneralKeyPatTrainStatisticVO();
    // 错情统计
    GeneralKeyPatTrainErrorCollect errorCollect = new GeneralKeyPatTrainErrorCollect();

    // 使用构建器模式重构统计逻辑
    PatTrainStatisticsBuilder<GeneralKeyPatUserEntity, GeneralKeyPatTrainErrorCollect> builder =
        PatTrainStatisticsBuilder.<GeneralKeyPatUserEntity, GeneralKeyPatTrainErrorCollect>create(trainUserEntities)
            .withScoreExtractor(GeneralKeyPatUserEntity::getScore)
            .withUserIdExtractor(GeneralKeyPatUserEntity::getUserId)
            .withDeductInfoExtractor(GeneralKeyPatUserEntity::getDeductInfo)
            .withCreateTimeExtractor(GeneralKeyPatUserEntity::getCreateTime)
            .calculateScoreDistribution()
            .buildUserTendencies(
                userId -> {
                  UserEntity userEntity = userService.getUserByIdNew(userId);
                  return userEntity != null
                      ? new PatTrainStatisticsBuilder.UserInfo(userEntity.getId(), userEntity.getUserName(), userEntity.getUserImg())
                      : null;
                },
                (userId, createTime) -> trainUserDao.findByFistTwoScore(userId, createTime)
            );

    // 合并错误统计
    for (GeneralKeyPatUserEntity trainUser : trainUserEntities) {
      if (CharSequenceUtil.isNotBlank(trainUser.getDeductInfo())) {
        GeneralKeyPatTrainErrorCollect userError = JSONUtils.fromJson(trainUser.getDeductInfo(),
            GeneralKeyPatTrainErrorCollect.class);
        if (userError != null) {
          errorCollect.setErrorNumber(errorCollect.getErrorNumber() + userError.getErrorNumber());
          errorCollect.setLackNumber(errorCollect.getLackNumber() + userError.getLackNumber());
          errorCollect.setMoreNumber(errorCollect.getMoreNumber() + userError.getMoreNumber());
          errorCollect.setLackLineNumber(errorCollect.getLackLineNumber() + userError.getLackLineNumber());
          errorCollect.setMoreLineNumber(errorCollect.getMoreLineNumber() + userError.getMoreLineNumber());
          errorCollect.setLackGroupNumber(errorCollect.getLackGroupNumber() + userError.getLackGroupNumber());
          errorCollect.setMoreGroupNumber(errorCollect.getMoreGroupNumber() + userError.getMoreGroupNumber());
        }
      }
    }

    // 封装结果
    ret.setSchoolReport(builder.getSchoolReport());
    ret.setUserTendencyVO(builder.getUserTendencies());
    ret.setErrorCollect(errorCollect);
    return ret;
  }

  /**
   * 计算分数
   *
   * @param entity
   * @param
   */
  private GeneralKeyPatUserEntity countScore(GeneralKeyPatEntity entity, String userId) {
    GeneralKeyPatUserEntity kehPatUserEntity = trainUserDao.findByUserIdAndTrainId(userId, entity.getId());
    // 存放扣分规则 key扣分名称，value扣分值
    Map<String, Object> deductInfo = new HashMap<>();
    requireProtocol(entity);
    GradingRuleEntity ruleEntity = Optional.ofNullable(JSONUtils.fromJson(entity.getRuleContent(), GradingRuleEntity.class))
        .orElseThrow(() -> new IllegalStateException("训练缺少冻结的评分规则"));
    PostKeyPatTrainRuleDto rule = ScoringRuleValidation.electronic(ruleEntity.getContent());
    // 积分规则
    KeyPatStatisticalDto keyPatStatistics = new KeyPatStatisticalDto();
    // 得到已存在的页
    Map<Integer, CapturedPage> capturePages = capturedPages(kehPatUserEntity);
    List<Integer> pageNumbers = capturePages.keySet().stream().sorted().toList();
    // 解析结果单独保存；原始拍发页保持不变，供复算与回读。
    List<KeyPatValueTransferDto> pageValueResult = new ArrayList<>();
    // P1-1：pageValueResult/keyPatStatistics 为共享可变状态，parallelStream 并发累加有竞态——改串行流
    pageNumbers.stream().forEach(pageNumber -> {
      List<KeyPatPageTransferDto> userPages = PojoUtils.convert(
          trainPageDao.findByTrainIdAndPageNumberOrderBySort(entity.getId(), pageNumber),
          KeyPatPageTransferDto.class);
      List<KeyPatValueTransferDto> userPageValues = PojoUtils.convert(
          userValueDao.findByTrainIdAndPageNumberAndUserIdOrderBySort(entity.getId(), pageNumber, userId),
          KeyPatValueTransferDto.class);
      List<KeyPatValueTransferDto> pageResult = new ArrayList<>();
      handle(userId, pageResult, userPages, userPageValues, keyPatStatistics);
      pageValueResult.addAll(pageResult);
    });
    List<GeneralKeyPatUserValueResolverEntity> resolverEntities = PojoUtils.convert(pageValueResult,
        GeneralKeyPatUserValueResolverEntity.class);
    resolverDao.deleteByTrainIdAndUserId(entity.getId(), userId);
    resolverDao.saveAndFlush(resolverEntities);

    // 计算少页
    Integer tp = entity.getTotalNumber();
    if (1 == entity.getIsCable()) {
      tp = trainPageDao.find("trainId", entity.getId()).list().size();
    }
    int totalPageNumber = tp / 100;
    int remaining = 0;
    if (tp % 100 > 0) {
      remaining = tp % 100;
      totalPageNumber++;
    }
    List<Integer> totalPageNumberList = new ArrayList<>();
    for (int i = 0; i < totalPageNumber; i++) {
      totalPageNumberList.add(i + 1);
    }
    if (pageNumbers.size() < totalPageNumber) {
      totalPageNumberList.removeAll(pageNumbers);
      for (int i = 0; i < totalPageNumberList.size(); i++) {
        if (i == totalPageNumberList.size() - 1) {
          if (tp % 100 == 0) {
            keyPatStatistics.setLackLine(keyPatStatistics.getLackLine() + 10);
          } else {
            // 余数
            int clout = tp % 100;
            keyPatStatistics.setLackLine(keyPatStatistics.getLackLine() + clout / 10);
            if (clout % 10 > 0) {
              keyPatStatistics.setMoreGroup(keyPatStatistics.getMoreGroup() + clout % 10);
            }
          }
        } else {
          keyPatStatistics.setLackLine(keyPatStatistics.getLackLine() + 10);
        }
      }
      if (remaining > 0) {
        keyPatStatistics.setLackGroup(keyPatStatistics.getLackGroup() + (totalPageNumber - 1 - pageNumbers.size()) * 100 + remaining);
        keyPatStatistics.setLackLine(keyPatStatistics.getLackLine() +
            (totalPageNumber - 1 - pageNumbers.size()) *
                10
            + (remaining / 10) - (keyPatStatistics.getPatGroup() / 10 + 1));
      } else {
        keyPatStatistics.setLackGroup(keyPatStatistics.getLackGroup() + (totalPageNumber - pageNumbers.size()) * 100);
        keyPatStatistics.setLackLine(keyPatStatistics.getLackLine() + (totalPageNumber - pageNumbers.size()) * 10 - (keyPatStatistics.getPatGroup() / 10 + 1));
      }
    }

    long activeMillis = capturePages.values().stream()
        .mapToLong(page -> CaptureTimeline.durationMillis(page.intervals(), Long.MAX_VALUE)).sum();
    long characters = userValueDao.find("trainId = ?1 and userId = ?2", entity.getId(), userId).list().stream()
        .mapToLong(value -> countCharacters(value.getValue())).sum();
    BigDecimal speed = TrainingRateUnit.FOUR_CHARACTER_GROUPS_PER_MINUTE.rate(characters, activeMillis);
    kehPatUserEntity.setActiveMillis(activeMillis);
    kehPatUserEntity.setDuration(Long.toString(activeMillis / 1000));

    kehPatUserEntity.setSpeed(String.valueOf(speed));

    // 错误个数
    kehPatUserEntity.setErrorNumber(keyPatStatistics.getError());

    int errorTotal = keyPatStatistics.getPatGroup() - keyPatStatistics.getError() - keyPatStatistics.getBunchGroup() - keyPatStatistics.getLack() - keyPatStatistics.getMore();
    BigDecimal accuracy = ScoreMath.accuracy(errorTotal, keyPatStatistics.getPatGroup());

    kehPatUserEntity.setAccuracy(accuracy.toString());

    // 得到要扣的分
    String minus = "-";
    BigDecimal score = new BigDecimal(ruleEntity.getScore());
    BigDecimal errorScore = rule.getOther().getErrorCode().multiply(new BigDecimal(keyPatStatistics.getError()));
    deductInfo.put("errorNumber", keyPatStatistics.getError());
    deductInfo.put("errorScore", minus + errorScore);

    BigDecimal lackScore = rule.getOther().getMuchLessCode().multiply(new BigDecimal(keyPatStatistics.getLack()));
    deductInfo.put("lackNumber", keyPatStatistics.getLack());
    deductInfo.put("lackScore", minus + lackScore);

    BigDecimal moreScore = rule.getOther().getMuchLessCode().multiply(new BigDecimal(keyPatStatistics.getMore()));
    deductInfo.put("moreNumber", keyPatStatistics.getMore());
    deductInfo.put("moreScore", minus + moreScore);

    BigDecimal lackLineScore = rule.getOther().getMuchLessLine().multiply(new BigDecimal(keyPatStatistics.getLackLine()));
    deductInfo.put("lackLineNumber", keyPatStatistics.getLackLine());
    deductInfo.put("lackLineScore", minus + lackLineScore);

    BigDecimal moreLineScore = rule.getOther().getMuchLessLine().multiply(new BigDecimal(keyPatStatistics.getMoreLine()));
    deductInfo.put("moreLineNumber", keyPatStatistics.getMoreLine());
    deductInfo.put("moreLineScore", minus + moreLineScore);

    BigDecimal lackGroupScore = rule.getOther().getMuchLessGroups().multiply(new BigDecimal(keyPatStatistics.getLackGroup()));
    deductInfo.put("lackGroupNumber", keyPatStatistics.getLackGroup());
    deductInfo.put("lackGroupScore", minus + lackGroupScore);

    BigDecimal moreGroupScore = rule.getOther().getMuchLessGroups().multiply(new BigDecimal(keyPatStatistics.getMoreGroup()));
    deductInfo.put("moreGroupNumber", keyPatStatistics.getMoreGroup());
    deductInfo.put("moreGroupScore", minus + moreGroupScore);

    // 改错
    BigDecimal alterScore = rule.getOther().getAlterError().multiply(new BigDecimal(keyPatStatistics.getAlterError()));
    deductInfo.put("alterErrorNumber", keyPatStatistics.getAlterError());
    deductInfo.put("alterErrorScore", minus + alterScore);

    // 串组
    BigDecimal bunchGroupScore = rule.getOther().getBunchGroup().multiply(new BigDecimal(keyPatStatistics.getBunchGroup()));
    deductInfo.put("bunchGroupNumber", keyPatStatistics.getBunchGroup());
    deductInfo.put("bunchGroupScore", minus + bunchGroupScore);
    // 少间隔
    BigDecimal lackGapScore = rule.getOther().getLessGap().multiply(new BigDecimal(keyPatStatistics.getLackGap()));
    deductInfo.put("lackGapNumber", keyPatStatistics.getLackGap());
    deductInfo.put("lackGapScore", minus + lackGapScore);

    score = score.subtract(errorScore)
        .subtract(lackScore)
        .subtract(moreScore)
        .subtract(lackLineScore)
        .subtract(moreLineScore)
        .subtract(lackGroupScore)
        .subtract(moreGroupScore)
        .subtract(alterScore)
        .subtract(bunchGroupScore)
        .subtract(lackGapScore);

    // 速率加减分：高于基准按 R 加分、低于基准按 L 扣分，走全仓唯一实现 ScoreMath.wpmScore
    deductInfo.put("speedNumber", speed.toString());
    int wpmBase = rule.getWpm().getBase();
    BigDecimal speedScore = ScoreMath.wpmScore(wpmBase, rule.getWpm().getR(),
        rule.getWpm().getL(), speed.intValue());
    score = score.add(speedScore);
    // 码率项与个人电子键同一口径：始终输出 speedScore，正值补 "+"，等于基准输出 "0"；
    // 缺 key 会让成绩页把码率扣分渲染成 NaN，违反“显示与扣分同源”。
    deductInfo.put("speedScore", speedScore.signum() > 0 ? "+" + speedScore : speedScore.toString());

    kehPatUserEntity.setScore(score);
    // 保存扣分详情
    kehPatUserEntity.setDeductInfo(JSONUtils.toJson(deductInfo));

    trainDao.saveAndFlush(entity);
    trainUserDao.saveAndFlush(kehPatUserEntity);
    return kehPatUserEntity;
  }

  /**
   * 查询在线学员信息,因为本项目socket与业务模块在一起，故无需跨项目调用
   *
   * @return
   */
  public List<GeneralPatTrainUserDto> findUserInfo(Integer trainId) {
    GeneralKeyPatTrainController gGeneralKeyPatTrainController = new GeneralKeyPatTrainController();
    Response<List<GeneralPatTrainUserDto>> oneLine = gGeneralKeyPatTrainController.getOneLine(trainId);
    return oneLine.getData();
  }

  public List<BigDecimal> score() {
    return trainUserDao.score();
  }

  public GeneralKeyPatUserInfoVO patDetail(GeneralKeyPatPageParamDto param) {
    // 查询该训练信息
    GeneralKeyPatEntity keyPatEntity = Optional.ofNullable(trainDao.findById(param.getTrainId()))
        .orElseThrow(() -> new IllegalArgumentException("未查询到训练"));
    GeneralKeyPatUserEntity patUserEntity = Optional.ofNullable(
            trainUserDao.findByUserIdAndTrainId(param.getUserId(), param.getTrainId()))
        .orElseThrow(() -> new IllegalArgumentException("未查询到该用户的参训记录"));

    List<Integer> pageNumber = trainPageDao.countPageNumber(param.getTrainId());
    // 查询前2页数据content
    List<GeneralKeyPatPageEntity> twoPage = trainPageDao.findTwoPage(param.getTrainId());
    List<GeneralKeyPatUserValueEntity> toPageValue = userValueDao.findTwoPage(param.getTrainId(), param.getUserId());
    List<GeneralKeyPatUserValueResolverEntity> resolverList = resolverDao.findTwoPage(param.getTrainId(),
        param.getUserId());
    List<PostTelegraphKeyPatTrainPageAnalyzeVO> analyzeVOS = generatePageAnalyze(param.getTrainId(), param.getUserId());

    return PojoUtils.convertOne(patUserEntity, GeneralKeyPatUserInfoVO.class, (t, v) -> {
      v.setProtocolVersion(keyPatEntity.getProtocolVersion());
      if (Objects.equals(keyPatEntity.getProtocolVersion(), 1)) {
        List<Integer> pages = capturedPages(patUserEntity).keySet().stream().sorted().toList();
        v.setExistNumber(pages);
        v.setExistPageNumber(pages.size());
      }
      v.setExistPage(pageNumber);
      if (Objects.equals(patUserEntity.getIsFinish(), 1)) {
        v.setContent(PojoUtils.convert(toPageValue, PostTelegraphKeyPatTrainPageMessageVO.class));
      } else {
        v.setContent(PojoUtils.convert(twoPage, PostTelegraphKeyPatTrainPageMessageVO.class));
      }
      v.setDuration(Objects.equals(keyPatEntity.getProtocolVersion(), 1)
          ? (patUserEntity.getActiveMillis() == null ? 0 : patUserEntity.getActiveMillis() / 1000)
          : keyPatEntity.getValidTime());
      v.setPageAnalyzeVOS(analyzeVOS);
      v.setRuleContent(keyPatEntity.getRuleContent());
      v.setTotalNumber(keyPatEntity.getTotalNumber());
      v.setIsCable(keyPatEntity.getIsCable());
      if (Objects.equals(keyPatEntity.getIsCable(), 1)) {
        v.setTotalNumber((int) trainPageDao.count("trainId", param.getTrainId()));
        v.setPageCount(trainPageDao.findMaxPageNumber(param.getTrainId()));
      }
    });
  }

  /**
   * 单点离线导出。训练不存在 / 登录失效 -> 202；身份成立但无导出权 -> 207，两者不折叠。
   */
  public GeneralKeyPatTrainDto getTrainInfo(Integer trainId, String token) {
    String actorId = userService.getUserByToken(token).getId();
    if (trainDao.findById(trainId) == null) {
      throw new IllegalArgumentException("未查询到训练");
    }
    if (!exportable(trainId, actorId)) {
      throw new ForbiddenException("非授权者导出电子键组训 " + trainId);
    }
    return trainInfo(trainId);
  }

  /**
   * 导出授权判定，与写口径同构：创建者 ∪ 该训练内 {@code role=1} 组训人 ∪ 管理员，统一在 {@link TrainWriteAccess}。
   *
   * <p>训练不存在返回 {@code false}：批量导出据此跳过孤儿参训行；单点导出的「不存在」由调用方先判为 202。
   */
  private boolean exportable(Integer trainId, String actorId) {
    GeneralKeyPatEntity train = trainDao.findById(trainId);
    return train != null
        && trainWriteAccess.manages(actorId, train.getCreateUser(), () -> organizer(trainId, actorId));
  }

  /** 「调用者是该训练内的 {@code role=1} 组训人」。 */
  private boolean organizer(Integer trainId, String actorId) {
    GeneralKeyPatUserEntity member = trainUserDao.findByUserIdAndTrainId(actorId, trainId);
    return member != null && Objects.equals(member.getRole(), 1);
  }

  private GeneralKeyPatTrainDto trainInfo(Integer trainId) {
    GeneralKeyPatTrainDto dto = new GeneralKeyPatTrainDto();
    GeneralKeyPatEntity keyPatEntity = Optional.ofNullable(trainDao.findById(trainId))
        .orElseThrow(() -> new IllegalArgumentException("未查询到训练"));
    List<GeneralKeyPatPageEntity> pageEntities = trainPageDao.findByTrainId(trainId);
    List<GeneralKeyPatUserEntity> patUserEntities = trainUserDao.findByTrainId(trainId);
    List<GeneralKeyPatUserValueEntity> userValueEntities = userValueDao.findByTrainId(trainId);
    List<GeneralKeyPatTrainMoreEntity> patTrainMoreEntities = moreEntityDao.findByTrainId(trainId);
    Set<String> userIds = new HashSet<>();
    userIds.add(keyPatEntity.getCreateUser());
    userIds.addAll(patUserEntities.stream().map(GeneralKeyPatUserEntity::getUserId).collect(Collectors.toSet()));
    userIds.addAll(userValueEntities.stream().map(GeneralKeyPatUserValueEntity::getUserId).collect(Collectors.toSet()));
    userIds
        .addAll(patTrainMoreEntities.stream().map(GeneralKeyPatTrainMoreEntity::getUserId).collect(Collectors.toSet()));
    List<UserEntity> userEntities = userDao.queryByIdIn(userIds);
    dto.setTrainDto(PojoUtils.convertOne(keyPatEntity, GeneralKeyPatSyncDto.class));
    dto.setPageDto(PojoUtils.convert(pageEntities, GeneralKeyPatPageSyncDto.class));
    dto.setUserDto(PojoUtils.convert(patUserEntities, GeneralKeyPatUserSyncDto.class));
    dto.setUserValueDto(PojoUtils.convert(userValueEntities, GeneralKeyPatUserValueSyncDto.class));
    dto.setMoreDto(PojoUtils.convert(patTrainMoreEntities, GeneralKeyPatTrainMoreSyncDto.class));
    dto.setUsers(PojoUtils.convert(userEntities, UserSyncDto.class));
    return dto;
  }

  /**
   * 离线导入。入口在 {@code /api/generalKeyPat/importTrainInfo}，已由 {@code @RequireAdmin} 限管理员（SEC-05）。
   *
   * <p>包内用户行一律经 {@link com.nip.service.UserService#replaceUserIdAndSaveIfNotExist(java.util.List)}
   * 的字段白名单建号：只落业务标识，{@code token}/{@code deviceId}/{@code password} 留 NULL、
   * {@code status} 服务端定 0 —— 导入不产生任何可直接使用的账号。
   */
  @Transactional
  public void importTrainInfo(GeneralKeyPatTrainDto dto) {
    GeneralKeyPatSyncDto trainDto = dto.getTrainDto();
    List<GeneralKeyPatPageSyncDto> pageDto = dto.getPageDto();
    List<GeneralKeyPatUserSyncDto> userDto = dto.getUserDto();
    List<GeneralKeyPatUserValueSyncDto> userValueDto = dto.getUserValueDto();
    List<GeneralKeyPatTrainMoreSyncDto> trainMoreDto = dto.getMoreDto();
    List<UserSyncDto> users = dto.getUsers();
    // 更新用户id 和训练id
    Map<String, String> userIdMap = userService.replaceUserIdAndSaveIfNotExist(users);
    trainDto.setCreateUser(userIdMap.get(trainDto.getCreateUser()));
    trainDto.setId(null);// 自增主键重新生成防重复
    GeneralKeyPatEntity save = PojoUtils.convertOne(trainDto, GeneralKeyPatEntity.class);
    trainDao.saveAndFlush(save);
    userDto.forEach(item -> {
      item.setUserId(userIdMap.get(item.getUserId()));
      item.setTrainId(save.getId());
      item.setId(null);
    });
    userValueDto.forEach(item -> {
      item.setUserId(userIdMap.get(item.getUserId()));
      item.setTrainId(save.getId());
      item.setId(null);
    });
    pageDto.forEach(item -> {
      item.setTrainId(save.getId());
      item.setId(null);
    });
    trainMoreDto.forEach(item -> {
      item.setUserId(userIdMap.get(item.getUserId()));
      item.setTrainId(save.getId());
      item.setId(null);
    });

    // 入库
    trainPageDao.save(PojoUtils.convert(pageDto, GeneralKeyPatPageEntity.class));
    trainUserDao.save(PojoUtils.convert(userDto, GeneralKeyPatUserEntity.class));
    userValueDao.save(PojoUtils.convert(userValueDto, GeneralKeyPatUserValueEntity.class));
    moreEntityDao.save(PojoUtils.convert(trainMoreDto, GeneralKeyPatTrainMoreEntity.class));
  }

  /**
   * 批量离线导出。{@code queryRelatedTrainId} 取的是该用户的**全部**参训行（含 role=0 学员行），
   * 因此这里必须 filter 而不是抛异常：学员只是无可导出训练（空列表），不是越权访问（207）。
   */
  public List<GeneralKeyPatTrainDto> getTrainInfoBatch(String token) {
    String actorId = userService.getUserByToken(token).getId();
    List<GeneralKeyPatTrainDto> ls = new ArrayList<>();
    for (Integer trainId : trainUserDao.queryRelatedTrainId(actorId)) {
      if (exportable(trainId, actorId)) {
        ls.add(trainInfo(trainId));
      }
    }
    return ls;
  }

  public void importTrainInfoBatch(List<GeneralKeyPatTrainDto> dto) {
    dto.forEach(this::importTrainInfo);
  }

  public AvgResult getClassAvgResult(List<String> userList) {
    BigDecimal totalThisResult = BigDecimal.ZERO;
    BigDecimal totalLastResult = BigDecimal.ZERO;
    for (String user : userList) {
      List<BigDecimal> lastTwoResult = trainUserDao.findLastTwoResult(user);
      totalThisResult = totalThisResult.add(ArraysSafeUtils.getElement(lastTwoResult, 0, BigDecimal.ZERO));
      totalLastResult = totalLastResult.add(ArraysSafeUtils.getElement(lastTwoResult, 1, BigDecimal.ZERO));
    }
    BigDecimal thisAvgResult = totalThisResult.divide(BigDecimal.valueOf(userList.size()), 0, RoundingMode.HALF_UP);
    BigDecimal lastAvgResult = totalLastResult.divide(BigDecimal.valueOf(userList.size()), 0, RoundingMode.HALF_UP);
    return AvgResult.builder()
        .thisAvgResult(thisAvgResult)
        .lastAvgResult(lastAvgResult)
        .build();

  }

  @Transactional
  public void startTrain(Integer trainId, Integer attempt, String token) {
    String userId = userService.getUserByToken(token).getId();
    GeneralKeyPatEntity train = lockedTrain(trainId);
    GeneralKeyPatUserEntity user = student(trainId, userId);
    captureBound(train, user, LocalDateTime.now());
    requireProtocol(train);
    requireAttempt(attempt, user);
    if (Objects.equals(user.getIsFinish(), 1)) {
      throw new IllegalStateException("已结算的训练需要先重置");
    }
    user.setIsFinish(2);
  }

  @Transactional
  public void reset(Integer trainId, Integer attempt, String token) {
    String userId = userService.getUserByToken(token).getId();
    GeneralKeyPatEntity train = lockedTrain(trainId);
    GeneralKeyPatUserEntity participant = student(trainId, userId);
    requireUnderway(train);
    requireProtocol(train);
    requireAttempt(attempt, participant);
    participant.setAttempt(Math.incrementExact(participant.getAttempt())).setCaptureStartedAt(LocalDateTime.now())
        .setActiveMillis(null).setCapturePages("{}");
    userValueDao.deleteByTrainIdAndUserId(trainId, userId);
    resolverDao.deleteByTrainIdAndUserId(trainId, userId);
    moreEntityDao.delete("trainId = ?1 and userId = ?2", trainId, userId);
    participant.setIsFinish(0);
    participant.setAccuracy("0.00");
    participant.setSpeed("0");
    participant.setErrorNumber(0);
    participant.setScore(BigDecimal.ZERO);
    participant.setDuration("0");
    participant.setFinishTime(null);
    participant.setContent(null);
    participant.setDeductInfo(null);
    participant.setStatisticInfo(null);
    trainUserDao.save(participant);
  }

  private GeneralKeyPatEntity lockedTrain(Integer trainId) {
    if (trainId == null) {
      throw new IllegalArgumentException("训练ID不能为空");
    }
    return Optional.ofNullable(trainDao.findById(trainId, LockModeType.PESSIMISTIC_WRITE))
        .orElseThrow(() -> new IllegalArgumentException("未查询到训练"));
  }

  private GeneralKeyPatUserEntity student(Integer trainId, String userId) {
    GeneralKeyPatUserEntity member = trainUserDao.findByUserIdAndTrainId(userId, trainId);
    if (member == null || !Objects.equals(member.getRole(), 0)) {
      throw new IllegalArgumentException("未查询到该用户的学员参训记录");
    }
    return member;
  }

  private void requireUnderway(GeneralKeyPatEntity entity) {
    if (!Objects.equals(entity.getStatus(), 1)) {
      throw new IllegalStateException("训练不在进行中");
    }
  }

  private void requireAttempt(Integer attempt, GeneralKeyPatUserEntity member) {
    if (attempt == null || !Objects.equals(attempt, member.getAttempt())) {
      throw new IllegalStateException("训练轮次已变化，请重新读取训练");
    }
  }

  private GeneralKeyPatUserEntity readableMember(GeneralKeyPatEntity train, String requestedUser, String token) {
    String actor = userService.getUserByToken(token).getId();
    String target = requestedUser == null ? actor : requestedUser;
    GeneralKeyPatUserEntity actorMember = trainUserDao.findByUserIdAndTrainId(actor, train.getId());
    if (!Objects.equals(actor, target) && !Objects.equals(train.getCreateUser(), actor)
        && (actorMember == null || !Objects.equals(actorMember.getRole(), 1))) {
      throw new IllegalArgumentException("无权读取该学员的拍发记录");
    }
    return Optional.ofNullable(trainUserDao.findByUserIdAndTrainId(target, train.getId()))
        .orElseThrow(() -> new IllegalArgumentException("未查询到参训记录"));
  }

  private long elapsedMillis(LocalDateTime startedAt) {
    return startedAt == null ? 0 : Math.max(0, Duration.between(startedAt, LocalDateTime.now()).toMillis());
  }

  private void requireProtocol(GeneralKeyPatEntity train) {
    if (!Objects.equals(train.getProtocolVersion(), 1)) {
      throw new IllegalStateException("旧训练缺少原始采集协议，请重新创建训练；历史成绩保持不变");
    }
  }

  private long captureBound(GeneralKeyPatEntity train, GeneralKeyPatUserEntity member, LocalDateTime receivedAt) {
    if (member.getCaptureStartedAt() == null) {
      throw new IllegalStateException("采集尚未开始");
    }
    long elapsed = Math.max(0, Duration.between(member.getCaptureStartedAt(), receivedAt).toMillis());
    if (Objects.equals(train.getStatus(), 1)) return elapsed;
    if (Objects.equals(train.getStatus(), 3) && train.getEndTime() != null
        && receivedAt.isBefore(train.getEndTime().plusSeconds(60))) {
      return elapsed;
    }
    throw new IllegalStateException("训练已停止接收拍发记录");
  }

  private Map<Integer, CapturedPage> capturedPages(GeneralKeyPatUserEntity member) {
    Map<Integer, CapturedPage> pages = JSONUtils.fromJson(member.getCapturePages(), new TypeToken<>() {});
    if (pages == null) throw new IllegalStateException("缺少原始采集页索引");
    return pages;
  }

  private long countCharacters(String rawValue) {
    List<String> characters = JSONUtils.fromJson(rawValue, new TypeToken<>() {});
    if (characters == null) throw new IllegalArgumentException("拍发字符列表不能为空");
    long count = 0;
    for (String character : characters) {
      if (character == null || character.codePointCount(0, character.length()) != 1) {
        throw new IllegalArgumentException("每个拍发事件必须是一个正文字符");
      }
      if (!character.isBlank() && !character.equals("?")) count++;
    }
    return count;
  }

  private boolean samePage(List<GeneralKeyPatPageDetailDto> requested, List<GeneralKeyPatUserValueEntity> saved) {
    if (requested.size() != saved.size()) return false;
    for (int index = 0; index < requested.size(); index++) {
      GeneralKeyPatPageDetailDto request = requested.get(index);
      GeneralKeyPatUserValueEntity value = saved.get(index);
      if (request == null || !Objects.equals(request.getSort(), value.getSort())
          || !Objects.equals(request.getKey(), value.getKey())
          || !Objects.equals(request.getValue(), value.getValue())
          || !Objects.equals(request.getTime(), value.getTime())) return false;
    }
    return true;
  }

}
