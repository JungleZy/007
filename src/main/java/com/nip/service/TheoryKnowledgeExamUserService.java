package com.nip.service;


import com.google.gson.reflect.TypeToken;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.common.utils.JSONUtils;
import com.nip.dao.TheoryKnowledgeExamUserDao;
import com.nip.dto.AllExamDto;
import com.nip.entity.TheoryKnowledgeExamUserEntity;
import com.nip.entity.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/3/2 9:05
 */
@ApplicationScoped
public class TheoryKnowledgeExamUserService {
  private final TheoryKnowledgeExamUserDao theoryKnowledgeExamUserDao;
  private final UserService userService;

  @Inject
  public TheoryKnowledgeExamUserService(TheoryKnowledgeExamUserDao theoryKnowledgeExamUserDao, UserService userService) {
    this.theoryKnowledgeExamUserDao = theoryKnowledgeExamUserDao;
    this.userService = userService;
  }

  @Transactional
  public Response<List<AllExamDto>> findAllTheoryKnowledgeExamUser(String token, boolean type) {
    UserEntity userEntity = userService.getUserByToken(token);
    List<AllExamDto> theoryKnowledgeExamUserEntities;
    if (type) {
      theoryKnowledgeExamUserEntities = theoryKnowledgeExamUserDao.fingAllExam(1, 2, userEntity.getId());
    } else {
      theoryKnowledgeExamUserEntities = theoryKnowledgeExamUserDao.fingAllExamTwo(4, userEntity.getId());
    }
    return ResponseResult.success(theoryKnowledgeExamUserEntities);
  }

  @Transactional
  public Response<Void> teacherUploadScore(String examId, Object map) {
    List<Map<String, Object>> maps = JSONUtils.fromJson(JSONUtils.toJson(map), new TypeToken<>() {
    });
    for (Map<String, Object> entry : maps) {
      String userId = entry.get("user_id").toString();
      TheoryKnowledgeExamUserEntity allByExamIdAndUserId = theoryKnowledgeExamUserDao.findAllByExamIdAndUserId(examId, userId);
      if (null != allByExamIdAndUserId) {
        allByExamIdAndUserId.setScore(new BigDecimal(entry.get("score").toString()).intValue());
        allByExamIdAndUserId.setState(4);
        allByExamIdAndUserId.setContent(
            Optional.ofNullable(entry.get("content"))
                .map(JSONUtils::toJson)
                .orElse("{}")
        );
      }
      theoryKnowledgeExamUserDao.save(allByExamIdAndUserId);
    }
    return ResponseResult.success();
  }


  @Transactional
  public Response<TheoryKnowledgeExamUserEntity> findExamUser(String userId, String examId) {
    return ResponseResult.success(theoryKnowledgeExamUserDao.findAllByExamIdAndUserId(examId, userId));
  }
}
