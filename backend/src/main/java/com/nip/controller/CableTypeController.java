package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.interceptor.RequireAdmin;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.entity.CableTypeEntity;
import com.nip.service.CableTypeService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestQuery;

import java.util.List;

@JWT
@Path("/cable/type")
@Tag(name = "固定报文")
@ApplicationScoped
public class CableTypeController {
  private final CableTypeService cableTypeService;

  @Inject
  public CableTypeController(CableTypeService cableTypeService) {
    this.cableTypeService = cableTypeService;
  }

  @POST
  @Path("/find")
  @Operation(summary = "查询类型")
  public Response<List<CableTypeEntity>> typeFind() {
    return ResponseResult.success(cableTypeService.findAll());
  }

  @POST
  @Path("/save")
  @Operation(summary = "新增/修改类型")
  @RequireAdmin
  public Response<List<CableTypeEntity>> typeSave(CableTypeEntity entity) {
    return ResponseResult.success(cableTypeService.save(entity));
  }

  @POST
  @Path("/delete")
  @Operation(summary = "删除类型")
  @RequireAdmin
  public Response<Boolean> typeDelete(@RestQuery("id") String id) {
    return ResponseResult.success(cableTypeService.delete(id));
  }

}
