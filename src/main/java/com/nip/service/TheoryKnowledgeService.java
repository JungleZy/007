package com.nip.service;

import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.google.gson.reflect.TypeToken;
import com.nip.common.constants.ResponseCode;
import com.nip.common.constants.TheoryKnowledgeClassifyTypeEnum;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.common.utils.DateTimeUtil;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.ListUtils;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.*;
import com.nip.dto.TheoryKnowledgeDto;
import com.nip.dto.TheoryKnowledgesDto;
import com.nip.dto.sql.FindTheoryKnowledgeDto;
import com.nip.dto.sql.ExamScoreThresholdDto;
import com.nip.dto.vo.TheoryKnowledgeSwfVO;
import com.nip.dto.vo.TheoryKnowledgeTestVO;
import com.nip.entity.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.text.DecimalFormat;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * TheoryKnowledgeSwfService
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2021-12-22 18:40
 */
@Slf4j
@ApplicationScoped
public class TheoryKnowledgeService {
  private static final DecimalFormat df = new DecimalFormat("0.00");

  private final UserService userService;
  private final TheoryKnowledgeDao knowledgeDao;
  private final TheoryKnowledgeSwfDao knowledgeSwfDao;
  private final TheoryKnowledgeSwfRecordDao knowledgeRecordDao;
  private final TheoryKnowledgeTestUserDao theoryKnowledgeTestUserDao;
  private final TheoryKnowledgeTestDao theoryKnowledgeTestDao;
  private final TheoryKnowledgeTestContentDao theoryKnowledgeTestContentDao;
  private final TheoryKnowledgeExamUserDao theoryKnowledgeExamUserDao;
  private final TheoryKnowledgeClassifyDao classifyDao;

  @Inject
  public TheoryKnowledgeService(UserService userService,
                                TheoryKnowledgeDao knowledgeDao,
                                TheoryKnowledgeSwfDao knowledgeSwfDao,
                                TheoryKnowledgeSwfRecordDao knowledgeRecordDao,
                                TheoryKnowledgeTestUserDao theoryKnowledgeTestUserDao,
                                TheoryKnowledgeTestDao theoryKnowledgeTestDao,
                                TheoryKnowledgeTestContentDao theoryKnowledgeTestContentDao,
                                TheoryKnowledgeExamUserDao theoryKnowledgeExamUserDao,
                                TheoryKnowledgeClassifyDao classifyDao) {
    this.userService = userService;
    this.knowledgeDao = knowledgeDao;
    this.knowledgeSwfDao = knowledgeSwfDao;
    this.knowledgeRecordDao = knowledgeRecordDao;
    this.theoryKnowledgeTestUserDao = theoryKnowledgeTestUserDao;
    this.theoryKnowledgeTestDao = theoryKnowledgeTestDao;
    this.theoryKnowledgeTestContentDao = theoryKnowledgeTestContentDao;
    this.theoryKnowledgeExamUserDao = theoryKnowledgeExamUserDao;
    this.classifyDao = classifyDao;
  }

  /**
   * 根据ID获取理论知识信息
   *
   * @param id 理论知识的ID
   * @return 返回包含理论知识DTO的响应对象
   */
  public Response<TheoryKnowledgesDto> getById(String id) {
    TheoryKnowledgeEntity knowledge = knowledgeDao.findById(id);
    if (ObjectUtil.isEmpty(knowledge)) {
      return ResponseResult.error("未查到到数据");
    }
    List<TheoryKnowledgeSwfEntity> swfEntities = knowledgeSwfDao.findAllByKnowledgeIdOrderBySortAsc(knowledge.getId());
    TheoryKnowledgesDto knowledgeDto = new TheoryKnowledgesDto();
    knowledgeDto.setKnowledge(knowledge);
    knowledgeDto.setKnowledgeSwfs(assembleSwfs(swfEntities, null));
    return ResponseResult.success(knowledgeDto);
  }

  /**
   * 根据知识ID和用户Token获取理论知识信息
   * 此方法首先验证用户身份，然后加载指定的理论知识实体及其相关数据
   * 包括与知识相关的SWF资源、测试信息和用户学习记录等
   *
   * @param id    知识的唯一标识符
   * @param token 用户的认证令牌
   * @return 包含理论知识详细信息的响应对象
   */
  public Response<TheoryKnowledgesDto> getByIdAndToken(String id, String token) {
    UserEntity userEntity = userService.getUserByToken(token);
    TheoryKnowledgeEntity knowledge = knowledgeDao.findById(id);
    if (knowledge == null) {
      return ResponseResult.error("数据异常");
    }
    List<TheoryKnowledgeSwfEntity> swfEntities = knowledgeSwfDao.findAllByKnowledgeIdOrderBySortAsc(knowledge.getId());
    TheoryKnowledgesDto knowledgeDto = new TheoryKnowledgesDto();
    knowledgeDto.setKnowledge(knowledge);
    knowledgeDto.setKnowledgeSwfs(assembleSwfs(swfEntities, userEntity.getId()));
    return ResponseResult.success(knowledgeDto);
  }

  /**
   * 批量装配课件树：一次性取回全部测验与考题；当 userId 非空时再各批量取回答案与学习记录，
   * 按外键分组后派生 haveTest/score/record，消除按课件、按测验的 N+1 查询。
   * DAO 结果已按 createTime 升序，分组保持每课件/每测验的顺序。
   *
   * @param swfs   已按 sort 升序的课件实体
   * @param userId 用户ID；为 null 时仅装配测验树（score/haveTest/record 保留实体默认值）
   * @return 课件 VO 列表
   */
  private List<TheoryKnowledgeSwfVO> assembleSwfs(List<TheoryKnowledgeSwfEntity> swfs, String userId) {
    List<String> swfIds = swfs.stream().map(TheoryKnowledgeSwfEntity::getId).toList();
    List<TheoryKnowledgeTestEntity> tests = theoryKnowledgeTestDao.findAllByKnowledgeSwfIdInOrderByCreateTimeAsc(swfIds);
    List<String> testIds = tests.stream().map(TheoryKnowledgeTestEntity::getId).toList();
    List<TheoryKnowledgeTestContentEntity> contents = theoryKnowledgeTestContentDao.findAllByKnowledgeTestIdInOrderByCreateTimeAsc(testIds);

    Map<String, List<TheoryKnowledgeTestEntity>> testsBySwf = tests.stream()
        .collect(Collectors.groupingBy(TheoryKnowledgeTestEntity::getKnowledgeSwfId));
    Map<String, List<TheoryKnowledgeTestContentEntity>> contentsByTest = contents.stream()
        .collect(Collectors.groupingBy(TheoryKnowledgeTestContentEntity::getKnowledgeTestId));

    Map<String, TheoryKnowledgeTestUserEntity> answerBySwf;
    Map<String, List<TheoryKnowledgeSwfRecordEntity>> recordsBySwf;
    if (userId != null) {
      answerBySwf = theoryKnowledgeTestUserDao.findAllByUserIdAndKnowledgeSwfIdIn(userId, swfIds).stream()
          .collect(Collectors.toMap(TheoryKnowledgeTestUserEntity::getKnowledgeSwfId,
              Function.identity(), (first, ignored) -> first));
      recordsBySwf = knowledgeRecordDao.findAllByUserIdAndKnowledgeSwfIdIn(userId, swfIds).stream()
          .collect(Collectors.groupingBy(TheoryKnowledgeSwfRecordEntity::getKnowledgeSwfId));
    } else {
      answerBySwf = Map.of();
      recordsBySwf = Map.of();
    }

    return PojoUtils.convert(swfs, TheoryKnowledgeSwfVO.class, (e, v) -> {
      List<TheoryKnowledgeTestEntity> swfTests = testsBySwf.getOrDefault(e.getId(), List.of());
      List<TheoryKnowledgeTestVO> testVos = PojoUtils.convert(swfTests, TheoryKnowledgeTestVO.class,
          (testEntity, testVo) -> testVo.setKnowledgeTestContents(
              contentsByTest.getOrDefault(testEntity.getId(), List.of())));
      v.setTest(testVos);
      if (userId != null) {
        v.setHaveTest(swfTests.stream().anyMatch(t -> Objects.equals(t.getVersions(), 1)));
        TheoryKnowledgeTestUserEntity answer = answerBySwf.get(e.getId());
        v.setScore(answer == null ? 0 : answer.getScore());
        long seconds = recordsBySwf.getOrDefault(e.getId(), List.of()).stream()
            .mapToLong(r -> DateUtil.between(DateUtil.parse(r.getJoinTime()), DateUtil.parse(r.getExitTime()), DateUnit.SECOND))
            .sum();
        v.setRecord(seconds / 60.0);
      }
    });
  }

  /**
   * 获取理论知识列表
   * 该方法根据类型、难度和专业来查询理论知识
   * 如果未指定难度或专业，则默认查询所有难度或所有专业
   *
   * @param type       查询类型，用于指定查询的类型（如：全部、部分等）
   * @param difficulty 难度列表，指定要查询的难度级别如果为null，则查询所有难度
   * @param specialty  专业列表，指定要查询的专业领域如果为null，则查询所有专业
   * @return 返回一个包含找到的理论知识DTO列表的响应对象
   */
  public Response<List<FindTheoryKnowledgeDto>> getAll(int type, List<String> difficulty, List<String> specialty) {
    //判断是否查询全部
    if (difficulty == null) {
      difficulty = classifyDao.findAllByType(TheoryKnowledgeClassifyTypeEnum.difficulty.getType())
          .stream()
          .map(TheoryKnowledgeClassifyEntity::getId)
          .toList();
    }
    if (specialty == null) {
      specialty = classifyDao.findAllByType(TheoryKnowledgeClassifyTypeEnum.specialty.getType())
          .stream()
          .map(TheoryKnowledgeClassifyEntity::getId)
          .toList();
    }
    List<FindTheoryKnowledgeDto> res = knowledgeDao.findTheoryKnowledgeDtoAllSql(type, difficulty, specialty);
    return ResponseResult.success(res);
  }

  /**
   * 获取所有理论知识信息
   *
   * @param type       类型，用于筛选理论知识的类型
   * @param status     状态，用于筛选理论知识的状态
   * @param token      用户令牌，用于验证用户身份
   * @param difficulty 难度列表，用于筛选理论知识的难度，默认为全部难度
   * @param specialty  专业列表，用于筛选理论知识的专业，默认为全部专业
   * @return 返回包含理论知识DTO列表的响应对象
   */
  public Response<List<TheoryKnowledgeDto>> getAll(int type, int status, String token, List<String> difficulty, List<String> specialty) {
    //判断是否查询全部
    if (difficulty == null) {
      difficulty = classifyDao.findAllByType(TheoryKnowledgeClassifyTypeEnum.difficulty.getType())
          .stream()
          .map(TheoryKnowledgeClassifyEntity::getId)
          .toList();
    }
    if (specialty == null) {
      specialty = classifyDao.findAllByType(TheoryKnowledgeClassifyTypeEnum.specialty.getType())
          .stream()
          .map(TheoryKnowledgeClassifyEntity::getId)
          .toList();
    }
    UserEntity userEntity = userService.getUserByToken(token);
    List<FindTheoryKnowledgeDto> dtoAllSql = knowledgeDao.findTheoryKnowledgeDtoAllSqlOpen(type, status, difficulty, specialty);
    List<TheoryKnowledgeDto> all = PojoUtils.convert(dtoAllSql, TheoryKnowledgeDto.class);
    all.forEach(study -> {
      List<TheoryKnowledgeTestUserEntity> allByUserIdAndKnowledgeIdAndScore = theoryKnowledgeTestUserDao.findAllByUserIdAndKnowledgeIdAndScore(userEntity.getId(), study.getId(), 100);
      study.setDoneCount(allByUserIdAndKnowledgeIdAndScore.size());
      List<TheoryKnowledgeTestEntity> allByKnowledgeIdAndVersions = theoryKnowledgeTestDao.findAllByKnowledgeIdAndVersions(study.getId(), 1);
      study.setSwfTestCount(allByKnowledgeIdAndVersions.size());
    });
    return ResponseResult.success(all);
  }

  /**
   * 保存理论知识信息
   *
   * @param knowledgesDto 理论知识DTO对象，包含需要保存的知识信息
   * @return 返回保存后的理论知识实体对象
   */
  @Transactional
  public Response<TheoryKnowledgeEntity> saveTheoryKnowledge(TheoryKnowledgesDto knowledgesDto) {
    if (ObjectUtil.isEmpty(knowledgesDto.getKnowledge().getTitle())) {
      throw new InvalidTitleException("标题不能是空!");
    }
    if (knowledgesDto.getKnowledgeSwfs() == null) {
      throw new IllegalArgumentException("课件列表缺失");
    }
    knowledgesDto.getKnowledgeSwfs().forEach(s -> {
      if (StringUtils.isEmpty(s.getTitle())) {
        throw new IllegalArgumentException("标题不能是空!");
      }
    });
    TheoryKnowledgeEntity knowledge = knowledgeDao.save(knowledgesDto.getKnowledge());
    //输入校验全部通过后再删除之前的课件
    knowledgeSwfDao.deleteAllByKnowledgeId(knowledgesDto.getKnowledge().getId());
    knowledgesDto.getKnowledgeSwfs().forEach(s -> {
      if (StringUtils.isEmpty(s.getId())) {
        s.setKnowledgeId(knowledge.getId());
      }
        //如果id是null则需要设置默认值
        if (s.getId() == null) {
          s.setCreateUserId(knowledge.getCreateUserId());
        }
        //保存到数据库中
        TheoryKnowledgeSwfEntity swfEntity = PojoUtils.convertOne(s, TheoryKnowledgeSwfEntity.class);
        TheoryKnowledgeSwfEntity saveSwfEntity = knowledgeSwfDao.save(swfEntity);
        //拿到测验test
        List<TheoryKnowledgeTestVO> testVOS = ListUtils.nullToEmpty(s.getTest());
        testVOS.forEach(test -> {
          //当ID是null的时候，设置默认值
          if (test.getId() == null) {
            test.setCreateUserId(knowledge.getCreateUserId());
            test.setKnowledgeId(knowledge.getId());
            test.setKnowledgeSwfId(saveSwfEntity.getId());
            test.setCreateTime(System.currentTimeMillis() + "");
          } else {
            if (test.getVersions() == 1) {
              TheoryKnowledgeTestEntity firstByKnowledgeSwfIdAndVersions = theoryKnowledgeTestDao.findFirstByKnowledgeSwfIdAndVersions(test.getKnowledgeSwfId(), 1);
              if (firstByKnowledgeSwfIdAndVersions == null) {
                throw new IllegalStateException("版本1测验不存在: swfId=" + test.getKnowledgeSwfId());
              }
              TheoryKnowledgeTestEntity testEntity = PojoUtils.convertOne(test, TheoryKnowledgeTestEntity.class, (t, e) -> e.setKnowledgeSwfId(saveSwfEntity.getId()));
              if (!firstByKnowledgeSwfIdAndVersions.getId().equals(testEntity.getId())) {
                theoryKnowledgeTestUserDao.deleteByKnowledgeIdAndKnowledgeSwfId(test.getKnowledgeId(), test.getKnowledgeSwfId());
              } else {
                List<TheoryKnowledgeTestContentEntity> allByKnowledgeTestId = theoryKnowledgeTestContentDao.findAllByKnowledgeTestId(testEntity.getId());
                List<TheoryKnowledgeTestContentEntity> knowledgeTestContents = ListUtils.nullToEmpty(test.getKnowledgeTestContents());
                if (!listEquals(allByKnowledgeTestId, knowledgeTestContents)) {
                  theoryKnowledgeTestUserDao.deleteByKnowledgeIdAndKnowledgeSwfId(test.getKnowledgeId(), test.getKnowledgeSwfId());
                }
              }
            }
          }
          TheoryKnowledgeTestEntity testEntity = PojoUtils.convertOne(test, TheoryKnowledgeTestEntity.class, (t, e) -> {
            e.setKnowledgeSwfId(saveSwfEntity.getId());
          });
          TheoryKnowledgeTestEntity saveTest = theoryKnowledgeTestDao.save(testEntity);
          //再拿到content
          List<TheoryKnowledgeTestContentEntity> knowledgeTestContents = ListUtils.nullToEmpty(test.getKnowledgeTestContents());
          theoryKnowledgeTestContentDao.deleteByKnowledgeIdAndCreateUserIdAndKnowledgeSwfIdAndKnowledgeTestId(
              knowledge.getId(), knowledge.getCreateUserId(), saveSwfEntity.getId(), saveTest.getId()
          );
          knowledgeTestContents.forEach(content -> {
            //新增记录设置默认值
            if (content.getId() == null) {
              content.setCreateUserId(knowledge.getCreateUserId());
              content.setKnowledgeId(knowledge.getId());
              content.setKnowledgeSwfId(saveSwfEntity.getId());
              content.setKnowledgeTestId(saveTest.getId());
            }
            theoryKnowledgeTestContentDao.save(content);
          });
        });
      });
    return ResponseResult.success(knowledge);
  }

  /**
   * 比较两个列表是否相等
   *
   * @param t1 第一个列表，泛型类型为T
   * @param t2 第二个列表，泛型类型为T
   * @return 如果两个列表相等返回true，否则返回false
   * <p>
   * 此方法主要解决列表内容的相等问题，包括对列表引用、大小和元素的比较
   * 1. 首先判断两个列表的引用是否相同，如果相同则认为它们相等
   * 2. 如果引用不同，再比较它们的大小，大小不同则不相等
   * 3. 大小相同的情况下，遍历第一个列表的每个元素，检查它是否被第二个列表包含
   * 如果有任一元素不在第二个列表中，则认为两个列表不相等
   * 4. 此方法使用了泛型，可以适用于任何类型的列表比较
   */
  public static <T> boolean listEquals(List<T> t1, List<T> t2) {
    if (t1 == t2) { // 为空or引用地址一致时
      return true;
    } else if (t1.size() != t2.size()) { // 数量一致, 过滤掉了list1中有{1,1,3},list2中有{1,3,4}的场景
      return false;
    }

    for (T t : t1) {
      if (!t2.contains(t)) { // equals比较
        return false;
      }
    }
    return true;
  }

  /**
   * 保存理论知识记录
   * <p>
   * 此方法用于保存用户学习理论知识的记录，包括用户进入和退出学习的时间
   * 它依赖于用户令牌来验证用户身份，并根据用户的学习时间来更新记录
   *
   * @param token  用户身份令牌，用于识别和验证用户
   * @param record 理论知识学习记录实体，包含学习的详细信息
   * @return 返回保存后的学习记录实体，如果学习时间无效，则返回空响应
   */
  @Transactional
  public Response<TheoryKnowledgeSwfRecordEntity> saveTheoryKnowledgeRecord(String token, TheoryKnowledgeSwfRecordEntity record) {
    UserEntity userEntity = userService.getUserByToken(token);
    if (ObjectUtil.isEmpty(record.getJoinTime())) {
      record.setJoinTime(DateTimeUtil.now());
      return new Response<>(ResponseCode.SUCCESS.getCode(), record);
    }
    if (ObjectUtil.isEmpty(record.getExitTime())) {
      record.setExitTime(DateTimeUtil.now());
    }
    if (1 <= DateUtil.between(DateUtil.parse(record.getJoinTime()), DateUtil.parse(record.getExitTime()), DateUnit.MINUTE)) {
      record.setUserId(userEntity.getId());
      TheoryKnowledgeEntity theoryKnowledgeEntity = Optional.ofNullable(knowledgeDao.findById(record.getKnowledgeId()))
          .orElseThrow(() -> new IllegalArgumentException("未查询到该教案"));
      record.setType(theoryKnowledgeEntity.getType());
      TheoryKnowledgeSwfRecordEntity save = knowledgeRecordDao.save(record);
      return ResponseResult.success(save);
    } else {
      return ResponseResult.success();
    }
  }

  /**
   * 根据ID删除知识条目
   * <p>
   * 该方法通过调用knowledgeDao的deleteById方法来删除指定ID的知识条目
   * 主要用于处理知识条目的删除请求，通过提供知识条目的唯一标识符（ID）来完成删除操作
   *
   * @param id 要删除的知识条目的唯一标识符
   * @return 返回一个Response对象，表示删除操作的结果
   */
  public Response<Void> deleteThroyKnowledgeById(String id) {
    knowledgeDao.deleteById(id);
    return ResponseResult.success();
  }

  /**
   * 统计记录
   * 根据用户令牌、年份、月份和类型查询统计信息
   *
   * @param token 用户身份令牌，用于识别用户
   * @param year  年份，用于查询年统计信息
   * @param month 月份，用于查询月统计信息，如果为空则进行年查询
   * @param type  记录类型，用于过滤查询结果
   * @return 返回包含统计信息的响应对象
   */
  public Response<Map<String, Object>> recordStatistice(String token, String year, String month, int type) {
    UserEntity userEntity = userService.getUserByToken(token);
    Map<String, Object> re = new HashMap<>();
    if (ObjectUtil.isEmpty(month)) {
      //年查询
      List<TheoryKnowledgeSwfRecordEntity> allByUserIdAndJoinTimeLike = knowledgeRecordDao.findAllByUserIdAndJoinTimeLikeAndType(userEntity.getId(), year + "%", type);
      if (!allByUserIdAndJoinTimeLike.isEmpty()) {
        re.put("up", check(allByUserIdAndJoinTimeLike, year, month));
        re.put("down", count(allByUserIdAndJoinTimeLike, userEntity.getId()));
      }
      return ResponseResult.success(re);
    } else {
      //月查询
      List<TheoryKnowledgeSwfRecordEntity> allByUserIdAndJoinTimeLike = knowledgeRecordDao.findAllByUserIdAndJoinTimeLikeAndType(userEntity.getId(), year + "-" + padTwoDigits(Integer.valueOf(month)) + "%", type);
      if (!allByUserIdAndJoinTimeLike.isEmpty()) {
        re.put("up", check(allByUserIdAndJoinTimeLike, year, month));
        re.put("down", count(allByUserIdAndJoinTimeLike, userEntity.getId()));
      }
      return ResponseResult.success(re);
    }
  }

  /**
   * 统计用户学习理论知识的时间
   *
   * @param list   用户学习记录列表，包含用户加入和退出学习的时间
   * @param userId 用户ID，用于查询用户特定的理论知识学习记录
   * @return 返回一个Map对象，键为理论知识实体，值为该知识各个SWF文件的学习时长
   */
  private Map<Object, Object> count(List<TheoryKnowledgeSwfRecordEntity> list, String userId) {
    // 直接聚合为 knowledgeId -> (swfId -> 秒数)，避免字符串拼接/拆分
    Map<String, Map<String, Long>> durationsByKnowledge = new HashMap<>();
    for (TheoryKnowledgeSwfRecordEntity a : list) {
      long between = DateUtil.between(DateUtil.parse(a.getJoinTime()), DateUtil.parse(a.getExitTime()), DateUnit.SECOND);
      durationsByKnowledge
          .computeIfAbsent(a.getKnowledgeId(), k -> new HashMap<>())
          .merge(a.getKnowledgeSwfId(), between, Long::sum);
    }

    List<String> knowledgeIds = list.stream()
        .map(TheoryKnowledgeSwfRecordEntity::getKnowledgeId).distinct().toList();

    // 三次批量取回，替代循环内逐知识/逐课件查询
    Map<String, TheoryKnowledgeEntity> knowledgeById = knowledgeDao.list("id in ?1", knowledgeIds).stream()
        .collect(Collectors.toMap(TheoryKnowledgeEntity::getId, Function.identity(), (first, ignored) -> first));
    // 该知识【全部】课件（不限本期已学）：既作完成判定分母，也覆盖 perSwf 的标题映射
    List<TheoryKnowledgeSwfEntity> swfEntities = knowledgeSwfDao.list("knowledgeId in ?1", knowledgeIds);
    Map<String, TheoryKnowledgeSwfEntity> swfById = swfEntities.stream()
        .collect(Collectors.toMap(TheoryKnowledgeSwfEntity::getId, Function.identity(), (first, ignored) -> first));
    Map<String, List<TheoryKnowledgeSwfEntity>> swfsByKnowledge = swfEntities.stream()
        .collect(Collectors.groupingBy(TheoryKnowledgeSwfEntity::getKnowledgeId));
    // 该知识【全部】答案（不限已学 swf）：完成判定分子
    Map<String, List<TheoryKnowledgeTestUserEntity>> answersByKnowledge =
        theoryKnowledgeTestUserDao.findAllByUserIdAndKnowledgeIdIn(userId, knowledgeIds).stream()
            .collect(Collectors.groupingBy(TheoryKnowledgeTestUserEntity::getKnowledgeId));

    Map<Object, Object> re = new HashMap<>();
    durationsByKnowledge.forEach((knowledgeId, perSwf) -> {
      TheoryKnowledgeEntity managed = knowledgeById.get(knowledgeId);
      // 不修改托管实体：仅在展示副本上按需清零学分
      TheoryKnowledgeEntity display = PojoUtils.convertOne(managed, TheoryKnowledgeEntity.class);
      int swfCount = swfsByKnowledge.getOrDefault(knowledgeId, List.of()).size();
      int answerCount = answersByKnowledge.getOrDefault(knowledgeId, List.of()).size();
      if (swfCount != answerCount) {
        display.setCredit(0.0);
      }
      Map<String, Long> titled = new HashMap<>();
      perSwf.forEach((swfId, seconds) -> {
        TheoryKnowledgeSwfEntity swfEntity = swfById.get(swfId);
        if (ObjectUtil.isNotEmpty(swfEntity)) {
          titled.put(swfEntity.getTitle(), seconds);
        }
      });
      re.put(display, titled);
    });
    return re;
  }

  /**
   * 根据给定的列表、年份和月份计算每个时间单位内的总时长
   * 此方法用于统计理论知识SWF记录实体在指定年份和月份的使用情况
   * 如果月份为空，则计算每年每个月的总时长；如果月份不为空，则计算该月每天的总时长
   *
   * @param list  理论知识SWF记录实体列表，包含每个记录的加入和退出时间
   * @param year  指定的年份，用于筛选记录
   * @param month 指定的月份，如果为空，则按月统计；如果不为空，则按天统计
   * @return 返回一个映射，键为月份或日期，值为该时间单位内的总时长（以秒为单位）
   */
  private Map<String, Long> check(List<TheoryKnowledgeSwfRecordEntity> list, String year, String month) {
    // list 已按 year(-month) 预筛，直接从 joinTime 提取月/日键聚合，替代 1..12 / 1..31 扫描
    boolean byMonth = ObjectUtil.isEmpty(month);
    Map<String, Long> map = new HashMap<>();
    for (TheoryKnowledgeSwfRecordEntity a : list) {
      String joinTime = a.getJoinTime();
      String key = byMonth
          ? String.valueOf(Integer.parseInt(joinTime.substring(5, 7)))
          : String.valueOf(Integer.parseInt(joinTime.substring(8, 10)));
      long duration = DateUtil.between(DateUtil.parse(a.getJoinTime()), DateUtil.parse(a.getExitTime()), DateUnit.SECOND);
      map.merge(key, duration, Long::sum);
    }
    return map;
  }

  /**
   * 将 1..31 的整数格式化为两位数字符串（月份/日期通用，P2-77 勘误：原名 getMonth 但同时被日期 1..31 复用）
   *
   * @param i 代表月份或日期的整数
   * @return 两位数字符串，例如 1 -> "01"，12 -> "12"
   */
  private String padTwoDigits(int i) {
    return String.format("%02d", i);
  }

  /**
   * 统计通过场次：以各场考试试卷的 passMark 为准（P2-78：替代散落的硬编码 >=60 及格线）
   */
  private int countPass(List<TheoryKnowledgeExamUserEntity> examUsers) {
    if (examUsers.isEmpty()) {
      return 0;
    }
    return theoryKnowledgeExamUserDao.countExamPass(
        examUsers.stream().map(TheoryKnowledgeExamUserEntity::getId).toList());
  }

  /**
   * 根据用户令牌、年份、月份和类型返回成绩统计信息
   *
   * @param token 用户身份令牌，用于识别用户
   * @param year  统计的年份
   * @param month 统计的月份
   * @param type  统计类型：0-成绩分布，1-考试次数，其他-分数统计
   * @return 包含成绩统计信息的响应对象
   */
  public Response<Object> gradeCount(String token, String year, String month, int type) {
    UserEntity userEntity = userService.getUserByToken(token);
    return switch (type) {
      case 0 -> ResponseResult.success(gradeDistribution(userEntity.getId(), year, month));
      case 1 -> ResponseResult.success(examTimes(userEntity.getId(), year, month));
      default -> ResponseResult.success(scoreCount(userEntity.getId(), year, month));
    };
  }

  /**
   * 根据用户ID、年份和月份统计成绩分布
   * 此方法从数据库中查询指定用户在指定时间范围内的考试记录，并根据分数段统计成绩分布情况
   * 同时统计考试总次数和通过次数
   *
   * @param userId 用户ID，用于筛选考试记录
   * @param year   年份，用于筛选考试记录
   * @param month  月份，用于筛选考试记录如果未提供月份，则考虑全年数据
   * @return 返回包含成绩分布、总次数和通过次数的Map对象
   */
  private Map<String, Object> gradeDistribution(String userId, String year, String month) {
    List<TheoryKnowledgeExamUserEntity> allByUserIdAndEndTimeLike;
    int all = 0;
    int good;
    if (ObjectUtil.isEmpty(month)) {
      allByUserIdAndEndTimeLike = theoryKnowledgeExamUserDao.findAllByUserIdAndEndTimeLikeAndState(userId, year + "%", 4);
    } else {
      allByUserIdAndEndTimeLike = theoryKnowledgeExamUserDao.findAllByUserIdAndEndTimeLikeAndState(userId, year + "-" + padTwoDigits(Integer.parseInt(month)) + "%", 4);
    }
    List<String> examUserIds = allByUserIdAndEndTimeLike.stream()
        .map(TheoryKnowledgeExamUserEntity::getId)
        .toList();
    List<ExamScoreThresholdDto> rows =
        theoryKnowledgeExamUserDao.findScoreThresholds(examUserIds);
    Map<String, Integer> map = new HashMap<>();
    for (ExamScoreThresholdDto row : rows) {
      if (row.score() == null || row.passMark() == null || row.total() == null) {
        throw new IllegalStateException("考试成绩或试卷分数配置缺失");
      }
      // goodBoundary = (total-passMark)/2 + passMark，向下取整；<passMark 不及格，[passMark,goodBoundary) 及格，>=goodBoundary 优秀
      int goodBoundary = BigDecimal.valueOf((long) row.total() - row.passMark())
          .divide(BigDecimal.valueOf(2), 0, RoundingMode.DOWN)
          .add(BigDecimal.valueOf(row.passMark()))
          .intValue();
      String key = row.score() < row.passMark() ? "59"
          : row.score() < goodBoundary ? "60" : "81";
      map.merge(key, 1, Integer::sum);
    }
    all = allByUserIdAndEndTimeLike.size();
    //统计考试成功通过的次数（以试卷 passMark 为准）
    good = countPass(allByUserIdAndEndTimeLike);
    return buildResultMap(all, good, map);
  }

  /**
   * 根据用户ID、年份和月份查询考试次数
   * 此方法统计用户在指定年份或月份的考试次数，并按各场试卷 passMark 计算通过次数
   * 如果未指定月份，将统计全年每月的考试次数；如果指定了月份，将统计该月每日的考试次数
   *
   * @param userId 用户ID，用于查询考试记录
   * @param year   年份，用于查询考试记录
   * @param month  月份，用于更具体的查询范围，如果为空，则统计全年数据
   * @return 返回包含考试总次数、通过次数和每月或每日考试次数的Map对象
   */
  private Map<String, Object> examTimes(String userId, String year, String month) {
    boolean byMonth = ObjectUtil.isEmpty(month);
    List<TheoryKnowledgeExamUserEntity> allByUserIdAndEndTimeLike = byMonth
        ? theoryKnowledgeExamUserDao.findAllByUserIdAndEndTimeLikeAndState(userId, year + "%", 4)
        : theoryKnowledgeExamUserDao.findAllByUserIdAndEndTimeLikeAndState(userId, year + "-" + padTwoDigits(Integer.parseInt(month)) + "%", 4);
    Map<String, Integer> map = new HashMap<>();
    for (TheoryKnowledgeExamUserEntity a : allByUserIdAndEndTimeLike) {
      String endTime = a.getEndTime();
      String key = byMonth
          ? String.valueOf(Integer.parseInt(endTime.substring(5, 7)))
          : String.valueOf(Integer.parseInt(endTime.substring(8, 10)));
      map.merge(key, 1, Integer::sum);
    }
    int all = allByUserIdAndEndTimeLike.size();
    // P2-78：及格线统一以试卷 passMark 为准，替代硬编码 >=60
    int good = countPass(allByUserIdAndEndTimeLike);
    return buildResultMap(all, good, map);
  }

  /**
   * 根据用户ID、年份和月份统计理论知识考试的成绩情况
   * 此方法用于计算用户在指定年份或月份的考试成绩汇总，包括总考试次数、及格次数以及每月或每日的考试详情
   *
   * @param userId 用户ID，用于标识特定用户
   * @param year   年份，用于筛选考试记录
   * @param month  月份，可选参数，用于进一步筛选考试记录至特定月份
   * @return 返回包含考试总次数、及格次数以及按月或按日分布的考试详情的Map对象
   */
  private Map<String, Object> scoreCount(String userId, String year, String month) {
    boolean byMonth = ObjectUtil.isEmpty(month);
    List<TheoryKnowledgeExamUserEntity> allByUserIdAndEndTimeLike = byMonth
        ? theoryKnowledgeExamUserDao.findAllByUserIdAndEndTimeLikeAndState(userId, year + "%", 4)
        : theoryKnowledgeExamUserDao.findAllByUserIdAndEndTimeLikeAndState(userId, year + "-" + padTwoDigits(Integer.parseInt(month)) + "%", 4);
    Map<String, List<TheoryKnowledgeExamUserEntity>> grouped = new HashMap<>();
    for (TheoryKnowledgeExamUserEntity a : allByUserIdAndEndTimeLike) {
      String endTime = a.getEndTime();
      String key = byMonth
          ? String.valueOf(Integer.parseInt(endTime.substring(5, 7)))
          : String.valueOf(Integer.parseInt(endTime.substring(8, 10)));
      grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(a);
    }
    Map<String, Object> detail = new HashMap<>();
    grouped.forEach((key, value) -> detail.put(key, zzsj(value)));
    int all = allByUserIdAndEndTimeLike.size();
    // P2-78：及格线统一以试卷 passMark 为准，替代硬编码 >=60
    int good = countPass(allByUserIdAndEndTimeLike);
    return buildResultMap(all, good, detail);
  }

  /**
   * 统计理论知识考试用户得分的最高分、最低分和平均分
   * 此方法首先检查输入列表是否为空，如果为空，则返回一个空的Map
   * 如果列表不为空，它将列表按照得分进行排序，然后提取最高分和最低分，
   * 并计算平均分，将这些信息存储在一个Map中并返回
   *
   * @param list 参与考试的用户列表，包含每个用户的得分
   * @return 包含最高分、最低分和平均分的Map
   */
  private Map<String, String> zzsj(List<TheoryKnowledgeExamUserEntity> list) {
    if (list.isEmpty()) {
      return Collections.emptyMap();
    }
    list.sort(Comparator.comparingInt(TheoryKnowledgeExamUserEntity::getScore));
    Map<String, String> result = new ConcurrentHashMap<>();
    result.put("high", list.get(list.size() - 1).getScore().toString());
    result.put("low", list.getFirst().getScore().toString());
    double avg = list.stream().mapToInt(TheoryKnowledgeExamUserEntity::getScore).average().orElse(0.0);
    result.put("avg", df.format(avg));
    return result;
  }

  /**
   * 构建结果映射表
   * 该方法用于汇总数据，将数据分为“up”和“down”两部分，“up”部分包含总数(all)和优质数量(good)
   *
   * @param all     总数
   * @param good    优质数量
   * @param downMap 下行数据映射表，包含各种类型的数量
   * @return 返回一个包含“up”和“down”数据的映射表
   */
  private Map<String, Object> buildResultMap(int all, int good, Map<String, ?> downMap) {
    Map<String, Object> re = new ConcurrentHashMap<>();
    Map<String, Integer> count = new ConcurrentHashMap<>();
    count.put("good", good);
    count.put("all", all);
    re.put("up", count);
    re.put("down", downMap);
    return re;
  }

  // 定义专门的异常类
  public static class InvalidTitleException extends RuntimeException {
    public InvalidTitleException(String message) {
      super(message);
    }
  }
}
