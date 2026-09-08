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
import com.nip.dto.vo.TheoryKnowledgeQuestionTemplateColumnVO;
import com.nip.entity.TheoryKnowledgeQuestionEntity;
import com.nip.entity.TheoryKnowledgeQuestionLevelEntity;
import com.nip.entity.UserEntity;
import io.quarkus.panache.common.Sort;
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
   * 导出指定题库的试题（后端只提供数据，由前端生成文件）
   */
  public List<TheoryKnowledgeQuestionEntity> exportQuestionByLevelId(String levelId) {
    return theoryKnowledgeQuestionDao.findAllByLevelId(levelId);
  }

  /**
   * 批量导入题库。Excel 由前端解析成 JSON 行后提交，后端不引入 poi ——
   * 与 {@code MilitaryTermDataService#saveBatch} 同一分工（其 API 原文即
   * 「批量保存军语密语-代替之前文件导入」）。
   *
   * <p>整批单事务：任何一行校验失败都抛异常，已处理的行一并回滚，不做「部分导入」。
   */
  @Transactional
  public List<TheoryKnowledgeQuestionEntity> saveBatch(String token, List<TheoryKnowledgeQuestionDto> params) {
    if (params == null || params.isEmpty()) {
      throw new IllegalArgumentException("导入数据为空或格式不完整");
    }
    UserEntity userEntity = userService.getUserByToken(token);
    List<TheoryKnowledgeQuestionEntity> saved = new ArrayList<>(params.size());
    for (int i = 0; i < params.size(); i++) {
      TheoryKnowledgeQuestionDto dto = params.get(i);
      int rowNumber = i + 1;
      if (dto == null) {
        throw new IllegalArgumentException("第 " + rowNumber + " 行为空");
      }
      if (StringUtils.isBlank(dto.getTopic())) {
        throw new IllegalArgumentException("第 " + rowNumber + " 行缺少题目");
      }
      if (dto.getType() == null) {
        throw new IllegalArgumentException("第 " + rowNumber + " 行缺少测验类型");
      }
      if (StringUtils.isBlank(dto.getLevelId())) {
        throw new IllegalArgumentException("第 " + rowNumber + " 行缺少所属题库");
      }
      TheoryKnowledgeQuestionEntity entity = new TheoryKnowledgeQuestionEntity();
      entity.setTopic(dto.getTopic());
      entity.setType(dto.getType());
      entity.setOptions(dto.getOptions());
      entity.setAnswer(dto.getAnswer());
      entity.setAnalysis(dto.getAnalysis());
      entity.setLevelId(dto.getLevelId());
      entity.setCreateUserId(userEntity.getId());
      saved.add(theoryKnowledgeQuestionDao.save(entity));
    }
    return saved;
  }

  /**
   * 导入模板的列规格。前端据此生成 .xlsx 模板，用户填好后再由前端解析成
   * JSON 行提交 {@link #saveBatch}。
   *
   * <p>列顺序与字段名必须与 {@link TheoryKnowledgeQuestionDto} 保持一致，
   * 否则用户按模板填的表导不进来。
   */
  public List<TheoryKnowledgeQuestionTemplateColumnVO> exportTemplate() {
    return List.of(
        new TheoryKnowledgeQuestionTemplateColumnVO("type", "测验类型", true, "1",
            "1 单选题、2 多选题、3 判断题、4 填空题、5 简答题"),
        new TheoryKnowledgeQuestionTemplateColumnVO("topic", "题目", true, "下列关于短波通信的说法正确的是",
            "题干正文"),
        new TheoryKnowledgeQuestionTemplateColumnVO("options", "选项", false, "[\"A.选项一\",\"B.选项二\"]",
            "选择题必填；判断题、填空题、简答题留空"),
        new TheoryKnowledgeQuestionTemplateColumnVO("answer", "答案", false, "A",
            "多选题多个答案连写，如 AC"),
        new TheoryKnowledgeQuestionTemplateColumnVO("analysis", "解析", false, "见教材第三章",
            "可留空"),
        new TheoryKnowledgeQuestionTemplateColumnVO("levelId", "所属题库 ID", true,
            "取自 findAllTheoryKnowledgeQuestionLevel 的 id", "必须是已存在的题库节点 ID"));
  }
}
