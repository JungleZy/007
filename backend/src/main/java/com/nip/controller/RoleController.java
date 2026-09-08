package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.RoleInfoDto;
import com.nip.dto.RoleMenusDto;
import com.nip.dto.SaveRoleDto;
import com.nip.entity.RoleEntity;
import com.nip.service.RoleService;
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
 * RoleController
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2023-07-24 11:18
 */
@JWT
@Path("/role")
@ApplicationScoped
@Tag(name = "角色管理接口")
public class RoleController {
  private final RoleService roleService;

  @Inject
  public RoleController(RoleService roleService) {
    this.roleService = roleService;
  }

  @POST
  @Path("/addRole")
  @Operation(summary = "新增角色")
  public Response<RoleEntity> addRole(SaveRoleDto entity) {
    return ResponseResult.success(roleService.addRole(entity));
  }

  @POST
  @Path("/getRoleAll")
  @Operation(summary = "获取所有角色信息")
  public Response<List<RoleInfoDto>> getRoleAll() {
    return ResponseResult.success(roleService.getRoleAll());
  }

  @GET
  @Path("/getRoleById")
  @Operation(summary = "根据角色ID获取该角色所拥有的权限")
  public Response<RoleInfoDto> getRoleById(@RestQuery(ID) String id) {
    return ResponseResult.success(roleService.getRoleById(id));
  }

  @GET
  @Path("/getRoleMenusInfo")
  @Operation(summary = "根据角色ID获取该角色所拥有的权限-已处理为前端菜单结构")
  public Response<RoleMenusDto> getRoleMenusInfo(@RestQuery(ID) String id) {
    return ResponseResult.success(roleService.getRoleMenusInfo(id));
  }
}
