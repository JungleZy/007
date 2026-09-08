package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.EquipmentDeviceDto;
import com.nip.dto.EquipmentDeviceKeyPointsDto;
import com.nip.dto.vo.EquipmentDeviceKeyPointsVo;
import com.nip.dto.vo.EquipmentDeviceVo;
import com.nip.service.EquipmentDeviceService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2022-10-31 14:10
 * @Description:
 */
@JWT
@Path("/equipmentDevice")
@ApplicationScoped
@Tag(name = "装备操作-设备管理")
public class EquipmentDeviceController {

  private final EquipmentDeviceService deviceService;

  @Inject
  public EquipmentDeviceController(EquipmentDeviceService deviceService) {
    this.deviceService = deviceService;
  }

  @POST
  @Path("/add")
  @Operation(summary = "添加设备")
  public Response<Void> add(@RequestBody EquipmentDeviceDto dto) {
    deviceService.addDevice(dto);
    return ResponseResult.success();
  }

  @POST
  @Path("/delete")
  @Operation(summary = "删除设备")
  public Response<Void> delete(@RequestBody EquipmentDeviceVo vo) {
    deviceService.deleteDevice(vo);
    return ResponseResult.success();
  }

  @POST
  @Path("/listPage")
  @Operation(summary = "设备列表")
  public Response<List<EquipmentDeviceVo>> listResponse() {
    return ResponseResult.success(deviceService.listPage());
  }

  @POST
  @Path("/update")
  @Operation(summary = "更新设备")
  public Response<Void> update(@RequestBody EquipmentDeviceVo vo) {
    deviceService.update(vo);
    return ResponseResult.success();
  }

  @POST
  @Path("/addKeyPoints")
  @Operation(summary = "添加、修改、删除、要点讲解")
  public Response<Void> addKeyPoints(@RequestBody EquipmentDeviceKeyPointsDto dto) {
    deviceService.saveKeyPoints(dto);
    return ResponseResult.success();
  }

  @POST
  @Path("/listKeyPoints")
  @Operation(summary = "查看设备要点")
  public Response<List<EquipmentDeviceKeyPointsVo>> listKeyPoints() {
    return ResponseResult.success(deviceService.listKeyPoints());
  }

}
