package com.nip.service;

import com.google.gson.reflect.TypeToken;
import com.nip.common.exception.ForbiddenException;
import com.nip.common.PageInfo;
import com.nip.common.constants.PostTelexPatTrainStatusEnum;
import com.nip.common.utils.CaptureTimeline;
import com.nip.common.utils.CheckUtils;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.Page;
import com.nip.common.utils.PojoUtils;
import com.nip.common.utils.ScoreMath;
import com.nip.common.utils.ScoringRuleValidation;
import com.nip.common.utils.TelexPatUtils;
import com.nip.dao.GradingRuleDao;
import com.nip.dao.PostTelexPatTrainDao;
import com.nip.dao.PostTelexPatTrainPageDao;
import com.nip.dao.PostTelexPatTrainPageValueDao;
import com.nip.dto.*;
import com.nip.dto.vo.PostTelexPatTrainPageInfoVO;
import com.nip.dto.score.TrainingRateUnit;
import com.nip.dto.vo.PostTelexPatTrainPageVO;
import com.nip.dto.vo.PostTelexPatTrainPageValueVO;
import com.nip.dto.vo.PostTelexPatTrainVO;
import com.nip.dto.vo.param.PostTelexPatTrainFinishParam;
import com.nip.dto.vo.param.PostTelexPatTrainParam;
import com.nip.entity.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.nip.common.constants.PostTelexPatTrainStatusEnum.NOT_STARTED;
import static com.nip.common.constants.PostTelexPatTrainStatusEnum.UNDERWAY;
import static com.nip.common.utils.GlobalMessageGeneratedUtil.bePointed;
import static com.nip.common.utils.GlobalMessageGeneratedUtil.generatedNumber;

/**
 * @Author: wushilin
 * @Data: 2022-05-06 11:14
 * @Description:
 */
@Slf4j
@ApplicationScoped
public class PostTelexPatTrainService {

  private static final Pattern PATTERN_REG_1 = Pattern.compile("^[^/]{1,3}/{1,3}");
  private static final Pattern PATTERN_REG_2 = Pattern.compile("^\\w+.*/.+");
  private static final Pattern PATTERN_REG_3 = Pattern.compile("^/+\\w+");
  private static final Pattern PATTERN_REG_4 = Pattern.compile("^\\w+/+");
  private static final Pattern PATTERN_REG_5 = Pattern.compile("^\\d{1,2}");
  private static final Pattern PATTERN_REG_6 = Pattern.compile("^\\w{4,}-\\w+/\\d+");
  private static final Pattern CAPTURE_LINES = Pattern.compile("\\r\\n|[\\r\\n]");
  private static final Pattern CAPTURE_TOKEN = Pattern.compile("[^\\s\\p{Z}\\p{Cc}]+");
  private static final Pattern PAGE_SUFFIX = Pattern.compile("(.*)-[1-9]\\d{0,2}(?:/[1-9]\\d{0,2})*");
  private static final Pattern PAGE_SELECTOR = Pattern.compile("[1-9]\\d{0,2}[Pp]");
  private static final Pattern GROUP_RANGE = Pattern.compile("[1-9]\\d{0,2}---[1-9]\\d{0,2}");

  private final PostTelexPatTrainDao postTelexPatTrainDao;
  private final PostTelexPatTrainPageDao pageDao;
  private final PostTelexPatTrainPageValueDao valueDao;
  private final UserService userService;
  private final GradingRuleDao gradingRuleDao;
  private final CableFloorService cableFloorService;

  @Inject
  public PostTelexPatTrainService(PostTelexPatTrainDao postTelexPatTrainDao,
      PostTelexPatTrainPageDao pageDao,
      PostTelexPatTrainPageValueDao valueDao,
      UserService userService, GradingRuleDao gradingRuleDao,
      CableFloorService cableFloorService) {
    this.postTelexPatTrainDao = postTelexPatTrainDao;
    this.pageDao = pageDao;
    this.valueDao = valueDao;
    this.userService = userService;
    this.gradingRuleDao = gradingRuleDao;
    this.cableFloorService = cableFloorService;
  }

  @Transactional
  public PostTelexPatTrainVO save(PostTelexPatTrainDto dto, String token) {
    // 从token中获取用户
    UserEntity userEntity = userService.getUserByToken(token);
    // 查询评分内容
    GradingRuleEntity ruleEntity = Optional.ofNullable(gradingRuleDao.findById(dto.getRuleId()))
        .orElseThrow(() -> new IllegalArgumentException("未查询到评分规则"));
    ScoringRuleValidation.telex(ruleEntity.getContent());
    if (ruleEntity.getScore() == null || ruleEntity.getScore() < 0) {
      throw new IllegalArgumentException("评分规则满分必须为非负数");
    }
    PostTelexPatTrainEntity entity = PojoUtils.convertOne(dto, PostTelexPatTrainEntity.class, (t, r) -> {
      // 设置默认值
      r.setAccuracy("0.00");
      r.setSpeed("0");
      r.setStatus(NOT_STARTED.getStatus());
      r.setProtocolVersion(1);
      r.setAttempt(0);
      r.setPauseIntervals("[]");
      r.setErrorNumber(0);
      r.setValidTime(0);
      r.setValidTimeLog(null);
      r.setSpeedLog(null);
      r.setChange(0);
      r.setCreateUser(userEntity.getId());
      r.setScore(ruleEntity.getScore() + "");
      r.setRuleContent(ruleEntity.getContent());
    });
    Integer groupNumber = entity.getGroupNumber();
    // P2-55/56：isCable/type/patType/groupNumber 均为 Integer，前置判空避免拆箱 NPE
    if (Objects.equals(entity.getIsCable(), 0)) {
      if (groupNumber == null) {
        throw new IllegalArgumentException("组数不能为空");
      }
      if (entity.getType() == null) {
        throw new IllegalArgumentException("报文类型不能为空");
      }
      if (entity.getType() == 0 && entity.getPatType() == null) {
        throw new IllegalArgumentException("拍发类型不能为空");
      }
    }
    int generateNumber = groupNumber != null && groupNumber < 200 ? groupNumber : 200;
    PostTelexPatTrainEntity save = postTelexPatTrainDao.save(entity);
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
          generateContent(entity, generateNumber, 1, save.getId());
        }
      } else {
        // 生成报文
        generateContent(entity, generateNumber, 1, save.getId());
      }
    } else {
      List<List<List<String>>> cableFloor = cableFloorService.findCableFloor(dto.getCableId(), null,
          dto.getStartPage());
      // Phase 7.4：电缆报底分支未走 :106-116 的校验，groupNumber 为空会拆箱 NPE
      if (groupNumber == null) {
        throw new IllegalArgumentException("组数不能为空");
      }
      int totalPage = groupNumber / 100;
      if (totalPage <= 0) {
        throw new IllegalArgumentException("报文组数不足一页，无法建立训练");
      }
      if (totalPage > cableFloor.size()) {
        throw new IllegalArgumentException("所选电缆可用楼层不足");
      }
      cableFloor = cableFloor.subList(0, totalPage);
      List<PostTelexPatTrainPageEntity> list = new ArrayList<>();
      int floorNumber = 1;
      for (List<List<String>> floor : cableFloor) {
        int sortIndex = 0;
        for (List<String> moresKey : floor) {
          if (moresKey == null) {
            continue;
          }
          PostTelexPatTrainPageEntity pageEntity = new PostTelexPatTrainPageEntity();
          pageEntity.setTrainId(save.getId());
          pageEntity.setKey(String.join("", moresKey));
          pageEntity.setPageNumber(floorNumber);
          pageEntity.setSort(sortIndex);
          list.add(pageEntity);
          sortIndex++;
        }
        floorNumber++;
      }
      pageDao.save(list);
    }

    return clockView(save, LocalDateTime.now());
  }

  public PageInfo<PostTelexPatTrainVO> findAll(String token, Integer trainType, Page page) {
    UserEntity userEntity = userService.getUserByToken(token);
    PageInfo<PostTelexPatTrainEntity> all = postTelexPatTrainDao.findPage((root, criteriaQuery, criteriaBuilder) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(criteriaBuilder.equal(root.get("createUser"), userEntity.getId()));
      predicates.add(criteriaBuilder.equal(root.get("trainType"), trainType));
      criteriaQuery.where(criteriaBuilder.and(predicates.toArray(new Predicate[0])));
      criteriaQuery.orderBy(criteriaBuilder.desc(root.get("createTime").as(LocalDateTime.class)));
      return criteriaQuery;
    }, page.getPage() - 1, page.getRows());
    List<PostTelexPatTrainVO> convert = PojoUtils.convert(
        all.getData(),
        PostTelexPatTrainVO.class);
    PageInfo<PostTelexPatTrainVO> pageInfo = new PageInfo<>();
    pageInfo.setCurrentPage(all.getCurrentPage());
    pageInfo.setPageSize(all.getPageSize());
    pageInfo.setTotalPage(all.getTotalPage());
    pageInfo.setTotalNumber(all.getTotalNumber());
    pageInfo.setData(convert);
    return pageInfo;
    // return postTelexPatTrainDao.findTrainList(trainType,
    // userService.getUserByToken(token).getId());
  }

  @Transactional
  public PostTelexPatTrainVO detail(PostTelexPatTrainParam param, String token) {
    PostTelexPatTrainEntity entity = requireOwnedTrain(param.getId(), token);
    requireReadable(entity);
    List<PostTelexPatTrainPageEntity> pageEntities = pageDao.findByTrainIdTop2(param.getId());
    List<PostTelexPatTrainPageValueEntity> pageValueEntities = valueDao.findByTrainIdTop2(param.getId());
    List<PostTelexPatTrainPageValueVO> pageValueVOS = PojoUtils.convert(pageValueEntities,
        PostTelexPatTrainPageValueVO.class, (source, target) -> target.setCaptureIntervals(
            source.getCaptureIntervals() == null ? null : intervals(source.getCaptureIntervals())));
    List<PostTelexPatTrainPageVO> convert = PojoUtils.convert(pageEntities, PostTelexPatTrainPageVO.class);
    PostTelexPatTrainVO result = clockView(entity, LocalDateTime.now());
    result.setExistPage(convert);
    result.setCodeAll(pageValueVOS);
    result.setPageNumber(pageDao.findMaxPageNumber(entity.getId()));
    return result;
  }

  @Transactional(rollbackOn = Exception.class)
  public PostTelexPatTrainVO begin(PostTelexPatTrainParam param, String token) {
    PostTelexPatTrainEntity entity = requireOwnedTrain(param.getId(), token);
    requireMutableAttempt(entity, param.getAttempt());
    Integer seconds = param.getCountdownSeconds();
    if (seconds != null && (seconds <= 0 || seconds > 86400)) {
      throw new IllegalArgumentException("倒计时须为1至86400秒或不启用");
    }
    LocalDateTime now = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    if (!NOT_STARTED.getStatus().equals(entity.getStatus())) {
      if (!Objects.equals(seconds, entity.getCountdownSeconds())) {
        throw new IllegalArgumentException("训练开始后不能修改倒计时");
      }
      return clockView(entity, now);
    }
    validateFrozenRule(entity);
    entity.setStatus(UNDERWAY.getStatus());
    entity.setStartTime(now);
    entity.setCountdownSeconds(seconds);
    entity.setDeadline(seconds == null ? null : now.plusSeconds(seconds));
    entity.setPausedAt(null);
    entity.setPauseIntervals("[]");
    return clockView(postTelexPatTrainDao.save(entity), now);
  }

  @Transactional(rollbackOn = Exception.class)
  public PostTelexPatTrainVO pause(PostTelexPatTrainParam param, String token) {
    PostTelexPatTrainEntity entity = requireOwnedTrain(param.getId(), token);
    requireMutableAttempt(entity, param.getAttempt());
    LocalDateTime now = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    if (PostTelexPatTrainStatusEnum.PAUSE.getStatus().equals(entity.getStatus())) {
      return clockView(entity, now);
    }
    CheckUtils.statusCheck(UNDERWAY.getStatus(), entity.getStatus(), "训练不在进行中");
    if (entity.getDeadline() != null && !now.isBefore(entity.getDeadline())) {
      throw new IllegalArgumentException("倒计时已结束，仅可补交截止前的内容");
    }
    entity.setPausedAt(now);
    entity.setStatus(PostTelexPatTrainStatusEnum.PAUSE.getStatus());
    return clockView(postTelexPatTrainDao.save(entity), now);
  }

  @Transactional(rollbackOn = Exception.class)
  public PostTelexPatTrainVO resume(PostTelexPatTrainParam param, String token) {
    PostTelexPatTrainEntity entity = requireOwnedTrain(param.getId(), token);
    requireMutableAttempt(entity, param.getAttempt());
    LocalDateTime now = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    if (UNDERWAY.getStatus().equals(entity.getStatus())) return clockView(entity, now);
    CheckUtils.statusCheck(PostTelexPatTrainStatusEnum.PAUSE.getStatus(), entity.getStatus(), "训练未暂停");
    LocalDateTime pausedAt = Objects.requireNonNull(entity.getPausedAt(), "暂停时刻缺失");
    List<CaptureInterval> pauses = new ArrayList<>(intervals(entity.getPauseIntervals()));
    long started = elapsed(entity, pausedAt);
    long ended = elapsed(entity, now);
    if (ended > started) pauses.add(new CaptureInterval(started, ended));
    entity.setPauseIntervals(JSONUtils.toJson(pauses));
    if (entity.getDeadline() != null) {
      entity.setDeadline(entity.getDeadline().plus(Duration.between(pausedAt, now)));
    }
    entity.setPausedAt(null);
    entity.setStatus(UNDERWAY.getStatus());
    return clockView(postTelexPatTrainDao.save(entity), now);
  }

  @Transactional(rollbackOn = Exception.class)
  public PostTelexPatTrainVO reset(PostTelexPatTrainParam param, String token) {
    PostTelexPatTrainEntity entity = requireOwnedTrain(param.getId(), token);
    requireMutableAttempt(entity, param.getAttempt());
    entity.setAttempt(Math.incrementExact(entity.getAttempt()));
    valueDao.delete("trainId", entity.getId());
    for (PostTelexPatTrainPageEntity page : pageDao.findByTrainIdOrderBySort(entity.getId())) {
      page.setValue(null);
    }
    entity.setStatus(NOT_STARTED.getStatus());
    entity.setStartTime(null);
    entity.setEndTime(null);
    entity.setCountdownSeconds(null);
    entity.setDeadline(null);
    entity.setPausedAt(null);
    entity.setPauseIntervals("[]");
    entity.setSpeed("0");
    entity.setTotalSpeed("0");
    entity.setValidTime(0);
    entity.setSpeedLog(null);
    entity.setValidTimeLog(null);
    entity.setAccuracy("0.00");
    entity.setErrorNumber(0);
    entity.setChange(0);
    entity.setDeductInfo(null);
    return clockView(postTelexPatTrainDao.save(entity), LocalDateTime.now());
  }

  @Transactional(rollbackOn = Exception.class)
  public PostTelexPatTrainVO finish(PostTelexPatTrainFinishParam param, String token) {
    PostTelexPatTrainEntity entity = requireOwnedTrain(param.getId(), token);
    requireReadable(entity);
    requireAttempt(entity, param.getAttempt());
    if (isFinished(entity)) return clockView(entity, LocalDateTime.now());
    return clockView(settle(entity), LocalDateTime.now());
  }

  @Transactional(value = Transactional.TxType.REQUIRES_NEW, rollbackOn = Exception.class)
  public void settleDue(String trainId) {
    PostTelexPatTrainEntity entity = postTelexPatTrainDao.findById(trainId, LockModeType.PESSIMISTIC_WRITE);
    if (entity == null || entity.getProtocolVersion() != 1 || !UNDERWAY.getStatus().equals(entity.getStatus())
        || entity.getDeadline() == null || LocalDateTime.now().isBefore(entity.getDeadline().plusSeconds(60))) return;
    settle(entity);
  }

  private PostTelexPatTrainEntity settle(PostTelexPatTrainEntity entity) {
    requireReadable(entity);
    if (!UNDERWAY.getStatus().equals(entity.getStatus())
        && !PostTelexPatTrainStatusEnum.PAUSE.getStatus().equals(entity.getStatus())) {
      throw new IllegalArgumentException("训练尚未开始，不能结算");
    }
    PostTelexPatTrainRuleDto rule = validateFrozenRule(entity);
    ensureReportFloor(entity);
    deriveCapture(entity);
    return postTelexPatTrainDao.save(countScore(entity, rule));
  }

  @Transactional
  public PostTelexPatTrainPageInfoVO getPage(String trainId, Integer pageNumber, String token) {
    PostTelexPatTrainEntity trainEntity = requireOwnedTrain(trainId, token);
    requireReadable(trainEntity);
    requirePageNumber(trainEntity, pageNumber);
    Integer groupNumber = 0;
    int generateNumber = 100;
    if (Objects.equals(trainEntity.getIsCable(), 0)) {
      groupNumber = trainEntity.getGroupNumber();
      // Phase 7.4：groupNumber 是可空 Integer，直接 /100 会拆箱 NPE
      if (groupNumber == null) {
        throw new IllegalArgumentException("训练组数缺失，无法取页");
      }
      int totalPage = groupNumber / 100;

      if (groupNumber % 100 > 0) {
        totalPage += 1;
      }
      if (totalPage < pageNumber || pageNumber < 1) {
        throw new IllegalArgumentException("页码不正确");
      }
      if (totalPage == pageNumber) {
        generateNumber = groupNumber - ((pageNumber - 1) * 100);
      }
    }

    List<PostTelexPatTrainPageEntity> pageEntities = pageDao.findByTrainIdAndPageNumberOrderBySort(trainId, pageNumber);
    if (pageEntities.isEmpty() && !isFinished(trainEntity)) {
      pageEntities = generateContent(trainEntity, generateNumber, pageNumber, trainId);
    }
    PostTelexPatTrainPageValueEntity submitted = valueDao.findByTrainIdAndPageNumber(trainId, pageNumber);
    return pageReceipt(trainEntity, pageNumber, submitted, pageEntities);
  }

  private PostTelexPatTrainPageInfoVO pageReceipt(PostTelexPatTrainEntity trainEntity, Integer pageNumber,
      PostTelexPatTrainPageValueEntity submitted, List<PostTelexPatTrainPageEntity> pageEntities) {
    PostTelexPatTrainPageInfoVO ret = new PostTelexPatTrainPageInfoVO();
    ret.setSubmitted(submitted != null);
    ret.setCodeAll(submitted == null ? "" : submitted.getPatValue());
    ret.setAttempt(trainEntity.getAttempt());
    ret.setCaptureIntervals(trainEntity.getProtocolVersion() == 0 ? null
        : submitted == null ? List.of() : intervals(submitted.getCaptureIntervals()));
    ret.setReceivedAt(submitted == null ? null : submitted.getReceivedAt());
    if (submitted != null) {
      List<Integer> times = JSONUtils.fromJson(trainEntity.getValidTimeLog(), new TypeToken<List<Integer>>() {});
      List<String> speeds = JSONUtils.fromJson(trainEntity.getSpeedLog(), new TypeToken<List<String>>() {});
      ret.setValidTime(times != null && times.size() >= pageNumber ? times.get(pageNumber - 1) : null);
      ret.setSpeed(speeds != null && speeds.size() >= pageNumber ? speeds.get(pageNumber - 1) : null);
    }
    ret.setPageVo(PojoUtils.convert(pageEntities, PostTelexPatTrainPageVO.class));
    return ret;
  }

  @Transactional(rollbackOn = Exception.class)
  public PostTelexPatTrainPageInfoVO finishPage(PostTelexPatTrainPageValueVO vo, String token) {
    LocalDateTime receivedAt = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    PostTelexPatTrainEntity entity = requireOwnedTrain(vo.getTrainId(), token);
    requireReadable(entity);
    requireAttempt(entity, vo.getAttempt());
    if (entity.getProtocolVersion() != 1) throw new IllegalArgumentException("历史训练不支持逐页采集写入");
    requirePageNumber(entity, vo.getPageNumber());
    PostTelexPatTrainPageValueEntity saved = valueDao.findByTrainIdAndPageNumber(entity.getId(), vo.getPageNumber());
    if (saved != null) {
      requireAttempt(entity, saved.getAttempt());
      if (Objects.equals(saved.getPatValue(), vo.getPatValue())
          && intervals(saved.getCaptureIntervals()).equals(vo.getCaptureIntervals())) {
        return pageReceipt(entity, vo.getPageNumber(), saved,
            pageDao.findByTrainIdAndPageNumberOrderBySort(entity.getId(), vo.getPageNumber()));
      }
    }
    requireMutableAttempt(entity, vo.getAttempt());
    if (!UNDERWAY.getStatus().equals(entity.getStatus())
        && !PostTelexPatTrainStatusEnum.PAUSE.getStatus().equals(entity.getStatus())) {
      throw new IllegalArgumentException("训练不在进行中，不能提交页面");
    }
    if (entity.getDeadline() != null && entity.getPausedAt() == null
        && !receivedAt.isBefore(entity.getDeadline().plusSeconds(60))) {
      throw new IllegalArgumentException("补交窗口已结束，不能修改页面");
    }
    if (vo.getPatValue() == null) throw new IllegalArgumentException("页面内容不能为空");
    long duration = CaptureTimeline.durationMillis(vo.getCaptureIntervals(), captureBound(entity, receivedAt));
    if (characterCount(vo.getPatValue(), entity.getTrainType()) > 0 && duration == 0) {
      throw new IllegalArgumentException("非空正文必须有有效采集时长");
    }
    List<List<CaptureInterval>> timelines = new ArrayList<>();
    timelines.add(vo.getCaptureIntervals());
    timelines.add(intervals(entity.getPauseIntervals()));
    for (PostTelexPatTrainPageValueEntity page : valueDao.findAllByTrainId(entity.getId())) {
      requireAttempt(entity, page.getAttempt());
      if (!Objects.equals(page.getPageNumber(), vo.getPageNumber())) timelines.add(intervals(page.getCaptureIntervals()));
    }
    CaptureTimeline.requireNoOverlap(timelines);
    if (saved != null) CaptureTimeline.requireExtension(intervals(saved.getCaptureIntervals()), vo.getCaptureIntervals());
    if (saved == null) {
      saved = new PostTelexPatTrainPageValueEntity();
      saved.setTrainId(entity.getId());
      saved.setPageNumber(vo.getPageNumber());
    }
    saved.setPatValue(vo.getPatValue());
    saved.setAttempt(entity.getAttempt());
    saved.setCaptureIntervals(JSONUtils.toJson(vo.getCaptureIntervals()));
    saved.setReceivedAt(receivedAt);
    valueDao.save(saved);
    deriveCapture(entity);
    postTelexPatTrainDao.save(entity);
    return getPage(entity.getId(), vo.getPageNumber(), token);
  }

  private PostTelexPatTrainRuleDto validateFrozenRule(PostTelexPatTrainEntity entity) {
    if (entity.getScore() == null || new BigDecimal(entity.getScore()).signum() < 0) {
      throw new IllegalArgumentException("训练冻结满分必须为非负数");
    }
    return ScoringRuleValidation.telex(entity.getRuleContent());
  }

  private boolean isFinished(PostTelexPatTrainEntity entity) {
    return PostTelexPatTrainStatusEnum.FINISH.getStatus().equals(entity.getStatus());
  }

  private void requireReadable(PostTelexPatTrainEntity entity) {
    if (entity.getProtocolVersion() != 1 && !isFinished(entity)) {
      throw new IllegalArgumentException("旧训练缺少原始采集时序，请终止旧训练并重新创建训练");
    }
  }

  private void requireAttempt(PostTelexPatTrainEntity entity, Integer attempt) {
    if (attempt == null || attempt != entity.getAttempt()) {
      throw new IllegalArgumentException("训练轮次已改变，请重新读取训练，旧轮次不能提交");
    }
  }

  private void requireMutableAttempt(PostTelexPatTrainEntity entity, Integer attempt) {
    requireReadable(entity);
    requireAttempt(entity, attempt);
    if (isFinished(entity)) throw new IllegalArgumentException("训练已完成，不能修改");
  }

  private long elapsed(PostTelexPatTrainEntity entity, LocalDateTime time) {
    if (entity.getStartTime() == null || time == null) {
      throw new IllegalArgumentException("训练开始或采集接收时刻缺失");
    }
    return Duration.between(entity.getStartTime(), time).toMillis();
  }

  private long captureBound(PostTelexPatTrainEntity entity, LocalDateTime receivedAt) {
    long bound = elapsed(entity, receivedAt);
    if (entity.getDeadline() != null) bound = Math.min(bound, elapsed(entity, entity.getDeadline()));
    if (entity.getPausedAt() != null) bound = Math.min(bound, elapsed(entity, entity.getPausedAt()));
    return bound;
  }

  private List<CaptureInterval> intervals(String json) {
    List<CaptureInterval> result = JSONUtils.fromJson(json, new TypeToken<List<CaptureInterval>>() {});
    if (result == null) throw new IllegalArgumentException("原始采集时间轴缺失，不能计算成绩");
    return result;
  }

  // Count the transmitted body, not the corrected final text: earlier wrong characters still
  // count, while recognized commands and their address operands do not. Incomplete commands
  // and unknown tokens remain body instead of being removed by a broad text replacement.
  static long characterCount(String text, Integer trainType) {
    if (text == null) throw new IllegalArgumentException("已保存正文缺失");
    long characters = 0;
    boolean telex = !Objects.equals(trainType, 4);
    for (String line : CAPTURE_LINES.split(text)) {
      List<String> groups = new ArrayList<>();
      var tokens = CAPTURE_TOKEN.matcher(line);
      while (tokens.find()) groups.add(tokens.group());
      if (telex && groups.size() == 2 && TelexPatUtils.isBetweenOneAndHundred(groups.getFirst())) {
        characters += bodyTokenCharacters(groups.getLast(), false);
        continue;
      }
      for (int index = 0; index < groups.size(); index++) {
        String group = groups.get(index);
        int remaining = groups.size() - index - 1;
        if (telex) {
          if ((group.equals("QTA") || group.equals("ADD")) && remaining > 0) {
            int addressed = addressedGroups(groups.get(index + 1));
            if (addressed > 0 && (group.equals("QTA") || remaining >= addressed + 1)) {
              index++;
              if (group.equals("ADD")) {
                for (int body = 0; body < addressed; body++) {
                  characters += bodyTokenCharacters(groups.get(++index), false);
                }
              }
              continue;
            }
          }
          if (remaining >= 2 && PAGE_SELECTOR.matcher(group).matches()
              && TelexPatUtils.isPageModification(group)
              && TelexPatUtils.isBetweenOneAndHundred(groups.get(index + 1))) {
            characters += bodyTokenCharacters(groups.get(index + 2), false);
            index += 2;
            continue;
          }
          if (remaining > 0 && TelexPatUtils.isBetweenOneAndTen(group)) {
            characters += bodyTokenCharacters(groups.get(++index), false);
            continue;
          }
          if (remaining > 0 && ((index > 0 && TelexPatUtils.isOnlySlashes(group))
              || TelexPatUtils.isValidFormat(group))) {
            characters += bodyTokenCharacters(group, true);
            characters += bodyTokenCharacters(groups.get(++index), false);
            continue;
          }
        }
        boolean slashControl = telex && (TelexPatUtils.containsPattern(group)
            || TelexPatUtils.hasMiddleSlash(group)
            || (index > 0 && PATTERN_REG_3.matcher(group).matches()));
        characters += bodyTokenCharacters(group, slashControl);
      }
    }
    return characters;
  }

  private static int addressedGroups(String selector) {
    if (TelexPatUtils.isBetweenOneAndHundred(selector)) return 1;
    if (GROUP_RANGE.matcher(selector).matches() && TelexPatUtils.checkHyphenPattern(selector) == 1) {
      List<Integer> bounds = TelexPatUtils.extractNumbersAroundHyphens(selector);
      return bounds.getLast() - bounds.getFirst() + 1;
    }
    return 0;
  }

  private static long bodyTokenCharacters(String token, boolean slashControl) {
    var page = PAGE_SUFFIX.matcher(token);
    int end = page.matches() ? page.end(1) : token.length();
    long characters = 0;
    for (int offset = 0; offset < end;) {
      int character = token.codePointAt(offset);
      if (!slashControl || character != '/') characters++;
      offset += Character.charCount(character);
    }
    return characters;
  }

  private void deriveCapture(PostTelexPatTrainEntity entity) {
    long totalMillis = 0;
    long totalCharacters = 0;
    List<Integer> times = new ArrayList<>();
    List<String> speeds = new ArrayList<>();
    List<List<CaptureInterval>> timelines = new ArrayList<>();
    List<CaptureInterval> pauses = intervals(entity.getPauseIntervals());
    CaptureTimeline.durationMillis(pauses, elapsed(entity, LocalDateTime.now()));
    timelines.add(pauses);
    for (PostTelexPatTrainPageValueEntity page : valueDao.findAllByTrainId(entity.getId())) {
      requireAttempt(entity, page.getAttempt());
      requirePageNumber(entity, page.getPageNumber());
      List<CaptureInterval> captured = intervals(page.getCaptureIntervals());
      long duration = CaptureTimeline.durationMillis(captured, captureBound(entity, page.getReceivedAt()));
      long characters = characterCount(page.getPatValue(), entity.getTrainType());
      if (characters > 0 && duration == 0) throw new IllegalArgumentException("非空正文必须有有效采集时长");
      timelines.add(captured);
      totalMillis = Math.addExact(totalMillis, duration);
      totalCharacters = Math.addExact(totalCharacters, characters);
      while (times.size() < page.getPageNumber()) {
        times.add(null);
        speeds.add(null);
      }
      times.set(page.getPageNumber() - 1, Math.toIntExact(duration / 1000));
      speeds.set(page.getPageNumber() - 1, TrainingRateUnit.CHARACTERS_PER_MINUTE.rate(characters, duration).toPlainString());
    }
    CaptureTimeline.requireNoOverlap(timelines);
    String rate = TrainingRateUnit.CHARACTERS_PER_MINUTE.rate(totalCharacters, totalMillis).toPlainString();
    entity.setSpeed(rate);
    entity.setTotalSpeed(rate);
    entity.setValidTime(Math.toIntExact(totalMillis / 1000));
    entity.setSpeedLog(JSONUtils.toJson(speeds));
    entity.setValidTimeLog(JSONUtils.toJson(times));
  }

  private PostTelexPatTrainVO clockView(PostTelexPatTrainEntity entity, LocalDateTime now) {
    PostTelexPatTrainVO result = PojoUtils.convertOne(entity, PostTelexPatTrainVO.class);
    result.setServerElapsedMs(entity.getStartTime() == null ? 0 : Math.max(0, elapsed(entity, now)));
    result.setRemainingMs(entity.getDeadline() == null ? null : isFinished(entity) ? 0L
        : Math.max(0, Duration.between(entity.getPausedAt() == null ? now : entity.getPausedAt(),
            entity.getDeadline()).toMillis()));
    return result;
  }

  private void ensureReportFloor(PostTelexPatTrainEntity entity) {
    if (!Objects.equals(entity.getIsCable(), 0)) return;
    if (entity.getGroupNumber() == null || entity.getGroupNumber() <= 0) {
      throw new IllegalArgumentException("训练组数缺失，不能结算");
    }
    Set<Integer> generated = new HashSet<>(pageDao.countPageNumber(entity.getId()));
    int pageCount = (entity.getGroupNumber() + 99) / 100;
    for (int number = 1; number <= pageCount; number++) {
      if (!generated.contains(number)) {
        generateContent(entity, Math.min(100, entity.getGroupNumber() - (number - 1) * 100), number, entity.getId());
      }
    }
  }

  /**
   * 训练属主判定。不存在 -> 202；身份成立但非创建者 -> 207（ForbiddenException），两者不再折叠。
   */
  private PostTelexPatTrainEntity requireOwnedTrain(String trainId, String token) {
    UserEntity user = userService.getUserByToken(token);
    PostTelexPatTrainEntity entity = Optional.ofNullable(postTelexPatTrainDao.findById(trainId, LockModeType.PESSIMISTIC_WRITE))
        .orElseThrow(() -> new IllegalArgumentException("未查询到训练信息"));
    if (user == null || !Objects.equals(user.getId(), entity.getCreateUser())) {
      throw new ForbiddenException("非创建者访问个人电传训练 " + trainId);
    }
    return entity;
  }

  private void requirePageNumber(PostTelexPatTrainEntity entity, Integer pageNumber) {
    Integer maximum = Objects.equals(entity.getIsCable(), 1)
        ? pageDao.findMaxPageNumber(entity.getId())
        : entity.getGroupNumber() == null ? null : (entity.getGroupNumber() + 99) / 100;
    if (pageNumber == null || pageNumber < 1 || maximum == null || pageNumber > maximum) {
      throw new IllegalArgumentException("页码不正确");
    }
  }

  @Transactional
  public Boolean delete(String trainId, String token) {
    requireOwnedTrain(trainId, token);
    pageDao.delete("trainId", trainId);
    valueDao.delete("trainId", trainId);
    return postTelexPatTrainDao.deleteById(trainId);
  }

  /**
   * 规整相邻的不规组：三五码 234 56789 -> 2345 6789；五三码 23456 789 -> 2345 6789。
   * 语义与 convertCodeAll 中的同名处理一致（P1-21）。
   *
   * @return 规整次数
   */
  static int normalizeAdjacentGroups(String[] groups) {
    int count = 0;
    for (int i = 0; i < groups.length; i++) {
      if (groups[i].length() == 3 && groups.length > (i + 1) && groups[i + 1].length() == 5) {
        String nextGroup = groups[i + 1];
        groups[i] = groups[i] + nextGroup.charAt(0);
        groups[i + 1] = nextGroup.substring(1);
        count++;
      } else if (groups[i].length() == 5 && groups.length > (i + 1) && groups[i + 1].length() == 3) {
        groups[i + 1] = groups[i].charAt(groups[i].length() - 1) + groups[i + 1];
        groups[i] = groups[i].substring(0, groups[i].length() - 1);
        count++;
      }
    }
    return count;
  }

  /**
   * 计算得分
   *
   * @return
   */
  private PostTelexPatTrainEntity countScore(PostTelexPatTrainEntity entity, PostTelexPatTrainRuleDto rule) {
    // Phase 7.4：trainType 是可空 Integer，裸 compareTo 会拆箱 NPE（下方 :393/:405 同一字段由本守卫覆盖）
    if (entity.getTrainType() == null) {
      throw new IllegalArgumentException("训练类型缺失，无法结算");
    }
    if (entity.getTrainType().compareTo(4) == 0) {
      // 创建扣分信息Map
      Map<String, String> deductMap = new HashMap<>();
      // 少行
      int lackLineNumber = 0;
      // 少回行
      int lackReturnLineNumber = 0;
      // 多行
      int moreLineNumber = 0;
      // 多组
      int moreGroupNumber = 0;
      // 少页标
      int lackPageMarkNumber = 0;
      // 页标错
      int pageMarkErrorNumber = 0;
      // 错组
      int errorGroupNumber = 0;
      // 多码
      int moreCodeNumber = 0;
      // 少码
      int lackCodeNumber = 0;
      // 少组
      int lackGroupNumber = 0;
      // 不规
      Integer nonStandartNumber = 0;
      // 改错次数
      int change = 0;

      List<Integer> pageNumber = pageDao.countPageNumber(entity.getId());
      List<PostTelexPatTrainPageEntity> pageEntities = pageDao.findByTrainIdOrderBySort(entity.getId());
      List<Map<String, Object>> codeAll = new ArrayList<>();
      List<String> page = new ArrayList<>();
      pageNumber.forEach(item -> {
        PostTelexPatTrainPageValueEntity pageValueEntity = valueDao.findByTrainIdAndPageNumber(entity.getId(), item);
        if (pageValueEntity != null) {
          String patValue = pageValueEntity.getPatValue();
          if (entity.getTrainType().compareTo(0) == 0) {
            codeAll.addAll(Objects
                .requireNonNull(JSONUtils.fromJson(Optional.ofNullable(patValue).orElse("[]"), new TypeToken<>() {
                })));
          } else {
            page.add(Optional.ofNullable(patValue).orElse(""));
          }
        }
        else page.add("");
      });

      // 得到 codeAll[{"text":"4","time":0}...] 用户输入的内容
      List<List<List<String>>> parseCodeAll = new ArrayList<>();
      if (entity.getTrainType().compareTo(0) == 0) {
        // 按规则解析客户输入的内容
        Map<String, Object> convertCodeAll = convertCodeAll(codeAll, entity.getTrainType());
        // 改错次数
        change = (int) convertCodeAll.get("errorNumber");
        nonStandartNumber = (Integer) convertCodeAll.get("irregularityNumber");
        parseCodeAll.addAll((List<List<List<String>>>) convertCodeAll.get("data"));
      } else {
        for (String p : page) {
          if (StringUtils.isBlank(p)) {
            parseCodeAll.add(new ArrayList<>());
            continue;
          }
          // 每行之间用\n
          String[] row = p.split("\\n");
          List<List<String>> pageList = new ArrayList<>();
          for (String s : row) {
            if (StringUtils.isBlank(s)) {
              pageList.add(new ArrayList<>());
              continue;
            }
            // 每组间用空格 分
            String[] groups = s.split(" ");
            // 处理不规（三五码/五三码 规整）
            nonStandartNumber += normalizeAdjacentGroups(groups);
            pageList.add(Arrays.stream(groups).toList());
          }
          if (!pageList.isEmpty()) {
            parseCodeAll.add(pageList);
          }
        }
      }

      // 错误次数
      int errorNumber = 0;
      // 得到 code正确的内容
      List<List<List<PostTelexPatTrainPageEntity>>> convertText = convertTextListString(pageEntities);


      BigDecimal score = new BigDecimal(entity.getScore());

      // 计算分数
      for (int i = 0; i < parseCodeAll.size(); i++) {
        // 得到页
        List<List<String>> pages = parseCodeAll.get(i);
        // 多页
        if (i > (convertText.size() - 1)) {
          // 多行
          moreLineNumber += pages.size();
          continue;
        }

        List<List<PostTelexPatTrainPageEntity>> correctPages = convertText.get(i);
        boolean lineEq = pages.size() < correctPages.size();
        for (int j = 0; j < pages.size(); j++) {
          // 得到行
          List<String> row = pages.get(j);
          // 如果此行长度是0 则判定成少行
          if (row.isEmpty()) {
            lackLineNumber += 1;
            score = score.subtract(rule.getOther().getMuchLessLine());
            continue;
          }
          // 判断多行
          if (correctPages.size() - 1 < j) {
            // 多一行扣10分
            BigDecimal muchLessLine = rule.getOther().getMuchLessLine();
            score = score.subtract(muchLessLine);
            moreLineNumber++;
            continue;
          }
          List<PostTelexPatTrainPageEntity> correctRow = correctPages.get(j);
          // 比对每一组
          for (int k = 0; k < row.size(); k++) {
            // 判断是否少回行
            if (lineEq && row.size() > 10 && k > 9 && (correctPages.get(j + 1) != null)) {
              // 匹配下一行如下一行匹配率达80%及以上，则视为少回行
              List<PostTelexPatTrainPageEntity> nextRow = correctPages.get(j + 1);
              List<String> moreRow = new ArrayList<>();
              int c = 0;
              for (int l = 0; l < row.size() - k; l++) {
                String moreGroup = row.get(l + k);
                moreRow.add(moreGroup);
                if (nextRow.size() > l) {
                  String s = Optional.ofNullable(nextRow.get(l))
                      .map(PostTelexPatTrainPageEntity::getKey)
                      .orElseGet(String::new);
                  if (Objects.equals(moreGroup, s)) {
                    c++;
                  }
                }
              }
              BigDecimal decimal = new BigDecimal(c).divide(new BigDecimal(nextRow.size()), 2, RoundingMode.HALF_DOWN)
                  .multiply(new BigDecimal(100));
              // 如果大于 将moreRow 插入到下一组中 同时也判定成少回行
              if (decimal.compareTo(new BigDecimal(80)) >= 0) {
                pages.add(j + 1, moreRow);
                // 少回行
                lackReturnLineNumber++;
              }
              continue;
            }
            String group = row.get(k);
            // 判断多组情况
            if (correctRow.size() - 1 < k) {
              score = score.subtract(rule.getOther().getMuchLessGroups());
              moreGroupNumber++;
              continue;
            }
            String correctGroup = correctRow.get(k).getKey();
            // 判断是否需要加上页码
            if ((correctPages.size() - 1 == j) && (correctRow.size() - 1 == k)) {
              correctGroup = correctGroup + "-" + (i + 1);
            }
            if (!Objects.equals(group, correctGroup)) {
              // 先排除是否少页标
              if (correctGroup.length() > 4 && correctGroup.contains("-") && group.length() == 4) {
                String t = correctGroup.split("-")[0];
                if (!Objects.equals(t, group)) {
                  // 如果除开字符串页不相同的情况需要扣除 错组35分
                  score = score.subtract(rule.getOther().getErrorCode());
                  errorGroupNumber++;
                } else {
                  // 页标10分
                  score = score.subtract(rule.getOther().getLessPage());
                  lackPageMarkNumber++;
                }
              } // 判断是否是页标记错误
              else if (correctGroup.length() > 4 && correctGroup.contains("-") && group.length() > 4
                  && group.contains("-")) {
                String ct = correctGroup.split("-")[0];
                String cp = correctGroup.split("-")[1];
                String gt = group.split("-")[0];
                String gp = group.split("-")[1];
                // 判断组和也标记是否有错误
                if (!Objects.equals(ct, gt)) {
                  // 字符串错误扣35
                  score = score.subtract(rule.getOther().getErrorCode());
                  errorGroupNumber++;
                }
                if (!Objects.equals(cp, gp)) {
                  // 页标记错误扣10分
                  score = score.subtract(rule.getOther().getErrorPage());
                  pageMarkErrorNumber++;
                }
              }
              // 多码或这少码
              else if (StringUtils.isNotEmpty(group) && group.length() != 4) {
                if (group.contains("-")) {
                  group = group.split("-")[0];
                }
                BigDecimal differ;
                if (group.length() > correctGroup.length()) {
                  differ = new BigDecimal(group.length() - correctGroup.length());
                  moreCodeNumber += differ.intValue();
                } else {
                  differ = new BigDecimal(correctGroup.length() - group.length());
                  lackCodeNumber += differ.intValue();
                }
                //
                score = score.subtract(rule.getOther().getMuchLessCode().multiply(differ));
              } else {
                score = score.subtract(rule.getOther().getErrorCode());
                errorGroupNumber++;
              }
              // 错误数量++
              errorNumber++;
            }

            // 判断是否少组
            if ((row.size() - 1 == k) && (correctRow.size() - 1 > k)) {
              score = score.subtract(
                  new BigDecimal(correctRow.size() - row.size()).multiply(rule.getOther().getMuchLessGroups()));
              lackGroupNumber += correctRow.size() - row.size();
            }
          }
        }
        // 判断是否少行
        if (pages.size() < correctPages.size()) {
          // 记录少行数
          lackLineNumber += correctPages.size() - pages.size();
          for (int k = 0; k < correctPages.size() - pages.size(); k++) {
            // 少回行扣10分
            BigDecimal lessReturnLine = rule.getOther().getMuchLessLine();
            score = score.subtract(lessReturnLine);
          }
        }
        // 判断少页
        if (i == (parseCodeAll.size() - 1) && i < convertText.size() - 1) {
          // 获取剩余页
          for (int j = (i + 1); j < convertText.size(); j++) {
            List<List<PostTelexPatTrainPageEntity>> nextPage = convertText.get(j);
            // 得到行数
            int size = nextPage.size();
            lackLineNumber += size;
            // 扣分
            score = score.subtract(rule.getOther().getMuchLessLine().multiply(new BigDecimal(size)));
          }
        }
      }
      // 计算未生成的数据，少行
      Integer groupNumber = entity.getGroupNumber();
      int totalPage = groupNumber / 100;
      if (groupNumber % 100 > 0) {
        totalPage++;
      }
      if (parseCodeAll.size() < totalPage) {
        List<Integer> totalPageNumber = new ArrayList<>();
        for (int i = 0; i < totalPage; i++) {
          totalPageNumber.add(i + 1);
        }
        // 获取用户已提交的页
        List<Integer> existPageNumber = valueDao.countPageNumber(entity.getId());
        // 移除已提交的页和已经生成的页
        totalPageNumber.removeAll(existPageNumber);
        totalPageNumber.removeAll(pageNumber);
        for (int i = 0; i < totalPageNumber.size(); i++) {
          // P2-64：末页判定按实际页号（==totalPage），不再依赖 removeAll 后残余列表的最后一个元素；
          // 末页行数按剩余组数折算（每行 10 组，向上取整），替代原「totalPage % 100」变量用错
          int missingPage = totalPageNumber.get(i);
          int missingLines;
          if (missingPage == totalPage && groupNumber % 100 > 0) {
            int lastPageGroups = groupNumber % 100;
            missingLines = lastPageGroups / 10 + (lastPageGroups % 10 > 0 ? 1 : 0);
          } else {
            missingLines = 10;
          }
          lackLineNumber += missingLines;
          score = score.subtract(
              new BigDecimal(missingLines).multiply(new BigDecimal(rule.getOther().getMuchLessLine().toString())));
        }
      }

      // 少行
      deductMap.put("lackLineNumber", String.valueOf(lackLineNumber));
      deductMap.put("lackLineScore",
          new BigDecimal(lackLineNumber).multiply(rule.getOther().getMuchLessLine()).toString());

      // 多行数量放入到deductMap中，计算多行扣除的分数
      deductMap.put("moreLineNumber", String.valueOf(moreLineNumber));
      deductMap.put("moreLineScore",
          new BigDecimal(moreLineNumber).multiply(rule.getOther().getMuchLessLine()).toString());

      // 多组放入到deductMap中，计算多组扣除的分数
      deductMap.put("moreGroupNumber", String.valueOf(moreGroupNumber));
      deductMap.put("moreGroupScore",
          new BigDecimal(moreGroupNumber).multiply(rule.getOther().getMuchLessGroups()).toString());

      // 少回行lackReturnLineNumber
      deductMap.put("lackReturnLineNumber", String.valueOf(lackReturnLineNumber));
      deductMap.put("lackReturnLineScore",
          new BigDecimal(lackReturnLineNumber).multiply(rule.getOther().getLessReturnLine()).toString());

      // 少页标 lackPageMarkNumber
      deductMap.put("lackPageMarkNumber", String.valueOf(lackPageMarkNumber));
      deductMap.put("lackPageMarkScore",
          new BigDecimal(lackPageMarkNumber).multiply(rule.getOther().getLessPage()).toString());

      // 页标错
      deductMap.put("pageMarkErrorNumber", String.valueOf(pageMarkErrorNumber));
      deductMap.put("pageMarkErrorScore",
          new BigDecimal(pageMarkErrorNumber).multiply(rule.getOther().getErrorPage()).toString());

      // 错组
      deductMap.put("errorGroupNumber", String.valueOf(errorGroupNumber));
      deductMap.put("errorGroupScore",
          new BigDecimal(errorGroupNumber).multiply(rule.getOther().getErrorCode()).toString());

      // 多码 moreCodeNumber
      deductMap.put("moreCodeNumber", String.valueOf(moreCodeNumber));
      deductMap.put("moreCodeScore",
          new BigDecimal(moreCodeNumber).multiply(rule.getOther().getMuchLessCode()).toString());

      // 少码 lackCodeNumber
      deductMap.put("lackCodeNumber", String.valueOf(lackCodeNumber));
      deductMap.put("lackCodeScore",
          new BigDecimal(lackCodeNumber).multiply(rule.getOther().getMuchLessCode()).toString());

      // 少组lackGroupNumber
      deductMap.put("lackGroupNumber", String.valueOf(lackGroupNumber));
      deductMap.put("lackGroupScore",
          new BigDecimal(lackGroupNumber).multiply(rule.getOther().getMuchLessGroups()).toString());

      // 不规
      deductMap.put("nonStandartNumber", String.valueOf(nonStandartNumber));
      deductMap.put("nonStandartScor",
          new BigDecimal(nonStandartNumber)
              .multiply(Optional.ofNullable(rule.getOther().getNonStandart()).orElseGet(() -> new BigDecimal(0)))
              .toString());

      // 改错扣分 = 改错字数 *2
      BigDecimal updateScore = rule.getOther().getCorrectMistakes().multiply(new BigDecimal(change));
      score = score.subtract(updateScore);

      // 改错
      deductMap.put("updateErrorNumber", change + "");
      deductMap.put("updateErrorScore", updateScore.toString());

      BigDecimal speed = new BigDecimal(entity.getSpeed());
      BigDecimal speedDiffer = BigDecimal.valueOf(rule.getWpm().getBase()).subtract(speed);
      BigDecimal speedAdjustment = ScoreMath.wpmScore(rule.getWpm().getBase(), rule.getWpm().getR(), rule.getWpm().getL(), speed);
      score = score.add(speedAdjustment);
      if (speedDiffer.signum() > 0) {
        // 速率扣分
        deductMap.put("speedLowNumber", speedDiffer.abs().toPlainString());
        deductMap.put("speedLowScore", speedAdjustment.negate().toString());
        deductMap.put("speedOverTopNumber", "0");
        deductMap.put("speedOverTopScore", "0");
      } else {
        // 速率超出
        deductMap.put("speedOverTopNumber", speedDiffer.abs().toPlainString());
        deductMap.put("speedOverTopScore", speedAdjustment.toString());
        deductMap.put("speedLowNumber", "0");
        deductMap.put("speedLowScore", "0");
      }
      entity.setSpeed(speed.toString());

      // 错误个数
      entity.setErrorNumber(errorNumber);
      // 计算正确率 （拍发总个数- 错误个数 = 正确个数） / 总个数
      BigDecimal correctNum = parseCodeAll.stream()
          .flatMap(Collection::stream)
          .map(List::size)
          .map(BigDecimal::new)
          .reduce(BigDecimal.ZERO, BigDecimal::add);
      if (correctNum.compareTo(new BigDecimal(0)) > 0) {
        String accuracy = correctNum.subtract(new BigDecimal(errorNumber))
            .divide(correctNum, 2, RoundingMode.HALF_DOWN)
            .multiply(new BigDecimal(100))
            .toString();
        entity.setAccuracy(accuracy);
      } else {
        entity.setAccuracy("0.0");
      }
      entity.setEndTime(LocalDateTime.now());
      entity.setStatus(PostTelexPatTrainStatusEnum.FINISH.getStatus());
      entity.setScore(score.toString());
      entity.setChange(change + nonStandartNumber);
      // 将Count修改成[key:正确内容,value:错误内容]
      for (int i = 0; i < convertText.size(); i++) {
        List<List<PostTelexPatTrainPageEntity>> pages = convertText.get(i);
        List<List<String>> pPages;
        try {
          pPages = parseCodeAll.get(i);
        } catch (Exception e) {
          pPages = new ArrayList<>();
        }
        for (int j = 0; j < pages.size(); j++) {
          List<PostTelexPatTrainPageEntity> groups = pages.get(j);
          List<String> pGroups;
          try {
            pGroups = pPages.get(j);
          } catch (Exception e) {
            pGroups = new ArrayList<>();
          }
          for (int k = 0; k < groups.size(); k++) {
            PostTelexPatTrainPageEntity pageEntity = groups.get(k);
            String gGroup;
            try {
              gGroup = pGroups.get(k);
            } catch (Exception e) {
              gGroup = "";
            }
            pageEntity.setValue(gGroup);
          }
          pageDao.save(groups);
        }
      }

      // 存入扣分信息
      String deductInfo = JSONUtils.toJson(deductMap);
      entity.setDeductInfo(deductInfo);
      return entity;
    } else {
      List<Integer> pageNumbers = pageDao.countPageNumber(entity.getId());
      TelexPatStatisticalDto ks = new TelexPatStatisticalDto();
      List<TelexPatValueTransferDto> pageValueResult = new ArrayList<>();
      List<PostTelexPatTrainPageValueEntity> trainUserValues = valueDao.findAllByTrainId(entity.getId());
      List<PostTelexPatTrainPageEntity> trainPages = pageDao.findByTrainIdOrderBySort(entity.getId());
      Map<Integer, PostTelexPatTrainPageValueEntity> valueMap = trainUserValues.stream()
          .filter(e -> Objects.nonNull(e.getPageNumber())) // 过滤掉pageNumber为null的实体
          .collect(Collectors.toMap(
              PostTelexPatTrainPageValueEntity::getPageNumber, // 使用pageNumber作为键
              e -> e, // 实体本身作为值
              (existing, replacement) -> replacement));
      Map<Integer, List<PostTelexPatTrainPageEntity>> pageMap = trainPages.stream()
          .collect(Collectors.groupingBy(PostTelexPatTrainPageEntity::getPageNumber));
      pageNumbers.forEach(pageNumber -> {
        List<TelexPatPageTransferDto> userPages = PojoUtils.convert(
            pageMap.get(pageNumber),
            TelexPatPageTransferDto.class);
        PostTelexPatTrainPageValueEntity pageValueEntity = valueMap.get(pageNumber);
        TelexPatUtils.handle(null, pageNumber, pageValueResult, userPages,
            null == pageValueEntity ? null : pageValueEntity.getPatValue(), ks, pageNumber == pageNumbers.size() - 1);
      });
      List<PostTelexPatTrainPageEntity> convert = PojoUtils.convert(pageValueResult, PostTelexPatTrainPageEntity.class);
      // P2-2.3：报底重建改为「先构建 + 校验，后删除 + 写入」。校验不过直接抛出，delete 绝不先发生，
      // 否则构建失败会把旧报底删空（目标表实测 MyISAM，事务回滚在其上是空操作）。
      checkRebuiltPages(entity.getId(), pageMap, convert);
      pageDao.deleteByTrainId(entity.getId());
      pageDao.saveAndFlush(convert);
      // 创建扣分信息Map
      String minus = "-";
      Map<String, Object> deductMap = new HashMap<>();
      BigDecimal score = new BigDecimal(entity.getScore());

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

      BigDecimal nonStandartScore = new BigDecimal(ks.getNonStandartNumber())
          .multiply(rule.getOther().getNonStandart());
      deductMap.put("nonStandartNumber", ks.getNonStandartNumber());
      deductMap.put("nonStandartScore", minus + nonStandartScore);

      BigDecimal alterError = rule.getOther().getAlterError();
      BigDecimal correctMistakesScore = new BigDecimal(ks.getCorrectMistakesNumber())
          .multiply(null == alterError ? rule.getOther().getCorrectMistakes() : alterError);
      deductMap.put("correctMistakesNumber", ks.getCorrectMistakesNumber());
      deductMap.put("correctMistakesScore", minus + correctMistakesScore);

      // 正确组数 =（拍发总组数 - 错码组 - 多少码组）；errorNumber 落库真实错误计数（P1-20）
      int correctTotal = ks.getPatGroup() - ks.getErrorCodeNumber() - ks.getMuchLessCodeNumber();
      entity.setErrorNumber(ks.getErrorCodeNumber() + ks.getMuchLessCodeNumber());
      // 计算正确率（守分母，ScoreMath 统一口径）
      BigDecimal accuracy = ScoreMath.accuracy(correctTotal, ks.getPatGroup());

      score = score.subtract(errorCodeScore)
          .subtract(muchLessLineScore)
          .subtract(muchLessGroupsScore)
          .subtract(muchLessCodeScore)
          .subtract(lessReturnLineScore)
          .subtract(lessPageScore)
          .subtract(errorPageScore)
          .subtract(nonStandartScore)
          .subtract(correctMistakesScore);
      BigDecimal avgSpeed = new BigDecimal(entity.getSpeed());
      BigDecimal speedScore = ScoreMath.wpmScore(rule.getWpm().getBase(), rule.getWpm().getR(), rule.getWpm().getL(), avgSpeed);
      score = score.add(speedScore);
      int speedComparison = avgSpeed.compareTo(BigDecimal.valueOf(rule.getWpm().getBase()));
      if (speedComparison > 0) {
        deductMap.put("speedScore", "+" + speedScore);
      } else if (speedComparison < 0) {
        deductMap.put("speedScore", minus + speedScore.abs());
      }
      entity.setSpeed(String.valueOf(avgSpeed));
      entity.setScore(String.valueOf(score));
      entity.setAccuracy(String.valueOf(accuracy));
      entity.setDeductInfo(JSONUtils.toJson(deductMap));
      entity.setEndTime(LocalDateTime.now());
      entity.setStatus(PostTelexPatTrainStatusEnum.FINISH.getStatus());
      return entity;
    }
  }

  /**
   * P2-2.3：校验重建后的报底集合，校验通过后调用方才可以 delete + 批量 save。
   * 约束：结果非空、页号全部有值、页号集合与原报底一致且连续、每页行数不少于原报底行数。
   * 任一条不满足即抛 IllegalStateException，让旧报底原封不动地留在库里。
   *
   * @param trainId     训练 id，写进错误信息便于定位
   * @param sourcePages 原报底按页号分组的结果
   * @param rebuilt     内存中重建出来的新报底集合
   */
  private static void checkRebuiltPages(String trainId,
      Map<Integer, List<PostTelexPatTrainPageEntity>> sourcePages,
      List<PostTelexPatTrainPageEntity> rebuilt) {
    if (sourcePages.isEmpty()) {
      // 原本就没有报底，delete 无损，直接放行
      return;
    }
    if (rebuilt.isEmpty()) {
      throw new IllegalStateException("报底重建结果为空，拒绝删除已有报底，训练ID: " + trainId);
    }
    if (rebuilt.stream().anyMatch(p -> Objects.isNull(p.getPageNumber()))) {
      throw new IllegalStateException("报底重建结果存在页号为空的行，拒绝删除已有报底，训练ID: " + trainId);
    }
    Map<Integer, Long> rebuiltPerPage = rebuilt.stream().collect(
        Collectors.groupingBy(PostTelexPatTrainPageEntity::getPageNumber, TreeMap::new, Collectors.counting()));
    if (!rebuiltPerPage.keySet().equals(new TreeSet<>(sourcePages.keySet()))) {
      throw new IllegalStateException("报底重建结果页号与原报底不一致，拒绝删除已有报底，训练ID: " + trainId
          + "，原页号: " + new TreeSet<>(sourcePages.keySet()) + "，重建页号: " + rebuiltPerPage.keySet());
    }
    int min = Collections.min(rebuiltPerPage.keySet());
    int max = Collections.max(rebuiltPerPage.keySet());
    if (max - min + 1 != rebuiltPerPage.size()) {
      throw new IllegalStateException("报底页号不连续，拒绝删除已有报底，训练ID: " + trainId
          + "，页号: " + rebuiltPerPage.keySet());
    }
    for (Map.Entry<Integer, Long> e : rebuiltPerPage.entrySet()) {
      int expected = sourcePages.get(e.getKey()).size();
      if (e.getValue() < expected) {
        throw new IllegalStateException("报底第 " + e.getKey() + " 页重建后行数变少（" + e.getValue()
            + " < " + expected + "），拒绝删除已有报底，训练ID: " + trainId);
      }
    }
  }

  private static Map<String, Object> convertCodeAll(List<Map<String, Object>> codeAll, Integer trainType) {
    try {
      Map<String, Object> ret = new HashMap<>();
      StringBuilder builder = new StringBuilder();
      // 将所有的字符串都拼接起来
      codeAll.stream().map(m -> m.get("text")).forEach(builder::append);

      String temp = builder.toString();

      // 按AltEnterEnter分页
      String[] page = temp.split("EnterEnter");

      int errorNumber = 0;

      // 不规次数
      int irregularityCount = 0;

      // 全部解析后的数据
      List<List<List<String>>> totalPageData = new ArrayList<>();
      // 将每页按AltEnter换行
      for (String item : page) {
        List<List<String>> pageData = new ArrayList<>();

        // 得到了行
        String[] row = item.split("Enter");

        for (int j = 0; j < row.length; j++) {
          String line = row[j];

          // 再将line按空格拆分,得到了每一组
          String[] groups = line.split(" ");

          // 然后便利每一组进行解析
          List<String> rowList = new ArrayList<>();

          for (int z = 0; z < groups.length; z++) {
            String group = groups[z];
            if (group.isEmpty()) {
              continue;
            }
            // 电传拍发
            if (trainType.compareTo(0) == 0) {
              // 立即修改 5/// 1234 ，12// 1234，123/ 1234
              if (PATTERN_REG_1.matcher(group).matches() && group.length() == 4) {
                // 如果长度不大于4 则拿下一组，循环变量+1 防止再次进入解析
                int handle = handle(rowList, PATTERN_REG_1, groups, z, 1);
                z = z + handle;
                // 错误数+1
                errorNumber = errorNumber + handle;
              }
              // 立即修改 1234/4321
              else if (PATTERN_REG_2.matcher(group).matches()) {
                String[] split = group.split("/");
                if (split.length > 0) {
                  rowList.add(split.length == 1 ? "" : split[1]);
                }
                // 错误数+1
                errorNumber++;
              }
              // 立即修改：1233 //// 1234
              else if (z != 0 && Objects.equals(group, "////")) {
                // 将下一个元素放入到集合中
                if (!rowList.isEmpty()) {
                  rowList.set(rowList.size() - 1, groups[z + 1]);
                  // 让z++防止下一个元素在判断
                }
                z++;
                errorNumber++;
              }
              // 不规 2345 /4567
              else if (PATTERN_REG_3.matcher(group).matches()) {
                if (!rowList.isEmpty()) {
                  // ["/","4567"]
                  String[] split = group.split("/");
                  // 4567
                  String s = split[split.length - 1];
                  rowList.set(rowList.size() - 1, s);
                  irregularityCount++;
                }
                errorNumber++;
              }
              // 不规 2// 1234 ,2/ 1234 23456/ 2345
              else if (PATTERN_REG_4.matcher(group).matches() && group.length() != 4) {
                if (groups.length - 1 >= z + 1) {
                  rowList.add(groups[z + 1]);
                  irregularityCount++;
                  errorNumber++;
                  z++;
                } else {
                  rowList.add(group);
                }
              }
              // 不规 1234 / 2345
              else if (Objects.equals("/", group)) {
                if (groups.length - 1 >= z + 1 && !rowList.isEmpty()) {
                  rowList.set(rowList.size() - 1, groups[z + 1]);
                  irregularityCount++;
                  errorNumber++;
                  z++;
                } else {
                  rowList.add(group);
                }
              }
              // 三五码 234 56789 => 2345 6789
              else if (group.length() == 3 && z + 1 < groups.length && groups[z + 1].length() == 5) {
                // 将下一组的第一位放到此组的最后一位
                String nextGroup = groups[z + 1];
                groups[z + 1] = nextGroup.substring(1);
                rowList.add(group + nextGroup.charAt(0));
                irregularityCount++;
              }
              // 五三码 23456 789 => 2345 6789
              else if (group.length() == 5 && (groups.length - 1 > z + 1) && (groups[z + 1].length() == 3)) {
                groups[z + 1] = group.charAt(group.length() - 1) + groups[z + 1];
                rowList.add(group.substring(0, group.length() - 1));
                irregularityCount++;
              }
              // 行尾修改
              else if (rowList.size() >= 10 && PATTERN_REG_5.matcher(group).matches()) {
                if (z + 1 < groups.length) {
                  int index = Integer.parseInt(group);
                  if (rowList.size() > index) {
                    rowList.set(index - 1, groups[z + 1]);
                  }
                  z++;
                  errorNumber++;
                } else {
                  rowList.add(group); // 安全兜底
                }
              }
              // 页尾修改 上一行 最后一组有了页标记
              else if (z == 0 && PATTERN_REG_5.matcher(group).matches()) {
                // 得到上一行的内容
                if (j > 0) {
                  // 得到上一行
                  List<String> lastRow = pageData.get(j - (1 + j - pageData.size()));
                  // 得到最后一组
                  String lastGroup = lastRow.getLast();
                  // 判断最后一组是否是包含"-"
                  if (lastGroup.contains("-")) {
                    Integer index = Integer.valueOf(group);
                    int rowNum;
                    int groupNum;
                    int rowNum1 = Integer.parseInt((index + "").charAt(0) + "") - 1;
                    if (index >= 10) {
                      rowNum = rowNum1;
                      groupNum = Integer.parseInt((index + "").charAt(1) + "") - 1;
                      pageData.get(rowNum).set(groupNum, groups[z + 1]);
                    } else {
                      groupNum = rowNum1;
                      rowList.set(groupNum, groups[z + 1]);
                    }
                    // 获取内容并替换
                    z++;
                    // 改错+1
                    errorNumber++;
                  } else {
                    rowList.add(group);
                  }
                }
                // 开始就第一行则将正常让如到集合中
                else {
                  rowList.add(group);
                }
              }
              // qta add 开头 多行少行的处理和多组少组处理
              else if (Objects.equals(group, "QTA") || Objects.equals(group, "ADD")) {
                try {
                  // 通过页标记来判断是否是行尾取消还是页尾取消
                  AtomicBoolean pageMark = new AtomicBoolean(false);
                  pageData.stream().flatMap(Collection::stream).forEach(str -> {
                    if (str.contains("-") && str.length() > 4) {
                      pageMark.set(true);
                    }
                  });
                  rowList.forEach(str -> {
                    if (str.contains("-") && str.length() > 4) {
                      pageMark.set(true);
                    }
                  });
                  // 得到下一个元素
                  String next = groups[z + 1];
                  if (pageMark.get()) {
                    // 这里需要判断是否是 多行少行的处理 qta 2---4 add 3---5 1111 1111 1111
                    if (Pattern.matches("^\\d{1,}-{3}\\d{1,}", next)) {
                      String[] split = next.split("---");
                      // 计算出开始索引和结束索引
                      String startStr = split[0];
                      String endStr = split[1];
                      int startIndex = Integer.parseInt(startStr) - 1;
                      int endIndex = Integer.parseInt(endStr) - 1;

                      int startRow = (startIndex % 100) / 10;
                      int startColumn = startIndex % 10;

                      int endRow = (endIndex % 100) / 10;
                      int endColumn = endIndex % 10;

                      // 判断是否在同一行中
                      if (startRow != endRow) {
                        throw new IllegalArgumentException("多少行处理，已超出一行");
                      }
                      // 得到要操作的行
                      List<String> rowData = pageData.get(startRow);

                      // 删除
                      if (Objects.equals(group, "QTA")) {
                        List<String> data = new ArrayList<>();
                        int tempIndex = -1;
                        for (int k = 0; (data.size() - 1) < endIndex; k++) {
                          data.addAll(pageData.get(k));
                          tempIndex++;
                        }
                        // 得到要删除的行
                        List<String> deleteRow = pageData.get(tempIndex);
                        // 删除 startColumn - endColumn
                        for (int k = 0; k <= endColumn - startColumn; k++) {
                          String value = data.get(startIndex + k);
                          deleteRow.remove(value);
                          errorNumber++;
                        }
                        // 删除完之后判断这行是否还有数据，如果没有则移除
                        if (deleteRow.isEmpty()) {
                          pageData.remove(deleteRow);
                        }
                        z++;
                      } else {
                        // 得到添加的内容
                        int mark = 1;
                        for (int k = 0; k <= endColumn - startColumn; k++) {
                          String data = groups[z + 1 + mark];
                          rowData.add(startColumn + k, data);
                          mark++;
                          errorNumber++;
                        }
                        z = z + mark + 1;
                      }
                    }
                    // 页尾添加或删除
                    else {
                      int index = Integer.parseInt(next) - 1;
                      int rowNum = (index % 100) / 10;
                      int groupNum = (index % 10);
                      // 如果有页标则说明是页尾修改或添加
                      if (Objects.equals(group, "QTA")) {
                        pageData.get(rowNum).remove(groupNum);
                        errorNumber++;
                        z++;
                      } else {
                        // 得到后面第2个元素
                        String data = groups[z + 2];
                        // 添加
                        pageData.get(rowNum).add(groupNum, data);
                        z = z + 2;
                        errorNumber++;
                      }
                    }

                  } else {
                    int index = Integer.parseInt(next);
                    // 如果没有页标则说是行尾删除或添加
                    if (Objects.equals(group, "QTA")) {
                      rowList.remove(index - 1);
                      z++;
                      errorNumber++;
                    } else {
                      // 得到添加的内容
                      String data = groups[z + 2];
                      rowList.add(index - 1, data);
                      z = z + 2;
                      errorNumber++;
                    }
                  }
                  // 判断是
                } catch (Exception e) {
                  // 若出现异常则添加到集合中
                  log.error("解析ADD或QTA 失败", e);
                  rowList.add(group);
                }
              }
              // 标错页修改 1234 .... 7890-2/1 表示将第二页修改成第一页
              else if (PATTERN_REG_6.matcher(group).matches()) {
                // 7890-2/1
                try {
                  String[] split = group.split("/");
                  String pageNumber = split[1];
                  String pageMark = split[0];
                  // 得到"7890-" 这部分
                  String substring = pageMark.substring(0, pageMark.indexOf("-") + 1);
                  // 将修改后的页标放入到元素中，错误+1
                  rowList.add(substring + pageNumber);
                  errorNumber++;
                } catch (Exception e) {
                  log.error("解析标错页修改错误,原字符串内容：{}", group);
                  rowList.add(group);
                }
              }
              // 隔页修改 1P 1 4321
              else if (countString(group, "P") == 1 && groups.length == 3 && group.endsWith("P")) {
                try {
                  // 得到P前面的内容
                  String pMark = group.substring(0, group.lastIndexOf("P"));
                  int pageNum = Integer.parseInt(pMark) - 1;
                  // 得到组号
                  int index = Integer.parseInt(groups[z + 1]) - 1;
                  int rowNum = (index % 100) / 10;
                  int groupNum = (index % 10);
                  // 得到修改内容,并修改
                  String data = groups[z + 2];
                  totalPageData.get(pageNum).get(rowNum).set(groupNum, data);
                  errorNumber++;
                  break;
                } catch (Exception e) {
                  log.error("解析隔页修改出错，原内容是：{}", group);
                }
              }
              // 如果都不符合上面的条件，则视为正常内容，放入到这行中
              else {
                rowList.add(group);
              }
            }
            // 如果是数据报拍发，只有三五码
            else {
              if (group.length() == 3 && z + 1 < groups.length && groups[z + 1].length() == 5) {
                // 将下一组的第一位放到此组的最后一位
                String nextGroup = groups[z + 1];
                groups[z + 1] = nextGroup.substring(1);
                rowList.add(group + nextGroup.charAt(0));
                irregularityCount++;
              }
              // 五三码 23456 789 => 2345 6789
              else if (group.length() == 5 && (groups.length - 1 > z + 1) && (groups[z + 1].length() == 3)) {
                groups[z + 1] = group.charAt(group.length() - 1) + groups[z + 1];
                rowList.add(group.substring(0, group.length() - 1));
                irregularityCount++;
              }
              // 如果都不符合上面的条件，则视为正常内容，放入到这行中
              else {
                rowList.add(group);
              }
            }
          }
          if (!rowList.isEmpty()) {
            pageData.add(rowList);
          }
        }
        // 封装整页内容
        totalPageData.add(pageData);
      }

      ret.put("data", totalPageData);
      ret.put("errorNumber", errorNumber);
      ret.put("irregularityNumber", irregularityCount);

      return ret;
    } catch (ForbiddenException | IllegalArgumentException | IllegalStateException e) {
      // 同上：授权拒绝不得被兜底降级为 500。
      throw e;
    } catch (Exception e) {
      log.error("解析报文出错", e);
      throw new RuntimeException(e);
    }
  }

  private static int handle(List<String> rowList, Pattern reg, String[] groups, int z, int cumsum) {
    if (z + cumsum >= groups.length) {
      rowList.add("");
      return cumsum;
    }
    String str = groups[z + cumsum];
    if (reg.matcher(str).matches()) {
      return handle(rowList, reg, groups, z, cumsum + 1);
    }
    rowList.add(str);
    return cumsum;
  }

  /**
   * 统计字符串出现的个数
   *
   * @param: str
   * @param: target
   */
  private static int countString(String str, String target) {
    int count = 0;
    while (str.contains(target)) {
      str = str.substring(str.indexOf(target) + 1);
      count++;
    }
    return count;
  }

  /**
   * 将text 格式转为页 行 组 格式
   * [
   * [ 页
   * ["",""], //行
   * ["",""]
   * ],
   * [
   * ["",""],
   * ["",""]
   * ]
   * ]
   **/
  private List<List<List<PostTelexPatTrainPageEntity>>> convertTextListString(List<PostTelexPatTrainPageEntity> text) {
    // 转成页 行 组 格式
    List<List<List<PostTelexPatTrainPageEntity>>> ret = new ArrayList<>();
    List<List<PostTelexPatTrainPageEntity>> page = new ArrayList<>();
    List<PostTelexPatTrainPageEntity> group = new ArrayList<>();
    for (int i = 0; i < text.size(); i++) {
      PostTelexPatTrainPageEntity code = text.get(i);
      group.add(code);
      if (group.size() == 10) {
        page.add(group);
        group = new ArrayList<>();
        if (page.size() == 10) {
          ret.add(page);
          page = new ArrayList<>();
        }
      }
      if (i == text.size() - 1) {
        if (!group.isEmpty()) {
          page.add(group);
        }
        if (!page.isEmpty()) {
          ret.add(page);
        }
      }
    }

    return ret;
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
  private List<PostTelexPatTrainPageEntity> generateContent(
      PostTelexPatTrainEntity entity, int generateNumber, int pageNumber,
      String trainId) {
    List<PostTelexPatTrainPageEntity> pageEntities = new ArrayList<>();
    // Phase 7.4：type 是可空 Integer，下面的 == 与 switch 都会拆箱 NPE
    Integer type = entity.getType();
    if (type == null) {
      throw new IllegalArgumentException("报文类型缺失，无法生成报文");
    }
    if (type == 0) {
      if (entity.getPatType() != null) {
        if (entity.getPatType() == 0) {
          List<String> content = bePointed(generateNumber);
          return generateContentAuto(content, content.size(), pageNumber, trainId);
        } else if (entity.getPatType() == 1) {
          List<String> content = generatedNumber(generateNumber, true, true);
          return generateContentAuto(content, content.size(), pageNumber, trainId);
        }
      }
    }
    Deque<String> recentGroups = new ArrayDeque<>(10);
    Set<String> recentGroupSet = new HashSet<>();
    for (int i = 0; i < generateNumber; i++) {
      String key;
      switch (entity.getType()) {
        case 0:
          key = generateUniqueNumbers();
          break;
        case 1:
          key = generateUniqueLetters();
          break;
        case 2:
          key = generateUniqueMixed();
          break;
        default:
          throw new IllegalArgumentException("未知数据类型");
      }
      key = ensureUniqueGroup(key, recentGroups, recentGroupSet, entity.getType());
      if (i % 100 == 0 && i != 0) {
        pageNumber += 1;
      }
      PostTelexPatTrainPageEntity pageEntity = new PostTelexPatTrainPageEntity();
      pageEntity.setTrainId(trainId);
      pageEntity.setKey(key);
      pageEntity.setPageNumber(pageNumber);
      pageEntity.setSort(i % 100);
      pageEntities.add(pageEntity);
      updateRecentGroups(recentGroups, recentGroupSet, key);
    }
    return pageDao.save(pageEntities);
  }

  private List<PostTelexPatTrainPageEntity> generateContentAuto(List<String> content, int generateNumber,
      int pageNumber, String trainId) {
    List<PostTelexPatTrainPageEntity> pageEntities = new ArrayList<>();
    Deque<String> recentGroups = new ArrayDeque<>(10);
    Set<String> recentGroupSet = new HashSet<>();
    for (int i = 0; i < generateNumber; i++) {
      if (i % 100 == 0 && i != 0) {
        pageNumber += 1;
      }
      String key = ensureUniqueGroup(content.get(i), recentGroups, recentGroupSet, null);
      PostTelexPatTrainPageEntity pageEntity = new PostTelexPatTrainPageEntity();
      pageEntity.setTrainId(trainId);
      pageEntity.setKey(key);
      pageEntity.setPageNumber(pageNumber);
      pageEntity.setSort(i % 100);
      pageEntities.add(pageEntity);
      updateRecentGroups(recentGroups, recentGroupSet, key);
    }

    return pageDao.save(pageEntities);
  }

  private static String generateUniqueNumbers() {
    List<Integer> pool = new ArrayList<>(10);
    for (int i = 0; i < 10; i++) {
      pool.add(i);
    }
    Collections.shuffle(pool, ThreadLocalRandom.current());
    StringBuilder body = new StringBuilder();
    for (int i = 0; i < 4; i++) {
      body.append(pool.get(i));
    }
    return body.toString();
  }

  private static String generateUniqueLetters() {
    List<Character> pool = new ArrayList<>(26);
    for (int i = 0; i < 26; i++) {
      pool.add((char) (i + 65));
    }
    Collections.shuffle(pool, ThreadLocalRandom.current());
    StringBuilder body = new StringBuilder();
    for (int i = 0; i < 4; i++) {
      body.append(pool.get(i));
    }
    return body.toString();
  }

  private static String generateUniqueMixed() {
    List<Character> pool = new ArrayList<>(36);
    for (int i = 0; i < 36; i++) {
      if (i < 10) {
        pool.add((char) (i + 48));
      } else {
        pool.add((char) (i + 55));
      }
    }
    Collections.shuffle(pool, ThreadLocalRandom.current());
    StringBuilder body = new StringBuilder();
    for (int i = 0; i < 4; i++) {
      body.append(pool.get(i));
    }
    return body.toString();
  }

  private static String ensureUniqueGroup(String candidate, Deque<String> recent, Set<String> recentSet, Integer type) {
    String value = candidate;
    int attempts = 0;
    while (recentSet.contains(value) && attempts < 20) {
      if (type == null) {
        break;
      }
      switch (type) {
        case 0 -> value = generateUniqueNumbers();
        case 1 -> value = generateUniqueLetters();
        case 2 -> value = generateUniqueMixed();
        default -> {
          return value;
        }
      }
      attempts++;
    }
    return value;
  }

  private static void updateRecentGroups(Deque<String> recent, Set<String> recentSet, String key) {
    recent.addLast(key);
    recentSet.add(key);
    if (recent.size() > 10) {
      String removed = recent.removeFirst();
      recentSet.remove(removed);
    }
  }
}
