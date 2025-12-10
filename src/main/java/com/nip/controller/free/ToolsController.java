package com.nip.controller.free;

import cn.hutool.core.date.DateTime;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ToolsController
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2023-07-24 11:04
 */
@Path("/tools")
@ApplicationScoped
@Tag(name = "工具库接口")
public class ToolsController {
  @GET
  @Path("/getNowTime")
  @Operation(summary = "获取当前服务器时间")
  public Response<Long> getNowTime() {
    return ResponseResult.success(DateTime.now().getTime());
  }

  @GET
  @Path("/getTwelvemonth")
  @Operation(summary = "获取当前月的前12个月")
  public Response<List<Map<String, Object>>> getTwelvemonth() {
    try {
      YearMonth currentMonth = YearMonth.now();
      List<Map<String, Object>> list = new ArrayList<>(12);

      for (int i = 0; i < 12; i++) {
        Map<String, Object> map = new HashMap<>(2);
        map.put("year", currentMonth.getYear());
        map.put("month", currentMonth.getMonthValue());
        list.add(map);
        currentMonth = currentMonth.minusMonths(1);
      }

      return ResponseResult.success(list);
    } catch (Exception e) {
      // 根据实际需求处理异常，这里简单返回错误响应
      return ResponseResult.error("Error occurred while generating the list of months: " + e.getMessage());
    }
  }
}
