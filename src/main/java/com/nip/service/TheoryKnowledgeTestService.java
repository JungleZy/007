package com.nip.service;


import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dao.TheoryKnowledgeTestContentDao;
import com.nip.dao.TheoryKnowledgeTestDao;
import com.nip.dao.TheoryKnowledgeTestUserDao;
import com.nip.dto.TheoryKnowledgeTestDto;
import com.nip.dto.TheoryKnowledgeTestUserDto;
import com.nip.entity.TheoryKnowledgeTestContentEntity;
import com.nip.entity.TheoryKnowledgeTestEntity;
import com.nip.entity.TheoryKnowledgeTestUserEntity;
import com.nip.entity.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * TheoryKnowledgeSwfService
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2021-12-22 18:40
 */
@ApplicationScoped
public class TheoryKnowledgeTestService {
  private final UserService userService;
  private final TheoryKnowledgeTestDao testDao;
  private final TheoryKnowledgeTestContentDao testContentDao;
  private final TheoryKnowledgeTestUserDao testUserDao;

  // 使用构造函数注入
  @Inject
  public TheoryKnowledgeTestService(UserService userService, TheoryKnowledgeTestDao testDao,
                                    TheoryKnowledgeTestContentDao testContentDao,
                                    TheoryKnowledgeTestUserDao testUserDao) {
    this.userService = userService;
    this.testDao = testDao;
    this.testContentDao = testContentDao;
    this.testUserDao = testUserDao;
  }

  /**
   * 保存测验信息
   *
   * @param token
   * @param testDto
   * @return
   */
  @Transactional
  public Response<TheoryKnowledgeTestDto> saveKnowledgeTest(String token, TheoryKnowledgeTestDto testDto) {
    UserEntity userEntity = userService.getUserByToken(token);
    TheoryKnowledgeTestEntity knowledgeTest = testDto.getKnowledgeTest();
    if (StringUtils.isEmpty(knowledgeTest.getId())) {
      knowledgeTest.setCreateTime(new Date().getTime() + "");
      knowledgeTest.setCreateUserId(userEntity.getId());
    } else {
      TheoryKnowledgeTestEntity entity = testDao.findById(knowledgeTest.getId());
      assert entity != null;
      knowledgeTest.setKnowledgeId(entity.getKnowledgeId());
      knowledgeTest.setKnowledgeSwfId(entity.getKnowledgeSwfId());
      knowledgeTest.setCreateTime(entity.getCreateTime());
      knowledgeTest.setCreateUserId(entity.getCreateUserId());
    }
    List<TheoryKnowledgeTestEntity> tests = testDao.findAllByKnowledgeSwfIdOrderByCreateTimeAsc(
        knowledgeTest.getKnowledgeSwfId());
    if (!tests.isEmpty()) {
      if (knowledgeTest.getVersions() == 1) {
        testDao.updateVersions2CloseByKnowledgeSwfId(knowledgeTest.getKnowledgeSwfId());
      } else {
        TheoryKnowledgeTestEntity versions = testDao.findFirstByKnowledgeSwfIdAndVersions(
            knowledgeTest.getKnowledgeSwfId(), 1);
        if (versions == null && knowledgeTest.getVersions() == 0) {
          knowledgeTest.setVersions(1);
        }
      }
    } else {
      knowledgeTest.setVersions(1);
    }

    TheoryKnowledgeTestEntity save = testDao.save(knowledgeTest);
    List<TheoryKnowledgeTestContentEntity> testContents = testDto.getKnowledgeTestContents();
    if (testContents != null) {
      testContentDao.deleteAllByKnowledgeTestId(save.getId());
      for (int i = 0; i < testContents.size(); i++) {
        testContents.get(i).setKnowledgeId(save.getKnowledgeId());
        testContents.get(i).setKnowledgeSwfId(save.getKnowledgeSwfId());
        testContents.get(i).setKnowledgeTestId(save.getId());
        testContents.get(i).setCreateTime(new Date().getTime() + "");
        testContents.get(i).setCreateUserId(userEntity.getId());
        testContents.get(i).setSort(i);
      }
      testContents = testContentDao.save(testContents);
    }
    testDto.setKnowledgeTest(save);
    testDto.setKnowledgeTestContents(testContents);
    return ResponseResult.success(testDto);
  }

  /**
   * 根据教案id获取全部测验
   *
   * @param knowledgeSwfId
   * @return
   */
  public Response<List<TheoryKnowledgeTestDto>> getAllByKnowledgeSwfId(String knowledgeSwfId) {
    List<TheoryKnowledgeTestEntity> knowledgeTests = testDao.findAllByKnowledgeSwfIdOrderByCreateTimeAsc(
        knowledgeSwfId);
    List<TheoryKnowledgeTestDto> list = new ArrayList<>(knowledgeTests.size());
    for (TheoryKnowledgeTestEntity test : knowledgeTests) {
      TheoryKnowledgeTestDto testDto = new TheoryKnowledgeTestDto();
      testDto.setKnowledgeTest(test);
      testDto.setKnowledgeTestContents(testContentDao.findAllByKnowledgeTestId(test.getId()));
      list.add(testDto);
    }
    return ResponseResult.success(list);
  }

  /**
   * 根据教案id获取被启用的测验
   *
   * @param knowledgeSwfId
   * @return
   */
  public Response<List<TheoryKnowledgeTestContentEntity>> getByKnowledgeSwfIdAndEnable(String knowledgeSwfId) {
    TheoryKnowledgeTestEntity testEntity = testDao.findFirstByKnowledgeSwfIdAndVersions(knowledgeSwfId, 1);
    if (testEntity != null) {
      List<TheoryKnowledgeTestContentEntity> allByKnowledgeTestId = testContentDao.findAllByKnowledgeTestId(testEntity.getId());
      return ResponseResult.success(allByKnowledgeTestId);
    } else {
      return ResponseResult.error("数据异常");
    }
  }

  /**
   * 获取当前用户在指定理论知识下的测验答案
   *
   * @param token
   * @param knowledgeId
   * @return
   */
  public Response<List<TheoryKnowledgeTestUserEntity>> getTestContentByUserIdAndKnowledgeId(String token, String knowledgeId) {
    UserEntity userEntity = userService.getUserByToken(token);
    List<TheoryKnowledgeTestUserEntity> testUsers = testUserDao.findAllByUserIdAndKnowledgeId(
        userEntity.getId(), knowledgeId);
    return ResponseResult.success(testUsers);

  }

  /**
   * 根据测验结果ID获取测验结果
   *
   * @param id
   * @return
   */
  public Response<TheoryKnowledgeTestUserEntity> getTestContentById(String id) {
    return ResponseResult.success(testUserDao.findById(id));
  }

  /**
   * 获取当前用户在指定课件下的测验答案
   *
   * @param token
   * @param knowledgeSwfId
   * @return
   */
  public Response<TheoryKnowledgeTestUserEntity> getTestContentByUserIdAndKnowledgeSwfId(String token, String knowledgeSwfId) {
    UserEntity userEntity = userService.getUserByToken(token);
    TheoryKnowledgeTestUserEntity testUsers = testUserDao.findFirstByUserIdAndKnowledgeSwfId(
        userEntity.getId(), knowledgeSwfId);
    return ResponseResult.success(testUsers);
  }

  @Transactional
  public Response<TheoryKnowledgeTestUserEntity> saveUserKnowledgeSwfTestContent(String token, TheoryKnowledgeTestUserDto theoryKnowledgeTestUserDto) {
    UserEntity userEntity = userService.getUserByToken(token);
    TheoryKnowledgeTestUserEntity entity = new TheoryKnowledgeTestUserEntity();
    entity.setUserId(userEntity.getId());
    entity.setContent(theoryKnowledgeTestUserDto.getContent());
    entity.setKnowledgeId(theoryKnowledgeTestUserDto.getKnowledgeId());
    entity.setKnowledgeSwfId(theoryKnowledgeTestUserDto.getKnowledgeSwfId());
    entity.setScore(theoryKnowledgeTestUserDto.getScore());
    TheoryKnowledgeTestUserEntity save = testUserDao.save(entity);
    return ResponseResult.success(save);
  }
}
