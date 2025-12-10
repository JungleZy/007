package com.nip.service.general;

import cn.hutool.core.text.CharSequenceUtil;
import com.google.gson.reflect.TypeToken;
import com.nip.common.PageInfo;
import com.nip.common.constants.CodeConstants;
import com.nip.common.constants.PostTelegramTrainEnum;
import com.nip.common.constants.PostTelegramTrainTypeEnum;
import com.nip.common.response.Response;
import com.nip.common.utils.*;
import com.nip.controller.general.GeneralKeyPatTrainController;
import com.nip.dao.GradingRuleDao;
import com.nip.dao.UserDao;
import com.nip.dao.general.key.*;
import com.nip.dto.KeyPatPageTransferDto;
import com.nip.dto.KeyPatStatisticalDto;
import com.nip.dto.KeyPatValueTransferDto;
import com.nip.dto.PostKeyPatTrainRuleDto;
import com.nip.dto.general.*;
import com.nip.dto.general.statistic.*;
import com.nip.dto.vo.PostTelegraphKeyPatResolverDetailVO;
import com.nip.dto.vo.PostTelegraphKeyPatTrainPageAnalyzeVO;
import com.nip.dto.vo.PostTelegraphKeyPatTrainPageMessageVO;
import com.nip.dto.vo.PostTelegraphKeyPatTrainPageVO;
import com.nip.entity.GradingRuleEntity;
import com.nip.entity.UserEntity;
import com.nip.entity.simulation.key.*;
import com.nip.service.CableFloorService;
import com.nip.service.UserService;
import com.nip.ws.WebSocketService;
import com.nip.ws.model.ResponseModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static com.nip.common.utils.KeyPatUtils.handle;

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
    UserEntity currentUser = userService.getUserByToken(token);
    GeneralKeyPatEntity trainEntity = PojoUtils.convertOne(param, GeneralKeyPatEntity.class);
    trainEntity.setCreateUser(currentUser.getId());
    //默认状态为未开始
    trainEntity.setStatus(0);
    //有效时间设置为0
    trainEntity.setValidTime(0L);
    //获取ruleContent
    GradingRuleEntity ruleOp = gradingRuleDao.findById(trainEntity.getRuleId());
    trainEntity.setRuleContent(JSONUtils.toJson(ruleOp));
    GeneralKeyPatEntity save = trainDao.save(trainEntity);

    //保存参训人员信息
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
              "title", save.getTitle()
          )));
    }
    GeneralKeyPatUserEntity groupUser = new GeneralKeyPatUserEntity();
    groupUser.setTrainId(save.getId());
    groupUser.setUserId(currentUser.getId());
    groupUser.setRole(1);
    trainUserEntityList.add(groupUser);
    trainUserDao.save(trainUserEntityList);

    //生成报文begin
    if (param.getIsCable() == 0) {
      Integer messageNumber = param.getTotalNumber();
      int generate = 0;
      if (messageNumber > 200) {
        generate = 200;
      } else {
        generate = messageNumber;
      }
      Integer type = param.getMessageType();

      List<GeneralKeyPatPageEntity> ret = new ArrayList<>();
      List<String> messageBody;
      //生成对应的报文
      if (type.compareTo(PostTelegramTrainTypeEnum.NUMBER_MESSAGE.getType()) == 0) {
        messageBody = GlobalMessageGeneratedUtil.generatedNumber(generate, param.getIsAverage().equals(1), param.getIsRandom().equals(1));
      } else if (type.compareTo(PostTelegramTrainTypeEnum.STRING_MESSAGE.getType()) == 0) {
        messageBody = GlobalMessageGeneratedUtil.generatedWord(generate, param.getIsAverage().equals(1), param.getIsRandom().equals(1));
      } else {
        messageBody = GlobalMessageGeneratedUtil.generatedMingle(generate, param.getIsAverage().equals(1), param.getIsRandom().equals(1));
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
      List<List<List<String>>> cableFloor = cableFloorService.findCableFloor(param.getCableId(), null, param.getStartPage());
      int totalPage = param.getTotalNumber() / 100;
      cableFloor = cableFloor.subList(0, totalPage);
      for (int i = 0; i < cableFloor.size(); i++) {
        for (int j = 0; j < cableFloor.get(i).size(); j++) {
          GeneralKeyPatPageEntity pageEntity = new GeneralKeyPatPageEntity();
          pageEntity.setTrainId(save.getId());
          pageEntity.setTime("[]");
          pageEntity.setKey(JSONUtils.toJson(cableFloor.get(i).get(j)));
          pageEntity.setPageNumber(i + 1);
          pageEntity.setSort(j);
          pageEntity.setValue("[]");
          trainPageDao.save(pageEntity);
        }
      }
    }

    //生成报文end

    return PojoUtils.convertOne(save, GeneralKeyPatTrainVO.class);
  }

  private List<GeneralKeyPatPageEntity> generateAndSavePatKey(Integer generateNumber, Integer pageNumber, int trainId, Integer type, Integer isAvg, Integer isRandom) {
    //生成报文begin
    List<GeneralKeyPatPageEntity> ret = new ArrayList<>();
    List<String> messageBody;
    //生成对应的报文
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
      pageEntity.setPageNumber(pageNumber);
      pageEntity.setSort(i % 100);
      ret.add(pageEntity);
    }
    //生成报文end
    trainPageDao.save(ret);
    return ret;
  }

  /**
   * 查询指定tarinId的报底
   */
  public GeneralKeyPatPageDto findMessageBody(GeneralKeyPatPageParamDto param) {
    //查询出该训练对应页码的报底
    final List<GeneralKeyPatPageEntity> trainPageList = trainPageDao.findByPageNumberAndTrainIdOrderBySort(param.getPageNumber(), param.getTrainId());
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
          v.setUserInfoList(JSONUtils.fromJson(JSONUtils.toJson(trainUserDao.findByTrainIdToMap(e.getId())), new TypeToken<>() {
          }));
          v.setRuleContent(null);
        }
    );
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
   * param  trainId
   */
  public GeneralKeyPatTrainVO detail(GeneralKeyPatPageParamDto param) {
    try {
      //查询该训练信息
      GeneralKeyPatEntity keyPatEntity = trainDao.findById(param.getTrainId());
      GeneralKeyPatTrainVO patTrainVO = PojoUtils.convertOne(keyPatEntity, GeneralKeyPatTrainVO.class);
      if (keyPatEntity.getIsCable() == 1) {
        patTrainVO.setTotalNumber((int) trainPageDao.count("trainId", param.getTrainId()));
        patTrainVO.setPageCount(trainPageDao.findMaxPageNumber(param.getTrainId()));
      }
      //查询该训练的所有参与用户信息
      List<GeneralKeyPatUserInfoVO> userInfoList = JSONUtils.fromJson(JSONUtils.toJson(trainUserDao.findByTrainIdToMap(param.getTrainId())), new TypeToken<>() {
      });
      patTrainVO.setUserInfoList(userInfoList);

      //查询每个用户在线状态
      List<GeneralPatTrainUserDto> userDto = new ArrayList<>(findUserInfo(param.getTrainId()));
      Map<String, List<GeneralPatTrainUserDto>> collect = userDto.stream().collect(Collectors.groupingBy(GeneralPatTrainUserDto::getId));
      for (GeneralKeyPatUserInfoVO item : userInfoList) {
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
  private List<PostTelegraphKeyPatTrainPageAnalyzeVO> generatePageAnalyze(Integer trainId, String userId) {
    //统计每页拍发时长和个数
    List<GeneralKeyPatUserValueEntity> pageValueEntities = userValueDao.findByTrainIdAndUserIdOrderByPageNumberAscSortAsc(trainId, userId);
    Map<Integer, List<GeneralKeyPatUserValueEntity>> collect = pageValueEntities.stream()
        .collect(Collectors.groupingBy(GeneralKeyPatUserValueEntity::getPageNumber));
    List<PostTelegraphKeyPatTrainPageAnalyzeVO> analyzeVOS = new ArrayList<>();
    collect.forEach((key, value) -> {
      PostTelegraphKeyPatTrainPageAnalyzeVO analyzeVO = new PostTelegraphKeyPatTrainPageAnalyzeVO();
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
    //先删除旧的拍发记录
    String userId = userService.getUserByToken(token).getId();
    userValueDao.deleteByTrainIdAndPageNumberAndUserId(dto.getTrainId(), dto.getPageNumber(), userId);
    //把新的拍发记录保存
    List<GeneralKeyPatPageDetailDto> pageValue = dto.getPageValue();
    List<GeneralKeyPatUserValueEntity> valueEntities = PojoUtils.convert(pageValue, GeneralKeyPatUserValueEntity.class, (s, d) -> {
      d.setId(null);//防止误新增
      d.setUserId(userId);
      d.setTrainId(dto.getTrainId());
      d.setPageNumber(dto.getPageNumber());
      d.setTime(s.getTime());
    });
    //更新完成时间
    GeneralKeyPatUserEntity patUserEntity = trainUserDao.findByUserIdAndTrainId(userId, dto.getTrainId());
    patUserEntity.setFinishTime(LocalDateTime.now());
    //拍发时间
    long time = patUserEntity.getFinishTime().toEpochSecond(ZoneOffset.of("+8")) - patUserEntity.getCreateTime().toEpochSecond(ZoneOffset.of("+8"));
    patUserEntity.setDuration(time + "");
    trainUserDao.save(patUserEntity);
    userValueDao.save(valueEntities);
  }

  @Transactional
  public List<GeneralKeyPatUserInfoVO> finish(GeneralKeyPatFinishDto dto) {
    try {
      GeneralKeyPatEntity entity = trainDao.findById(dto.getTrainId());
        /*//校验状态是否是进行中
        if (!Objects.equals(entity.getStatus(), PostTelegramTrainEnum.UNDERWAY.getStatus())) {
            throw new RuntimeException(entity.getTitle() + "训练的状态不是进行中");
        }*/

      //分数计算，计算该训练下所有人员的分数
      List<GeneralKeyPatUserInfoVO> userInfoList = new ArrayList<GeneralKeyPatUserInfoVO>();
      //List<String> userIds = trainUserDao.findTrainUserIdsByTrainId(entity.getId());
      GeneralKeyPatUserEntity userTrainEntity = trainUserDao.findByUserIdAndTrainId(dto.getUserId(), dto.getTrainId());
      userTrainEntity.setIsFinish(1);
      trainUserDao.save(userTrainEntity);
      GeneralKeyPatUserEntity generalKeyPatUserEntity = countScore(entity, dto.getUserId());
      userInfoList.add(PojoUtils.convertOne(generalKeyPatUserEntity, GeneralKeyPatUserInfoVO.class));
      trainUserDao.findRoleAdminByUserId(dto.getTrainId()).forEach(admin -> {
        WebSocketService.sendInfo(admin.getUserId(), new ResponseModel(CodeConstants.NOTIFICATION_TRAIN_RESULT.getCode(),
            Map.of(
                "type", "key",
                "userId", userTrainEntity.getUserId(),
                "trainId",entity.getId()
            )));
      });
      return userInfoList;
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  /**
   * 开放给socket的接口
   *
   * @param
   */
  @Transactional
  public void updateStatus(Integer trainId, Integer status) {
    GeneralKeyPatEntity keyPatTrain = trainDao.findById(trainId);
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

  public PostTelegraphKeyPatTrainPageVO getPage(Integer trainId, Integer pageNumber, String userId) {
    try {
      PostTelegraphKeyPatTrainPageVO ret = new PostTelegraphKeyPatTrainPageVO();
      GeneralKeyPatEntity entity = trainDao.findById(trainId);
      List<GeneralKeyPatPageEntity> messageVO = null;
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
      List<GeneralKeyPatUserValueEntity> userPage = userValueDao.findByTrainIdAndPageNumberAndUserIdOrderBySort(trainId, pageNumber, userId);
      //生成的内容
      List<GeneralKeyPatPageEntity> pageDaoAll = trainPageDao.findByTrainIdAndPageNumberOrderBySort(trainId, pageNumber);
      if (!pageDaoAll.isEmpty()) {
        messageVO = pageDaoAll;
      } else {
        messageVO = generateAndSavePatKey(generateNumber, pageNumber, entity.getId(), entity.getMessageType(), entity.getIsAverage(), entity.getIsRandom());
      }
      //用户未拍发本页内容，则获取生成的内容
      if (userPage.isEmpty()) {
        ret.setMessageVO(PojoUtils.convert(messageVO, PostTelegraphKeyPatTrainPageMessageVO.class));
      } else {
        ret.setMessageVO(PojoUtils.convert(userPage, PostTelegraphKeyPatTrainPageMessageVO.class));
      }

      //获取到本页的多组多行信息
      GeneralKeyPatTrainMoreEntity trainMoreEntity = moreEntityDao.findByTrainIdAndPageNumberAndUserId(trainId, pageNumber, userId);
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
    } catch (Exception e) {
      e.printStackTrace();
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
    List<GeneralKeyPatUserValueEntity> patUserValueEntities = userValueDao.findByPageNumberAndTrainIdAndUserId(param.getPageNumber(), param.getTrainId(), param.getUserId());
    return PojoUtils.convert(patUserValueEntities, GeneralKeyPatTrainUserValueVO.class);
  }

  /**
   * 获取统计信息
   *
   * @param trainId
   * @return
   */
  public GeneralKeyPatTrainStatisticVO statistic(Integer trainId) {
    //role-0,学员
    List<GeneralKeyPatUserEntity> trainUserEntities = trainUserDao.findByTrainIdAndRole(trainId, 0);
    return statisticsScoreAndDotLineGapRate(trainUserEntities);
  }

  private GeneralKeyPatTrainStatisticVO statisticsScoreAndDotLineGapRate(List<GeneralKeyPatUserEntity> trainUserEntities) {
    //结果集
    GeneralKeyPatTrainStatisticVO ret = new GeneralKeyPatTrainStatisticVO();
    //成绩分布
    GeneralPatTrainSchoolReportVO reportVO = new GeneralPatTrainSchoolReportVO();
    //本次成绩和上次成绩对比
    List<GeneralPatTrainUserTendencyVO> userTendencyVOS = new ArrayList<>();
    int good = 0;
    int nice = 0;
    int belowStandard = 0;
    GeneralKeyPatTrainErrorCollect errorCollect = new GeneralKeyPatTrainErrorCollect();
    for (GeneralKeyPatUserEntity trainUser : trainUserEntities) {
      BigDecimal score = trainUser.getScore();
      if (score.compareTo(new BigDecimal(90)) > -1) {
        good++;
      } else if (score.compareTo(new BigDecimal(70)) > -1) {
        nice++;
      } else {
        belowStandard++;
      }
      if (CharSequenceUtil.isNotBlank(trainUser.getDeductInfo())) {
        GeneralKeyPatTrainErrorCollect userError = com.nip.common.utils.JSONUtils.fromJson(trainUser.getDeductInfo(), GeneralKeyPatTrainErrorCollect.class);
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

      //统计参训人拍发态势 本次本次训练和与上次训练分数对比
      String userId = trainUser.getUserId();
      LocalDateTime createTime = trainUser.getCreateTime();
      UserEntity userEntity = userService.getUserByIdNew(userId);
      GeneralPatTrainUserTendencyVO userTendencyVO = new GeneralPatTrainUserTendencyVO();
      userTendencyVO.setUserId(userId);
      userTendencyVO.setUserName(userEntity.getUserName());
      userTendencyVO.setUserImg(userEntity.getUserImg());
      userTendencyVO.setThisScore(trainUser.getScore());
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

  /**
   * 计算分数
   *
   * @param entity
   * @param
   */
  private GeneralKeyPatUserEntity countScore(GeneralKeyPatEntity entity, String userId) {
    GeneralKeyPatUserEntity kehPatUserEntity = trainUserDao.findByUserIdAndTrainId(userId, entity.getId());
    //存放扣分规则 key扣分名称，value扣分值
    Map<String, Object> deductInfo = new HashMap<>();
    //查询扣分规则
    GradingRuleEntity ruleEntity = gradingRuleDao.findById(entity.getRuleId());
    String ruleContent = ruleEntity.getContent();
    PostKeyPatTrainRuleDto rule = JSONUtils.fromJson(ruleContent, PostKeyPatTrainRuleDto.class);
    //积分规则
    KeyPatStatisticalDto ks = new KeyPatStatisticalDto();
    //得到已存在的页
    List<Integer> pageNumbers = userValueDao.findPageNumberByTrainIdAndUserId(entity.getId(), userId);
    // 创建每页的处理结果，该结果会在每页处理完毕后替换旧的page_value数据
    List<KeyPatValueTransferDto> pageValueResult = new ArrayList<>();
    pageNumbers.parallelStream().forEach(pageNumber -> {
      List<KeyPatPageTransferDto> userPages = PojoUtils.convert(
          trainPageDao.findByTrainIdAndPageNumberOrderBySort(entity.getId(), pageNumber),
          KeyPatPageTransferDto.class);
      List<KeyPatValueTransferDto> userPageValues = PojoUtils.convert(
          userValueDao.findByTrainIdAndPageNumberAndUserIdOrderBySort(entity.getId(), pageNumber, userId),
          KeyPatValueTransferDto.class);
      List<KeyPatValueTransferDto> pageResult = new ArrayList<>();
      handle(userId, pageResult, userPages, userPageValues, ks);
      pageValueResult.addAll(pageResult);
    });
    List<GeneralKeyPatUserValueResolverEntity> pv = PojoUtils.convert(pageValueResult, GeneralKeyPatUserValueResolverEntity.class);
    resolverDao.deleteByTrainIdAndUserId(entity.getId(), userId);
    resolverDao.saveAndFlush(pv);
    List<GeneralKeyPatUserValueEntity> pvv = PojoUtils.convert(pageValueResult, GeneralKeyPatUserValueEntity.class);
    userValueDao.deleteByTrainIdAndUserId(entity.getId(), userId);
    userValueDao.saveAndFlush(pvv);

    //计算少页
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
            ks.setLackLine(ks.getLackLine() + 10);
          } else {
            //余数
            int clout = tp % 100;
            ks.setLackLine(ks.getLackLine() + clout / 10);
            if (clout % 10 > 0) {
              ks.setMoreGroup(ks.getMoreGroup() + clout % 10);
            }
          }
        } else {
          ks.setLackLine(ks.getLackLine() + 10);
        }
      }
      if (remaining > 0) {
        ks.setLackGroup(ks.getLackGroup() + (totalPageNumber - 1 - pageNumbers.size()) * 100 + remaining);
        ks.setLackLine(ks.getLackLine() +
            (totalPageNumber - 1 - pageNumbers.size()) *
                10 + (remaining / 10) - (ks.getPatGroup() / 10 + 1));
      } else {
        ks.setLackGroup(ks.getLackGroup() + (totalPageNumber - pageNumbers.size()) * 100);
        ks.setLackLine(ks.getLackLine() + (totalPageNumber - pageNumbers.size()) * 10 - (ks.getPatGroup() / 10 + 1));
      }
    }


    //计算速率 拍发个数/训练时长*60
    BigDecimal speed = new BigDecimal("0");
    if (ks.getPat() != 0) {
      speed = new BigDecimal(ks.getPat()).divide(new BigDecimal(4), 10, RoundingMode.HALF_UP)
          .divide(new BigDecimal(ks.getPatTime()).divide(new BigDecimal(1000), 10, RoundingMode.HALF_UP), 10, RoundingMode.HALF_UP)
          .multiply(new BigDecimal(60)).setScale(0, RoundingMode.HALF_UP);
    }

    kehPatUserEntity.setSpeed(String.valueOf(speed));

    //错误个数
    kehPatUserEntity.setErrorNumber(ks.getError());

    BigDecimal accuracy = new BigDecimal("0");
    int errorTotal = ks.getPatGroup() - ks.getError() - ks.getBunchGroup() - ks.getLack() - ks.getMore();
    if (errorTotal != 0) {
      //计算正确率 （拍发总个数 - 错误个数- 多字- 少字)） /拍发总个数
      accuracy = new BigDecimal(errorTotal).divide(
          new BigDecimal(ks.getPatGroup()), 2, RoundingMode.HALF_UP).multiply(new BigDecimal(100));
    }

    kehPatUserEntity.setAccuracy(accuracy.toString());


    //得到要扣的分
    String minus = "-";
    BigDecimal score = new BigDecimal(ruleEntity.getScore());
    BigDecimal errorScore = rule.getOther().getErrorCode().multiply(new BigDecimal(ks.getError()));
    deductInfo.put("errorNumber", ks.getError());
    deductInfo.put("errorScore", minus + errorScore);

    BigDecimal lackScore = rule.getOther().getMuchLessCode().multiply(new BigDecimal(ks.getLack()));
    deductInfo.put("lackNumber", ks.getLack());
    deductInfo.put("lackScore", minus + lackScore);

    BigDecimal moreScore = rule.getOther().getMuchLessCode().multiply(new BigDecimal(ks.getMore()));
    deductInfo.put("moreNumber", ks.getMore());
    deductInfo.put("moreScore", minus + moreScore);

    BigDecimal lackLineScore = rule.getOther().getMuchLessLine().multiply(new BigDecimal(ks.getLackLine()));
    deductInfo.put("lackLineNumber", ks.getLackLine());
    deductInfo.put("lackLineScore", minus + lackLineScore);

    BigDecimal moreLineScore = rule.getOther().getMuchLessLine().multiply(new BigDecimal(ks.getMoreLine()));
    deductInfo.put("moreLineNumber", ks.getMoreLine());
    deductInfo.put("moreLineScore", minus + moreLineScore);

    BigDecimal lackGroupScore = rule.getOther().getMuchLessGroups().multiply(new BigDecimal(ks.getLackGroup()));
    deductInfo.put("lackGroupNumber", ks.getLackGroup());
    deductInfo.put("lackGroupScore", minus + lackGroupScore);

    BigDecimal moreGroupScore = rule.getOther().getMuchLessGroups().multiply(new BigDecimal(ks.getMoreGroup()));
    deductInfo.put("moreGroupNumber", ks.getMoreGroup());
    deductInfo.put("moreGroupScore", minus + moreGroupScore);

    //改错
    BigDecimal alterScore = rule.getOther().getAlterError().multiply(new BigDecimal(ks.getAlterError()));
    deductInfo.put("alterErrorNumber", ks.getAlterError());
    deductInfo.put("alterErrorScore", minus + alterScore);

    //串组
    BigDecimal bunchGroupScore = rule.getOther().getBunchGroup().multiply(new BigDecimal(ks.getBunchGroup()));
    deductInfo.put("bunchGroupNumber", ks.getBunchGroup());
    deductInfo.put("bunchGroupScore", minus + bunchGroupScore);
    //少间隔
    BigDecimal lackGapScore = rule.getOther().getLessGap().multiply(new BigDecimal(ks.getLackGap()));
    deductInfo.put("lackGapNumber", ks.getLackGap());
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

    //判断速率是+分开始扣分
    deductInfo.put("speedNumber", speed.toString());
    if (speed.compareTo(new BigDecimal(rule.getWpm().getBase())) > 0) {
      int diff = speed.intValue() - rule.getWpm().getBase();
      BigDecimal speedScore = rule.getWpm().getR().multiply(new BigDecimal(diff));
      score = score.add(speedScore);
      deductInfo.put("speedScore", "+" + speedScore);
    } else if (speed.compareTo(new BigDecimal(rule.getWpm().getBase())) < 0) {
      int diff = rule.getWpm().getBase() - speed.intValue();
      BigDecimal speedScore = rule.getWpm().getL().multiply(new BigDecimal(diff));
      score = score.subtract(speedScore);
      deductInfo.put("speedScore", minus + speedScore);
    }

    kehPatUserEntity.setScore(score);
    //保存扣分详情
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
    Response<List<GeneralPatTrainUserDto>> oneLien = gGeneralKeyPatTrainController.getOneLien(trainId);
    return oneLien.getData();
  }

  public List<BigDecimal> score() {
    return trainUserDao.score();
  }

  public GeneralKeyPatUserInfoVO patDetail(GeneralKeyPatPageParamDto param) {
    //查询该训练信息
    GeneralKeyPatEntity keyPatEntity = trainDao.findById(param.getTrainId());
    GeneralKeyPatUserEntity patUserEntity = trainUserDao.findByUserIdAndTrainId(param.getUserId(), param.getTrainId());

    List<Integer> pageNumber = trainPageDao.countPageNumber(param.getTrainId());
    //查询前2页数据content
    List<GeneralKeyPatPageEntity> twoPage = trainPageDao.findTwoPage(param.getTrainId());
    List<GeneralKeyPatUserValueEntity> toPageValue = userValueDao.findTwoPage(param.getTrainId(), param.getUserId());
    List<GeneralKeyPatUserValueResolverEntity> resolverList = resolverDao.findTwoPage(param.getTrainId(), param.getUserId());
    //统计每页拍发时长和个数
    List<GeneralKeyPatUserValueEntity> pageValueEntities = userValueDao.findByTrainIdAndUserIdOrderByPageNumberAscSortAsc(param.getTrainId(), param.getUserId());
    Map<Integer, List<GeneralKeyPatUserValueEntity>> collect = pageValueEntities.stream()
        .collect(Collectors.groupingBy(GeneralKeyPatUserValueEntity::getPageNumber));
    List<PostTelegraphKeyPatTrainPageAnalyzeVO> analyzeVOS = new ArrayList<>();
    collect.forEach((key, value) -> {
      PostTelegraphKeyPatTrainPageAnalyzeVO analyzeVO = new PostTelegraphKeyPatTrainPageAnalyzeVO();
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

    return PojoUtils.convertOne(patUserEntity, GeneralKeyPatUserInfoVO.class, (t, v) -> {
      v.setExistPage(pageNumber);
      if (patUserEntity.getIsFinish().compareTo(1) == 0) {
        v.setContent(PojoUtils.convert(toPageValue, PostTelegraphKeyPatTrainPageMessageVO.class));
      } else {
        v.setContent(PojoUtils.convert(twoPage, PostTelegraphKeyPatTrainPageMessageVO.class));
      }
      v.setDuration(keyPatEntity.getValidTime());
      v.setPageAnalyzeVOS(analyzeVOS);
      v.setRuleContent(keyPatEntity.getRuleContent());
      v.setTotalNumber(keyPatEntity.getTotalNumber());
      v.setIsCable(keyPatEntity.getIsCable());
      if (keyPatEntity.getIsCable() == 1) {
        v.setTotalNumber((int) trainPageDao.count("trainId", param.getTrainId()));
        v.setPageCount(trainPageDao.findMaxPageNumber(param.getTrainId()));
      }
    });
  }

  public GeneralKeyPatTrainDto getTrainInfo(Integer trainId) {
    GeneralKeyPatTrainDto dto = new GeneralKeyPatTrainDto();
    GeneralKeyPatEntity keyPatEntity = trainDao.findById(trainId);
    List<GeneralKeyPatPageEntity> pageEntities = trainPageDao.findByTrainId(trainId);
    List<GeneralKeyPatUserEntity> patUserEntities = trainUserDao.findByTrainId(trainId);
    List<GeneralKeyPatUserValueEntity> userValueEntities = userValueDao.findByTrainId(trainId);
    List<GeneralKeyPatTrainMoreEntity> patTrainMoreEntities = moreEntityDao.findByTrainId(trainId);
    Set<String> userIds = new HashSet<>();
    userIds.add(keyPatEntity.getCreateUser());
    userIds.addAll(patUserEntities.stream().map(GeneralKeyPatUserEntity::getUserId).collect(Collectors.toSet()));
    userIds.addAll(userValueEntities.stream().map(GeneralKeyPatUserValueEntity::getUserId).collect(Collectors.toSet()));
    userIds.addAll(patTrainMoreEntities.stream().map(GeneralKeyPatTrainMoreEntity::getUserId).collect(Collectors.toSet()));
    List<UserEntity> userEntities = userDao.queryByIdIn(userIds);
    dto.setTrainDto(PojoUtils.convertOne(keyPatEntity, GeneralKeyPatSyncDto.class));
    dto.setPageDto(PojoUtils.convert(pageEntities, GeneralKeyPatPageSyncDto.class));
    dto.setUserDto(PojoUtils.convert(patUserEntities, GeneralKeyPatUserSyncDto.class));
    dto.setUserValueDto(PojoUtils.convert(userValueEntities, GeneralKeyPatUserValueSyncDto.class));
    dto.setMoreDto(PojoUtils.convert(patTrainMoreEntities, GeneralKeyPatTrainMoreSyncDto.class));
    dto.setUsers(PojoUtils.convert(userEntities, UserSyncDto.class));
    return dto;
  }

  @Transactional
  public void importTrainInfo(GeneralKeyPatTrainDto dto) {
    GeneralKeyPatSyncDto trainDto = dto.getTrainDto();
    List<GeneralKeyPatPageSyncDto> pageDto = dto.getPageDto();
    List<GeneralKeyPatUserSyncDto> userDto = dto.getUserDto();
    List<GeneralKeyPatUserValueSyncDto> userValueDto = dto.getUserValueDto();
    List<GeneralKeyPatTrainMoreSyncDto> trainMoreDto = dto.getMoreDto();
    List<UserSyncDto> users = dto.getUsers();
    //更新用户id 和训练id
    Map<String, String> userIdMap = userService.replaceUserIdAndSaveIfNotExist(users);
    trainDto.setCreateUser(userIdMap.get(trainDto.getCreateUser()));
    trainDto.setId(null);//自增主键重新生成防重复
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

    //入库
    trainPageDao.save(PojoUtils.convert(pageDto, GeneralKeyPatPageEntity.class));
    trainUserDao.save(PojoUtils.convert(userDto, GeneralKeyPatUserEntity.class));
    userValueDao.save(PojoUtils.convert(userValueDto, GeneralKeyPatUserValueEntity.class));
    moreEntityDao.save(PojoUtils.convert(trainMoreDto, GeneralKeyPatTrainMoreEntity.class));
  }

  public List<GeneralKeyPatTrainDto> getTrainInfoBatch(String token) {
    UserEntity userEntityByToken = userDao.findUserEntityByToken(token);
    List<GeneralKeyPatTrainDto> ls = new ArrayList<>();
    List<Integer> ids = trainUserDao.queryRelatedTrainId(userEntityByToken.getId());
    ids.forEach(item -> ls.add(getTrainInfo(item)));
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

  public void startTrain(Integer trainId, String token) {
    String userId = userService.getUserByToken(token).getId();
    GeneralKeyPatUserEntity patUserEntity = trainUserDao.findByUserIdAndTrainId(userId, trainId);
    if (null != patUserEntity) {
      patUserEntity.setIsFinish(2);
      trainUserDao.save(patUserEntity);
    }
  }
}
