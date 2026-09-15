package com.nip.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.nip.common.constants.CodeConstants;
import com.nip.common.exception.ForbiddenException;
import com.nip.common.exception.TerminalStateException;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.common.utils.DateTimeUtil;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.ListUtils;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.*;
import com.nip.dto.TestPaperDto;
import com.nip.dto.TestPaperQuestionDto;
import com.nip.dto.TheoryKnowledgeExamDto;
import com.nip.dto.TheoryKnowledgeExamSelfFinishDto;
import com.nip.dto.TheoryKnowledgeQuestionCheckDto;
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
  private final RoleDao roleDao;

  @Inject
  public TheoryKnowledgeExamService(TheoryKnowledgeExamDao theoryKnowledgeExamDao, UserService userService,
      TheoryKnowledgeExamTestPaperDao theoryKnowledgeExamTestPaperDao,
      TheoryKnowledgeExamUserDao theoryKnowledgeExamUserDao, TestPaperQuestionDao questionDao, UserDao userDao,
      RoleDao roleDao) {
    this.theoryKnowledgeExamDao = theoryKnowledgeExamDao;
    this.userService = userService;
    this.theoryKnowledgeExamTestPaperDao = theoryKnowledgeExamTestPaperDao;
    this.theoryKnowledgeExamUserDao = theoryKnowledgeExamUserDao;
    this.questionDao = questionDao;
    this.userDao = userDao;
    this.roleDao = roleDao;
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
        .count("examId = ?1 and (state <> 1 or score > 0 or content is not null and trim(content) <> '')", save.getId());
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
  public Response<List<FindAllExamDto>> findAllTheoryKnowledgeExam(String token, boolean mark) {
    String actorId = userService.getUserByToken(token).getId();
    boolean admin = roleDao.existsAdminRoleByUserId(actorId);
    List<FindAllExamDto> exams = theoryKnowledgeExamDao.findAllExam(mark ? 1 : 3, mark ? 2 : 4);
    return ResponseResult.success(exams.stream()
        .filter(exam -> admin || Objects.equals(actorId, exam.getCreate_user_id())
            || Objects.equals(actorId, exam.getTeacher()))
        .toList());
  }

  @Transactional
  public Response<Map<String, Object>> findTheoryKnowledgeExamById(String token, String id) {
    String actorId = userService.getUserByToken(token).getId();
    TheoryKnowledgeExamEntity exam = Optional.ofNullable(theoryKnowledgeExamDao.findById(id))
        .orElseThrow(() -> new IllegalArgumentException("未查询到考试"));
    TheoryKnowledgeExamUserEntity candidate = theoryKnowledgeExamUserDao.findAllByExamIdAndUserId(id, actorId);
    boolean controller = canControlExam(actorId, exam);
    if (!controller && candidate == null) {
      throw new ForbiddenException("无权查看该考试");
    }
    List<FindExamIdDto> users = theoryKnowledgeExamUserDao.findAllByExamIdSql(id);
    if (!controller) {
      users = users.stream().filter(row -> Objects.equals(actorId, row.getUser_id())).toList();
    }
    Map<String, Object> data = new HashMap<>();
    data.put("exam", theoryKnowledgeExamDao.findAllExamById(id));
    data.put("paper", projectPaper(exam, candidate, controller));
    data.put("user", users);
    return ResponseResult.success(data);
  }

  private boolean isSelfTesting(String examId) {
    return theoryKnowledgeExamUserDao.count("examId = ?1 and isSelfTesting = 0", examId) > 0;
  }

  private boolean canControlExam(String actorId, TheoryKnowledgeExamEntity exam) {
    // A self-test creator is a candidate, including when that account is an administrator.
    return !isSelfTesting(exam.getId()) && (Objects.equals(actorId, exam.getCreateUserId())
        || Objects.equals(actorId, exam.getTeacher()) || roleDao.existsAdminRoleByUserId(actorId));
  }

  private TheoryKnowledgeExamTestPaperEntity projectPaper(TheoryKnowledgeExamEntity exam,
      TheoryKnowledgeExamUserEntity candidate, boolean controller) {
    TheoryKnowledgeExamTestPaperEntity snapshot = Optional.ofNullable(
        theoryKnowledgeExamTestPaperDao.findAllByExamId(exam.getId()))
        .orElseThrow(() -> new IllegalArgumentException("未查询到试卷快照"));
    TheoryKnowledgeExamTestPaperEntity paper = PojoUtils.convertOne(snapshot,
        TheoryKnowledgeExamTestPaperEntity.class);
    boolean review = candidate != null && Objects.equals(candidate.getState(), 4)
        && (Objects.equals(candidate.getIsSelfTesting(), 0) || Objects.equals(exam.getState(), 4));
    if (!controller && !review) {
      paper.setSingleChoiceList(projectQuestions(snapshot.getSingleChoiceList(), 1));
      paper.setMultipleChoiceList(projectQuestions(snapshot.getMultipleChoiceList(), 2));
      paper.setJudgeList(projectQuestions(snapshot.getJudgeList(), 3));
      paper.setCompletionList(projectQuestions(snapshot.getCompletionList(), 4));
      paper.setShortAnswer(projectQuestions(snapshot.getShortAnswer(), 5));
    }
    return paper;
  }

  private String projectQuestions(String json, int type) {
    List<TestPaperQuestionDto> questions = ListUtils.nullToEmpty(JSONUtils.fromJson(json,
        new TypeToken<List<TestPaperQuestionDto>>() {}));
    for (TestPaperQuestionDto question : questions) {
      question.setAnalysis(null);
      question.setAnswer(switch (type) {
        case 2 -> "[]";
        case 4 -> JSONUtils.toJson(Collections.nCopies(completionSlots(question.getTopic()), ""));
        case 5 -> "";
        default -> "\"\"";
      });
    }
    return JSONUtils.toJson(questions);
  }

  private int completionSlots(String topic) {
    if (topic == null) return 0;
    return topic.split("________|\\(___\\)|\\$_\\$", -1).length - 1;
  }

  @Transactional
  public Response<TheoryKnowledgeExamEntity> teacherStartExam(String token, String examId, int type) {
    String actorId = userService.getUserByToken(token).getId();
    TheoryKnowledgeExamEntity entity = Optional.ofNullable(theoryKnowledgeExamDao.findByIdForUpdate(examId))
        .orElseThrow(() -> new IllegalArgumentException("未查询到考试"));
    boolean admin = roleDao.existsAdminRoleByUserId(actorId);
    if (!admin && (type != 2
        || theoryKnowledgeExamUserDao.findByExamAndUserForUpdate(examId, actorId) == null)) {
      throw new ForbiddenException("无权修改该考试状态");
    }
    if (type < 2 || type > 4) {
      throw new IllegalArgumentException("考试状态必须为2、3或4");
    }
    if (Objects.equals(entity.getState(), type)) {
      return ResponseResult.success(entity);
    }
    if (type == 2) {
      if (!Objects.equals(entity.getState(), 1)) {
        throw new TerminalStateException("考试已结束，不能重新开始");
      }
      entity.setStartTime(DateTimeUtil.now());
    } else if (type == 3) {
      if (!Objects.equals(entity.getState(), 2)) {
        throw new TerminalStateException("考试不在进行中，不能结束");
      }
      entity.setEndTime(DateTimeUtil.now());
      List<TheoryKnowledgeExamUserEntity> students = theoryKnowledgeExamUserDao.findAllByExamForUpdate(examId);
      students.forEach(student -> {
        if (Objects.equals(1, student.getState()) || Objects.equals(2, student.getState())) {
          student.setState(3);
          student.setEndTime(entity.getEndTime());
        }
      });
      theoryKnowledgeExamUserDao.save(students);
    } else if (!Objects.equals(entity.getState(), 3)) {
      throw new TerminalStateException("考试尚未结束，不能完成阅卷");
    }
    entity.setState(type);
    TheoryKnowledgeExamEntity save = theoryKnowledgeExamDao.save(entity);
    Map<String, Object> map = new HashMap<>();
    map.put("exam", save);
    List<TheoryKnowledgeExamUserEntity> students = theoryKnowledgeExamUserDao.findAllByExamId(examId);
    students.forEach(student -> WebSocketService.sendInfo(student.getUserId(),
        new ResponseModel(CodeConstants.TEACHERCHANGEEXAMSTATE.getCode(), map)));
    return ResponseResult.success(entity);
  }

  /**
   * 学员自己改考核状态 / 交卷。
   *
   * <p>身份只从 token 推导：历史实现按请求体的 {@code userId} 定位考生行，任何登录用户都能
   * 把别人的 {@code state} 置 3（提前锁死他人考试）或覆盖他人 {@code content}。
   */
  @Transactional
  public Response<Map<String, Object>> studentChangeExamState(String token, String examId, int type, String content) {
    String userId = userService.getUserByToken(token).getId();
    if (type < 1 || type > 3) {
      throw new IllegalArgumentException("考生状态必须为1、2或3");
    }
    TheoryKnowledgeExamEntity entity = Optional.ofNullable(theoryKnowledgeExamDao.findByIdForUpdate(examId))
        .orElseThrow(() -> new IllegalArgumentException("未查询到考试"));
    TheoryKnowledgeExamUserEntity student = Optional.ofNullable(
        theoryKnowledgeExamUserDao.findByExamAndUserForUpdate(examId, userId))
        .orElseThrow(() -> new ForbiddenException("不是本场考试的考生"));
    requireActiveCandidate(entity, student);
    Map<String, Object> data = new HashMap<>();
    if (type == 2) {
      if (StringUtils.isEmpty(student.getStartTime())) {
        student.setStartTime(DateTimeUtil.now());
      }
      data.put("paper", projectPaper(entity, student, canControlExam(userId, entity)));
    } else {
      student.setContent(content);
      if (type == 3) {
        student.setEndTime(DateTimeUtil.now());
      }
    }
    student.setState(type);
    TheoryKnowledgeExamUserEntity save = theoryKnowledgeExamUserDao.save(student);
    data.put("exam", entity);
    data.put("student", save);
    if (StringUtils.isNotBlank(entity.getTeacher())) {
      WebSocketService.sendInfo(entity.getTeacher(),
          new ResponseModel(CodeConstants.STUDENTCHANGEEXAMSTATE.getCode(), data));
    }
    return ResponseResult.success(data);
  }

  /**
   * 学员提交实时答案：同样只认 token 推导出的考生，请求体不再接受 {@code userId}。
   */
  @Transactional
  public Response<TheoryKnowledgeExamUserEntity> saveUserRealTimeParam(String token, String examId, String content) {
    String userId = userService.getUserByToken(token).getId();
    TheoryKnowledgeExamEntity entity = Optional.ofNullable(theoryKnowledgeExamDao.findByIdForUpdate(examId))
        .orElseThrow(() -> new IllegalArgumentException("未查询到考试"));
    TheoryKnowledgeExamUserEntity student = Optional.ofNullable(
        theoryKnowledgeExamUserDao.findByExamAndUserForUpdate(examId, userId))
        .orElseThrow(() -> new ForbiddenException("不是本场考试的考生"));
    requireActiveCandidate(entity, student);
    student.setContent(content);
    TheoryKnowledgeExamUserEntity save = theoryKnowledgeExamUserDao.save(student);
    Map<String, Object> map = new HashMap<>();
    map.put("student", save);
    if (StringUtils.isNotBlank(entity.getTeacher())) {
      WebSocketService.sendInfo(entity.getTeacher(), new ResponseModel(CodeConstants.USERUPLOADCONTENT.getCode(), map));
    }
    return ResponseResult.success(save);
  }

  private void requireActiveCandidate(TheoryKnowledgeExamEntity exam, TheoryKnowledgeExamUserEntity candidate) {
    if (!Objects.equals(exam.getState(), 2)
        || (!Objects.equals(candidate.getState(), 1) && !Objects.equals(candidate.getState(), 2))
        || candidate.getEndTime() != null) {
      throw new TerminalStateException("考试或答卷已结束，不能继续作答");
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
    TheoryKnowledgeExamEntity existing = null;
    if (StringUtils.isNotBlank(dto.getId())) {
      existing = Optional.ofNullable(theoryKnowledgeExamDao.findByIdForUpdate(dto.getId()))
          .orElseThrow(() -> new IllegalArgumentException("未查询到考试"));
      if (!Objects.equals(existing.getCreateUserId(), userEntity.getId())
          && !roleDao.existsAdminRoleByUserId(userEntity.getId())) {
        throw new ForbiddenException("无权修改他人的自测");
      }
      List<TheoryKnowledgeExamUserEntity> existingUsers = theoryKnowledgeExamUserDao.findAllByExamForUpdate(dto.getId());
      if (existingUsers.size() != 1 || !Objects.equals(existingUsers.getFirst().getIsSelfTesting(), 0)
          || !Objects.equals(existingUsers.getFirst().getUserId(), existing.getCreateUserId())) {
        throw new ForbiddenException("普通考核不能改为自测");
      }
      if (Objects.equals(existing.getState(), 3) || Objects.equals(existing.getState(), 4)
          || existingUsers.stream().anyMatch(row -> Objects.equals(row.getState(), 3)
              || Objects.equals(row.getState(), 4) || row.getEndTime() != null)) {
        throw new TerminalStateException("考试已结束，不能重建自测");
      }
    }
    TheoryKnowledgeExamEntity examEntity = PojoUtils.convertOne(dto, TheoryKnowledgeExamEntity.class);
    examEntity.setId(existing == null ? null : existing.getId());
    examEntity.setCreateUserId(existing == null ? userEntity.getId() : existing.getCreateUserId());
    examEntity.setCreateTime(existing == null ? String.valueOf(System.currentTimeMillis()) : existing.getCreateTime());
    examEntity.setStartTime(DateTimeUtil.now());
    // 状态设置成进行中
    examEntity.setState(2);
    TheoryKnowledgeExamEntity save = theoryKnowledgeExamDao.save(examEntity);
    // 再保存考试试卷
    TestPaperDto testPaper = dto.getTestPaper();
    // 只删本场考试自己的旧快照与考生行，绝不按试卷 id 删
    theoryKnowledgeExamTestPaperDao.delete("examId", save.getId());
    theoryKnowledgeExamUserDao.deleteAllByExamId(save.getId());
    TheoryKnowledgeExamTestPaperEntity snap = PojoUtils.convertOne(testPaper,
        TheoryKnowledgeExamTestPaperEntity.class);
    // 快照永远新建，不复用源试卷主键（否则 save 因 id 非空走 merge，覆盖前一场快照）
    snap.setId(null);
    snap.setExamId(save.getId());
    // 五题型列表 null 归一后再序列化，与主路径一致
    snap.setSingleChoiceList(JSONUtils.toJson(ListUtils.nullToEmpty(testPaper.getSingleChoice())));
    snap.setMultipleChoiceList(JSONUtils.toJson(ListUtils.nullToEmpty(testPaper.getMultipleChoice())));
    snap.setJudgeList(JSONUtils.toJson(ListUtils.nullToEmpty(testPaper.getJudge())));
    snap.setCompletionList(JSONUtils.toJson(ListUtils.nullToEmpty(testPaper.getCompletion())));
    snap.setShortAnswer(JSONUtils.toJson(ListUtils.nullToEmpty(testPaper.getShortAnswer())));
    theoryKnowledgeExamTestPaperDao.save(snap);
    // 保存考生考试信息
    TheoryKnowledgeExamUserEntity examUser = new TheoryKnowledgeExamUserEntity();
    examUser.setState(2);
    examUser.setScore(0);
    examUser.setUserId(save.getCreateUserId());
    examUser.setStartTime(save.getStartTime());
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
   * 完成自测：归属校验 + 服务端按试卷快照重算总分。
   *
   * <p>历史实现按 {@code examId} 取「任意一行」考生记录、并把请求体的 {@code score} 直接落库，
   * 等价于「谁都能给任何一场自测打任意分」。现在：考生行只按 (examId, token 所属用户) 定位，
   * 分数只由 {@link #recomputeSelfTestingScore} 依据快照重算，请求体不再有 score 字段。
   *
   * @param token 调用者令牌
   * @param dto   只含 examId 与作答内容
   */
  @Transactional(rollbackOn = Exception.class)
  public TheoryKnowledgeExamEntity finishSelfTesting(String token, TheoryKnowledgeExamSelfFinishDto dto) {
    String actorId = userService.getUserByToken(token).getId();
    TheoryKnowledgeExamEntity exam = Optional.ofNullable(theoryKnowledgeExamDao.findByIdForUpdate(dto.getExamId()))
        .orElseThrow(() -> new IllegalArgumentException("未查询到考试"));
    TheoryKnowledgeExamUserEntity student = theoryKnowledgeExamUserDao
        .findByExamAndUserForUpdate(dto.getExamId(), actorId);
    if (student == null || !Objects.equals(student.getIsSelfTesting(), 0)) {
      throw new ForbiddenException("只能结算本人的自测");
    }
    TheoryKnowledgeExamTestPaperEntity paper = theoryKnowledgeExamTestPaperDao.findAllByExamId(dto.getExamId());
    if (paper == null) {
      throw new IllegalArgumentException("未查询到试卷快照，无法结算自测");
    }
    JsonObject answers = parseAnswerContent(dto.getContent());
    int score = recomputeSelfTestingScore(paper, answers);
    String content = answers == null ? dto.getContent() : JSONUtils.toJson(answers);
    if (Objects.equals(exam.getState(), 4) && Objects.equals(student.getState(), 4)
        && Objects.equals(content, student.getContent())) {
      return exam;
    }
    requireActiveCandidate(exam, student);
    student.setContent(content);
    student.setEndTime(DateTimeUtil.now());
    student.setState(4);
    student.setScore(score);
    theoryKnowledgeExamUserDao.save(student);
    exam.setState(4);
    exam.setEndTime(student.getEndTime());
    theoryKnowledgeExamDao.save(exam);
    return exam;
  }

  /**
   * 把作答内容解析成 JSON 对象；空内容返回 null（记 0 分），非法 JSON 直接拒绝而不是静默记 0。
   */
  private JsonObject parseAnswerContent(String content) {
    if (StringUtils.isBlank(content)) {
      return null;
    }
    JsonElement parsed;
    try {
      parsed = JsonParser.parseString(content);
    } catch (JsonParseException malformed) {
      throw new IllegalArgumentException("作答内容不是合法 JSON");
    }
    return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
  }

  /**
   * 按试卷快照逐题重算自测总分，口径与原前端判分一致：
   * <ul>
   *   <li>单选/多选/判断/填空：作答与快照标准答案归一化后全等才得该题满分；</li>
   *   <li>简答：与标准答案逐字（trim 后）相同才得满分，否则 0 分 —— 自测没有教员阅卷环节，
   *       客户端自报的 {@code teacherScore} 一律被服务端重算值覆盖，不构成加分通道。</li>
   * </ul>
   */
  private int recomputeSelfTestingScore(TheoryKnowledgeExamTestPaperEntity paper, JsonObject answers) {
    if (answers == null) {
      return 0;
    }
    return scoreSection(paper.getSingleChoiceList(), answers, "singleChoice")
        + scoreSection(paper.getMultipleChoiceList(), answers, "multipleChoice")
        + scoreSection(paper.getJudgeList(), answers, "judge")
        + scoreSection(paper.getCompletionList(), answers, "completion")
        + scoreSection(paper.getShortAnswer(), answers, "shortAnswer");
  }

  /**
   * 结算一个题型分区：按题目 id 把作答对到快照题目上，未作答的题不给分。
   *
   * @param snapshotJson 快照中该题型的序列化列表
   * @param answers      整份作答内容（会就地把 {@code teacherScore} 重写为服务端判定值）
   * @param section      作答内容里的题型键
   */
  private int scoreSection(String snapshotJson, JsonObject answers, String section) {
    List<TestPaperQuestionDto> questions = ListUtils.nullToEmpty(
        JSONUtils.fromJson(snapshotJson, new TypeToken<List<TestPaperQuestionDto>>() {
        }));
    if (questions.isEmpty()) {
      return 0;
    }
    Map<String, JsonObject> answered = new HashMap<>();
    JsonElement submitted = answers.get(section);
    if (submitted != null && submitted.isJsonArray()) {
      for (JsonElement item : submitted.getAsJsonArray()) {
        if (!item.isJsonObject()) {
          continue;
        }
        JsonObject entry = item.getAsJsonObject();
        JsonElement id = entry.get("id");
        if (id != null && id.isJsonPrimitive()) {
          // 同一题重复提交时以第一条为准，避免用重复条目刷分
          answered.putIfAbsent(id.getAsString(), entry);
        }
      }
    }
    int sum = 0;
    for (TestPaperQuestionDto question : questions) {
      JsonObject entry = answered.get(question.getId());
      if (entry == null) {
        continue;
      }
      boolean correct = Objects.equals(normalizeAnswer(entry.get("answer")),
          normalizeStoredAnswer(question.getAnswer()));
      int awarded = correct ? Optional.ofNullable(question.getScore()).orElse(0) : 0;
      entry.addProperty("teacherScore", awarded);
      sum += awarded;
    }
    return sum;
  }

  /**
   * 归一化一份作答：数组按 {@code ,} 拼接（与前端 {@code Array.prototype.toString} 同形），
   * 标量取字符串值，空值取空串；再 trim 后比较。
   */
  private static String normalizeAnswer(JsonElement answer) {
    if (answer == null || answer.isJsonNull()) {
      return "";
    }
    if (answer.isJsonArray()) {
      StringBuilder joined = new StringBuilder();
      boolean first = true;
      for (JsonElement item : answer.getAsJsonArray()) {
        if (!first) {
          // 空元素也要占位分隔符，否则 ["","b"] 会被折成 "b" 而与前端 toString 口径分叉
          joined.append(',');
        }
        first = false;
        joined.append(item.isJsonNull() ? "" : item.isJsonPrimitive() ? item.getAsString() : item.toString());
      }
      return joined.toString().trim();
    }
    return (answer.isJsonPrimitive() ? answer.getAsString() : answer.toString()).trim();
  }

  /**
   * 归一化快照里的标准答案：选择/判断/填空题的 answer 列存的是 JSON 文本（前端要 {@code JSON.parse}），
   * 简答题存的是纯文本，两者用同一入口处理 —— 能解析成 JSON 就按 JSON 归一，否则按原文。
   */
  private static String normalizeStoredAnswer(String stored) {
    if (stored == null) {
      return "";
    }
    try {
      return normalizeAnswer(JsonParser.parseString(stored));
    } catch (JsonParseException plainText) {
      return stored.trim();
    }
  }

  /**
   * 考核分析
   *
   * @param examId 考试id
   */
  public TheoryKnowLedgeExamAnalyseVO examineAnalyse(String token, String examId) {
    String actorId = userService.getUserByToken(token).getId();
    TheoryKnowLedgeExamAnalyseVO ret = new TheoryKnowLedgeExamAnalyseVO();
    ret.setOrdinary(0);
    ret.setFailing(0);
    ret.setGood(0);

    TheoryKnowledgeExamEntity examEntity = theoryKnowledgeExamDao.findById(examId);
    if (ObjectUtil.isEmpty(examEntity)) {
      throw new IllegalArgumentException("未查询到考试");
    }
    if (!canControlExam(actorId, examEntity)) {
      throw new ForbiddenException("无权分析该考试");
    }
    List<TheoryKnowledgeExamUserEntity> examUserEntityList = theoryKnowledgeExamUserDao.findAllByExamId(examId);
    // 考题
    TheoryKnowledgeExamTestPaperEntity testPaperEntity = theoryKnowledgeExamTestPaperDao.findAllByExamId(examId);
    // 查无即为错：建考试与建快照在同一事务里落库（saveTheoryKnowledgeExam /
    // saveTheoryKnowledgeExamSelfTesting），删考试也连带删快照（deleteTheoryKnowledgeExam），
    // 所以「考试行在、快照行不在」只可能是脏数据。findAllByExamId 走 firstResult()，查无返 null，
    // 下面对 total/passMark 与五个题型列表全是裸解引用 —— 不拦就是 NPE/500。
    if (ObjectUtil.isEmpty(testPaperEntity)) {
      throw new IllegalArgumentException("未查询到试卷快照，无法进行考核分析");
    }

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
    theoryKnowledgeExamUserDao.deleteAllByExamId(examId);
    theoryKnowledgeExamTestPaperDao.delete("examId = ?1", examId);
    theoryKnowledgeExamDao.deleteById(examId);
    return ResponseResult.success();
  }
}
