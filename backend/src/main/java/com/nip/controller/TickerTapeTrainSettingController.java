package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.vo.TickerTapeTrainSettingVO;
import com.nip.dto.vo.param.TickerTapeTrainSettingAddParam;
import com.nip.service.TickerTapeTrainSettingService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2022-04-06 16:02
 * @Description:
 */
@JWT
@ApplicationScoped
@Path("/tickerTapeTrainSetting")
@Tag(name = "收报管理-速率配置")
public class TickerTapeTrainSettingController {
  private final TickerTapeTrainSettingService settingService;

  @Inject
  public TickerTapeTrainSettingController(TickerTapeTrainSettingService settingService) {
    this.settingService = settingService;
  }

  @Path("/findAll")
  @POST
  @Operation(summary = "查询所有的速率配置")
  public Response<List<TickerTapeTrainSettingVO>> findAll() {
    return ResponseResult.success(settingService.findAll());
  }

  @Path("/addOrUpdate")
  @POST
  @Operation(summary = "添加或修改速率配置")
  public Response<TickerTapeTrainSettingAddParam> addOrUpdate(TickerTapeTrainSettingAddParam param) {
    return ResponseResult.success(settingService.addOrUpdate(param));
  }

  @Path("/getDotRate")
  @POST
  @Operation(summary = "低速报点标准速率")
  public Response<Integer> getDotRage() {
    return ResponseResult.success(settingService.getDotStandardRate());
  }
}
