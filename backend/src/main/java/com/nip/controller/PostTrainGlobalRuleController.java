package com.nip.controller;

import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.vo.PostTrainGlobalRuleVO;
import com.nip.service.PostTrainGlobalRuleService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@ApplicationScoped
@Path("/postTrainGlobalRule")
@Tag(name = "岗位训练-报话训练 汉字录入 频分规则")
public class PostTrainGlobalRuleController {
  private final PostTrainGlobalRuleService ruleService;

  @Inject
  public PostTrainGlobalRuleController(PostTrainGlobalRuleService ruleService) {
    this.ruleService = ruleService;
  }

  @POST
  @Path(value = "/addRule")
  @Operation(summary = "添加规则")
  public Response<List<PostTrainGlobalRuleVO>> addRule(@RequestBody List<PostTrainGlobalRuleVO> vo) {
    return ResponseResult.success(ruleService.addRule(vo));
  }

  @POST
  @Path(value = "/findByType")
  @Operation(summary = "根据类型查询规则")
  public Response<List<PostTrainGlobalRuleVO>> findByType(@RequestBody PostTrainGlobalRuleVO vo) {
    return ResponseResult.success(ruleService.findByType(vo));
  }

  @POST
  @Path(value = "/deleteById")
  @Operation(summary = "删除评分规则")
  public Response<Void> deleteById(@RequestBody PostTrainGlobalRuleVO vo) {
    ruleService.deleteById(vo);
    return ResponseResult.success();
  }
}
