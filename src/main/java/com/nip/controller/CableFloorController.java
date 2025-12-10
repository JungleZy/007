package com.nip.controller;

import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.vo.CableFindByIdOrFloorNumberVO;
import com.nip.service.CableFloorService;
import com.nip.service.CableService;
import com.nip.service.CableTypeService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/cable/floor")
@Tag(name = "固定报文")
@ApplicationScoped
public class CableFloorController {
  private final CableService cableService;
  private final CableTypeService cableTypeService;
  private final CableFloorService cableFloorService;

  @Inject
  public CableFloorController(CableService cableService, CableTypeService cableTypeService, CableFloorService cableFloorService) {
    this.cableService = cableService;
    this.cableTypeService = cableTypeService;
    this.cableFloorService = cableFloorService;
  }

  @POST
  @Path("/find")
  @Operation(summary = "获取指定报底报文",description = "floorNumber不传为获取全部")
  public Response<List<List<List<String>>>> floorFind(CableFindByIdOrFloorNumberVO vo) {
    return ResponseResult.success(cableFloorService.findCableFloor(vo.getId(), vo.getFloorNumber(),null));
  }

}
