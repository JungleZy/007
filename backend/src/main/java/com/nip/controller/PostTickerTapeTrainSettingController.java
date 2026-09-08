package com.nip.controller;


import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.vo.PostTickerTapeTrainSettingVO;
import com.nip.dto.vo.param.PostTickerTapeTrainSettingAddParam;
import com.nip.service.PostTickerTapeTrainSettingService;
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
@ApplicationScoped
@Path("/postTickerTapeTrainSetting")
@Tag(name = "岗位训练-收报管理-速率配置")
public class PostTickerTapeTrainSettingController {

  private final PostTickerTapeTrainSettingService settingService;

  @Inject
  public PostTickerTapeTrainSettingController(PostTickerTapeTrainSettingService settingService) {
    this.settingService = settingService;
  }

  @POST
  @Path("/findAll")
  @Operation(summary = "查询所有的速率配置")
  public Response<List<PostTickerTapeTrainSettingVO>> findAll() {
    return ResponseResult.success(settingService.findAll());
  }

  @POST
  @Path("/addOrUpdate")
  @Operation(summary = "添加或修改速率配置")
  public Response<PostTickerTapeTrainSettingAddParam> addOrUpdate(PostTickerTapeTrainSettingAddParam param) {
    return ResponseResult.success(settingService.addOrUpdate(param));
  }

  @POST
  @Path("/getDotRate")
  @Operation(summary = "低速报点标准速率")
  public Response<Integer> getDotRate() {
    return ResponseResult.success(settingService.getDotStandard());
  }
}
