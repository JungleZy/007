package com.nip.controller.simulation;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.vo.param.simulation.report.SimulationRoomReportAddParam;
import com.nip.dto.vo.simulation.report.SimulationReportRoomVO;
import com.nip.entity.simulation.router.SimulationRouterRoomEntity;
import com.nip.service.simulation.SimulationReportRoomService;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestQuery;

import java.util.List;

@JWT
@Path("/simulation/report")
@ApplicationScoped
@Tag(name = "仿真训练-通播教学")
public class SimulationReportRoomController {
  private final SimulationReportRoomService roomService;

  @Inject
  public SimulationReportRoomController(SimulationReportRoomService roomService) {
    this.roomService = roomService;
  }

  @POST
  @Path("/addRoom")
  @Operation(summary = "添加房间")
  public Response<SimulationRouterRoomEntity> addRoom(HttpServerRequest request, @RequestBody SimulationRoomReportAddParam param) {
    SimulationRouterRoomEntity simulationRouterRoomEntity = roomService.addRoom(request, param);
    return ResponseResult.success(simulationRouterRoomEntity);
  }

  @POST
  @Path("/findRoom")
  @Operation(summary = "查询房间")
  public Response<List<SimulationReportRoomVO>> findRoom(HttpServerRequest request) {
    return ResponseResult.success(roomService.findRoom(request));
  }

  @GET
  @Path("/getRoomDetail")
  @Operation(summary = "查询房间详情")
  public Response<SimulationReportRoomVO> getRoomDetail(@RestQuery("roomgId") Integer roomId, HttpServerRequest request) {
    return ResponseResult.success(roomService.getRoomDetail(roomId, request));
  }
}
