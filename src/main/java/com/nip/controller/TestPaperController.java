package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.dto.TestPaperDto;
import com.nip.service.TestPaperService;
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
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/1/24 16:09
 */
@JWT
@Path("/theoryKnowledgeTestPaper")
@ApplicationScoped
@Tag(name = "理论测试试卷库")
public class TestPaperController {
  private final TestPaperService testPaperService;

  @Inject
  public TestPaperController(TestPaperService testPaperService) {
    this.testPaperService = testPaperService;
  }

  @POST
  @Path("/saveTestPaper")
  public Response<Void> saveTestPaper(@RestHeader(TOKEN) String token, TestPaperDto testPaperDto) {
    return testPaperService.saveTestPaper(token, testPaperDto);
  }

  @POST
  @Path("/findAllTestPaper")
  public Response<List<TestPaperDto>> findAllTestPaper() {
    return testPaperService.findAllTestPaper();
  }

  @POST
  @Path("/findTestPaperById")
  public Response<TestPaperDto> findTestPaperById(Map<String, String> map) {
    return testPaperService.findTestPaperById(map.get(ID));
  }

  @POST
  @Path("/findTestPaperByLevelIdAndName")
  public Response<List<TestPaperDto>> findTestPaperByLevelIdAndName(Map<String, String> map) {
    return testPaperService.findTestPaperByLevelIdAndName(map.get("levelId"), map.get("name"));
  }
}
