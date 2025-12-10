package com.nip.service;

import com.google.gson.reflect.TypeToken;
import com.nip.common.constants.PostTelegraphKeyPatTrainEnum;
import com.nip.common.utils.GlobalMessageGeneratedUtil;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.*;
import com.nip.dto.*;
import com.nip.dto.vo.*;
import com.nip.entity.*;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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

  private final UserService userService;
  private final PostTelegraphKeyPatTrainDao patTrainDao;
  private final GradingRuleDao gradingRuleDao;
  private final PostTelegraphKeyPatTrainPageDao pageDao;
  private final PostTelegraphKeyPatTrainPageValueDao valueDao;
  private final PostTelegraphKeyPatTrainMoreEntityDao moreEntityDao;
  private final CableFloorService cableFloorService;

  @Inject
  public PostTelegraphKeyPatTrainService(UserService userService,
                                         PostTelegraphKeyPatTrainDao patTrainDao,
                                         GradingRuleDao gradingRuleDao,
                                         PostTelegraphKeyPatTrainPageDao pageDao,
                                         PostTelegraphKeyPatTrainPageValueDao valueDao,
                                         PostTelegraphKeyPatTrainMoreEntityDao moreEntityDao,
                                         CableFloorService cableFloorService) {
    this.userService = userService;
    this.patTrainDao = patTrainDao;
    this.gradingRuleDao = gradingRuleDao;
    this.pageDao = pageDao;
    this.valueDao = valueDao;
    this.moreEntityDao = moreEntityDao;
    this.cableFloorService = cableFloorService;
  }


  @Transactional
  public PostTelegraphKeyPatTrainVO add(PostTelegraphKeyPatTrainDto dto, String token) {
    UserEntity userEntity = userService.getUserByToken(token);
    PostTelegraphKeyPatTrainEntity entity = PojoUtils.convertOne(dto, PostTelegraphKeyPatTrainEntity.class);
    entity.setAccuracy(0)
        .setCreateUserId(userEntity.getId())
        .setDuration("0")
        .setErrorNumber(0)
        .setStatus(NOT_STARTED.getStatus());

    //根据id查询评分规则
    GradingRuleEntity ruleEntity = Optional.ofNullable(gradingRuleDao.findById(entity.getRuleId()))
        .orElseThrow(() -> new IllegalArgumentException("未查询到该规则"));
    entity.setScore(new BigDecimal(ruleEntity.getScore()));
    entity.setRuleContent(ruleEntity.getContent());
    PostTelegraphKeyPatTrainEntity save = patTrainDao.saveAndFlush(entity);
    if (dto.getIsCable() == 0) {
      Integer totalNumber = save.getTotalNumber();
      if (totalNumber > 200) {
        totalNumber = 200;
      }
      generatePatKey(totalNumber, 1, save.getId(), entity.getMessageType());
    } else {
      List<List<List<String>>> cableFloor = cableFloorService.findCableFloor(dto.getCableId(), null, dto.getStartPage());
      int totalPage = dto.getTotalNumber() / 100;
      cableFloor = cableFloor.subList(0, totalPage);
      for (int i = 0; i < cableFloor.size(); i++) {
        for (int j = 0; j < cableFloor.get(i).size(); j++) {
          PostTelegraphKeyPatTrainPageEntity pageEntity = new PostTelegraphKeyPatTrainPageEntity();
          pageEntity.setPageNumber(i + 1);
          pageEntity.setSort(j);
          pageEntity.setKey(JSONUtils.toJson(cableFloor.get(i).get(j)));
          pageEntity.setValue("");
          pageEntity.setTime("[]");
          pageEntity.setTrainId(save.getId());
          pageDao.save(pageEntity);
        }
      }
    }

    return PojoUtils.convertOne(save, PostTelegraphKeyPatTrainVO.class);
  }


  public List<PostTelegraphKeyPatTrainVO> listPage(String token) {
    UserEntity userEntity = userService.getUserByToken(token);
    List<PostTelegraphKeyPatTrainEntity> entityList = patTrainDao.find("createUserId = ?1",
        Sort.by("createTime").descending(),
        userEntity.getId()
    ).list();

    return PojoUtils.convert(entityList, PostTelegraphKeyPatTrainVO.class);
  }


  @Transactional
  public void begin(PostTelegraphKeyPatTrainDto dto) {
    PostTelegraphKeyPatTrainEntity entity = Optional.ofNullable(patTrainDao.findById(dto.getId()))
        .orElseThrow(() -> new IllegalArgumentException(TRAINING_NOT_FOUND));
    entity.setStatus(UNDERWAY.getStatus());
    entity.setBeginTime(LocalDateTime.now());
    patTrainDao.save(entity);
  }


  @Transactional
  public PostTelegraphKeyPatTrainVO finish(PostTelegraphKeyPatTrainDto dto) {
    try {
      PostTelegraphKeyPatTrainEntity entity = Optional.ofNullable(patTrainDao.findById(dto.getId()))
          .orElseThrow(() -> new IllegalArgumentException(TRAINING_NOT_FOUND));
      //分数
      PostTelegraphKeyPatTrainEntity save = countScore(entity, dto);
      return PojoUtils.convertOne(save, PostTelegraphKeyPatTrainVO.class);
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  @Transactional
  public PostTelegraphKeyPatTrainVO details(String id) {
    try {
      PostTelegraphKeyPatTrainEntity entity = Optional.ofNullable(patTrainDao.findById(id))
          .orElseThrow(() -> new IllegalArgumentException(TRAINING_NOT_FOUND));
      List<Integer> pageNumber = pageDao.countPageNumber(id);
      //查询前2页数据content
      List<PostTelegraphKeyPatTrainPageEntity> twoPage = pageDao.findTwoPage(id);
      List<PostTelegraphKeyPatTrainPageValueEntity> twoPageValue = valueDao.findTwoPage(id);
      //统计每页拍发时长和个数
      List<PostTelegraphKeyPatTrainPageValueEntity> pageValueEntities = valueDao.findByTrainIdOrderByPageNumberAscSortAsc(
          id);
      Map<Integer, List<PostTelegraphKeyPatTrainPageValueEntity>> collect = pageValueEntities.stream().collect(
          Collectors.groupingBy(PostTelegraphKeyPatTrainPageValueEntity::getPageNumber));
      List<PostTelegraphKeyPatTrainPageAnalyzeVO> analyzeVOS = new ArrayList<>();
      collect.forEach((key, value) -> {
        PostTelegraphKeyPatTrainPageAnalyzeVO analyzeVO = new PostTelegraphKeyPatTrainPageAnalyzeVO();
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
          if (patValueArray != null) {
            patNumber += patValueArray.size();
          }
        }
        analyzeVO.setPatNumber(patNumber);
        analyzeVO.setTotalTime(totalTime);
        analyzeVOS.add(analyzeVO);
      });

      return PojoUtils.convertOne(entity, PostTelegraphKeyPatTrainVO.class, (t, v) -> {
        if (t.getStatus().compareTo(PostTelegraphKeyPatTrainEnum.FINISH.getStatus()) != 0 && t.getRuleId() != null) {
          String ruleContent = Optional.ofNullable(gradingRuleDao.findById(t.getRuleId()))
              .map(GradingRuleEntity::getContent).orElse("");
          v.setRuleContent(ruleContent);
        }
        v.setExistPage(pageNumber);
        if (v.getStatus().compareTo(2) == 0) {
          //在大于2页报文时，用户拍发的页数少于生成页数，需用生成的报文补足2数据
          if (twoPageValue.size() < twoPage.size()) {
            int index = twoPage.size() - twoPageValue.size();
            for (int i = 0; i < index; i++) {
              try {
                if (index+i<twoPage.size()){
                  if (null != twoPage.get(index + i)) {
                    twoPageValue.add(PojoUtils.convertOne(twoPage.get(index + i), PostTelegraphKeyPatTrainPageValueEntity.class));
                  }
                }
              } catch (Exception e) {
                log.error("details：{},index:{},i:{}", e.getMessage(), index, i);
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
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }


  public PostTelegraphKeyPatTrainPageVO getPage(String trainId, Integer pageNumber) {
    PostTelegraphKeyPatTrainPageVO ret = new PostTelegraphKeyPatTrainPageVO();
    PostTelegraphKeyPatTrainEntity entity = Optional.ofNullable(patTrainDao.findById(trainId))
        .orElseThrow(() -> new IllegalArgumentException("未查询到训练id"));
    List<PostTelegraphKeyPatTrainPageEntity> messageVO;
    //页码是否正确
    int totalPage;
    int totalNumber;
    int generateNumber = 100;
    if (entity.getIsCable() == 0) {
      totalPage = entity.getTotalNumber() / 100;
      totalNumber = entity.getTotalNumber();
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
    List<PostTelegraphKeyPatTrainPageValueEntity> userPage = valueDao.find("trainId = ?1 and pageNumber = ?2",
        Sort.by("sort").ascending(), trainId,
        pageNumber
    ).list();

    //生成的内容
    List<PostTelegraphKeyPatTrainPageEntity> pageDaoAll = pageDao.find("trainId =?1 and pageNumber=?2 ",
        Sort.by("sort").ascending(), trainId, pageNumber
    ).list();
    if (!pageDaoAll.isEmpty()) {
      messageVO = pageDaoAll;
    } else {
      messageVO = generatePatKey(generateNumber, pageNumber, entity.getId(), entity.getMessageType());
    }
    //用户未拍发本页内容，则获取生成的内容
    if (userPage.isEmpty()) {
      ret.setMessageVO(PojoUtils.convert(messageVO, PostTelegraphKeyPatTrainPageMessageVO.class));
    } else {
      ret.setMessageVO(PojoUtils.convert(userPage, PostTelegraphKeyPatTrainPageMessageVO.class));
    }

    //获取解析后的内容
    List<String> resolver = pageDaoAll.stream().map(PostTelegraphKeyPatTrainPageEntity::getValue)
        .toList();
    ret.setResolverMessage(resolver);

    //获取到本页的多组多行信息
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
  public Boolean delete(String trainId) {
    moreEntityDao.delete("trainId", trainId);
    pageDao.delete("trainId", trainId);
    valueDao.delete("trainId", trainId);
    return patTrainDao.deleteById(trainId);
  }

  @Transactional
  public void finishPage(List<PostTelegraphKeyPatTrainPageMessageVO> vo, String trainId, Integer pageNumber) {
    try {
      valueDao.deleteByTrainIdAndPageNumber(trainId, pageNumber);
      List<PostTelegraphKeyPatTrainPageValueEntity> list = new ArrayList<>();
      for (PostTelegraphKeyPatTrainPageMessageVO postTelegraphKeyPatTrainPageMessageVO : vo) {
        PostTelegraphKeyPatTrainPageValueEntity postTelegraphKeyPatTrainPageValueEntity = PojoUtils.convertOne(postTelegraphKeyPatTrainPageMessageVO, PostTelegraphKeyPatTrainPageValueEntity.class);
        postTelegraphKeyPatTrainPageValueEntity.setId(null);
        list.add(postTelegraphKeyPatTrainPageValueEntity);
      }
      valueDao.persist(list);
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  /**
   * 统计分数
   *
   * @param: entity
   */
  private PostTelegraphKeyPatTrainEntity countScore(PostTelegraphKeyPatTrainEntity entity,
                                                    PostTelegraphKeyPatTrainDto dto) {
    entity.setStatus(PostTelegraphKeyPatTrainEnum.FINISH.getStatus());
    entity.setEndTime(LocalDateTime.now());
    entity.setContent(dto.getContent());

    //计算训练时长
    long time = entity.getEndTime().toEpochSecond(ZoneOffset.of("+8")) - entity.getBeginTime()
        .toEpochSecond(ZoneOffset.of("+8"));
    entity.setDuration(String.valueOf(time));

    //查询扣分规则
    String ruleContent = Optional.ofNullable(gradingRuleDao.findById(entity.getRuleId()))
        .map(GradingRuleEntity::getContent).orElse("");
    PostKeyPatTrainRuleDto rule = JSONUtils.fromJson(ruleContent, PostKeyPatTrainRuleDto.class);
    entity.setRuleContent(ruleContent);

    //积分规则
    KeyPatStatisticalDto ks = new KeyPatStatisticalDto();
    //得到已存在的页
    List<Integer> pageNumbers = valueDao.findAllByTrainIdToPageNumber(entity.getId());
    // 创建每页的处理结果，该结果会在每页处理完毕后替换旧的page_value数据
    List<KeyPatValueTransferDto> pageValueResult = new ArrayList<>();
    // 并行处理每页数据
    pageNumbers.parallelStream().forEach(pageNumber -> {
      // 根据page获取目标数据
      List<KeyPatPageTransferDto> userPages = PojoUtils.convert(pageDao.findByTrainIdAndPageNumberOrderBySort(entity.getId(), pageNumber), KeyPatPageTransferDto.class);
      // 根据page获取拍发数据
      List<KeyPatValueTransferDto> userPageValues = PojoUtils.convert(valueDao.findByTrainIdAndPageNumberOrderBySort(entity.getId(), pageNumber), KeyPatValueTransferDto.class);
      List<KeyPatValueTransferDto> pageResult = new ArrayList<>();
      handle(null, pageResult, userPages, userPageValues, ks);
      pageValueResult.addAll(pageResult);
    });

    List<PostTelegraphKeyPatTrainPageValueEntity> pv = PojoUtils.convert(pageValueResult, PostTelegraphKeyPatTrainPageValueEntity.class);
    valueDao.deleteByTrainId(entity.getId());
    valueDao.saveAndFlush(pv);

    //存放扣分规则 key扣分名称，value扣分值
    Map<String, Object> deductInfo = new HashMap<>();
    //计算少行
    int totalGroups = entity.getIsCable() == 1 ?
        (int) pageDao.count("trainId", entity.getId()) :
        entity.getTotalNumber();
    int fullPages = totalGroups / 100;
    int lastPageGroups = totalGroups % 100;
    int totalPages = fullPages + (lastPageGroups > 0 ? 1 : 0);
    int missingPages = totalPages - pageNumbers.size();
    if (missingPages > 0) {
      int missingGroups = missingPages * 100;
      // 调整最后一页的缺失组数
      if (lastPageGroups > 0 && missingPages == 1) {
        missingGroups = lastPageGroups;
      }
      ks.setLackGroup(ks.getLackGroup() + missingGroups);
      ks.setLackLine(ks.getLackLine() + missingPages * 10);
    }

    //计算速率 拍发个数/训练时长*60
    BigDecimal speed = new BigDecimal("0");
    if (ks.getPat() != 0) {
      speed = new BigDecimal(ks.getPat()).divide(new BigDecimal(4), 10, RoundingMode.HALF_UP)
          .divide(new BigDecimal(ks.getPatTime()).divide(new BigDecimal(1000), 10, RoundingMode.HALF_UP), 10, RoundingMode.HALF_UP)
          .multiply(new BigDecimal(60)).setScale(0, RoundingMode.HALF_UP);
    }

    entity.setSpeed(String.valueOf(speed));

    //错误个数
    entity.setErrorNumber(ks.getError());
    BigDecimal accuracy = new BigDecimal("0");
    if (ks.getPatGroup() - ks.getError() != 0) {
      //计算正确率 （拍发总个数 - 错误个数- 多字- 少字)） /拍发总个数
      accuracy = new BigDecimal(ks.getPatGroup() - ks.getError() - ks.getBunchGroup()).divide(
          new BigDecimal(ks.getPatGroup()), 2, RoundingMode.HALF_UP).multiply(new BigDecimal(100));
    }
    entity.setAccuracy(accuracy.doubleValue());

    //得到要扣的分
    String minus = "-";
    BigDecimal score = entity.getScore();
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

    //判断速率是加分还是扣分
    deductInfo.put("speedNumber", speed.toString());
    if (speed.compareTo(new BigDecimal(rule.getWpm().getBase())) > 0) {
      int diff = speed.intValue() - rule.getWpm().getBase();
      BigDecimal speedScore = rule.getWpm().getR().multiply(new BigDecimal(diff));
      score = score.add(speedScore);
      deductInfo.put("speedScore", "+" + speedScore);
    } else if (speed.compareTo(new BigDecimal(rule.getWpm().getBase())) <= 0) {
      int diff = rule.getWpm().getBase() - speed.intValue();
      BigDecimal speedScore = rule.getWpm().getL().multiply(new BigDecimal(diff));
      score = score.subtract(speedScore);
      deductInfo.put("speedScore", minus + speedScore);
    }

    entity.setScore(score);
    //保存扣分详情
    entity.setDeductInfo(JSONUtils.toJson(deductInfo));

    return patTrainDao.save(entity);
  }

  /**
   * 生成每一页数据
   *
   * @param generateNumber 生成数量
   * @param pageNumber     页码
   * @param trainId        训练id
   * @param messageType    训练报文 0数码 1字码 2混合码
   */
  @Transactional
  public List<PostTelegraphKeyPatTrainPageEntity> generatePatKey(Integer generateNumber, Integer pageNumber,
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
        case 0 -> GlobalMessageGeneratedUtil.generatedNumber(generate, true, true);
        case 1 -> GlobalMessageGeneratedUtil.generatedWord(generate, true, true);
        default -> GlobalMessageGeneratedUtil.generatedMingle(generate, true, true);
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
        pageEntity.setValue("");
        pageEntity.setTime("[]");
        pageEntity.setTrainId(trainId);
        pageEntities.add(pageEntity);
      }
    }
    return pageDao.save(pageEntities);
  }
}
