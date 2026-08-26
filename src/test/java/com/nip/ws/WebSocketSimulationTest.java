package com.nip.ws;

import com.nip.dao.UserDao;
import com.nip.dao.simulation.SimulationRouterRoomDao;
import com.nip.dao.simulation.SimulationRouterRoomUserDao;
import com.nip.entity.simulation.router.SimulationRouterRoomEntity;
import com.nip.entity.simulation.router.SimulationRouterRoomUserEntity;
import com.nip.testsupport.Fixtures;
import com.nip.testsupport.MySqlResource;
import com.nip.ws.service.simulation.SimulationGlobal;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.nip.common.constants.SimulationRoomTypeEnum.REPORT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(MySqlResource.class)
class WebSocketSimulationTest {

  @Inject
  UserDao userDao;
  @Inject
  SimulationRouterRoomDao roomDao;
  @Inject
  SimulationRouterRoomUserDao roomUserDao;

  @ClientEndpoint
  public static class Probe {
    final LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();

    @OnMessage
    public void on(String m) {
      received.add(m);
    }
  }

  // P0#9：学员断线不得按“最后连接者”的身份处理。
  // 缺陷：onOpen 把身份写进共享单例字段（:80/:84），onClose 把 this 传给 quitRoomReport（:193/:195）。
  // 先连学员、后连教员 → this.userModel 停在教员身份 → 学员断线走教员分支：
  // 整房 playStatus 被置 0（暂停）并落库。
  @Test
  void studentDisconnectMustNotPauseRoomAsTeacher() throws Exception {
    String teacherId = Fixtures.user(userDao, "t-sim-teacher").getId();
    String studentId = Fixtures.user(userDao, "t-sim-student").getId();

    SimulationRouterRoomEntity room = new SimulationRouterRoomEntity();
    room.setName("report-room");
    room.setCreateUserId(teacherId);
    room.setRoomType(REPORT.getType());
    room.setStats(1);
    room.setPlayStatus(1); // 播报中；学员断线不得把它改成 0（暂停）
    room = roomDao.save(room);
    Integer roomId = room.getId();

    saveRoomUser(roomId, teacherId, 0, 0); // 教员：userType=0（发报），channel=0
    saveRoomUser(roomId, studentId, 1, 1); // 学员：userType=1（收报），channel=1

    WebSocketContainer c = ContainerProvider.getWebSocketContainer();
    Probe studentP = new Probe();
    Probe teacherP = new Probe();
    // 连接顺序触发缺陷：先连学员、后连教员，共享字段停在教员身份。
    // onOpen 各自 get→new list→put 存在并发覆盖（P1-3，Phase 2 修），这里串行等待注册完成再连下一个。
    Session student = c.connectToServer(studentP, uri(studentId, roomId));
    awaitRoomSize(roomId, 1);
    try (Session teacher = c.connectToServer(teacherP, uri(teacherId, roomId))) {
      awaitRoomSize(roomId, 2);

      student.close(); // 学员断线
      awaitRemoved(roomId, studentId);

      SimulationRouterRoomEntity after = roomDao.findById(roomId);
      assertEquals(1, after.getPlayStatus().intValue(),
          "学员断线不得按教员身份暂停整房：playStatus 必须保持 1");
      List<WebSocketSimulationService> members = SimulationGlobal.reportRoom.get(roomId);
      assertNotNull(members, "教员仍在线，房间列表不得消失");
      assertTrue(members.stream().anyMatch(m -> teacherId.equals(m.getUserModel().getId())),
          "教员连接必须仍在房间列表");
      assertTrue(teacher.isOpen(), "教员连接必须仍然打开");
    } finally {
      if (student.isOpen()) {
        student.close();
      }
    }
  }

  private static URI uri(String userId, Integer roomId) {
    return URI.create("ws://localhost:18081/simulation/" + userId + "/" + roomId);
  }

  private void saveRoomUser(Integer roomId, String userId, int userType, int channel) {
    SimulationRouterRoomUserEntity e = new SimulationRouterRoomUserEntity();
    e.setRoomId(roomId);
    e.setUserId(userId);
    e.setUserType(userType);
    e.setChannel(channel);
    e.setUserStatus(0);
    roomUserDao.save(e);
  }

  /** 服务端 onOpen 完成注册是异步的：轮询房间列表直到到达期望人数。 */
  private static void awaitRoomSize(Integer roomId, int size) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      List<WebSocketSimulationService> members = SimulationGlobal.reportRoom.get(roomId);
      if (members != null && members.size() >= size) {
        return;
      }
      Thread.sleep(100);
    }
    throw new AssertionError("10s 内房间列表未到达 " + size + " 人");
  }

  /** 轮询直到该用户被移出房间列表（onClose 异步执行）。 */
  private static void awaitRemoved(Integer roomId, String userId) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      List<WebSocketSimulationService> members = SimulationGlobal.reportRoom.get(roomId);
      if (members == null || members.stream().noneMatch(m -> userId.equals(m.getUserModel().getId()))) {
        return;
      }
      Thread.sleep(100);
    }
    throw new AssertionError("学员断线后 10s 内未被移出房间列表");
  }
}
