package com.nip.controller.simulation;

import cn.hutool.core.util.ObjectUtil;
import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.vo.param.simulation.router.SimulationDisturdDetailParam;
import com.nip.dto.vo.param.simulation.router.SimulationRoomRouterContentAddParam;
import com.nip.dto.vo.simulation.disturd.SimulationDisturdDetailVO;
import com.nip.dto.vo.simulation.disturd.SimulationDisturdSettingVO;
import com.nip.dto.vo.simulation.disturd.SimulationDisturdTrainVO;
import com.nip.dto.vo.simulation.disturd.SimulationDisturdUploadResultVO;
import com.nip.dto.vo.simulation.router.SimulationRouterRoomContentVO;
import com.nip.service.simulation.SimulationRouterRoomContentService;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@JWT
@Path("/simulation/routerRoomContent")
@ApplicationScoped
@Tag(name = "仿真训练-快报/干扰报")
public class SimulationDisturdController {
  private final SimulationRouterRoomContentService roomContentService;

  @Inject
  public SimulationDisturdController(SimulationRouterRoomContentService roomContentService) {
    this.roomContentService = roomContentService;
  }

  @POST
  @Path("/addRoomAndContent")
  @Operation(summary = "添加房间")
  public Response<Object> addRoomAndContent(HttpServerRequest request, @RequestBody SimulationRoomRouterContentAddParam param) {
    if (ObjectUtil.isNotEmpty(param)) {
      Integer i = roomContentService.addRoomAndContent(request, param);
      return ResponseResult.success(i);
    } else {
      return ResponseResult.error("参数不能为空");
    }
  }

  @POST
  @Path("/addStudent")
  @Operation(summary = "添加学生")
  public Response<Object> addStudent(HttpServerRequest request, @RequestBody SimulationDisturdDetailParam param) {
    if (ObjectUtil.isNotEmpty(param)) {
      Integer i = roomContentService.addStudent(request, param);
      return ResponseResult.success(i);
    } else {
      return ResponseResult.error("参数不能为空");
    }
  }

  @POST
  @Path("/findAlls")
  @Operation(summary = "查询所有数据")
  public Response<List<SimulationRouterRoomContentVO>> findAlls(HttpServerRequest request) {
    return ResponseResult.success(roomContentService.findAlls(request));
  }

  @POST
  @Path("/findById")
  @Operation(summary = "查看详情")
  public Response<SimulationDisturdDetailVO> findById(HttpServerRequest request, @RequestBody SimulationDisturdDetailParam param) {
    Integer roomId = param.getRoomId();
    return ResponseResult.success(roomContentService.findOne(request, roomId));
  }


  @POST
  @Path("/findTrainUser")
  @Operation(summary = "参训人员列表")
  public Response<List<SimulationDisturdTrainVO>> findTrainUser(HttpServerRequest request, @RequestBody SimulationDisturdDetailParam param) {
    Integer roomId = param.getRoomId();
    return ResponseResult.success(roomContentService.findTrainUser(request, roomId));
  }

  @POST
  @Path("/uploadResult")
  @Operation(summary = "上传结果（通用）")
  public Response<SimulationDisturdDetailVO> uploadResult(HttpServerRequest request, @RequestBody SimulationDisturdUploadResultVO detailVO) {
    return ResponseResult.success(roomContentService.uploadResult(request, detailVO));
  }

  @POST
  @Path("/saveSetting")
  @Operation(summary = "保存设置")
  public Response<SimulationDisturdSettingVO> saveSetting(@RequestBody SimulationDisturdSettingVO vo) {
    return ResponseResult.success(roomContentService.saveSetting(vo));
  }

}
