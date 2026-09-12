package com.nip.testsupport;

import com.nip.ws.WebSocketGeneralKeyPatService;
import com.nip.ws.WebSocketGeneralTelexPatService;
import com.nip.ws.WebSocketGeneralTickerPatService;
import com.nip.ws.WebSocketUnionService;
import com.nip.ws.service.simulation.SimulationGlobal;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * WebSocket 端点房表的统一清零通道。
 *
 * <p>被清的每一张表都是进程级 static（端点是 {@code @ApplicationScoped} 单例，连接态一律挂在
 * static 表上），因此它们在整个测试 JVM 内被所有用例共享：谁都能看见别人留下的条目。
 * 唯一让这种共享可用的前提是测试套件当前串行执行（{@code backend/pom.xml} 的 surefire
 * 未配置 {@code parallel}/{@code threadCount} 等并行参数）。禁止为本套件引入并行：一旦两个
 * 用例同时跑，本类的 clear 会把对方正在断言的房间抹掉，失败将不可复现。AGENTS.md 中
 * 「测试可并行」的旧约定已按此事实更正。
 *
 * <p>集中在这里，是为了让「哪些表是全局的」只有一处定义：新增一张 static 房表时，
 * 只需要改 {@link #clearAll()}，而不是去四个 WS 测试类里各补一行 clear。
 */
public final class WebSocketStateReset {

  private WebSocketStateReset() {
  }

  /**
   * 清零全部 WebSocket 进程级 static 表：三个 General 端点的房表、三张仿真房表、
   * Union 端点的三张私有表。用在用例之间的复位点（{@code @BeforeEach}/{@code @AfterEach}
   * 或用例自己的起点清零），不要在用例中段调用——它会抹掉本用例刚播下的夹具。
   */
  public static void clearAll() {
    WebSocketGeneralKeyPatService.ROOM.clear();
    WebSocketGeneralTelexPatService.ROOM.clear();
    WebSocketGeneralTickerPatService.PAT_ROOM.clear();
    SimulationGlobal.routerRoom.clear();
    SimulationGlobal.disturbRoom.clear();
    SimulationGlobal.reportRoom.clear();
    unionTable("webSocketClientSet").clear();
    unionTable("onlineUsers").clear();
    unionTable("onlineRooms").clear();
  }

  /**
   * 反射读取 {@link WebSocketUnionService} 的某张私有 static 表，供用例播种夹具与断言残留。
   *
   * @param fieldName 字段名：{@code webSocketClientSet} / {@code onlineUsers} / {@code onlineRooms}
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> unionTable(String fieldName) {
    try {
      Field field = WebSocketUnionService.class.getDeclaredField(fieldName);
      field.setAccessible(true);
      return (Map<String, Object>) field.get(null);
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("WebSocketUnionService." + fieldName + " 已改名或改签名，测试通道失效", failure);
    }
  }
}
