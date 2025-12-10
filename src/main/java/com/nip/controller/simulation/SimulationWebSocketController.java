package com.nip.controller.simulation;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.ws.WebSocketSimulationService;
import com.nip.ws.model.SimulationUserModel;
import com.nip.ws.service.simulation.SimulationGlobal;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JWT
@Path("/simulation/socket")
@ApplicationScoped
@Tag(name = "仿真训练房间个人员显示（调试使用）")
public class SimulationWebSocketController {
  @GET
  @Path("/getAllRoomInfo")
  @Operation(summary = "获取所有房间在线人员信息")
  public Response<Map<String, List<Map<Integer, List<SimulationUserModel>>>>> getAllRoomInfo() {
    Map<Integer, List<WebSocketSimulationService>> routerRoom = SimulationGlobal.routerRoom;
    Map<Integer, List<WebSocketSimulationService>> disturbRoom = SimulationGlobal.disturbRoom;
    Map<Integer, List<WebSocketSimulationService>> reportRoom = SimulationGlobal.reportRoom;
    Map<String, List<Map<Integer, List<SimulationUserModel>>>> ret = new HashMap<>();

    List<Map<Integer, List<SimulationUserModel>>> rRooms = new ArrayList<>();
    routerRoom.forEach((key, value) -> {
      List<SimulationUserModel> collect = value.stream().map(WebSocketSimulationService::getUserModel)
          .toList();
      Map<Integer, List<SimulationUserModel>> room = new HashMap<>();
      room.put(key, collect);
      rRooms.add(room);
    });
    List<Map<Integer, List<SimulationUserModel>>> dRooms = new ArrayList<>();
    disturbRoom.forEach((key, value) -> {
      List<SimulationUserModel> collect = value.stream().map(WebSocketSimulationService::getUserModel)
          .toList();
      Map<Integer, List<SimulationUserModel>> room = new HashMap<>();
      room.put(key, collect);
      dRooms.add(room);
    });
    List<Map<Integer, List<SimulationUserModel>>> rpRooms = new ArrayList<>();
    reportRoom.forEach((key, value) -> {
      List<SimulationUserModel> collect = value.stream().map(WebSocketSimulationService::getUserModel)
          .toList();
      Map<Integer, List<SimulationUserModel>> room = new HashMap<>();
      room.put(key, collect);
      rpRooms.add(room);
    });

    ret.put("线路通报", rRooms);
    ret.put("快速/干扰通报", dRooms);
    ret.put("通报教学", rpRooms);
    return ResponseResult.success(ret);
  }

}
