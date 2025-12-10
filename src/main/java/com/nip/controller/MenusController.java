package com.nip.controller;

import com.nip.common.constants.ResponseCode;
import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.MenusButtonDto;
import com.nip.dto.MenusDto;
import com.nip.service.MenusService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestQuery;

import java.util.List;

import static com.nip.common.constants.BaseConstants.ID;

/**
 * MenusController
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2023-07-24 15:57
 */
@JWT
@Path("/menus")
@ApplicationScoped
@Tag(name = "权限管理")
public class MenusController {
  private final MenusService menusService;

  @Inject
  public MenusController(MenusService menusService) {
    this.menusService = menusService;
  }

  @POST
  @Path("/addMenu")
  @Operation(summary = "添加或修改 权限")
  public Response<MenusButtonDto> addMenu(MenusButtonDto entity) {
    MenusButtonDto menusButtonDto = menusService.addMenus(entity);
    return menusButtonDto == null
        ? ResponseResult.error(ResponseCode.SYSTEM_ERROR)
        : ResponseResult.success(menusButtonDto);
  }

  @GET
  @Path("/getMenusAll")
  @Operation(summary = "获取 所有权限")
  public Response<List<MenusDto>> getMenusAll() {
    return ResponseResult.success(menusService.getMenusAll());
  }

  @GET
  @Path("/getMenuById")
  @Operation(summary = "根据 id 获取 该权限")
  public Response<MenusButtonDto> getMenuById(@RestQuery(ID) String id) {
    return ResponseResult.success(menusService.getMenuById(id));
  }
}
