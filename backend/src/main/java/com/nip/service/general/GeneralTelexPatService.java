package com.nip.service.general;

import cn.hutool.core.text.CharSequenceUtil;
import com.google.gson.reflect.TypeToken;
import com.nip.common.exception.UnauthorizedException;
import com.nip.common.PageInfo;
import com.nip.common.constants.CodeConstants;
import com.nip.common.constants.PostTelegramTrainEnum;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.common.utils.ArraySafeGetUtils;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.Page;
import com.nip.common.utils.PojoUtils;
import com.nip.common.utils.ScoreMath;
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
import com.nip.ws.service.RoomDeletionTransaction;
import com.nip.ws.service.RoomLifecycleLocks;
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
import java.util.concurrent.locks.Lock;
import com.nip.common.exception.ForbiddenException;
import com.nip.common.exception.TerminalStateException;
import com.nip.common.utils.CaptureTimeline;
import com.nip.dto.CaptureInterval;
import com.nip.dto.score.TrainingRateUnit;
import com.nip.service.PostTelexPatTrainService;
import com.nip.service.TrainWriteAccess;
import jakarta.persistence.LockModeType;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import com.nip.common.utils.PatTrainStatisticsBuilder;
import static com.nip.common.constants.PostTelegramTrainEnum.NOT_STARTED;
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
  @Inject
  RoomDeletionTransaction roomDeletionTransaction;

  /**
   * 写口径（创建者 ∪ 该训练内 role=1 组训人 ∪ 管理员）与结算后通知的唯一实现。
   * 用字段注入而不是加构造器参数：构造器已被现存用例以固定实参列表调用。
   */
  @Inject
  TrainWriteAccess trainWriteAccess;
  @Inject
  GeneralPatResultNotifier resultNotifier;

  /** 采集协议版本。0 为历史训练：没有原始采集时间轴，只读不重算。 */
  private static final int CAPTURE_PROTOCOL = 1;
  /** 教员结束训练后仍接收在途提交的宽限窗口，与 general 手键/电子键一致。 */
  private static final int GRACE_SECONDS = 60;

  @Inject
  public GeneralTelexPatService(GeneralTelexPatDao trainDao, GeneralTelexPatPageDao trainPageDao,
      GeneralTelexPatUserDao trainUserDao,
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
    // Phase 7.4：isCable/totalNumber/type/patType 均为可空 Integer，下面 :131-:155 的裸拆箱会 NPE 成 500；
    // 入参校验前置到写库之前
    if (param.getIsCable() == null) {
      throw new IllegalArgumentException("是否使用电缆报底不能为空");
    }
    if (param.getTotalNumber() == null) {
      throw new IllegalArgumentException("训练总组数不能为空");
    }
    if (Objects.equals(param.getIsCable(), 0)) {
      if (param.getType() == null) {
        throw new IllegalArgumentException("报文类型不能为空");
      }
      if (Objects.equals(param.getType(), 0) && param.getPatType() == null) {
        throw new IllegalArgumentException("拍发类型不能为空");
      }
    }
    UserEntity userEntity = userService.getUserByToken(token);
    GradingRuleEntity ruleEntity = Optional.ofNullable(gradingRuleDao.findById(param.getRuleId()))
        .orElseThrow(() -> new IllegalArgumentException("未查询到评分规则"));
    if (ruleEntity.getScore() == null || ruleEntity.getScore() <= 0) {
      throw new IllegalArgumentException("评分规则满分不合法");
    }
    GeneralTelexPatEntity entity = PojoUtils.convertOne(param, GeneralTelexPatEntity.class, (t, r) -> {
      // 设置默认值
      r.setStatus(NOT_STARTED.getStatus());
      r.setValidTime(0L);
      r.setCreateUser(userEntity.getId());
      r.setRuleContent(ruleEntity.getContent());
      // 冻结满分：规则事后被改不影响已建训练的结算基准
      r.setRuleScore(ruleEntity.getScore());
      r.setProtocolVersion(CAPTURE_PROTOCOL);
    });
    GeneralTelexPatEntity save = trainDao.save(entity);

    // 保存参训人员信息
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
              "title", save.getTitle())));
    }
    GeneralTelexPatUserEntity groupUser = new GeneralTelexPatUserEntity();
    groupUser.setTrainId(save.getId());
    groupUser.setUserId(userEntity.getId());
    groupUser.setRole(1);
    trainUserEntityList.add(groupUser);
    trainUserDao.save(trainUserEntityList);
    Integer groupNumber = entity.getTotalNumber();
    int generateNumber = groupNumber < 200 ? groupNumber : 200;
    if (Objects.equals(save.getIsCable(), 0)) {
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
        // 生成报文
        generateContent(save.getType(), generateNumber, 1, save.getId());
      }

    } else {
      List<List<List<String>>> cableFloor = cableFloorService.findCableFloor(param.getCableId(), null,
          param.getStartPage());
      int totalPage = groupNumber / 100;
      if (totalPage <= 0) {
        throw new IllegalArgumentException("报文组数不足一页，无法建立房间");
      }
      if (totalPage > cableFloor.size()) {
        throw new IllegalArgumentException("所选电缆可用楼层不足");
      }
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
            v.setUserInfoList(PojoUtils.convert(trainUserDao.findByTrainIdToMapSimple(e.getId()),
                GeneralTelexPatUserInfoVO.class));
            v.setRuleContent(null);
          });
      PageInfo<GeneralTelexPatTrainVO> pageInfo = new PageInfo<>();
      pageInfo.setCurrentPage(all.getCurrentPage());
      pageInfo.setPageSize(all.getPageSize());
      pageInfo.setTotalPage(all.getTotalPage());
      pageInfo.setTotalNumber(all.getTotalNumber());
      pageInfo.setData(convert);
      return pageInfo;
    } catch (UnauthorizedException | ForbiddenException | TerminalStateException e) {
      throw e;
    } catch (IllegalArgumentException | IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      log.error("查询训练列表失败", e);
      throw new RuntimeException(e);
    }
  }

  public GeneralTelexPatTrainVO detail(GeneralTelexPatPageParamDto param, String token) {
    try {
      // 查询该训练信息
      GeneralTelexPatEntity keyPatEntity = Optional.ofNullable(trainDao.findById(param.getTrainId()))
          .orElseThrow(() -> new IllegalArgumentException("未查询到训练"));
      requireMember(keyPatEntity, token);
      GeneralTelexPatTrainVO patTrainVO = PojoUtils.convertOne(keyPatEntity, GeneralTelexPatTrainVO.class);
      if (Objects.equals(keyPatEntity.getIsCable(), 1)) {
        patTrainVO.setTotalNumber((int) trainPageDao.count("trainId", param.getTrainId()));
        patTrainVO.setPageCount(trainPageDao.findMaxPageNumber(param.getTrainId()));
      }
      // 查询该训练的所有参与用户信息
      List<GeneralTelexPatUserInfoVO> userInfoList = PojoUtils.convert(
          trainUserDao.findByTrainIdToMap(param.getTrainId()), GeneralTelexPatUserInfoVO.class);
      patTrainVO.setUserInfoList(userInfoList);

      // 查询每个用户在线状态
      List<GeneralPatTrainUserDto> userDto = new ArrayList<>(findUserInfo(param.getTrainId()));
      Map<String, List<GeneralPatTrainUserDto>> collect = userDto.stream()
          .collect(Collectors.groupingBy(GeneralPatTrainUserDto::getId));
      for (GeneralTelexPatUserInfoVO item : userInfoList) {
        List<GeneralPatTrainUserDto> keyPatTrainUserDto = collect.get(item.getUserId());
        if (keyPatTrainUserDto != null && !keyPatTrainUserDto.isEmpty()) {
          Integer status = keyPatTrainUserDto.stream().findFirst().map(GeneralPatTrainUserDto::getStatus).orElse(0);
          item.setUserStatus(status);
        } else {
          // 没有默认为0（离线状态）
          item.setUserStatus(0);
        }
        // 统计信息
        item.setPageAnalyzeVOS(generatePageAnalyze(param.getTrainId(), item.getUserId()));
      }
      return patTrainVO;
    } catch (ForbiddenException | TerminalStateException | IllegalArgumentException | IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      log.error("查询训练详情失败，训练ID: {}", param.getTrainId(), e);
      throw new RuntimeException(e);
    }
  }

  /**
   * 生成统计信息
   */
  private List<PostTelegraphKeyPatTrainPageAnalyzeVO> generatePageAnalyze(String trainId, String userId) {
    // 统计每页拍发时长和个数
    List<GeneralTelexPatUserValueEntity> pageValueEntities = trainUserValueDao
        .findByTrainIdAndUserIdOrderByPageNumberAscSortAsc(trainId, userId);
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

  public GeneralTelexPatUserInfoVO patDetail(GeneralTelexPatPageParamDto param, String token) {
    try {
      // 查询该训练信息
      GeneralTelexPatEntity keyPatEntity = Optional.ofNullable(trainDao.findById(param.getTrainId()))
          .orElseThrow(() -> new IllegalArgumentException("未查询到训练"));
      requireReadableTarget(keyPatEntity, param.getUserId(), token);
      GeneralTelexPatUserEntity patUserEntity = Optional.ofNullable(
              trainUserDao.findByUserIdAndTrainId(param.getUserId(), param.getTrainId()))
          .orElseThrow(() -> new IllegalArgumentException("未查询到该用户的参训记录"));

      List<Integer> pageNumber = trainPageDao.countPageNumber(param.getTrainId());
      // 查询前2页数据content
      List<GeneralTelexPatPageEntity> twoPage = trainPageDao.findTwoPage(param.getTrainId());
      List<GeneralTelexPatUserValueEntity> toPageValue = trainUserValueDao.findTwoPage(param.getTrainId(),
          param.getUserId());
      // 统计每页拍发时长和个数
      List<GeneralTelexPatUserValueEntity> pageValueEntities = trainUserValueDao
          .findByTrainIdAndUserIdOrderByPageNumberAscSortAsc(param.getTrainId(), param.getUserId());
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
        if (Objects.equals(patUserEntity.getIsFinish(), 1)) {
          v.setContent(PojoUtils.convert(toPageValue, PostTelegraphKeyPatTrainPageMessageVO.class));
        } else {
          v.setContent(PojoUtils.convert(twoPage, PostTelegraphKeyPatTrainPageMessageVO.class));
        }
        v.setDuration(null == t.getValidTime() ? 0L : Long.valueOf(t.getValidTime()));
        v.setPageAnalyzeVOS(analyzeVOS);
        v.setRuleContent(keyPatEntity.getRuleContent());
        v.setTotalNumber(keyPatEntity.getTotalNumber());
        v.setIsCable(keyPatEntity.getIsCable());
        if (Objects.equals(keyPatEntity.getIsCable(), 1)) {
          v.setTotalNumber((int) trainPageDao.count("trainId", param.getTrainId()));
          v.setPageCount(trainPageDao.findMaxPageNumber(param.getTrainId()));
        }
      });
    } catch (ForbiddenException | TerminalStateException | IllegalArgumentException | IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      log.error("查询拍发详情失败，训练ID: {}", param.getTrainId(), e);
      throw new RuntimeException(e);
    }

  }

  /**
   * 查询指定 trainId 对应页码的报底
   */
  public GeneralTelexPatPageDto findMessageBody(GeneralTelexPatPageParamDto param, String token) {
    GeneralTelexPatEntity train = Optional.ofNullable(trainDao.findById(param.getTrainId()))
        .orElseThrow(() -> new IllegalArgumentException("未查询到训练"));
    requireMember(train, token);
    // 查询出该训练对应页码的报底
    final List<GeneralTelexPatPageEntity> trainPageList = trainPageDao
        .findByTrainIdAndPageNumberOrderBySort(param.getTrainId(), param.getPageNumber());
    GeneralTelexPatPageDto dto = new GeneralTelexPatPageDto();
    dto.setMessageContent(PojoUtils.convert(trainPageList, GeneralTelexPatPageDetailDto.class));
    return dto;
  }

  /**
   * 修改训练状态。写口径统一为「创建者 ∪ 该训练内 role=1 组训人 ∪ 管理员」，拒绝抛 207。
   *
   * <p>开始训练时把采集起点写进每个学员的成员行：采集区间与训练时钟都以它为零点，
   * 训练行只提供结束时刻（收尾窗口起点），不参与单个学员的采集边界。
   */
  @Transactional
  public void updateStatus(String trainId, Integer status, String token) {
    String actor = userService.getUserByToken(token).getId();
    GeneralTelexPatEntity keyPatTrain = lockedTrain(trainId);
    trainWriteAccess.requireWritableTrain(actor, keyPatTrain.getCreateUser(),
        () -> organizer(trainId, actor), "数据报组训 " + trainId);
    keyPatTrain.setStatus(status);
    if (Objects.equals(status, PostTelegramTrainEnum.UNDERWAY.getStatus())) {
      // 教员点击开始训练，设置开始时间与各学员采集起点
      LocalDateTime startedAt = LocalDateTime.now();
      keyPatTrain.setStartTime(startedAt);
      for (GeneralTelexPatUserEntity participant : trainUserDao.findByTrainIdAndRole(trainId, 0)) {
        participant.setCaptureStartedAt(startedAt);
        trainUserDao.save(participant);
      }
    } else if (Objects.equals(status, PostTelegramTrainEnum.FINISH.getStatus())) {
      keyPatTrain.setEndTime(LocalDateTime.now());
      keyPatTrain.setValidTime(keyPatTrain.getStartTime() == null ? 0L
          : Math.max(0, Duration.between(keyPatTrain.getStartTime(), keyPatTrain.getEndTime()).toSeconds()));
    }
    trainDao.saveAndFlush(keyPatTrain);
  }

  /**
   * 保存某页的原始提交。
   *
   * <p>写入的是<b>原始行</b>（{@code sort = -1}）：整页文本 + 轮次 + 采集区间 + 收到时刻。
   * 逐页用时与码率一律由 {@link #deriveCapture} 从采集区间重算，请求体不再携带 speed/validTime。
   * 训练行取悲观写锁串行化同一训练的全部写入，成员行的「已结算判定 → 写入」不再存在 TOCTOU 窗口。
   */
  @Transactional
  public void saveContentValue(GeneralTelexPatPageSubmitDto dto, String token) {
    LocalDateTime receivedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    String userId = userService.getUserByToken(token).getId();
    GeneralTelexPatEntity train = lockedTrain(dto.getTrainId());
    GeneralTelexPatUserEntity member = student(train.getId(), userId);
    requireProtocol(train);
    if (!Objects.equals(dto.getProtocolVersion(), CAPTURE_PROTOCOL)) {
      // 与兄弟域同族：能力/目标不匹配，属「换个目标再来」的 202，不是重试必败的终态
      throw new IllegalStateException("客户端采集协议版本不匹配，请刷新后重新进入训练");
    }
    requireAttempt(dto.getAttempt(), member);
    requirePageNumber(train, dto.getPageNumber());
    if (dto.getPatValue() == null) {
      throw new IllegalArgumentException("页面内容不能为空");
    }
    if (dto.getCaptureIntervals() == null) {
      throw new IllegalArgumentException("采集时间轴不能为空");
    }
    List<GeneralTelexPatUserValueEntity> savedRows = trainUserValueDao
        .findRawByTrainIdAndPageNumberAndUserId(train.getId(), dto.getPageNumber(), userId);
    if (!savedRows.isEmpty()) {
      List<CaptureInterval> previous = intervals(savedRows.getFirst().getCaptureIntervals());
      if (previous.equals(dto.getCaptureIntervals())) {
        // 同一份采集区间重投：内容一致即幂等返回，内容不一致说明客户端在本地改写了已确认的页
        if (Objects.equals(savedRows.getFirst().getValue(), dto.getPatValue())) {
          return;
        }
        // 202 而非终态：学员重新读取已保存页、再提交修正后的正文就能成功
        throw new IllegalStateException("该页采集区间已确认，但正文不一致");
      }
      CaptureTimeline.requireExtension(previous, dto.getCaptureIntervals());
    }
    if (Objects.equals(member.getIsFinish(), 1)) {
      throw new TerminalStateException("已结算的训练不能上传");
    }
    long duration = CaptureTimeline.durationMillis(dto.getCaptureIntervals(),
        captureBound(train, member, receivedAt));
    if (PostTelexPatTrainService.characterCount(dto.getPatValue(), train.getTrainType()) > 0 && duration == 0) {
      throw new IllegalArgumentException("非空正文必须有有效采集时长");
    }
    List<List<CaptureInterval>> timelines = new ArrayList<>();
    timelines.add(dto.getCaptureIntervals());
    for (GeneralTelexPatUserValueEntity page : trainUserValueDao.findRawByTrainIdAndUserId(train.getId(), userId)) {
      if (!Objects.equals(page.getPageNumber(), dto.getPageNumber())) {
        timelines.add(intervals(page.getCaptureIntervals()));
      }
    }
    CaptureTimeline.requireNoOverlap(timelines);
    trainUserValueDao.deleteRawByTrainIdAndPageNumberAndUserId(train.getId(), dto.getPageNumber(), userId);
    trainUserValueDao.saveAndFlush(new GeneralTelexPatUserValueEntity()
        .setTrainId(train.getId())
        .setUserId(userId)
        .setPageNumber(dto.getPageNumber())
        .setSort(-1)
        .setValue(dto.getPatValue())
        .setAttempt(member.getAttempt())
        .setCaptureIntervals(JSONUtils.toJson(dto.getCaptureIntervals()))
        .setReceivedAt(receivedAt));
    deriveCapture(train, member);
    trainUserDao.save(member);
  }

  public boolean delete(String trainId, String token) {
    String actor = userService.getUserByToken(token).getId();
    GeneralTelexPatEntity train = Optional.ofNullable(trainDao.findById(trainId))
        .orElseThrow(() -> new IllegalArgumentException("未查询到训练"));
    trainWriteAccess.requireWritableTrain(actor, train.getCreateUser(),
        () -> organizer(trainId, actor), "数据报组训 " + trainId);
    Lock lock = RoomLifecycleLocks.generalTelexRoom(trainId);
    GeneralPatTrainRoomUserDto removed;
    boolean deleted;
    lock.lock();
    try {
      deleted = roomDeletionTransaction.run(() -> {
        trainUserValueDao.delete("trainId=?1", trainId);
        trainPageDao.delete("trainId=?1", trainId);
        trainUserDao.delete("trainId=?1", trainId);
        return trainDao.deleteById(trainId);
      });
      removed = WebSocketGeneralTelexPatService.ROOM.remove(trainId);
    } finally {
      lock.unlock();
    }
    WebSocketGeneralTelexPatService.closeRoomSessions(removed);
    return deleted;
  }

  /**
   * 学员结算。结算对象取自 token，不接受请求体指定他人。
   *
   * <p>训练行悲观写锁 + 轮次栅栏 + {@code isFinish} 幂等：并发两次只会真正结算一次，
   * 第二次直接返回既有成绩，不会重复扣分也不会再发一帧结果通知。
   */
  @Transactional
  public List<GeneralTelexPatUserInfoVO> finish(GeneralTelexPatFinishDto dto, String token) {
    try {
      String userId = userService.getUserByToken(token).getId();
      GeneralTelexPatEntity entity = lockedTrain(dto.getTrainId());
      GeneralTelexPatUserEntity userTrainEntity = lockedStudent(entity.getId(), userId);
      requireAttempt(dto.getAttempt(), userTrainEntity);
      if (Objects.equals(userTrainEntity.getIsFinish(), 1)) {
        return List.of(PojoUtils.convertOne(userTrainEntity, GeneralTelexPatUserInfoVO.class));
      }
      requireProtocol(entity);
      return List.of(PojoUtils.convertOne(settleMember(entity, userTrainEntity),
          GeneralTelexPatUserInfoVO.class));
    } catch (ForbiddenException | TerminalStateException | IllegalArgumentException | IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      log.error("完成训练失败，训练ID: {}", dto.getTrainId(), e);
      throw new RuntimeException(e);
    }
  }

  /**
   * 结算一名学员并登记结果通知。
   *
   * <p>通知走 {@link GeneralPatResultNotifier}（{@code AFTER_SUCCESS}）：结算事务回滚时教员端不会收到幻影帧。
   */
  private GeneralTelexPatUserEntity settleMember(GeneralTelexPatEntity train, GeneralTelexPatUserEntity member) {
    GeneralTelexPatUserEntity settled = countScore(train, member);
    settled.setIsFinish(1);
    settled.setFinishTime(LocalDateTime.now());
    trainUserDao.saveAndFlush(settled);
    resultNotifier.publish("telex", train.getId(), member.getUserId(),
        trainUserDao.findRoleAdminByUserId(train.getId()).stream()
            .map(GeneralTelexPatUserEntity::getUserId).toList());
    return settled;
  }

  /**
   * 扣底扫描清单：教员已结束（status=2）、补交窗口已过、但仍有学员没结算的训练。
   *
   * <p>组训电传只有三态，没有独立的「待收尾」状态，因此终止条件由「还存在未结算学员」给出：
   * 全部结算完后本查询自然不再返回该训练，定时器不会每 5 秒空跑同一条。
   */
  @Transactional
  public List<String> closingTrainIds() {
    // 只投影主键：该查询每 5 秒执行一次，取整行会把 ruleContent 等 longtext 一并载入后立刻丢弃。
    return trainDao.getEntityManager().createQuery(
            "select t.id from general_telex_pat t where t.protocolVersion = " + CAPTURE_PROTOCOL
                + " and t.status = " + PostTelegramTrainEnum.FINISH.getStatus() + " and t.endTime <= :deadline"
                + " and exists (select 1 from general_telex_pat_user u where u.trainId = t.id and u.role = 0"
                + " and (u.isFinish is null or u.isFinish <> 1))", String.class)
        .setParameter("deadline", LocalDateTime.now().minusSeconds(GRACE_SECONDS))
        .getResultList();
  }

  /** 扣底结算：补交窗口过后仍未 finish 的学员按已提交内容结算，避免成绩永久悬空。 */
  @Transactional
  public void settleExpired(String trainId) {
    GeneralTelexPatEntity train = lockedTrain(trainId);
    if (!Objects.equals(train.getProtocolVersion(), CAPTURE_PROTOCOL)
        || !Objects.equals(train.getStatus(), PostTelegramTrainEnum.FINISH.getStatus())
        || train.getEndTime() == null
        || LocalDateTime.now().isBefore(train.getEndTime().plusSeconds(GRACE_SECONDS))) {
      return;
    }
    for (GeneralTelexPatUserEntity participant : trainUserDao.findByTrainIdAndRole(trainId, 0)) {
      // 与 finish 同理：成员行必须在训练行锁内做当前读，否则扣底扫描可能按旧快照重复结算。
      trainUserDao.getEntityManager().refresh(participant, LockModeType.PESSIMISTIC_WRITE);
      if (!Objects.equals(participant.getIsFinish(), 1)) {
        settleMember(train, participant);
      }
    }
  }

  /**
   * 取某页报底与该学员的已确认采集状态。
   *
   * <p>返回体里的 {@code protocolVersion}/{@code attempt}/{@code serverElapsedMs}/{@code savedCaptureIntervals}
   * 是前端 {@code useTrainingCapture} 的绑定输入：客户端据此对齐服务端时钟并在已确认区间之后继续采集。
   */
  public PostTelegraphTelexPatTrainPageVO getPage(String trainId, Integer pageNumber, String userId, String token) {
    try {
      PostTelegraphTelexPatTrainPageVO ret = new PostTelegraphTelexPatTrainPageVO();
      GeneralTelexPatEntity entity = Optional.ofNullable(trainDao.findById(trainId))
          .orElseThrow(() -> new IllegalArgumentException("未查询到训练"));
      String target = requireReadableTarget(entity, userId, token);
      List<GeneralTelexPatPageEntity> messageVO = null;
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
      List<GeneralTelexPatUserValueEntity> userPage = trainUserValueDao
          .findByTrainIdAndPageNumberAndUserIdOrderBySort(trainId, pageNumber, target);
      // 生成的内容
      List<GeneralTelexPatPageEntity> pageDaoAll = trainPageDao.findByTrainIdAndPageNumberOrderBySort(trainId,
          pageNumber);
      if (!pageDaoAll.isEmpty()) {
        messageVO = pageDaoAll;
      } else {
        messageVO = generateContent(entity.getType(), generateNumber, pageNumber, entity.getId());
      }
      // 用户未拍发本页内容，则获取生成的内容
      if (userPage.isEmpty()) {
        ret.setMessageVO(PojoUtils.convert(messageVO, PostTelegraphTelexPatTrainPageMessageVO.class));
      } else {
        ret.setMessageVO(PojoUtils.convert(userPage, PostTelegraphTelexPatTrainPageMessageVO.class));
      }
      GeneralTelexPatUserEntity member = trainUserDao.findByUserIdAndTrainId(target, trainId);
      List<GeneralTelexPatUserValueEntity> rawRows = trainUserValueDao
          .findRawByTrainIdAndPageNumberAndUserId(trainId, pageNumber, target);
      ret.setProtocolVersion(entity.getProtocolVersion());
      ret.setAttempt(member == null ? null : member.getAttempt());
      ret.setServerElapsedMs(member == null ? 0 : elapsedMillis(member.getCaptureStartedAt()));
      ret.setSubmitted(!rawRows.isEmpty());
      // 历史训练（protocol_version=0）的原始行没有时间轴，这里返回空集合而不是报错，读旧成绩不受影响
      ret.setSavedCaptureIntervals(rawRows.isEmpty() || rawRows.getFirst().getCaptureIntervals() == null
          ? List.of() : intervals(rawRows.getFirst().getCaptureIntervals()));
      return ret;
    } catch (ForbiddenException | TerminalStateException | IllegalArgumentException | IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      log.error("获取训练页面失败，训练ID: {}, 页码: {}", trainId, pageNumber, e);
      throw new RuntimeException(e);
    }
  }

  public List<GeneralTelexPatTrainUserValueVO> getPatValue(GeneralTelexPatPageParamDto param, String token) {
    GeneralTelexPatEntity train = Optional.ofNullable(trainDao.findById(param.getTrainId()))
        .orElseThrow(() -> new IllegalArgumentException("未查询到训练"));
    String target = requireReadableTarget(train, param.getUserId(), token);
    List<GeneralTelexPatUserValueEntity> patUserValueEntities = trainUserValueDao
        .findByPageNumberAndTrainIdAndUserId(param.getPageNumber(), param.getTrainId(), target);
    return PojoUtils.convert(patUserValueEntities, GeneralTelexPatTrainUserValueVO.class);
  }

  public GeneralTelexPatTrainStatisticVO statistic(String trainId, String token) {
    GeneralTelexPatEntity train = Optional.ofNullable(trainDao.findById(trainId))
        .orElseThrow(() -> new IllegalArgumentException("未查询到训练"));
    requireMember(train, token);
    // role-0,学员
    List<GeneralTelexPatUserEntity> trainUserEntities = trainUserDao.findByTrainIdAndRole(trainId, 0);
    return statisticsScoreAndDotLineGapRate(trainUserEntities);
  }

  private GeneralTelexPatTrainStatisticVO statisticsScoreAndDotLineGapRate(
      List<GeneralTelexPatUserEntity> trainUserEntities) {
    // 结果集
    GeneralTelexPatTrainStatisticVO ret = new GeneralTelexPatTrainStatisticVO();
    // 错情统计
    GeneralTelexPatTrainErrorCollect errorCollect = new GeneralTelexPatTrainErrorCollect();

    // 使用构建器模式重构统计逻辑
    PatTrainStatisticsBuilder<GeneralTelexPatUserEntity, GeneralTelexPatTrainErrorCollect> builder =
        PatTrainStatisticsBuilder.<GeneralTelexPatUserEntity, GeneralTelexPatTrainErrorCollect>create(trainUserEntities)
            .withScoreExtractor(GeneralTelexPatUserEntity::getScore)
            .withUserIdExtractor(GeneralTelexPatUserEntity::getUserId)
            .withDeductInfoExtractor(GeneralTelexPatUserEntity::getDeductInfo)
            .withCreateTimeExtractor(GeneralTelexPatUserEntity::getCreateTime)
            .withStatusExtractor(GeneralTelexPatUserEntity::getIsFinish)
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
    for (GeneralTelexPatUserEntity trainUser : trainUserEntities) {
      if (CharSequenceUtil.isNotBlank(trainUser.getDeductInfo())) {
        GeneralTelexPatTrainErrorCollect userError = JSONUtils.fromJson(trainUser.getDeductInfo(),
            GeneralTelexPatTrainErrorCollect.class);
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
    }

    // 封装结果
    ret.setSchoolReport(builder.getSchoolReport());
    ret.setUserTendencyVO(builder.getUserTendencies());
    ret.setErrorCollect(errorCollect);
    return ret;
  }

  public Response<List<GeneralPatTrainUserDto>> getOnline(String trainId, String token) {
    GeneralTelexPatEntity train = Optional.ofNullable(trainDao.findById(trainId))
        .orElseThrow(() -> new IllegalArgumentException("未查询到训练"));
    requireMember(train, token);
    return ResponseResult.success(onlineUsers(trainId));
  }

  private List<GeneralPatTrainUserDto> onlineUsers(String trainId) {
    GeneralPatTrainRoomUserDto trainRoomUser = WebSocketGeneralTelexPatService.ROOM.get(trainId);
    if (trainRoomUser == null) {
      return new ArrayList<>();
    }
    List<GeneralPatTrainUserModelDto> joinUser = new ArrayList<>(trainRoomUser.getJoinUser());
    if (trainRoomUser.getGroupUser() != null) {
      joinUser.add(trainRoomUser.getGroupUser());
    }
    return PojoUtils.convert(joinUser, GeneralPatTrainUserDto.class);
  }

  /**
   * 学员进入拍发状态。轮次栅栏挡住陈旧页面发来的开始请求。
   */
  @Transactional
  public void startTrain(String trainId, Integer attempt, String token) {
    String userId = userService.getUserByToken(token).getId();
    GeneralTelexPatEntity train = lockedTrain(trainId);
    GeneralTelexPatUserEntity patUserEntity = student(trainId, userId);
    requireAttempt(attempt, patUserEntity);
    requireProtocol(train);
    if (Objects.equals(patUserEntity.getIsFinish(), 1)) {
      throw new TerminalStateException("已结算的训练不能重新开始");
    }
    patUserEntity.setIsFinish(2);
    trainUserDao.save(patUserEntity);
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
            int number = ThreadLocalRandom.current().nextInt(10);
            key.append(number);
          }
          break;
        case 1:
          for (int j = 0; j < 4; j++) {
            int number = ThreadLocalRandom.current().nextInt(26);
            char c = (char) (number + 65);
            key.append(c);
          }
          break;
        case 2:
          for (int j = 0; j < 4; j++) {
            int number = ThreadLocalRandom.current().nextInt(36);
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

  private List<GeneralTelexPatPageEntity> generateContentAuto(List<String> content, int generateNumber, int pageNumber,
      String trainId) {
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
   * 计算分数。
   *
   * <p>行分层：<b>原始提交行</b>（{@code sort = -1}，带 attempt/采集区间/收到时刻）只读不删，
   * 结算只重建 {@code handle} 产出的<b>分析行</b>（{@code sort >= 0}）。
   * 旧实现把该学员全部 value 行删掉后用不含采集字段的 DTO 重建，
   * finish 跑过一次原始采集时间轴就永久丢失，之后任何重算都只能报「已保存页缺少原始采集时间轴」。
   *
   * <p>逐页用时与总码率先由 {@link #deriveCapture} 从原始采集区间重算，再进入扣分。
   */
  private GeneralTelexPatUserEntity countScore(GeneralTelexPatEntity entity,
      GeneralTelexPatUserEntity kehPatUserEntity) {
    String userId = kehPatUserEntity.getUserId();
    deriveCapture(entity, kehPatUserEntity);
    TelexPatStatisticalDto ks = new TelexPatStatisticalDto();

    List<Integer> pageNumbers = trainPageDao.countPageNumber(entity.getId());
    List<TelexPatValueTransferDto> pageValueResult = new ArrayList<>();
    pageNumbers.forEach(pageNumber -> {
      List<TelexPatPageTransferDto> userPages = PojoUtils.convert(
          trainPageDao.findByTrainIdAndPageNumberOrderBySort(entity.getId(), pageNumber),
          TelexPatPageTransferDto.class);
      List<GeneralTelexPatUserValueEntity> userValue = trainUserValueDao
          .findRawByTrainIdAndPageNumberAndUserId(entity.getId(), pageNumber, userId);
      if (!userValue.isEmpty()) {
        handle(userId, pageNumber, pageValueResult, userPages, userValue.getFirst().getValue(), ks,
            pageNumber == pageNumbers.size() - 1);
      }
    });
    List<GeneralTelexPatUserValueEntity> convert = PojoUtils.convert(pageValueResult,
        GeneralTelexPatUserValueEntity.class);
    trainUserValueDao.deleteAnalysisByTrainIdAndUserId(entity.getId(), userId);
    trainUserValueDao.saveAndFlush(convert);
    PostTelexPatTrainRuleDto rule = JSONUtils.fromJson(entity.getRuleContent(), PostTelexPatTrainRuleDto.class);
    if (rule == null) {
      throw new IllegalArgumentException("评分规则未设定");
    }
    // 创建扣分信息Map
    String minus = "-";
    Map<String, Object> deductMap = new HashMap<>();
    // 结算基准是建训时冻结的规则满分，不是写死的 100
    BigDecimal score = new BigDecimal(frozenFullScore(entity));

    BigDecimal errorCodeScore = new BigDecimal(ks.getErrorCodeNumber()).multiply(rule.getOther().getErrorCode());
    deductMap.put("errorCodeNumber", ks.getErrorCodeNumber());
    deductMap.put("errorCodeScore", minus + errorCodeScore);

    BigDecimal muchLessLineScore = new BigDecimal(ks.getMuchLessLineNumber())
        .multiply(rule.getOther().getMuchLessLine());
    deductMap.put("muchLessLineNumber", ks.getMuchLessLineNumber());
    deductMap.put("muchLessLineScore", minus + muchLessLineScore);

    BigDecimal muchLessGroupsScore = new BigDecimal(ks.getMuchLessGroupsNumber())
        .multiply(rule.getOther().getMuchLessGroups());
    deductMap.put("muchLessGroupsNumber", ks.getMuchLessGroupsNumber());
    deductMap.put("muchLessGroupsScore", minus + muchLessGroupsScore);

    BigDecimal muchLessCodeScore = new BigDecimal(ks.getMuchLessCodeNumber())
        .multiply(rule.getOther().getMuchLessCode());
    deductMap.put("muchLessCodeNumber", ks.getMuchLessCodeNumber());
    deductMap.put("muchLessCodeScore", minus + muchLessCodeScore);

    BigDecimal lessReturnLineScore = new BigDecimal(ks.getLessReturnLineNumber())
        .multiply(rule.getOther().getLessReturnLine());
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

    BigDecimal correctMistakesScore = new BigDecimal(ks.getCorrectMistakesNumber())
        .multiply(rule.getOther().getAlterError());
    deductMap.put("correctMistakesNumber", ks.getCorrectMistakesNumber());
    deductMap.put("correctMistakesScore", minus + correctMistakesScore);

    // 计算正确率 （拍发总个数- 错误个数 = 正确个数） / 总个数
    BigDecimal accuracy = new BigDecimal("0");
    int errorTotal = ks.getPatGroup() - ks.getErrorCodeNumber() - ks.getMuchLessCodeNumber();
    if (errorTotal != 0) {
      // 计算正确率 （拍发总个数 - 错误个数- 多字- 少字)） /拍发总个数
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
    // 码率取服务端从采集区间重算的总码率，客户端不再上报 speed
    BigDecimal avgSpeed = Optional.ofNullable(kehPatUserEntity.getSpeed()).orElse(BigDecimal.ZERO);
    // 速率加减分：高于基准按 R 加分、低于基准按 L 扣分，走全仓唯一实现 ScoreMath.wpmScore
    int wpmBase = rule.getWpm().getBase();
    BigDecimal speedScore = ScoreMath.wpmScore(wpmBase, rule.getWpm().getR(),
        rule.getWpm().getL(), avgSpeed.intValue());
    score = score.add(speedScore);
    // 下面只负责 deductMap 的历史文本口径（等于基准不出 key、正值补 "+"、负值出绝对值），
    // 不参与算分；用方向而非 speedScore 的符号判断，是为了在系数为 0 时仍输出既有的 "+0"/"-0"。
    if (avgSpeed.intValue() > wpmBase) {
      deductMap.put("speedScore", "+" + speedScore);
    } else if (avgSpeed.intValue() < wpmBase) {
      deductMap.put("speedScore", minus + speedScore.negate());
    }
    // 有效时长与总码率已由 deriveCapture 写入成员行，这里不再累加客户端上报值
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
    return onlineUsers(trainId);
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
     * 从持久层查询用户与训练项目的关联实体
     * 该实体包含用户在特定训练项目中的角色等业务属性
     */
    GeneralTelexPatUserEntity userTrainEntity = trainUserDao.findByUserIdAndTrainId(uid, trainId);

    /*
     * 从用户服务获取最新的用户基础信息实体
     * 包含用户ID、用户名、头像等核心用户属性
     */
    UserEntity userEntity = userService.getUserByIdNew(uid);

    /*
     * 数据有效性校验
     * 确保用户基础信息和训练关联信息同时存在
     * 避免后续空指针异常和数据一致性问题
     */
    if (userEntity == null || userTrainEntity == null) {
      throw new IllegalArgumentException("训练数据异常");
    }

    // 创建数据传输对象并填充属性
    return new GeneralPatTrainUserDto(userEntity.getId(), userEntity.getUserName(), userEntity.getUserImg(),
        userTrainEntity.getRole());
  }

  /** 训练行悲观写锁：同一训练的提交/结算/改状态在这里排队，杜绝「读判定 → 写入」之间的竞态。 */
  private GeneralTelexPatEntity lockedTrain(String trainId) {
    if (CharSequenceUtil.isBlank(trainId)) {
      throw new IllegalArgumentException("训练ID不能为空");
    }
    return Optional.ofNullable(trainDao.findById(trainId, LockModeType.PESSIMISTIC_WRITE))
        .orElseThrow(() -> new IllegalArgumentException("未查询到训练"));
  }

  /** 学员成员行。非本训练学员（含教员、组训人、外人）一律 207，而不是「参数错误」。 */
  private GeneralTelexPatUserEntity student(String trainId, String userId) {
    GeneralTelexPatUserEntity member = trainUserDao.findByUserIdAndTrainId(userId, trainId);
    if (member == null || !Objects.equals(member.getRole(), 0)) {
      throw new ForbiddenException("非参训学员操作数据报组训 " + trainId);
    }
    return member;
  }

  /**
   * 结算路径专用：在训练行锁内对成员行做**当前读**。
   *
   * <p>为什么不能沿用 {@link #student}：InnoDB 的 REPEATABLE READ 事务快照在本事务的**第一次读**
   * （这里是 {@code getUserByToken}）就已建立，训练行的 {@code SELECT ... FOR UPDATE} 只保证串行化，
   * 之后对成员行的普通读仍走那个旧快照。并发两次 finish 时，后到的那次拿到锁后读到的
   * 仍是 {@code isFinish} 未置位的旧版本，于是重复结算并再发一帧结果通知
   * （实测：教员端收到两帧）。{@code refresh} 带写锁是当前读，绕过快照并锁住该行。
   */
  private GeneralTelexPatUserEntity lockedStudent(String trainId, String userId) {
    GeneralTelexPatUserEntity member = student(trainId, userId);
    trainUserDao.getEntityManager().refresh(member, LockModeType.PESSIMISTIC_WRITE);
    if (!Objects.equals(member.getRole(), 0)) {
      throw new ForbiddenException("非参训学员操作数据报组训 " + trainId);
    }
    return member;
  }

  /** 「调用者是该训练内的 role=1 组训人」。 */
  private boolean organizer(String trainId, String actorId) {
    GeneralTelexPatUserEntity member = trainUserDao.findByUserIdAndTrainId(actorId, trainId);
    return member != null && Objects.equals(member.getRole(), 1);
  }

  /**
   * 训练级读权限：本训练成员（学员或组训人）∪ 创建者 ∪ 管理员。
   *
   * <p>刻意不用写权限口径：训练详情、报底、统计、在线名单都是学员自己要看的，
   * 按写口径判会把学员整个挡在训练页外面。
   */
  private String requireMember(GeneralTelexPatEntity train, String token) {
    String actor = userService.getUserByToken(token).getId();
    if (trainUserDao.findByUserIdAndTrainId(actor, train.getId()) == null
        && !trainWriteAccess.manages(actor, train.getCreateUser(), () -> false)) {
      throw new ForbiddenException("非参训人员读取数据报组训 " + train.getId());
    }
    return actor;
  }

  /**
   * 成绩级读权限：本人 ∪ 创建者 ∪ role=1 组训人 ∪ 管理员，返回真正要读的用户 id。
   *
   * @param requestedUser 请求指定的目标用户；为空表示读自己
   */
  private String requireReadableTarget(GeneralTelexPatEntity train, String requestedUser, String token) {
    String actor = userService.getUserByToken(token).getId();
    String target = CharSequenceUtil.isBlank(requestedUser) ? actor : requestedUser;
    if (!Objects.equals(actor, target)
        && !trainWriteAccess.manages(actor, train.getCreateUser(), () -> organizer(train.getId(), actor))) {
      throw new ForbiddenException("无权读取他人拍发记录，数据报组训 " + train.getId());
    }
    return target;
  }

  private void requireProtocol(GeneralTelexPatEntity train) {
    if (!Objects.equals(train.getProtocolVersion(), CAPTURE_PROTOCOL)) {
      throw new TerminalStateException("旧训练缺少原始采集协议，请重新创建训练；历史成绩保持不变");
    }
  }

  private void requireAttempt(Integer attempt, GeneralTelexPatUserEntity member) {
    if (attempt == null || !Objects.equals(attempt, member.getAttempt())) {
      throw new TerminalStateException("训练轮次已变化，请重新读取训练");
    }
  }

  private void requirePageNumber(GeneralTelexPatEntity train, Integer pageNumber) {
    if (pageNumber == null || pageNumber < 1 || pageNumber > pageCount(train)) {
      throw new IllegalArgumentException("页码不正确");
    }
  }

  private int pageCount(GeneralTelexPatEntity train) {
    if (Objects.equals(train.getIsCable(), 1)) {
      Integer maxPage = trainPageDao.findMaxPageNumber(train.getId());
      return maxPage == null ? 0 : maxPage;
    }
    if (train.getTotalNumber() == null) {
      throw new IllegalArgumentException("训练总组数缺失，无法提交页面");
    }
    return (train.getTotalNumber() + 99) / 100;
  }

  private long elapsedMillis(LocalDateTime startedAt) {
    return startedAt == null ? 0 : Math.max(0, Duration.between(startedAt, LocalDateTime.now()).toMillis());
  }

  /**
   * 采集边界：成员行 {@code captureStartedAt} 起，到训练结束时刻（含 60 秒补交窗口）止。
   *
   * <p>组训电传没有倒计时与暂停语义（状态只有未开始/进行中/已完成），因此不引入
   * deadline / pausedAt 这类时钟列，边界只由「成员采集起点 + 训练三态」决定。
   */
  private long captureBound(GeneralTelexPatEntity train, GeneralTelexPatUserEntity member,
      LocalDateTime receivedAt) {
    if (member.getCaptureStartedAt() == null) {
      throw new IllegalStateException("采集尚未开始");
    }
    long elapsed = Math.max(0, Duration.between(member.getCaptureStartedAt(), receivedAt).toMillis());
    if (Objects.equals(train.getStatus(), PostTelegramTrainEnum.UNDERWAY.getStatus())) {
      return elapsed;
    }
    if (Objects.equals(train.getStatus(), PostTelegramTrainEnum.FINISH.getStatus()) && train.getEndTime() != null
        && receivedAt.isBefore(train.getEndTime().plusSeconds(GRACE_SECONDS))) {
      return elapsed;
    }
    throw new TerminalStateException("训练已停止接收拍发记录");
  }

  private List<CaptureInterval> intervals(String json) {
    List<CaptureInterval> result = JSONUtils.fromJson(json, new TypeToken<List<CaptureInterval>>() {
    });
    if (result == null) {
      throw new IllegalStateException("已保存页缺少原始采集时间轴");
    }
    return result;
  }

  /**
   * 从该学员全部原始提交行重算逐页用时、逐页码率、总码率与有效时长。
   *
   * <p>「页」在本域是一整页文本（一个 {@code patValue}），服务端只能按正文字符计数，
   * 口径与个人电传域共用 {@link PostTelexPatTrainService#characterCount} 这一份实现；
   * 规则单位由 {@code DatagramGardRule.vue} 固定为字符/分钟。
   */
  private void deriveCapture(GeneralTelexPatEntity train, GeneralTelexPatUserEntity member) {
    long totalMillis = 0;
    long totalCharacters = 0;
    List<Integer> times = new ArrayList<>();
    List<String> speeds = new ArrayList<>();
    List<List<CaptureInterval>> timelines = new ArrayList<>();
    for (GeneralTelexPatUserValueEntity page : trainUserValueDao.findRawByTrainIdAndUserId(train.getId(),
        member.getUserId())) {
      requireAttempt(page.getAttempt(), member);
      List<CaptureInterval> captured = intervals(page.getCaptureIntervals());
      long duration = CaptureTimeline.durationMillis(captured, captureBound(train, member, page.getReceivedAt()));
      long characters = PostTelexPatTrainService.characterCount(page.getValue(), train.getTrainType());
      if (characters > 0 && duration == 0) {
        throw new IllegalArgumentException("非空正文必须有有效采集时长");
      }
      timelines.add(captured);
      totalMillis = Math.addExact(totalMillis, duration);
      totalCharacters = Math.addExact(totalCharacters, characters);
      while (times.size() < page.getPageNumber()) {
        times.add(null);
        speeds.add(null);
      }
      times.set(page.getPageNumber() - 1, Math.toIntExact(duration / 1000));
      speeds.set(page.getPageNumber() - 1,
          TrainingRateUnit.CHARACTERS_PER_MINUTE.rate(characters, duration).toPlainString());
    }
    CaptureTimeline.requireNoOverlap(timelines);
    member.setActiveMillis(totalMillis);
    member.setValidTime(Math.toIntExact(totalMillis / 1000));
    member.setSpeed(TrainingRateUnit.CHARACTERS_PER_MINUTE.rate(totalCharacters, totalMillis));
    member.setSpeedLog(JSONUtils.toJson(speeds));
    member.setValidTimeLog(JSONUtils.toJson(times));
  }

  /** 冻结满分：建训时从评分规则复制到训练行，规则事后被改也不影响已建训练。 */
  private int frozenFullScore(GeneralTelexPatEntity train) {
    if (train.getRuleScore() == null || train.getRuleScore() <= 0) {
      throw new IllegalStateException("训练缺少有效的冻结规则满分");
    }
    return train.getRuleScore();
  }

}
