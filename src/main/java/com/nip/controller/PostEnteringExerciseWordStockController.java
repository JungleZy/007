package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.PostEnteringExerciseWordStockDto;
import com.nip.dto.vo.param.PostEnteringExerciseWordStockQueryParam;
import com.nip.service.PostEnteringExerciseWordStockService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.List;

import static com.nip.common.constants.BaseConstants.TOKEN;

/**
 * @Author: wushilin
 * @Data: 2022-05-24 09:37
 * @Description:
 */
@JWT
@Path("/postEnteringExerciseWordStock")
@ApplicationScoped
@Tag(name = "岗位-汉字录入-文章管理")
public class PostEnteringExerciseWordStockController {

  private final PostEnteringExerciseWordStockService wordStockService;

  @Inject
  public PostEnteringExerciseWordStockController(PostEnteringExerciseWordStockService wordStockService) {
    this.wordStockService = wordStockService;
  }

  @POST
  @Path("/add")
  @Operation(summary = "添加文章")
  public Response<PostEnteringExerciseWordStockDto> add(PostEnteringExerciseWordStockDto vo,
                                                        @RestHeader(TOKEN) String token) throws Exception {
    return ResponseResult.success(wordStockService.add(vo, token));
  }

  @POST
  @Path("/view")
  @Operation(summary = "预览文章")
  public Response<PostEnteringExerciseWordStockDto> view(@RestForm("file") FileUpload file) {
    return ResponseResult.success(wordStockService.view(file));
  }

  @POST
  @Path("/listPage")
  @Operation(summary = "查询所有文章")
  public Response<List<PostEnteringExerciseWordStockDto>> listPage(@RestHeader(TOKEN) String token,
                                                                   PostEnteringExerciseWordStockQueryParam param)
      throws Exception {
    return ResponseResult.success(wordStockService.listPage(token, param.getType()));
  }

  @POST
  @Path("/delete")
  @Operation(summary = "删除文章(只传入id)")
  public Response<Void> delete(PostEnteringExerciseWordStockDto vo) {
    wordStockService.delete(vo.getId());
    return ResponseResult.success();
  }
}
