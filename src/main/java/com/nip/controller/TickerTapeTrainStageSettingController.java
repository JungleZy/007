package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.vo.TickerTapeTrainStageSettingVO;
import com.nip.service.TickerTapeTrainStageSettingService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * @Author: wushilin
 * @Data: 2023-02-21 17:15
 * @Description:
 */

@JWT
@Path("/tickerTapeTrainStageSetting")
@ApplicationScoped
@Tag(name = "岗前收报管理-科室/连贯 配置")
public class TickerTapeTrainStageSettingController {
  private final TickerTapeTrainStageSettingService stageSettingService;

  @Inject
  public TickerTapeTrainStageSettingController(TickerTapeTrainStageSettingService stageSettingService) {
    this.stageSettingService = stageSettingService;
  }

  @POST
  @Path(value = "add")
  @Operation(summary = "添加/修改配置")
  public Response<TickerTapeTrainStageSettingVO> add(TickerTapeTrainStageSettingVO vo) {
    return ResponseResult.success(stageSettingService.add(vo));
  }

  @POST
  @Path(value = "findAll")
  @Operation(summary = "查询配置")
  public Response<TickerTapeTrainStageSettingVO> findAll() {
    return ResponseResult.success(stageSettingService.findAll());
  }
}
