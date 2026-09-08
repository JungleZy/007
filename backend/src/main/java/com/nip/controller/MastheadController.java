package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.entity.MastheadEntity;
import com.nip.service.MastheadService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestQuery;

import static com.nip.common.constants.BaseConstants.TRAIN_ID;

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
  public Response<MastheadEntity> save(MastheadEntity entity) {
    return ResponseResult.success(mastheadService.save(entity));
  }

  @GET
  @Path("/findByTrainId")
  @Operation(summary = "查询报头")
  public Response<MastheadEntity> findByTrainId(@RestQuery(TRAIN_ID) String trainId) {
    return ResponseResult.success(mastheadService.findByTrainId(trainId));
  }
}
