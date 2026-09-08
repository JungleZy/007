package com.nip.controller;

import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.vo.DeviceDescriptionVO;
import com.nip.dto.vo.DeviceVO;
import com.nip.dto.vo.param.DeviceDeleteParam;
import com.nip.dto.vo.param.DeviceDescriptionAddParam;
import com.nip.dto.vo.param.DeviceUpdateParam;
import com.nip.service.DeviceService;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

import static com.nip.common.constants.BaseConstants.TOKEN;

/**
 * @Author: wushilin
 * @Data: 2023-07-07 10:16
 * @Description:
 */
@Path("/device")
@Tag(name = "装备操作-设备操作")
@ApplicationScoped
public class DeviceController {
  private final DeviceService deviceService;

  @Inject
  public DeviceController(DeviceService deviceService) {
    this.deviceService = deviceService;
  }

  @POST
  @Path("save")
  @Operation(summary = "添加/修改")
  public Response<DeviceVO> save(@RequestBody DeviceUpdateParam param, HttpServerRequest request) throws Exception {
    String token = request.getHeader(TOKEN);
    return ResponseResult.success(deviceService.save(param, token));
  }

  @POST
  @Path("listPage")
  @Operation(summary = "分页")
  public Response<List<DeviceVO>> listPage(@RequestBody DeviceDeleteParam page) {
    return ResponseResult.success(deviceService.listPage(page.getId()));
  }

  @POST
  @Path("delete")
  @Operation(summary = "删除")
  public Response<Void> delete(@RequestBody DeviceDeleteParam param) {
    deviceService.delete(param.getId());
    return ResponseResult.success();
  }

  @POST
  @Path("addDeviceDescription")
  @Operation(summary = "添加设备说明")
  public Response<DeviceDescriptionVO> addDeviceDescription(
      DeviceDescriptionAddParam param,
      HttpServerRequest request) throws Exception {
    String token = request.getHeader(TOKEN);
    return ResponseResult.success(deviceService.addDescription(param, token));
  }

}
