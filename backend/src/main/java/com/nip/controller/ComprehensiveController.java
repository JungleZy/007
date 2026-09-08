package com.nip.controller;


import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.vo.ComprehensiveExamYearVO;
import com.nip.dto.vo.ComprehensiveTheoryYearVO;
import com.nip.dto.vo.ComprehensiveVO;
import com.nip.service.ComprehensiveService;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestQuery;

/**
 * @Author: wushilin
 * @Data: 2022-03-31 11:19
 * @Description:
 */
@JWT
@Path("/comprehensive")
@ApplicationScoped
@Tag(name = "学习管理-综合分析")
public class ComprehensiveController {

  private final ComprehensiveService comprehensiveService;

  @Inject
  public ComprehensiveController(ComprehensiveService comprehensiveService) {
    this.comprehensiveService = comprehensiveService;
  }

  @GET
  @Path("/getUserOverallInfo")
  @Operation(summary = "根据token获取用户总信息", description = "根据token获取用户总信息")
  public Response<ComprehensiveVO> getUserInfo(@Context HttpServerRequest request) {
    return ResponseResult.success(comprehensiveService.getUserOverallInfo(request));
  }

  @GET
  @Path("/getTheoryYear")
  @Operation(summary = "每月理论信息", description = "每月理论信息")
  public Response<ComprehensiveTheoryYearVO> getTheoryYear(@RestQuery(value = "year") String year, HttpServerRequest request) throws Exception {
    return ResponseResult.success(comprehensiveService.getTheoryYear(year, request));
  }

  @GET
  @Path("/getTheoryTestYear")
  @Operation(description = "每月考试信息", summary = "每月考试信息")
  public Response<ComprehensiveExamYearVO> getTheoryTestYear(@RestQuery(value = "year") String year, HttpServerRequest request) throws Exception {
    return ResponseResult.success(comprehensiveService.getTheoryTestYear(year, request));
  }


}
