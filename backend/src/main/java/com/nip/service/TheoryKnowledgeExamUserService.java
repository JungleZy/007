package com.nip.service;


import com.google.gson.reflect.TypeToken;
import com.nip.common.exception.ForbiddenException;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.common.utils.JSONUtils;
import com.nip.dao.RoleDao;
import com.nip.dao.TheoryKnowledgeExamDao;
import com.nip.dao.TheoryKnowledgeExamUserDao;
import com.nip.dto.AllExamDto;
import com.nip.entity.TheoryKnowledgeExamEntity;
import com.nip.entity.TheoryKnowledgeExamUserEntity;
import com.nip.entity.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/3/2 9:05
 */
@ApplicationScoped
public class TheoryKnowledgeExamUserService {
  private final TheoryKnowledgeExamUserDao theoryKnowledgeExamUserDao;
  private final TheoryKnowledgeExamDao theoryKnowledgeExamDao;
  private final RoleDao roleDao;
  private final UserService userService;

  @Inject
  public TheoryKnowledgeExamUserService(TheoryKnowledgeExamUserDao theoryKnowledgeExamUserDao,
      TheoryKnowledgeExamDao theoryKnowledgeExamDao, RoleDao roleDao, UserService userService) {
    this.theoryKnowledgeExamUserDao = theoryKnowledgeExamUserDao;
    this.theoryKnowledgeExamDao = theoryKnowledgeExamDao;
    this.roleDao = roleDao;
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

  /**
   * 教员阅卷上分（端点已收敛到 {@code @RequireAdmin}）。
   *
   * <p>先整批校验归属，再整批落库：只要列表中有一个条目不是该 examId 下真实存在的考生行，
   * 就抛 {@link IllegalArgumentException}（业务码 202）中止事务，同批的合法考生也不会被写入，
   * 避免「半批上分」留下不可追溯的分数。</p>
   */
  @Transactional
  public Response<Void> teacherUploadScore(String examId, Object map) {
    List<Map<String, Object>> maps = JSONUtils.fromJson(JSONUtils.toJson(map), new TypeToken<>() {
    });
    if (Objects.isNull(maps) || maps.isEmpty()) {
      throw new IllegalArgumentException("上分列表不能为空");
    }
    // 第一遍：解析并校验每个条目的考生归属，任一不匹配整批拒绝
    List<TheoryKnowledgeExamUserEntity> targets = new ArrayList<>(maps.size());
    for (Map<String, Object> entry : maps) {
      Object rawUserId = entry.get("user_id");
      if (Objects.isNull(rawUserId) || rawUserId.toString().isBlank()) {
        throw new IllegalArgumentException("上分列表中存在缺少 user_id 的条目，整批已拒绝");
      }
      String userId = rawUserId.toString();
      TheoryKnowledgeExamUserEntity examUser = theoryKnowledgeExamUserDao.findAllByExamIdAndUserId(examId, userId);
      if (Objects.isNull(examUser)) {
        throw new IllegalArgumentException("考生 " + userId + " 不是本场考试的考生，整批上分已拒绝");
      }
      targets.add(examUser);
    }
    // 第二遍：全部校验通过后才写入
    for (int i = 0; i < maps.size(); i++) {
      Map<String, Object> entry = maps.get(i);
      TheoryKnowledgeExamUserEntity examUser = targets.get(i);
      examUser.setScore(new BigDecimal(entry.get("score").toString()).intValue());
      examUser.setState(4);
      examUser.setContent(
          Optional.ofNullable(entry.get("content"))
              .map(JSONUtils::toJson)
              .orElse("{}")
      );
      theoryKnowledgeExamUserDao.save(examUser);
    }
    return ResponseResult.success();
  }


  /**
   * 读取某考生在某场考试中的答卷。
   *
   * <p>{@code userId} 必须保留为入参：教员阅卷与实时监看传的正是<b>他人</b>的考生 id，
   * 改成 token 推导会让教员只能读到自己（且教员在该场考试里没有 exam_user 行）。
   * 因此这里不换参数，只补授权：本人、该场考试的监考人/创建人、系统管理员放行，其余 207。
   */
  @Transactional
  public Response<TheoryKnowledgeExamUserEntity> findExamUser(String token, String userId, String examId) {
    String actorId = userService.getUserByToken(token).getId();
    TheoryKnowledgeExamEntity exam = theoryKnowledgeExamDao.findById(examId);
    if (Objects.isNull(exam)) {
      throw new IllegalArgumentException("未查询到考试");
    }
    boolean allowed = Objects.equals(actorId, userId)
        || Objects.equals(actorId, exam.getTeacher())
        || Objects.equals(actorId, exam.getCreateUserId())
        || roleDao.existsAdminRoleByUserId(actorId);
    if (!allowed) {
      throw new ForbiddenException("无权查看他人的答卷");
    }
    return ResponseResult.success(theoryKnowledgeExamUserDao.findAllByExamIdAndUserId(examId, userId));
  }
}
