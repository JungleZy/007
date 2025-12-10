package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.dto.TheoryKnowledgeTestDto;
import com.nip.dto.TheoryKnowledgeTestUserDto;
import com.nip.entity.TheoryKnowledgeTestContentEntity;
import com.nip.entity.TheoryKnowledgeTestUserEntity;
import com.nip.service.TheoryKnowledgeTestService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestHeader;

import java.util.List;
import java.util.Map;

import static com.nip.common.constants.BaseConstants.ID;
import static com.nip.common.constants.BaseConstants.TOKEN;

/**
 * ToolsController
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2021-05-08 15:27
 */
@JWT
@Path("/theoryKnowledgeTest")
@ApplicationScoped
@Tag(name = "理论学习教案测验接口")
public class TheoryKnowledgeTestController {
  private final TheoryKnowledgeTestService testService;

  @Inject
  public TheoryKnowledgeTestController(TheoryKnowledgeTestService testService) {
    this.testService = testService;
  }

  @POST
  @Path("/saveTheoryKnowledgeTest")
  public Response<TheoryKnowledgeTestDto> saveTheoryKnowledgeTest(@RestHeader(TOKEN) String token,
                                                                  TheoryKnowledgeTestDto testDto) {
    return testService.saveKnowledgeTest(token, testDto);
  }

  @POST
  @Path("/saveUserKnowledgeSwfTestContent")
  public Response<TheoryKnowledgeTestUserEntity> saveUserKnowledgeSwfTestContent(@RestHeader(TOKEN) String token, TheoryKnowledgeTestUserDto theoryKnowledgeTestUserDto) {
    return testService.saveUserKnowledgeSwfTestContent(token, theoryKnowledgeTestUserDto);
  }

  @POST
  @Path("/getTestBySwfId")
  public Response<List<TheoryKnowledgeTestDto>> getTestBySwfId(Map<String, String> map) {
    return testService.getAllByKnowledgeSwfId(map.get(ID));
  }

  @POST
  @Path("/getByKnowledgeSwfIdAndEnable")
  public Response<List<TheoryKnowledgeTestContentEntity>> getByKnowledgeSwfIdAndEnable(Map<String, String> map) {
    return testService.getByKnowledgeSwfIdAndEnable(map.get(ID));
  }

  @POST
  @Path("/getTestContentByUserIdAndKnowledgeId")
  public Response<List<TheoryKnowledgeTestUserEntity>> getTestContentByUserIdAndKnowledgeId(@RestHeader(TOKEN) String token,
                                                                                            Map<String, String> map) {
    return testService.getTestContentByUserIdAndKnowledgeId(token, map.get(ID));
  }

  @Path("/getTestContentById")
  @POST
  public Response<TheoryKnowledgeTestUserEntity> getTestContentById(Map<String, String> map) {
    return testService.getTestContentById(map.get(ID));
  }

  @POST
  @Path("/getTestContentByUserIdAndKnowledgeSwfId")
  public Response<TheoryKnowledgeTestUserEntity> getTestContentByUserIdAndKnowledgeSwfId(@RestHeader(TOKEN) String token,
                                                                                         Map<String, String> map) {
    return testService.getTestContentByUserIdAndKnowledgeSwfId(token, map.get(ID));
  }
}
