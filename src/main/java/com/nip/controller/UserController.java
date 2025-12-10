package com.nip.controller;

import com.nip.common.constants.MessageConstants;
import com.nip.common.constants.ResponseCode;
import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.common.utils.ToolUtil;
import com.nip.dto.UserInfoDto;
import com.nip.dto.sql.FindUserByRoleIdDto;
import com.nip.dto.sql.FindUserByStatusDescDto;
import com.nip.entity.UserEntity;
import com.nip.service.UserService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestQuery;

import java.util.List;
import java.util.Map;

import static com.nip.common.constants.BaseConstants.TOKEN;
import static com.nip.common.constants.BaseConstants.USER_ID;

@JWT
@Path("/user")
@ApplicationScoped
@Tag(name = "用户管理接口")
public class UserController {
  private final UserService userService;

  @Inject
  public UserController(UserService userService) {
    this.userService = userService;
  }

  @POST
  @Path("/saveUser")
  @Operation(summary = "更新用户")
  public Response<Object> saveUser(UserEntity entity) {
    return userService.addUser(entity, true);
  }

  @POST
  @Path("/userOut")
  @Operation(summary = "退出登录")
  public Response<Boolean> userOut(@RestHeader(TOKEN) String token) {
    return ResponseResult.success(userService.userOut(token));
  }

  @POST
  @Path("/changePassword")
  @Operation(summary = "修改密码")
  public Response<Boolean> changePassword(Map<String, String> data) {
    String id = data.get(USER_ID);
    String oldPassword = data.get("oldPassword");
    String newPassword = data.get("newPassword");
    String newPasswordV = data.get("newPasswordV");
    if (StringUtils.isEmpty(id) || StringUtils.isEmpty(oldPassword) || StringUtils.isEmpty(newPassword)
        || StringUtils.isEmpty(newPasswordV)) {
      return ResponseResult.error(ResponseCode.NULL_ERROR);
    }
    return userService.changePassword(id, oldPassword, newPassword, newPasswordV);
  }

  @POST
  @Path("/importUser")
  @Operation(summary = "导入用户")
  public Response<List<UserEntity>> importUser(List<UserEntity> entity) {
    return ResponseResult.success(userService.importUser(entity));
  }

  @POST
  @Path("/addUserRole")
  @Operation(summary = "新增用户角色")
  public Response<String> addUserRole(Map<String, Object> data) {
    return userService.addUserRole(data.get(USER_ID).toString(), ToolUtil.objToList(data.get("roleIds")))
        ? ResponseResult.success(MessageConstants.DATA_SUCCESS)
        : ResponseResult.error(MessageConstants.DATA_EXCEPTION);
  }

  @POST
  @Path("/getAllUser")
  @Operation(summary = "获取全部用户")
  public Response<List<UserEntity>> getAllUser() {
    return ResponseResult.success(userService.getAllUser());
  }

  @POST
  @Path("/getAllUserByContent")
  @Operation(summary = "获取全部用户")
  public Response getAllUserByContent(@RestQuery("userName") String userName, @RestQuery("userAccount") String userAccount) {
    return userService.getAllUserByContent(userName, userAccount);
  }

  @POST
  @Path("/getUsersByUserNameStartingWith")
  @Operation(summary = "根据 名字前几位 获取 所用满足的用户")
  public Response<List<UserEntity>> getUsersByUserNameStartingWith(Map<String, String> map) {
    return ResponseResult.success(userService.getUsersByUserNameStartingWith(map.get("userName")));
  }

  @POST
  @Path("/getUserInfoAllByStatusDesc")
  @Operation(summary = "根据 用户状态 排序")
  public Response<List<FindUserByStatusDescDto>> getUserInfoAllByStatusDesc() {
    return ResponseResult.success(userService.getUserInfoAllByStatusDesc());
  }

  @POST
  @Path("/getUserById")
  @Operation(summary = "根据 用户编号 获取 用户信息")
  public Response<UserEntity> getUserById(Map<String, String> map) {
    return ResponseResult.success(userService.getUserById(map.get(USER_ID)));
  }

  @POST
  @Path("/getUserAndRoleById")
  @Operation(summary = "根据 用户编号 获取 用户信息与角色信息")
  public Response<UserInfoDto> getUserAndRoleById(Map<String, String> map) {
    return ResponseResult.success(userService.getUserAndRoleById(map.get(USER_ID)));
  }

  @POST
  @Path("/getUsersByIds")
  @Operation(summary = "根据 用户编号 获取用户信息（批量）")
  public Response<List<UserEntity>> getUsersByIds(List<String> ids) {
    return ResponseResult.success(userService.getUsers(ids));
  }

  @POST
  @Path("/getUsersByToken")
  @Operation(summary = "根据 token 获取 用户信息")
  public Response<UserEntity> getUsersByToken(@RestHeader(TOKEN) String token) {
    return ResponseResult.success(userService.getUserByToken(token));
  }

  @POST
  @Path("/verifyPassword")
  @Operation(summary = "校验用户密码")
  public Response<Object> verifyPassword(String password) {
    return userService.verifyPassword(password);
  }

  @POST
  @Path("/findAllTeacher")
  @Operation(summary = "查询所有教员")
  public Response<List<FindUserByRoleIdDto>> findAllTeacher() {
    return ResponseResult.success(userService.findAllTeacher());
  }

  @POST
  @Path("/findAllStu")
  @Operation(summary = "查询所有学员")
  public Response<List<FindUserByRoleIdDto>> findAllStu() {
    return ResponseResult.success(userService.findAllStu());
  }

  @GET
  @Path(value = "delete")
  @Operation(summary = "删除用户")
  public Response<Boolean> delete(@RestQuery(USER_ID) String userId) {
    return ResponseResult.success(userService.delete(userId));
  }
  @GET
  @Path(value = "resetPassword")
  @Operation(summary = "重置用户密码")
  public Response<String> resetPassword(@RestQuery(USER_ID) String userId) {
    return ResponseResult.success(userService.resetPassword(userId));
  }
}
