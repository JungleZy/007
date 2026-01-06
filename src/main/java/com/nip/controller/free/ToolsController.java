package com.nip.controller.free;

import cn.hutool.core.date.DateTime;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.config.ConfigProvider;

import java.net.InetAddress;
import java.net.UnknownHostException;
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
  @Path("/system")
  @Operation(summary = "获取系统与版本信息")
  public Response<Map<String, Object>> getSystemAndVersionInfo() {
    Map<String, Object> info = new HashMap<>(16);
    String osName = System.getProperty("os.name");
    String osArch = System.getProperty("os.arch");
    String osVersion = System.getProperty("os.version");
    String javaVersion = System.getProperty("java.version");
    String javaVendor = System.getProperty("java.vendor");
    int processors = Runtime.getRuntime().availableProcessors();
    long totalMemory = Runtime.getRuntime().totalMemory();
    long freeMemory = Runtime.getRuntime().freeMemory();
    long totalMemoryMB = totalMemory / (1024 * 1024);
    long freeMemoryMB = freeMemory / (1024 * 1024);
    String hostname = "unknown";
    String ip = "unknown";
    try {
      InetAddress localHost = InetAddress.getLocalHost();
      hostname = localHost.getHostName();
      ip = localHost.getHostAddress();
    } catch (UnknownHostException ignored) {
    }
    String version = ConfigProvider.getConfig().getOptionalValue("version", String.class).orElse(null);
    info.put("软件版本", version);
    info.put("操作系统", osName);
    info.put("系统架构", osArch);
    info.put("系统版本", osVersion);
    info.put("Java版本", javaVersion);
    info.put("Java厂商", javaVendor);
    info.put("CPU核心数", processors);
    info.put("总内存(MB)", totalMemoryMB);
    info.put("空闲内存(MB)", freeMemoryMB);
    info.put("主机名", hostname);
    info.put("IP地址", ip);
    return ResponseResult.success(info);
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
