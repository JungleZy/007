package com.nip.controller;

import com.nip.common.interceptor.JWT;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.dto.vo.HandKeyRecentTrainVO;
import com.nip.dto.vo.UserTrainDurationStatVO;
import com.nip.service.UserService;
import com.nip.service.UserTrainStatisticsService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestHeader;

import java.util.List;
import java.util.Map;

import static com.nip.common.constants.BaseConstants.TOKEN;

/**
 * 个人训练统计端点。
 *
 * <p>这三个端点原本住在 {@code com.nip.controller.free} 包里（匿名可达）且按请求体的 {@code userId}
 * 取数：任何人不带凭据就能枚举任意用户的训练时长与成绩曲线。现在移出 free 包、带类级
 * {@link JWT}，统计对象只由 token 推导，请求体不再有 {@code userId}。
 *
 * <p>路径保持 {@code /api/user/...} 不变（与 {@code UserController}、
 * {@code com.nip.controller.free.UserController} 共享 {@code /user} 前缀，子路径互不重叠），
 * 换前缀只会让前端多改一轮而不带来任何安全收益。
 */
@JWT
@Path("/user")
@ApplicationScoped
@Tag(name = "个人训练统计接口")
public class UserTrainStatisticsController {
  private final UserService userService;
  private final UserTrainStatisticsService userTrainStatisticsService;

  @Inject
  public UserTrainStatisticsController(UserService userService,
      UserTrainStatisticsService userTrainStatisticsService) {
    this.userService = userService;
    this.userTrainStatisticsService = userTrainStatisticsService;
  }

  @POST
  @Path("/getUserTrainDurationStat")
  @Operation(summary = "统计当前用户训练时长")
  public Response<UserTrainDurationStatVO> getUserTrainDurationStat(@RestHeader(TOKEN) String token,
      Map<String, String> map) {
    String userId = userService.getUserByToken(token).getId();
    // 时间区间仍是可选入参；请求体缺失时按「全部区间」统计
    String startTime = map == null ? null : map.get("startTime");
    String endTime = map == null ? null : map.get("endTime");
    return ResponseResult.success(
        userTrainStatisticsService.getUserTrainDurationStat(userId, startTime, endTime));
  }

  @POST
  @Path("/getRecentHandKeyTrains")
  @Operation(summary = "最近十次手键拍发数据（训练时间、得分、速率）")
  public Response<List<HandKeyRecentTrainVO>> getRecentHandKeyTrains(@RestHeader(TOKEN) String token) {
    String userId = userService.getUserByToken(token).getId();
    return ResponseResult.success(userTrainStatisticsService.getRecentHandKeyTrains(userId));
  }

  @POST
  @Path("/getRecentElectronicKeyTrains")
  @Operation(summary = "最近十次电子键拍发数据（训练开始时间、训练时间、得分、速率）")
  public Response<List<HandKeyRecentTrainVO>> getRecentElectronicKeyTrains(@RestHeader(TOKEN) String token) {
    String userId = userService.getUserByToken(token).getId();
    return ResponseResult.success(userTrainStatisticsService.getRecentElectronicKeyTrains(userId));
  }
}
