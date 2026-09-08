package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.PostRadiotelephoneTermDataDto;
import com.nip.dto.vo.PostRadiotelephoneTermDataVO;
import com.nip.service.RadiotelephoneTermDataService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2022-06-24 11:48
 * @Description:
 */
@JWT
@Path("/postRadiotelephoneTermData")
@ApplicationScoped
@Tag(name = "岗位训练-报话训练-通报用语配置")
public class RadiotelephoneTermDataController {

  private final RadiotelephoneTermDataService dataService;

  @Inject
  public RadiotelephoneTermDataController(RadiotelephoneTermDataService dataService) {
    this.dataService = dataService;
  }

  @POST
  @Path("/findByType")
  @Operation(summary = "根据类型查询")
  public Response<List<PostRadiotelephoneTermDataVO>> findByType(PostRadiotelephoneTermDataDto dto) {
    return ResponseResult.success(dataService.findByType(dto.getType()));
  }

  @POST
  @Path("/save")
  @Operation(summary = "添加数据")
  public Response<PostRadiotelephoneTermDataVO> save(PostRadiotelephoneTermDataVO vo) {
    return ResponseResult.success(dataService.add(vo));
  }

  @POST
  @Path("/delete")
  @Operation(summary = "删除数据(只传入ID)")
  public Response<Void> delete(PostRadiotelephoneTermDataVO vo) {
    dataService.delete(vo);
    return ResponseResult.success();
  }

  @POST
  @Path("/update")
  @Operation(summary = "修改记录")
  public Response<PostRadiotelephoneTermDataVO> update(PostRadiotelephoneTermDataVO vo) {
    return ResponseResult.success(dataService.update(vo));
  }

}
