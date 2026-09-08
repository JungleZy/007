package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.TelegraphKeyPatTrainDto;
import com.nip.dto.vo.TelegraphKeyPatTrainVO;
import com.nip.service.TelegraphKeyPatTrainService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestHeader;

import static com.nip.common.constants.BaseConstants.TOKEN;

/**
 * @Author: wushilin
 * @Data: 2022-06-09 09:23
 * @Description:
 */
@ApplicationScoped
@Path("/telegraphKeyPatTrain")
@JWT
@Tag(name = "岗前训练-电子键拍发-单字训练")
public class TelegraphKeyPatTrainController {
  private final TelegraphKeyPatTrainService patTrainService;

  @Inject
  public TelegraphKeyPatTrainController(TelegraphKeyPatTrainService patTrainService) {
    this.patTrainService = patTrainService;
  }

  @POST
  @Path("/save")
  @Operation(summary = "添加/修改")
  public Response<Void> save(@RestHeader(TOKEN) String token, TelegraphKeyPatTrainDto dto) {
    patTrainService.save(token, dto);
    return ResponseResult.success();
  }

  @POST
  @Path("/findByType")
  @Operation(summary = "根据类型查询")
  public Response<TelegraphKeyPatTrainVO> findByType(@RestHeader(TOKEN) String token, TelegraphKeyPatTrainDto dto) {
    return ResponseResult.success(patTrainService.findByUserIdAndType(token, dto.getType()));
  }

  @POST
  @Path("/clear")
  @Operation(summary = "清空,只传入type")
  public Response<TelegraphKeyPatTrainVO> clear(@RestHeader(TOKEN) String token, TelegraphKeyPatTrainDto dto) throws Exception {
    return ResponseResult.success(patTrainService.clear(token, dto.getType()));
  }
}
