package com.nip.service;

import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.google.gson.reflect.TypeToken;
import com.nip.common.constants.ResponseCode;
import com.nip.common.constants.TheoryKnowledgeClassifyTypeEnum;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.*;
import com.nip.dto.TheoryKnowledgeDto;
import com.nip.dto.TheoryKnowledgesDto;
import com.nip.dto.sql.FindTheoryKnowledgeDto;
import com.nip.dto.vo.TheoryKnowledgeSwfVO;
import com.nip.dto.vo.TheoryKnowledgeTestVO;
import com.nip.entity.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
    List<TheoryKnowledgeSwfVO> swfVOS = PojoUtils.convert(swfEntities, TheoryKnowledgeSwfVO.class, (e, v) -> {
      //查询测验
      List<TheoryKnowledgeTestEntity> testEntities = theoryKnowledgeTestDao.findAllByKnowledgeSwfIdOrderByCreateTimeAsc(e.getId());
      List<TheoryKnowledgeTestVO> testVos = PojoUtils.convert(testEntities, TheoryKnowledgeTestVO.class, (testEntity, testVo) -> {
        //查询考题
        List<TheoryKnowledgeTestContentEntity> testContentEntities =
            theoryKnowledgeTestContentDao.findAllByKnowledgeTestId(testEntity.getId());
        testVo.setKnowledgeTestContents(testContentEntities);
      });
      v.setTest(testVos);
    });
    knowledgeDto.setKnowledgeSwfs(swfVOS);
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
    swfEntities.forEach(swf -> {
      TheoryKnowledgeTestUserEntity firstByUserIdAndKnowledgeSwfId = theoryKnowledgeTestUserDao.findFirstByUserIdAndKnowledgeSwfId(userEntity.getId(), swf.getId());
      swf.setScore(null == firstByUserIdAndKnowledgeSwfId ? 0 : firstByUserIdAndKnowledgeSwfId.getScore());
      List<TheoryKnowledgeTestEntity> allByKnowledgeIdAndVersions = theoryKnowledgeTestDao.findAllByKnowledgeSwfIdAndVersions(swf.getId(), 1);
      swf.setHaveTest(!allByKnowledgeIdAndVersions.isEmpty());
      List<TheoryKnowledgeSwfRecordEntity> allByUserIdAndKnowledgeSwfId = knowledgeRecordDao.findAllByUserIdAndKnowledgeSwfId(userEntity.getId(), swf.getId());
      double count = 0;
      if (!allByUserIdAndKnowledgeSwfId.isEmpty()) {
        for (TheoryKnowledgeSwfRecordEntity theoryKnowledgeSwfRecordEntity : allByUserIdAndKnowledgeSwfId) {
          long between = DateUtil.between(DateUtil.parse(theoryKnowledgeSwfRecordEntity.getJoinTime()), DateUtil.parse(theoryKnowledgeSwfRecordEntity.getExitTime()), DateUnit.SECOND);
          count += between;
        }
        swf.setRecord(count / 60);
      } else {
        swf.setRecord(0);
      }
    });
    knowledgeDto.setKnowledge(knowledge);
    List<TheoryKnowledgeSwfVO> swfVOS = PojoUtils.convert(swfEntities, TheoryKnowledgeSwfVO.class, (e, v) -> {
      //查询测验
      List<TheoryKnowledgeTestEntity> testEntities = theoryKnowledgeTestDao.findAllByKnowledgeSwfIdOrderByCreateTimeAsc(e.getId());
      List<TheoryKnowledgeTestVO> testVos = PojoUtils.convert(testEntities, TheoryKnowledgeTestVO.class, (testEntity, testVo) -> {
        //查询考题
        List<TheoryKnowledgeTestContentEntity> testContentEntities =
            theoryKnowledgeTestContentDao.findAllByKnowledgeTestId(testEntity.getId());
        testVo.setKnowledgeTestContents(testContentEntities);
      });
      v.setTest(testVos);
    });
    knowledgeDto.setKnowledgeSwfs(swfVOS);
    return ResponseResult.success(knowledgeDto);
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
    List<TheoryKnowledgeDto> all = JSONUtils.fromJson(JSONUtils.toJson(dtoAllSql), new TypeToken<>() {
    });
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
    try {
      if (ObjectUtil.isEmpty(knowledgesDto.getKnowledge().getTitle())) {
        throw new InvalidTitleException("标题不能是空!");
      }
      TheoryKnowledgeEntity knowledge = knowledgeDao.save(knowledgesDto.getKnowledge());
      //删除之前的课件
      knowledgeSwfDao.deleteAllByKnowledgeId(knowledgesDto.getKnowledge().getId());
      knowledgesDto.getKnowledgeSwfs().forEach(s -> {
        if (StringUtils.isEmpty(s.getId())) {
          s.setKnowledgeId(knowledge.getId());
        }
        if (StringUtils.isEmpty(s.getTitle())) {
          throw new IllegalArgumentException("标题不能是空!");
        }
        //如果id是null则需要设置默认值
        if (s.getId() == null) {
          s.setCreateUserId(knowledge.getCreateUserId());
        }
        //保存到数据库中
        TheoryKnowledgeSwfEntity swfEntity = PojoUtils.convertOne(s, TheoryKnowledgeSwfEntity.class);
        TheoryKnowledgeSwfEntity saveSwfEntity = knowledgeSwfDao.save(swfEntity);
        //拿到测验test
        List<TheoryKnowledgeTestVO> testVOS = s.getTest();
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
              TheoryKnowledgeTestEntity testEntity = PojoUtils.convertOne(test, TheoryKnowledgeTestEntity.class, (t, e) -> e.setKnowledgeSwfId(saveSwfEntity.getId()));
              if (!firstByKnowledgeSwfIdAndVersions.getId().equals(testEntity.getId())) {
                theoryKnowledgeTestUserDao.deleteByKnowledgeIdAndKnowledgeSwfId(test.getKnowledgeId(), test.getKnowledgeSwfId());
              } else {
                List<TheoryKnowledgeTestContentEntity> allByKnowledgeTestId = theoryKnowledgeTestContentDao.findAllByKnowledgeTestId(testEntity.getId());
                List<TheoryKnowledgeTestContentEntity> knowledgeTestContents = test.getKnowledgeTestContents();
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
          List<TheoryKnowledgeTestContentEntity> knowledgeTestContents = test.getKnowledgeTestContents();
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
    } catch (Exception e) {
      log.error("保存失败:{}", e.getMessage());
      return ResponseResult.error(ResponseCode.CODE_500.getMessage());
    }
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
      record.setJoinTime(DateUtil.now());
      return new Response<>(ResponseCode.CODE_200.getCode(), record);
    }
    if (ObjectUtil.isEmpty(record.getExitTime())) {
      record.setExitTime(DateUtil.now());
    }
    if (1 <= DateUtil.between(DateUtil.parse(record.getJoinTime()), DateUtil.parse(record.getExitTime()), DateUnit.MINUTE)) {
      record.setUserId(userEntity.getId());
      TheoryKnowledgeEntity theoryKnowledgeEntity = knowledgeDao.findById(record.getKnowledgeId());
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
      List<TheoryKnowledgeSwfRecordEntity> allByUserIdAndJoinTimeLike = knowledgeRecordDao.findAllByUserIdAndJoinTimeLikeAndType(userEntity.getId(), year + "-" + getMonth(Integer.valueOf(month)) + "%", type);
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
    Map<String, Map<String, Long>> map = new HashMap<>();
    Map<String, Long> swf = new HashMap<>();
    list.forEach(a -> {
      long between = DateUtil.between(DateUtil.parse(a.getJoinTime()), DateUtil.parse(a.getExitTime()), DateUnit.SECOND);
      //统计每一个章节的时长
      if (ObjectUtil.isEmpty(swf.get(a.getKnowledgeId() + ":" + a.getKnowledgeSwfId()))) {
        swf.put(a.getKnowledgeId() + ":" + a.getKnowledgeSwfId(), between);
      } else {
        swf.put(a.getKnowledgeId() + ":" + a.getKnowledgeSwfId(), swf.get(a.getKnowledgeId() + ":" + a.getKnowledgeSwfId()) + between);
      }
    });
    swf.forEach((key, value) -> {
      String[] s = key.split(":");
      String id = s[0];
      String swfId = s[1];
      Map<String, Long> longMap = new HashMap<>();
      longMap.put(swfId, value);
      if (ObjectUtil.isEmpty(map.get(id))) {
        map.put(id, longMap);
      } else {
        Map<String, Long> map1 = map.get(id);
        if (ObjectUtil.isEmpty(map1.get(swfId))) {
          map1.put(swfId, value);
          map.put(id, map1);
        } else {
          map1.put(swfId, value + map1.get(swfId));
          map.put(id, map1);
        }
      }
    });
    Map<Object, Object> re = new HashMap<>();
    Map<String, Long> swf2 = new HashMap<>();
    for (Map.Entry<String, Map<String, Long>> mapEntry : map.entrySet()) {
      TheoryKnowledgeEntity theoryKnowledgeEntity = knowledgeDao.findById(mapEntry.getKey());
      //查询是否得到学分
      List<TheoryKnowledgeSwfEntity> allByKnowledgeIdOrderBySortAsc = knowledgeSwfDao.findAllByKnowledgeIdOrderBySortAsc(theoryKnowledgeEntity.getId());
      List<TheoryKnowledgeTestUserEntity> allByUserIdAndKnowledgeId = theoryKnowledgeTestUserDao.findAllByUserIdAndKnowledgeId(userId, theoryKnowledgeEntity.getId());
      if (allByKnowledgeIdOrderBySortAsc.size() != allByUserIdAndKnowledgeId.size()) {
        theoryKnowledgeEntity.setCredit(0.0);
      }
      for (Map.Entry<String, Long> valueEn : mapEntry.getValue().entrySet()) {
        TheoryKnowledgeSwfEntity swfEntityOptional = knowledgeSwfDao.findById(valueEn.getKey());
        if (ObjectUtil.isNotEmpty(swfEntityOptional)) {
          swf2.put(swfEntityOptional.getTitle(), valueEn.getValue());
        }
      }
      re.put(theoryKnowledgeEntity, swf2);
      swf2 = new HashMap<>();
    }
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
    Map<String, Long> map = new HashMap<>();
    if (ObjectUtil.isEmpty(month)) {
      for (TheoryKnowledgeSwfRecordEntity a : list) {
        for (int i = 1; i <= 12; i++) {
          String substring = a.getJoinTime().substring(0, 7);
          if ((year + "-" + getMonth(i)).equals(substring)) {
            map.put(String.valueOf(i), (ObjectUtil.isEmpty(map.get(String.valueOf(i))) ? 0 : map.get(String.valueOf(i))) + DateUtil.between(DateUtil.parse(a.getJoinTime()), DateUtil.parse(a.getExitTime()), DateUnit.SECOND));
          }
        }
      }
    } else {
      month = getMonth(Integer.parseInt(month));
      for (TheoryKnowledgeSwfRecordEntity a : list) {
        for (int i = 1; i <= 31; i++) {
          String substring = a.getJoinTime().substring(0, 10);
          String s = year + "-" + month + "-" + getMonth(i);
          if (s.equals(substring)) {
            map.put(String.valueOf(i), (ObjectUtil.isEmpty(map.get(String.valueOf(i))) ? 0 : map.get(String.valueOf(i))) + DateUtil.between(DateUtil.parse(a.getJoinTime()), DateUtil.parse(a.getExitTime()), DateUnit.SECOND));
          }
        }
      }
    }
    return map;
  }

  /**
   * 将给定的整数转换为表示月份的字符串
   * 如果整数大于9，则直接转换为字符串；否则，使用两位数的格式化字符串
   * 这是为了确保月份总是以两位数的形式表示，例如，1月显示为"01"，12月显示为"12"
   *
   * @param i 代表月份的整数，范围是1到12
   * @return 格式化后的月份字符串，始终为两位数形式
   */
  private String getMonth(int i) {
    if (9 < i) {
      return String.valueOf(i);
    } else {
      return String.format("%02d", i);
    }
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
    if (userEntity == null) {
      return ResponseResult.error("Invalid token");
    }

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
      allByUserIdAndEndTimeLike = theoryKnowledgeExamUserDao.findAllByUserIdAndEndTimeLikeAndState(userId, year + "-" + getMonth(Integer.parseInt(month)) + "%", 4);
    }
    Map<String, Integer> map = new HashMap<>();
    for (TheoryKnowledgeExamUserEntity a : allByUserIdAndEndTimeLike) {
      if (a.getScore() < 60) {
        map.put("59", ObjectUtil.isEmpty(map.get("59")) ? 1 : map.get("59") + 1);
      } else if (a.getScore() <= 80) {
        map.put("60", ObjectUtil.isEmpty(map.get("60")) ? 1 : map.get("60") + 1);
      } else {
        map.put("81", ObjectUtil.isEmpty(map.get("81")) ? 1 : map.get("81") + 1);
      }
      all++;

    }
    //统计考试成功通过的次数
    List<String> examIds = allByUserIdAndEndTimeLike.stream()
        .map(TheoryKnowledgeExamUserEntity::getId)
        .toList();
    good = theoryKnowledgeExamUserDao.countExamPass(examIds);
    return buildResultMap(all, good, map);
  }

  /**
   * 根据用户ID、年份和月份查询考试次数
   * 此方法统计用户在指定年份或月份的考试次数，并计算通过（分数>=60）的考试次数
   * 如果未指定月份，将统计全年每月的考试次数；如果指定了月份，将统计该月每日的考试次数
   *
   * @param userId 用户ID，用于查询考试记录
   * @param year   年份，用于查询考试记录
   * @param month  月份，用于更具体的查询范围，如果为空，则统计全年数据
   * @return 返回包含考试总次数、通过次数和每月或每日考试次数的Map对象
   */
  private Map<String, Object> examTimes(String userId, String year, String month) {
    List<TheoryKnowledgeExamUserEntity> allByUserIdAndEndTimeLike;
    Map<String, Integer> map = new HashMap<>();
    int all = 0;
    int good = 0;
    if (ObjectUtil.isEmpty(month)) {
      allByUserIdAndEndTimeLike = theoryKnowledgeExamUserDao.findAllByUserIdAndEndTimeLikeAndState(userId, year + "%", 4);
      for (TheoryKnowledgeExamUserEntity a : allByUserIdAndEndTimeLike) {
        for (int i = 1; i <= 12; i++) {
          String substring = a.getEndTime().substring(0, 7);
          if ((year + "-" + getMonth(i)).equals(substring)) {
            map.put(String.valueOf(i), (ObjectUtil.isEmpty(map.get(String.valueOf(i))) ? 0 : map.get(String.valueOf(i))) + 1);
          }
        }
        all++;
        if (a.getScore() >= 60) {
          good++;
        }
      }
    } else {
      month = getMonth(Integer.parseInt(month));
      allByUserIdAndEndTimeLike = theoryKnowledgeExamUserDao.findAllByUserIdAndEndTimeLikeAndState(userId, year + "-" + month + "%", 4);
      for (TheoryKnowledgeExamUserEntity a : allByUserIdAndEndTimeLike) {
        for (int i = 1; i <= 31; i++) {
          String substring = a.getEndTime().substring(0, 10);
          String s = year + "-" + month + "-" + getMonth(i);
          if (s.equals(substring)) {
            map.put(String.valueOf(i), (ObjectUtil.isEmpty(map.get(String.valueOf(i))) ? 0 : map.get(String.valueOf(i))) + 1);
          }
        }
        all++;
        if (a.getScore() >= 60) {
          good++;
        }
      }
    }
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
    List<TheoryKnowledgeExamUserEntity> allByUserIdAndEndTimeLike;
    Map<String, List<TheoryKnowledgeExamUserEntity>> map = new ConcurrentHashMap<>();
    int all = 0;
    int good = 0;
    if (ObjectUtil.isEmpty(month)) {
      allByUserIdAndEndTimeLike = theoryKnowledgeExamUserDao.findAllByUserIdAndEndTimeLikeAndState(userId, year + "%", 4);
      for (TheoryKnowledgeExamUserEntity a : allByUserIdAndEndTimeLike) {
        for (int i = 1; i <= 12; i++) {
          String substring = a.getEndTime().substring(0, 7);
          if ((year + "-" + getMonth(i)).equals(substring)) {
            List<TheoryKnowledgeExamUserEntity> list;
            if (ObjectUtil.isEmpty(map.get(String.valueOf(i)))) {
              list = new ArrayList<>();
            } else {
              list = map.get(String.valueOf(i));
            }
            list.add(a);
            map.put(String.valueOf(i), list);
          }
        }
        all++;
        if (a.getScore() >= 60) {
          good++;
        }
      }
    } else {
      allByUserIdAndEndTimeLike = theoryKnowledgeExamUserDao.findAllByUserIdAndEndTimeLikeAndState(userId, year + "-" + getMonth(Integer.valueOf(month)) + "%", 4);
      month = getMonth(Integer.parseInt(month));
      for (TheoryKnowledgeExamUserEntity a : allByUserIdAndEndTimeLike) {
        for (int i = 1; i <= 31; i++) {
          String substring = a.getEndTime().substring(0, 10);
          String s = year + "-" + month + "-" + getMonth(i);
          if (s.equals(substring)) {
            List<TheoryKnowledgeExamUserEntity> list;
            if (ObjectUtil.isEmpty(map.get(String.valueOf(i)))) {
              list = new ArrayList<>();
            } else {
              list = map.get(String.valueOf(i));
            }
            list.add(a);
            map.put(String.valueOf(i), list);
          }
        }
        all++;
        if (a.getScore() >= 60) {
          good++;
        }
      }
    }
    Map<String, Object> a = new HashMap<>();
    for (Map.Entry<String, List<TheoryKnowledgeExamUserEntity>> mapEntry : map.entrySet()) {
      Map<String, String> zzsj = zzsj(mapEntry.getValue());
      a.put(mapEntry.getKey(), zzsj);
    }
    return buildResultMap(all, good, a);
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
