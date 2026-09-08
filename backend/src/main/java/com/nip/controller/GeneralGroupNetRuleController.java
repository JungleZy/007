package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.vo.GeneralGroupNetRuleVO;
import com.nip.service.GeneralGroupNetRuleService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@JWT
@Path("/generalGroupNetRule")
@ApplicationScoped
@Tag(name = "综合组网运用评分规则")
public class GeneralGroupNetRuleController {
  private final GeneralGroupNetRuleService ruleService;

  @Inject
  public GeneralGroupNetRuleController(GeneralGroupNetRuleService ruleService) {
    this.ruleService = ruleService;
  }

  @POST
  @Path("/save")
  @Operation(summary = "添加配置")
  public Response<GeneralGroupNetRuleVO> save(@RequestBody GeneralGroupNetRuleVO vo) {
    return ResponseResult.success(ruleService.save(vo));
  }

  @POST
  @Path("/findAll")
  @Operation(summary = "查询所有评分规则")
  public Response<List<GeneralGroupNetRuleVO>> findAll() {
    return ResponseResult.success(ruleService.findAll());
  }

  @POST
  @Path("/deleteById")
  @Operation(summary = "根据id删除评分规则")
  public Response<Void> deleteById(@RequestBody GeneralGroupNetRuleVO vo) {
    ruleService.deleteById(vo);
    return ResponseResult.success();
  }
}
