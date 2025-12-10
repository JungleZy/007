package com.nip.controller;


import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.entity.GradingRuleEntity;
import com.nip.service.GradingRuleService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;

import java.util.List;
import java.util.Map;

import static com.nip.common.constants.BaseConstants.ID;
import static com.nip.common.constants.BaseConstants.TYPE;

/**
 * LayersController
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2021-05-08 15:27
 */
@ApplicationScoped
@JWT
@Path("/gradingRule")
public class GradingRuleController {
  private final GradingRuleService gradingRuleService;

  @Inject
  public GradingRuleController(GradingRuleService gradingRuleService) {
    this.gradingRuleService = gradingRuleService;
  }

  @Path("/getGradingRuleListByType")
  @POST
  @Operation(summary = "根据type获取该类型下所有评分规则")
  public Response<List<GradingRuleEntity>> getGradingRuleListByType(Map<String, Integer> data) {
    return gradingRuleService.getGradingRuleListByType(data.get(TYPE));
  }

  @Path("/getGradingRuleById")
  @POST
  @Operation(summary = "根据ID获取单条评分规则")
  public Response<GradingRuleEntity> getGradingRuleById(Map<String, String> data) {
    return gradingRuleService.getGradingRuleById(data.get(ID));
  }

  @Path("/saveGradingRule")
  @Operation(summary = "保存评分规则")
  @POST
  public Response<GradingRuleEntity> saveGradingRule(GradingRuleEntity entity) {
    return gradingRuleService.saveGradingRule(entity);
  }

  @Path("/updateGradingRuleStatus")
  @POST
  @Operation(summary = "更新规则状态")
  public Response<GradingRuleEntity> updateGradingRuleStatus(Map<String, Object> data) {
    return gradingRuleService.updateGradingRuleStatus(data.get(ID).toString(), (Integer) data.get("status"));
  }

  @Path("/changeGradingRuleIsDefault")
  @POST
  @Operation(summary = "更新默认项")
  public Response<Void> changeGradingRuleIsDefault(Map<String, Object> data) {
    return gradingRuleService.changeGradingRuleIsDefault(data.get(ID).toString());
  }
}
