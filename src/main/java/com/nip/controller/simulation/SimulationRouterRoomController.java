package com.nip.controller.simulation;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.vo.param.simulation.router.SimulationRoomRouterAddParam;
import com.nip.dto.vo.param.simulation.router.SimulationRoomRouterChangeParam;
import com.nip.dto.vo.simulation.SimulationRouterRoomPageInfoVO;
import com.nip.dto.vo.simulation.router.SimulationRouterRoomUserVO;
import com.nip.dto.vo.simulation.router.SimulationRouterRoomVO;
import com.nip.service.simulation.SimulationRouterRoomService;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.server.PathParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestQuery;

import java.util.List;

import static com.nip.common.constants.BaseConstants.*;

@JWT
@Path("/simulation/router")
@ApplicationScoped
@Tag(name = "仿真训练-线路通报")
public class SimulationRouterRoomController {
  private final SimulationRouterRoomService roomService;

  @Inject
  public SimulationRouterRoomController(SimulationRouterRoomService roomService) {
    this.roomService = roomService;
  }

  @POST
  @Path("/addRoom")
  @Operation(summary = "添加房间")
  public Response<Void> addRoom(HttpServerRequest request, @RequestBody SimulationRoomRouterAddParam param) {
    roomService.addRoom(request, param);
    return ResponseResult.success();
  }

  @POST
  @Path("/findRoom")
  @Operation(summary = "查询房间")
  public Response<List<SimulationRouterRoomVO>> findRoom(HttpServerRequest request) {
    return ResponseResult.success(roomService.findRoom(request));
  }

  @GET
  @Path("/getRoomUserList/{roomId}")
  @Operation(summary = "查询房间人员配置")
  public Response<SimulationRouterRoomUserVO> getRoomUserList(@PathParam(ROOM_ID) Integer roomId) {
    return ResponseResult.success(roomService.getRoomUserList(roomId));
  }

  @POST
  @Path("/changeChannel")
  @Operation(summary = "更改频道号")
  public Response<SimulationRouterRoomUserVO> changeChannel(@RequestBody SimulationRoomRouterChangeParam param) {
    return ResponseResult.success(roomService.changeChannel(param));
  }

  @GET
  @Path("/getRoomDetail")
  @Operation(summary = "查询房间详情")
  public Response<SimulationRouterRoomVO> getRoomDetail(HttpServerRequest request, @RestQuery("roomgId") Integer roomId) {
    return ResponseResult.success(roomService.getRoomDetail(request, roomId));
  }

  @GET
  @Path("/getRoomChannels")
  @Operation(summary = "查询所有管道")
  public Response<List<Integer>> getRoomChannels(@RestQuery(ROOM_ID) Integer roomId) {
    return ResponseResult.success(roomService.getRoomChannels(roomId));
  }

  @POST
  @Path("/sendFinish")
  @Operation(summary = "发送放完成")
  public Response<SimulationRouterRoomVO> sendFinish(@RestQuery(ROOM_ID) Integer roomId, HttpServerRequest request) {
    return ResponseResult.success(roomService.sendFinish(roomId, request));
  }

  @GET
  @Path("/findPage")
  @Operation(summary = "查询页内容 (通用)")
  public Response<SimulationRouterRoomPageInfoVO> findPage(@RestQuery(ROOM_ID) Integer roomId,
                                                           @RestQuery(PAGE_NUMBER) Integer pageNumber,
                                                           @RestQuery(USER_ID) String userId) {
    return ResponseResult.success(roomService.findPage(userId, roomId, pageNumber));
  }
}
