package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
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
  public Response<Map<String, Object>> findAllTheoryKnowledgeExamUser(@RestHeader(TOKEN) String token, Map<String, Boolean> map) throws Exception {
    //创建返回结果集
    Map<String, Object> ret = new HashMap<>();
    Boolean type = map.get(TYPE);
    Object exam = null;
    List<TheoryKnowledgeExamUserSelfVO> examSelfVos = null;
    //如果是Null 则查询自测和考评
    if (Objects.isNull(type)) {
      exam = theoryKnowledgeExamUserService.findAllTheoryKnowledgeExamUser(token, map.get("state")).getData();
      examSelfVos = theoryKnowledgeExamService.listPageSelfTesting(token);
    } else if (type) { //如果是true 只查询自测
      examSelfVos = theoryKnowledgeExamService.listPageSelfTesting(token);
    } else { //只查询考评
      exam = theoryKnowledgeExamUserService.findAllTheoryKnowledgeExamUser(token, map.get("state")).getData();
    }
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
