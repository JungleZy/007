package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.TheoryKnowledgeExamDto;
import com.nip.dto.sql.FindAllExamDto;
import com.nip.dto.vo.TheoryKnowLedgeExamAnalyseVO;
import com.nip.dto.vo.TheoryKnowledgeExamUserSelfVO;
import com.nip.entity.TheoryKnowledgeExamEntity;
import com.nip.entity.TheoryKnowledgeExamUserEntity;
import com.nip.service.TheoryKnowledgeExamService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestHeader;

import java.util.List;
import java.util.Map;

import static com.nip.common.constants.BaseConstants.*;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/2/22 16:51
 */
@JWT
@Path("/theoryKnowledgeExam")
@ApplicationScoped
@Tag(name = "理论测试接口")
public class TheoryKnowledgeExamController {
  private final TheoryKnowledgeExamService theoryKnowledgeExamService;

  @Inject
  public TheoryKnowledgeExamController(TheoryKnowledgeExamService theoryKnowledgeExamService) {
    this.theoryKnowledgeExamService = theoryKnowledgeExamService;
  }

  @POST
  @Path("/savetheoryKnowledgeExam")
  public Response<Void> saveTheoryKnowledgeExam(@RestHeader(TOKEN) String token, TheoryKnowledgeExamDto map) {
    return theoryKnowledgeExamService.saveTheoryKnowledgeExam(token, map);
  }

  @POST
  @Path("/saveTheoryKnowledgeExamSelfTesting")
  @Operation(summary = "添加自测考试")
  public Response<TheoryKnowledgeExamEntity> saveTheoryKnowledgeExamSelfTesting(@RestHeader(TOKEN) String token,
                                                                                TheoryKnowledgeExamDto dto) throws Exception {
    return ResponseResult.success(theoryKnowledgeExamService.saveTheoryKnowledgeExamSelfTesting(token, dto));
  }

  @POST
  @Path("/listPageSelfTesting")
  @Operation(summary = "自测列表")
  public Response<List<TheoryKnowledgeExamUserSelfVO>> listPageSelfTesting(@RestHeader(TOKEN) String token) throws Exception {
    return ResponseResult.success(theoryKnowledgeExamService.listPageSelfTesting(token));
  }

  @POST
  @Path("/finishSelfTesting")
  @Operation(summary = "完成自测考试")
  public Response<TheoryKnowledgeExamEntity> finishSelfTesting(TheoryKnowledgeExamUserSelfVO vo) {
    return ResponseResult.success(theoryKnowledgeExamService.finishSelfTesting(vo));
  }

  @POST
  @Path("/findAllTheoryKnowledgeExam")
  public Response<List<FindAllExamDto>> findAllTheoryKnowledgeExam(Map<String, Boolean> map) {
    return theoryKnowledgeExamService.findAllTheoryKnowledgeExam(map.get("state"));
  }

  @POST
  @Path("/findTheoryKnowledgeExamById")
  public Response<Map<String, Object>> findTheoryKnowledgeExamById(Map<String, String> map) {
    return theoryKnowledgeExamService.findTheoryKnowledgeExamById(map.get(ID));
  }

  /**
   * 监考人修改考核状态（(开始传2，结束传3，阅卷完毕4））
   *
   * @param map
   * @return
   */
  @POST
  @Path("/teacherStartTheoryKnowledgeExam")
  public Response<TheoryKnowledgeExamEntity> teacherStartTheoryKnowledgeExam(Map<String, String> map) {
    return theoryKnowledgeExamService.teacherStartExam(map.get(EXAM_ID), Integer.parseInt(map.get(TYPE)));
  }

  /**
   * 学员修改考核状态（离开穿1，进入考核传2，结束传3）
   *
   * @param map
   * @return
   */
  @POST
  @Path("/studentChangeExamState")
  public Response<Map<String, Object>> studentChangeExamState(Map<String, String> map) {
    return theoryKnowledgeExamService.studentChangeExamState(map.get(EXAM_ID), map.get(USER_ID), Integer.parseInt(map.get(TYPE)), map.get("content"));
  }


  /**
   * 學員提交实时答案
   *
   * @param map
   * @return
   */
  @POST
  @Path("/studentSaveExamRealtimeContont")
  public Response<TheoryKnowledgeExamUserEntity> saveUserRealTimeParam(Map<String, String> map) {
    return theoryKnowledgeExamService.saveUserRealTimeParam(map.get(EXAM_ID), map.get(USER_ID), map.get("content"));
  }

  /**
   * 考核分析
   *
   * @return
   */
  @POST
  @Path("/examineAnalyse")
  @Operation(summary = "考核分析")
  public Response<TheoryKnowLedgeExamAnalyseVO> examineAnalyse(Map<String, String> body) {
    return ResponseResult.success(theoryKnowledgeExamService.examineAnalyse(body.get(EXAM_ID)));
  }
}
