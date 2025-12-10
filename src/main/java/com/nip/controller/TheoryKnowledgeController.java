package com.nip.controller;


import com.google.gson.reflect.TypeToken;
import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.common.utils.JSONUtils;
import com.nip.dto.TheoryKnowledgeClassifyDto;
import com.nip.dto.TheoryKnowledgeDto;
import com.nip.dto.TheoryKnowledgesDto;
import com.nip.dto.sql.FindTheoryKnowledgeDto;
import com.nip.dto.vo.TheoryKnowledgeClassifyPageVO;
import com.nip.dto.vo.TheoryKnowledgeClassifyVO;
import com.nip.dto.vo.TheoryKnowledgeDocumentContentVO;
import com.nip.entity.TheoryKnowledgeEntity;
import com.nip.entity.TheoryKnowledgeSwfRecordEntity;
import com.nip.service.TheoryKnowledgeClassifyService;
import com.nip.service.TheoryKnowledgeService;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.List;
import java.util.Map;

import static com.nip.common.constants.BaseConstants.*;

/**
 * ToolsController
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2021-05-08 15:27
 */
@JWT
@Path("/theoryKnowledge")
@ApplicationScoped
@Tag(name = "理论学习接口")
public class TheoryKnowledgeController {
  private final TheoryKnowledgeService knowledgeService;
  private final TheoryKnowledgeClassifyService classifyService;

  @Inject
  public TheoryKnowledgeController(TheoryKnowledgeService knowledgeService, TheoryKnowledgeClassifyService classifyService) {
    this.knowledgeService = knowledgeService;
    this.classifyService = classifyService;
  }

  @POST
  @Path("/getBasicTheory")
  public Response<List<FindTheoryKnowledgeDto>> getBasicTheory(Map<String, String> map) {
    if (map.isEmpty()) {
      return knowledgeService.getAll(0, null, null);
    }
    List<String> difficulty = null;
    if (map.get("difficulty") != null) {
      difficulty = JSONUtils.fromJson(map.get("difficulty"), new TypeToken<>() {
      });
    }
    List<String> specialty = null;
    if (map.get("specialty") != null) {
      specialty = JSONUtils.fromJson(map.get("specialty"), new TypeToken<>() {
      });
    }

    return knowledgeService.getAll(Integer.parseInt(map.get(TYPE)), difficulty, specialty);
  }

  @POST
  @Path("/getBasicTheoryOpen")
  public Response<List<TheoryKnowledgeDto>> getBasicTheoryOpen(@RestHeader(TOKEN) String token, Map<String, String> map) {
    List<String> difficulty = null;
    if (map.get("difficulty") != null) {
      difficulty = JSONUtils.fromJson(map.get("difficulty"), new TypeToken<>() {
      });
    }
    List<String> specialty = null;
    if (map.get("specialty") != null) {
      specialty = JSONUtils.fromJson(map.get("specialty"), new TypeToken<>() {
      });
    }
    return knowledgeService.getAll(Integer.parseInt(map.get(TYPE)), 1, token, difficulty, specialty);
  }

//  @PostMapping("/findAllThoty")
//  public Response getBasicTheoryOpen(@RequestHeader(BaseConstants.TOKEN) String token) {
//    return knowledgeService.getAll(0, 1,token);
//  }

  @POST
  @Path("/getDutyBusiness")
  public Response<List<FindTheoryKnowledgeDto>> getDutyBusiness() {
    return knowledgeService.getAll(1, null, null);
  }

  @POST
  @Path("/getChargeEquipment")
  public Response<List<FindTheoryKnowledgeDto>> getChargeEquipment() {
    return knowledgeService.getAll(2, null, null);
  }

  @POST
  @Path("/getById")
  public Response<TheoryKnowledgesDto> getById(Map<String, String> map) {
    return knowledgeService.getById(map.get(ID));
  }

  @POST
  @Path("/getByIdAndToken")
  public Response<TheoryKnowledgesDto> getById(@RestHeader(TOKEN) String token, Map<String, String> map) {
    return knowledgeService.getByIdAndToken(map.get(ID), token);
  }

  @POST
  @Path("/saveTheoryKnowledge")
  public Response<TheoryKnowledgeEntity> saveTheoryKnowledge(TheoryKnowledgesDto knowledgesDto) {
    return knowledgeService.saveTheoryKnowledge(knowledgesDto);
  }

  @POST
  @Path("/saveTheoryKnowledgeRecord")
  public Response<TheoryKnowledgeSwfRecordEntity> saveTheoryKnowledgeRecord(@RestHeader(TOKEN) String token,
                                                                            TheoryKnowledgeSwfRecordEntity record) {
    return knowledgeService.saveTheoryKnowledgeRecord(token, record);
  }

  @POST
  @Path("/deleteThroyKnowledgeById")
  public Response<Void> deleteThroyKnowledgeById(@RequestBody Map<String, String> map) {
    return knowledgeService.deleteThroyKnowledgeById(map.get(ID));
  }

  @POST
  @Path("/recordStatistice")
  public Response<Map<String, Object>> recordStatistice(@RestHeader(TOKEN) String token,
                                                        Map<String, String> map) {
    return knowledgeService.recordStatistice(token, map.get("year"), map.get("month"), Integer.parseInt(map.get(TYPE)));
  }

  @POST
  @Path("/gradeCount")
  public Response<Object> gradeCount(@RestHeader(TOKEN) String token, Map<String, String> map) {
    return knowledgeService.gradeCount(token, map.get("year"), map.get("month"), Integer.parseInt(map.get(TYPE)));
  }

  @POST
  @Path("/listPageClassify")
  @Operation(summary = "查询所有难易/专业分类")
  public Response<TheoryKnowledgeClassifyPageVO> listPageClassify() {
    return ResponseResult.success(classifyService.listPageClassify());
  }

  @POST
  @Path("/addClassify")
  @Operation(summary = "添加或修改难易/专业分类")
  public Response<TheoryKnowledgeClassifyVO> addClassify(TheoryKnowledgeClassifyDto dto, @RestHeader(TOKEN) String token) {
    return ResponseResult.success(classifyService.add(dto, token));
  }

  @POST
  @Path("/removeClassify")
  @Operation(summary = "移除难易/专业分类")
  public Response<Void> removeClassify(@RequestBody TheoryKnowledgeClassifyDto dto) {
    classifyService.remove(dto);
    return ResponseResult.success();
  }

  @POST
  @Path("/uploadFileToNip")
  @Operation(summary = "上传文件到NIP服务中")
  public Response<TheoryKnowledgeDocumentContentVO> updateFileToNip(FileUpload file, HttpServerRequest request) {
    return ResponseResult.success(classifyService.updateFileToNip(file, request));
  }
}
