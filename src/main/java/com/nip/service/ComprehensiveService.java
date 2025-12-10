package com.nip.service;

import com.google.gson.reflect.TypeToken;
import com.nip.common.constants.TheoryKnowledgeTypeEnum;
import com.nip.common.utils.JSONUtils;
import com.nip.dao.*;
import com.nip.dto.ComprehensiveTheoryYearDto;
import com.nip.dto.sql.FindTheoryKnowledgeSwfTestDto;
import com.nip.dto.sql.FindUserMonthAvgScoreDto;
import com.nip.dto.vo.ComprehensiveAvgScore;
import com.nip.dto.vo.ComprehensiveExamYearVO;
import com.nip.dto.vo.ComprehensiveTheoryYearVO;
import com.nip.dto.vo.ComprehensiveVO;
import com.nip.entity.*;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static com.nip.common.constants.BaseConstants.TOKEN;

/**
 * @Author: wushilin
 * @Data: 2022-03-31 11:32
 * @Description:
 */
@ApplicationScoped
public class ComprehensiveService {

  private final UserService userService;
  private final TheoryKnowledgeExamUserDao examUserDao;
  private final TheoryKnowledgeExamTestPaperDao testPaperDao;
  private final TheoryKnowledgeSwfRecordDao swfRecordDao;
  private final TheoryKnowledgeDao ledgeDao;
  private final TheoryKnowledgeSwfDao swfDao;
  private final TheoryKnowledgeTestUserDao testUserDao;
  private final TestPaperQuestionDao testPaperQuestionDao;
  private final TheoryKnowledgeTestFallibleDao testFallibleDao;

  @Inject
  public ComprehensiveService(UserService userService,
                              TheoryKnowledgeExamUserDao examUserDao,
                              TheoryKnowledgeExamTestPaperDao testPaperDao,
                              TheoryKnowledgeSwfRecordDao swfRecordDao,
                              TheoryKnowledgeDao ledgeDao,
                              TheoryKnowledgeSwfDao swfDao,
                              TheoryKnowledgeTestUserDao testUserDao,
                              TestPaperQuestionDao testPaperQuestionDao,
                              TheoryKnowledgeTestFallibleDao testFallibleDao
  ) {
    this.userService = userService;
    this.examUserDao = examUserDao;
    this.testPaperDao = testPaperDao;
    this.swfRecordDao = swfRecordDao;
    this.ledgeDao = ledgeDao;
    this.swfDao = swfDao;
    this.testUserDao = testUserDao;
    this.testPaperQuestionDao = testPaperQuestionDao;
    this.testFallibleDao = testFallibleDao;
  }

  public ComprehensiveVO getUserOverallInfo(HttpServerRequest request) {
    //构建返回结果
    ComprehensiveVO comprehensiveVO = new ComprehensiveVO();

    //通过token查询用户信息
    String token = request.getHeader(TOKEN);
    UserEntity userEntity = userService.getUserByToken(token);
    comprehensiveVO.setUserEntity(userEntity);

    //查询用户考试次数
    Integer examNum = examUserDao.countByUserIdAndStateGreaterThanEqual(userEntity.getId(), 4);
    comprehensiveVO.setExamNum(examNum);

    //查询考试合格次数
    Integer passNum = testPaperDao.getByUserIdPassNum(userEntity.getId()).intValue();
    comprehensiveVO.setPassNum(passNum);

    //已学课件(t_theory_knowledge_test_user表中有有记录就算已学课件)
    Integer swfNum = testUserDao.countByUserId(userEntity.getId());
    comprehensiveVO.setSwfNum(swfNum);

    //学习时长
    BigDecimal studyTime = Optional.ofNullable(swfRecordDao.countStudyTimeByUserId(userEntity.getId()))
        .orElse(BigDecimal.ZERO);
    comprehensiveVO.setStudyTime(studyTime);

    //易错题
    List<TestPaperQuestionEntity> errorSubject = countErrorSubject(userEntity.getId());
    List<String> errorTopic = errorSubject.stream().map(TestPaperQuestionEntity::getTopic).toList();
    comprehensiveVO.setErrorTopic(errorTopic);

    //理论学习时长
    comprehensiveVO.setTheoryTime(studyTime);

    //已得学分
    comprehensiveVO.setTotalCredit(countTotalCredit(userEntity.getId()));

    //最高得分
    BigDecimal maxScore = Optional.ofNullable(examUserDao.findByUserIdMaxScore(userEntity.getId()))
        .orElse(BigDecimal.ZERO);
    comprehensiveVO.setTheoryTestMaxCredit(maxScore);

    //最低得分
    BigDecimal minScore = Optional.ofNullable(examUserDao.findByUserIdMinScore(userEntity.getId()))
        .orElse(BigDecimal.ZERO);
    comprehensiveVO.setTheoryTestMinCredit(minScore);

    //平均分
    BigDecimal avgScore = Optional.ofNullable(examUserDao.findByUserIdAvgScore(userEntity.getId()))
        .orElse(BigDecimal.ZERO);
    comprehensiveVO.setTheoryTestAvgCredit(avgScore);

    return comprehensiveVO;
  }


  public ComprehensiveTheoryYearVO getTheoryYear(String year, HttpServerRequest request) {
    UserEntity userEntity = userService.getUserByToken(request.getHeader(TOKEN));
    //返回结果
    ComprehensiveTheoryYearVO result = new ComprehensiveTheoryYearVO();

    //统计各个理论学分
    Map<Integer, BigDecimal> typeCredit = countTypeCredit(userEntity, year);
    result.setBaseTheoryCredit(
        Optional.ofNullable(typeCredit.get(TheoryKnowledgeTypeEnum.BASE.getType())).orElse(BigDecimal.ZERO));
    result.setEquipCredit(
        Optional.ofNullable(typeCredit.get(TheoryKnowledgeTypeEnum.QEUIP.getType())).orElse(BigDecimal.ZERO));
    result.setWorkCredit(
        Optional.ofNullable(typeCredit.get(TheoryKnowledgeTypeEnum.WORK.getType())).orElse(BigDecimal.ZERO));

    //统计各个月份的学习时长和课件数量
    List<ComprehensiveTheoryYearVO.TheoryYearInfoVO> yearInfoVOS = getTheoryYearInfoVOS(userEntity, year);
    result.setYearInfoVO(yearInfoVOS);
    return result;
  }

  public ComprehensiveExamYearVO getTheoryTestYear(String year, HttpServerRequest request) {
    UserEntity userEntity = userService.getUserByToken(request.getHeader(TOKEN));
    ComprehensiveExamYearVO result = new ComprehensiveExamYearVO();
    //得到各个月份的学习时长和课件数量
    List<ComprehensiveTheoryYearVO.TheoryYearInfoVO> theoryYearInfoVOS = getTheoryYearInfoVOS(userEntity, year);
    //查询用户平均分
    List<FindUserMonthAvgScoreDto> byUserMonthAvgScore = examUserDao.findByUserMonthAvgScore(userEntity.getId(), year);
    List<ComprehensiveAvgScore> avgScores = new ArrayList<>();
    for (FindUserMonthAvgScoreDto dto : byUserMonthAvgScore) {
      //月份
      ComprehensiveAvgScore avgScore = new ComprehensiveAvgScore(dto.getM(), dto.getAvg());
      avgScores.add(avgScore);
    }

    return result.setAvgScores(avgScores).setTheoryYearInfoVOS(theoryYearInfoVOS);
  }

  /**
   * 统计各个月份的学习时长和课件数量
   *
   * @param: userEntity
   */
  private List<ComprehensiveTheoryYearVO.TheoryYearInfoVO> getTheoryYearInfoVOS(UserEntity userEntity, String year) {
    List<ComprehensiveTheoryYearVO.TheoryYearInfoVO> ret = new ArrayList<>();
    //先查询课件数量
    List<Map<String, Object>> countSwfNum = testUserDao.countSwfNum(userEntity.getId(), year + "%");
    Map<String, Integer> countSwfNumRet = new HashMap<>();
    //将课件数量转换以下
    for (Map<String, Object> map : countSwfNum) {
      String time = String.valueOf(Integer.valueOf(map.get("time").toString().substring(5)));
      Integer swfNum = Integer.valueOf(map.get("count").toString());
      countSwfNumRet.put(time, swfNum);
    }
    //统计各个月的学习时间
    List<Map<String, Object>> studyTime = swfRecordDao.countStudyTime(userEntity.getId(), year + "%");
    Map<String, BigDecimal> studyTimeMap = new HashMap<>();
    for (Map<String, Object> map : studyTime) {
      String date = String.valueOf(Integer.valueOf(map.get("date").toString().substring(5)));
      BigDecimal time = new BigDecimal(map.get("time").toString());
      studyTimeMap.put(date, time);
    }

    //将上面2个Map合并成一个对象
    for (int i = 1; i < 13; i++) {
      Integer swfNum = countSwfNumRet.get(String.valueOf(i));
      BigDecimal time = studyTimeMap.get(String.valueOf(i));
      if (swfNum != null || time != null) {
        ComprehensiveTheoryYearVO.TheoryYearInfoVO element = new ComprehensiveTheoryYearVO.TheoryYearInfoVO();
        element.setMoth(String.valueOf(i));
        element.setStudyTime(time);
        element.setSwfNum(swfNum);
        ret.add(element);
      }
    }
    return ret;
  }

  /**
   * 统计各个理论学分
   *
   * @param: userEntity
   */
  private Map<Integer, BigDecimal> countTypeCredit(UserEntity userEntity, String year) {
    List<FindTheoryKnowledgeSwfTestDto> knowledgeAndSwfAndTest = ledgeDao.findKnowledgeAndSwfAndTest(
        userEntity.getId(), year);
    List<ComprehensiveTheoryYearDto> yearDtoList = JSONUtils.fromJson(
        JSONUtils.toJson(knowledgeAndSwfAndTest), new TypeToken<>() {
        });

    //先按knowledgeId 分组
    Map<String, List<ComprehensiveTheoryYearDto>> collect = yearDtoList.stream().collect(
        Collectors.groupingBy(ComprehensiveTheoryYearDto::getKId));
    List<ComprehensiveTheoryYearDto> theoryYearDtos = new ArrayList<>();
    //根据用户id进行过滤
    for (Map.Entry<String, List<ComprehensiveTheoryYearDto>> entry : collect.entrySet()) {
      List<ComprehensiveTheoryYearDto> yearDtos = entry.getValue();
      boolean mark = true;
      for (ComprehensiveTheoryYearDto dto : yearDtos) {
        if (Objects.isNull(dto.getUserId())) {
          mark = false;
          break;
        }
      }
      if (mark) {
        // 可以得到学分记录保留到新的集合中
        theoryYearDtos.add(yearDtos.getFirst());
      }
    }
    //按类型分组
    Map<Integer, List<ComprehensiveTheoryYearDto>> types = theoryYearDtos.stream().collect(
        Collectors.groupingBy(ComprehensiveTheoryYearDto::getType));
    Iterator<Integer> typesIterator = types.keySet().iterator();
    Map<Integer, BigDecimal> typeCredit = new HashMap<>();
    //计算学分
    while (typesIterator.hasNext()) {
      Integer key = typesIterator.next();
      List<ComprehensiveTheoryYearDto> dtos = types.get(key);
      BigDecimal credit = dtos.stream().map(ComprehensiveTheoryYearDto::getCredit).filter(Objects::nonNull)
          .reduce(BigDecimal.ZERO, BigDecimal::add);
      typeCredit.put(key, credit);
    }
    return typeCredit;
  }

  /**
   * 根据userId统计总学分
   *
   * @param: userId
   */
  private BigDecimal countTotalCredit(String userId) {
    //查询所有知识
    List<TheoryKnowledgeEntity> ledgeAll = ledgeDao.findAll().list();
    List<String> ledgeIds = ledgeAll.stream().map(TheoryKnowledgeEntity::getId).toList();

    //通过知识ID查询对应的课件并将课件按KnowledgeId分组
    List<TheoryKnowledgeSwfEntity> swfList = swfDao.findAllByKnowledgeIdIn(ledgeIds);
    Map<String, List<TheoryKnowledgeSwfEntity>> ledgeMapSwf = swfList.stream().collect(
        Collectors.groupingBy(TheoryKnowledgeSwfEntity::getKnowledgeId));

    //得到课件中所有的测试题,并按swfId 分组
    List<String> swfIds = swfList.stream().map(TheoryKnowledgeSwfEntity::getId).toList();

    //查询已提交的答案并按swfId 分组
    List<TheoryKnowledgeTestUserEntity> testUserEntityList = testUserDao.findAllByUserIdAndKnowledgeSwfIdIn(
        userId, swfIds);
    Map<String, List<TheoryKnowledgeTestUserEntity>> swfMapTest = testUserEntityList.stream().collect(
        Collectors.groupingBy(TheoryKnowledgeTestUserEntity::getKnowledgeSwfId));

    //返回学分
    return ledgeAll.stream()
        //过滤掉未完成的Knowledge
        .filter(ledge -> {
          List<TheoryKnowledgeSwfEntity> swfs = ledgeMapSwf.get(ledge.getId());
          boolean mark = true;
          if (swfs == null) {
            return false;
          }
          //通过swf的id查询提交测验记录，如果有则得到该学分
          for (TheoryKnowledgeSwfEntity swf : swfs) {
            List<TheoryKnowledgeTestUserEntity> userEntityList = swfMapTest.get(swf.getId());
            if (userEntityList == null || userEntityList.isEmpty()) {
              mark = false;
              break;
            }
          }
          return mark;
        }).map(TheoryKnowledgeEntity::getCredit).filter(Objects::nonNull).map(BigDecimal::new)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }


  /**
   * 统计易错题
   *
   * @param: id
   */
  private List<TestPaperQuestionEntity> countErrorSubject(String id) {
    TheoryKnowledgeTestFallibleEntity byUserId = testFallibleDao.findByUserId(id);
    //根据id查询已考试的题目
    List<TheoryKnowledgeExamUserEntity> examUserEntityList = examUserDao.findByUserId(id);
    if (null == byUserId || byUserId.getNumber() != examUserEntityList.size()) {
      //获取content字段
      List<TheoryKnowledgeQuestionEntity> paperDtos = new ArrayList<>();
      for (TheoryKnowledgeExamUserEntity entity : examUserEntityList) {
        if (entity.getContent() != null) {
          Map<String, Object> map = JSONUtils.fromJson(entity.getContent(), new TypeToken<>() {
          });
          map.forEach((k, v) -> {
            List<Map<String, Object>> completionList = JSONUtils.fromJson(JSONUtils.toJson(v), new TypeToken<>() {
            });
            for (Map<String, Object> ma : completionList) {
              TheoryKnowledgeQuestionEntity e = new TheoryKnowledgeQuestionEntity();
              e.setId(ma.get("id").toString());
              e.setAnswer(ma.get("answer").toString());
              paperDtos.add(e);
            }
          });
        }
      }
      //过滤掉正确的答案留下做错的题
      List<TestPaperQuestionEntity> questionEntities = handleQuestionEntities(paperDtos);

      //把做错的题按id分组，找出做错最多的题
      Map<String, Long> errorCountMap = questionEntities.stream()
          .collect(Collectors.groupingBy(TestPaperQuestionEntity::getId, Collectors.counting()));

      // 按错误次数降序排序，并取前5个
      List<TestPaperQuestionEntity> list = errorCountMap.entrySet().stream()
          .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
          .limit(5)
          .map(entry -> questionEntities.stream()
              .filter(q -> q.getId().equals(entry.getKey()))
              .findFirst()
              .orElse(null))
          .filter(Objects::nonNull)
          .toList();
      TheoryKnowledgeTestFallibleEntity fallible;
      fallible = Objects.requireNonNullElseGet(byUserId, TheoryKnowledgeTestFallibleEntity::new);
      fallible.setNumber(examUserEntityList.size());
      fallible.setUserId(id);
      fallible.setContent(JSONUtils.toJson(list));
      testFallibleDao.save(fallible);
      return list;
    }
    return JSONUtils.fromJson(byUserId.getContent(), new TypeToken<>() {
    });
  }

  private List<TestPaperQuestionEntity> handleQuestionEntities(List<TheoryKnowledgeQuestionEntity> paperDtos) {
    List<TestPaperQuestionEntity> questionEntities = new ArrayList<>();
    paperDtos.forEach(paper -> {
      //通过id跟answer查询如有没有记录则保留
      Optional<TestPaperQuestionEntity> optional = Optional.ofNullable(testPaperQuestionDao.findById(paper.getId()));
      if (optional.isPresent()) {
        TestPaperQuestionEntity entity = optional.get();
        if (!Objects.equals(entity.getAnswer(), paper.getAnswer())) {
          questionEntities.add(entity);
        }
      }
    });
    return questionEntities;
  }
}
