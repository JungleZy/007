package com.nip.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.google.gson.reflect.TypeToken;
import com.nip.common.constants.CodeConstants;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.common.utils.DateTimeUtil;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.ListUtils;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.*;
import com.nip.dto.TestPaperDto;
import com.nip.dto.TheoryKnowledgeExamDto;
import com.nip.dto.TheoryKnowledgeQuestionCheckDto;
import com.nip.dto.sql.FindAllExamByIdDto;
import com.nip.dto.sql.FindAllExamDto;
import com.nip.dto.sql.FindExamIdDto;
import com.nip.dto.vo.TheoryKnowLedgeExamAnalyseVO;
import com.nip.dto.vo.TheoryKnowLedgeExamUserVO;
import com.nip.dto.vo.TheoryKnowledgeExamUserSelfVO;
import com.nip.dto.vo.TheoryKnowledgeQuestionErrorTopVO;
import com.nip.entity.*;
import com.nip.ws.WebSocketService;
import com.nip.ws.model.ResponseModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/2/22 16:50
 */
@ApplicationScoped
@Slf4j
public class TheoryKnowledgeExamService {
  private final TheoryKnowledgeExamDao theoryKnowledgeExamDao;
  private final UserService userService;
  private final TheoryKnowledgeExamTestPaperDao theoryKnowledgeExamTestPaperDao;
  private final TheoryKnowledgeExamUserDao theoryKnowledgeExamUserDao;
  private final TestPaperQuestionDao questionDao;
  private final UserDao userDao;

  @Inject
  public TheoryKnowledgeExamService(TheoryKnowledgeExamDao theoryKnowledgeExamDao, UserService userService,
      TheoryKnowledgeExamTestPaperDao theoryKnowledgeExamTestPaperDao,
      TheoryKnowledgeExamUserDao theoryKnowledgeExamUserDao, TestPaperQuestionDao questionDao, UserDao userDao) {
    this.theoryKnowledgeExamDao = theoryKnowledgeExamDao;
    this.userService = userService;
    this.theoryKnowledgeExamTestPaperDao = theoryKnowledgeExamTestPaperDao;
    this.theoryKnowledgeExamUserDao = theoryKnowledgeExamUserDao;
    this.questionDao = questionDao;
    this.userDao = userDao;
  }

  @Transactional
  public Response<Void> saveTheoryKnowledgeExam(String token, TheoryKnowledgeExamDto dto) {
    UserEntity userEntity = userService.getUserByToken(token);
    TheoryKnowledgeExamEntity entity = PojoUtils.convertOne(dto, TheoryKnowledgeExamEntity.class);
    entity.setCreateUserId(userEntity.getId());
    entity.setState(1);
    TheoryKnowledgeExamEntity save = theoryKnowledgeExamDao.save(entity);
    TestPaperDto testPaper = dto.getTestPaper();
    // #4 状态守卫：已有作答/进行中/已交卷的考生存在时禁止重建
    long touched = theoryKnowledgeExamUserDao
        .count("examId = ?1 and (state <> 1 or score > 0)", save.getId());
    if (touched > 0) {
      throw new IllegalStateException("考试已有作答记录，禁止编辑重建考生名单");
    }
    // #19 只删本考试自己的旧快照，绝不按试卷 id 删
    theoryKnowledgeExamTestPaperDao.delete("examId", save.getId());
    theoryKnowledgeExamUserDao.deleteAllByExamId(save.getId());

    TheoryKnowledgeExamTestPaperEntity snap = PojoUtils.convertOne(testPaper,
        TheoryKnowledgeExamTestPaperEntity.class);
    snap.setId(null); // #19 快照永远新建，不复用源试卷主键
    snap.setExamId(save.getId());
    // #20 五列表 null 归一后再序列化
    snap.setSingleChoiceList(JSONUtils.toJson(ListUtils.nullToEmpty(testPaper.getSingleChoice())));
    snap.setMultipleChoiceList(JSONUtils.toJson(ListUtils.nullToEmpty(testPaper.getMultipleChoice())));
    snap.setJudgeList(JSONUtils.toJson(ListUtils.nullToEmpty(testPaper.getJudge())));
    snap.setCompletionList(JSONUtils.toJson(ListUtils.nullToEmpty(testPaper.getCompletion())));
    snap.setShortAnswer(JSONUtils.toJson(ListUtils.nullToEmpty(testPaper.getShortAnswer())));
    theoryKnowledgeExamTestPaperDao.save(snap);
    dto.getStuId().forEach(stu -> {
      TheoryKnowledgeExamUserEntity theoryKnowledgeExamUserEntity = new TheoryKnowledgeExamUserEntity();
      theoryKnowledgeExamUserEntity.setUserId(stu);
      theoryKnowledgeExamUserEntity.setExamId(save.getId());
      theoryKnowledgeExamUserEntity.setState(1);
      theoryKnowledgeExamUserEntity.setScore(0);
      theoryKnowledgeExamUserEntity.setIsSelfTesting(1);
      theoryKnowledgeExamUserDao.save(theoryKnowledgeExamUserEntity);
    });
    return ResponseResult.success();
  }

  @Transactional
  public Response<List<FindAllExamDto>> findAllTheoryKnowledgeExam(boolean mark) {
    if (mark) {
      return ResponseResult.success(theoryKnowledgeExamDao.findAllExam(1, 2));
    } else {
      return ResponseResult.success(theoryKnowledgeExamDao.findAllExam(3, 4));
    }
  }

  @Transactional
  public Response<Map<String, Object>> findTheoryKnowledgeExamById(String id) {
    try {
      FindAllExamByIdDto allExamById = theoryKnowledgeExamDao.findAllExamById(id);
      TheoryKnowledgeExamTestPaperEntity paper = theoryKnowledgeExamTestPaperDao.findAllByExamId(id);
      List<FindExamIdDto> allByExamIdSql = theoryKnowledgeExamUserDao.findAllByExamIdSql(id);
      Map<String, Object> map = new HashMap<>();
      map.put("exam", allExamById);
      map.put("paper", paper);
      map.put("user", allByExamIdSql);
      return ResponseResult.success(map);
    } catch (Exception e) {
      log.error("findTheoryKnowledgeExamById error", e);
      return ResponseResult.error();
    }
  }

  @Transactional
  public Response<TheoryKnowledgeExamEntity> teacherStartExam(String examId, int type) {
    try {
      TheoryKnowledgeExamEntity entity = theoryKnowledgeExamDao.findById(examId);
      if (type == 2) {
        entity.setStartTime(DateTimeUtil.now());
      } else if (type == 3) {
        entity.setEndTime(DateTimeUtil.now());
        List<TheoryKnowledgeExamUserEntity> allByExamId = theoryKnowledgeExamUserDao.findAllByExamId(examId);
        allByExamId.forEach(a -> {
          if (3 != a.getState()) {
            a.setState(3);
            a.setEndTime(entity.getEndTime());
          }
        });
        theoryKnowledgeExamUserDao.save(allByExamId);
      }
      entity.setState(type);
      TheoryKnowledgeExamEntity save = theoryKnowledgeExamDao.save(entity);
      Map<String, Object> map = new HashMap<>();
      map.put("exam", save);
      List<TheoryKnowledgeExamUserEntity> allByExamId = theoryKnowledgeExamUserDao.findAllByExamId(examId);
      allByExamId.forEach(a -> WebSocketService.sendInfo(a.getUserId(),
          new ResponseModel(CodeConstants.TEACHERCHANGEEXAMSTATE.getCode(), map)));
      return ResponseResult.success(entity);
    } catch (Exception e) {
      log.error("teacherStartExam error", e);
      return ResponseResult.error();
    }
  }

  @Transactional
  public Response<Map<String, Object>> studentChangeExamState(String examId, String userId, int type, String content) {
    try {
      TheoryKnowledgeExamEntity entity = theoryKnowledgeExamDao.findById(examId);
      TheoryKnowledgeExamUserEntity allByExamIdAndUserId = theoryKnowledgeExamUserDao.findAllByExamIdAndUserId(examId,
          userId);
      Map<String, Object> data = new HashMap<>();
      switch (type) {
        case 2 -> {
          if (StringUtils.isEmpty(allByExamIdAndUserId.getStartTime())) {
            allByExamIdAndUserId.setStartTime(DateTimeUtil.now());
          }
          TheoryKnowledgeExamTestPaperEntity paper = theoryKnowledgeExamTestPaperDao.findAllByExamId(examId);
          data.put("paper", paper);
          allByExamIdAndUserId.setState(type);
        }
        case 3 -> {
          allByExamIdAndUserId.setEndTime(DateTimeUtil.now());
          allByExamIdAndUserId.setState(type);
          allByExamIdAndUserId.setContent(content);
        }
        default -> {
          allByExamIdAndUserId.setContent(content);
          allByExamIdAndUserId.setState(type);
        }
      }
      TheoryKnowledgeExamUserEntity save = theoryKnowledgeExamUserDao.save(allByExamIdAndUserId);
      data.put("exam", entity);
      data.put("student", save);
      WebSocketService.sendInfo(entity.getTeacher(),
          new ResponseModel(CodeConstants.STUDENTCHANGEEXAMSTATE.getCode(), data));
      return ResponseResult.success(data);
    } catch (Exception e) {
      log.error("student change status error", e);
      return ResponseResult.error();
    }
  }

  @Transactional
  public Response<TheoryKnowledgeExamUserEntity> saveUserRealTimeParam(String examId, String userId, String content) {
    try {
      TheoryKnowledgeExamUserEntity allByExamIdAndUserId = theoryKnowledgeExamUserDao.findAllByExamIdAndUserId(examId,
          userId);
      if (ObjectUtil.isEmpty(allByExamIdAndUserId)) {
        return ResponseResult.error("数据错误");
      }
      allByExamIdAndUserId.setContent(content);
      TheoryKnowledgeExamUserEntity save = theoryKnowledgeExamUserDao.save(allByExamIdAndUserId);
      TheoryKnowledgeExamEntity entity = theoryKnowledgeExamDao.findById(examId);
      Map<String, Object> map = new HashMap<>();
      map.put("student", save);
      WebSocketService.sendInfo(entity.getTeacher(), new ResponseModel(CodeConstants.USERUPLOADCONTENT.getCode(), map));
      return ResponseResult.success(save);
    } catch (Exception e) {
      log.error("saveUserRealTimeParam", e);
      return ResponseResult.error();
    }
  }

  /**
   * 新增自测考试
   *
   * @param token
   * @param dto
   * @throws Exception
   */
  @Transactional
  public TheoryKnowledgeExamEntity saveTheoryKnowledgeExamSelfTesting(String token, TheoryKnowledgeExamDto dto)
      throws Exception {
    UserEntity userEntity = userService.getUserByToken(token);
    // 先保存这场考试
    TheoryKnowledgeExamEntity examEntity = PojoUtils.convertOne(dto, TheoryKnowledgeExamEntity.class);
    examEntity.setCreateUserId(userEntity.getId());
    // 状态设置成进行中
    examEntity.setState(2);
    TheoryKnowledgeExamEntity save = theoryKnowledgeExamDao.save(examEntity);
    // 再保存考试试卷
    TestPaperDto testPaper = dto.getTestPaper();
    TheoryKnowledgeExamTestPaperEntity theoryKnowledgeExamTestPaperEntity = PojoUtils.convertOne(testPaper,
        TheoryKnowledgeExamTestPaperEntity.class);
    theoryKnowledgeExamTestPaperEntity.setExamId(save.getId());
    theoryKnowledgeExamTestPaperEntity.setSingleChoiceList(JSONUtils.toJson(testPaper.getSingleChoice()));
    theoryKnowledgeExamTestPaperEntity.setMultipleChoiceList(JSONUtils.toJson(testPaper.getMultipleChoice()));
    theoryKnowledgeExamTestPaperEntity.setJudgeList(JSONUtils.toJson(testPaper.getJudge()));
    theoryKnowledgeExamTestPaperEntity.setCompletionList(JSONUtils.toJson(testPaper.getCompletion()));
    theoryKnowledgeExamTestPaperEntity.setShortAnswer(JSONUtils.toJson(testPaper.getShortAnswer()));
    theoryKnowledgeExamTestPaperDao.save(theoryKnowledgeExamTestPaperEntity);
    // 保存考生考试信息
    TheoryKnowledgeExamUserEntity examUser = new TheoryKnowledgeExamUserEntity();
    examUser.setState(2);
    examUser.setScore(0);
    examUser.setUserId(userEntity.getId());
    examUser.setStartTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    examUser.setExamId(save.getId());
    examUser.setIsSelfTesting(0);
    theoryKnowledgeExamUserDao.save(examUser);
    return save;
  }

  /**
   * 自测列表
   *
   * @param token
   * @return
   * @throws Exception
   */
  public List<TheoryKnowledgeExamUserSelfVO> listPageSelfTesting(String token) throws Exception {
    UserEntity userEntity = userService.getUserByToken(token);
    // 查询该用户的t_theory_knowledge_exam_user表is_self_testing=0
    return theoryKnowledgeExamUserDao.findAllIsSelfTesting(userEntity.getId());
  }

  /**
   * 完成自测
   *
   * @param vo
   */
  @Transactional(rollbackOn = Exception.class)
  public TheoryKnowledgeExamEntity finishSelfTesting(TheoryKnowledgeExamUserSelfVO vo) {
    TheoryKnowledgeExamUserEntity examUserEntity = theoryKnowledgeExamUserDao.findByExamId(vo.getExamId());
    if (ObjectUtil.isEmpty(examUserEntity)) {
      throw new IllegalArgumentException("未查询到训练");
    }
    examUserEntity.setContent(vo.getContent());
    examUserEntity.setEndTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    examUserEntity.setState(4);
    examUserEntity.setScore(vo.getScore());
    // 保存 exam_user表
    theoryKnowledgeExamUserDao.save(examUserEntity);

    // 保存exam表
    TheoryKnowledgeExamEntity examEntity = theoryKnowledgeExamDao.findById(vo.getExamId());
    if (ObjectUtil.isEmpty(examEntity)) {
      throw new IllegalArgumentException("未查询到考试");
    }
    examEntity.setState(4);
    theoryKnowledgeExamDao.save(examEntity);

    return examEntity;

  }

  /**
   * 考核分析
   *
   * @param examId 考试id
   */
  public TheoryKnowLedgeExamAnalyseVO examineAnalyse(String examId) {
    TheoryKnowLedgeExamAnalyseVO ret = new TheoryKnowLedgeExamAnalyseVO();
    ret.setOrdinary(0);
    ret.setFailing(0);
    ret.setGood(0);

    TheoryKnowledgeExamEntity examEntity = theoryKnowledgeExamDao.findById(examId);
    if (ObjectUtil.isEmpty(examEntity)) {
      throw new IllegalArgumentException("未查询到考试");
    }
    List<TheoryKnowledgeExamUserEntity> examUserEntityList = theoryKnowledgeExamUserDao.findAllByExamId(examId);
    // 考题
    TheoryKnowledgeExamTestPaperEntity testPaperEntity = theoryKnowledgeExamTestPaperDao.findAllByExamId(examId);

    // 拿到本场考试的就及格比吧 计算良的区间 公式：(总分-及格分)/2+及格分
    Integer total = BigDecimal.valueOf((long) testPaperEntity.getTotal() - (long) testPaperEntity.getPassMark())
        .divide(new BigDecimal(2), 0, RoundingMode.DOWN)
        .add(new BigDecimal(testPaperEntity.getPassMark()))
        .intValue();

    List<TheoryKnowLedgeExamUserVO> previousUser = new ArrayList<>();
    List<TheoryKnowledgeQuestionEntity> questionEntities = new ArrayList<>();

    questionEntities.addAll(ListUtils.nullToEmpty(JSONUtils
        .fromJson(testPaperEntity.getSingleChoiceList(), new TypeToken<>() {
        })));
    questionEntities.addAll(ListUtils.nullToEmpty(JSONUtils
        .fromJson(testPaperEntity.getMultipleChoiceList(), new TypeToken<>() {
        })));
    questionEntities.addAll(ListUtils.nullToEmpty(JSONUtils
        .fromJson(testPaperEntity.getJudgeList(), new TypeToken<>() {
        })));
    questionEntities.addAll(ListUtils.nullToEmpty(JSONUtils
        .fromJson(testPaperEntity.getCompletionList(), new TypeToken<>() {
        })));
    questionEntities.addAll(ListUtils.nullToEmpty(JSONUtils
        .fromJson(testPaperEntity.getShortAnswer(), new TypeToken<>() {
        })));

    // 再依次比对找出记录题目错误的次数
    Map<String, TheoryKnowledgeQuestionErrorTopVO> errorTop3 = new HashMap<>();

    examUserEntityList.forEach(item -> {
      String userId = item.getUserId();
      UserEntity userEntity = userDao.findByIdOptional(userId)
          .orElseThrow(() -> new IllegalArgumentException("未查询到考生用户"));
      String endTime = item.getEndTime();
      // 查询上一次考试成绩
      TheoryKnowledgeExamUserEntity previous = theoryKnowledgeExamUserDao.findByUserIdAndEndTimePrevious(userId,
          endTime);
      previousUser.add(
          new TheoryKnowLedgeExamUserVO(userEntity.getUserAccount(),
              item.getScore(),
              Optional.ofNullable(previous)
                  .map(TheoryKnowledgeExamUserEntity::getScore)
                  .orElse(0)));

      // 查询错题
      String content = item.getContent();
      List<TheoryKnowledgeQuestionCheckDto> userQuestionEntity = new ArrayList<>();
      if (StringUtils.isNotBlank(content)) {
        Map<String, List<Map<String, Object>>> map = JSONUtils.fromJson(content, new TypeToken<>() {
        });
        userQuestionEntity = map.keySet().stream().filter(s -> Objects.equals(s, "completion") ||
            Objects.equals(s, "singleChoice") ||
            Objects.equals(s, "judge") ||
            Objects.equals(s, "multipleChoice") ||
            Objects.equals(s, "shortAnswer"))
            .map(s -> PojoUtils.convert(map.get(s), TheoryKnowledgeQuestionCheckDto.class)).flatMap(Collection::stream)
            .toList();
      }

      for (TheoryKnowledgeQuestionEntity questionEntity : questionEntities) {
        boolean isError = true;
        for (TheoryKnowledgeQuestionCheckDto userQuestion : userQuestionEntity) {
          if (Objects.equals(userQuestion.getId(), questionEntity.getId()) &&
              (questionEntity.getType().compareTo(5) == 0 &&
                  Optional.ofNullable(userQuestion.getTeacherScore()).orElse(BigDecimal.ZERO)
                      .compareTo(BigDecimal.ZERO) > 0
                  ||
                  Objects.equals(userQuestion.getAnswer(), questionEntity.getAnswer()))) {
            isError = false;
            break;
          }

        }
        if (isError) {
          TheoryKnowledgeQuestionErrorTopVO top = Optional.ofNullable(errorTop3.get(questionEntity.getId()))
              .map(temp -> temp.setNumber(temp.getNumber() + 1))
              .orElseGet(() -> new TheoryKnowledgeQuestionErrorTopVO()
                  .setNumber(1)
                  .setId(questionEntity.getId())
                  .setTopic(questionEntity.getTopic())
                  .setType(questionEntity.getType()));
          errorTop3.put(questionEntity.getId(), top);

        }
      }

      if (item.getScore().compareTo(testPaperEntity.getPassMark()) < 0) {
        ret.setFailing(ret.getFailing() + 1);
      } else if (item.getScore().compareTo(testPaperEntity.getPassMark()) >= 0
          && item.getScore().compareTo(total) < 0) {
        ret.setOrdinary(ret.getOrdinary() + 1);
      } else {
        ret.setGood(ret.getGood() + 1);
      }
    });

    List<TheoryKnowledgeQuestionErrorTopVO> errorTopDtos = errorTop3.values().stream()
        .sorted((o1, o2) -> o2.getNumber() - o1.getNumber())
        .toList();
    ret.setErrorTop3(errorTopDtos);
    ret.setScoreList(previousUser);
    return ret;
  }

  @Transactional
  public Response<Void> deleteTheoryKnowledgeExam(String examId) {
    try {
      theoryKnowledgeExamUserDao.deleteAllByExamId(examId);
      theoryKnowledgeExamTestPaperDao.delete("examId = ?1", examId);
      theoryKnowledgeExamDao.deleteById(examId);
      return ResponseResult.success();
    } catch (Exception e) {
      return ResponseResult.error();
    }
  }
}
