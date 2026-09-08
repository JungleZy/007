package com.nip.controller;

import com.nip.common.constants.ResponseCode;
import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.AllExamDto;
import com.nip.dto.vo.TheoryKnowledgeExamUserSelfVO;
import com.nip.entity.TheoryKnowledgeExamUserEntity;
import com.nip.service.TheoryKnowledgeExamService;
import com.nip.service.TheoryKnowledgeExamUserService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestHeader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.nip.common.constants.BaseConstants.*;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/3/2 9:47
 */
@JWT
@Path("/theoryKnowledgeExamUser")
@ApplicationScoped
@Tag(name = "学员端理论测试接口")
public class TheoryKnowledgeExamUserController {
  private final TheoryKnowledgeExamUserService theoryKnowledgeExamUserService;
  private final TheoryKnowledgeExamService theoryKnowledgeExamService;

  @Inject
  public TheoryKnowledgeExamUserController(TheoryKnowledgeExamUserService theoryKnowledgeExamUserService, TheoryKnowledgeExamService theoryKnowledgeExamService) {
    this.theoryKnowledgeExamUserService = theoryKnowledgeExamUserService;
    this.theoryKnowledgeExamService = theoryKnowledgeExamService;
  }

  @POST
  @Path("/findAllTheoryKnowledgeExamUser")
  public Response<Map<String, Object>> findAllTheoryKnowledgeExamUser(@RestHeader(TOKEN) String token,
      Map<String, Boolean> map) throws Exception {
    Boolean type = map.get(TYPE);
    Object exam = null;
    List<TheoryKnowledgeExamUserSelfVO> examSelfVos = null;
    // type 为 null 查自测 + 考评；true 只查自测；false 只查考评
    if (!Boolean.TRUE.equals(type)) {
      Boolean state = map.get("state");
      if (Objects.isNull(state)) {
        // 原来直接把 null 传给 boolean 形参会拆箱 NPE → 500
        throw new IllegalArgumentException("state 不能为空");
      }
      Response<List<AllExamDto>> examResponse = theoryKnowledgeExamUserService
          .findAllTheoryKnowledgeExamUser(token, state);
      if (examResponse.getCode() != ResponseCode.SUCCESS.getCode()) {
        // 内层业务码必须透传，不能被外层 success 信封盖住
        return ResponseResult.error(examResponse.getCode(), examResponse.getMessage(),
            examResponse.getDescription());
      }
      exam = examResponse.getData();
    }
    if (!Boolean.FALSE.equals(type)) {
      examSelfVos = theoryKnowledgeExamService.listPageSelfTesting(token);
    }
    //创建返回结果集
    Map<String, Object> ret = new HashMap<>();
    ret.put("exam", exam);
    ret.put("examSelf", examSelfVos);
    return ResponseResult.success(ret);
  }

  @POST
  @Path("/teacherUploadScore")
  public Response<Void> teacherUploadScore(@RequestBody Map<String, Object> map) {
    return theoryKnowledgeExamUserService.teacherUploadScore(String.valueOf(map.get(EXAM_ID)), map.get("list"));
  }

  @POST
  @Path("/findExamUser")
  public Response<TheoryKnowledgeExamUserEntity> findExamUser(@RequestBody Map<String, String> map) {
    return theoryKnowledgeExamUserService.findExamUser(map.get(USER_ID), map.get(EXAM_ID));
  }
}
