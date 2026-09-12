package com.nip.service;

import cn.hutool.core.util.ObjectUtil;
import com.google.gson.reflect.TypeToken;
import com.nip.common.exception.TerminalStateException;
import com.nip.common.utils.GlobalMessageGeneratedUtil;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.PojoUtils;
import com.nip.common.utils.ScoreMath;
import com.nip.common.utils.CaptureTimeline;
import com.nip.common.utils.ScoringRuleValidation;
import com.nip.dto.CaptureInterval;
import com.nip.dto.score.TrainingRateUnit;
import jakarta.persistence.LockModeType;
import java.time.Duration;
import com.nip.dao.GradingRuleDao;
import com.nip.dao.PostTelegramTrainContentValueDao;
import com.nip.dao.PostTelegramTrainDao;
import com.nip.dao.PostTelegramTrainFloorContentDao;
import com.nip.dto.PostTelegramTrainContentValueDto;
import com.nip.dto.PostTelegramTrainFinishDto;
import com.nip.dto.PostTelegramTrainFinishInfoDto;
import com.nip.dto.score.PostTelegramTrainRule;
import com.nip.dto.score.SpeedDeduct;
import com.nip.dto.vo.PostTelegramTrainAddContentValueVO;
import com.nip.dto.vo.PostTelegramTrainContentVO;
import com.nip.dto.vo.PostTelegramTrainResolverVO;
import com.nip.dto.vo.PostTelegramTrainScoreVO;
import com.nip.dto.vo.PostTelegramTrainStatisticsVO;
import com.nip.dto.vo.PostTelegramTrainVO;
import com.nip.dto.vo.PostTelegraphKeyPatTrainPageAnalyzeVO;
import com.nip.dto.vo.param.PostTelegramTrainAddParam;
import com.nip.dto.vo.param.PostTelegramTrainContentAddParam;
import com.nip.dto.vo.param.PostTelegramTrainFloorContentQueryParam;
import com.nip.dto.vo.param.PostTelegramTrainQueryParam;
import com.nip.entity.GradingRuleEntity;
import com.nip.entity.PostTelegramTrainContentFloorValueEntity;
import com.nip.entity.PostTelegramTrainEntity;
import com.nip.entity.PostTelegramTrainFloorContentEntity;
import com.nip.entity.UserEntity;
import io.quarkus.panache.common.Sort;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import static com.nip.common.constants.PostTelegramTrainEnum.*;
import static com.nip.common.constants.PostTelegramTrainTypeEnum.NUMBER_MESSAGE;
import static com.nip.common.constants.PostTelegramTrainTypeEnum.STRING_MESSAGE;
import static com.nip.common.utils.TickerPatUtils.handleMessageBody;
import static com.nip.common.utils.ToolUtil.calculateScore;
import static com.nip.common.utils.ToolUtil.calculateTS;
import static com.nip.service.general.GeneralKeyPatService.REGEX;

/**
 * @Author: wushilin
 * @Data: 2022-05-05 11:39
 * @Description:
 */

@Slf4j
@ApplicationScoped
public class PostTelegramTrainService {
  /**
   * 混合报
   */
  private final Map<String, String> mixture = new HashMap<>();
  private final Map<String, String> sortNumber = new HashMap<>();

  /** 属主判定的唯一口径（个人域 = 仅创建者）。 */
  @Inject TrainWriteAccess trainWriteAccess;

  /**
   * 初始划 morse码和数字和字母的映射
   */
  @PostConstruct
  public void initMap() {
    mixture.put("01", "A");
    mixture.put("1000", "B");
    mixture.put("1010", "C");
    mixture.put("100", "D");
    mixture.put("0", "E");
    mixture.put("0010", "F");
    mixture.put("110", "G");
    mixture.put("0000", "H");
    mixture.put("00", "I");
    mixture.put("0111", "J");
    mixture.put("101", "K");
    mixture.put("0100", "L");
    mixture.put("11", "M");
    mixture.put("10", "N");
    mixture.put("111", "O");
    mixture.put("0110", "P");
    mixture.put("1101", "Q");
    mixture.put("010", "R");
    mixture.put("000", "S");
    mixture.put("1", "T");
    mixture.put("001", "U");
    mixture.put("0001", "V");
    mixture.put("011", "W");
    mixture.put("1001", "X");
    mixture.put("1011", "Y");
    mixture.put("1100", "Z");
    mixture.put("11111", "0");
    mixture.put("01111", "1");
    mixture.put("00111", "2");
    mixture.put("00011", "3");
    mixture.put("00001", "4");
    mixture.put("00000", "5");
    mixture.put("10000", "6");
    mixture.put("11000", "7");
    mixture.put("11100", "8");
    mixture.put("11110", "9");

    // 数字短码
    sortNumber.put("1", "0");
    sortNumber.put("01", "1");
    sortNumber.put("001", "2");
    sortNumber.put("00011", "3");
    sortNumber.put("00001", "4");
    sortNumber.put("00000", "5");
    sortNumber.put("10000", "6");
    sortNumber.put("11000", "7");
    sortNumber.put("100", "8");
    sortNumber.put("10", "9");
  }

  private static final String EMPTY_JSON_ARRAY = "[]";

  private final PostTelegramTrainDao postTelegramTrainDao;
  private final UserService userService;
  private final PostTelegramTrainFloorContentDao floorContentDao;
  private final PostTelegramTrainContentValueDao contentValueDao;
  private final GradingRuleDao gradingRuleDao;
  private final CableFloorService cableFloorService;
  private final MessageComparisonService messageComparisonService;

  @Inject
  public PostTelegramTrainService(PostTelegramTrainDao postTelegramTrainDao,
      UserService userService,
      PostTelegramTrainFloorContentDao floorContentDao,
      PostTelegramTrainContentValueDao contentValueDao,
      GradingRuleDao gradingRuleDao,
      CableFloorService cableFloorService,
      MessageComparisonService messageComparisonService) {
    this.postTelegramTrainDao = postTelegramTrainDao;
    this.userService = userService;
    this.floorContentDao = floorContentDao;
    this.contentValueDao = contentValueDao;
    this.gradingRuleDao = gradingRuleDao;
    this.cableFloorService = cableFloorService;
    this.messageComparisonService = messageComparisonService;
  }

  @Transactional
  public PostTelegramTrainVO save(PostTelegramTrainAddParam param, String token) {
    // Phase 7.4：isCable/messageNumber/type 均为可空包装类型，裸拆箱会 NPE 成 500
    if (param.getIsCable() == null) {
      throw new IllegalArgumentException("是否使用固定报底不能为空");
    }
    if (param.getMessageNumber() == null) {
      throw new IllegalArgumentException("报文数量不能为空");
    }
    if (Objects.equals(param.getIsCable(), 0) && param.getType() == null) {
      throw new IllegalArgumentException("报文类型不能为空");
    }
    // 从token中获取用户
    UserEntity userEntity = userService.getUserByToken(token);
    GradingRuleEntity gradingRule = gradingRuleDao.findByIdOptional(param.getRuleId())
        .orElseThrow(() -> new IllegalArgumentException("评分规则不存在"));
    ScoringRuleValidation.handkey(gradingRule.getContent());
    if (gradingRule.getScore() == null || gradingRule.getScore() < 0) throw new IllegalArgumentException("评分规则满分必须为非负整数");
    PostTelegramTrainEntity entity = PojoUtils.convertOne(param, PostTelegramTrainEntity.class, (t, r) -> {
      r.setProtocolVersion(1);
      r.setAttempt(0);
      r.setFullScore(gradingRule.getScore());
      r.setActiveMillis(0L);
      // 初始速度是0
      r.setSpeed("0");
      // 默认状态为未开始
      r.setStatus(NOT_STARTED.getStatus());
      // 有效时间设置为0
      r.setValidTime(0L);
      r.setCreateUser(userEntity.getId());
      // 设置长短码和是否随机
      r.setCodeSort(Boolean.TRUE.equals(t.getCodeSort()) ? 1 : 0);
      r.setIsRandom(Boolean.TRUE.equals(t.getIsRandom()) ? 1 : 0);
      r.setIsAverage(Boolean.TRUE.equals(t.getIsAverage()) ? 1 : 0);
      // 错误个数默认为0
      r.setErrorNumber(0);
      // 正确率默认为0
      r.setAccuracy("0.00");
      r.setRuleId(gradingRule.getId());
      r.setRuleContent(gradingRule.getContent());
      r.setScore(gradingRule.getScore().toString());
    });
    PostTelegramTrainEntity save = postTelegramTrainDao.saveAndFlush(entity);

    // 生成随机报文
    if (Objects.equals(param.getIsCable(), 0)) {
      Integer messageNumber = param.getMessageNumber();
      int generate;
      if (messageNumber > 200) {
        generate = 200;
      } else {
        generate = messageNumber;
      }
      Integer type = param.getType();

      List<PostTelegramTrainFloorContentEntity> ret = new ArrayList<>();
      List<String> messageBody;
      // 生成对应的报文
      if (type.compareTo(NUMBER_MESSAGE.getType()) == 0) {
        messageBody = GlobalMessageGeneratedUtil.generatedNumber(generate,
            Boolean.TRUE.equals(param.getIsAverage()), Boolean.TRUE.equals(param.getIsRandom()));
      } else if (type.compareTo(STRING_MESSAGE.getType()) == 0) {
        messageBody = GlobalMessageGeneratedUtil.generatedWord(generate,
            Boolean.TRUE.equals(param.getIsAverage()), Boolean.TRUE.equals(param.getIsRandom()));
      } else {
        messageBody = GlobalMessageGeneratedUtil.generatedMingle(generate,
            Boolean.TRUE.equals(param.getIsAverage()), Boolean.TRUE.equals(param.getIsRandom()));
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
        PostTelegramTrainFloorContentEntity contentEntity = handleContentEntity(entity, floorNumber, i % 100, keys);
        ret.add(contentEntity);
      }

      floorContentDao.save(ret);
    } else { // 生成固定报
      List<List<List<String>>> cableFloor = cableFloorService.findCableFloor(param.getCableId(), null,
          param.getStartPage());
      int totalPage = param.getMessageNumber() / 100;
      if (totalPage <= 0) {
        throw new IllegalArgumentException("报文组数不足一页，无法建立训练");
      }
      if (totalPage > cableFloor.size()) {
        throw new IllegalArgumentException("所选电缆可用楼层不足");
      }
      cableFloor = cableFloor.subList(0, totalPage);
      List<PostTelegramTrainFloorContentEntity> list = new ArrayList<>();
      int floorNumber = 1;
      for (List<List<String>> floor : cableFloor) {
        int sortIndex = 0;
        for (List<String> moresKey : floor) {
          if (moresKey == null) {
            continue;
          }
          PostTelegramTrainFloorContentEntity contentEntity = handleContentEntity(entity, floorNumber, sortIndex,
              moresKey);
          list.add(contentEntity);
          sortIndex++;
        }
        floorNumber++;
      }
      floorContentDao.save(list);
    }
    return PojoUtils.convertOne(save, PostTelegramTrainVO.class);
  }

  private PostTelegramTrainFloorContentEntity handleContentEntity(PostTelegramTrainEntity entity,
      int floorNumber,
      int sortNumber,
      List<String> moresKey) {
    PostTelegramTrainFloorContentEntity contentEntity = new PostTelegramTrainFloorContentEntity();
    contentEntity.setTrainId(entity.getId());
    contentEntity.setMoresValue(EMPTY_JSON_ARRAY);
    contentEntity.setMoresTime(EMPTY_JSON_ARRAY);
    contentEntity.setPatKeys(EMPTY_JSON_ARRAY);
    contentEntity.setMoresKey(JSONUtils.toJson(moresKey));
    contentEntity.setFloorNumber(floorNumber);
    contentEntity.setSort(sortNumber);
    return contentEntity;
  }

  /**
   * 生成报文
   *
   * @param param   训练参数
   * @param trainId 训练id
   */
  private List<PostTelegramTrainFloorContentEntity> generateMessage(PostTelegramTrainEntity param,
      Integer generateNumber, int index, int floorNumber,
      String trainId) {
    List<PostTelegramTrainFloorContentEntity> floorContentEntities = new ArrayList<>();
    List<String> messageBody;
    // Phase 7.4：type/isAverage/isRandom 均为可空 Integer，裸 compareTo 会拆箱 NPE
    if (param.getType() == null) {
      throw new IllegalArgumentException("报文类型缺失，无法生成报文");
    }
    boolean average = Objects.equals(param.getIsAverage(), 1);
    boolean random = Objects.equals(param.getIsRandom(), 1);
    if (param.getType().compareTo(NUMBER_MESSAGE.getType()) == 0) {
      messageBody = GlobalMessageGeneratedUtil.generatedNumber(generateNumber, average, random);
    } else if (param.getType().compareTo(STRING_MESSAGE.getType()) == 0) {
      messageBody = GlobalMessageGeneratedUtil.generatedWord(generateNumber, average, random);
    } else {
      messageBody = GlobalMessageGeneratedUtil.generatedMingle(generateNumber, average, random);
    }
    int currentFloor = floorNumber;
    for (int i = 0; i < messageBody.size(); i++) {
      if (i % 100 == 0) {
        currentFloor++;
      }
      String group = messageBody.get(i);
      List<String> moresKey = new ArrayList<>();
      for (int j = 0; j < group.length(); j++) {
        moresKey.add(String.valueOf(group.charAt(j)));
      }
      PostTelegramTrainFloorContentEntity entity = new PostTelegramTrainFloorContentEntity();
      entity.setMoresValue(EMPTY_JSON_ARRAY);
      entity.setMoresTime(EMPTY_JSON_ARRAY);
      entity.setPatKeys(EMPTY_JSON_ARRAY);
      entity.setTrainId(trainId);
      entity.setFloorNumber(currentFloor);
      entity.setSort(i % 100);
      entity.setMoresKey(JSONUtils.toJson(moresKey));
      floorContentEntities.add(entity);
    }
    return floorContentDao.saveAndFlush(floorContentEntities);
  }

  public List<PostTelegramTrainVO> findAll(String token) {
    // 从token中获取用户
    UserEntity userEntity = userService.getUserByToken(token);

    List<PostTelegramTrainEntity> entities = postTelegramTrainDao.find(
        "createUser = ?1", Sort.by("createTime").descending(), userEntity.getId()).list();
    return PojoUtils.convert(entities, PostTelegramTrainVO.class, (t, r) -> {
      r.setCodeSort(Objects.equals(t.getCodeSort(), 1));
      r.setIsRandom(Objects.equals(t.getIsRandom(), 1));
    });
  }

  public PostTelegramTrainVO detail(PostTelegramTrainQueryParam param, String token) {
    PostTelegramTrainEntity entity = owned(param.getId(), token, false);
    requireReadable(entity);

    return PojoUtils.convertOne(entity, PostTelegramTrainVO.class, (t, r) -> {
      r.setServerElapsedMs(elapsed(entity, LocalDateTime.now()));
      if (Objects.equals(entity.getProtocolVersion(), 1)) {
        r.setPageAnalyzeVOS(contentValueDao.findAllByTrainIdOrderByFloorNumber(entity.getId()).stream().map(page -> {
          PostTelegraphKeyPatTrainPageAnalyzeVO analysis = new PostTelegraphKeyPatTrainPageAnalyzeVO();
          analysis.setPageNumber(page.getFloorNumber());
          analysis.setPatNumber(Math.toIntExact(countCharacters(JSONUtils.fromJson(page.getMessageBody(),
              new TypeToken<List<PostTelegramTrainContentAddParam>>() {}))));
          analysis.setTotalTime(CaptureTimeline.durationMillis(intervals(page), elapsed(entity, page.getReceivedAt())));
          return analysis;
        }).toList());
      }
      // 判断报底是否为null
      if (t.getFloorNow() == null) {
        r.setFloorNow(1);
      }
      r.setCodeSort(Objects.equals(t.getCodeSort(), 1));
      r.setIsRandom(Objects.equals(t.getIsRandom(), 1));
      List<String> messageBody = new ArrayList<>();
      List<PostTelegramTrainFloorContentEntity> floorContentEntities = floorContentDao
          .findByTrainIdOrderByFloorNumberSort(entity.getId());
      List<Integer> floorNumber = floorContentDao.findByTrainIdCountFloor(entity.getId());

      r.setExistNumber(floorNumber);
      List<String> resolver = new ArrayList<>();

      Map<Integer, List<PostTelegramTrainFloorContentEntity>> collect = floorContentEntities.stream().collect(
          Collectors.groupingBy(PostTelegramTrainFloorContentEntity::getFloorNumber));
      List<PostTelegramTrainFinishInfoDto> finishInfoDtos = new ArrayList<>();
      List<String> standards = new ArrayList<>();
      for (Map.Entry<Integer, List<PostTelegramTrainFloorContentEntity>> entry : collect.entrySet()) {
        int next = entry.getKey();
        List<PostTelegramTrainFloorContentEntity> page = entry.getValue();
        PostTelegramTrainContentFloorValueEntity contentFloorValueEntity = contentValueDao.findByFloorNumberAndTrainId(
            next, param.getId());
        if (contentFloorValueEntity == null) {
          List<PostTelegramTrainContentAddParam> addParams = page.stream().map(
              e -> PojoUtils.convertOne(e, PostTelegramTrainContentAddParam.class)).toList();
          messageBody.add(JSONUtils.toJson(addParams));
          finishInfoDtos.add(null);
          standards.add("{}");
        } else {
          String finishInfo = contentFloorValueEntity.getFinishInfo();
          finishInfoDtos.add(JSONUtils.fromJson(finishInfo, PostTelegramTrainFinishInfoDto.class));
          messageBody.add(
              Optional.of(contentFloorValueEntity).map(PostTelegramTrainContentFloorValueEntity::getMessageBody)
                  .orElse(EMPTY_JSON_ARRAY));
          standards.add(contentFloorValueEntity.getStandard());

          resolver.add(contentFloorValueEntity.getResolver());
          if (messageBody.size() == 2) {
            break;
          }
        }
      }
      r.setResolver(resolver);
      r.setFinishInfo(finishInfoDtos);
      r.setMessageBody(messageBody);
      r.setStandards(standards);
      if (null != t.getIsCable() && t.getIsCable() == 1) {
        r.setMessageNumber(collect.size());
        r.setMessageGroup(floorContentEntities.size());
      }
    });
  }

  @Transactional
  public PostTelegramTrainContentVO findMessageBody(PostTelegramTrainFloorContentQueryParam param, String token) {
    PostTelegramTrainEntity entity = owned(param.getId(), token, true);
    requireReadable(entity);
    requirePage(entity, param.getFloorNumber());

    List<PostTelegramTrainFloorContentEntity> contentEntities = floorContentDao.findByFloorNumberAndTrainIdOrderBySort(
        param.getFloorNumber(), param.getId());
    if (contentEntities.isEmpty()) {
      if (Objects.equals(entity.getStatus(), FINISH.getStatus())) throw new IllegalArgumentException("历史训练缺少该页报底，不再生成新内容");
      // Phase 7.4：floorNumber/messageNumber/type/isAverage/isRandom 均为可空包装类型，裸拆箱会 NPE
      if (param.getFloorNumber() == null) {
        throw new IllegalArgumentException("页码不能为空");
      }
      if (entity.getMessageNumber() == null) {
        throw new IllegalArgumentException("训练报文数量缺失，无法生成报底");
      }
      if (entity.getType() == null) {
        throw new IllegalArgumentException("报文类型缺失，无法生成报底");
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
      List<PostTelegramTrainFloorContentEntity> ret = new ArrayList<>();
      List<String> messageBody;
      boolean average = Objects.equals(entity.getIsAverage(), 1);
      boolean random = Objects.equals(entity.getIsRandom(), 1);
      // 生成对应的报文
      if (entity.getType().compareTo(NUMBER_MESSAGE.getType()) == 0) {
        messageBody = GlobalMessageGeneratedUtil.generatedNumber(generateNumber, average, random);
      } else if (entity.getType().compareTo(STRING_MESSAGE.getType()) == 0) {
        messageBody = GlobalMessageGeneratedUtil.generatedWord(generateNumber, average, random);
      } else {
        messageBody = GlobalMessageGeneratedUtil.generatedMingle(generateNumber, average, random);
      }
      // 按请求页号落库（原取「最后一页 + 1」，跳页请求会把内容写到错误的页号上）
      int floorNumber = currentPage - 1;
      for (int i = 0; i < messageBody.size(); i++) {
        if (i % 100 == 0) {
          floorNumber++;
        }
        String group = messageBody.get(i);
        List<String> keys = new ArrayList<>();
        for (int j = 0; j < group.length(); j++) {
          keys.add(String.valueOf(group.charAt(j)));
        }
        PostTelegramTrainFloorContentEntity contentEntity = handleContentEntity(entity, floorNumber, i % 100, keys);
        ret.add(contentEntity);
      }
      contentEntities = floorContentDao.saveAndFlush(ret);
    }
    List<PostTelegramTrainContentAddParam> addParams = contentEntities.stream().map(
        e -> PojoUtils.convertOne(e, PostTelegramTrainContentAddParam.class)).toList();

    // 查询此页提交内容
    PostTelegramTrainContentFloorValueEntity contentFloorValueEntity = contentValueDao.findByFloorNumberAndTrainId(
        param.getFloorNumber(), param.getId());

    return new PostTelegramTrainContentVO()
        .setProtocolVersion(entity.getProtocolVersion()).setAttempt(entity.getAttempt())
        .setServerElapsedMs(elapsed(entity, LocalDateTime.now()))
        .setSubmitted(contentFloorValueEntity != null)
        .setSavedCaptureIntervals(contentFloorValueEntity == null || !Objects.equals(entity.getProtocolVersion(), 1)
            ? List.of() : intervals(contentFloorValueEntity))
        .setMessageBody(contentFloorValueEntity == null ? JSONUtils.toJson(addParams) : contentFloorValueEntity.getMessageBody())
        .setMessageKey(addParams)
        .setFinishInfo(contentFloorValueEntity == null ? EMPTY_JSON_ARRAY : contentFloorValueEntity.getFinishInfo())
        .setStandard(contentFloorValueEntity == null ? EMPTY_JSON_ARRAY : contentFloorValueEntity.getStandard())
        .setResolver(contentFloorValueEntity == null ? "" : contentFloorValueEntity.getResolver());
  }

  @Transactional()
  public PostTelegramTrainVO begin(String id, Integer attempt, String token) {
    PostTelegramTrainEntity entity = owned(id, token, true);
    requireProtocol(entity);
    requireAttempt(entity, attempt);
    if (Objects.equals(entity.getStatus(), FINISH.getStatus())) {
      throw new TerminalStateException("已完成训练不可重新开始");
    }
    ScoringRuleValidation.handkey(entity.getRuleContent());
    if (entity.getFullScore() == null || entity.getFullScore() < 0) throw new IllegalArgumentException("训练满分快照无效，请新建训练");
    if (Objects.equals(entity.getStatus(), NOT_STARTED.getStatus())) {
      entity.setStartTime(LocalDateTime.now());
      entity.setStatus(UNDERWAY.getStatus());
      postTelegramTrainDao.saveAndFlush(entity);
    }
    return trainingView(entity);
  }

  @Transactional(rollbackOn = Exception.class)
  public void stop(String id, Integer attempt, String token) {
    PostTelegramTrainEntity entity = owned(id, token, true);
    requireProtocol(entity);
    requireAttempt(entity, attempt);
    // 判断状态是否是进行中
    if (entity.getStatus().compareTo(UNDERWAY.getStatus()) != 0) {
      throw new IllegalArgumentException(entity.getName() + "训练的状态不是进行中");
    }
    // 将状态修改成未开始，将报底修改为1
    entity.setStatus(NOT_STARTED.getStatus());
    entity.setFloorNow(1);
    entity.setSpeedLog(null);
    entity.setErrorNumber(0);
    entity.setAccuracy("0.00");
    entity.setAttempt(Math.addExact(entity.getAttempt(), 1));
    entity.setStartTime(null);
    entity.setEndTime(null);
    entity.setValidTime(0L);
    entity.setActiveMillis(0L);
    entity.setSpeed("0.00");
    entity.setScore(String.valueOf(entity.getFullScore()));
    entity.setFinishInfo(null);
    entity.setStatisticInfo(null);
    entity.setDeductInfo(null);
    entity.setLack(0);
    // 清除本场训练的所有拍发内容
    contentValueDao.deleteByTrainId(id);
    // 清除floor content 内容
    floorContentDao.clearByTranId(id);
    postTelegramTrainDao.saveAndFlush(entity);
  }

  @Transactional(rollbackOn = Exception.class)
  public PostTelegramTrainVO finish(PostTelegramTrainFinishDto dto, String token) {
    try {
      PostTelegramTrainEntity entity = owned(dto.getId(), token, true);
      if (Objects.equals(entity.getProtocolVersion(), 1)) requireAttempt(entity, dto.getAttempt());
      if (Objects.equals(entity.getStatus(), FINISH.getStatus())) return trainingView(entity);
      requireUnderway(entity);
      // 校验状态是否是进行中
      entity.setEndTime(LocalDateTime.now());
      entity.setStatus(FINISH.getStatus());
      List<PostTelegramTrainContentFloorValueEntity> pages = contentValueDao.findAllByTrainIdOrderByFloorNumber(entity.getId());
      long activeMillis = 0;
      long characters = 0;
      List<List<CaptureInterval>> timelines = new ArrayList<>();
      for (PostTelegramTrainContentFloorValueEntity page : pages) {
        List<CaptureInterval> timeline = intervals(page);
        if (!Objects.equals(page.getAttempt(), entity.getAttempt()) || page.getReceivedAt() == null) {
          throw new IllegalStateException("已保存页轮次或接收时间缺失");
        }
        activeMillis = Math.addExact(activeMillis, CaptureTimeline.durationMillis(timeline, elapsed(entity, page.getReceivedAt())));
        characters = Math.addExact(characters, countCharacters(JSONUtils.fromJson(page.getMessageBody(), new TypeToken<List<PostTelegramTrainContentAddParam>>() {})));
        timelines.add(timeline);
      }
      CaptureTimeline.requireNoOverlap(timelines);
      entity.setActiveMillis(activeMillis);
      entity.setValidTime(activeMillis / 1000);
      entity.setSpeed(TrainingRateUnit.CHARACTERS_PER_MINUTE.rate(characters, activeMillis).toPlainString());
      countScore(entity);
      postTelegramTrainDao.saveAndFlush(entity);
      return trainingView(entity);
    } catch (Exception e) {
      log.error("完成训练失败，训练ID: {}", dto.getId(), e);
      throw e;
    }
  }

  @Transactional(rollbackOn = Exception.class)
  public void saveContentValue(PostTelegramTrainContentValueDto dto, String token) {
    LocalDateTime receivedAt = LocalDateTime.now();
    PostTelegramTrainEntity trainEntity = owned(dto.getTrainId(), token, true);
    requireProtocol(trainEntity);
    requirePage(trainEntity, dto.getFloorNumber());
    if (!Objects.equals(dto.getAttempt(), trainEntity.getAttempt())) {
      throw new TerminalStateException("训练轮次已变化，请重新加载训练");
    }
    long duration = CaptureTimeline.durationMillis(dto.getCaptureIntervals(), elapsed(trainEntity, receivedAt));
    long characters = countCharacters(dto.getMessageBody());
    if (characters > 0 && duration == 0) throw new IllegalArgumentException("拍发正文缺少有效采集区间");
    if (dto.getStandard() == null || (characters > 0 && dto.getStandard().isEmpty())) throw new IllegalArgumentException("点划基准记录不能为空");
    for (PostTelegramTrainFinishInfoDto calibration : dto.getStandard()) {
      if (calibration == null || calibration.getDot() == null || calibration.getDot() <= 0
          || calibration.getLine() == null || calibration.getLine() <= 0 || calibration.getCodeGap() == null || calibration.getCodeGap() <= 0
          || calibration.getWordGap() == null || calibration.getWordGap() <= 0 || calibration.getGroupGap() == null || calibration.getGroupGap() <= 0) {
        throw new IllegalArgumentException("点划自校准基准记录无效");
      }
    }
    String body = JSONUtils.toJson(dto.getMessageBody());
    String standard = JSONUtils.toJson(dto.getStandard());
    String capture = JSONUtils.toJson(dto.getCaptureIntervals());
    List<PostTelegramTrainContentFloorValueEntity> pages = contentValueDao.findAllByTrainIdOrderByFloorNumber(dto.getTrainId());
    PostTelegramTrainContentFloorValueEntity valueEntity = pages.stream()
        .filter(saved -> Objects.equals(saved.getFloorNumber(), dto.getFloorNumber())).findFirst().orElse(null);
    if (valueEntity != null && Objects.equals(valueEntity.getMessageBody(), body)
        && Objects.equals(valueEntity.getStandard(), standard) && Objects.equals(valueEntity.getCaptureIntervals(), capture)
        && Objects.equals(valueEntity.getFinishInfo(), dto.getFinishInfo())) return;
    requireUnderway(trainEntity);
    if (valueEntity == null) {
      valueEntity = new PostTelegramTrainContentFloorValueEntity();
    } else {
      CaptureTimeline.requireExtension(intervals(valueEntity), dto.getCaptureIntervals());
    }
    PostTelegramTrainFloorContentQueryParam sourcePage = new PostTelegramTrainFloorContentQueryParam();
    sourcePage.setId(dto.getTrainId());
    sourcePage.setFloorNumber(dto.getFloorNumber());
    findMessageBody(sourcePage, token);
    List<List<CaptureInterval>> timelines = pages.stream()
        .filter(page -> !Objects.equals(page.getFloorNumber(), dto.getFloorNumber()))
        .map(this::intervals).collect(Collectors.toCollection(ArrayList::new));
    timelines.add(dto.getCaptureIntervals());
    CaptureTimeline.requireNoOverlap(timelines);
    valueEntity.setTrainId(dto.getTrainId());
    valueEntity.setFloorNumber(dto.getFloorNumber());
    valueEntity.setAttempt(dto.getAttempt());
    valueEntity.setMessageBody(body);
    valueEntity.setStandard(standard);
    valueEntity.setFinishInfo(dto.getFinishInfo());
    valueEntity.setCaptureIntervals(capture);
    valueEntity.setReceivedAt(receivedAt);
    valueEntity.setResolver(null);
    trainEntity.setFloorNow(Math.max(trainEntity.getFloorNow() == null ? 1 : trainEntity.getFloorNow(),
        Math.min(dto.getFloorNumber() + 1, (trainEntity.getMessageNumber() + 99) / 100)));
    contentValueDao.saveAndFlush(valueEntity);
    postTelegramTrainDao.saveAndFlush(trainEntity);
  }


  @Transactional
  public List<String> printBottomReport(PostTelegramTrainQueryParam param, String token) {
    PostTelegramTrainEntity entity = owned(param.getId(), token, true);
    requireReadable(entity);
    if (Objects.equals(entity.getStatus(), FINISH.getStatus())) {
      return floorContentDao.findByTrainIdOrderByFloorNumberSort(entity.getId()).stream().map(PostTelegramTrainFloorContentEntity::getMoresKey).toList();
    }
    PostTelegramTrainFloorContentEntity floorContentEntity = floorContentDao
        .findByTrainIdOrderByFloorNumberDescSortDesc(param.getId());
    // Phase 7.4：messageNumber/type 均为可空 Integer，裸拆箱会 NPE
    if (entity.getMessageNumber() == null) {
      throw new IllegalArgumentException("训练报文数量缺失，无法打印报底");
    }
    if (entity.getType() == null) {
      throw new IllegalArgumentException("报文类型缺失，无法打印报底");
    }
    Integer messageNumber = entity.getMessageNumber();
    int totalPage = messageNumber / 100;
    int floorNumber = 0;
    int generateNumber;
    if (messageNumber % 100 > 0) {
      totalPage += 1;
    }
    int pageSize = 10000;
    if (ObjectUtil.isNotEmpty(floorContentEntity)) {
      if (floorContentEntity.getFloorNumber().equals(totalPage)) {
        // P2-07：原「异步分页」实为同步（无 @Asynchronous），删除假 Future 与死异常处理，直接分页查询
        List<PostTelegramTrainFloorContentEntity> contentEntities = new ArrayList<>();
        Integer count = floorContentDao.findCountByTrainIdOrderByFloorNumberAscSortAsc(param.getId());
        if (count <= pageSize) {
          contentEntities
              .addAll(floorContentDao.findAllByTrainIdOrderByFloorNumberAscSortAscLimit(param.getId(), 0, count));
        } else {
          int totalPage1 = count % pageSize == 0 ? count / pageSize : count / pageSize + 1;
          for (int i = 0; i < totalPage1; i++) {
            int index = i * pageSize;
            contentEntities.addAll(
                floorContentDao.findAllByTrainIdOrderByFloorNumberAscSortAscLimit(param.getId(), index, pageSize));
          }
        }
        return contentEntities.stream().map(PostTelegramTrainFloorContentEntity::getMoresKey).toList();
      }
      generateNumber = entity.getMessageNumber() - (floorContentEntity.getFloorNumber() * 100);
      floorNumber = floorContentEntity.getFloorNumber();
    } else {
      generateNumber = entity.getMessageNumber();
    }
    Integer type = entity.getType();
    int index = type.compareTo(STRING_MESSAGE.getType()) == 0 ? 65 : 48;
    List<PostTelegramTrainFloorContentEntity> contentEntityList = floorContentDao
        .findByTrainIdOrderByFloorNumberSort(param.getId());
    List<PostTelegramTrainFloorContentEntity> contentEntities = generateMessage(entity, generateNumber, index,
        floorNumber, param.getId());
    contentEntityList.addAll(contentEntities);
    return contentEntityList.stream().map(PostTelegramTrainFloorContentEntity::getMoresKey).toList();
  }

  /**
   * 计算分数
   *
   * @param entity
   */
  private void countScore(PostTelegramTrainEntity entity) {
    Map<String, Integer> deductMap = new HashMap<>();
    Integer score = Objects.requireNonNull(entity.getFullScore(), "训练满分快照缺失");
    PostTelegramTrainRule rule = ScoringRuleValidation.handkey(entity.getRuleContent());
    PostTelegramTrainStatisticsVO statisticsVO = new PostTelegramTrainStatisticsVO();
    PostTelegramTrainScoreVO scoreVO = new PostTelegramTrainScoreVO();

    List<Integer> existFloorNumber = contentValueDao.countExistFloorNumber(entity.getId());
    if (existFloorNumber.isEmpty()) {
      handleEmptyFinishInfo(entity, deductMap, statisticsVO);
      return;
    }

    processPageComparisons(entity, existFloorNumber, scoreVO, rule, statisticsVO);

    statisticsAllAvg(statisticsVO, scoreVO.getDotTotalTime(), scoreVO.getLineTotalTime(),
        scoreVO.getCodeTotalTime(), scoreVO.getWordTotalTime(), scoreVO.getGroupTotalTime());

    calculateLackCount(entity.getMessageNumber(), existFloorNumber, scoreVO);

    score = applyDeductions(score, scoreVO, rule, deductMap);

    saveTrainResult(entity, scoreVO, score, statisticsVO, deductMap, rule);
  }

  private void handleEmptyFinishInfo(PostTelegramTrainEntity entity, Map<String, Integer> deductMap,
      PostTelegramTrainStatisticsVO statisticsVO) {
    entity.setAccuracy("0.00");
    entity.setSpeed("0.00");
    entity.setScore("0");
    entity.setStatisticInfo(JSONUtils.toJson(statisticsVO));
    deductMap.put("dotMinScore", 0);
    deductMap.put("lineScore", 0);
    deductMap.put("codeGapScore", 0);
    deductMap.put("wordGapScore", 0);
    deductMap.put("groupGapScore", 0);
    deductMap.put("alterErrorScore", 0);
    deductMap.put("errorWord", 0);
    deductMap.put("quantoGroup", 0);
    deductMap.put("quantoCode", 0);
    deductMap.put("quantoRow", 0);
    deductMap.put("bunchGroup", 0);
    entity.setDeductInfo(JSONUtils.toJson(deductMap));
  }

  private void processPageComparisons(PostTelegramTrainEntity entity, List<Integer> existFloorNumber,
      PostTelegramTrainScoreVO scoreVO, PostTelegramTrainRule rule, PostTelegramTrainStatisticsVO statisticsVO) {
    for (Integer floorNumber : existFloorNumber) {
      PostTelegramTrainContentFloorValueEntity contentFloorValueEntity = contentValueDao.findByFloorNumberAndTrainId(
          floorNumber, entity.getId());
      String messageBody = contentFloorValueEntity.getMessageBody();
      String standard = contentFloorValueEntity.getStandard();
      List<PostTelegramTrainContentAddParam> userContents = handleMessageBody(JSONUtils.fromJson(messageBody, new TypeToken<List<PostTelegramTrainContentAddParam>>() {}));
      List<PostTelegramTrainFinishInfoDto> standards = JSONUtils.fromJson(standard, new TypeToken<>() {
      });

      List<PostTelegramTrainFloorContentEntity> floorContentEntities = floorContentDao
          .findByFloorNumberAndTrainIdOrderBySort(floorNumber, entity.getId());
      List<String> sources = floorContentEntities.stream()
          .map(PostTelegramTrainFloorContentEntity::getMoresKey)
          .map(this::normalizeGroupString)
          .toList();
      List<String> patKeys = userContents != null
          ? userContents.stream()
              .map(PostTelegramTrainContentAddParam::getPatKeys)
              .map(this::normalizeGroupString)
              .toList()
          : null;

      PostTelegramTrainResolverVO comparison = messageComparisonService.comparison(
          sources, patKeys, scoreVO, userContents, standards, rule, statisticsVO);
      contentFloorValueEntity.setResolver(JSONUtils.toJson(comparison));
    }
  }

  private void calculateLackCount(Integer messageNumber, List<Integer> existFloorNumber,
      PostTelegramTrainScoreVO scoreVO) {
    int totalFloorNumber = messageNumber / 100;
    if (messageNumber % 100 > 0) {
      totalFloorNumber++;
    }
    List<Integer> existPageNumber = new ArrayList<>();
    for (int i = 0; i < totalFloorNumber; i++) {
      existPageNumber.add(i + 1);
    }
    existPageNumber.removeAll(existFloorNumber);
    for (Integer missingPage : existPageNumber) {
      int groups = Math.min(100, messageNumber - (missingPage - 1) * 100);
      scoreVO.setLackGroup(scoreVO.getLackGroup() + groups);
    }
  }

  static int applyDeductions(Integer baseScore, PostTelegramTrainScoreVO scoreVO, PostTelegramTrainRule rule,
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

    int alterErrorScore = calculateScore(rule.getAlterError().getMax(), scoreVO.getAlterErrorScore(),
        rule.getAlterError().getMax());
    score -= alterErrorScore;
    deductMap.put("alterErrorScore", alterErrorScore);
    deductMap.put("alterErrorNumber", scoreVO.getAlterErrorScore());

    int errorCode = calculateScore(rule.getErrorCode().getMax(), scoreVO.getErrorNumber() * rule.getErrorCode().getL(),
        rule.getErrorCode().getMax());
    score -= errorCode;
    deductMap.put("errorWord", errorCode);
    deductMap.put("errorWordNumber", scoreVO.getErrorNumber());

    int moreOrLackWord = calculateScore(rule.getQuantoCode().getMax(),
        scoreVO.getMoreOrLackWord() * rule.getQuantoCode().getL(), rule.getQuantoCode().getMax());
    score -= moreOrLackWord;
    deductMap.put("quantoCode", moreOrLackWord);
    deductMap.put("quantoCodeNumber", scoreVO.getMoreOrLackWord());

    int moreOrLackGroup = calculateScore(rule.getQuantoGroup().getMax(),
        (scoreVO.getMoreGroup() + scoreVO.getLackGroup()) * rule.getQuantoGroup().getL(),
        rule.getQuantoGroup().getMax());
    score -= moreOrLackGroup;
    deductMap.put("quantoGroup", moreOrLackGroup);
    deductMap.put("quantoGroupNumber", scoreVO.getMoreGroup());

    int moreOrLackLine = calculateScore(rule.getQuantoRow().getMax(),
        scoreVO.getMoreOrLackLine() * rule.getQuantoRow().getL(), rule.getQuantoRow().getMax());
    score -= moreOrLackLine;
    deductMap.put("quantoRow", moreOrLackLine);
    deductMap.put("quantoRowNumber", scoreVO.getMoreOrLackLine());

    int bunchGroup = calculateScore(rule.getBunchGroup().getMax(),
        scoreVO.getBunchGroup() * rule.getBunchGroup().getL(), rule.getBunchGroup().getMax());
    score -= bunchGroup;
    deductMap.put("bunchGroup", bunchGroup);
    deductMap.put("bunchGroupNumber", scoreVO.getBunchGroup());

    return score;
  }

  static void saveTrainResult(PostTelegramTrainEntity entity, PostTelegramTrainScoreVO scoreVO,
      int score, PostTelegramTrainStatisticsVO statisticsVO, Map<String, Integer> deductMap, PostTelegramTrainRule rule) {
    entity.setErrorNumber(scoreVO.getErrorNumber());
    entity.setLack(scoreVO.getLackGroup());

    if (scoreVO.getCorrect() == 0) {
      entity.setAccuracy("0.00");
    } else {
      String accuracy = new BigDecimal(scoreVO.getCorrect())
          .divide(new BigDecimal(scoreVO.getPatTotalNum()), 2, RoundingMode.HALF_UP)
          .multiply(new BigDecimal(100)).toString();
      entity.setAccuracy(accuracy);
    }


    // 与公共评分契约一致：高于基准按r加分，低于基准按l扣分。
    SpeedDeduct baseWpm = rule.getWpm();
    int speed = new BigDecimal(entity.getSpeed()).intValueExact();
    int wpmScore = ScoreMath.wpmScore(baseWpm.getBase(),
        baseWpm.getR() == null ? null : BigDecimal.valueOf(baseWpm.getR()),
        baseWpm.getL() == null ? null : BigDecimal.valueOf(baseWpm.getL()), speed).intValue();
    deductMap.put("wpmScore", wpmScore);
    score += wpmScore;

    entity.setScore(String.valueOf(score));
    entity.setStatisticInfo(JSONUtils.toJson(statisticsVO));
    entity.setDeductInfo(JSONUtils.toJson(deductMap));
  }

  /**
   * 追加 content value
   *
   * @param vo
   */

  @Transactional(rollbackOn = Exception.class)
  public List<Integer> addContentValue(PostTelegramTrainAddContentValueVO vo, String token) {
    PostTelegramTrainEntity train = owned(vo.getTrainId(), token, true);
    requireProtocol(train);
    if (!Objects.equals(train.getStatus(), NOT_STARTED.getStatus())) throw new TerminalStateException("训练开始后不可追加报底");
    Integer floorNumber = 0;
    PostTelegramTrainFloorContentEntity entity = floorContentDao.findByTrainId(vo.getTrainId());
    if (!Objects.isNull(entity)) {
      floorNumber = entity.getFloorNumber();
    }
    List<List<PostTelegramTrainContentAddParam>> messageBody = vo.getMessageBody();
    for (int i = 0; i < messageBody.size(); i++) {
      // 基准楼层之后逐页递增：第 i 页落 base + i + 1（原 floorNumber += i 会复用 base 并跳过 base+2）
      int currentFloor = floorNumber + i + 1;
      List<PostTelegramTrainContentAddParam> addParams = messageBody.get(i);
      for (int j = 0; j < addParams.size(); j++) {
        PostTelegramTrainContentAddParam contentAddParam = addParams.get(j);
        int finalJ = j;
        PostTelegramTrainFloorContentEntity contentEntity = PojoUtils.convertOne(
            contentAddParam, PostTelegramTrainFloorContentEntity.class, (p, e) -> {
              e.setFloorNumber(currentFloor);
              e.setSort(finalJ);
              e.setTrainId(vo.getTrainId());
              e.setMoresTime(EMPTY_JSON_ARRAY);
            });
        floorContentDao.saveAndFlush(contentEntity);
      }
    }
    // 查询已返回的FloorNumber
    return floorContentDao.findByTrainIdCountFloor(vo.getTrainId());
  }

  @Transactional
  public Boolean delete(String trainId, String token) {
    owned(trainId, token, true);
    contentValueDao.delete("trainId", trainId);
    floorContentDao.delete("trainId", trainId);
    return postTelegramTrainDao.deleteById(trainId);
  }

  /**
   * 训练属主判定。不存在与无权是两种拒绝：前者 202（参数/目标问题），后者 207（身份成立但无权限），
   * 不再像旧实现那样都折叠成「训练不存在或无权访问」的 202。
   *
   * <p>本方法只负责取实体与「不存在 -> 202」，授权口径本身统一在 {@link TrainWriteAccess#requireTrainOwner}。
   */
  private PostTelegramTrainEntity owned(String id, String token, boolean lock) {
    UserEntity user = userService.getUserByToken(token);
    PostTelegramTrainEntity entity = lock ? postTelegramTrainDao.findById(id, LockModeType.PESSIMISTIC_WRITE) : postTelegramTrainDao.findById(id);
    if (entity == null) {
      throw new IllegalArgumentException("未查询到训练");
    }
    trainWriteAccess.requireTrainOwner(user == null ? null : user.getId(), entity.getCreateUser(), "个人手键训练 " + id);
    return entity;
  }

  private void requireProtocol(PostTelegramTrainEntity entity) {
    if (!Objects.equals(entity.getProtocolVersion(), 1)) throw new IllegalArgumentException("旧训练缺少原始采集协议，请终止旧训练并新建");
  }

  private void requireAttempt(PostTelegramTrainEntity entity, Integer attempt) {
    if (attempt == null || !Objects.equals(entity.getAttempt(), attempt)) throw new TerminalStateException("训练轮次已变化，请重新加载训练");
  }

  private void requireReadable(PostTelegramTrainEntity entity) {
    if (!Objects.equals(entity.getStatus(), FINISH.getStatus())) requireProtocol(entity);
  }

  private void requireUnderway(PostTelegramTrainEntity entity) {
    requireProtocol(entity);
    if (!Objects.equals(entity.getStatus(), UNDERWAY.getStatus()) || entity.getStartTime() == null) throw new IllegalArgumentException("训练不在进行中");
  }

  private void requirePage(PostTelegramTrainEntity entity, Integer page) {
    if (page == null || page < 1 || entity.getMessageNumber() == null || page > (entity.getMessageNumber() + 99) / 100) throw new IllegalArgumentException("页码超出训练范围");
  }

  private long elapsed(PostTelegramTrainEntity entity, LocalDateTime now) {
    return entity.getStartTime() == null ? 0 : Math.max(0, Duration.between(entity.getStartTime(), now).toMillis());
  }

  private PostTelegramTrainVO trainingView(PostTelegramTrainEntity entity) {
    return PojoUtils.convertOne(entity, PostTelegramTrainVO.class, (t, r) -> {
      r.setCodeSort(Objects.equals(t.getCodeSort(), 1));
      r.setIsRandom(Objects.equals(t.getIsRandom(), 1));
      r.setServerElapsedMs(elapsed(t, LocalDateTime.now()));
    });
  }

  private List<CaptureInterval> intervals(PostTelegramTrainContentFloorValueEntity page) {
    return JSONUtils.fromJson(page.getCaptureIntervals(), new TypeToken<List<CaptureInterval>>() {});
  }

  static long countCharacters(List<PostTelegramTrainContentAddParam> body) {
    if (body == null) throw new IllegalArgumentException("拍发原始记录不能为空");
    long count = 0;
    for (PostTelegramTrainContentAddParam group : body) {
      if (group == null) throw new IllegalArgumentException("拍发组不能为空");
      List<String> keys = JSONUtils.fromJson(group.getPatKeys(), new TypeToken<List<String>>() {});
      List<List<Integer>> times = JSONUtils.fromJson(group.getMoresTime(), new TypeToken<List<List<Integer>>>() {});
      List<List<Integer>> values = JSONUtils.fromJson(group.getMoresValue(), new TypeToken<List<List<Integer>>>() {});
      List<List<PostTelegramTrainFinishInfoDto.PatLogs>> logs = JSONUtils.fromJson(group.getPatLogs(), new TypeToken<List<List<PostTelegramTrainFinishInfoDto.PatLogs>>>() {});
      if (keys == null || times == null || values == null || logs == null || keys.size() != logs.size() || keys.size() != times.size() || keys.size() != values.size()) {
        throw new IllegalArgumentException("拍发字符、码值与时长记录不一致");
      }
      for (int i = 0; i < keys.size(); i++) {
        String key = keys.get(i);
        if (key == null || times.get(i) == null || values.get(i) == null || times.get(i).size() != values.get(i).size()
            || times.get(i).stream().anyMatch(time -> time == null || time < 0)
            || values.get(i).stream().anyMatch(value -> value == null || (value != 0 && value != 1))) {
          throw new IllegalArgumentException("拍发码值或时长记录无效");
        }
        List<PostTelegramTrainFinishInfoDto.PatLogs> events = logs.get(i);
        if (events == null) throw new IllegalArgumentException("拍发事件记录不能为空");
        int symbol = 0;
        for (PostTelegramTrainFinishInfoDto.PatLogs event : events) {
          if (event == null || event.getKey() == null || event.getKey() < 0 || event.getKey() > 4 || event.getValue() == null || event.getValue() < 0) throw new IllegalArgumentException("拍发事件类型或时长无效");
          if (event.getKey() < 2) {
            if (symbol >= values.get(i).size() || !Objects.equals(event.getKey(), values.get(i).get(symbol)) || !Objects.equals(event.getValue(), times.get(i).get(symbol))) throw new IllegalArgumentException("拍发事件与码值时长不一致");
            symbol++;
          }
        }
        if (symbol != values.get(i).size()) throw new IllegalArgumentException("拍发事件缺少码值记录");
        count += key.codePoints().filter(c -> !Character.isWhitespace(c) && c != '?' && c != '.' && c != '。').count();
      }
    }
    return count;
  }

  /**
   * 对控制变量进行校验 在最后一组会出现，当intArray中的数字，与ret中相同，此时需要用之前的报文来替换。
   * 例如：ret = 81 intArray=83 就需要此段代码来解除死循环；找出前面的组将intArray中的8进行替换 替换后intArray = 63
   * 即可解除循环
   *
   * @param ret
   * @param intArray
   * @param sb
   */
  private void relieveWhile(List<String> ret, List<Integer> intArray, StringBuilder sb) {
    for (int z = 0; z < intArray.size(); z++) {
      Integer integer = intArray.get(z);
      if (sb.indexOf(String.valueOf(integer)) != -1) {

        for (int k = 1; k < 10; k++) {
          if (ret.size() < k) {
            break;
          }
          String lastGroup = ret.get(ret.size() - k);
          // 对上一组的进行判定是否包含本次重复字符串
          if (!lastGroup.contains(String.valueOf(integer))) {
            // 如果不包含进行替换本次重复字符串
            for (int l = 0; l < lastGroup.length(); l++) {
              int temp = Integer.parseInt(String.valueOf(lastGroup.charAt(l)));
              // 找出范围
              String replace = lastGroup.replace(String.valueOf(temp), String.valueOf(integer));
              if (integer >= 1 && integer <= 5) {
                if (temp >= 1 && temp <= 5) {
                  ret.set(ret.size() - k, replace);
                  intArray.set(z, temp);
                  break;
                }
              } else {
                if ((temp >= 6 && temp <= 9) || temp == 0) {
                  ret.set(ret.size() - k, replace);
                  intArray.set(z, temp);
                  break;
                }
              }
            }
          }
        }
      }
    }
  }

  private String normalizeGroupString(String json) {
    try {
      List<String> list = JSONUtils.fromJson(json, new TypeToken<List<String>>() {
      });
      if (list == null) {
        return "";
      }
      return String.join("", list);
    } catch (Exception e) {
      return "";
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
