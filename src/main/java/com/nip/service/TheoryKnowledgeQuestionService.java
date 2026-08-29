package com.nip.service;


import com.google.gson.reflect.TypeToken;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.TheoryKnowledgeQuestionDao;
import com.nip.dao.TheoryKnowledgeQuestionLevelDao;
import com.nip.dao.UserDao;
import com.nip.dto.TheoryKnowledgeQuestionAllDto;
import com.nip.dto.TheoryKnowledgeQuestionDto;
import com.nip.dto.TheoryKnowledgeQuestionLevelDto;
import com.nip.entity.TheoryKnowledgeQuestionEntity;
import com.nip.entity.TheoryKnowledgeQuestionLevelEntity;
import com.nip.entity.UserEntity;
import io.quarkus.panache.common.Sort;
import io.vertx.core.http.HttpServerResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.function.Function;

import static com.nip.common.constants.BaseConstants.ID;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/1/19 14:23
 */
@ApplicationScoped
public class TheoryKnowledgeQuestionService {
  private final TheoryKnowledgeQuestionDao theoryKnowledgeQuestionDao;
  private final UserService userService;
  private final TheoryKnowledgeQuestionLevelDao theoryKnowledgeQuestionLevelDao;
  private final UserDao userDao;

  @Inject
  public TheoryKnowledgeQuestionService(TheoryKnowledgeQuestionDao theoryKnowledgeQuestionDao,
                                        UserService userService,
                                        TheoryKnowledgeQuestionLevelDao theoryKnowledgeQuestionLevelDao,
                                        UserDao userDao) {
    this.theoryKnowledgeQuestionDao = theoryKnowledgeQuestionDao;
    this.userService = userService;
    this.theoryKnowledgeQuestionLevelDao = theoryKnowledgeQuestionLevelDao;
    this.userDao = userDao;
  }

  @Transactional
  public Response<TheoryKnowledgeQuestionEntity> saveTheoryKnowledgeQuestion(String token, TheoryKnowledgeQuestionDto questionDto) {
    UserEntity userEntity = userService.getUserByToken(token);
    if (!StringUtils.isEmpty(questionDto.getId())) {
      TheoryKnowledgeQuestionEntity tkq = theoryKnowledgeQuestionDao.findByIdOptional(questionDto.getId())
          .orElseThrow(() -> new IllegalArgumentException("未查询到该试题"));
      tkq.setTopic(questionDto.getTopic());
      tkq.setCreateUserId(userEntity.getId());
      tkq.setAnalysis(questionDto.getAnalysis());
      tkq.setType(questionDto.getType());
      tkq.setAnswer(questionDto.getAnswer());
      tkq.setLevelId(questionDto.getLevelId());
      tkq.setOptions(questionDto.getOptions());
      return ResponseResult.success(tkq);
    } else {
      TheoryKnowledgeQuestionEntity entity = new TheoryKnowledgeQuestionEntity();
      entity.setTopic(questionDto.getTopic());
      entity.setCreateUserId(userEntity.getId());
      entity.setAnalysis(questionDto.getAnalysis());
      entity.setType(questionDto.getType());
      entity.setAnswer(questionDto.getAnswer());
      entity.setLevelId(questionDto.getLevelId());
      entity.setOptions(questionDto.getOptions());
      TheoryKnowledgeQuestionEntity save = theoryKnowledgeQuestionDao.save(entity);
      return ResponseResult.success(save);
    }
  }

  @Transactional
  public Response<TheoryKnowledgeQuestionLevelEntity> saveTheoryKnowledgeQuestionLevel(String token, TheoryKnowledgeQuestionLevelDto map) {
    UserEntity userEntity = userService.getUserByToken(token);
    if (!StringUtils.isEmpty(map.getId())) {
      TheoryKnowledgeQuestionLevelEntity tkql = theoryKnowledgeQuestionLevelDao.findByIdOptional(map.getId())
          .orElseThrow(() -> new IllegalArgumentException("未查询到该题目分类"));
      tkql.setParentId(map.getParentId());
      tkql.setName(map.getName());
      return ResponseResult.success(tkql);
    } else {
      TheoryKnowledgeQuestionLevelEntity entity = new TheoryKnowledgeQuestionLevelEntity();
      entity.setParentId(map.getParentId());
      entity.setName(map.getName());
      entity.setCreateUserId(userEntity.getId());
      TheoryKnowledgeQuestionLevelEntity save = theoryKnowledgeQuestionLevelDao.save(entity);
      return ResponseResult.success(save);
    }
  }

  @Transactional
  public Response<List<TheoryKnowledgeQuestionLevelEntity>> findAllTheoryKnowledgeQuestionLevel() {
    List<TheoryKnowledgeQuestionLevelEntity> res = theoryKnowledgeQuestionLevelDao.findAll(Sort.by("createTime").descending()).list();
    return ResponseResult.success(res);
  }

  List<String> ids = new ArrayList<>();

  private void findAllLevel(String id) {
    ids.add(id);
    List<TheoryKnowledgeQuestionLevelEntity> allByParentId = theoryKnowledgeQuestionLevelDao.findAllByParentId(id);
    if (!allByParentId.isEmpty()) {
      allByParentId.forEach(a -> {
        findAllLevel(a.getId());
      });
    }
  }

  @Transactional
  public Response<List<TheoryKnowledgeQuestionAllDto>> findAllQuestionByLevelId(String id, String type, String name) {
    findAllLevel(id);
    List<TheoryKnowledgeQuestionEntity> allByIdIn;
    if (!StringUtils.isEmpty(type)) {
      if (StringUtils.isEmpty(name)) {
        allByIdIn = theoryKnowledgeQuestionDao.findAllByLevelIdInAndType(ids, Integer.parseInt(type));
      } else {
        allByIdIn = theoryKnowledgeQuestionDao.findAllByLevelIdInAndTypeAndTopicLike(ids, Integer.parseInt(type), "%" + name + "%");
      }
    } else {
      if (StringUtils.isEmpty(name)) {
        allByIdIn = theoryKnowledgeQuestionDao.findAllByLevelIdIn(ids);
      } else {
        allByIdIn = theoryKnowledgeQuestionDao.findAllByLevelIdInAndTopicLike(ids, "%" + name + "%");
      }
    }
    ids = new ArrayList<>();
    List<TheoryKnowledgeQuestionAllDto> theoryKnowledgeQuestionAllDtos = PojoUtils.convert(allByIdIn, TheoryKnowledgeQuestionAllDto.class);

    Set<String> userIds = allByIdIn.stream()
        .map(TheoryKnowledgeQuestionEntity::getCreateUserId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    Map<String, UserEntity> users = userDao.queryByIdIn(userIds).stream()
        .collect(Collectors.toMap(UserEntity::getId, Function.identity()));

    theoryKnowledgeQuestionAllDtos.forEach(ques -> {
      UserEntity user = users.get(ques.getCreateUserId());
      if (user != null) {
        ques.setCreateUserName(user.getUserName());
      }
    });
    return ResponseResult.success(theoryKnowledgeQuestionAllDtos);
  }

  @Transactional
  public Response<List<TheoryKnowledgeQuestionLevelEntity>> deleteTheoryKnowledgeQuestionLevelById(String id) {
    theoryKnowledgeQuestionLevelDao.deleteById(id);
    return ResponseResult.success(theoryKnowledgeQuestionLevelDao.findAll().list());
  }

  @Transactional
  public Response<List<TheoryKnowledgeQuestionEntity>> deleteTheoryKnowledgeQuestion(String id) {
    theoryKnowledgeQuestionDao.deleteById(id);
    return ResponseResult.success(theoryKnowledgeQuestionDao.findAll().list());
  }

  /**
   * 导出模板
   */
  public void exportTemplate(HttpServerResponse response) {
  }

  public List<TheoryKnowledgeQuestionEntity> exportQuestionByLevelId(HttpServerResponse response, String levelId) {
    return theoryKnowledgeQuestionDao.findAllByLevelId(levelId);
  }
}
