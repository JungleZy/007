package com.nip.service.general;

import cn.hutool.core.date.LocalDateTimeUtil;
import com.google.gson.reflect.TypeToken;
import com.nip.common.PageInfo;
import com.nip.common.constants.CodeConstants;
import com.nip.common.response.Response;
import com.nip.common.utils.*;
import com.nip.controller.general.GeneralTickerPatTrainController;
import com.nip.dao.GradingRuleDao;
import com.nip.dao.general.ticker.GeneralTickerPatTrainDao;
import com.nip.dao.general.ticker.GeneralTickerPatTrainPageDao;
import com.nip.dao.general.ticker.GeneralTickerPatTrainUserDao;
import com.nip.dao.general.ticker.GeneralTickerPatTrainUserValueDao;
import com.nip.dto.GeneralTickerPatTrainUserDto;
import com.nip.dto.PostTelegramTrainFinishInfoDto;
import com.nip.dto.general.GeneralTickerPatTrainUserInfoVO;
import com.nip.dto.general.GeneralTickerPatTrainVO;
import com.nip.dto.general.*;
import com.nip.dto.score.PostTelegramTrainRule;
import com.nip.dto.score.SpeedDeduct;
import com.nip.dto.vo.PostTelegramTrainResolverDetailVO;
import com.nip.dto.vo.PostTelegramTrainResolverVO;
import com.nip.dto.vo.PostTelegramTrainScoreVO;
import com.nip.dto.vo.PostTelegramTrainStatisticsVO;
import com.nip.dto.vo.param.PostTelegramTrainContentAddParam;
import com.nip.dto.vo.param.simulation.tickerPat.*;
import com.nip.dto.vo.simulation.tickerPat.*;
import com.nip.entity.GradingRuleEntity;
import com.nip.entity.UserEntity;
import com.nip.entity.simulation.ticker.GeneralTickerPatTrainEntity;
import com.nip.entity.simulation.ticker.GeneralTickerPatTrainPageEntity;
import com.nip.entity.simulation.ticker.GeneralTickerPatTrainUserEntity;
import com.nip.entity.simulation.ticker.GeneralTickerPatTrainUserValueEntity;
import com.nip.service.CableFloorService;
import com.nip.service.MessageComparisonService;
import com.nip.service.UserService;
import com.nip.ws.WebSocketService;
import com.nip.ws.model.ResponseModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static com.nip.common.constants.BaseConstants.TRAIN_ID;
import static com.nip.common.utils.TickerPatUtils.*;
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
    if (param.getIsCable() == 0) {
      Integer messageNumber = param.getMessageNumber();
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
        switch (param.getType()) {
          case 0:
            messages.addAll(
                GlobalMessageGeneratedUtil.generatedNumber(generateNum, param.getIsAverage(), param.getIsRandom()));
            break;
          case 1:
            messages.addAll(
                GlobalMessageGeneratedUtil.generatedWord(generateNum, param.getIsAverage(), param.getIsRandom()));
            break;
          case 2:
            messages.addAll(
                GlobalMessageGeneratedUtil.generatedMingle(generateNum, param.getIsAverage(), param.getIsRandom()));
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
      int totalPage = param.getMessageNumber() / 100;
      cableFloor = cableFloor.subList(0, totalPage);
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
          trainPageDao.save(pageEntity);
        }
      }
    }

    return PojoUtils.convertOne(save, GeneralTickerPatTrainVO.class);
  }

  @Transactional
  public GeneralTickerPatTrainContentVO findMessageBody(GeneralTickerPatTrainPageParam param, String token) {
    UserEntity userEntity = userService.getUserByIdNew(param.getUserId());
    GeneralTickerPatTrainEntity entity = trainDao.findById(param.getId());
    Optional.ofNullable(entity)
        .orElseThrow(() -> new IllegalArgumentException("未查询到训练"));
    List<GeneralTickerPatTrainPageEntity> contentEntities;
    List<GeneralTickerPatTrainContentAddParam> addParams;
    synchronized (this) {
      contentEntities = trainPageDao.findByFloorNumberAndTrainIdOrderBySort(param.getFloorNumber(), param.getId());
      if (contentEntities.isEmpty()) {
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
                entity.getIsAverage() == 0, entity.getIsRandom() == 1));
            break;
          case 1:
            generatedMessage.addAll(GlobalMessageGeneratedUtil.generatedWord(generateNumber, entity.getIsAverage() == 0,
                entity.getIsRandom() == 1));
            break;
          case 2:
            generatedMessage.addAll(GlobalMessageGeneratedUtil.generatedMingle(generateNumber,
                entity.getIsAverage() == 0, entity.getIsRandom() == 1));
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
    }

    // 查询此页提交内容
    GeneralTickerPatTrainUserValueEntity contentFloorValueEntity = userValueDao
        .findByFloorNumberAndTrainIdAndUserId(param.getFloorNumber(), param.getId(), userEntity.getId());
    List<GeneralTickerPatTrainContentAddParam> finalAddParams = addParams;
    return new GeneralTickerPatTrainContentVO(
        Optional.ofNullable(contentFloorValueEntity)
            .map(GeneralTickerPatTrainUserValueEntity::getMessageBody)
            .orElseGet(() -> JSONUtils.toJson(finalAddParams)),
        addParams,
        Optional.ofNullable(contentFloorValueEntity)
            .map(GeneralTickerPatTrainUserValueEntity::getFinishInfo)
            .orElse("[]"),
        Optional.ofNullable(contentFloorValueEntity)
            .map(GeneralTickerPatTrainUserValueEntity::getStandard)
            .orElse("[]"),
        Optional.ofNullable(contentFloorValueEntity)
            .map(GeneralTickerPatTrainUserValueEntity::getResolver)
            .orElse("[]"));
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
          List<GeneralTickerPatTrainUserInfoVO> userInfoVOList = JSONUtils.fromJson(JSONUtils.toJson(byTrainId),
              new TypeToken<>() {
              });
          r.setUserInfoList(userInfoVOList);
          r.setCodeSort(t.getCodeSort().compareTo(1) == 0);
          r.setIsRandom(t.getIsRandom().compareTo(1) == 0);
          if (t.getIsCable() == 1) {
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
      GeneralTickerPatTrainEntity trainEntity = trainDao.findById(param.getId());
      // log.info("用户id：{},完成查询训练信息:{}",
      // Optional.ofNullable(param.getUid()).orElse(""), LocalDateTime.now());

      return PojoUtils.convertOne(trainEntity, GeneralTickerPatTrainVO.class, (e, v) -> {
        // 查询该场训练信息
        // log.info("用户id：{},查询用户在该训练信息:{}",
        // Optional.ofNullable(param.getUid()).orElse(""), LocalDateTime.now());
        List<GeneralTickerPatTrainUserDto> trainId = trainUserDao.findByTrainIdToMap(e.getId(), param.getUid());
        List<GeneralTickerPatTrainUserInfoVO> userInfoVOList = JSONUtils.fromJson(JSONUtils.toJson(trainId),
            new TypeToken<>() {
            });
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
            // 如果已完成填报，计算训练持续时长
            if (item.getFinishTime() != null && item.getIsFinish().compareTo(1) == 0) {
              LocalDateTime finishTime = Optional.of(item.getFinishTime())
                  .orElse(LocalDateTime.now());
              item.setValidTime(LocalDateTimeUtil.between(trainEntity.getStartTime(), finishTime).toMillis() / 1000);
            }
          }
        }
        v.setUserInfoList(userInfoVOList);
        if (e.getStatus().compareTo(0) == 0) {
          v.setValidTime(0L);
        } else if (e.getStatus().compareTo(1) == 0) {
          v.setValidTime(LocalDateTimeUtil.between(e.getStartTime(), LocalDateTime.now()).toMillis() / 1000);
        } else if (e.getStatus().compareTo(2) == 0) {
          v.setValidTime(LocalDateTimeUtil.between(e.getStartTime(), e.getEndTime()).toMillis() / 1000);
        }
        if (e.getIsCable() == 1) {
          v.setMessageNumber((int) trainPageDao.count("trainId", e.getId()));
          v.setPageCount(trainPageDao.findMaxPageNumber(e.getId()));
        }
        log.info("接口完成:{}", LocalDateTime.now());
      });
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  @Transactional
  public GeneralTickerPatTrainVO finish(GeneralTickerPatTrainFinishVO dto) {
    GeneralTickerPatTrainEntity entity = trainDao.findById(dto.getId());
    // 校验状态是否是进行中
    // throw new RuntimeException(entity.getName() + "训练的状态不是进行中");

    // 分数计算
    countScore(entity, dto);
    trainDao.saveAndFlush(entity);
    trainUserDao.findRoleAdminByUserId(dto.getId()).forEach(admin -> {
      WebSocketService.sendInfo(admin.getUserId(), new ResponseModel(CodeConstants.NOTIFICATION_TRAIN_RESULT.getCode(),
          Map.of(
              "type", "ticker",
              "userId", dto.getUserId(),
              "trainId", entity.getId())));
    });
    return PojoUtils.convertOne(entity, GeneralTickerPatTrainVO.class, (t, r) -> {
      r.setCodeSort(t.getCodeSort().compareTo(1) == 0);
      r.setIsRandom(t.getIsRandom().compareTo(1) == 0);
    });
  }

  @Transactional
  public void saveContentValue(GeneralTickerPatTrainContentValueVO dto) {
    GeneralTickerPatTrainEntity trainEntity = trainDao.findById(dto.getTrainId());

    GeneralTickerPatTrainUserEntity trainUserEntity = trainUserDao.findByUserIdAndTrainId(dto.getUserId(),
        trainEntity.getId());
    trainUserEntity.setIsFinish(0);

    // 记录每页速率
    List<String> speedLog = Optional.ofNullable(trainUserEntity.getSpeedLog())
        .map(speed -> JSONUtils.fromJson(speed, new TypeToken<List<String>>() {
        }))
        .orElseGet(ArrayList::new);
    speedLog.add(dto.getSpeed());
    trainUserEntity.setSpeedLog(JSONUtils.toJson(speedLog));
    trainUserEntity.setErrorNumber(dto.getErrorNumber());
    trainUserEntity.setAccuracy(dto.getAccuracy());

    GeneralTickerPatTrainUserValueEntity trainUserValueEntity = PojoUtils.convertOne(dto,
        GeneralTickerPatTrainUserValueEntity.class, (d, e) -> {
          List<GeneralTickerPatTrainContentAddParam> messageBody = d.getMessageBody();
          e.setMessageBody(JSONUtils.toJson(messageBody));
          List<GeneralTickerPatTrainFinishInfoVO> standard = dto.getStandard();
          e.setStandard(JSONUtils.toJson(standard));

        });
    // 保存拍发速率
    // postTelegramTrainDao.save(trainEntity);
    trainUserDao.save(trainUserEntity);
    // 删除之前保存的训练记录
    userValueDao.deleteByTrainIdAndFloorNumberAndUserId(dto.getTrainId(), dto.getFloorNumber(), dto.getUserId());
    userValueDao.save(trainUserValueEntity);
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
  public void updateStatus(GeneralTickerPatTrainUpdateDto dto) {
    GeneralTickerPatTrainEntity tickerPatTrain = trainDao.findById(dto.getTrainId());
    tickerPatTrain.setStatus(dto.getStatus());
    if (tickerPatTrain.getStatus().compareTo(1) == 0) {
      tickerPatTrain.setStartTime(LocalDateTime.now());
    }
    if (tickerPatTrain.getStatus().compareTo(2) == 0) {
      tickerPatTrain.setEndTime(LocalDateTime.now());
      // 计算训练时长
      tickerPatTrain.setValidTime(
          LocalDateTimeUtil.between(tickerPatTrain.getStartTime(), tickerPatTrain.getEndTime(), ChronoUnit.MINUTES));
      // 查询出该训练中的所有人
      List<GeneralTickerPatTrainUserEntity> trainUserEntities = trainUserDao.findByTrainIdAndRole(dto.getTrainId(), 0);
      // 查询用户所在房间状态
      Map<String, Object> httpParam = new HashMap<>();
      httpParam.put(TRAIN_ID, dto.getTrainId());
      List<GeneralPatTrainUserDto> userDto = new ArrayList<>();
      try {
        // userDto.addAll(HttpUtils.getRequestList(BASE_URL + FIND_USER_INFO, httpParam,
        // GeneralTickerPatTrainUserDto.class));
        GeneralTickerPatTrainController gGeneralTickerPatTrainSocketController = new GeneralTickerPatTrainController();
        Response<List<GeneralPatTrainUserDto>> userInfo = gGeneralTickerPatTrainSocketController
            .findUserInfo(dto.getTrainId());
        userDto.addAll(userInfo.getData());
      } catch (Exception ex) {
        ex.printStackTrace();
      }
      List<String> userId = userDto.stream().map(GeneralPatTrainUserDto::getId).toList();
      // 拿到过滤后的人员信息
      List<GeneralTickerPatTrainUserEntity> userEntities = trainUserEntities.stream()
          .filter(item -> userId.contains(item.getUserId()))
          .toList();
      for (GeneralTickerPatTrainUserEntity trainUser : userEntities) {
        // 查询出用户是否有提交内容
        Integer integer = userValueDao.countByTrainIdAndUserId(dto.getTrainId(), trainUser.getUserId());
        if (integer > 0) {
          // 自动提交
          GeneralTickerPatTrainFinishVO vo = new GeneralTickerPatTrainFinishVO();
          vo.setId(dto.getTrainId());
          vo.setUserId(trainUser.getUserId());
          finish(vo);
        }
      }
    }
    trainDao.save(tickerPatTrain);
  }

  /**
   * 重置训练，清空之前的拍发内容
   *
   * @param param
   */
  @Transactional
  public void reset(GeneralTickerPatTrainResetParam param) {
    userValueDao.deleteByUserIdAndTrainId(param.getUid(), param.getId());
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

  public void startTrain(Integer trainId, String token) {
    String userId = userService.getUserByToken(token).getId();
    GeneralTickerPatTrainUserEntity patUserEntity = trainUserDao.findByUserIdAndTrainId(userId, trainId);
    if (null != patUserEntity) {
      patUserEntity.setIsFinish(2);
      trainUserDao.save(patUserEntity);
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

  /**
   * 统计分数 和点划间隔虚粗占比
   *
   * @param trainUserEntities
   */
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
    int good = 0;
    int nice = 0;
    int belowStandard = 0;

    int dotMin = 0;
    int lineMin = 0;
    int codeGapMin = 0;
    int wordGapMin = 0;
    int groupGapMin = 0;
    int dotMax = 0;
    int lineMax = 0;
    int codeGapMax = 0;
    int wordGapMax = 0;
    int groupGapMax = 0;
    int dotTotal = 0;
    int lineTotal = 0;
    int codeTotal = 0;
    int wordTotal = 0;
    int groupTotal = 0;

    for (GeneralTickerPatTrainUserEntity trainUser : trainUserEntities) {
      BigDecimal score = trainUser.getScore();
      if (score.compareTo(new BigDecimal(90)) > -1) {
        good++;
      } else if (score.compareTo(new BigDecimal(70)) > -1) {
        nice++;
      } else {
        belowStandard++;
      }

      // 获取统计信息 点划 码字组间隔粗细
      String statisticInfo = trainUser.getStatisticInfo();
      if (StringUtils.isNotBlank(statisticInfo)) {
        GeneralTickerPatTrainStatisticsVO statisticsVO = JSONUtils.fromJson(statisticInfo,
            GeneralTickerPatTrainStatisticsVO.class);
        // 各项指标总和
        if (statisticsVO != null) {
          dotTotal += statisticsVO.getDotMaxNumber() + statisticsVO.getDotMinNumber()
              + statisticsVO.getDotPerfectNumber();
          lineTotal += statisticsVO.getLineMaxNumber() + statisticsVO.getLineMinNumber()
              + statisticsVO.getLinePerfectNumber();
          codeTotal += statisticsVO.getCodeMaxNumber() + statisticsVO.getCodeMinNumber()
              + statisticsVO.getCodePerfectNumber();
          wordTotal += statisticsVO.getWordMaxNumber() + statisticsVO.getWordMinNumber()
              + statisticsVO.getWordPerfectNumber();
          groupTotal += statisticsVO.getGroupMaxNumber() + statisticsVO.getGroupMinNumber()
              + statisticsVO.getGroupPerfectNumber();
          // 统计各项数据虚和粗的数量
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

      // 统计参训人拍发态势 本次本次训练和与上次训练分数对比
      String userId = trainUser.getUserId();
      UserEntity userEntity = userService.getUserByIdNew(userId);

      GeneralTickerPatTrainUserTendencyVO userTendencyVO = new GeneralTickerPatTrainUserTendencyVO();
      // 查询此次成绩前2次考核成绩
      List<GeneralTickerPatTrainUserEntity> patTrainUserTop2 = trainUserDao.findByUseridTop2(trainUser.getCreateTime(),
          userEntity.getId());
      for (int i = 0; i < patTrainUserTop2.size(); i++) {
        GeneralTickerPatTrainUserEntity trainUserEntity = patTrainUserTop2.get(i);
        if (i == 0) {
          userTendencyVO.setLastScore(trainUserEntity.getScore());
        } else {
          userTendencyVO.setLastLastScore(trainUserEntity.getScore());
        }
      }
      userTendencyVO.setUserId(userId);
      userTendencyVO.setUserName(userEntity.getUserName());
      userTendencyVO.setUserImg(userEntity.getUserImg());
      userTendencyVO.setThisScore(trainUser.getScore());
      userTendencyVOS.add(userTendencyVO);
    }
    errorInfoVO.setDotMin(calculateRate(dotTotal, dotMin, dotTotal));
    errorInfoVO.setDotMax(calculateRate(dotTotal, dotMax, dotTotal));
    errorInfoVO.setLineMin(calculateRate(lineTotal, lineMin, lineTotal));
    errorInfoVO.setLineMax(calculateRate(lineTotal, lineMax, lineTotal));
    errorInfoVO.setCodeGapMin(calculateRate(codeTotal, codeGapMin, codeTotal));
    errorInfoVO.setCodeGapMax(calculateRate(codeTotal, codeGapMax, codeTotal));
    errorInfoVO.setWordGapMin(calculateRate(wordTotal, wordGapMin, wordTotal));
    errorInfoVO.setWordGapMax(calculateRate(wordTotal, wordGapMax, wordTotal));
    errorInfoVO.setGroupGapMin(calculateRate(groupGapMin, groupGapMin, groupTotal));
    errorInfoVO.setGroupGapMax(calculateRate(groupGapMin, groupGapMax, groupTotal));

    // 优秀
    GeneralTickerPatTrainScoreInfoVO goodInfo = new GeneralTickerPatTrainScoreInfoVO();
    goodInfo.setRate(calculateRate(good, good, trainUserEntities.size()));
    goodInfo.setPeopleNumber(good);
    reportVO.setGood(goodInfo);
    // 良好
    GeneralTickerPatTrainScoreInfoVO niceInfo = new GeneralTickerPatTrainScoreInfoVO();
    niceInfo.setRate(calculateRate(nice, nice, trainUserEntities.size()));
    niceInfo.setPeopleNumber(nice);
    reportVO.setNice(niceInfo);
    // 差
    GeneralTickerPatTrainScoreInfoVO belowStandardInfo = new GeneralTickerPatTrainScoreInfoVO();
    belowStandardInfo.setRate(calculateRate(belowStandard, belowStandard, trainUserEntities.size()));
    belowStandardInfo.setPeopleNumber(belowStandard);
    reportVO.setBelowStandard(belowStandardInfo);

    // 封装成绩信息
    ret.setSchoolReport(reportVO);
    // 封装点划间隔粗虚百分比
    ret.setErrorInfoVO(errorInfoVO);
    // 封装用户上次训练得分和本次训练得分对比
    ret.setUserTendencyVO(userTendencyVOS);
    return ret;
  }

  /**
   * 计算分数
   *
   * @param entity
   * @param dto
   */
  private void countScore(GeneralTickerPatTrainEntity entity, GeneralTickerPatTrainFinishVO dto) {
    try {
      // 扣分Map，最后将其转成JSON存入到deduct_info 字段中
      Map<String, Integer> deductMap = new HashMap<>();
      // 得到评分规则
      GradingRuleEntity ruleEntity = gradingRuleDao.findByIdOptional(entity.getRuleId())
          .orElseThrow(() -> new IllegalArgumentException("评分规则不存在"));
      // 基础分数
      Integer score = ruleEntity.getScore();
      // 解析
      String content = ruleEntity.getContent();
      PostTelegramTrainRule rule = parseContent(content);
      // 漏拍
      int lack = 0;
      // 错误个数
      int errorNumber = 0;
      // 多组或少组
      int moreOrLackGroup = 0;
      // 多字或少字
      int moreOrLackWord;
      int dotScore;
      int lineScore;
      int codeScore;
      int wordScore;
      int groupScore;
      int alterErrorScore;
      int errorCode;
      int dotTotalTime = 0;
      int lineTotalTime = 0;
      int codeTotalTime = 0;
      int wordTotalTime = 0;
      int groupTotalTime = 0;
      // 统计信息
      PostTelegramTrainStatisticsVO statisticsVO = new PostTelegramTrainStatisticsVO();
      PostTelegramTrainScoreVO scoreVO = new PostTelegramTrainScoreVO();

      // 查询出提交的页码数
      List<Integer> existFloorNumber = userValueDao.countByTrainIdAndUserIdGroupByPageNumber(entity.getId(),
          dto.getUserId());
      for (Integer floorNumber : existFloorNumber) {
        // 查询提交的页内容
        GeneralTickerPatTrainUserValueEntity contentFloorValueEntity = userValueDao
            .findByFloorNumberAndTrainIdAndUserId(floorNumber, entity.getId(), dto.getUserId());
        String messageBody = contentFloorValueEntity.getMessageBody();
        String standard = contentFloorValueEntity.getStandard();
        // 拍发内容
        List<PostTelegramTrainContentAddParam> userContents = JSONUtils.fromJson(messageBody, new TypeToken<>() {
        });
        // 拍发此页的标准值
        List<PostTelegramTrainFinishInfoDto> standards = JSONUtils.fromJson(standard, new TypeToken<>() {
        });

        // 生成的标准内容
        List<GeneralTickerPatTrainPageEntity> userValueEntities = trainPageDao
            .findByFloorNumberAndTrainIdOrderBySort(floorNumber, entity.getId());
        List<String> sources = userValueEntities.stream().map(GeneralTickerPatTrainPageEntity::getMoresKey)
            .map(item -> item.substring(1)
                .replaceAll("[\",\\]]", ""))
            .toList();
        List<String> patKeys = null;
        if (userContents != null) {
          patKeys = userContents.stream().map(PostTelegramTrainContentAddParam::getPatKeys)
              .map(item -> item.substring(1)
                  .replaceAll("[\",\\]]", ""))
              .toList();
        }

        PostTelegramTrainResolverVO comparison = messageComparisonService.comparison(sources, patKeys, scoreVO,
            userContents, standards, rule, statisticsVO);
        contentFloorValueEntity.setResolver(JSONUtils.toJson(comparison));
      }

      statisticsAllAvg(statisticsVO,
          dotTotalTime,
          lineTotalTime,
          codeTotalTime,
          wordTotalTime,
          groupTotalTime);

      // 计算未生成和未拍的数量
      Integer messageNumber = entity.getMessageNumber();
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
          moreOrLackGroup += rule.getQuantoGroup().getL() * 100;
        } else {
          lack += messageNumber % 100;
          moreOrLackGroup += rule.getQuantoGroup().getL() * (messageNumber % 100);
        }
      }

      // 处理完进行扣分
      dotScore = calculateScore(rule.getDot().getMax(), scoreVO.getDotScore(), rule.getDot().getMax());
      score = score - dotScore;
      deductMap.put("dotMinScore", dotScore);
      deductMap.put("dotMinNumber", scoreVO.getDotScore());
      lineScore = calculateScore(rule.getDash().getMax(), scoreVO.getLineScore(), rule.getDot().getMax());
      score = score - lineScore;
      deductMap.put("lineScore", lineScore);
      deductMap.put("lineNumber", scoreVO.getLineScore());
      codeScore = calculateScore(rule.getLittle().getMax(), scoreVO.getCodeScore(), rule.getLittle().getMax());
      score = score - codeScore;
      deductMap.put("codeGapScore", codeScore);
      deductMap.put("codeNumber", scoreVO.getCodeScore());
      wordScore = calculateScore(rule.getMiddle().getMax(), scoreVO.getWordScore(), rule.getMiddle().getMax());
      score = score - wordScore;
      deductMap.put("wordGapScore", wordScore);
      deductMap.put("wordNumber", scoreVO.getWordScore());
      groupScore = calculateScore(rule.getLarge().getMax(), scoreVO.getGroupScore(), rule.getLarge().getMax());
      score = score - groupScore;
      deductMap.put("groupGapScore", groupScore);
      deductMap.put("groupNumber", scoreVO.getGroupScore());
      alterErrorScore = calculateScore(rule.getAlterError().getMax(), scoreVO.getAlterErrorScore(),
          rule.getAlterError().getMax());
      score = score - alterErrorScore;
      deductMap.put("alterErrorScore", alterErrorScore);
      deductMap.put("alterErrorNumber", scoreVO.getAlterErrorScore());

      // 扣分
      errorCode = calculateScore(rule.getErrorCode().getMax(), scoreVO.getErrorNumber() * rule.getErrorCode().getL(),
          rule.getErrorCode().getMax());
      score = score - errorCode;
      deductMap.put("errorWord", errorCode);
      deductMap.put("errorWordNumber", scoreVO.getErrorNumber());

      moreOrLackWord = calculateScore(rule.getQuantoCode().getMax(),
          scoreVO.getMoreOrLackWord() * rule.getQuantoCode().getL(), rule.getQuantoCode().getMax());
      score = score - moreOrLackWord;
      deductMap.put("quantoCode", moreOrLackWord);
      deductMap.put("quantoCodeNumber", scoreVO.getMoreOrLackWord());

      moreOrLackGroup = calculateScore(rule.getQuantoGroup().getMax(),
          (scoreVO.getMoreGroup() + scoreVO.getLackGroup()) * rule.getQuantoGroup().getL(),
          rule.getQuantoGroup().getMax());
      score = score - moreOrLackGroup;
      deductMap.put("quantoGroup", moreOrLackGroup);
      deductMap.put("quantoGroupNumber", scoreVO.getMoreGroup());

      // 多行少行
      int moreOrLackLine = calculateScore(rule.getQuantoRow().getMax(),
          scoreVO.getMoreOrLackLine() * rule.getQuantoRow().getL(), rule.getQuantoRow().getMax());
      score = score - moreOrLackLine;
      deductMap.put("quantoRow", moreOrLackLine);
      deductMap.put("quantoRowNumber", scoreVO.getMoreOrLackLine());

      // 串行
      int bunchGroup = calculateScore(rule.getBunchGroup().getMax(),
          scoreVO.getBunchGroup() * rule.getBunchGroup().getL(), rule.getBunchGroup().getMax());
      score = score - bunchGroup;
      deductMap.put("bunchGroup", bunchGroup);
      deductMap.put("bunchGroupNumber", scoreVO.getBunchGroup());

      // 查询出该场训练对应的用户信息
      GeneralTickerPatTrainUserEntity trainUserEntity = trainUserDao.findByUserIdAndTrainId(dto.getUserId(),
          entity.getId());

      // 设置
      trainUserEntity.setErrorNumber(scoreVO.getErrorNumber());
      trainUserEntity.setLack(lack);
      // 计算正确率,若是没有任何拍发记录则是0
      if (scoreVO.getCorrect() == 0) {
        trainUserEntity.setAccuracy("0.00");
      } else {
        String accuracy = new BigDecimal(scoreVO.getCorrect()).divide(
            new BigDecimal(scoreVO.getPatTotalNum()),
            2,
            RoundingMode.HALF_UP).multiply(new BigDecimal(100)).toString();
        trainUserEntity.setAccuracy(accuracy);
      }

      // 计算平均速率
      List<String> speedLog = Optional.ofNullable(trainUserEntity.getSpeedLog())
          .map(speedLod -> JSONUtils.fromJson(speedLod, new TypeToken<List<String>>() {
          }))
          .orElseGet(ArrayList::new);

      // 判断速率是码每分还是WPM
      String speed = speedLog.stream()
          .map(BigDecimal::new)
          .reduce(BigDecimal.ZERO, BigDecimal::add)
          .divide(new BigDecimal(speedLog.size()), 0, RoundingMode.HALF_DOWN)
          .toString();
      trainUserEntity.setSpeed(speed);

      // 计算wpm
      SpeedDeduct baseWpm = rule.getWpm();
      int wpm = baseWpm.getBase() - new BigDecimal(trainUserEntity.getSpeed()).intValue();
      int wpmScore = (wpm > 0 ? -(wpm * baseWpm.getL()) : wpm * baseWpm.getR());
      deductMap.put("wpmScore", wpmScore);
      score = score + wpmScore;
      trainUserEntity.setScore(new BigDecimal(score));
      trainUserEntity.setStatisticInfo(JSONUtils.toJson(statisticsVO));
      trainUserEntity.setDeductInfo(JSONUtils.toJson(deductMap));
      trainUserEntity.setIsFinish(1);
      trainUserEntity.setFinishTime(LocalDateTime.now());
      trainUserDao.save(trainUserEntity);

      // 保存评分content
      entity.setRuleContent(ruleEntity.getContent());
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException("计算分数异常");
    }
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
