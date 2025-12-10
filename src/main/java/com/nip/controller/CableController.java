package com.nip.controller;

import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.vo.CableFindAllVO;
import com.nip.dto.vo.CableFindByIdVO;
import com.nip.dto.vo.CableVO;
import com.nip.entity.CableEntity;
import com.nip.service.CableFloorService;
import com.nip.service.CableService;
import com.nip.service.CableTypeService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameters;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestQuery;

import java.util.List;

@Path("/cable")
@Tag(name = "固定报文")
@ApplicationScoped
public class CableController {
  private final CableService cableService;
  private final CableTypeService cableTypeService;
  private final CableFloorService cableFloorService;

  @Inject
  public CableController(CableService cableService, CableTypeService cableTypeService, CableFloorService cableFloorService) {
    this.cableService = cableService;
    this.cableTypeService = cableTypeService;
    this.cableFloorService = cableFloorService;
  }

  @POST
  @Path("/find")
  @Operation(summary = "查询报文",description = "报文类型，报文范围")
  @Parameters(value = {
      @Parameter(name = "typeId", description = "报文类型编号"),
      @Parameter(name = "scope", description = "报文类型，0:收报，1:发报，2:收发报")
  })
  @Parameter(name = "page", description = "页码")
  public Response<List<CableEntity>> find(CableFindAllVO vo) {
    return ResponseResult.success(cableService.findAll(vo.getTypeId(), vo.getScope()));
  }

  @POST
  @Path("/findById")
  @Operation(summary = "根据编号查询报文")
  public Response<CableEntity> find(CableFindByIdVO vo) {
    return ResponseResult.success(cableService.findById(vo.getId()));
  }

  @POST
  @Path("/save")
  @Operation(summary = "新增/修改报文")
  public Response<CableEntity> save(CableVO vo) {
    return ResponseResult.success(cableService.save(vo));
  }

  @POST
  @Path("/delete")
  @Operation(summary = "删除报文")
  public Response<Boolean> delete(@RestQuery("id") String id) {
    return ResponseResult.success(cableService.delete(id));
  }
}
