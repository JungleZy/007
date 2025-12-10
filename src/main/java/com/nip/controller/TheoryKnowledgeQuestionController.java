package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.TheoryKnowledgeQuestionAllDto;
import com.nip.dto.TheoryKnowledgeQuestionDto;
import com.nip.dto.TheoryKnowledgeQuestionLevelDto;
import com.nip.entity.TheoryKnowledgeQuestionEntity;
import com.nip.entity.TheoryKnowledgeQuestionLevelEntity;
import com.nip.service.TheoryKnowledgeQuestionService;
import io.vertx.core.http.HttpServerResponse;
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
 * @Date:Create 2022/1/19 14:29
 */
@JWT
@Path("/theoryKnowledgeQuestion")
@ApplicationScoped
@Tag(name = "理论测试试题库")
public class TheoryKnowledgeQuestionController {
  private final TheoryKnowledgeQuestionService theoryKnowledgeQuestionService;

  @Inject
  public TheoryKnowledgeQuestionController(TheoryKnowledgeQuestionService theoryKnowledgeQuestionService) {
    this.theoryKnowledgeQuestionService = theoryKnowledgeQuestionService;
  }

  @POST
  @Path("/saveTheoryKnowledgeQuestion")
  public Response<TheoryKnowledgeQuestionEntity> saveTheoryKnowledgeQuestion(@RestHeader(TOKEN) String token, TheoryKnowledgeQuestionDto entity) {
    return theoryKnowledgeQuestionService.saveTheoryKnowledgeQuestion(token, entity);
  }

  @POST
  @Path("/saveTheoryKnowledgeQuestionLevel")
  public Response<TheoryKnowledgeQuestionLevelEntity> saveTheoryKnowledgeQuestionLevel(@RestHeader(TOKEN) String token, TheoryKnowledgeQuestionLevelDto map) {
    return theoryKnowledgeQuestionService.saveTheoryKnowledgeQuestionLevel(token, map);
  }

  @POST
  @Path("/findAllTheoryKnowledgeQuestionLevel")
  public Response<List<TheoryKnowledgeQuestionLevelEntity>> findAllTheoryKnowledgeQuestionLevel() {
    return theoryKnowledgeQuestionService.findAllTheoryKnowledgeQuestionLevel();
  }

  @POST
  @Path("/deleteTheoryKnowledgeQuestionLevelById")
  public Response<List<TheoryKnowledgeQuestionLevelEntity>> deleteTheoryKnowledgeQuestionLevelById(Map<String, String> map) {
    return theoryKnowledgeQuestionService.deleteTheoryKnowledgeQuestionLevelById(map.get(ID));
  }

  @POST
  @Path("/deleteTheoryKnowledgeQuestion")
  public Response<List<TheoryKnowledgeQuestionEntity>> deleteTheoryKnowledgeQuestion(Map<String, String> map) {
    return theoryKnowledgeQuestionService.deleteTheoryKnowledgeQuestion(map.get(ID));
  }

  @POST
  @Path("/findAllQuestionByLevelId")
  public Response<List<TheoryKnowledgeQuestionAllDto>> findAllQuestionByLevelId(Map<String, String> map) {
    return theoryKnowledgeQuestionService.findAllQuestionByLevelId(map.get("levelId"), map.get(TYPE), map.get("name"));
  }

  @POST
  @Path("/upLoadFile")
  @Operation(summary = "上传题库-新框架不做上传功能")
  public Response<Void> upLoadFile() {
    return ResponseResult.success();
  }

  @POST
  @Path("/exportTemplate")
  @Operation(summary = "导出模板")
  public void exportTemplate(HttpServerResponse response) {
    theoryKnowledgeQuestionService.exportTemplate(response);
  }


  @POST
  @Path("/exportQuestionByLevelId")
  @Operation(summary = "导出指定题库-需要改造-后端只提供数据由前端生成文件导出")
  public Response<List<TheoryKnowledgeQuestionEntity>> exportQuestionByLevelId(HttpServerResponse response, Map<String, String> map) {
    return ResponseResult.success(theoryKnowledgeQuestionService.exportQuestionByLevelId(response, map.get("levelId")));
  }
}
