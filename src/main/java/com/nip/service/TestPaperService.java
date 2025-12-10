package com.nip.service;


import cn.hutool.core.util.ObjectUtil;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.TestPaperDao;
import com.nip.dao.TestPaperQuestionDao;
import com.nip.dao.TheoryKnowledgeQuestionLevelDao;
import com.nip.dto.TestPaperDto;
import com.nip.dto.TestPaperQuestionDto;
import com.nip.entity.TestPaperEntity;
import com.nip.entity.TestPaperQuestionEntity;
import com.nip.entity.TheoryKnowledgeQuestionLevelEntity;
import com.nip.entity.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/1/24 15:05
 */
@Slf4j
@ApplicationScoped
public class TestPaperService {

  private final UserService userService;
  private final TestPaperDao testPaperDao;
  private final TestPaperQuestionDao testPaperQuestionDao;
  private final TheoryKnowledgeQuestionLevelDao theoryKnowledgeQuestionLevelDao;

  @Inject
  public TestPaperService(UserService userService, TestPaperDao testPaperDao, TestPaperQuestionDao testPaperQuestionDao, TheoryKnowledgeQuestionLevelDao theoryKnowledgeQuestionLevelDao) {
    this.userService = userService;
    this.testPaperDao = testPaperDao;
    this.testPaperQuestionDao = testPaperQuestionDao;
    this.theoryKnowledgeQuestionLevelDao = theoryKnowledgeQuestionLevelDao;
  }

  /**
   * 保存试卷信息
   *
   * @param token        用户令牌，用于验证用户身份
   * @param testPaperDto 试卷数据传输对象，包含试卷的相关信息
   * @return 返回保存结果，成功或失败
   * <p>
   * 本方法首先根据传入的试卷DTO创建或更新试卷实体，然后根据试卷类型保存试卷题目
   */
  @Transactional
  public Response<Void> saveTestPaper(String token, TestPaperDto testPaperDto) {
    try {
      TestPaperEntity entity = new TestPaperEntity();
      if (ObjectUtil.isNotEmpty(testPaperDto.getId())) {
        entity.setId(testPaperDto.getId());
        testPaperQuestionDao.deleteAllByTestPaperId(testPaperDto.getId());
      }
      entity.setName(testPaperDto.getName());
      entity.setLevelId(testPaperDto.getLevelId());
      entity.setTotal(testPaperDto.getTotal());
      entity.setPassMark(testPaperDto.getPassMark());
      UserEntity userEntity = userService.getUserByToken(token);
      entity.setCreateUserId(userEntity.getId());
      entity.setCreateUserName(userEntity.getUserName());
      entity.setPassTheExamThan(testPaperDto.getPassTheExamThan());
      TestPaperEntity save = testPaperDao.save(entity);
      List<TestPaperQuestionDto> testPaperQuestionDtos = new ArrayList<>();
      testPaperQuestionDtos.addAll(testPaperDto.getSingleChoice());
      testPaperQuestionDtos.addAll(testPaperDto.getMultipleChoice());
      testPaperQuestionDtos.addAll(testPaperDto.getJudge());
      testPaperQuestionDtos.addAll(testPaperDto.getCompletion());
      testPaperQuestionDtos.addAll(testPaperDto.getShortAnswer());
      testPaperQuestionDtos.forEach(ques -> {
        TestPaperQuestionEntity testPaperQuestionEntity1 = PojoUtils.convertOne(ques, TestPaperQuestionEntity.class);
        testPaperQuestionEntity1.setId(null);
        testPaperQuestionEntity1.setTestPaperId(save.getId());
        testPaperQuestionDao.save(testPaperQuestionEntity1);
      });
      return ResponseResult.success();
    } catch (Exception e) {
      log.error("保存试卷失败：{}", e.getMessage());
      return ResponseResult.error();
    }
  }

  /**
   * 获取所有试卷的信息
   *
   * 此方法从数据库中检索所有试卷实体，将它们转换为DTO（数据传输对象）格式，并包含每个试卷的所有相关问题
   * 问题根据类型被分类到不同的列表中，以便于后续处理和展示
   *
   * @return 包含TestPaperDto列表的响应对象，如果发生错误，返回错误响应
   */
  @Transactional
  public Response<List<TestPaperDto>> findAllTestPaper() {
    try {
      List<TestPaperEntity> all = testPaperDao.findAll().list();
      List<TestPaperDto> testPaperDtos = new ArrayList<>();
      for (TestPaperEntity entity : all) {
        TestPaperDto testPaperDto = new TestPaperDto();
        testPaperDto.setId(entity.getId());
        testPaperDto.setName(entity.getName());
        testPaperDto.setLevelId(entity.getLevelId());
        testPaperDto.setTotal(entity.getTotal());
        testPaperDto.setPassMark(entity.getPassMark());
        testPaperDto.setCreateTime(entity.getCreateTime());
        testPaperDto.setCompletion(new ArrayList<>());
        testPaperDto.setJudge(new ArrayList<>());
        testPaperDto.setPassTheExamThan(entity.getPassTheExamThan());
        testPaperDto.setMultipleChoice(new ArrayList<>());
        testPaperDto.setSingleChoice(new ArrayList<>());
        testPaperDto.setShortAnswer(new ArrayList<>());
        List<TestPaperQuestionEntity> allByTestPaperId = testPaperQuestionDao.findAllByTestPaperId(entity.getId());
        for (TestPaperQuestionEntity questionEntity : allByTestPaperId) {
          Map<Integer, List<TestPaperQuestionDto>> typeToListMap = new HashMap<>();
          typeToListMap.put(1, testPaperDto.getSingleChoice());
          typeToListMap.put(2, testPaperDto.getMultipleChoice());
          typeToListMap.put(3, testPaperDto.getJudge());
          typeToListMap.put(4, testPaperDto.getCompletion());
          typeToListMap.put(5, testPaperDto.getShortAnswer());
          int type = questionEntity.getType();
          if (typeToListMap.containsKey(type)) {
            TestPaperQuestionDto testPaperQuestionDto = PojoUtils.convertOne(questionEntity, TestPaperQuestionDto.class);
            typeToListMap.get(type).add(testPaperQuestionDto);
          }
        }
        testPaperDtos.add(testPaperDto);
      }
      return ResponseResult.success(testPaperDtos);
    } catch (Exception e) {
      log.error("findAllTestPaper error:{}", e.getMessage());
      return ResponseResult.error();
    }
  }

  /**
   * 根据测试卷ID查找测试卷信息
   * 此方法使用了@Transactional注解，确保在查找测试卷时，操作是原子性的，可以维护数据一致性
   *
   * @param id 测试卷的唯一标识符
   * @return 返回一个Response对象，其中包含找到的TestPaperDto对象
   */
  @Transactional
  public Response<TestPaperDto> findTestPaperById(String id) {
    TestPaperEntity entity = testPaperDao.findById(id);
    return ResponseResult.success(getTestPaper(entity));
  }

  /**
   * 将考试试卷实体类转换为DTO
   *
   * @param entity 考试卷实体类，包含试卷的基本信息和题目
   * @return 返回一个转换后的TestPaperDto对象，用于展示层和业务层之间的数据传递
   */
  private TestPaperDto getTestPaper(TestPaperEntity entity) {
    TestPaperDto testPaperDto = new TestPaperDto();
    testPaperDto.setId(entity.getId());
    testPaperDto.setName(entity.getName());
    testPaperDto.setLevelId(entity.getLevelId());
    testPaperDto.setTotal(entity.getTotal());
    testPaperDto.setPassTheExamThan(entity.getPassTheExamThan());
    testPaperDto.setPassMark(entity.getPassMark());
    testPaperDto.setCompletion(new ArrayList<>());
    testPaperDto.setCreateTime(entity.getCreateTime());
    testPaperDto.setJudge(new ArrayList<>());
    testPaperDto.setMultipleChoice(new ArrayList<>());
    testPaperDto.setSingleChoice(new ArrayList<>());
    testPaperDto.setShortAnswer(new ArrayList<>());
    List<TestPaperQuestionEntity> allByTestPaperId = testPaperQuestionDao.findAllByTestPaperId(entity.getId());
    for (TestPaperQuestionEntity questionEntity : allByTestPaperId) {
      switch (questionEntity.getType()) {
        case 1 -> {
          TestPaperQuestionDto testPaperQuestionDto = PojoUtils.convertOne(questionEntity, TestPaperQuestionDto.class);
          testPaperDto.getSingleChoice().add(testPaperQuestionDto);
        }
        case 2 -> {
          TestPaperQuestionDto testPaperQuestionDto2 = PojoUtils.convertOne(questionEntity, TestPaperQuestionDto.class);
          testPaperDto.getMultipleChoice().add(testPaperQuestionDto2);
        }
        case 3 -> {
          TestPaperQuestionDto testPaperQuestionDto3 = PojoUtils.convertOne(questionEntity, TestPaperQuestionDto.class);
          testPaperDto.getJudge().add(testPaperQuestionDto3);
        }
        case 4 -> {
          TestPaperQuestionDto testPaperQuestionDto4 = PojoUtils.convertOne(questionEntity, TestPaperQuestionDto.class);
          testPaperDto.getCompletion().add(testPaperQuestionDto4);
        }
        case 5 -> {
          TestPaperQuestionDto testPaperQuestionDto5 = PojoUtils.convertOne(questionEntity, TestPaperQuestionDto.class);
          testPaperDto.getShortAnswer().add(testPaperQuestionDto5);
        }
        default -> throw new IllegalArgumentException("未知题型");
      }
    }
    return testPaperDto;
  }

  /**
   * 根据层级ID和名称查找测试卷
   *
   * 此方法旨在通过层级ID和/或名称来筛选测试卷它首先根据输入参数的是否存在来决定使用哪种查询策略
   * 如果ID为空，那么将根据名称进行模糊查询如果ID不为空，那么将根据ID进行查询，同时如果名称也不空，则增加名称的模糊查询条件
   *
   * @param id 层级ID，用于筛选测试卷如果为空，将不作为筛选条件
   * @param name 测试卷名称，用于模糊筛选如果为空，将不作为筛选条件
   * @return 返回一个包含测试卷列表的响应对象
   */
  @Transactional
  public Response<List<TestPaperDto>> findTestPaperByLevelIdAndName(String id, String name) {
    List<TestPaperEntity> allByNameStartingWith;
    if (StringUtils.isEmpty(id)) {
      allByNameStartingWith = testPaperDao.findAllByNameLike("%" + name + "%");
    } else {
      findAllLevel(id);
      if (StringUtils.isEmpty(name)) {
        allByNameStartingWith = testPaperDao.findAllByLevelIdIn(ids);
      } else {
        allByNameStartingWith = testPaperDao.findAllByLevelIdInAndNameLike(ids, "%" + name + "%");
      }
    }
    List<TestPaperDto> list = new ArrayList<>();
    for (TestPaperEntity entity : allByNameStartingWith) {
      list.add(getTestPaper(entity));
    }
    ids = new ArrayList<>();
    return ResponseResult.success(list);
  }

  List<String> ids = new ArrayList<>();

  private void findAllLevel(String id) {
    ids.add(id);
    List<TheoryKnowledgeQuestionLevelEntity> entityList = theoryKnowledgeQuestionLevelDao.findAllByParentId(id);
    if (!entityList.isEmpty()) {
      entityList.forEach(a -> findAllLevel(a.getId()));
    }
  }

}
