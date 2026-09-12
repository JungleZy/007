package com.nip.service.general;

import cn.hutool.core.date.LocalDateTimeUtil;
import com.google.gson.reflect.TypeToken;
import com.nip.common.PageInfo;
import com.nip.common.constants.CodeConstants;
import com.nip.common.constants.TrainConstants;
import com.nip.common.response.Response;
import com.nip.controller.general.GeneralTickerPatTrainController;
import com.nip.common.utils.ArraysSafeUtils;
import com.nip.common.utils.GlobalMessageGeneratedUtil;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.Page;
import com.nip.common.utils.PojoUtils;
import com.nip.common.utils.ScoreMath;
import com.nip.common.utils.CaptureTimeline;
import com.nip.common.utils.ScoringRuleValidation;
import com.nip.dto.CaptureInterval;
import com.nip.dto.score.TrainingRateUnit;
import com.nip.dao.GradingRuleDao;
import com.nip.dao.general.ticker.GeneralTickerPatTrainDao;
import com.nip.dao.general.ticker.GeneralTickerPatTrainPageDao;
import com.nip.dao.general.ticker.GeneralTickerPatTrainUserDao;
import com.nip.dao.general.ticker.GeneralTickerPatTrainUserValueDao;
import com.nip.dto.GeneralTickerPatTrainUserDto;
import com.nip.dto.PostTelegramTrainFinishInfoDto;
import com.nip.dto.general.AvgResult;
import com.nip.dto.general.GeneralPatTrainUserDto;
import com.nip.dto.general.GeneralTickerPatTrainUpdateDto;
import com.nip.dto.general.GeneralTickerPatTrainUserInfoVO;
import com.nip.dto.general.GeneralTickerPatTrainVO;
import com.nip.dto.score.PostTelegramTrainRule;
import com.nip.dto.score.SpeedDeduct;
import com.nip.dto.vo.PostTelegramTrainResolverVO;
import com.nip.dto.vo.PostTelegramTrainScoreVO;
import com.nip.dto.vo.PostTelegramTrainStatisticsVO;
import com.nip.dto.vo.param.PostTelegramTrainContentAddParam;
import com.nip.dto.vo.param.simulation.tickerPat.GeneralTickerPatTrainAddParam;
import com.nip.dto.vo.param.simulation.tickerPat.GeneralTickerPatTrainContentAddParam;
import com.nip.dto.vo.param.simulation.tickerPat.GeneralTickerPatTrainPageParam;
import com.nip.dto.vo.param.simulation.tickerPat.GeneralTickerPatTrainQueryParam;
import com.nip.dto.vo.param.simulation.tickerPat.GeneralTickerPatTrainResetParam;
import com.nip.dto.vo.simulation.tickerPat.GeneralTickerPatTrainContentVO;
import com.nip.dto.vo.simulation.tickerPat.GeneralTickerPatTrainContentValueVO;
import com.nip.dto.vo.simulation.tickerPat.GeneralTickerPatTrainErrorInfoVO;
import com.nip.dto.vo.simulation.tickerPat.GeneralTickerPatTrainFinishInfoVO;
import com.nip.dto.vo.simulation.tickerPat.GeneralTickerPatTrainFinishVO;
import com.nip.dto.vo.simulation.tickerPat.GeneralTickerPatTrainSchoolReportVO;
import com.nip.dto.vo.simulation.tickerPat.GeneralTickerPatTrainScoreInfoVO;
import com.nip.dto.vo.simulation.tickerPat.GeneralTickerPatTrainStatisticVO;
import com.nip.dto.vo.simulation.tickerPat.GeneralTickerPatTrainStatisticsVO;
import com.nip.dto.vo.simulation.tickerPat.GeneralTickerPatTrainUserTendencyVO;
import com.nip.entity.GradingRuleEntity;
import com.nip.entity.UserEntity;
import com.nip.entity.simulation.ticker.GeneralTickerPatTrainEntity;
import com.nip.entity.simulation.ticker.GeneralTickerPatTrainPageEntity;
import com.nip.entity.simulation.ticker.GeneralTickerPatTrainUserEntity;
import com.nip.entity.simulation.ticker.GeneralTickerPatTrainUserValueEntity;
import com.nip.service.CableFloorService;
import com.nip.service.MessageComparisonService;
import com.nip.service.UserService;
import com.nip.ws.WebSocketGeneralTickerPatService;
import com.nip.ws.WebSocketService;
import com.nip.ws.model.ResponseModel;
import com.nip.ws.service.RoomDeletionTransaction;
import com.nip.ws.service.RoomLifecycleLocks;
import com.nip.ws.model.GeneralTickerPatTrainRoomUserModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.concurrent.locks.Lock;

import static com.nip.common.constants.BaseConstants.TRAIN_ID;
import static com.nip.common.utils.PatTrainStatisticsUtil.calculateRate;
import static com.nip.common.utils.ToolUtil.*;

@Slf4j
@ApplicationScoped
public class GeneralTickerPatService {

  private final UserService userService;
  private final GeneralTickerPatTrainDao trainDao;
  private final GeneralTickerPatTrainUserDao trainUserDao;
  private final GeneralTickerPatTrainPageDao trainPageDao;
  private final GeneralTickerPatTrainUserValueDao userValueDao;
  private final GradingRuleDao gradingRuleDao;
  private final CableFloorService cableFloorService;
  private final MessageComparisonService messageComparisonService;
  @Inject
  RoomDeletionTransaction roomDeletionTransaction;
  @Inject
  GeneralPatResultNotifier resultNotifier;

  @Inject
  public GeneralTickerPatService(UserService userService, GeneralTickerPatTrainDao trainDao,
      GeneralTickerPatTrainUserDao trainUserDao,
      GeneralTickerPatTrainPageDao trainPageDao, GeneralTickerPatTrainUserValueDao userValueDao,
      GradingRuleDao gradingRuleDao,
      CableFloorService cableFloorService, MessageComparisonService messageComparisonService) {
    this.userService = userService;
    this.trainDao = trainDao;
    this.trainUserDao = trainUserDao;
    this.trainPageDao = trainPageDao;
    this.userValueDao = userValueDao;
    this.gradingRuleDao = gradingRuleDao;
    this.cableFloorService = cableFloorService;
    this.messageComparisonService = messageComparisonService;
  }

  @Transactional
  public GeneralTickerPatTrainVO add(GeneralTickerPatTrainAddParam param, String token) {
    UserEntity userEntity = userService.getUserByToken(token);
    // 报底数与报文类型是下面两个分支都要用的必填项：缺失时显式报错，不再裸拆箱成 NPE
    Integer messageNumber = param.getMessageNumber();
    if (messageNumber == null) {
      throw new IllegalArgumentException("报底数不能为空");
    }
    Integer patType = param.getType();
    if (patType == null) {
      throw new IllegalArgumentException("报文类型不能为空");
    }
    GeneralTickerPatTrainEntity trainEntity = PojoUtils.convertOne(param, GeneralTickerPatTrainEntity.class);
    trainEntity.setCreateUser(userEntity.getId());
    // 默认状态为未开始
    trainEntity.setStatus(0);
    // 有效时间设置为0
    trainEntity.setValidTime(0L);
    trainEntity.setCreateUser(userEntity.getId());
    // 设置长短码和是否随机
    trainEntity.setCodeSort(Boolean.TRUE.equals(param.getCodeSort()) ? 1 : 0);
    trainEntity.setIsRandom(Boolean.TRUE.equals(param.getIsRandom()) ? 1 : 0);
    trainEntity.setIsAverage(Boolean.TRUE.equals(param.getIsAverage()) ? 1 : 0);
    GradingRuleEntity frozenRule = gradingRuleDao.findByIdOptional(trainEntity.getRuleId())
        .orElseThrow(() -> new IllegalArgumentException("评分规则不存在"));
    ScoringRuleValidation.handkey(frozenRule.getContent());
    if (frozenRule.getScore() == null || frozenRule.getScore() <= 0) {
      throw new IllegalArgumentException("评分规则满分不合法");
    }
    trainEntity.setRuleContent(frozenRule.getContent()).setRuleScore(frozenRule.getScore()).setProtocolVersion(1);
    GeneralTickerPatTrainEntity save = trainDao.save(trainEntity);
    // 保存参训人员信息
    List<GeneralTickerPatTrainUserEntity> trainUserEntityList = new ArrayList<>();
    for (String id : param.getUserId()) {
      GeneralTickerPatTrainUserEntity trainUser = new GeneralTickerPatTrainUserEntity();
      trainUser.setAccuracy("0.00");
      trainUser.setTrainId(save.getId());
      trainUser.setUserId(id);
      trainUser.setErrorNumber(0);
      trainUser.setRole(0);
      trainUser.setScore(BigDecimal.ZERO);
      trainUserEntityList.add(trainUser);
      WebSocketService.sendInfo(id, new ResponseModel(CodeConstants.NOTIFICATION_NEW_TRAIN.getCode(),
          Map.of(
              "type", "ticker",
              "id", save.getId(),
              "title", save.getName())));
    }
    GeneralTickerPatTrainUserEntity groupUser = new GeneralTickerPatTrainUserEntity();
    groupUser.setTrainId(save.getId());
    groupUser.setUserId(userEntity.getId());
    groupUser.setRole(1);
    trainUserEntityList.add(groupUser);
    trainUserDao.save(trainUserEntityList);

    // 生成报文
    // 平均报/随机报是 Boolean 且无默认值，null 按「否」处理
    boolean average = Boolean.TRUE.equals(param.getIsAverage());
    boolean random = Boolean.TRUE.equals(param.getIsRandom());
    // isCable 声明默认值为 0（随机报）：显式传 null 时按默认值处理，其余取值分支走向不变
    int cable = Optional.ofNullable(param.getIsCable()).orElse(0);
    if (cable == 0) {
      int generate = 0;
      if (messageNumber > 200) {
        generate = 200;
      } else {
        generate = messageNumber;
      }
      int pageNumber = generate / 100;
      pageNumber += generate % 100 == 0 ? 0 : 1;
      for (int i = 0; i < pageNumber; i++) {
        int generateNum;
        if (i == pageNumber - 1) {
          generateNum = generate - i * 100;
        } else {
          generateNum = 100;
        }
        List<String> messages = new ArrayList<>();
        // 类型 0 数码报 1 字码报 2 混合报
        switch (patType) {
          case 0:
            messages.addAll(
                GlobalMessageGeneratedUtil.generatedNumber(generateNum, average, random));
            break;
          case 1:
            messages.addAll(
                GlobalMessageGeneratedUtil.generatedWord(generateNum, average, random));
            break;
          case 2:
            messages.addAll(
                GlobalMessageGeneratedUtil.generatedMingle(generateNum, average, random));
            break;
          default:
            throw new IllegalArgumentException("类型不匹配");
        }
        List<GeneralTickerPatTrainPageEntity> pageEntities = new ArrayList<>();
        for (int j = 0; j < messages.size(); j++) {
          String message = messages.get(j);
          List<String> group = new ArrayList<>();
          for (int z = 0; z < message.length(); z++) {
            group.add(String.valueOf(message.charAt(z)));
          }
          GeneralTickerPatTrainPageEntity pageEntity = new GeneralTickerPatTrainPageEntity();
          pageEntity.setTrainId(save.getId());
          pageEntity.setFloorNumber(i + 1);
          pageEntity.setSort(j);
          pageEntity.setMoresKey(JSONUtils.toJson(group));
          pageEntity.setMoresTime("[]");
          pageEntity.setMoresValue("[]");
          pageEntity.setPatKeys("[]");
          pageEntities.add(pageEntity);
        }
        trainPageDao.save(pageEntities);
      }
    } else {
      List<List<List<String>>> cableFloor = cableFloorService.findCableFloor(param.getCableId(), null,
          param.getStartPage());
      int totalPage = messageNumber / 100;
      if (totalPage <= 0) {
        throw new IllegalArgumentException("报文组数不足一页，无法建立房间");
      }
      if (totalPage > cableFloor.size()) {
        throw new IllegalArgumentException("所选电缆可用楼层不足");
      }
      cableFloor = cableFloor.subList(0, totalPage);
      // 使用批量保存替代循环逐条保存，提升性能
      List<GeneralTickerPatTrainPageEntity> pageEntities = new ArrayList<>();
      for (int i = 0; i < cableFloor.size(); i++) {
        for (int j = 0; j < cableFloor.get(i).size(); j++) {
          GeneralTickerPatTrainPageEntity pageEntity = new GeneralTickerPatTrainPageEntity();
          pageEntity.setTrainId(save.getId());
          pageEntity.setFloorNumber(i + 1);
          pageEntity.setSort(j);
          pageEntity.setMoresKey(JSONUtils.toJson(cableFloor.get(i).get(j)));
          pageEntity.setMoresTime("[]");
          pageEntity.setMoresValue("[]");
          pageEntity.setPatKeys("[]");
          pageEntities.add(pageEntity);
        }
      }
      trainPageDao.save(pageEntities);
    }

    return PojoUtils.convertOne(save, GeneralTickerPatTrainVO.class);
  }

  public boolean delete(Integer trainId) {
    Lock lock = RoomLifecycleLocks.generalTickerRoom(trainId);
    GeneralTickerPatTrainRoomUserModel removed;
    boolean deleted;
    lock.lock();
    try {
      deleted = roomDeletionTransaction.run(() -> {
        userValueDao.delete("trainId=?1", trainId);
        trainPageDao.delete("trainId=?1", trainId);
        trainUserDao.delete("trainId=?1", trainId);
        return trainDao.deleteById(trainId);
      });
      removed = WebSocketGeneralTickerPatService.PAT_ROOM.remove(trainId);
    } finally {
      lock.unlock();
    }
    WebSocketGeneralTickerPatService.closeRoomSessions(removed);
    return deleted;
  }

  @Transactional
  public GeneralTickerPatTrainContentVO findMessageBody(GeneralTickerPatTrainPageParam param, String token) {
    GeneralTickerPatTrainEntity entity = lockedTrain(param.getId());
    GeneralTickerPatTrainUserEntity member = readableMember(entity, param.getUserId(), token);
    if (param.getFloorNumber() == null || param.getFloorNumber() < 1
        || param.getFloorNumber() > (entity.getMessageNumber() + 99) / 100) {
      throw new IllegalArgumentException("页码不正确");
    }
    List<GeneralTickerPatTrainPageEntity> contentEntities;
    List<GeneralTickerPatTrainContentAddParam> addParams;
      contentEntities = trainPageDao.findByFloorNumberAndTrainIdOrderBySort(param.getFloorNumber(), param.getId());
      if (contentEntities.isEmpty()) {
        if (Objects.equals(entity.getIsCable(), 1)) {
          throw new IllegalStateException("固定报底页不存在");
        }
        Integer currentPage = param.getFloorNumber();
        Integer messageNumber = entity.getMessageNumber();
        int totalPage = messageNumber / 100;
        int generateNumber = 0;
        if (messageNumber % 100 > 0) {
          totalPage += 1;
        }
        if (totalPage > currentPage) {
          generateNumber = 100;
        } else if (totalPage == currentPage) {
          generateNumber = messageNumber - ((currentPage - 1) * 100);
        }
        // 查询出上一页最后的值
        /*
         * GeneralTickerPatTrainPageEntity floorContentEntity =
         * trainPageDao.findByTrainIdOrderByFloorNumberDescSortDesc(param.getId());
         * List<String> array = JSONUtils.parseArray(floorContentEntity.getMoresKey(),
         * String.class);
         * String s = array.get(3);
         */
        // contentEntities =
        // generateMessage(entity,generateNumber,s.charAt(0)+1,floorContentEntity.getFloorNumber(),param.getId());
        List<String> generatedMessage = new ArrayList<>();
        // 类型 0 数码报 1 字码报 2 混合报
        switch (entity.getType()) {
          case 0:
            generatedMessage.addAll(GlobalMessageGeneratedUtil.generatedNumber(generateNumber,
                Objects.equals(entity.getIsAverage(), 1), Objects.equals(entity.getIsRandom(), 1)));
            break;
          case 1:
            generatedMessage.addAll(GlobalMessageGeneratedUtil.generatedWord(generateNumber,
                Objects.equals(entity.getIsAverage(), 1), Objects.equals(entity.getIsRandom(), 1)));
            break;
          case 2:
            generatedMessage.addAll(GlobalMessageGeneratedUtil.generatedMingle(generateNumber,
                Objects.equals(entity.getIsAverage(), 1), Objects.equals(entity.getIsRandom(), 1)));
            break;
          default:
            throw new IllegalArgumentException("类型异常");
        }
        for (int i = 0; i < generatedMessage.size(); i++) {
          String message = generatedMessage.get(i);
          List<String> group = new ArrayList<>();
          for (int j = 0; j < message.length(); j++) {
            group.add(String.valueOf(message.charAt(j)));
          }
          GeneralTickerPatTrainPageEntity pageEntity = new GeneralTickerPatTrainPageEntity();
          pageEntity.setTrainId(entity.getId());
          pageEntity.setSort(i);
          pageEntity.setFloorNumber(param.getFloorNumber());
          pageEntity.setMoresKey(JSONUtils.toJson(group));
          pageEntity.setMoresTime("[]");
          pageEntity.setMoresValue("[]");
          pageEntity.setPatKeys("[]");
          contentEntities.add(pageEntity);
        }
        trainPageDao.save(contentEntities);
      }
      addParams = contentEntities.stream()
          .map(e -> PojoUtils.convertOne(e, GeneralTickerPatTrainContentAddParam.class))
          .toList();

    // 查询此页提交内容
    GeneralTickerPatTrainUserValueEntity contentFloorValueEntity = userValueDao
        .findByFloorNumberAndTrainIdAndUserId(param.getFloorNumber(), param.getId(), member.getUserId());
    GeneralTickerPatTrainContentVO result = new GeneralTickerPatTrainContentVO()
        .setMessageBody(contentFloorValueEntity == null ? JSONUtils.toJson(addParams) : contentFloorValueEntity.getMessageBody())
        .setMessageKey(addParams)
        .setFinishInfo(contentFloorValueEntity == null ? "[]" : contentFloorValueEntity.getFinishInfo())
        .setStandard(contentFloorValueEntity == null ? "[]" : contentFloorValueEntity.getStandard())
        .setResolver(contentFloorValueEntity == null ? "[]" : contentFloorValueEntity.getResolver());
    result.setProtocolVersion(entity.getProtocolVersion()).setAttempt(member.getAttempt())
        .setServerElapsedMs(elapsedMillis(member.getCaptureStartedAt()))
        .setSubmitted(contentFloorValueEntity != null)
        .setSavedCaptureIntervals(contentFloorValueEntity == null || !Objects.equals(entity.getProtocolVersion(), 1)
            ? List.of() : captureIntervals(contentFloorValueEntity));
    return result;
  }

  @Transactional
  public PageInfo<GeneralTickerPatTrainVO> findAll(String token, Page page) {
    UserEntity userEntity = userService.getUserByToken(token);
    List<GeneralTickerPatTrainUserEntity> userEntityList = trainUserDao.findByUserId(userEntity.getId());
    PageInfo<GeneralTickerPatTrainEntity> entities = trainDao.findPage((root, criteriaQuery, criteriaBuilder) -> {
      CriteriaBuilder.In<Integer> id = criteriaBuilder.in(root.get("id").as(Integer.class));
      userEntityList.stream()
          .map(GeneralTickerPatTrainUserEntity::getTrainId)
          .forEach(id::value);
      criteriaQuery.where(id);
      criteriaQuery.orderBy(criteriaBuilder.desc(root.get("createTime").as(LocalDateTime.class)));
      return criteriaQuery;
    }, page.getPage() - 1, page.getRows());
    List<GeneralTickerPatTrainVO> convert = PojoUtils.convert(entities.getData(), GeneralTickerPatTrainVO.class,
        (t, r) -> {
          List<GeneralTickerPatTrainUserDto> byTrainId = trainUserDao.findByTrainIdToMap(t.getId(), null);
          r.setUserInfoList(PojoUtils.convert(byTrainId, GeneralTickerPatTrainUserInfoVO.class));
          r.setCodeSort(Objects.equals(t.getCodeSort(), 1));
          r.setIsRandom(Objects.equals(t.getIsRandom(), 1));
          if (Objects.equals(t.getIsCable(), 1)) {
            r.setMessageNumber((int) trainPageDao.count("trainId", t.getId()));
          }
        });
    PageInfo<GeneralTickerPatTrainVO> pageInfo = new PageInfo<>();
    pageInfo.setCurrentPage(entities.getCurrentPage());
    pageInfo.setPageSize(entities.getPageSize());
    pageInfo.setTotalPage(entities.getTotalPage());
    pageInfo.setTotalNumber(entities.getTotalNumber());
    pageInfo.setData(convert);
    return pageInfo;
  }

  @Transactional
  public GeneralTickerPatTrainVO detail(GeneralTickerPatTrainQueryParam param) {
    try {
      // log.info("用户id：{},查询训练信息:{}", Optional.ofNullable(param.getUid()).orElse(""),
      // LocalDateTime.now());
      GeneralTickerPatTrainEntity trainEntity = Optional.ofNullable(trainDao.findById(param.getId()))
          .orElseThrow(() -> new IllegalArgumentException("未查询到训练"));
      // log.info("用户id：{},完成查询训练信息:{}",
      // Optional.ofNullable(param.getUid()).orElse(""), LocalDateTime.now());

      return PojoUtils.convertOne(trainEntity, GeneralTickerPatTrainVO.class, (e, v) -> {
        // 查询该场训练信息
        List<GeneralTickerPatTrainUserDto> trainId = trainUserDao.findByTrainIdToMap(e.getId(), param.getUid());
        List<GeneralTickerPatTrainUserInfoVO> userInfoVOList = PojoUtils.convert(trainId, GeneralTickerPatTrainUserInfoVO.class);
        // log.info("完成查询用户在该训练信息:{}", LocalDateTime.now());
        // 查询用户所在房间状态
        Map<String, Object> httpParam = new HashMap<>();
        httpParam.put(TRAIN_ID, e.getId());
        List<GeneralPatTrainUserDto> userDto = new ArrayList<>();
        for (int i = 0; true; i++) {
          try {
            // log.info("开始调用http调用查询在线人员情况:{},第：{}次", LocalDateTime.now(), i + 1);
            GeneralTickerPatTrainController gGeneralTickerPatTrainSocketController = new GeneralTickerPatTrainController();
            Response<List<GeneralPatTrainUserDto>> userInfo = gGeneralTickerPatTrainSocketController
                .findUserInfo(e.getId());
            userDto.addAll(userInfo.getData());
            break;
          } catch (Exception ex) {
            if (i == 1) {
              log.error("Http2次请求出错，抛出异常");
              throw new IllegalArgumentException("获取在线人数异常");
            }
            log.error("Http请求出错");
          }
        }

        // log.info("完成http调用查询在线人员情况:{}", LocalDateTime.now());

        Map<String, List<GeneralPatTrainUserDto>> collect = userDto.stream()
            .collect(Collectors.groupingBy(GeneralPatTrainUserDto::getId));

        // 统计每个人填报的页码
        if (userInfoVOList != null) {
          for (GeneralTickerPatTrainUserInfoVO item : userInfoVOList) {
            List<GeneralPatTrainUserDto> generalTickerPatTrainUserDtos = collect.get(item.getUserId());
            if (generalTickerPatTrainUserDtos != null && !generalTickerPatTrainUserDtos.isEmpty()) {
              Integer status = generalTickerPatTrainUserDtos.stream()
                  .findFirst()
                  .map(GeneralPatTrainUserDto::getStatus)
                  .orElse(0);
              item.setUserStatus(status);
            } else {
              item.setUserStatus(0);
            }
            if (param.getUid() != null) {
              // log.info("统计填报页数:{}", LocalDateTime.now());
              int size = userValueDao.countByTrainIdAndUserId(e.getId(), item.getUserId());
              // log.info("完成统计填报页数:{}", LocalDateTime.now());
              item.setExistPageNumber(size);
              // log.info("统计页码信息:{}", LocalDateTime.now());
              item.setExistNumber(userValueDao.countByTrainIdAndUserIdGroupByPageNumber(e.getId(), item.getUserId()));
              // log.info("完成统计页码信息:{}", LocalDateTime.now());
            }
            if (Objects.equals(trainEntity.getProtocolVersion(), 1)) {
              item.setValidTime(item.getActiveMillis() == null ? 0 : item.getActiveMillis() / 1000);
            } else if (item.getFinishTime() != null && Objects.equals(item.getIsFinish(), 1)) {
              item.setValidTime(LocalDateTimeUtil.between(trainEntity.getStartTime(), item.getFinishTime()).toMillis() / 1000);
            }
          }
        }
        v.setUserInfoList(userInfoVOList);
        if (Objects.equals(e.getStatus(), 0)) {
          v.setValidTime(0L);
        } else if (Objects.equals(e.getStatus(), 1)) {
          v.setValidTime(LocalDateTimeUtil.between(e.getStartTime(), LocalDateTime.now()).toMillis() / 1000);
        } else if (Objects.equals(e.getStatus(), 2) || Objects.equals(e.getStatus(), 3)) {
          v.setValidTime(LocalDateTimeUtil.between(e.getStartTime(), e.getEndTime()).toMillis() / 1000);
        }
        if (Objects.equals(e.getIsCable(), 1)) {
          v.setMessageNumber((int) trainPageDao.count("trainId", e.getId()));
          v.setPageCount(trainPageDao.findMaxPageNumber(e.getId()));
        }
        log.info("接口完成:{}", LocalDateTime.now());
      });
    } catch (IllegalArgumentException | IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      log.error("查询训练详情失败", e);
      throw new RuntimeException(e);
    }
  }

  @Transactional
  public GeneralTickerPatTrainVO finish(GeneralTickerPatTrainFinishVO dto, String token) {
    String userId = userService.getUserByToken(token).getId();
    GeneralTickerPatTrainEntity entity = lockedTrain(dto.getId());
    GeneralTickerPatTrainUserEntity user = student(entity.getId(), userId);
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
    GeneralTickerPatTrainVO result = finishUser(entity, user);
    if (Objects.equals(entity.getStatus(), 3)
        && trainUserDao.count("trainId = ?1 and role = 0 and (isFinish is null or isFinish <> 1)", entity.getId()) == 0) {
      entity.setStatus(2);
      result.setStatus(2);
    }
    return result;
  }

  private GeneralTickerPatTrainVO finishUser(GeneralTickerPatTrainEntity entity,
      GeneralTickerPatTrainUserEntity user) {
    if (Objects.equals(user.getIsFinish(), 1)) {
      return PojoUtils.convertOne(entity, GeneralTickerPatTrainVO.class, (t, r) -> {
        r.setCodeSort(Objects.equals(t.getCodeSort(), 1));
        r.setIsRandom(Objects.equals(t.getIsRandom(), 1));
      });
    }
    countScore(entity, user.getUserId());
    trainDao.saveAndFlush(entity);
    resultNotifier.publish("ticker", entity.getId(), user.getUserId(),
        trainUserDao.findRoleAdminByUserId(entity.getId()).stream()
            .map(GeneralTickerPatTrainUserEntity::getUserId).toList());
    return PojoUtils.convertOne(entity, GeneralTickerPatTrainVO.class, (t, r) -> {
      r.setCodeSort(Objects.equals(t.getCodeSort(), 1));
      r.setIsRandom(Objects.equals(t.getIsRandom(), 1));
    });
  }

  @Transactional
  public void saveContentValue(GeneralTickerPatTrainContentValueVO dto, String token) {
    LocalDateTime receivedAt = LocalDateTime.now();
    String userId = userService.getUserByToken(token).getId();
    GeneralTickerPatTrainEntity train = lockedTrain(dto.getTrainId());
    GeneralTickerPatTrainUserEntity member = student(train.getId(), userId);
    requireProtocol(train);
    if (!Objects.equals(dto.getAttempt(), member.getAttempt())) {
      throw new IllegalStateException("训练轮次已变化，请重新读取训练");
    }
    if (dto.getFloorNumber() == null || dto.getFloorNumber() < 1
        || dto.getFloorNumber() > (train.getMessageNumber() - 1) / 100 + 1
        || dto.getMessageBody() == null || dto.getStandard() == null) {
      throw new IllegalArgumentException("页码、拍发记录和校准记录不能为空");
    }
    String messageBody = JSONUtils.toJson(dto.getMessageBody());
    String standard = JSONUtils.toJson(dto.getStandard());
    GeneralTickerPatTrainUserValueEntity previous = userValueDao.findByFloorNumberAndTrainIdAndUserId(
        dto.getFloorNumber(), train.getId(), userId);
    if (previous != null) {
      List<CaptureInterval> previousIntervals = captureIntervals(previous);
      if (Objects.equals(previousIntervals, dto.getCaptureIntervals())) {
        if (Objects.equals(previous.getMessageBody(), messageBody) && Objects.equals(previous.getStandard(), standard)
            && Objects.equals(previous.getFinishInfo(), dto.getFinishInfo())) {
          return;
        }
        throw new IllegalStateException("该页采集区间已确认，但拍发内容不一致");
      }
      CaptureTimeline.requireExtension(previousIntervals, dto.getCaptureIntervals());
    }
    if (Objects.equals(member.getIsFinish(), 1)) {
      throw new IllegalStateException("已结算的训练不能上传");
    }
    long duration = CaptureTimeline.durationMillis(dto.getCaptureIntervals(), captureBound(train, member, receivedAt));
    PageMeasure measured = measurePage(dto.getMessageBody());
    if ((measured.characters() > 0 && duration == 0) || measured.symbolMillis() > duration + dto.getCaptureIntervals().size()) {
      throw new IllegalArgumentException("原始按键时长与采集区间不一致");
    }
    List<List<CaptureInterval>> timelines = new ArrayList<>();
    for (GeneralTickerPatTrainUserValueEntity page : userValueDao.list("trainId = ?1 and userId = ?2", train.getId(), userId)) {
      if (!Objects.equals(page.getFloorNumber(), dto.getFloorNumber())) {
        timelines.add(captureIntervals(page));
      }
    }
    timelines.add(dto.getCaptureIntervals());
    CaptureTimeline.requireNoOverlap(timelines);
    GeneralTickerPatTrainUserValueEntity value = new GeneralTickerPatTrainUserValueEntity()
        .setTrainId(train.getId()).setUserId(userId).setFloorNumber(dto.getFloorNumber())
        .setAttempt(dto.getAttempt()).setCaptureIntervals(JSONUtils.toJson(dto.getCaptureIntervals()))
        .setReceivedAt(receivedAt).setMessageBody(messageBody)
        .setStandard(standard).setFinishInfo(dto.getFinishInfo());
    userValueDao.deleteByTrainIdAndFloorNumberAndUserId(train.getId(), dto.getFloorNumber(), userId);
    userValueDao.saveAndFlush(value);
  }

  /**
   * 提供给socket的api
   *
   * @param trainId 训练id
   * @param userId  用户id
   * @return
   */
  @Transactional
  public GeneralPatTrainUserDto getByTrainIdAndUserId(Integer trainId, String userId) {
    GeneralTickerPatTrainUserEntity userTrainEntity = trainUserDao.findByUserIdAndTrainId(userId, trainId);
    UserEntity userEntity = userService.getUserByIdNew(userId);
    if (userEntity == null || userTrainEntity == null) {
      throw new IllegalArgumentException("训练数据异常");
    }
    return new GeneralPatTrainUserDto(userEntity.getId(), userEntity.getUserName(), userEntity.getUserImg(),
        userTrainEntity.getRole());
  }

  /**
   * 开放给sockett的接口
   *
   * @param dto 参数
   */
  @Transactional
  public void updateStatus(GeneralTickerPatTrainUpdateDto dto, String token) {
    String userId = userService.getUserByToken(token).getId();
    GeneralTickerPatTrainEntity tickerPatTrain = lockedTrain(dto.getTrainId());
    GeneralTickerPatTrainUserEntity member = trainUserDao.findByUserIdAndTrainId(userId, dto.getTrainId());
    if (!Objects.equals(tickerPatTrain.getCreateUser(), userId)
        && (member == null || !Objects.equals(member.getRole(), 1))) {
      throw new IllegalArgumentException("无权管理该训练");
    }
    requireProtocol(tickerPatTrain);
    if (!Objects.equals(dto.getStatus(), 1) && !Objects.equals(dto.getStatus(), 2)) {
      throw new IllegalArgumentException("训练状态不合法");
    }
    if (Objects.equals(tickerPatTrain.getStatus(), dto.getStatus())
        || (Objects.equals(tickerPatTrain.getStatus(), 3) && Objects.equals(dto.getStatus(), 2))) {
      return;
    }
    if (Objects.equals(dto.getStatus(), 1)) {
      if (!Objects.equals(tickerPatTrain.getStatus(), 0)) {
        throw new IllegalStateException("训练不能重新开始");
      }
      ScoringRuleValidation.handkey(tickerPatTrain.getRuleContent());
      LocalDateTime now = LocalDateTime.now();
      tickerPatTrain.setStatus(1).setStartTime(now);
      for (GeneralTickerPatTrainUserEntity participant : trainUserDao.findByTrainIdAndRole(dto.getTrainId(), 0)) {
        participant.setCaptureStartedAt(now);
      }
    } else {
      requireUnderway(tickerPatTrain);
      tickerPatTrain.setStatus(3).setEndTime(LocalDateTime.now());
      tickerPatTrain.setValidTime(Duration.between(tickerPatTrain.getStartTime(), tickerPatTrain.getEndTime()).toSeconds());
      if (trainUserDao.count("trainId = ?1 and role = 0 and (isFinish is null or isFinish <> 1)", dto.getTrainId()) == 0) {
        tickerPatTrain.setStatus(2);
      }
    }
  }

  @Transactional
  public List<Integer> closingTrainIds() {
    return trainDao.find("protocolVersion = 1 and status = 3 and endTime <= ?1", LocalDateTime.now().minusSeconds(60))
        .list().stream().map(GeneralTickerPatTrainEntity::getId).toList();
  }

  @Transactional
  public void settleExpired(Integer trainId) {
    GeneralTickerPatTrainEntity train = lockedTrain(trainId);
    if (Objects.equals(train.getProtocolVersion(), 1) && Objects.equals(train.getStatus(), 3)
        && !LocalDateTime.now().isBefore(train.getEndTime().plusSeconds(60))) {
      settleClosing(train);
    }
  }

  private void settleClosing(GeneralTickerPatTrainEntity train) {
    for (GeneralTickerPatTrainUserEntity participant : trainUserDao.findByTrainIdAndRole(train.getId(), 0)) {
      finishUser(train, participant);
    }
    train.setStatus(2);
    trainDao.flush();
  }

  /**
   * 重置训练，清空之前的拍发内容
   *
   * @param param
   */
  @Transactional
  public void reset(GeneralTickerPatTrainResetParam param, String token) {
    String userId = userService.getUserByToken(token).getId();
    GeneralTickerPatTrainEntity entity = lockedTrain(param.getId());
    GeneralTickerPatTrainUserEntity user = student(entity.getId(), userId);
    requireAttempt(param.getAttempt(), user);
    requireUnderway(entity);
    requireProtocol(entity);
    user.setAttempt(Math.incrementExact(user.getAttempt())).setCaptureStartedAt(LocalDateTime.now()).setActiveMillis(null);
    userValueDao.deleteByUserIdAndTrainId(userId, param.getId());
    user.setScore(null).setDeductInfo(null).setStatisticInfo(null).setErrorNumber(0)
        .setAccuracy("0.00").setSpeed("0").setSpeedLog(null).setLack(0)
        .setFinishTime(null).setIsFinish(0);
  }

  /**
   * 获取统计数据
   *
   * @param param
   * @return
   */
  @Transactional
  public GeneralTickerPatTrainStatisticVO statistic(GeneralTickerPatTrainResetParam param) {
    // 查询本次训练信息
    List<GeneralTickerPatTrainUserEntity> trainUserEntities = trainUserDao.findByTrainIdAndRole(param.getId(), 0);
    // 统计分数 和点划间隔虚粗占比
    return statisticsScoreAndDotLineGapRate(trainUserEntities);
  }

  @Transactional
  public void startTrain(Integer trainId, Integer attempt, String token) {
    String userId = userService.getUserByToken(token).getId();
    GeneralTickerPatTrainEntity entity = lockedTrain(trainId);
    GeneralTickerPatTrainUserEntity user = student(trainId, userId);
    requireAttempt(attempt, user);
    captureBound(entity, user, LocalDateTime.now());
    requireProtocol(entity);
    if (Objects.equals(user.getIsFinish(), 1)) {
      throw new IllegalStateException("已结算的训练需要先重置");
    }
    user.setIsFinish(2);
  }

  private GeneralTickerPatTrainEntity lockedTrain(Integer trainId) {
    if (trainId == null) {
      throw new IllegalArgumentException("训练ID不能为空");
    }
    return Optional.ofNullable(trainDao.findById(trainId, LockModeType.PESSIMISTIC_WRITE))
        .orElseThrow(() -> new IllegalArgumentException("未查询到训练"));
  }

  private GeneralTickerPatTrainUserEntity student(Integer trainId, String userId) {
    GeneralTickerPatTrainUserEntity member = trainUserDao.findByUserIdAndTrainId(userId, trainId);
    if (member == null || !Objects.equals(member.getRole(), 0)) {
      throw new IllegalArgumentException("未查询到该用户的学员参训记录");
    }
    return member;
  }

  private GeneralTickerPatTrainUserEntity readableMember(GeneralTickerPatTrainEntity train, String requestedUser, String token) {
    String actor = userService.getUserByToken(token).getId();
    String target = requestedUser == null ? actor : requestedUser;
    GeneralTickerPatTrainUserEntity actorMember = trainUserDao.findByUserIdAndTrainId(actor, train.getId());
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

  private void requireAttempt(Integer attempt, GeneralTickerPatTrainUserEntity member) {
    if (attempt == null || !Objects.equals(attempt, member.getAttempt())) {
      throw new IllegalStateException("训练轮次已变化，请重新读取训练");
    }
  }

  private void requireProtocol(GeneralTickerPatTrainEntity train) {
    if (!Objects.equals(train.getProtocolVersion(), 1)) {
      throw new IllegalStateException("旧训练缺少原始采集协议，请重新创建训练；历史成绩保持不变");
    }
  }

  private long captureBound(GeneralTickerPatTrainEntity train, GeneralTickerPatTrainUserEntity member,
      LocalDateTime receivedAt) {
    if (member.getCaptureStartedAt() == null) {
      throw new IllegalStateException("采集尚未开始");
    }
    long elapsed = Math.max(0, Duration.between(member.getCaptureStartedAt(), receivedAt).toMillis());
    if (Objects.equals(train.getStatus(), 1)) {
      return elapsed;
    }
    if (Objects.equals(train.getStatus(), 3) && train.getEndTime() != null
        && receivedAt.isBefore(train.getEndTime().plusSeconds(60))) {
      return elapsed;
    }
    throw new IllegalStateException("训练已停止接收拍发记录");
  }

  private List<CaptureInterval> captureIntervals(GeneralTickerPatTrainUserValueEntity page) {
    List<CaptureInterval> intervals = JSONUtils.fromJson(page.getCaptureIntervals(), new TypeToken<>() {});
    if (intervals == null) {
      throw new IllegalStateException("已保存页缺少原始采集时间轴");
    }
    return intervals;
  }

  private PageMeasure measurePage(List<GeneralTickerPatTrainContentAddParam> groups) {
    long count = 0;
    double symbolMillis = 0;
    for (GeneralTickerPatTrainContentAddParam group : groups) {
      if (group == null) throw new IllegalArgumentException("拍发组不能为空");
      List<String> characters = JSONUtils.fromJson(group.getPatKeys(), new TypeToken<>() {});
      List<List<String>> signals = JSONUtils.fromJson(group.getMoresValue(), new TypeToken<>() {});
      List<List<Double>> times = JSONUtils.fromJson(group.getMoresTime(), new TypeToken<>() {});
      if (characters == null || signals == null || times == null
          || signals.size() != characters.size() || times.size() != characters.size()) {
        throw new IllegalArgumentException("拍发字符、原始码和时长数量不一致");
      }
      for (int index = 0; index < characters.size(); index++) {
        String character = characters.get(index);
        List<String> signal = signals.get(index);
        List<Double> durations = times.get(index);
        if (character == null || character.codePointCount(0, character.length()) != 1) {
          throw new IllegalArgumentException("每个拍发事件必须是一个正文字符");
        }
        if (signal == null || signal.isEmpty() || durations == null || durations.size() != signal.size()) {
          throw new IllegalArgumentException("原始点划与时长数量不一致");
        }
        for (int symbol = 0; symbol < signal.size(); symbol++) {
          if (!"0".equals(signal.get(symbol)) && !"1".equals(signal.get(symbol))) {
            throw new IllegalArgumentException("原始拍发码只能包含点和划");
          }
          Double milliseconds = durations.get(symbol);
          if (milliseconds == null || !Double.isFinite(milliseconds) || milliseconds < 0) {
            throw new IllegalArgumentException("原始拍发时长必须为有限非负数");
          }
          symbolMillis += milliseconds;
        }
        if (!character.isBlank() && !character.equals("?")) count++;
      }
    }
    return new PageMeasure(count, symbolMillis);
  }

  private record PageMeasure(long characters, double symbolMillis) {}


  private void requireUnderway(GeneralTickerPatTrainEntity entity) {
    if (!Objects.equals(entity.getStatus(), 1)) {
      throw new IllegalStateException("训练不在进行中");
    }
  }

  public AvgResult getClassAvgResult(List<String> userList) {
    BigDecimal totalThisResult = BigDecimal.ZERO;
    BigDecimal totalLastResult = BigDecimal.ZERO;
    for (String user : userList) {
      List<BigDecimal> lastTwoResult = trainUserDao.getClassLastTwoResult(user);
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

  private GeneralTickerPatTrainStatisticVO statisticsScoreAndDotLineGapRate(
      List<GeneralTickerPatTrainUserEntity> trainUserEntities) {
    // 结果集
    GeneralTickerPatTrainStatisticVO ret = new GeneralTickerPatTrainStatisticVO();
    // 成绩分布
    GeneralTickerPatTrainSchoolReportVO reportVO = new GeneralTickerPatTrainSchoolReportVO();
    // 计算点划间隔虚粗占比
    GeneralTickerPatTrainErrorInfoVO errorInfoVO = new GeneralTickerPatTrainErrorInfoVO();
    // 本次成绩和上次成绩对比
    List<GeneralTickerPatTrainUserTendencyVO> userTendencyVOS = new ArrayList<>();

    // 分数分布统计
    int good = 0, nice = 0, belowStandard = 0;

    // 点划间隔统计变量
    int dotMin = 0, lineMin = 0, codeGapMin = 0, wordGapMin = 0, groupGapMin = 0;
    int dotMax = 0, lineMax = 0, codeGapMax = 0, wordGapMax = 0, groupGapMax = 0;
    int dotTotal = 0, lineTotal = 0, codeTotal = 0, wordTotal = 0, groupTotal = 0;

    for (GeneralTickerPatTrainUserEntity trainUser : trainUserEntities) {
      // 分数分布统计
      BigDecimal score = trainUser.getScore();
      if (score.compareTo(TrainConstants.SCORE_EXCELLENT_THRESHOLD) >= 0) {
        good++;
      } else if (score.compareTo(TrainConstants.SCORE_GOOD_THRESHOLD) >= 0) {
        nice++;
      } else {
        belowStandard++;
      }

      // 获取统计信息 点划 码字组间隔粗细
      String statisticInfo = trainUser.getStatisticInfo();
      if (StringUtils.isNotBlank(statisticInfo)) {
        GeneralTickerPatTrainStatisticsVO statisticsVO = JSONUtils.fromJson(statisticInfo,
            GeneralTickerPatTrainStatisticsVO.class);
        if (statisticsVO != null) {
          dotTotal += statisticsVO.getDotMaxNumber() + statisticsVO.getDotMinNumber() + statisticsVO.getDotPerfectNumber();
          lineTotal += statisticsVO.getLineMaxNumber() + statisticsVO.getLineMinNumber() + statisticsVO.getLinePerfectNumber();
          codeTotal += statisticsVO.getCodeMaxNumber() + statisticsVO.getCodeMinNumber() + statisticsVO.getCodePerfectNumber();
          wordTotal += statisticsVO.getWordMaxNumber() + statisticsVO.getWordMinNumber() + statisticsVO.getWordPerfectNumber();
          groupTotal += statisticsVO.getGroupMaxNumber() + statisticsVO.getGroupMinNumber() + statisticsVO.getGroupPerfectNumber();
          dotMin += statisticsVO.getDotMinNumber();
          dotMax += statisticsVO.getDotMaxNumber();
          lineMin += statisticsVO.getLineMinNumber();
          lineMax += statisticsVO.getLineMaxNumber();
          codeGapMin += statisticsVO.getCodeMinNumber();
          codeGapMax += statisticsVO.getCodeMaxNumber();
          wordGapMin += statisticsVO.getWordMinNumber();
          wordGapMax += statisticsVO.getWordMaxNumber();
          groupGapMin += statisticsVO.getGroupMinNumber();
          groupGapMax += statisticsVO.getGroupMaxNumber();
        }
      }

      // 构建用户成绩趋势
      String userId = trainUser.getUserId();
      UserEntity userEntity = userService.getUserByIdNew(userId);
      if (null == userEntity) {
        continue;
      }
      GeneralTickerPatTrainUserTendencyVO userTendencyVO = new GeneralTickerPatTrainUserTendencyVO();
      List<GeneralTickerPatTrainUserEntity> patTrainUserTop2 = trainUserDao.findByUseridTop2(trainUser.getCreateTime(), userEntity.getId());
      for (int i = 0; i < patTrainUserTop2.size(); i++) {
        if (i == 0) {
          userTendencyVO.setLastScore(patTrainUserTop2.get(i).getScore());
        } else {
          userTendencyVO.setLastLastScore(patTrainUserTop2.get(i).getScore());
        }
      }
      userTendencyVO.setUserId(userId);
      userTendencyVO.setUserName(userEntity.getUserName());
      userTendencyVO.setUserImg(userEntity.getUserImg());
      userTendencyVO.setThisScore(trainUser.getScore());
      userTendencyVOS.add(userTendencyVO);
    }

    // 计算点划间隔虚粗占比
    errorInfoVO.setDotMin(calculateRate(dotMin, dotTotal));
    errorInfoVO.setDotMax(calculateRate(dotMax, dotTotal));
    errorInfoVO.setLineMin(calculateRate(lineMin, lineTotal));
    errorInfoVO.setLineMax(calculateRate(lineMax, lineTotal));
    errorInfoVO.setCodeGapMin(calculateRate(codeGapMin, codeTotal));
    errorInfoVO.setCodeGapMax(calculateRate(codeGapMax, codeTotal));
    errorInfoVO.setWordGapMin(calculateRate(wordGapMin, wordTotal));
    errorInfoVO.setWordGapMax(calculateRate(wordGapMax, wordTotal));
    errorInfoVO.setGroupGapMin(calculateRate(groupGapMin, groupTotal));
    errorInfoVO.setGroupGapMax(calculateRate(groupGapMax, groupTotal));

    // 构建成绩分布
    int total = trainUserEntities.size();
    GeneralTickerPatTrainScoreInfoVO goodInfo = new GeneralTickerPatTrainScoreInfoVO();
    goodInfo.setRate(calculateRate(good, total));
    goodInfo.setPeopleNumber(good);
    reportVO.setGood(goodInfo);

    GeneralTickerPatTrainScoreInfoVO niceInfo = new GeneralTickerPatTrainScoreInfoVO();
    niceInfo.setRate(calculateRate(nice, total));
    niceInfo.setPeopleNumber(nice);
    reportVO.setNice(niceInfo);

    GeneralTickerPatTrainScoreInfoVO belowStandardInfo = new GeneralTickerPatTrainScoreInfoVO();
    belowStandardInfo.setRate(calculateRate(belowStandard, total));
    belowStandardInfo.setPeopleNumber(belowStandard);
    reportVO.setBelowStandard(belowStandardInfo);

    // 封装结果
    ret.setSchoolReport(reportVO);
    ret.setErrorInfoVO(errorInfoVO);
    ret.setUserTendencyVO(userTendencyVOS);
    return ret;
  }

  /**
   * 计算分数
   *
   * @param entity
   * @param userId 已校验的参训用户
   */
  private void countScore(GeneralTickerPatTrainEntity entity, String userId) {
    try {
      Map<String, Integer> deductMap = new HashMap<>();
      requireProtocol(entity);
      Integer score = Optional.ofNullable(entity.getRuleScore())
          .orElseThrow(() -> new IllegalStateException("训练缺少冻结的规则满分"));
      PostTelegramTrainRule rule = ScoringRuleValidation.handkey(entity.getRuleContent());
      PostTelegramTrainStatisticsVO statisticsVO = new PostTelegramTrainStatisticsVO();
      PostTelegramTrainScoreVO scoreVO = new PostTelegramTrainScoreVO();

      List<Integer> existFloorNumber = userValueDao.countByTrainIdAndUserIdGroupByPageNumber(entity.getId(), userId);
      processPageComparisons(entity, userId, existFloorNumber, scoreVO, rule, statisticsVO);

      statisticsAllAvg(statisticsVO, 0, 0, 0, 0, 0);

      int lack = calculateLackCount(entity.getMessageNumber(), existFloorNumber, rule, scoreVO);

      score = applyDeductions(score, scoreVO, rule, deductMap);

      saveTrainUserResult(entity, userId, scoreVO, score, lack, statisticsVO, deductMap, rule);
    } catch (IllegalArgumentException | IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      log.error("计算分数失败，训练ID: {}", entity.getId(), e);
      throw new RuntimeException("计算分数异常", e);
    }
  }

  private void processPageComparisons(GeneralTickerPatTrainEntity entity, String userId, List<Integer> existFloorNumber,
      PostTelegramTrainScoreVO scoreVO, PostTelegramTrainRule rule, PostTelegramTrainStatisticsVO statisticsVO) {
    for (Integer floorNumber : existFloorNumber) {
      GeneralTickerPatTrainUserValueEntity contentFloorValueEntity = userValueDao
          .findByFloorNumberAndTrainIdAndUserId(floorNumber, entity.getId(), userId);
      String messageBody = contentFloorValueEntity.getMessageBody();
      String standard = contentFloorValueEntity.getStandard();
      List<PostTelegramTrainContentAddParam> userContents = JSONUtils.fromJson(messageBody, new TypeToken<>() {
      });
      List<PostTelegramTrainFinishInfoDto> standards = JSONUtils.fromJson(standard, new TypeToken<>() {
      });

      List<GeneralTickerPatTrainPageEntity> userValueEntities = trainPageDao
          .findByFloorNumberAndTrainIdOrderBySort(floorNumber, entity.getId());
      List<String> sources = userValueEntities.stream().map(GeneralTickerPatTrainPageEntity::getMoresKey)
          .map(item -> item.substring(1).replaceAll("[\",\\]]", "")).toList();
      List<String> patKeys = userContents != null
          ? userContents.stream().map(PostTelegramTrainContentAddParam::getPatKeys)
              .map(item -> item.substring(1).replaceAll("[\",\\]]", "")).toList()
          : null;

      PostTelegramTrainResolverVO comparison = messageComparisonService.comparison(sources, patKeys, scoreVO,
          userContents, standards, rule, statisticsVO);
      contentFloorValueEntity.setResolver(JSONUtils.toJson(comparison));
    }
  }

  private int calculateLackCount(Integer messageNumber, List<Integer> existFloorNumber,
      PostTelegramTrainRule rule, PostTelegramTrainScoreVO scoreVO) {
    int lack = 0;
    int totalFloorNumber = messageNumber / 100;
    if (messageNumber % 100 > 0) {
      totalFloorNumber++;
    }
    List<Integer> existPageNumber = new ArrayList<>();
    for (int i = 0; i < totalFloorNumber; i++) {
      existPageNumber.add(i + 1);
    }
    existPageNumber.removeAll(existFloorNumber);
    for (int i = 0; i < existPageNumber.size(); i++) {
      if (i != existPageNumber.size() - 1) {
        lack += 100;
      } else {
        lack += messageNumber % 100;
      }
    }
    scoreVO.setLackGroup(scoreVO.getLackGroup() + existPageNumber.size() * 100);
    return lack;
  }

  private int applyDeductions(Integer baseScore, PostTelegramTrainScoreVO scoreVO, PostTelegramTrainRule rule,
      Map<String, Integer> deductMap) {
    int score = baseScore;

    int dotScore = calculateScore(rule.getDot().getMax(), scoreVO.getDotScore(), rule.getDot().getMax());
    score -= dotScore;
    deductMap.put("dotMinScore", dotScore);
    deductMap.put("dotMinNumber", scoreVO.getDotScore());

    int lineScore = calculateScore(rule.getDash().getMax(), scoreVO.getLineScore(), rule.getDash().getMax());
    score -= lineScore;
    deductMap.put("lineScore", lineScore);
    deductMap.put("lineNumber", scoreVO.getLineScore());

    int codeScore = calculateScore(rule.getLittle().getMax(), scoreVO.getCodeScore(), rule.getLittle().getMax());
    score -= codeScore;
    deductMap.put("codeGapScore", codeScore);
    deductMap.put("codeNumber", scoreVO.getCodeScore());

    int wordScore = calculateScore(rule.getMiddle().getMax(), scoreVO.getWordScore(), rule.getMiddle().getMax());
    score -= wordScore;
    deductMap.put("wordGapScore", wordScore);
    deductMap.put("wordNumber", scoreVO.getWordScore());

    int groupScore = calculateScore(rule.getLarge().getMax(), scoreVO.getGroupScore(), rule.getLarge().getMax());
    score -= groupScore;
    deductMap.put("groupGapScore", groupScore);
    deductMap.put("groupNumber", scoreVO.getGroupScore());

    int alterErrorScore = calculateScore(rule.getAlterError().getMax(), scoreVO.getAlterErrorScore(), rule.getAlterError().getMax());
    score -= alterErrorScore;
    deductMap.put("alterErrorScore", alterErrorScore);
    deductMap.put("alterErrorNumber", scoreVO.getAlterErrorScore());

    int errorCode = calculateScore(rule.getErrorCode().getMax(), scoreVO.getErrorNumber() * rule.getErrorCode().getL(), rule.getErrorCode().getMax());
    score -= errorCode;
    deductMap.put("errorWord", errorCode);
    deductMap.put("errorWordNumber", scoreVO.getErrorNumber());

    int moreOrLackWord = calculateScore(rule.getQuantoCode().getMax(), scoreVO.getMoreOrLackWord() * rule.getQuantoCode().getL(), rule.getQuantoCode().getMax());
    score -= moreOrLackWord;
    deductMap.put("quantoCode", moreOrLackWord);
    deductMap.put("quantoCodeNumber", scoreVO.getMoreOrLackWord());

    int moreOrLackGroup = calculateScore(rule.getQuantoGroup().getMax(), (scoreVO.getMoreGroup() + scoreVO.getLackGroup()) * rule.getQuantoGroup().getL(), rule.getQuantoGroup().getMax());
    score -= moreOrLackGroup;
    deductMap.put("quantoGroup", moreOrLackGroup);
    deductMap.put("quantoGroupNumber", scoreVO.getMoreGroup());

    int moreOrLackLine = calculateScore(rule.getQuantoRow().getMax(), scoreVO.getMoreOrLackLine() * rule.getQuantoRow().getL(), rule.getQuantoRow().getMax());
    score -= moreOrLackLine;
    deductMap.put("quantoRow", moreOrLackLine);
    deductMap.put("quantoRowNumber", scoreVO.getMoreOrLackLine());

    int bunchGroup = calculateScore(rule.getBunchGroup().getMax(), scoreVO.getBunchGroup() * rule.getBunchGroup().getL(), rule.getBunchGroup().getMax());
    score -= bunchGroup;
    deductMap.put("bunchGroup", bunchGroup);
    deductMap.put("bunchGroupNumber", scoreVO.getBunchGroup());

    return score;
  }

  private void saveTrainUserResult(GeneralTickerPatTrainEntity entity, String userId, PostTelegramTrainScoreVO scoreVO,
      int score, int lack, PostTelegramTrainStatisticsVO statisticsVO, Map<String, Integer> deductMap, PostTelegramTrainRule rule) {
    GeneralTickerPatTrainUserEntity trainUserEntity = trainUserDao.findByUserIdAndTrainId(userId, entity.getId());

    trainUserEntity.setErrorNumber(scoreVO.getErrorNumber());
    trainUserEntity.setLack(lack);

    if (scoreVO.getCorrect() == 0) {
      trainUserEntity.setAccuracy("0.00");
    } else {
      String accuracy = new BigDecimal(scoreVO.getCorrect())
          .divide(new BigDecimal(scoreVO.getPatTotalNum()), 2, RoundingMode.HALF_UP)
          .multiply(new BigDecimal(100)).toString();
      trainUserEntity.setAccuracy(accuracy);
    }

    long activeMillis = 0;
    long characters = 0;
    List<String> pageRates = new ArrayList<>();
    List<GeneralTickerPatTrainUserValueEntity> savedPages = userValueDao.find(
        "trainId = ?1 and userId = ?2 order by floorNumber", entity.getId(), userId).list();
    for (GeneralTickerPatTrainUserValueEntity page : savedPages) {
      List<GeneralTickerPatTrainContentAddParam> body = JSONUtils.fromJson(page.getMessageBody(), new TypeToken<>() {});
      long pageCharacters = measurePage(body).characters();
      long pageMillis = CaptureTimeline.durationMillis(captureIntervals(page), Long.MAX_VALUE);
      characters += pageCharacters;
      activeMillis += pageMillis;
      pageRates.add(TrainingRateUnit.CHARACTERS_PER_MINUTE.rate(pageCharacters, pageMillis).toPlainString());
    }
    trainUserEntity.setActiveMillis(activeMillis);
    trainUserEntity.setSpeed(TrainingRateUnit.CHARACTERS_PER_MINUTE.rate(characters, activeMillis).toPlainString());
    trainUserEntity.setSpeedLog(JSONUtils.toJson(pageRates));

    int wpmScore = calculateWpmScore(rule.getWpm(), new BigDecimal(trainUserEntity.getSpeed()).intValue());
    deductMap.put("wpmScore", wpmScore);
    score += wpmScore;

    trainUserEntity.setScore(new BigDecimal(score));
    trainUserEntity.setStatisticInfo(JSONUtils.toJson(statisticsVO));
    trainUserEntity.setDeductInfo(JSONUtils.toJson(deductMap));
    trainUserEntity.setIsFinish(1);
    trainUserEntity.setFinishTime(LocalDateTime.now());
    trainUserDao.save(trainUserEntity);
  }

  /**
   * 速率加减分：委托全仓唯一实现 {@link ScoreMath#wpmScore(int, BigDecimal, BigDecimal, int)}，
   * 高于基准按 R 加分、低于基准按 L 扣分。
   *
   * @param baseWpm 速率规则
   * @param speed   本次训练的平均拍发速度
   * @return 速率项得分，正数为加分、负数为扣分
   */
  public static int calculateWpmScore(SpeedDeduct baseWpm, int speed) {
    Integer r = baseWpm.getR();
    Integer l = baseWpm.getL();
    return ScoreMath.wpmScore(baseWpm.getBase(),
        r == null ? null : BigDecimal.valueOf(r),
        l == null ? null : BigDecimal.valueOf(l),
        speed).intValue();
  }

  public GeneralPatTrainUserDto getTrainUserInfo(String uid, Integer trainId) {
    GeneralTickerPatTrainEntity train = trainDao.findById(trainId);
    GeneralTickerPatTrainUserEntity membership = trainUserDao.findByUserIdAndTrainId(uid, trainId);
    UserEntity user = userService.getUserByIdNew(uid);
    if (train == null || membership == null || user == null) {
      throw new IllegalArgumentException("训练数据异常");
    }
    return new GeneralPatTrainUserDto(
        user.getId(), user.getUserName(), user.getUserImg(), membership.getRole());
  }

  /**
   * 统计所有点划和所有间隔的平均时长
   *
   * @param statisticsVO   统计对象
   * @param dotTotalTime   点
   * @param lineTotalTime  划
   * @param codeTotalTime  码
   * @param wordTotalTime  词
   * @param groupTotalTime 组
   */

  private void statisticsAllAvg(PostTelegramTrainStatisticsVO statisticsVO, int dotTotalTime, int lineTotalTime,
      int codeTotalTime, int wordTotalTime, int groupTotalTime) {
    // 计算点划间隔的平均时长
    if (statisticsVO.getDotMaxNumber() + statisticsVO.getDotMinNumber() + statisticsVO.getDotPerfectNumber() != 0) {
      statisticsVO.setDotAvg(calculateTS(dotTotalTime, statisticsVO.getDotMaxNumber(), statisticsVO.getDotMinNumber(),
          statisticsVO.getDotPerfectNumber()));
    }
    if (statisticsVO.getLineMaxNumber() + statisticsVO.getLineMinNumber() + statisticsVO.getLinePerfectNumber() != 0) {
      statisticsVO.setLineAvg(calculateTS(lineTotalTime, statisticsVO.getLineMaxNumber(),
          statisticsVO.getLineMinNumber(), statisticsVO.getLinePerfectNumber()));
    }
    if (statisticsVO.getCodeMaxNumber() + statisticsVO.getCodeMinNumber() + statisticsVO.getCodePerfectNumber() != 0) {
      statisticsVO.setCodeAvg(calculateTS(codeTotalTime, statisticsVO.getCodeMaxNumber(),
          statisticsVO.getCodeMinNumber(), statisticsVO.getCodePerfectNumber()));
    }
    if (statisticsVO.getWordMaxNumber() + statisticsVO.getWordMinNumber() + statisticsVO.getWordPerfectNumber() != 0) {
      statisticsVO.setWordAvg(calculateTS(wordTotalTime, statisticsVO.getWordMaxNumber(),
          statisticsVO.getWordMinNumber(), statisticsVO.getWordPerfectNumber()));
    }
    if (statisticsVO.getGroupMaxNumber() + statisticsVO.getGroupMinNumber()
        + statisticsVO.getGroupPerfectNumber() != 0) {
      statisticsVO.setGroupAvg(calculateTS(groupTotalTime, statisticsVO.getGroupMaxNumber(),
          statisticsVO.getGroupMinNumber(), statisticsVO.getGroupPerfectNumber()));
    }
  }
}
