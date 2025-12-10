package com.nip.service.general;

import cn.hutool.core.text.CharSequenceUtil;
import com.google.gson.reflect.TypeToken;
import com.nip.common.PageInfo;
import com.nip.common.constants.CodeConstants;
import com.nip.common.constants.PostTelegramTrainEnum;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.common.utils.ArraySafeGetUtils;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.Page;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.GradingRuleDao;
import com.nip.dao.general.telex.GeneralTelexPatDao;
import com.nip.dao.general.telex.GeneralTelexPatPageDao;
import com.nip.dao.general.telex.GeneralTelexPatUserDao;
import com.nip.dao.general.telex.GeneralTelexPatUserValueDao;
import com.nip.dto.PostTelexPatTrainRuleDto;
import com.nip.dto.TelexPatPageTransferDto;
import com.nip.dto.TelexPatStatisticalDto;
import com.nip.dto.TelexPatValueTransferDto;
import com.nip.dto.general.*;
import com.nip.dto.general.statistic.*;
import com.nip.dto.vo.PostTelegraphKeyPatTrainPageAnalyzeVO;
import com.nip.dto.vo.PostTelegraphKeyPatTrainPageMessageVO;
import com.nip.dto.vo.PostTelegraphTelexPatTrainPageMessageVO;
import com.nip.dto.vo.PostTelegraphTelexPatTrainPageVO;
import com.nip.entity.GradingRuleEntity;
import com.nip.entity.UserEntity;
import com.nip.entity.simulation.telex.GeneralTelexPatEntity;
import com.nip.entity.simulation.telex.GeneralTelexPatPageEntity;
import com.nip.entity.simulation.telex.GeneralTelexPatUserEntity;
import com.nip.entity.simulation.telex.GeneralTelexPatUserValueEntity;
import com.nip.service.CableFloorService;
import com.nip.service.UserService;
import com.nip.ws.WebSocketGeneralTelexPatService;
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
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static com.nip.common.constants.PostTelexPatTrainStatusEnum.NOT_STARTED;
import static com.nip.common.utils.GlobalMessageGeneratedUtil.bePointed;
import static com.nip.common.utils.GlobalMessageGeneratedUtil.generatedNumber;
import static com.nip.common.utils.TelexPatUtils.*;

@Slf4j
@ApplicationScoped
public class GeneralTelexPatService {
  private final GeneralTelexPatDao trainDao;
  private final GeneralTelexPatPageDao trainPageDao;
  private final GeneralTelexPatUserDao trainUserDao;
  private final GeneralTelexPatUserValueDao trainUserValueDao;
  private final GradingRuleDao gradingRuleDao;
  private final UserService userService;
  private final CableFloorService cableFloorService;
  private static final ThreadLocalRandom RANDOM = ThreadLocalRandom.current();

  @Inject
  public GeneralTelexPatService(GeneralTelexPatDao trainDao, GeneralTelexPatPageDao trainPageDao, GeneralTelexPatUserDao trainUserDao,
                                GeneralTelexPatUserValueDao trainUserValueDao, GradingRuleDao gradingRuleDao, UserService userService,
                                CableFloorService cableFloorService) {
    this.trainDao = trainDao;
    this.trainPageDao = trainPageDao;
    this.trainUserDao = trainUserDao;
    this.trainUserValueDao = trainUserValueDao;
    this.gradingRuleDao = gradingRuleDao;
    this.userService = userService;
    this.cableFloorService = cableFloorService;
  }

  @Transactional
  public GeneralTelexPatTrainVO add(GeneralTelexPatAddParamDto param, String token) {
    UserEntity userEntity = userService.getUserByToken(token);
    GradingRuleEntity ruleEntity = Optional.ofNullable(gradingRuleDao.findById(param.getRuleId()))
        .orElseThrow(() -> new IllegalArgumentException("未查询到评分规则"));
    GeneralTelexPatEntity entity = PojoUtils.convertOne(param, GeneralTelexPatEntity.class, (t, r) -> {
      //设置默认值
      r.setStatus(NOT_STARTED.getStatus());
      r.setValidTime(0L);
      r.setRuleContent(JSONUtils.toJson(ruleEntity));
      r.setCreateUser(userEntity.getId());
      r.setRuleContent(ruleEntity.getContent());
    });
    GeneralTelexPatEntity save = trainDao.save(entity);

    //保存参训人员信息
    List<GeneralTelexPatUserEntity> trainUserEntityList = new ArrayList<>();
    for (String id : param.getUserId()) {
      GeneralTelexPatUserEntity trainUser = new GeneralTelexPatUserEntity();
      trainUser.setAccuracy(BigDecimal.ZERO);
      trainUser.setUserId(id);
      trainUser.setTrainId(save.getId());
      trainUser.setErrorNumber(0);
      trainUser.setRole(0);
      trainUser.setIsFinish(0);
      trainUser.setScore(BigDecimal.ZERO);
      trainUserEntityList.add(trainUser);
      WebSocketService.sendInfo(id, new ResponseModel(CodeConstants.NOTIFICATION_NEW_TRAIN.getCode(),
          Map.of(
              "type", "telex",
              "id", save.getId(),
              "title", save.getTitle()
          )));
    }
    GeneralTelexPatUserEntity groupUser = new GeneralTelexPatUserEntity();
    groupUser.setTrainId(save.getId());
    groupUser.setUserId(userEntity.getId());
    groupUser.setRole(1);
    trainUserEntityList.add(groupUser);
    trainUserDao.save(trainUserEntityList);
    Integer groupNumber = entity.getTotalNumber();
    int generateNumber = groupNumber < 200 ? groupNumber : 200;
    if (save.getIsCable() == 0) {
      if (0 == entity.getType()) {
        if (0 == entity.getPatType()) {
          List<String> bePointed = bePointed(generateNumber);
          generateContentAuto(bePointed, bePointed.size(), 1, save.getId());
        } else if (1 == entity.getPatType()) {
          List<String> strings = generatedNumber(groupNumber, true, true);
          if (strings.size() > 200) {
            strings = strings.subList(0, 200);
          }
          generateContentAuto(strings, strings.size(), 1, save.getId());
        } else if (2 == entity.getPatType()) {
          generateContent(entity.getType(), generateNumber, 1, save.getId());
        }
      } else {
        //生成报文
        generateContent(save.getType(), generateNumber, 1, save.getId());
      }

    } else {
      List<List<List<String>>> cableFloor = cableFloorService.findCableFloor(param.getCableId(), null, param.getStartPage());
      int totalPage = groupNumber / 100;
      cableFloor = cableFloor.subList(0, totalPage);
      List<GeneralTelexPatPageEntity> list = new ArrayList<>();
      int floorNumber = 1;
      for (List<List<String>> floor : cableFloor) {
        int sortIndex = 0;
        for (List<String> moresKey : floor) {
          if (moresKey == null) {
            continue;
          }
          GeneralTelexPatPageEntity pageEntity = new GeneralTelexPatPageEntity();
          pageEntity.setTrainId(save.getId());
          pageEntity.setKey(String.join("", moresKey));
          pageEntity.setPageNumber(floorNumber);
          pageEntity.setSort(sortIndex);
          list.add(pageEntity);
          sortIndex++;
        }
        floorNumber++;
      }
      trainPageDao.save(list);
    }
    return PojoUtils.convertOne(save, GeneralTelexPatTrainVO.class);
  }

  public PageInfo<GeneralTelexPatTrainVO> findAll(Page page, String token) {
    try {
      UserEntity currentUser = userService.getUserByToken(token);
      List<GeneralTelexPatUserEntity> userEntityList = trainUserDao.findByUserId(currentUser.getId());
      PageInfo<GeneralTelexPatEntity> all = trainDao.findPage((root, criteriaQuery, criteriaBuilder) -> {
        CriteriaBuilder.In<String> id = criteriaBuilder.in(root.get("id").as(String.class));
        userEntityList.stream().map(GeneralTelexPatUserEntity::getTrainId)
            .forEach(id::value);
        criteriaQuery.where(id);
        criteriaQuery.orderBy(criteriaBuilder.desc(root.get("createTime").as(LocalDateTime.class)));
        return criteriaQuery;
      }, page.getPage() - 1, page.getRows());
      List<GeneralTelexPatTrainVO> convert = PojoUtils.convert(
          all.getData(),
          GeneralTelexPatTrainVO.class,
          (e, v) -> {
            v.setUserInfoList(JSONUtils.fromJson(JSONUtils.toJson(trainUserDao.findByTrainIdToMapSimple(e.getId())), new TypeToken<>() {
            }));
            v.setRuleContent(null);
          }
      );
      PageInfo<GeneralTelexPatTrainVO> pageInfo = new PageInfo<>();
      pageInfo.setCurrentPage(all.getCurrentPage());
      pageInfo.setPageSize(all.getPageSize());
      pageInfo.setTotalPage(all.getTotalPage());
      pageInfo.setTotalNumber(all.getTotalNumber());
      pageInfo.setData(convert);
      return pageInfo;
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  public GeneralTelexPatTrainVO detail(GeneralTelexPatPageParamDto param) {
    try {
      //查询该训练信息
      GeneralTelexPatEntity keyPatEntity = trainDao.findById(param.getTrainId());
      GeneralTelexPatTrainVO patTrainVO = PojoUtils.convertOne(keyPatEntity, GeneralTelexPatTrainVO.class);
      if (keyPatEntity.getIsCable() == 1) {
        patTrainVO.setTotalNumber((int) trainPageDao.count("trainId", param.getTrainId()));
        patTrainVO.setPageCount(trainPageDao.findMaxPageNumber(param.getTrainId()));
      }
      //查询该训练的所有参与用户信息
      List<GeneralTelexPatUserInfoVO> userInfoList = JSONUtils.fromJson(JSONUtils.toJson(trainUserDao.findByTrainIdToMap(param.getTrainId())), new TypeToken<>() {
      });
      patTrainVO.setUserInfoList(userInfoList);

      //查询每个用户在线状态
      List<GeneralPatTrainUserDto> userDto = new ArrayList<>(findUserInfo(param.getTrainId()));
      Map<String, List<GeneralPatTrainUserDto>> collect = userDto.stream().collect(Collectors.groupingBy(GeneralPatTrainUserDto::getId));
      for (GeneralTelexPatUserInfoVO item : userInfoList) {
        List<GeneralPatTrainUserDto> keyPatTrainUserDto = collect.get(item.getUserId());
        if (keyPatTrainUserDto != null && !keyPatTrainUserDto.isEmpty()) {
          Integer status = keyPatTrainUserDto.stream().findFirst().map(GeneralPatTrainUserDto::getStatus).orElse(0);
          item.setUserStatus(status);
        } else {
          //没有默认为0（离线状态）
          item.setUserStatus(0);
        }
        //统计信息
        item.setPageAnalyzeVOS(generatePageAnalyze(param.getTrainId(), item.getUserId()));
      }
      return patTrainVO;
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  /**
   * 生成统计信息
   */
  private List<PostTelegraphKeyPatTrainPageAnalyzeVO> generatePageAnalyze(String trainId, String userId) {
    //统计每页拍发时长和个数
    List<GeneralTelexPatUserValueEntity> pageValueEntities = trainUserValueDao.findByTrainIdAndUserIdOrderByPageNumberAscSortAsc(trainId, userId);
    Map<Integer, List<GeneralTelexPatUserValueEntity>> collect = pageValueEntities.stream()
        .collect(Collectors.groupingBy(GeneralTelexPatUserValueEntity::getPageNumber));
    List<PostTelegraphKeyPatTrainPageAnalyzeVO> analyzeVOS = new ArrayList<>();
    collect.forEach((key, value) -> {
      PostTelegraphKeyPatTrainPageAnalyzeVO analyzeVO = new PostTelegraphKeyPatTrainPageAnalyzeVO();
      int patNumber = 0;
      for (GeneralTelexPatUserValueEntity valueEntity : value) {
        if (valueEntity.getSort() == -1) {
          Integer convert = groupNumber(valueEntity.getValue());
          patNumber += convert;
        }
      }
      analyzeVO.setPatNumber(patNumber);
      analyzeVOS.add(analyzeVO);
    });
    return analyzeVOS;
  }

  public GeneralTelexPatUserInfoVO patDetail(GeneralTelexPatPageParamDto param) {
    try {
      //查询该训练信息
      GeneralTelexPatEntity keyPatEntity = trainDao.findById(param.getTrainId());
      GeneralTelexPatUserEntity patUserEntity = trainUserDao.findByUserIdAndTrainId(param.getUserId(), param.getTrainId());

      List<Integer> pageNumber = trainPageDao.countPageNumber(param.getTrainId());
      //查询前2页数据content
      List<GeneralTelexPatPageEntity> twoPage = trainPageDao.findTwoPage(param.getTrainId());
      List<GeneralTelexPatUserValueEntity> toPageValue = trainUserValueDao.findTwoPage(param.getTrainId(), param.getUserId());
      //统计每页拍发时长和个数
      List<GeneralTelexPatUserValueEntity> pageValueEntities = trainUserValueDao.findByTrainIdAndUserIdOrderByPageNumberAscSortAsc(param.getTrainId(), param.getUserId());
      Map<Integer, List<GeneralTelexPatUserValueEntity>> collect = pageValueEntities.stream()
          .collect(Collectors.groupingBy(GeneralTelexPatUserValueEntity::getPageNumber));
      List<PostTelegraphKeyPatTrainPageAnalyzeVO> analyzeVOS = new ArrayList<>();
      collect.forEach((key, value) -> {
        PostTelegraphKeyPatTrainPageAnalyzeVO analyzeVO = new PostTelegraphKeyPatTrainPageAnalyzeVO();
        int totalTime = 0;
        int patNumber = 0;
        for (GeneralTelexPatUserValueEntity valueEntity : value) {
          if (valueEntity.getSort() == -1) {
            Integer convert = groupNumber(valueEntity.getValue());
            patNumber += convert;
          }

        }
        analyzeVO.setPatNumber(patNumber);
        analyzeVO.setTotalTime(totalTime);
        analyzeVOS.add(analyzeVO);
      });

      return PojoUtils.convertOne(patUserEntity, GeneralTelexPatUserInfoVO.class, (t, v) -> {
        v.setExistPage(pageNumber);
        if (patUserEntity.getIsFinish().compareTo(1) == 0) {
          v.setContent(PojoUtils.convert(toPageValue, PostTelegraphKeyPatTrainPageMessageVO.class));
        } else {
          v.setContent(PojoUtils.convert(twoPage, PostTelegraphKeyPatTrainPageMessageVO.class));
        }
        v.setDuration(null == t.getValidTime() ? 0L : Long.valueOf(t.getValidTime()));
        v.setPageAnalyzeVOS(analyzeVOS);
        v.setRuleContent(keyPatEntity.getRuleContent());
        v.setTotalNumber(keyPatEntity.getTotalNumber());
        v.setIsCable(keyPatEntity.getIsCable());
        if (keyPatEntity.getIsCable() == 1) {
          v.setTotalNumber((int) trainPageDao.count("trainId", param.getTrainId()));
          v.setPageCount(trainPageDao.findMaxPageNumber(param.getTrainId()));
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }

  }

  public GeneralTelexPatPageDto findMessageBody(GeneralTelexPatPageParamDto param) {
    return null;
  }

  @Transactional
  public void updateStatus(String trainId, Integer status) {
    GeneralTelexPatEntity keyPatTrain = trainDao.findById(trainId);
    keyPatTrain.setStatus(status);
    if (Objects.equals(status, PostTelegramTrainEnum.UNDERWAY.getStatus())) {
      //教员点击开始训练，设置开始时间
      keyPatTrain.setStartTime(LocalDateTime.now());
    } else if (Objects.equals(status, PostTelegramTrainEnum.FINISH.getStatus())) {
      keyPatTrain.setEndTime(LocalDateTime.now());
      long time = keyPatTrain.getEndTime().toEpochSecond(ZoneOffset.of("+8")) - keyPatTrain.getStartTime().toEpochSecond(ZoneOffset.of("+8"));
      keyPatTrain.setValidTime(time);
    }
    trainDao.saveAndFlush(keyPatTrain);
  }

  @Transactional
  public void saveContentValue(GeneralTelexPatPageSubmitDto dto, String token) {
    String userId = userService.getUserByToken(token).getId();
    GeneralTelexPatUserEntity entity = trainUserDao.findByUserIdAndTrainId(userId, dto.getTrainId());
    List<String> speedLog = Optional.ofNullable(entity.getSpeedLog()).map(speed -> JSONUtils.fromJson(speed, new TypeToken<List<String>>() {
    })).orElseGet(ArrayList::new);
    if (!StringUtils.isEmpty(dto.getSpeed())) {
      if (speedLog.isEmpty()) {
        speedLog.add(dto.getSpeed());
      } else {
        if (speedLog.size() >= dto.getPageNumber()) {
          if (!StringUtils.isEmpty(speedLog.get(dto.getPageNumber() - 1))) {
            speedLog.set(dto.getPageNumber() - 1, dto.getSpeed());
          }
        } else {
          speedLog.add(dto.getSpeed());
        }
      }
    }
    entity.setSpeedLog(JSONUtils.toJson(speedLog));
    log.info("Telex page speed:{},speedLog:{}", dto.getSpeed(), speedLog);
    // 记录每页耗时
    List<Integer> validTimeLog = Optional.ofNullable(entity.getValidTimeLog()).map(validTime -> JSONUtils.fromJson(validTime, new TypeToken<List<Integer>>() {
    })).orElseGet(ArrayList::new);
    if (dto.getValidTime() != null) {
      if (validTimeLog.isEmpty()) {
        validTimeLog.add(dto.getValidTime());
      } else {
        if (validTimeLog.size() >= dto.getPageNumber()) {
          validTimeLog.set(dto.getPageNumber() - 1, dto.getValidTime());
        } else {
          validTimeLog.add(dto.getValidTime());
        }
      }
    }
    entity.setValidTimeLog(JSONUtils.toJson(validTimeLog));
    log.info("Telex page time：time{},timeLog{}", dto.getValidTime(), validTimeLog);
    trainUserDao.save(entity);
    trainUserValueDao.delete("trainId=?1 and pageNumber=?2 and userId=?3",
        dto.getTrainId(), dto.getPageNumber(), userId);
    trainUserValueDao.save(PojoUtils.convertOne(dto, GeneralTelexPatUserValueEntity.class, (t, v) -> {
      v.setUserId(userId);
      v.setSort(-1);
      v.setValue(t.getPatValue());
    }));
  }

  @Transactional
  public List<GeneralTelexPatUserInfoVO> finish(GeneralTelexPatFinishDto dto) {
    try {
      GeneralTelexPatEntity entity = trainDao.findById(dto.getTrainId());
      //分数计算，计算该训练下所有人员的分数
      List<GeneralTelexPatUserInfoVO> userInfoList = new ArrayList<>();
      GeneralTelexPatUserEntity userTrainEntity = countScore(entity, dto.getUserId());
      userTrainEntity.setIsFinish(1);
      userTrainEntity.setFinishTime(LocalDateTime.now());
      trainUserDao.save(userTrainEntity);
      userInfoList.add(PojoUtils.convertOne(userTrainEntity, GeneralTelexPatUserInfoVO.class));
      trainUserDao.findRoleAdminByUserId(dto.getTrainId()).forEach(admin -> {
        WebSocketService.sendInfo(admin.getUserId(), new ResponseModel(CodeConstants.NOTIFICATION_TRAIN_RESULT.getCode(),
            Map.of(
                "type", "telex",
                "userId", userTrainEntity.getUserId(),
                "trainId", entity.getId()
            )));
      });
      return userInfoList;
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  public PostTelegraphTelexPatTrainPageVO getPage(String trainId, Integer pageNumber, String userId) {
    try {
      PostTelegraphTelexPatTrainPageVO ret = new PostTelegraphTelexPatTrainPageVO();
      GeneralTelexPatEntity entity = trainDao.findById(trainId);
      List<GeneralTelexPatPageEntity> messageVO = null;
      int generateNumber = 100;
      //页码是否正确
      if (entity.getIsCable() == 0) {
        int totalPage = entity.getTotalNumber() / 100;
        int totalNumber = entity.getTotalNumber();
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

      //用户拍发内容
      List<GeneralTelexPatUserValueEntity> userPage = trainUserValueDao.findByTrainIdAndPageNumberAndUserIdOrderBySort(trainId, pageNumber, userId);
      //生成的内容
      List<GeneralTelexPatPageEntity> pageDaoAll = trainPageDao.findByTrainIdAndPageNumberOrderBySort(trainId, pageNumber);
      if (!pageDaoAll.isEmpty()) {
        messageVO = pageDaoAll;
      } else {
        messageVO = generateContent(entity.getType(), generateNumber, pageNumber, entity.getId());
      }
      //用户未拍发本页内容，则获取生成的内容
      if (userPage.isEmpty()) {
        ret.setMessageVO(PojoUtils.convert(messageVO, PostTelegraphTelexPatTrainPageMessageVO.class));
      } else {
        ret.setMessageVO(PojoUtils.convert(userPage, PostTelegraphTelexPatTrainPageMessageVO.class));
      }

      return ret;
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  public List<GeneralTelexPatTrainUserValueVO> getPatValue(GeneralTelexPatPageParamDto param) {
    List<GeneralTelexPatUserValueEntity> patUserValueEntities = trainUserValueDao.findByPageNumberAndTrainIdAndUserId(param.getPageNumber(), param.getTrainId(), param.getUserId());
    return PojoUtils.convert(patUserValueEntities, GeneralTelexPatTrainUserValueVO.class);
  }

  public GeneralTelexPatTrainStatisticVO statistic(String trainId) {
    //role-0,学员
    List<GeneralTelexPatUserEntity> trainUserEntities = trainUserDao.findByTrainIdAndRole(trainId, 0);
    return statisticsScoreAndDotLineGapRate(trainUserEntities);
  }

  private GeneralTelexPatTrainStatisticVO statisticsScoreAndDotLineGapRate(List<GeneralTelexPatUserEntity> trainUserEntities) {
    //结果集
    GeneralTelexPatTrainStatisticVO ret = new GeneralTelexPatTrainStatisticVO();
    //成绩分布
    GeneralPatTrainSchoolReportVO reportVO = new GeneralPatTrainSchoolReportVO();
    //本次成绩和上次成绩对比
    List<GeneralPatTrainUserTendencyVO> userTendencyVOS = new ArrayList<>();

    int good = 0;
    int nice = 0;
    int belowStandard = 0;
    GeneralTelexPatTrainErrorCollect errorCollect = new GeneralTelexPatTrainErrorCollect();
    for (GeneralTelexPatUserEntity trainUser : trainUserEntities) {
      BigDecimal score = trainUser.getScore();
      if (score.compareTo(new BigDecimal(90)) > -1) {
        good++;
      } else if (score.compareTo(new BigDecimal(70)) > -1) {
        nice++;
      } else {
        belowStandard++;
      }
      if (CharSequenceUtil.isNotBlank(trainUser.getDeductInfo())) {
        GeneralTelexPatTrainErrorCollect userError = JSONUtils.fromJson(trainUser.getDeductInfo(), GeneralTelexPatTrainErrorCollect.class);
        if (userError != null) {
          errorCollect.setErrorCodeNumber(errorCollect.getErrorCodeNumber() + userError.getErrorCodeNumber());
          errorCollect.setErrorPageNumber(errorCollect.getErrorPageNumber() + userError.getErrorPageNumber());
          errorCollect.setCorrectMistakesNumber(errorCollect.getCorrectMistakesNumber() + userError.getCorrectMistakesNumber());
          errorCollect.setLessPageNumber(errorCollect.getLessPageNumber() + userError.getLessPageNumber());
          errorCollect.setLessReturnLineNumber(errorCollect.getLessReturnLineNumber() + userError.getLessReturnLineNumber());
          errorCollect.setMuchLessCodeNumber(errorCollect.getMuchLessCodeNumber() + userError.getMuchLessCodeNumber());
          errorCollect.setMuchLessGroupsNumber(errorCollect.getMuchLessGroupsNumber() + userError.getMuchLessGroupsNumber());
          errorCollect.setMuchLessLineNumber(errorCollect.getMuchLessLineNumber() + userError.getMuchLessLineNumber());
          errorCollect.setNonStandartNumber(errorCollect.getNonStandartNumber() + userError.getNonStandartNumber());
        }
      }
      //统计参训人拍发态势 本次本次训练和与上次训练分数对比
      String userId = trainUser.getUserId();
      LocalDateTime createTime = trainUser.getCreateTime();
      UserEntity userEntity = userService.getUserByIdNew(userId);
      GeneralPatTrainUserTendencyVO userTendencyVO = new GeneralPatTrainUserTendencyVO();
      userTendencyVO.setUserId(userId);
      userTendencyVO.setUserName(userEntity.getUserName());
      userTendencyVO.setUserImg(userEntity.getUserImg());
      userTendencyVO.setThisScore(trainUser.getScore());
      userTendencyVO.setStatus(trainUser.getIsFinish());
      List<BigDecimal> fistTwoScore = trainUserDao.findByFistTwoScore(userId, createTime);
      userTendencyVO.setLastScore(ArraySafeGetUtils.get(fistTwoScore, 0, BigDecimal.ZERO));
      userTendencyVO.setLastLastScore(ArraySafeGetUtils.get(fistTwoScore, 1, BigDecimal.ZERO));
      userTendencyVOS.add(userTendencyVO);
    }
    BigDecimal scale = new BigDecimal(100);
    BigDecimal totalNumber = new BigDecimal(trainUserEntities.size());
    //优秀
    GeneralPatTrainScoreInfoVO goodInfo = new GeneralPatTrainScoreInfoVO();
    BigDecimal goodRate = good == 0 ? BigDecimal.ZERO : new BigDecimal(good).divide(totalNumber, 10, RoundingMode.HALF_UP).multiply(scale).setScale(0, RoundingMode.HALF_UP);
    goodInfo.setRate(goodRate);
    goodInfo.setPeopleNumber(good);
    reportVO.setGood(goodInfo);
    //良好
    GeneralPatTrainScoreInfoVO niceInfo = new GeneralPatTrainScoreInfoVO();
    BigDecimal niceRate = nice == 0 ? BigDecimal.ZERO : new BigDecimal(nice).divide(totalNumber, 10, RoundingMode.HALF_UP).multiply(scale).setScale(0, RoundingMode.HALF_UP);
    niceInfo.setPeopleNumber(nice);
    niceInfo.setRate(niceRate);
    reportVO.setNice(niceInfo);
    //差
    GeneralPatTrainScoreInfoVO belowStandardInfo = new GeneralPatTrainScoreInfoVO();
    BigDecimal belowStandardRate = belowStandard == 0 ? BigDecimal.ZERO : new BigDecimal(belowStandard).divide(totalNumber, 10, RoundingMode.HALF_UP).multiply(scale).setScale(0, RoundingMode.HALF_UP);
    belowStandardInfo.setPeopleNumber(belowStandard);
    belowStandardInfo.setRate(belowStandardRate);
    reportVO.setBelowStandard(belowStandardInfo);

    //封装成绩信息
    ret.setSchoolReport(reportVO);
    //封装用户上次训练得分和本次训练得分对比
    ret.setUserTendencyVO(userTendencyVOS);
    //设置错情统计
    ret.setErrorCollect(errorCollect);
    return ret;
  }

  public Response<List<GeneralPatTrainUserDto>> getOnline(String trainId) {
    GeneralPatTrainRoomUserDto trainRoomUser = WebSocketGeneralTelexPatService.ROOM.get(trainId);
    if (trainRoomUser == null) {
      return ResponseResult.success(new ArrayList<>());
    }
    List<GeneralPatTrainUserModelDto> joinUser = new ArrayList<>(trainRoomUser.getJoinUser());
    if (trainRoomUser.getGroupUser() != null) {
      joinUser.add(trainRoomUser.getGroupUser());
    }
    return ResponseResult.success(PojoUtils.convert(joinUser, GeneralPatTrainUserDto.class));
  }

  public void startTrain(String trainId, String token) {
    String userId = userService.getUserByToken(token).getId();
    GeneralTelexPatUserEntity patUserEntity = trainUserDao.findByUserIdAndTrainId(userId, trainId);
    if (null != patUserEntity) {
      patUserEntity.setIsFinish(2);
      trainUserDao.save(patUserEntity);
    }
  }

  /**
   * 报文生成
   *
   * @param type           类型
   * @param generateNumber 生成数量
   * @param pageNumber     页码
   * @param trainId        训练id
   * @return 生成内容
   */
  private List<GeneralTelexPatPageEntity> generateContent(int type, int generateNumber,
                                                          int pageNumber, String trainId) {
    List<GeneralTelexPatPageEntity> pageEntities = new ArrayList<>();
    for (int i = 0; i < generateNumber; i++) {
      StringBuilder key = new StringBuilder();
      switch (type) {
        case 0:
          for (int j = 0; j < 4; j++) {
            int number = RANDOM.nextInt(10);
            key.append(number);
          }
          break;
        case 1:
          for (int j = 0; j < 4; j++) {
            int number = RANDOM.nextInt(26);
            char c = (char) (number + 65);
            key.append(c);
          }
          break;
        case 2:
          for (int j = 0; j < 4; j++) {
            int number = RANDOM.nextInt(36);
            if (number < 10) {
              key.append(number);
            } else {
              char c = (char) (number + 55);
              key.append(c);
            }
          }
          break;
        default:
          throw new IllegalArgumentException("未知数据类型");
      }
      if (i % 100 == 0 && i != 0) {
        pageNumber += 1;
      }
      GeneralTelexPatPageEntity pageEntity = new GeneralTelexPatPageEntity();
      pageEntity.setTrainId(trainId);
      pageEntity.setKey(key.toString());
      pageEntity.setPageNumber(pageNumber);
      pageEntity.setSort(i % 100);
      pageEntities.add(pageEntity);
    }
    return trainPageDao.save(pageEntities);
  }

  private List<GeneralTelexPatPageEntity> generateContentAuto(List<String> content, int generateNumber, int pageNumber, String trainId) {
    List<GeneralTelexPatPageEntity> pageEntities = new ArrayList<>();
    for (int i = 0; i < generateNumber; i++) {
      if (i % 100 == 0 && i != 0) {
        pageNumber += 1;
      }
      GeneralTelexPatPageEntity pageEntity = new GeneralTelexPatPageEntity();
      pageEntity.setTrainId(trainId);
      pageEntity.setKey(content.get(i));
      pageEntity.setPageNumber(pageNumber);
      pageEntity.setSort(i % 100);
      pageEntities.add(pageEntity);
    }

    return trainPageDao.save(pageEntities);
  }

  /**
   * 计算分数
   *
   * @param entity
   * @param
   */
  private GeneralTelexPatUserEntity countScore(GeneralTelexPatEntity entity, String userId) {
    GeneralTelexPatUserEntity kehPatUserEntity = trainUserDao.findByUserIdAndTrainId(userId, entity.getId());
    TelexPatStatisticalDto ks = new TelexPatStatisticalDto();

    List<Integer> pageNumbers = trainPageDao.countPageNumber(entity.getId());
    List<TelexPatValueTransferDto> pageValueResult = new ArrayList<>();
    pageNumbers.forEach(pageNumber -> {
      List<TelexPatPageTransferDto> userPages = PojoUtils.convert(
          trainPageDao.findByTrainIdAndPageNumberOrderBySort(entity.getId(), pageNumber),
          TelexPatPageTransferDto.class);
      List<GeneralTelexPatUserValueEntity> userValue = trainUserValueDao.findByTrainIdAndPageNumberAndUserIdAndSortOrderBySort(entity.getId(), pageNumber, userId);
      if (!userValue.isEmpty()) {
        pageValueResult.addAll(PojoUtils.convert(userValue, TelexPatValueTransferDto.class));
        handle(userId, pageNumber, pageValueResult, userPages, userValue.getFirst().getValue(), ks, pageNumber == pageNumbers.size() - 1);
      }
    });
    List<GeneralTelexPatUserValueEntity> convert = PojoUtils.convert(pageValueResult, GeneralTelexPatUserValueEntity.class);
    trainUserValueDao.deleteByTrainIdAndUserId(entity.getId(), userId);
    trainUserValueDao.saveAndFlush(convert);
    PostTelexPatTrainRuleDto rule = JSONUtils.fromJson(entity.getRuleContent(), PostTelexPatTrainRuleDto.class);
    if (rule == null) {
      throw new IllegalArgumentException("评分规则未设定");
    }
    //创建扣分信息Map
    String minus = "-";
    Map<String, Object> deductMap = new HashMap<>();
    BigDecimal score = new BigDecimal(100);

    BigDecimal errorCodeScore = new BigDecimal(ks.getErrorCodeNumber()).multiply(rule.getOther().getErrorCode());
    deductMap.put("errorCodeNumber", ks.getErrorCodeNumber());
    deductMap.put("errorCodeScore", minus + errorCodeScore);

    BigDecimal muchLessLineScore = new BigDecimal(ks.getMuchLessLineNumber()).multiply(rule.getOther().getMuchLessLine());
    deductMap.put("muchLessLineNumber", ks.getMuchLessLineNumber());
    deductMap.put("muchLessLineScore", minus + muchLessLineScore);

    BigDecimal muchLessGroupsScore = new BigDecimal(ks.getMuchLessGroupsNumber()).multiply(rule.getOther().getMuchLessGroups());
    deductMap.put("muchLessGroupsNumber", ks.getMuchLessGroupsNumber());
    deductMap.put("muchLessGroupsScore", minus + muchLessGroupsScore);

    BigDecimal muchLessCodeScore = new BigDecimal(ks.getMuchLessCodeNumber()).multiply(rule.getOther().getMuchLessCode());
    deductMap.put("muchLessCodeNumber", ks.getMuchLessCodeNumber());
    deductMap.put("muchLessCodeScore", minus + muchLessCodeScore);

    BigDecimal lessReturnLineScore = new BigDecimal(ks.getLessReturnLineNumber()).multiply(rule.getOther().getLessReturnLine());
    deductMap.put("lessReturnLineNumber", ks.getLessReturnLineNumber());
    deductMap.put("lessReturnLineScore", minus + lessReturnLineScore);

    BigDecimal lessPageScore = new BigDecimal(ks.getLessPageNumber()).multiply(rule.getOther().getLessPage());
    deductMap.put("lessPageNumber", ks.getLessPageNumber());
    deductMap.put("lessPageScore", minus + lessPageScore);

    BigDecimal errorPageScore = new BigDecimal(ks.getErrorPageNumber()).multiply(rule.getOther().getErrorPage());
    deductMap.put("errorPageNumber", ks.getErrorPageNumber());
    deductMap.put("errorPageScore", minus + errorPageScore);

    BigDecimal nonStandartScore = new BigDecimal(ks.getNonStandartNumber()).multiply(rule.getOther().getNonStandart());
    deductMap.put("nonStandartNumber", ks.getNonStandartNumber());
    deductMap.put("nonStandartScore", minus + nonStandartScore);

    BigDecimal correctMistakesScore = new BigDecimal(ks.getCorrectMistakesNumber()).multiply(rule.getOther().getAlterError());
    deductMap.put("correctMistakesNumber", ks.getCorrectMistakesNumber());
    deductMap.put("correctMistakesScore", minus + correctMistakesScore);

    //计算正确率 （拍发总个数- 错误个数 = 正确个数） / 总个数
    BigDecimal accuracy = new BigDecimal("0");
    int errorTotal = ks.getPatGroup() - ks.getErrorCodeNumber() - ks.getMuchLessCodeNumber();
    if (errorTotal != 0) {
      //计算正确率 （拍发总个数 - 错误个数- 多字- 少字)） /拍发总个数
      accuracy = new BigDecimal(errorTotal).divide(
          new BigDecimal(ks.getPatGroup()), 2, RoundingMode.HALF_UP).multiply(new BigDecimal(100));
    }

    score = score.subtract(errorCodeScore)
        .subtract(muchLessLineScore)
        .subtract(muchLessGroupsScore)
        .subtract(muchLessCodeScore)
        .subtract(lessReturnLineScore)
        .subtract(lessPageScore)
        .subtract(errorPageScore)
        .subtract(nonStandartScore)
        .subtract(correctMistakesScore);
    BigDecimal avgSpeed = calculateAverage(JSONUtils.fromJson(kehPatUserEntity.getSpeedLog(), new TypeToken<>() {
    }), 0, RoundingMode.HALF_UP).orElse(BigDecimal.ZERO);
    if (avgSpeed.compareTo(new BigDecimal(rule.getWpm().getBase())) > 0) {
      int diff = avgSpeed.intValue() - rule.getWpm().getBase();
      BigDecimal speedScore = rule.getWpm().getR().multiply(new BigDecimal(diff));
      score = score.add(speedScore);
      deductMap.put("speedScore", "+" + speedScore);
    } else if (avgSpeed.compareTo(new BigDecimal(rule.getWpm().getBase())) < 0) {
      int diff = rule.getWpm().getBase() - avgSpeed.intValue();
      BigDecimal speedScore = rule.getWpm().getL().multiply(new BigDecimal(diff));
      score = score.subtract(speedScore);
      deductMap.put("speedScore", minus + speedScore);
    }
    List<Integer> validTimeLog = JSONUtils.fromJson(kehPatUserEntity.getValidTimeLog(), new TypeToken<>() {
    });
    int validTime = 0;
    if (validTimeLog != null) {
      for (Integer i : validTimeLog) {
        validTime += i;
      }
    }
    kehPatUserEntity.setValidTime(validTime);
    kehPatUserEntity.setSpeed(avgSpeed);
    kehPatUserEntity.setScore(score);
    kehPatUserEntity.setAccuracy(accuracy);
    kehPatUserEntity.setDeductInfo(JSONUtils.toJson(deductMap));
    return kehPatUserEntity;
  }


  /**
   * 查询在线学员信息,因为本项目socket与业务模块在一起，故无需跨项目调用
   *
   * @return
   */
  public List<GeneralPatTrainUserDto> findUserInfo(String trainId) {
    return getOnline(trainId).getData();
  }

  /**
   * 获取训练用户详细信息并封装为数据传输对象
   *
   * @param uid     用户唯一标识
   * @param trainId 训练项目唯一标识
   * @return 包含用户基础信息和训练角色的通用训练用户数据传输对象
   * @throws IllegalArgumentException 当用户基础信息或训练关联数据不存在时抛出异常
   */
  public GeneralPatTrainUserDto getTrainUserInfo(String uid, String trainId) {
    /*
      从持久层查询用户与训练项目的关联实体
      该实体包含用户在特定训练项目中的角色等业务属性
     */
    GeneralTelexPatUserEntity userTrainEntity = trainUserDao.findByUserIdAndTrainId(uid, trainId);

    /*
      从用户服务获取最新的用户基础信息实体
      包含用户ID、用户名、头像等核心用户属性
     */
    UserEntity userEntity = userService.getUserByIdNew(uid);

    /*
      数据有效性校验
      确保用户基础信息和训练关联信息同时存在
      避免后续空指针异常和数据一致性问题
     */
    if (userEntity == null || userTrainEntity == null) {
      throw new IllegalArgumentException("训练数据异常");
    }

    // 创建数据传输对象并填充属性
    return new GeneralPatTrainUserDto(userEntity.getId(), userEntity.getUserName(), userEntity.getUserImg(), userTrainEntity.getRole());
  }

}
