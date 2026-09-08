package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.vo.DeviceTypeVO;
import com.nip.dto.vo.param.DeviceTypeUpdateParam;
import com.nip.service.DeviceTypeService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestHeader;

import java.util.List;

import static com.nip.common.constants.BaseConstants.TOKEN;

/**
 * @Author: wushilin
 * @Data: 2023-07-07 09:20
 * @Description:
 */
@JWT
@Path("/deviceType")
@ApplicationScoped
@Tag(name = "装备操作")
public class DeviceTypeController {

  private final DeviceTypeService typeService;

  @Inject
  public DeviceTypeController(DeviceTypeService typeService) {
    this.typeService = typeService;
  }

  @POST
  @Path(value = "save")
  @Operation(summary = "添加/修改类型")
  public Response<DeviceTypeVO> save(@RestHeader(TOKEN) String token, DeviceTypeUpdateParam param) throws Exception {
    return ResponseResult.success(typeService.save(param, token));
  }

  @POST
  @Path(value = "findAll")
  @Operation(summary = "查询所有类型")
  public Response<List<DeviceTypeVO>> findAll() {
    return ResponseResult.success(typeService.findAll());
  }

  @POST
  @Path(value = "delete")
  @Operation(summary = "删除设备")
  public Response<Void> delete(DeviceTypeUpdateParam param) {
    typeService.delete(param);
    return ResponseResult.success();
  }

}
