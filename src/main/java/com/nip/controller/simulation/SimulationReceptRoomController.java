package com.nip.controller.simulation;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.vo.param.simulation.report.SimulationRoomReportAddParam;
import com.nip.dto.vo.simulation.report.SimulationReportRoomVO;
import com.nip.entity.simulation.router.SimulationRouterRoomEntity;
import com.nip.service.simulation.SimulationReceptRoomService;
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
@Path("/simulation/recept")
@ApplicationScoped
@Tag(name = "组网运用-收报组训")
public class SimulationReceptRoomController {
  private final SimulationReceptRoomService roomService;

  @Inject
  public SimulationReceptRoomController(SimulationReceptRoomService roomService) {
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
