package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.EquipmentTrainDto;
import com.nip.dto.vo.EquipmentTrainVO;
import com.nip.service.EquipmentTrainService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestHeader;

import java.util.List;
import java.util.Map;

import static com.nip.common.constants.BaseConstants.TOKEN;

/**
 * @Author: wushilin
 * @Data: 2022-12-06 11:40
 * @Description:
 */
@JWT
@Path("/equipmentTrain")
@ApplicationScoped
@Tag(name = "装备操作-训练记录")
public class EquipmentTrainController {

  private final EquipmentTrainService service;

  @Inject
  public EquipmentTrainController(EquipmentTrainService service) {
    this.service = service;
  }

  @POST
  @Path("/add")
  @Operation(summary = "添加训练记录")
  public Response<String> add(@RequestBody EquipmentTrainDto dto, @RestHeader(value = TOKEN) String token) throws Exception {
    return ResponseResult.success(service.addTrain(dto, token));
  }

  @POST
  @Path("/listPage")
  @Operation(summary = "查询所有训练")
  public Response<List<EquipmentTrainVO>> listPage(@RestHeader(value = TOKEN) String token) throws Exception {
    return ResponseResult.success(service.listPage(token));
  }

  @POST
  @Path("/detail")
  @Operation(summary = "详情")
  public Response<EquipmentTrainVO> detail(@RequestBody Map<String, String> param) {
    return ResponseResult.success(service.detail(param));
  }

}
