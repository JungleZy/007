package com.nip.controller;

import com.nip.common.constants.MessageConstants;
import com.nip.common.constants.ResponseCode;
import com.nip.common.interceptor.JWT;
import com.nip.common.interceptor.RequireAdmin;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.common.utils.ToolUtil;
import com.nip.dto.UserProfile;
import com.nip.dto.UserInfoDto;
import com.nip.dto.UserSummary;
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
  @RequireAdmin
  public Response<Object> saveUser(UserEntity entity) {
    Response<Object> response = userService.addUser(entity, true);
    if (response.getCode() == ResponseCode.SUCCESS.getCode() && response.getData() instanceof UserEntity user) {
      response.setData(UserProfile.from(user));
    }
    return response;
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
  public Response<Boolean> changePassword(@RestHeader(TOKEN) String token, Map<String, String> data) {
    String oldPassword = data.get("oldPassword");
    String newPassword = data.get("newPassword");
    String newPasswordV = data.get("newPasswordV");
    if (StringUtils.isEmpty(oldPassword) || StringUtils.isEmpty(newPassword)
        || StringUtils.isEmpty(newPasswordV)) {
      return ResponseResult.error(ResponseCode.PARAMS_ERROR);
    }
    return userService.changePassword(token, oldPassword, newPassword, newPasswordV);
  }

  @POST
  @Path("/importUser")
  @Operation(summary = "导入用户")
  @RequireAdmin
  public Response<List<UserProfile>> importUser(List<UserEntity> entity) {
    return ResponseResult.success(userService.importUser(entity).stream().map(UserProfile::from).toList());
  }

  @POST
  @Path("/addUserRole")
  @Operation(summary = "新增用户角色")
  @RequireAdmin
  public Response<String> addUserRole(Map<String, Object> data) {
    return userService.addUserRole(data.get(USER_ID).toString(), ToolUtil.objToList(data.get("roleIds")))
        ? ResponseResult.success(MessageConstants.DATA_SUCCESS)
        : ResponseResult.error(MessageConstants.DATA_EXCEPTION);
  }

  @POST
  @Path("/getAllUser")
  @Operation(summary = "获取全部用户")
  @RequireAdmin
  public Response<List<UserProfile>> getAllUser() {
    return ResponseResult.success(userService.getAllUser());
  }

  @POST
  @Path("/getUsersByUserNameStartingWith")
  @Operation(summary = "根据 名字前几位 获取 所用满足的用户")
  public Response<List<UserSummary>> getUsersByUserNameStartingWith(Map<String, String> map) {
    return ResponseResult.success(userService.getUsersByUserNameStartingWith(map.get("userName")));
  }

  @POST
  @Path("/getUserDirectory")
  @Operation(summary = "获取用户选择目录")
  public Response<List<UserSummary>> getUserDirectory() {
    return ResponseResult.success(userService.getUserDirectory());
  }

  // 该端点按状态倒序返回全量用户（含 idCard/phone/角色名），是 getAllUser 的管理台变体，
  // 故与 getAllUser 同样限定为管理员。
  @POST
  @Path("/getUserInfoAllByStatusDesc")
  @Operation(summary = "根据 用户状态 排序")
  @RequireAdmin
  public Response<List<FindUserByStatusDescDto>> getUserInfoAllByStatusDesc() {
    return ResponseResult.success(userService.getUserInfoAllByStatusDesc());
  }

  // getUserById / getUserAndRoleById 返回含 idCard/phone/email 的完整档案，
  // 唯一消费方是系统管理页（systemManage/structure），故与 getAllUser 同级收敛为管理员可达。
  @POST
  @Path("/getUserById")
  @Operation(summary = "根据 用户编号 获取 用户信息")
  @RequireAdmin
  public Response<UserProfile> getUserById(Map<String, String> map) {
    return ResponseResult.success(userService.getUserById(map.get(USER_ID)));
  }

  @POST
  @Path("/getUserAndRoleById")
  @Operation(summary = "根据 用户编号 获取 用户信息与角色信息")
  @RequireAdmin
  public Response<UserInfoDto> getUserAndRoleById(Map<String, String> map) {
    return ResponseResult.success(userService.getUserAndRoleById(map.get(USER_ID)));
  }

  @POST
  @Path("/getUsersByIds")
  @Operation(summary = "根据 用户编号 批量获取 用户目录条目")
  public Response<List<UserSummary>> getUsersByIds(List<String> ids) {
    return ResponseResult.success(userService.getUsers(ids));
  }

  @POST
  @Path("/getUsersByToken")
  @Operation(summary = "根据 token 获取 用户信息")
  public Response<UserProfile> getUsersByToken(@RestHeader(TOKEN) String token) {
    return ResponseResult.success(UserProfile.from(userService.getUserByToken(token)));
  }

  @POST
  @Path("/verifyPassword")
  @Operation(summary = "校验用户密码")
  public Response<Boolean> verifyPassword(@RestHeader(TOKEN) String token, String password) {
    return userService.verifyPassword(token, password);
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
  @RequireAdmin
  public Response<Boolean> delete(@RestQuery(USER_ID) String userId) {
    return ResponseResult.success(userService.delete(userId));
  }

  @GET
  @Path(value = "resetPassword")
  @Operation(summary = "重置用户密码")
  @RequireAdmin
  public Response<String> resetPassword(@RestQuery(USER_ID) String userId) {
    return ResponseResult.success(userService.resetPassword(userId));
  }

}
