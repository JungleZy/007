package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.DeviceScoringRuleDto;
import com.nip.dto.vo.DeviceScoringRuleVO;
import com.nip.service.DeviceScoringRuleService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestQuery;

import static com.nip.common.constants.BaseConstants.DEVICE_ID;
import static com.nip.common.constants.BaseConstants.ID;

/**
 * @Author: wushilin
 * @Data: 2023-08-22 14:18
 * @Description:
 */
@JWT
@Path("/deviceScoringRule")
@ApplicationScoped
@Tag(name = "设备评分规则")
public class DeviceScoringRuleController {

  private final DeviceScoringRuleService ruleService;

  @Inject
  public DeviceScoringRuleController(DeviceScoringRuleService ruleService) {
    this.ruleService = ruleService;
  }

  @Path("/save")
  @POST
  @Operation(summary = "添加/修改规则")
  public Response<Void> saveRule(DeviceScoringRuleDto dto) {
    ruleService.save(dto);
    return ResponseResult.success();
  }

  @GET()
  @Path("/delete")
  @Operation(summary = "删除")
  public Response<Void> delete(@RestQuery(ID) Integer id) {
    ruleService.deleteRule(id);
    return ResponseResult.success();
  }

  @GET
  @Path("/findAllByDeviceId")
  @Operation(summary = "查询")
  public Response<DeviceScoringRuleVO> findAllByDeviceId(@RestQuery(DEVICE_ID) Integer deviceId) {
    return ResponseResult.success(ruleService.findAllByDeviceId(deviceId));
  }


}
