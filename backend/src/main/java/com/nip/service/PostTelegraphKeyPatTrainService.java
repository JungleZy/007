package com.nip.service;

import com.google.gson.reflect.TypeToken;
import com.nip.common.constants.PostTelegraphKeyPatTrainEnum;
import com.nip.common.utils.GlobalMessageGeneratedUtil;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.PojoUtils;
import com.nip.common.utils.ScoreMath;
import com.nip.common.utils.CaptureTimeline;
import com.nip.common.utils.ScoringRuleValidation;
import com.nip.dto.score.TrainingRateUnit;
import com.nip.dao.*;
import com.nip.dto.*;
import com.nip.dto.vo.*;
import com.nip.entity.*;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static com.nip.common.constants.BaseConstants.TRAINING_NOT_FOUND;
import static com.nip.common.constants.PostTelegraphKeyPatTrainEnum.NOT_STARTED;
import static com.nip.common.constants.PostTelegraphKeyPatTrainEnum.UNDERWAY;
import static com.nip.common.utils.KeyPatUtils.handle;

/**
 * @Author: wushilin
 * @Data: 2022-06-13 09:12
 * @Description:
 */

@ApplicationScoped
@Slf4j
public class PostTelegraphKeyPatTrainService {
  private static final Set<String> CONTROL_TOKENS = Set.of("开始", "句号", "结束", "?");

  private final UserService userService;
  private final PostTelegraphKeyPatTrainDao patTrainDao;
  private final GradingRuleDao gradingRuleDao;
  private final PostTelegraphKeyPatTrainPageDao pageDao;
  private final PostTelegraphKeyPatTrainPageValueDao valueDao;
  private final PostTelegraphKeyPatTrainMoreEntityDao moreEntityDao;
  private final CableFloorService cableFloorService;
  private final PostTelegraphKeyPatTrainRawPageDao rawPageDao;

  @Inject
  public PostTelegraphKeyPatTrainService(UserService userService,
      PostTelegraphKeyPatTrainDao patTrainDao,
      GradingRuleDao gradingRuleDao,
      PostTelegraphKeyPatTrainPageDao pageDao,
      PostTelegraphKeyPatTrainPageValueDao valueDao,
      PostTelegraphKeyPatTrainMoreEntityDao moreEntityDao,
      CableFloorService cableFloorService,
      PostTelegraphKeyPatTrainRawPageDao rawPageDao) {
    this.userService = userService;
    this.patTrainDao = patTrainDao;
    this.gradingRuleDao = gradingRuleDao;
    this.pageDao = pageDao;
    this.valueDao = valueDao;
    this.moreEntityDao = moreEntityDao;
    this.cableFloorService = cableFloorService;
    this.rawPageDao = rawPageDao;
  }

  @Transactional
  public PostTelegraphKeyPatTrainVO add(PostTelegraphKeyPatTrainDto dto, String token) {
    // Phase 7.4：isCable/totalNumber 是可空 Integer，下面 :82-:91 的裸拆箱会 NPE 成 500
    if (dto.getIsCable() == null) {
      throw new IllegalArgumentException("是否使用固定报底不能为空");
    }
    if (dto.getTotalNumber() == null) {
      throw new IllegalArgumentException("训练总组数不能为空");
    }
    UserEntity userEntity = requireUser(token);
    PostTelegraphKeyPatTrainEntity entity = PojoUtils.convertOne(dto, PostTelegraphKeyPatTrainEntity.class);
    entity.setAccuracy(0)
        .setCreateUserId(userEntity.getId())
        .setDuration("0")
        .setErrorNumber(0)
        .setStatus(NOT_STARTED.getStatus());

    // 根据id查询评分规则
    GradingRuleEntity ruleEntity = Optional.ofNullable(gradingRuleDao.findById(entity.getRuleId()))
        .orElseThrow(() -> new IllegalArgumentException("未查询到该规则"));
    ScoringRuleValidation.electronic(ruleEntity.getContent());
    if (ruleEntity.getScore() == null || ruleEntity.getScore() < 0) throw new IllegalArgumentException("评分规则满分必须为非负整数");
    entity.setProtocolVersion(1).setAttempt(0).setFullScore(new BigDecimal(ruleEntity.getScore()));
    entity.setScore(new BigDecimal(ruleEntity.getScore()));
    entity.setRuleContent(ruleEntity.getContent());
    PostTelegraphKeyPatTrainEntity save = patTrainDao.saveAndFlush(entity);
    if (Objects.equals(dto.getIsCable(), 0)) {
      Integer totalNumber = save.getTotalNumber();
      if (totalNumber > 200) {
        totalNumber = 200;
      }
      generatePatKey(totalNumber, 1, save.getId(), entity.getMessageType());
    } else {
      List<List<List<String>>> cableFloor = cableFloorService.findCableFloor(dto.getCableId(), null,
          dto.getStartPage());
      int totalPage = dto.getTotalNumber() / 100;
      if (totalPage <= 0) {
        throw new IllegalArgumentException("报文组数不足一页，无法建立训练");
      }
      if (totalPage > cableFloor.size()) {
        throw new IllegalArgumentException("所选电缆可用楼层不足");
      }
      cableFloor = cableFloor.subList(0, totalPage);
      // 使用批量保存替代循环逐条保存，提升性能
      List<PostTelegraphKeyPatTrainPageEntity> pageEntities = new ArrayList<>();
      for (int i = 0; i < cableFloor.size(); i++) {
        for (int j = 0; j < cableFloor.get(i).size(); j++) {
          PostTelegraphKeyPatTrainPageEntity pageEntity = new PostTelegraphKeyPatTrainPageEntity();
          pageEntity.setPageNumber(i + 1);
          pageEntity.setSort(j);
          pageEntity.setKey(JSONUtils.toJson(cableFloor.get(i).get(j)));
          pageEntity.setValue("[]");
          pageEntity.setTime("[]");
          pageEntity.setTrainId(save.getId());
          pageEntities.add(pageEntity);
        }
      }
      pageDao.save(pageEntities);
    }

    return toVO(save);
  }

  public List<PostTelegraphKeyPatTrainVO> listPage(String token) {
    UserEntity userEntity = requireUser(token);
    List<PostTelegraphKeyPatTrainEntity> entityList = patTrainDao.find("createUserId = ?1",
        Sort.by("createTime").descending(),
        userEntity.getId()).list();

    return PojoUtils.convert(entityList, PostTelegraphKeyPatTrainVO.class);
  }

  @Transactional
  public PostTelegraphKeyPatTrainVO begin(PostTelegraphKeyPatTrainActionDto dto, String token) {
    PostTelegraphKeyPatTrainEntity entity = owned(dto.getId(), token, true);
    requireAttempt(entity, dto.getProtocolVersion(), dto.getAttempt());
    ScoringRuleValidation.electronic(entity.getRuleContent());
    if (entity.getFullScore() == null || entity.getFullScore().signum() < 0) throw new IllegalArgumentException("训练满分快照无效，请新建训练");
    if (Objects.equals(entity.getStatus(), NOT_STARTED.getStatus())) {
      entity.setStatus(UNDERWAY.getStatus());
      entity.setBeginTime(LocalDateTime.now());
      patTrainDao.save(entity);
    } else if (!Objects.equals(entity.getStatus(), UNDERWAY.getStatus())) {
      throw new IllegalStateException("训练已结束，请重置或新建训练");
    }
    return toVO(entity);
  }

  @Transactional
  public PostTelegraphKeyPatTrainVO finish(PostTelegraphKeyPatTrainActionDto dto, String token) {
    PostTelegraphKeyPatTrainEntity entity = owned(dto.getId(), token, true);
    if (Objects.equals(entity.getProtocolVersion(), 0)
        && Objects.equals(entity.getStatus(), PostTelegraphKeyPatTrainEnum.FINISH.getStatus())) {
      return toVO(entity);
    }
    requireAttempt(entity, dto.getProtocolVersion(), dto.getAttempt());
    if (Objects.equals(entity.getStatus(), PostTelegraphKeyPatTrainEnum.FINISH.getStatus())) {
      return toVO(entity);
    }
    requireUnderway(entity);
    return toVO(countScore(entity));
  }

  @Transactional
  public PostTelegraphKeyPatTrainVO reset(PostTelegraphKeyPatTrainActionDto dto, String token) {
    PostTelegraphKeyPatTrainEntity entity = owned(dto.getId(), token, true);
    requireAttempt(entity, dto.getProtocolVersion(), dto.getAttempt());
    entity.setAttempt(Math.incrementExact(entity.getAttempt()));
    rawPageDao.delete("trainId", entity.getId());
    valueDao.deleteByTrainId(entity.getId());
    moreEntityDao.delete("trainId", entity.getId());
    entity.setStatus(NOT_STARTED.getStatus()).setBeginTime(null).setEndTime(null)
        .setDuration("0").setSpeed("0").setAccuracy(0).setErrorNumber(0)
        .setScore(entity.getFullScore()).setDeductInfo(null).setContent(null);
    patTrainDao.save(entity);
    return toVO(entity);
  }

  @Transactional
  public PostTelegraphKeyPatTrainVO details(String id, String token) {
    try {
      PostTelegraphKeyPatTrainEntity entity = owned(id, token, false);
      requireReadable(entity);
      List<Integer> pageNumber = pageDao.countPageNumber(id);
      // 查询前2页数据content
      List<PostTelegraphKeyPatTrainPageEntity> twoPage = pageDao.findTwoPage(id);
      List<PostTelegraphKeyPatTrainPageValueEntity> twoPageValue = valueDao.findTwoPage(id);
      // 统计每页拍发时长和个数
      List<PostTelegraphKeyPatTrainPageValueEntity> pageValueEntities = Objects.equals(entity.getProtocolVersion(), 0)
          ? valueDao.findByTrainIdOrderByPageNumberAscSortAsc(id) : List.of();
      Map<Integer, List<PostTelegraphKeyPatTrainPageValueEntity>> collect = pageValueEntities.stream().collect(
          Collectors.groupingBy(PostTelegraphKeyPatTrainPageValueEntity::getPageNumber));
      List<PostTelegraphKeyPatTrainPageAnalyzeVO> analyzeVOS = new ArrayList<>();
      collect.forEach((key, value) -> {
        PostTelegraphKeyPatTrainPageAnalyzeVO analyzeVO = new PostTelegraphKeyPatTrainPageAnalyzeVO();
        analyzeVO.setPageNumber(key);
        int totalTime = 0;
        int patNumber = 0;
        for (PostTelegraphKeyPatTrainPageValueEntity valueEntity : value) {
          String time = valueEntity.getTime();
          String patValue = valueEntity.getValue();
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

      if (Objects.equals(entity.getProtocolVersion(), 1)) {
        for (PostTelegraphKeyPatTrainRawPageEntity raw : rawPageDao.findPages(id)) {
          PostTelegraphKeyPatTrainPageAnalyzeVO analysis = new PostTelegraphKeyPatTrainPageAnalyzeVO();
          analysis.setPageNumber(raw.getPageNumber());
          analysis.setPatNumber(Math.toIntExact(bodyCharacters(rawValues(raw))));
          analysis.setTotalTime(captureDuration(entity, raw));
          analyzeVOS.add(analysis);
        }
      }
      return PojoUtils.convertOne(entity, PostTelegraphKeyPatTrainVO.class, (t, v) -> {
        setMetadata(t, v);
        v.setExistPage(pageNumber);
        if (Objects.equals(v.getStatus(), 2)) {
          // 在大于2页报文时，用户拍发的页数少于生成页数，需用生成的报文补足2数据
          if (twoPageValue.size() < twoPage.size()) {
            int index = twoPage.size() - twoPageValue.size();
            for (int i = 0; i < index; i++) {
              try {
                if (index + i < twoPage.size()) {
                  if (null != twoPage.get(index + i)) {
                    twoPageValue.add(
                        PojoUtils.convertOne(twoPage.get(index + i), PostTelegraphKeyPatTrainPageValueEntity.class));
                  }
                }
              } catch (Exception e) {
                log.error("details index:{},i:{}", index, i, e);
              }
            }
          }
          v.setContent(PojoUtils.convert(twoPageValue, PostTelegraphKeyPatTrainPageMessageVO.class));
        } else {
          v.setContent(PojoUtils.convert(twoPage, PostTelegraphKeyPatTrainPageMessageVO.class));
        }
        v.setPageAnalyzeVOS(analyzeVOS);
        if (null != t.getIsCable() && t.getIsCable() == 1) {
          v.setTotalNumber((int) pageDao.count("trainId", id));
          v.setPageNumber(pageDao.findMaxPageNumber(id));
        }
      });
    } catch (IllegalArgumentException | IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      log.error("查询训练详情失败，训练ID: {}", id, e);
      throw new RuntimeException(e);
    }
  }

  @Transactional
  public PostTelegraphKeyPatTrainPageVO getPage(String trainId, Integer pageNumber, String token) {
    PostTelegraphKeyPatTrainPageVO ret = new PostTelegraphKeyPatTrainPageVO();
    PostTelegraphKeyPatTrainEntity entity = owned(trainId, token, true);
    requireReadable(entity);
    requirePageNumber(entity, pageNumber);
    List<PostTelegraphKeyPatTrainPageEntity> messageVO;
    // 页码是否正确
    // Phase 7.4：pageNumber/isCable/totalNumber 均为可空 Integer，裸拆箱会 NPE
    if (pageNumber == null) {
      throw new IllegalArgumentException("页码不能为空");
    }
    int totalPage;
    int totalNumber;
    int generateNumber = 100;
    if (Objects.equals(entity.getIsCable(), 0)) {
      if (entity.getTotalNumber() == null) {
        throw new IllegalArgumentException("训练总组数缺失，无法取页");
      }
      totalNumber = entity.getTotalNumber();
      totalPage = totalNumber / 100;
      if (totalNumber % 100 > 0) {
        totalPage += 1;
      }
      if (pageNumber.compareTo(totalPage) > 0 || pageNumber < 1) {
        throw new IllegalArgumentException("页码不正确，页码需大于0且不大于" + totalPage);
      }
      if (pageNumber == totalPage) {
        generateNumber = totalNumber - ((pageNumber - 1) * 100);
      }
    }

    // 用户拍发内容
    List<PostTelegraphKeyPatTrainPageValueEntity> userPage = valueDao.find("trainId = ?1 and pageNumber = ?2",
        Sort.by("sort").ascending(), trainId,
        pageNumber).list();

    // 生成的内容
    List<PostTelegraphKeyPatTrainPageEntity> pageDaoAll = pageDao.find("trainId =?1 and pageNumber=?2 ",
        Sort.by("sort").ascending(), trainId, pageNumber).list();
    if (!pageDaoAll.isEmpty()) {
      messageVO = pageDaoAll;
    } else {
      if (Objects.equals(entity.getStatus(), PostTelegraphKeyPatTrainEnum.FINISH.getStatus())) {
        throw new IllegalStateException("已完成训练缺少该页报底");
      }
      messageVO = generatePatKey(generateNumber, pageNumber, entity.getId(), entity.getMessageType());
    }
    // 用户未拍发本页内容，则获取生成的内容
    if (userPage.isEmpty()) {
      ret.setMessageVO(PojoUtils.convert(messageVO, PostTelegraphKeyPatTrainPageMessageVO.class));
    } else {
      ret.setMessageVO(PojoUtils.convert(userPage, PostTelegraphKeyPatTrainPageMessageVO.class));
    }
    PostTelegraphKeyPatTrainRawPageEntity raw = rawPageDao.findPage(trainId, pageNumber);
    if (raw != null && !Objects.equals(entity.getStatus(), PostTelegraphKeyPatTrainEnum.FINISH.getStatus())) {
      ret.setMessageVO(rawValues(raw));
    }
    setPageMetadata(entity, raw, ret);

    // 获取解析后的内容
    List<String> resolver = pageDaoAll.stream().map(PostTelegraphKeyPatTrainPageEntity::getValue)
        .toList();
    ret.setResolverMessage(resolver);

    // 获取到本页的多组多行信息
    PostTelegraphKeyPatTrainMoreEntity trainMoreEntity = moreEntityDao.findByTrainIdAndPageNumber(trainId, pageNumber);
    if (!Objects.isNull(trainMoreEntity)) {
      String moreLine = trainMoreEntity.getMoreLine();
      String moreGroup = trainMoreEntity.getMoreGroup();
      List<PostTelegraphKeyPatResolverDetailVO> moreGroupDetail = JSONUtils.fromJson(moreGroup,
          new TypeToken<>() {
          });
      List<PostTelegraphKeyPatResolverDetailVO> moreLineDetail = JSONUtils.fromJson(moreLine,
          new TypeToken<>() {
          });
      ret.setMoreGroup(moreGroupDetail);
      ret.setMoreLine(moreLineDetail);
    } else {
      ret.setMoreLine(new ArrayList<>());
      ret.setMoreGroup(new ArrayList<>());
    }
    return ret;
  }

  @Transactional
  public Boolean delete(String trainId, String token) {
    owned(trainId, token, true);
    rawPageDao.delete("trainId", trainId);
    moreEntityDao.delete("trainId", trainId);
    pageDao.delete("trainId", trainId);
    valueDao.delete("trainId", trainId);
    return patTrainDao.deleteById(trainId);
  }

  @Transactional
  public PostTelegraphKeyPatTrainPageVO finishPage(PostTelegraphKeyPatTrainPageDto dto, String token) {
    LocalDateTime receivedAt = LocalDateTime.now();
    PostTelegraphKeyPatTrainEntity entity = owned(dto.getId(), token, true);
    requireAttempt(entity, dto.getProtocolVersion(), dto.getAttempt());
    requirePageNumber(entity, dto.getPageNumber());
    List<PostTelegraphKeyPatTrainPageMessageVO> values = canonicalValues(dto);
    String rawValue = JSONUtils.toJson(values);
    String intervals = JSONUtils.toJson(dto.getCaptureIntervals());
    PostTelegraphKeyPatTrainRawPageEntity existing = rawPageDao.findPage(dto.getId(), dto.getPageNumber());
    if (existing != null) {
      if (!Objects.equals(existing.getAttempt(), dto.getAttempt())) {
        throw new IllegalArgumentException("已保存页轮次不匹配，请重新读取训练");
      }
      if (existing.getCaptureIntervals().equals(intervals)) {
        if (existing.getValue().equals(rawValue)) {
          return getPage(dto.getId(), dto.getPageNumber(), token);
        }
        throw new IllegalStateException("同一采集时间轴内容冲突，请重新读取已保存页");
      }
      CaptureTimeline.requireExtension(intervals(existing), dto.getCaptureIntervals());
    }
    requireUnderway(entity);
    long duration = CaptureTimeline.durationMillis(dto.getCaptureIntervals(), elapsedAt(entity, receivedAt));
    if (duration == 0 && values.stream().anyMatch(value -> !stringArray(value.getValue()).isEmpty())) {
      throw new IllegalArgumentException("非空原始事件必须提供有效采集时长");
    }
    List<PostTelegraphKeyPatTrainRawPageEntity> pages = rawPageDao.findPages(dto.getId());
    List<List<CaptureInterval>> timeline = pages.stream()
        .filter(page -> !Objects.equals(page.getPageNumber(), dto.getPageNumber())).map(this::intervals)
        .collect(Collectors.toCollection(ArrayList::new));
    timeline.add(dto.getCaptureIntervals());
    CaptureTimeline.requireNoOverlap(timeline);
    // Ensure lazy source pages exist under the same parent lock before accepting raw data.
    getPage(dto.getId(), dto.getPageNumber(), token);
    PostTelegraphKeyPatTrainRawPageEntity raw = existing == null
        ? new PostTelegraphKeyPatTrainRawPageEntity() : existing;
    raw.setTrainId(entity.getId());
    raw.setPageNumber(dto.getPageNumber());
    raw.setAttempt(entity.getAttempt());
    raw.setValue(rawValue);
    raw.setCaptureIntervals(intervals);
    raw.setReceivedAt(receivedAt);
    if (existing == null) {
      rawPageDao.persist(raw);
    }
    return getPage(dto.getId(), dto.getPageNumber(), token);
  }


  /**
   * 统计分数
   *
   * @param: entity
   */
  private PostTelegraphKeyPatTrainEntity countScore(PostTelegraphKeyPatTrainEntity entity) {
    PostKeyPatTrainRuleDto rule = ScoringRuleValidation.electronic(entity.getRuleContent());
    List<PostTelegraphKeyPatTrainRawPageEntity> rawPages = rawPageDao.findPages(entity.getId());
    CaptureTimeline.requireNoOverlap(rawPages.stream().map(this::intervals).toList());
    long duration = 0;
    long characters = 0;
    for (PostTelegraphKeyPatTrainRawPageEntity raw : rawPages) {
      if (!Objects.equals(raw.getAttempt(), entity.getAttempt())) {
        throw new IllegalStateException("原始记录轮次不匹配");
      }
      duration = Math.addExact(duration, captureDuration(entity, raw));
      characters = Math.addExact(characters, bodyCharacters(rawValues(raw)));
    }
    entity.setDuration(BigDecimal.valueOf(duration, 3).stripTrailingZeros().toPlainString());
    entity.setStatus(PostTelegraphKeyPatTrainEnum.FINISH.getStatus());
    entity.setEndTime(LocalDateTime.now());

    // 积分规则
    KeyPatStatisticalDto ks = new KeyPatStatisticalDto();
    // 得到已存在的页
    List<Integer> pageNumbers = rawPages.stream().map(PostTelegraphKeyPatTrainRawPageEntity::getPageNumber).toList();
    // Build derived alignment independently; immutable raw pages remain untouched.
    List<KeyPatValueTransferDto> pageValueResult = new ArrayList<>();
    // P1-1：pageValueResult/ks 为共享可变状态，parallelStream 并发累加有竞态——改串行流
    rawPages.forEach(raw -> {
      Integer pageNumber = raw.getPageNumber();
      // 根据page获取目标数据
      List<KeyPatPageTransferDto> userPages = PojoUtils.convert(
          pageDao.findByTrainIdAndPageNumberOrderBySort(entity.getId(), pageNumber), KeyPatPageTransferDto.class);
      // 根据page获取拍发数据
      List<KeyPatValueTransferDto> userPageValues = PojoUtils.convert(
          rawValues(raw), KeyPatValueTransferDto.class);
      List<KeyPatValueTransferDto> pageResult = new ArrayList<>();
      handle(null, pageResult, userPages, userPageValues, ks);
      pageValueResult.addAll(pageResult);
    });

    List<PostTelegraphKeyPatTrainPageValueEntity> pv = PojoUtils.convert(pageValueResult,
        PostTelegraphKeyPatTrainPageValueEntity.class);
    // P2-2.3：拍发记录重建改为「先构建 + 校验，后删除 + 写入」。校验不过直接抛出，delete 绝不先发生，
    // 否则构建失败会把旧拍发记录删空（目标表实测 MyISAM，事务回滚在其上是空操作）。
    checkRebuiltPageValues(entity.getId(), pageNumbers, pv);
    valueDao.deleteByTrainId(entity.getId());
    valueDao.saveAndFlush(pv);

    // 存放扣分规则 key扣分名称，value扣分值
    Map<String, Object> deductInfo = new HashMap<>();
    // 计算少行
    // Phase 7.4：isCable/totalNumber 均为可空 Integer，三元与赋值都会拆箱 NPE
    if (!Objects.equals(entity.getIsCable(), 1) && entity.getTotalNumber() == null) {
      throw new IllegalArgumentException("训练总组数缺失，无法结算");
    }
    int totalGroups = Objects.equals(entity.getIsCable(), 1) ? (int) pageDao.count("trainId", entity.getId())
        : entity.getTotalNumber();
    int fullPages = totalGroups / 100;
    int lastPageGroups = totalGroups % 100;
    int totalPages = fullPages + (lastPageGroups > 0 ? 1 : 0);
    int missingPages = totalPages - pageNumbers.size();
    if (missingPages > 0) {
      int missingGroups = 0;
      Set<Integer> submittedPages = new HashSet<>(pageNumbers);
      for (int pageNumber = 1; pageNumber <= totalPages; pageNumber++) {
        if (!submittedPages.contains(pageNumber)) {
          missingGroups += pageNumber == totalPages && lastPageGroups > 0 ? lastPageGroups : 100;
        }
      }
      ks.setLackGroup(ks.getLackGroup() + missingGroups);
      ks.setLackLine(ks.getLackLine() + missingPages * 10);
    }

    // Divide by four in the denominator, before the sole final rate rounding.
    BigDecimal speed = TrainingRateUnit.FOUR_CHARACTER_GROUPS_PER_MINUTE.rate(characters, duration);

    entity.setSpeed(String.valueOf(speed));

    // 错误个数
    entity.setErrorNumber(ks.getError());
    // 计算正确率 （拍发总个数 - 错误个数 - 串组） / 拍发总个数（守分母，ScoreMath 统一口径）
    BigDecimal accuracy = ScoreMath.accuracy(
        (long) ks.getPatGroup() - ks.getError() - ks.getBunchGroup(), ks.getPatGroup());
    entity.setAccuracy(accuracy.doubleValue());

    // 得到要扣的分
    String minus = "-";
    BigDecimal score = Objects.requireNonNull(entity.getFullScore(), "训练缺少冻结满分");
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

    // 改错
    BigDecimal alterScore = rule.getOther().getAlterError().multiply(new BigDecimal(ks.getAlterError()));
    deductInfo.put("alterErrorNumber", ks.getAlterError());
    deductInfo.put("alterErrorScore", minus + alterScore);

    // 串组
    BigDecimal bunchGroupScore = rule.getOther().getBunchGroup().multiply(new BigDecimal(ks.getBunchGroup()));
    deductInfo.put("bunchGroupNumber", ks.getBunchGroup());
    deductInfo.put("bunchGroupScore", minus + bunchGroupScore);

    // 少间隔
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

    deductInfo.put("speedNumber", speed.toString());
    BigDecimal speedScore = ScoreMath.wpmScore(rule.getWpm().getBase(),
        rule.getWpm().getR(), rule.getWpm().getL(), speed.intValueExact());
    score = score.add(speedScore);
    deductInfo.put("speedScore", speedScore.signum() > 0 ? "+" + speedScore : speedScore.toString());

    entity.setScore(score);
    // 保存扣分详情
    entity.setDeductInfo(JSONUtils.toJson(deductInfo));

    return patTrainDao.save(entity);
  }

  /**
   * P2-2.3：校验重建后的拍发记录集合，校验通过后调用方才可以 delete + 批量 save。
   * 约束：结果非空、页号全部有值、页号集合与原有页号一致；缺页允许在结算时扣分。
   * 任一条不满足即抛 IllegalStateException，让旧拍发记录原封不动地留在库里。
   *
   * @param trainId           训练 id，写进错误信息便于定位
   * @param sourcePageNumbers 原拍发记录里出现过的页号（升序去重）
   * @param rebuilt           内存中重建出来的新拍发记录集合
   */
  private static void checkRebuiltPageValues(String trainId, List<Integer> sourcePageNumbers,
      List<PostTelegraphKeyPatTrainPageValueEntity> rebuilt) {
    if (sourcePageNumbers.isEmpty()) {
      // 原本就没有拍发记录，delete 无损，直接放行
      return;
    }
    if (rebuilt.isEmpty()) {
      throw new IllegalStateException("拍发记录重建结果为空，拒绝删除已有拍发记录，训练ID: " + trainId);
    }
    if (rebuilt.stream().anyMatch(v -> Objects.isNull(v.getPageNumber()))) {
      throw new IllegalStateException("拍发记录重建结果存在页号为空的行，拒绝删除已有拍发记录，训练ID: " + trainId);
    }
    Set<Integer> rebuiltPages = rebuilt.stream()
        .map(PostTelegraphKeyPatTrainPageValueEntity::getPageNumber)
        .collect(Collectors.toCollection(TreeSet::new));
    if (!rebuiltPages.equals(new TreeSet<>(sourcePageNumbers))) {
      throw new IllegalStateException("拍发记录重建结果页号与原记录不一致，拒绝删除已有拍发记录，训练ID: " + trainId
          + "，原页号: " + new TreeSet<>(sourcePageNumbers) + "，重建页号: " + rebuiltPages);
    }
  }

  /**
   * 生成每一页数据
   *
   * @param generateNumber 生成数量
   * @param pageNumber     页码
   * @param trainId        训练id
   * @param messageType    训练报文 0数码 1字码 2混合码
   */
  private List<PostTelegraphKeyPatTrainPageEntity> generatePatKey(Integer generateNumber, Integer pageNumber,
      String trainId, Integer messageType) {
    List<PostTelegraphKeyPatTrainPageEntity> pageEntities = new ArrayList<>();
    int totalPage = generateNumber / 100;
    if (generateNumber % 100 > 0) {
      totalPage++;
    }
    for (int i = 0; i < totalPage; i++) {
      int generate = 100;
      if (i == totalPage - 1) {
        generate = generateNumber - i * 100;
      }
      List<String> messages = switch (messageType) {
        case 0 -> GlobalMessageGeneratedUtil.generatedNumber(generate, false, true);
        case 1 -> GlobalMessageGeneratedUtil.generatedWord(generate, false, true);
        default -> GlobalMessageGeneratedUtil.generatedMingle(generate, false, true);
      };
      for (int j = 0; j < messages.size(); j++) {
        String message = messages.get(j);
        List<String> key = new ArrayList<>();
        for (int z = 0; z < message.length(); z++) {
          key.add(String.valueOf(message.charAt(z)));
        }
        PostTelegraphKeyPatTrainPageEntity pageEntity = new PostTelegraphKeyPatTrainPageEntity();
        pageEntity.setPageNumber(i + pageNumber);
        pageEntity.setSort(j);
        pageEntity.setKey(JSONUtils.toJson(key));
        pageEntity.setValue("[]");
        pageEntity.setTime("[]");
        pageEntity.setTrainId(trainId);
        pageEntities.add(pageEntity);
      }
    }
    return pageDao.save(pageEntities);
  }

  private UserEntity requireUser(String token) {
    UserEntity user = userService.getUserByToken(token);
    if (user == null) {
      throw new IllegalArgumentException("登录已失效，请重新登录");
    }
    return user;
  }

  private PostTelegraphKeyPatTrainEntity owned(String id, String token, boolean lock) {
    UserEntity user = requireUser(token);
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException(TRAINING_NOT_FOUND);
    }
    PostTelegraphKeyPatTrainEntity entity = lock
        ? patTrainDao.findById(id, LockModeType.PESSIMISTIC_WRITE) : patTrainDao.findById(id);
    if (entity == null || !Objects.equals(entity.getCreateUserId(), user.getId())) {
      throw new IllegalArgumentException(TRAINING_NOT_FOUND);
    }
    return entity;
  }

  private static void requireReadable(PostTelegraphKeyPatTrainEntity entity) {
    if (!Objects.equals(entity.getProtocolVersion(), 1)
        && !Objects.equals(entity.getStatus(), PostTelegraphKeyPatTrainEnum.FINISH.getStatus())) {
      throw new IllegalStateException("旧训练缺少采集时间轴，请终止旧训练并新建训练");
    }
  }

  private static void requireAttempt(PostTelegraphKeyPatTrainEntity entity, Integer protocolVersion, Integer attempt) {
    if (!Objects.equals(entity.getProtocolVersion(), 1)) {
      throw new IllegalStateException("旧训练缺少采集时间轴，请终止旧训练并新建训练");
    }
    if (!Objects.equals(protocolVersion, 1) || !Objects.equals(attempt, entity.getAttempt())) {
      throw new IllegalArgumentException("训练协议或轮次已失效，请重新读取训练");
    }
  }

  private static void requireUnderway(PostTelegraphKeyPatTrainEntity entity) {
    if (!Objects.equals(entity.getStatus(), UNDERWAY.getStatus()) || entity.getBeginTime() == null) {
      throw new IllegalStateException("训练尚未开始或已经结束");
    }
  }

  private void requirePageNumber(PostTelegraphKeyPatTrainEntity entity, Integer pageNumber) {
    int groups = Objects.equals(entity.getIsCable(), 1)
        ? Math.toIntExact(pageDao.count("trainId", entity.getId()))
        : Objects.requireNonNull(entity.getTotalNumber(), "训练总组数缺失");
    int pageCount = groups / 100 + (groups % 100 == 0 ? 0 : 1);
    if (pageNumber == null || pageNumber < 1 || pageNumber > pageCount) {
      throw new IllegalArgumentException("页码超出训练范围");
    }
  }


  private static long elapsedAt(PostTelegraphKeyPatTrainEntity entity, LocalDateTime at) {
    return entity.getBeginTime() == null ? -1 : Duration.between(entity.getBeginTime(), at).toMillis();
  }

  private List<CaptureInterval> intervals(PostTelegraphKeyPatTrainRawPageEntity raw) {
    return JSONUtils.fromJson(raw.getCaptureIntervals(), new TypeToken<List<CaptureInterval>>() {});
  }

  private List<PostTelegraphKeyPatTrainPageMessageVO> rawValues(PostTelegraphKeyPatTrainRawPageEntity raw) {
    return JSONUtils.fromJson(raw.getValue(), new TypeToken<List<PostTelegraphKeyPatTrainPageMessageVO>>() {});
  }

  private long captureDuration(PostTelegraphKeyPatTrainEntity entity, PostTelegraphKeyPatTrainRawPageEntity raw) {
    return CaptureTimeline.durationMillis(intervals(raw), elapsedAt(entity, raw.getReceivedAt()));
  }

  private static List<String> stringArray(String value) {
    if (value == null || !value.stripLeading().startsWith("[")) {
      throw new IllegalArgumentException("原始字段必须是JSON数组");
    }
    List<String> values = JSONUtils.fromJson(value, new TypeToken<List<String>>() {});
    if (values == null || values.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("原始数组不能包含空值");
    }
    return values;
  }

  private static List<PostTelegraphKeyPatTrainPageMessageVO> canonicalValues(PostTelegraphKeyPatTrainPageDto dto) {
    if (dto.getValue() == null) {
      throw new IllegalArgumentException("原始页内容不能为空");
    }
    List<PostTelegraphKeyPatTrainPageMessageVO> values = new ArrayList<>(dto.getValue().size());
    int previousSort = -1;
    for (PostTelegraphKeyPatTrainPageMessageVO value : dto.getValue()) {
      if (value == null || value.getSort() == null || value.getSort() <= previousSort
          || (value.getTrainId() != null && !Objects.equals(value.getTrainId(), dto.getId()))
          || (value.getPageNumber() != null && !Objects.equals(value.getPageNumber(), dto.getPageNumber()))) {
        throw new IllegalArgumentException("原始组必须有序且属于当前训练页");
      }
      List<String> body = stringArray(value.getValue());
      List<String> times = stringArray(value.getTime());
      stringArray(value.getKey());
      if (body.size() != times.size()) {
        throw new IllegalArgumentException("原始字符与事件时长数量不一致");
      }
      for (String time : times) {
        if (Long.parseLong(time) < 0) {
          throw new IllegalArgumentException("原始事件时长不能为负");
        }
      }
      PostTelegraphKeyPatTrainPageMessageVO copy = new PostTelegraphKeyPatTrainPageMessageVO();
      copy.setTrainId(dto.getId());
      copy.setPageNumber(dto.getPageNumber());
      copy.setSort(value.getSort());
      copy.setKey(value.getKey());
      copy.setValue(value.getValue());
      copy.setTime(value.getTime());
      values.add(copy);
      previousSort = value.getSort();
    }
    return values;
  }

  private static long bodyCharacters(List<PostTelegraphKeyPatTrainPageMessageVO> values) {
    long count = 0;
    for (PostTelegraphKeyPatTrainPageMessageVO group : values) {
      for (String token : stringArray(group.getValue())) {
        if (!CONTROL_TOKENS.contains(token)) {
          count += token.codePoints().filter(c -> !Character.isWhitespace(c)
              && !Character.isSpaceChar(c) && !Character.isISOControl(c)).count();
        }
      }
    }
    return count;
  }

  private PostTelegraphKeyPatTrainVO toVO(PostTelegraphKeyPatTrainEntity entity) {
    PostTelegraphKeyPatTrainVO vo = PojoUtils.convertOne(entity, PostTelegraphKeyPatTrainVO.class);
    setMetadata(entity, vo);
    return vo;
  }

  private void setMetadata(PostTelegraphKeyPatTrainEntity entity, PostTelegraphKeyPatTrainVO vo) {
    vo.setProtocolVersion(entity.getProtocolVersion());
    vo.setAttempt(entity.getAttempt());
    vo.setServerElapsedMs(Math.max(0, elapsedAt(entity, LocalDateTime.now())));
    List<PostTelegraphKeyPatTrainPageVO> pages = new ArrayList<>();
    List<Integer> pageNumbers = new ArrayList<>();
    if (Objects.equals(entity.getProtocolVersion(), 1)) {
      for (PostTelegraphKeyPatTrainRawPageEntity raw : rawPageDao.findPages(entity.getId())) {
        PostTelegraphKeyPatTrainPageVO page = new PostTelegraphKeyPatTrainPageVO();
        setPageMetadata(entity, raw, page);
        page.setMessageVO(rawValues(raw));
        pages.add(page);
        pageNumbers.add(raw.getPageNumber());
      }
    }
    vo.setSavedPages(pages);
    vo.setSavedPageNumbers(pageNumbers);
  }

  private void setPageMetadata(PostTelegraphKeyPatTrainEntity entity,
      PostTelegraphKeyPatTrainRawPageEntity raw, PostTelegraphKeyPatTrainPageVO vo) {
    vo.setProtocolVersion(entity.getProtocolVersion());
    vo.setAttempt(entity.getAttempt());
    vo.setServerElapsedMs(Math.max(0, elapsedAt(entity, LocalDateTime.now())));
    vo.setSubmitted(raw != null);
    vo.setSavedCaptureIntervals(raw == null ? List.of() : intervals(raw));
  }
}
