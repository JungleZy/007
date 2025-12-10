package com.nip.service;

import com.google.gson.reflect.TypeToken;
import com.nip.common.PageInfo;
import com.nip.common.constants.PostTelexPatTrainStatusEnum;
import com.nip.common.utils.Page;
import com.nip.common.utils.*;
import com.nip.dao.GradingRuleDao;
import com.nip.dao.PostTelexPatTrainDao;
import com.nip.dao.PostTelexPatTrainPageDao;
import com.nip.dao.PostTelexPatTrainPageValueDao;
import com.nip.dto.*;
import com.nip.dto.vo.PostTelexPatTrainPageInfoVO;
import com.nip.dto.vo.PostTelexPatTrainPageVO;
import com.nip.dto.vo.PostTelexPatTrainPageValueVO;
import com.nip.dto.vo.PostTelexPatTrainVO;
import com.nip.dto.vo.param.PostTelexPatTrainFinishParam;
import com.nip.dto.vo.param.PostTelexPatTrainParam;
import com.nip.entity.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.criteria.Predicate;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import static com.nip.common.utils.TelexPatUtils.calculateAverage;

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
  private static final ThreadLocalRandom RANDOM = ThreadLocalRandom.current();

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
    //从token中获取用户
    UserEntity userEntity = userService.getUserByToken(token);
    //查询评分内容
    GradingRuleEntity ruleEntity = Optional.ofNullable(gradingRuleDao.findById(dto.getRuleId()))
        .orElseThrow(() -> new IllegalArgumentException("未查询到评分规则"));
    PostTelexPatTrainEntity entity = PojoUtils.convertOne(dto, PostTelexPatTrainEntity.class, (t, r) -> {
      //设置默认值
      r.setAccuracy("0.00");
      r.setSpeed("0");
      r.setStatus(NOT_STARTED.getStatus());
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
    int generateNumber = groupNumber < 200 ? groupNumber : 200;
    PostTelexPatTrainEntity save = postTelexPatTrainDao.save(entity);
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
        generateContent(entity.getType(), generateNumber, 1, save.getId());
      }
    } else {
      List<List<List<String>>> cableFloor = cableFloorService.findCableFloor(dto.getCableId(), null, dto.getStartPage());
      int totalPage = dto.getGroupNumber() / 100;
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

    return PojoUtils.convertOne(save, PostTelexPatTrainVO.class);
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
        PostTelexPatTrainVO.class
    );
    PageInfo<PostTelexPatTrainVO> pageInfo = new PageInfo<>();
    pageInfo.setCurrentPage(all.getCurrentPage());
    pageInfo.setPageSize(all.getPageSize());
    pageInfo.setTotalPage(all.getTotalPage());
    pageInfo.setTotalNumber(all.getTotalNumber());
    pageInfo.setData(convert);
    return pageInfo;
//    return postTelexPatTrainDao.findTrainList(trainType, userService.getUserByToken(token).getId());
  }

  public PostTelexPatTrainVO detail(PostTelexPatTrainParam param) {
    PostTelexPatTrainEntity entity = postTelexPatTrainDao.findById(param.getId());
    List<PostTelexPatTrainPageEntity> pageEntities = pageDao.findByTrainIdTop2(param.getId());
    List<PostTelexPatTrainPageValueEntity> pageValueEntities = valueDao.findByTrainIdTop2(param.getId());
    List<PostTelexPatTrainPageValueVO> pageValueVOS = PojoUtils.convert(pageValueEntities, PostTelexPatTrainPageValueVO.class);
    List<PostTelexPatTrainPageVO> convert = PojoUtils.convert(pageEntities, PostTelexPatTrainPageVO.class);
    return PojoUtils.convertOne(entity, PostTelexPatTrainVO.class, (e, v) -> {
      v.setExistPage(convert);
      v.setCodeAll(pageValueVOS);
      v.setPageNumber(pageDao.findMaxPageNumber(entity.getId()));
    });
  }

  @Transactional(rollbackOn = Exception.class)
  public PostTelexPatTrainVO begin(PostTelexPatTrainParam param) {
    try {
      PostTelexPatTrainEntity entity = Optional.ofNullable(postTelexPatTrainDao.findById(param.getId()))
          .orElseThrow(() -> new IllegalArgumentException("未查询到训练信息"));
      //参数校验
      CheckUtils.statusCheck(NOT_STARTED.getStatus(), entity.getStatus(),
          "该训练状态不是未开始"
      );
      //修改状态并保存
      entity.setStatus(UNDERWAY.getStatus());
      entity.setStartTime(LocalDateTime.now());
      return PojoUtils.convertOne(postTelexPatTrainDao.save(entity), PostTelexPatTrainVO.class);
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  @Transactional(rollbackOn = Exception.class)
  public PostTelexPatTrainVO finish(PostTelexPatTrainFinishParam param) {
    try {
      PostTelexPatTrainEntity entity = Optional.ofNullable(postTelexPatTrainDao.findById(param.getId()))
          .orElseThrow(() -> new IllegalArgumentException("未查询到训练信息"));
      //根据评分规则计算训练得分
//      if (entity.getStatus().equals(3)) {
//        return PojoUtils.convertOne(entity, PostTelexPatTrainVO.class);
//      }
      PostTelexPatTrainEntity postTelexPatTrainEntity = countScore(param, entity);
      postTelexPatTrainEntity.setTotalSpeed(param.getTotalSpeed());
      return PojoUtils.convertOne(postTelexPatTrainDao.save(postTelexPatTrainEntity), PostTelexPatTrainVO.class);
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  public PostTelexPatTrainPageInfoVO getPage(String trainId, Integer pageNumber) {
    PostTelexPatTrainEntity trainEntity = postTelexPatTrainDao.findById(trainId);
    Integer groupNumber = 0;
    int generateNumber = 100;
    if (trainEntity.getIsCable() == 0) {
      groupNumber = trainEntity.getGroupNumber();
      int totalPage = groupNumber / 100;

      if (groupNumber % 100 > 0) {
        totalPage += 1;
      }
      if (totalPage < pageNumber || pageNumber < 0) {
        throw new IllegalArgumentException("页码不正确");
      }
      if (totalPage == pageNumber) {
        generateNumber = groupNumber - ((pageNumber - 1) * 100);
      }
    }

    List<PostTelexPatTrainPageEntity> pageEntities = pageDao.findByTrainIdAndPageNumberOrderBySort(trainId, pageNumber);
    if (pageEntities.isEmpty()) {
      pageEntities = generateContent(trainEntity.getType(), generateNumber, pageNumber, trainId);
    }
    String codeAll = Optional.ofNullable(valueDao.findByTrainIdAndPageNumber(trainId, pageNumber))
        .map(PostTelexPatTrainPageValueEntity::getPatValue)
        .orElseGet(String::new);
    PostTelexPatTrainPageInfoVO ret = new PostTelexPatTrainPageInfoVO();
    ret.setCodeAll(codeAll);
    ret.setPageVo(PojoUtils.convert(pageEntities, PostTelexPatTrainPageVO.class));
    return ret;
  }

  @Transactional
  public void finishPage(PostTelexPatTrainPageValueVO vo) {
    PostTelexPatTrainEntity entity = postTelexPatTrainDao.findById(vo.getTrainId());
    //记录每页速率
    List<String> speedLog = Optional.ofNullable(entity.getSpeedLog()).map(speed -> JSONUtils.fromJson(speed, new TypeToken<List<String>>() {
    })).orElseGet(ArrayList::new);
    if (!StringUtils.isEmpty(vo.getSpeed())) {
      if (speedLog.isEmpty()) {
        speedLog.add(vo.getSpeed());
      } else {
        if (speedLog.size() >= vo.getPageNumber()) {
          if (!StringUtils.isEmpty(speedLog.get(vo.getPageNumber() - 1))) {
            speedLog.set(vo.getPageNumber() - 1, vo.getSpeed());
          }
        } else {
          speedLog.add(vo.getSpeed());
        }
      }
    }
    entity.setSpeedLog(JSONUtils.toJson(speedLog));
    log.info("speed:{},speedLog:{}", vo.getSpeed(), speedLog);
    // 记录每页耗时
    List<Integer> validTimeLog = Optional.ofNullable(entity.getValidTimeLog()).map(validTime -> JSONUtils.fromJson(validTime, new TypeToken<List<Integer>>() {
    })).orElseGet(ArrayList::new);
    if (vo.getValidTime() != null) {
      if (validTimeLog.isEmpty()) {
        validTimeLog.add(vo.getValidTime());
      } else {
        if (validTimeLog.size() >= vo.getPageNumber()) {
          validTimeLog.set(vo.getPageNumber() - 1, vo.getValidTime());
        } else {
          validTimeLog.add(vo.getValidTime());
        }
      }
    }
    entity.setValidTimeLog(JSONUtils.toJson(validTimeLog));
    log.info("每页耗时：validTime{},validTimeLog{}", vo.getValidTime(), validTimeLog);
    postTelexPatTrainDao.save(entity);
    valueDao.delete("trainId=?1 and pageNumber=?2", vo.getTrainId(), vo.getPageNumber());
    valueDao.save(PojoUtils.convertOne(vo, PostTelexPatTrainPageValueEntity.class));
  }

  @Transactional
  public Boolean delete(String trainId) {
    pageDao.delete("trainId", trainId);
    valueDao.delete("trainId", trainId);
    return postTelexPatTrainDao.deleteById(trainId);
  }

  /**
   * 计算得分
   *
   * @param param
   * @return
   */
  private PostTelexPatTrainEntity countScore(PostTelexPatTrainFinishParam param, PostTelexPatTrainEntity entity) {
    if (entity.getTrainType().compareTo(4) == 0) {
      //创建扣分信息Map
      Map<String, String> deductMap = new HashMap<>();
      //少行
      int lackLineNumber = 0;
      //少回行
      int lackReturnLineNumber = 0;
      //多行
      int moreLineNumber = 0;
      //多组
      int moreGroupNumber = 0;
      //少页标
      int lackPageMarkNumber = 0;
      //页标错
      int pageMarkErrorNumber = 0;
      //错组
      int errorGroupNumber = 0;
      //多码
      int moreCodeNumber = 0;
      //少码
      int lackCodeNumber = 0;
      //少组
      int lackGroupNumber = 0;
      //不规
      Integer nonStandartNumber = 0;
      //改错次数
      int change = 0;

      List<Integer> pageNumber = pageDao.countPageNumber(param.getId());
      List<PostTelexPatTrainPageEntity> pageEntities = pageDao.findByTrainIdOrderBySort(param.getId());
      List<Map<String, Object>> codeAll = new ArrayList<>();
      List<String> page = new ArrayList<>();
      pageNumber.forEach(item -> {
        PostTelexPatTrainPageValueEntity pageValueEntity = valueDao.findByTrainIdAndPageNumber(param.getId(), item);
        if (pageValueEntity != null) {
          String patValue = pageValueEntity.getPatValue();
          if (entity.getTrainType().compareTo(0) == 0) {
            codeAll.addAll(Objects.requireNonNull(JSONUtils.fromJson(Optional.ofNullable(patValue).orElse("[]"), new TypeToken<>() {
            })));
          } else {
            page.add(Optional.ofNullable(patValue).orElse(""));
          }
        }
      });

      //得到 codeAll[{"text":"4","time":0}...] 用户输入的内容
      List<List<List<String>>> parseCodeAll = new ArrayList<>();
      if (entity.getTrainType().compareTo(0) == 0) {
        //按规则解析客户输入的内容
        Map<String, Object> convertCodeAll = convertCodeAll(codeAll, entity.getTrainType());
        //改错次数
        change = (int) convertCodeAll.get("errorNumber");
        nonStandartNumber = (Integer) convertCodeAll.get("irregularityNumber");
        parseCodeAll.addAll((List<List<List<String>>>) convertCodeAll.get("data"));
      } else {
        for (String p : page) {
          if (StringUtils.isBlank(p)) {
            parseCodeAll.add(new ArrayList<>());
            continue;
          }
          //每行之间用\n
          String[] row = p.split("\\n");
          List<List<String>> pageList = new ArrayList<>();
          for (String s : row) {
            if (StringUtils.isBlank(s)) {
              pageList.add(new ArrayList<>());
              continue;
            }
            //每组间用空格 分
            String[] groups = s.split(" ");
            //处理不规
            for (int i = 0; i < groups.length; i++) {
              if (groups[i].length() == 3 && groups.length > (i + 1) && groups[i + 1].length() == 5) {
                String nextGroup = groups[i + 1];
                groups[i] = groups[i] + nextGroup.charAt(0);
                groups[i + 1] = nextGroup.substring(1);
                nonStandartNumber += 1;
              } else if (groups[i].length() == 5 && groups.length > (i + 1) && groups[i + 1].length() == 3) {
                String nextGroup = groups[i + 1];
                groups[i] = groups[i].substring(0, groups[i].length() - 1);
                groups[i + 1] = nextGroup + groups[i].charAt(groups[i].length() - 1);
                nonStandartNumber += 1;
              }
            }
            pageList.add(Arrays.stream(groups).toList());
          }
          if (!pageList.isEmpty()) {
            parseCodeAll.add(pageList);
          }
        }
      }

      //错误次数
      int errorNumber = 0;
      //得到 code正确的内容
      List<List<List<PostTelexPatTrainPageEntity>>> convertText = convertTextListString(pageEntities);

      //具体的评分规则
      PostTelexPatTrainRuleDto rule = JSONUtils.fromJson(entity.getRuleContent(), PostTelexPatTrainRuleDto.class);
      if (rule == null) {
        throw new IllegalArgumentException("评分规则未设定");
      }

      BigDecimal score = new BigDecimal(entity.getScore());

      //计算分数
      for (int i = 0; i < parseCodeAll.size(); i++) {
        //得到页
        List<List<String>> pages = parseCodeAll.get(i);
        //多页
        if (i > (convertText.size() - 1)) {
          //多行
          moreLineNumber += pages.size();
          continue;
        }

        List<List<PostTelexPatTrainPageEntity>> correctPages = convertText.get(i);
        boolean lineEq = pages.size() < correctPages.size();
        for (int j = 0; j < pages.size(); j++) {
          //得到行
          List<String> row = pages.get(j);
          //如果此行长度是0 则判定成少行
          if (row.isEmpty()) {
            lackLineNumber += 1;
            score = score.subtract(rule.getOther().getMuchLessLine());
            continue;
          }
          //判断多行
          if (correctPages.size() - 1 < j) {
            //多一行扣10分
            BigDecimal muchLessLine = rule.getOther().getMuchLessLine();
            score = score.subtract(muchLessLine);
            moreLineNumber++;
            continue;
          }
          List<PostTelexPatTrainPageEntity> correctRow = correctPages.get(j);
          //比对每一组
          for (int k = 0; k < row.size(); k++) {
            //判断是否少回行
            if (lineEq && row.size() > 10 && k > 9 && (correctPages.get(j + 1) != null)) {
              //匹配下一行如下一行匹配率达80%及以上，则视为少回行
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
              BigDecimal decimal = new BigDecimal(c).
                  divide(new BigDecimal(nextRow.size()), 2, RoundingMode.HALF_DOWN)
                  .multiply(new BigDecimal(100));
              //如果大于 将moreRow 插入到下一组中 同时也判定成少回行
              if (decimal.compareTo(new BigDecimal(80)) >= 0) {
                pages.add(j + 1, moreRow);
                //少回行
                lackReturnLineNumber++;
              }
              continue;
            }
            String group = row.get(k);
            //判断多组情况
            if (correctRow.size() - 1 < k) {
              score = score.subtract(rule.getOther().getMuchLessGroups());
              moreGroupNumber++;
              continue;
            }
            String correctGroup = correctRow.get(k).getKey();
            //判断是否需要加上页码
            if ((correctPages.size() - 1 == j) && (correctRow.size() - 1 == k)) {
              correctGroup = correctGroup + "-" + (i + 1);
            }
            if (!Objects.equals(group, correctGroup)) {
              //先排除是否少页标
              if (correctGroup.length() > 4 && correctGroup.contains("-") && group.length() == 4) {
                String t = correctGroup.split("-")[0];
                if (!Objects.equals(t, group)) {
                  //如果除开字符串页不相同的情况需要扣除 错组35分
                  score = score.subtract(rule.getOther().getErrorCode());
                  errorGroupNumber++;
                } else {
                  //页标10分
                  score = score.subtract(rule.getOther().getLessPage());
                  lackPageMarkNumber++;
                }
              }//判断是否是页标记错误
              else if (correctGroup.length() > 4 && correctGroup.contains("-") && group.length() > 4 && group.contains("-")) {
                String ct = correctGroup.split("-")[0];
                String cp = correctGroup.split("-")[1];
                String gt = group.split("-")[0];
                String gp = group.split("-")[1];
                //判断组和也标记是否有错误
                if (!Objects.equals(ct, gt)) {
                  //字符串错误扣35
                  score = score.subtract(rule.getOther().getErrorCode());
                  errorGroupNumber++;
                }
                if (!Objects.equals(cp, gp)) {
                  //页标记错误扣10分
                  score = score.subtract(rule.getOther().getErrorPage());
                  pageMarkErrorNumber++;
                }
              }
              //多码或这少码
              else if (StringUtils.isNotEmpty(group) && group.length() != 4) {
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
              //错误数量++
              errorNumber++;
            }

            //判断是否少组
            if ((row.size() - 1 == k) && (correctRow.size() - 1 > k)) {
              score = score.subtract(
                  new BigDecimal(correctRow.size() - row.size()).multiply(rule.getOther().getMuchLessGroups()));
              lackGroupNumber += correctRow.size() - row.size();
            }
          }
        }
        //判断是否少行
        if (pages.size() < correctPages.size()) {
          //记录少行数
          lackLineNumber += correctPages.size() - pages.size();
          for (int k = 0; k < correctPages.size() - pages.size(); k++) {
            //少回行扣10分
            BigDecimal lessReturnLine = rule.getOther().getMuchLessLine();
            score = score.subtract(lessReturnLine);
          }
        }
        //判断少页
        if (i == (parseCodeAll.size() - 1) && i < convertText.size() - 1) {
          //获取剩余页
          for (int j = (i + 1); j < convertText.size(); j++) {
            List<List<PostTelexPatTrainPageEntity>> nextPage = convertText.get(j);
            //得到行数
            int size = nextPage.size();
            lackLineNumber += size;
            //扣分
            score = score.subtract(rule.getOther().getMuchLessLine().multiply(new BigDecimal(size)));
          }
        }
      }
      //计算未生成的数据，少行
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
        //获取用户已提交的页
        List<Integer> existPageNumber = valueDao.countPageNumber(entity.getId());
        //移除已提交的页和已经生成的页
        totalPageNumber.removeAll(existPageNumber);
        totalPageNumber.removeAll(pageNumber);
        for (int i = 0; i < totalPageNumber.size(); i++) {
          if (i == totalPageNumber.size() - 1) {
            //说明是最后一页
            lackLineNumber += totalPage % 100;
            score = score.subtract(new BigDecimal(totalPage % 100).multiply(new BigDecimal(rule.getOther().getMuchLessLine().toString())));
            if (groupNumber % 100 > 0) {
              lackLineNumber++;
              score = score.subtract(new BigDecimal(rule.getOther().getMuchLessLine().toString()));
            }
          } else {
            lackLineNumber += 10;
            score = score.subtract(new BigDecimal(10).multiply(new BigDecimal(rule.getOther().getMuchLessLine().toString())));
          }
        }
      }

      //少行
      deductMap.put("lackLineNumber", String.valueOf(lackLineNumber));
      deductMap.put("lackLineScore", new BigDecimal(lackLineNumber).multiply(rule.getOther().getMuchLessLine()).toString());

      //多行数量放入到deductMap中，计算多行扣除的分数
      deductMap.put("moreLineNumber", String.valueOf(moreLineNumber));
      deductMap.put("moreLineScore", new BigDecimal(moreLineNumber).multiply(rule.getOther().getMuchLessLine()).toString());

      //多组放入到deductMap中，计算多组扣除的分数
      deductMap.put("moreGroupNumber", String.valueOf(moreGroupNumber));
      deductMap.put("moreGroupScore", new BigDecimal(moreGroupNumber).multiply(rule.getOther().getMuchLessGroups()).toString());

      //少回行lackReturnLineNumber
      deductMap.put("lackReturnLineNumber", String.valueOf(lackReturnLineNumber));
      deductMap.put("lackReturnLineScore", new BigDecimal(lackReturnLineNumber).multiply(rule.getOther().getLessReturnLine()).toString());

      //少页标 lackPageMarkNumber
      deductMap.put("lackPageMarkNumber", String.valueOf(lackPageMarkNumber));
      deductMap.put("lackPageMarkScore", new BigDecimal(lackPageMarkNumber).multiply(rule.getOther().getLessPage()).toString());

      //页标错
      deductMap.put("pageMarkErrorNumber", String.valueOf(pageMarkErrorNumber));
      deductMap.put("pageMarkErrorScore", new BigDecimal(pageMarkErrorNumber).multiply(rule.getOther().getErrorPage()).toString());

      //错组
      deductMap.put("errorGroupNumber", String.valueOf(errorGroupNumber));
      deductMap.put("errorGroupScore", new BigDecimal(errorGroupNumber).multiply(rule.getOther().getErrorCode()).toString());

      //多码 moreCodeNumber
      deductMap.put("moreCodeNumber", String.valueOf(moreCodeNumber));
      deductMap.put("moreCodeScore", new BigDecimal(moreCodeNumber).multiply(rule.getOther().getMuchLessCode()).toString());

      //少码 lackCodeNumber
      deductMap.put("lackCodeNumber", String.valueOf(lackCodeNumber));
      deductMap.put("lackCodeScore", new BigDecimal(lackCodeNumber).multiply(rule.getOther().getMuchLessCode()).toString());

      //少组lackGroupNumber
      deductMap.put("lackGroupNumber", String.valueOf(lackGroupNumber));
      deductMap.put("lackGroupScore", new BigDecimal(lackGroupNumber).multiply(rule.getOther().getMuchLessGroups()).toString());

      //不规
      deductMap.put("nonStandartNumber", String.valueOf(nonStandartNumber));
      deductMap.put("nonStandartScor", new BigDecimal(nonStandartNumber).multiply(Optional.ofNullable(rule.getOther().getNonStandart()).orElseGet(() -> new BigDecimal(0))).toString());

      //改错扣分 = 改错字数 *2
      BigDecimal updateScore = rule.getOther().getCorrectMistakes().multiply(new BigDecimal(change));
      score = score.subtract(updateScore);

      //改错
      deductMap.put("updateErrorNumber", change + "");
      deductMap.put("updateErrorScore", updateScore.toString());

      //计算速率 组数 / 耗时 * 60
      BigDecimal speed = parseCodeAll.stream()
          .flatMap(Collection::stream)
          .map(List::size)
          .map(BigDecimal::new)
          .reduce(BigDecimal.ZERO, BigDecimal::add);
      speed = speed.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : speed.divide(new BigDecimal(param.getValidTime()), 10, RoundingMode.HALF_DOWN)
          .multiply(new BigDecimal(60)).setScale(0, RoundingMode.HALF_UP);
      //计算在结算速率是否高于规定速率
      int speedDiffer = rule.getWpm().getBase() - Integer.parseInt(param.getTotalSpeed());
      if (speedDiffer > 0) {
        //乘法
        BigDecimal speedLow = rule.getWpm().getL().multiply(new BigDecimal(speedDiffer));
        //减法
        score = score.subtract(speedLow);
        //速率扣分
        deductMap.put("speedLowNumber", Math.abs(speedDiffer) + "");
        deductMap.put("speedLowScore", speedLow.toString());
        deductMap.put("speedOverTopNumber", "0");
        deductMap.put("speedOverTopScore", "0");
      } else {
        BigDecimal speedOverTop = rule.getWpm().getR().multiply(new BigDecimal(Math.abs(speedDiffer)));
        score = score.add(speedOverTop);
        //速率超出
        deductMap.put("speedOverTopNumber", Math.abs(speedDiffer) + "");
        deductMap.put("speedOverTopScore", speedOverTop.toString());
        deductMap.put("speedLowNumber", "0");
        deductMap.put("speedLowScore", "0");
      }
      entity.setSpeed(speed.toString());

      //错误个数
      entity.setErrorNumber(errorNumber);
      //计算正确率 （拍发总个数- 错误个数 = 正确个数） / 总个数
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
      entity.setValidTime(param.getValidTime());
      entity.setEndTime(LocalDateTime.now());
      entity.setStatus(PostTelexPatTrainStatusEnum.FINISH.getStatus());
      entity.setScore(score.toString());
      entity.setChange(change + nonStandartNumber);
      //将Count修改成[key:正确内容,value:错误内容]
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

      //存入扣分信息
      String deductInfo = JSONUtils.toJson(deductMap);
      entity.setDeductInfo(deductInfo);
      return entity;
    } else {
      List<Integer> pageNumbers = pageDao.countPageNumber(param.getId());
      TelexPatStatisticalDto ks = new TelexPatStatisticalDto();
      List<TelexPatValueTransferDto> pageValueResult = new ArrayList<>();
      List<PostTelexPatTrainPageValueEntity> trainUserValues = valueDao.findAllByTrainId(param.getId());
      List<PostTelexPatTrainPageEntity> trainPages = pageDao.findByTrainIdOrderBySort(entity.getId());
      Map<Integer, PostTelexPatTrainPageValueEntity> valueMap = trainUserValues.stream()
          .filter(e -> Objects.nonNull(e.getPageNumber())) // 过滤掉pageNumber为null的实体
          .collect(Collectors.toMap(
              PostTelexPatTrainPageValueEntity::getPageNumber, // 使用pageNumber作为键
              e -> e,                                // 实体本身作为值
              (existing, replacement) -> replacement
          ));
      Map<Integer, List<PostTelexPatTrainPageEntity>> pageMap = trainPages.stream()
          .collect(Collectors.groupingBy(PostTelexPatTrainPageEntity::getPageNumber));
      long l = System.currentTimeMillis();
      pageNumbers.forEach(pageNumber -> {
        List<TelexPatPageTransferDto> userPages = PojoUtils.convert(
            pageMap.get(pageNumber),
            TelexPatPageTransferDto.class);
        PostTelexPatTrainPageValueEntity pageValueEntity = valueMap.get(pageNumber);
        TelexPatUtils.handle(null, pageNumber, pageValueResult, userPages, null == pageValueEntity ? null : pageValueEntity.getPatValue(), ks, pageNumber == pageNumbers.size() - 1);
      });
      List<PostTelexPatTrainPageEntity> convert = PojoUtils.convert(pageValueResult, PostTelexPatTrainPageEntity.class);
      pageDao.deleteByTrainId(entity.getId());
      pageDao.saveAndFlush(convert);
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

      BigDecimal alterError = rule.getOther().getAlterError();
      BigDecimal correctMistakesScore = new BigDecimal(ks.getCorrectMistakesNumber()).multiply(null == alterError ? rule.getOther().getCorrectMistakes() : alterError);
      deductMap.put("correctMistakesNumber", ks.getCorrectMistakesNumber());
      deductMap.put("correctMistakesScore", minus + correctMistakesScore);

      //计算正确率 （拍发总个数- 错误个数 = 正确个数） / 总个数
      BigDecimal accuracy = new BigDecimal("0");
      int errorTotal = ks.getPatGroup() - ks.getErrorCodeNumber() - ks.getMuchLessCodeNumber();
      entity.setErrorNumber(errorTotal);
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
      BigDecimal avgSpeed = calculateAverage(JSONUtils.fromJson(entity.getSpeedLog(), new TypeToken<>() {
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
      List<Integer> validTimeLog = JSONUtils.fromJson(entity.getValidTimeLog(), new TypeToken<>() {
      });
      int validTime = 0;
      if (validTimeLog != null) {
        for (Integer i : validTimeLog) {
          validTime += i;
        }
      }
      entity.setValidTime(validTime);
      entity.setSpeed(String.valueOf(avgSpeed));
      entity.setScore(String.valueOf(score));
      entity.setAccuracy(String.valueOf(accuracy));
      entity.setDeductInfo(JSONUtils.toJson(deductMap));
      entity.setEndTime(LocalDateTime.now());
      entity.setStatus(PostTelexPatTrainStatusEnum.FINISH.getStatus());
      return entity;
    }
  }

  private static Map<String, Object> convertCodeAll(List<Map<String, Object>> codeAll, Integer trainType) {
    try {
      Map<String, Object> ret = new HashMap<>();
      StringBuilder builder = new StringBuilder();
      //将所有的字符串都拼接起来
      codeAll.stream().map(m -> m.get("text")).forEach(builder::append);

      String temp = builder.toString();

      //按AltEnterEnter分页
      String[] page = temp.split("EnterEnter");

      int errorNumber = 0;

      //不规次数
      int lrregularity = 0;

      //全部解析后的数据
      List<List<List<String>>> totalPageData = new ArrayList<>();
      //将每页按AltEnter换行
      for (String item : page) {
        List<List<String>> pageData = new ArrayList<>();

        //得到了行
        String[] row = item.split("Enter");

        for (int j = 0; j < row.length; j++) {
          String line = row[j];

          //再将line按空格拆分,得到了每一组
          String[] groups = line.split(" ");

          //然后便利每一组进行解析
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
                //错误数+1
                errorNumber++;
              }
              // 立即修改：1233 //// 1234
              else if (z != 0 && Objects.equals(group, "////")) {
                //将下一个元素放入到集合中
                if (!rowList.isEmpty()) {
                  rowList.set(rowList.size() - 1, groups[z + 1]);
                  //让z++防止下一个元素在判断
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
                  lrregularity++;
                }
                errorNumber++;
              }
              // 不规 2// 1234 ,2/ 1234 23456/ 2345
              else if (PATTERN_REG_4.matcher(group).matches() && group.length() != 4) {
                if (groups.length - 1 >= z + 1) {
                  rowList.add(groups[z + 1]);
                  lrregularity++;
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
                  lrregularity++;
                  errorNumber++;
                  z++;
                } else {
                  rowList.add(group);
                }
              }
              // 三五码 234 56789 => 2345 6789
              else if (group.length() == 3 && z + 1 < groups.length && groups[z + 1].length() == 5) {
                //将下一组的第一位放到此组的最后一位
                String nextGroup = groups[z + 1];
                groups[z + 1] = nextGroup.substring(1);
                rowList.add(group + nextGroup.charAt(0));
                lrregularity++;
              }
              // 五三码 23456 789 => 2345 6789
              else if (group.length() == 5 && (groups.length - 1 > z + 1) && (groups[z + 1].length() == 3)) {
                groups[z + 1] = group.charAt(group.length() - 1) + groups[z + 1];
                rowList.add(group.substring(0, group.length() - 1));
                lrregularity++;
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
                //得到上一行的内容
                if (j > 0) {
                  //得到上一行
                  List<String> lastRow = pageData.get(j - (1 + j - pageData.size()));
                  //得到最后一组
                  String lastGroup = lastRow.getLast();
                  //判断最后一组是否是包含"-"
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
                    //获取内容并替换
                    z++;
                    //改错+1
                    errorNumber++;
                  } else {
                    rowList.add(group);
                  }
                }
                //开始就第一行则将正常让如到集合中
                else {
                  rowList.add(group);
                }
              }
              // qta add 开头 多行少行的处理和多组少组处理
              else if (Objects.equals(group, "QTA") || Objects.equals(group, "ADD")) {
                try {
                  //通过页标记来判断是否是行尾取消还是页尾取消
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
                  //得到下一个元素
                  String next = groups[z + 1];
                  if (pageMark.get()) {
                    //这里需要判断是否是 多行少行的处理 qta 2---4 add 3---5 1111 1111 1111
                    if (Pattern.matches("^\\d{1,}-{3}\\d{1,}", next)) {
                      String[] split = next.split("---");
                      //计算出开始索引和结束索引
                      String startStr = split[0];
                      String endStr = split[1];
                      int startIndex = Integer.parseInt(startStr) - 1;
                      int endIndex = Integer.parseInt(endStr) - 1;

                      int startRow = (startIndex % 100) / 10;
                      int startColumn = startIndex % 10;

                      int endRow = (endIndex % 100) / 10;
                      int endColumn = endIndex % 10;

                      //判断是否在同一行中
                      if (startRow != endRow) {
                        throw new IllegalArgumentException("多少行处理，已超出一行");
                      }
                      //得到要操作的行
                      List<String> rowData = pageData.get(startRow);

                      //删除
                      if (Objects.equals(group, "QTA")) {
                        List<String> data = new ArrayList<>();
                        int tempIndex = -1;
                        for (int k = 0; (data.size() - 1) < endIndex; k++) {
                          data.addAll(pageData.get(k));
                          tempIndex++;
                        }
                        //得到要删除的行
                        List<String> deleteRow = pageData.get(tempIndex);
                        //删除 startColumn - endColumn
                        for (int k = 0; k <= endColumn - startColumn; k++) {
                          String value = data.get(startIndex + k);
                          deleteRow.remove(value);
                          errorNumber++;
                        }
                        //删除完之后判断这行是否还有数据，如果没有则移除
                        if (deleteRow.isEmpty()) {
                          pageData.remove(deleteRow);
                        }
                        z++;
                      } else {
                        //得到添加的内容
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
                    //页尾添加或删除
                    else {
                      int index = Integer.parseInt(next) - 1;
                      int rowNum = (index % 100) / 10;
                      int groupNum = (index % 10);
                      //如果有页标则说明是页尾修改或添加
                      if (Objects.equals(group, "QTA")) {
                        pageData.get(rowNum).remove(groupNum);
                        errorNumber++;
                        z++;
                      } else {
                        //得到后面第2个元素
                        String data = groups[z + 2];
                        //添加
                        pageData.get(rowNum).add(groupNum, data);
                        z = z + 2;
                        errorNumber++;
                      }
                    }

                  } else {
                    int index = Integer.parseInt(next);
                    //如果没有页标则说是行尾删除或添加
                    if (Objects.equals(group, "QTA")) {
                      rowList.remove(index - 1);
                      z++;
                      errorNumber++;
                    } else {
                      //得到添加的内容
                      String data = groups[z + 2];
                      rowList.add(index - 1, data);
                      z = z + 2;
                      errorNumber++;
                    }
                  }
                  //判断是
                } catch (Exception e) {
                  //若出现异常则添加到集合中
                  log.error("解析ADD或QTA 失败:{}", e.getMessage());
                  rowList.add(group);
                }
              }
              // 标错页修改 1234 .... 7890-2/1 表示将第二页修改成第一页
              else if (PATTERN_REG_6.matcher(group).matches()) {
                //7890-2/1
                try {
                  String[] split = group.split("/");
                  String pageNumber = split[1];
                  String pageMark = split[0];
                  //得到"7890-" 这部分
                  String substring = pageMark.substring(0, pageMark.indexOf("-") + 1);
                  //将修改后的页标放入到元素中，错误+1
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
                  //得到P前面的内容
                  String pMark = group.substring(0, group.lastIndexOf("P"));
                  int pageNum = Integer.parseInt(pMark) - 1;
                  //得到组号
                  int index = Integer.parseInt(groups[z + 1]) - 1;
                  int rowNum = (index % 100) / 10;
                  int groupNum = (index % 10);
                  //得到修改内容,并修改
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
                //将下一组的第一位放到此组的最后一位
                String nextGroup = groups[z + 1];
                groups[z + 1] = nextGroup.substring(1);
                rowList.add(group + nextGroup.charAt(0));
                lrregularity++;
              }
              // 五三码 23456 789 => 2345 6789
              else if (group.length() == 5 && (groups.length - 1 > z + 1) && (groups[z + 1].length() == 3)) {
                groups[z + 1] = group.charAt(group.length() - 1) + groups[z + 1];
                rowList.add(group.substring(0, group.length() - 1));
                lrregularity++;
              }
              //如果都不符合上面的条件，则视为正常内容，放入到这行中
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
      ret.put("irregularityNumber", lrregularity);

      return ret;
    } catch (Exception e) {
      log.error("解析出错：{}", e.getMessage());
      e.printStackTrace();
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
    //转成页 行 组 格式
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
  private List<PostTelexPatTrainPageEntity> generateContent(int type, int generateNumber, int pageNumber, String trainId) {
    List<PostTelexPatTrainPageEntity> pageEntities = new ArrayList<>();
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
      PostTelexPatTrainPageEntity pageEntity = new PostTelexPatTrainPageEntity();
      pageEntity.setTrainId(trainId);
      pageEntity.setKey(key.toString());
      pageEntity.setPageNumber(pageNumber);
      pageEntity.setSort(i % 100);
      pageEntities.add(pageEntity);
    }
    return pageDao.save(pageEntities);
  }

  private List<PostTelexPatTrainPageEntity> generateContentAuto(List<String> content, int generateNumber, int pageNumber, String trainId) {
    List<PostTelexPatTrainPageEntity> pageEntities = new ArrayList<>();
    for (int i = 0; i < generateNumber; i++) {
      if (i % 100 == 0 && i != 0) {
        pageNumber += 1;
      }
      PostTelexPatTrainPageEntity pageEntity = new PostTelexPatTrainPageEntity();
      pageEntity.setTrainId(trainId);
      pageEntity.setKey(content.get(i));
      pageEntity.setPageNumber(pageNumber);
      pageEntity.setSort(i % 100);
      pageEntities.add(pageEntity);
    }

    return pageDao.save(pageEntities);
  }

}
