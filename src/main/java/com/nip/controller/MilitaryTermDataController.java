package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.MilitaryTermDataDto;
import com.nip.dto.MilitaryTermDataMoveDto;
import com.nip.dto.MilitaryTermDto;
import com.nip.dto.vo.MilitaryTermDataVO;
import com.nip.service.MilitaryTermDataService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/**
 * MilitaryTermDataController
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2022-06-23 15:19
 */
@JWT
@Path("/mtd")
@ApplicationScoped
@Tag(name = "军语密语管理")
public class MilitaryTermDataController {
  private final MilitaryTermDataService militaryTermDataService;

  @Inject
  public MilitaryTermDataController(MilitaryTermDataService militaryTermDataService) {
    this.militaryTermDataService = militaryTermDataService;
  }

  @POST
  @Path("/saveAll")
  @Operation(summary = "添加数据-后端开发用")
  public Response<Void> saveAll(String string) {
    militaryTermDataService.saveAll(string);
    return ResponseResult.success();
  }

  @POST
  @Path("/save")
  @Operation(summary = "新增")
  public Response<MilitaryTermDataVO> save(MilitaryTermDataDto dto) {
    return ResponseResult.success(militaryTermDataService.save(dto));
  }

  @POST
  @Path("/delete")
  @Operation(summary = "删除(只传入ID)")
  public Response<Void> delete(MilitaryTermDataVO vo) {
    militaryTermDataService.delete(vo);
    return ResponseResult.success();
  }

  @POST
  @Path("findAll")
  @Operation(summary = "查询所有军语密语")
  public Response<List<MilitaryTermDataVO>> findAll() {
    return ResponseResult.success(militaryTermDataService.findAll());
  }

  @POST
  @Path("/update")
  @Operation(summary = "修改")
  public Response<MilitaryTermDataVO> update(MilitaryTermDataVO vo) {
    return ResponseResult.success(militaryTermDataService.update(vo));
  }

  @POST
  @Path("/move")
  @Operation(summary = "移动")
  public Response<Void> move(MilitaryTermDataMoveDto dataMoveDto) {
    militaryTermDataService.move(dataMoveDto);
    return ResponseResult.success();
  }

  @POST
  @Path("/saveBatch")
  @Operation(summary = "批量保存军语密语-代替之前文件导入")
  public Response<List<MilitaryTermDataVO>> saveBatch(List<MilitaryTermDto> params) {
    return ResponseResult.success(militaryTermDataService.saveBatch(params));
  }
}
