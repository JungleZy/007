package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.entity.MastheadEntity;
import com.nip.service.MastheadService;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestQuery;

import static com.nip.common.constants.BaseConstants.TOKEN;
import static com.nip.common.constants.BaseConstants.TRAIN_ID;
import static com.nip.common.constants.BaseConstants.ROOM_ID;

@JWT
@Path("/masthead")
@ApplicationScoped
@Tag(name = "报头管理")
public class MastheadController {
  private final MastheadService mastheadService;

  @Inject
  public MastheadController(MastheadService mastheadService) {
    this.mastheadService = mastheadService;
  }

  @POST
  @Path("/save")
  @Operation(summary = "保存报头")
  public Response<MastheadEntity> save(@RestHeader(TOKEN) String token, MastheadEntity entity) {
    return ResponseResult.success(mastheadService.save(token, entity));
  }

  @GET
  @Path("/findByTrainId")
  @Operation(summary = "查询报头")
  public Response<MastheadEntity> findByTrainId(@RestHeader(TOKEN) String token, @RestQuery(TRAIN_ID) String trainId) {
    return ResponseResult.success(mastheadService.findByTrainId(token, trainId));
  }

  public record RoomHeader(String content) {}

  @POST
  @Path("/saveRoom")
  @Operation(summary = "保存仿真房间报头")
  public Response<MastheadEntity> saveRoom(HttpServerRequest request, @RestQuery(ROOM_ID) Integer roomId,
      RoomHeader header) {
    if (header == null) throw new IllegalArgumentException("报头不能为空");
    return ResponseResult.success(mastheadService.saveRoom(request, roomId, header.content()));
  }

  @GET
  @Path("/findByRoomId")
  @Operation(summary = "查询仿真房间报头")
  public Response<MastheadEntity> findByRoomId(HttpServerRequest request, @RestQuery(ROOM_ID) Integer roomId) {
    return ResponseResult.success(mastheadService.findByRoomId(request, roomId));
  }
}
