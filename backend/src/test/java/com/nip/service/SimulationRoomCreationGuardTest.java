package com.nip.service;

import com.nip.dao.UserDao;
import com.nip.dao.simulation.SimulationRouterRoomDao;
import com.nip.dto.vo.param.simulation.report.SimulationRoomReportAddParam;
import com.nip.service.simulation.SimulationReportRoomService;
import com.nip.testsupport.Fixtures;

import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Task 6.4 建房边界：四类推演房的 `cableFloor.subList(0, bwCount / 100)` 前置校验。
 * 修复前 totalPage 超过可用楼层数直接 IndexOutOfBounds（HTTP 500），
 * bwCount &lt; 100 则静默建出一个零报底的空房间。
 * 本类覆盖 REPORT 房；ROUTER/DISTURB/RECEPT 三处为同构代码。
 */
@QuarkusTest

class SimulationRoomCreationGuardTest {

  @Inject UserDao userDao;
  @Inject SimulationReportRoomService reportRoomService;
  @Inject SimulationRouterRoomDao roomDao;

  private SimulationRoomReportAddParam cableParam(Integer bwCount) {
    SimulationRoomReportAddParam param = new SimulationRoomReportAddParam();
    param.setRoomName("guard-" + UUID.randomUUID());
    param.setIsCable(1);
    param.setCableId(UUID.randomUUID().toString()); // 无楼层数据的电缆：可用楼层数为 0
    param.setStartPage(1);
    param.setBdType(1);
    param.setBwType(1);
    param.setBwCount(bwCount);
    param.setMainSignal("100");
    param.setIsRandom(0);
    param.setContent("");
    param.setSendUserList(List.of());
    param.setReceiveUserList(List.of());
    return param;
  }

  @Test
  void addRoomRejectsMorePagesThanAvailableCableFloors() {
    String token = UUID.randomUUID().toString();
    Fixtures.user(userDao, token);
    long before = roomDao.count();

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> reportRoomService.addRoom(request(token), cableParam(500)),
        "报文组数折算的页数超过可用楼层数必须是明确的业务错误而非 500");
    assertEquals("所选电缆可用楼层不足", ex.getMessage());
    assertEquals(before, roomDao.count(), "被拒的建房不得留下房间行");
  }

  @Test
  void addRoomRejectsMessageCountBelowOnePage() {
    String token = UUID.randomUUID().toString();
    Fixtures.user(userDao, token);
    long before = roomDao.count();

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> reportRoomService.addRoom(request(token), cableParam(50)),
        "不足一页的报文组数必须拒绝建房，而不是建出零报底空房");
    assertEquals("报文组数不足一页，无法建立房间", ex.getMessage());
    assertEquals(before, roomDao.count(), "被拒的建房不得留下房间行");
  }

  /** service 只读 request 的 token 头，用 Proxy 免去起 HTTP 层。 */
  private static HttpServerRequest request(String token) {
    return (HttpServerRequest) Proxy.newProxyInstance(
        HttpServerRequest.class.getClassLoader(),
        new Class<?>[]{HttpServerRequest.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getHeader" -> token;
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          case "toString" -> "HttpServerRequest(token=" + token + ")";
          default -> null;
        });
  }
}
