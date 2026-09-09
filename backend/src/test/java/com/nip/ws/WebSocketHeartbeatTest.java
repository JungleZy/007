package com.nip.ws;

import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class WebSocketHeartbeatTest {
  @ClientEndpoint
  public static class Probe {
    final LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();

    @OnMessage
    public void onMessage(String message) {
      messages.add(message);
    }
  }

  @Test
  void repositoryOwnedEndpointAnswersReservedHeartbeatWithoutBusinessDispatch() throws Exception {
    WebSocketContainer container = ContainerProvider.getWebSocketContainer();
    Probe probe = new Probe();
    try (Session session = container.connectToServer(probe, URI.create("ws://localhost:18081/status"))) {
      session.getBasicRemote().sendText(WebSocketHeartbeat.PING);
      assertEquals(WebSocketHeartbeat.PONG, probe.messages.poll(5, TimeUnit.SECONDS));
    }
  }
}
