package com.nip.rehearsal;

import com.nip.common.utils.JSONUtils;
import com.nip.dao.UserDao;
import com.nip.dto.general.GeneralPatTrainRoomUserDto;
import com.nip.dto.general.GeneralPatTrainUserModelDto;
import com.nip.testsupport.Fixtures;
import com.nip.testsupport.WebSocketSessionProbe;
import com.nip.testsupport.WebSocketStateReset;
import com.nip.ws.StatusWebSocket;
import com.nip.ws.WebSocketGeneralKeyPatService;
import com.nip.ws.WebSocketGeneralTelexPatService;
import com.nip.ws.WebSocketGeneralTickerPatService;
import com.nip.ws.WebSocketHeartbeat;
import com.nip.ws.WebSocketService;
import com.nip.ws.WebSocketSimulationService;
import com.nip.ws.WebSocketUnionService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Opt-in Phase 0 protocol evidence producer; this class is intentionally not a
 * Surefire {@code *Test} class. It uses the existing isolated session probe and
 * invokes the real CDI endpoint callbacks, writing redacted NDJSON sequences.
 *
 * <p>Run with:
 * {@code migration.evidence.dir=docs/reviews/rust-migration -Dtest=WebSocketContractSnapshotRehearsal test}
 * (the Maven test selector is supplied by the caller; this class does not run in
 * the default suite). No token or device value is ever written to the output.
 */
@QuarkusTest
class WebSocketContractSnapshotRehearsal {
  @Inject
  UserDao userDao;

  @Inject
  StatusWebSocket status;
  @Inject
  WebSocketService classic;
  @Inject
  WebSocketUnionService union;
  @Inject
  WebSocketSimulationService simulation;
  @Inject
  WebSocketGeneralKeyPatService key;
  @Inject
  WebSocketGeneralTelexPatService telex;
  @Inject
  WebSocketGeneralTickerPatService ticker;

  @Test
  void exportProtocolSnapshots() throws Exception {
    Path outputDir = Path.of(System.getProperty("migration.evidence.dir", "target/rust-migration"));
    Files.createDirectories(outputDir);
    List<Map<String, Object>> events = new ArrayList<>();

    recordAnonymousHandshakeEvents(events);
    recordInvalidHandshakeEvents(events);
    recordHeartbeat(events);
    recordUnionReplacementAndStaleClose(events);
    recordGeneralTrainingFrames(events);

    Path output = outputDir.resolve("ws-protocol-samples.ndjson");
    try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
      for (Map<String, Object> event : events) {
        writer.write(JSONUtils.toJson(event));
        writer.newLine();
      }
    }
    writeSampleMetadata(outputDir, events.size());
  }

  private void writeSampleMetadata(Path outputDir, int eventCount) throws Exception {
    Path repository = Path.of("").toAbsolutePath();
    while (repository != null && !Files.exists(repository.resolve(".git"))) {
      repository = repository.getParent();
    }
    if (repository == null) {
      throw new IllegalStateException("cannot locate repository root for WS evidence provenance");
    }
    Process git = new ProcessBuilder("git", "rev-parse", "HEAD")
        .directory(repository.toFile()).redirectErrorStream(true).start();
    String commit = new String(git.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
    if (git.waitFor() != 0 || commit.isEmpty()) {
      throw new IllegalStateException("cannot resolve source commit for WS evidence");
    }
    Path source = repository.resolve("backend/src/test/java/com/nip/rehearsal/WebSocketContractSnapshotRehearsal.java");
    String sourceSha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(source)));
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("schema", "rust-migration-ws-samples/v1");
    metadata.put("generated_at_utc", Instant.now().toString());
    metadata.put("source_commit", commit);
    metadata.put("collector_command", "cd backend && JAVA_HOME=$HOME/.local/opt/jdk21 ./mvnw -B -Dtest=WebSocketContractSnapshotRehearsal -Dmigration.evidence.dir=../docs/reviews/rust-migration test");
    metadata.put("source_file", "backend/src/test/java/com/nip/rehearsal/WebSocketContractSnapshotRehearsal.java");
    metadata.put("source_sha256", sourceSha);
    metadata.put("event_count", eventCount);
    metadata.put("credentials_policy", "synthetic credentials are used only at runtime and never written; IDs are redacted fixture references");
    metadata.put("coverage_boundary", "real CDI callbacks and isolated Session probes; no browser/network/container WebSocket client coverage");
    Files.writeString(outputDir.resolve("ws-protocol-samples-metadata.json"),
        JSONUtils.toJson(metadata) + System.lineSeparator(), StandardCharsets.UTF_8);
  }

  private void recordAnonymousHandshakeEvents(List<Map<String, Object>> events) throws Exception {
    event(events, "handshake", "/websocket/{sid}", "anonymous", "fixture-path-user", "fixture-user");
    WebSocketSessionProbe classicProbe = WebSocketSessionProbe.anonymous("anonymous-classic");
    classic.onOpen(classicProbe.session());
    append(events, "handshake", "/websocket/{sid}", "anonymous", classicProbe);

    WebSocketSessionProbe unionProbe = WebSocketSessionProbe.anonymous("anonymous-union");
    union.onOpen(unionProbe.session());
    append(events, "handshake", "/websocketUnion/{sid}", "anonymous", unionProbe);

    WebSocketSessionProbe simulationProbe = WebSocketSessionProbe.anonymous("anonymous-simulation");
    simulation.onOpen(simulationProbe.session(), "101");
    append(events, "handshake", "/simulation/{id}/{roomId}", "anonymous", simulationProbe);

    WebSocketSessionProbe keyProbe = WebSocketSessionProbe.anonymous("anonymous-key");
    key.onOpen("101", keyProbe.session());
    append(events, "handshake", "/generalKeyPatTrain/{uid}/{trainId}", "anonymous", keyProbe);

    WebSocketSessionProbe telexProbe = WebSocketSessionProbe.anonymous("anonymous-telex");
    telex.onOpen("101", telexProbe.session());
    append(events, "handshake", "/generalTelexPatTrain/{uid}/{trainId}", "anonymous", telexProbe);

    WebSocketSessionProbe tickerProbe = WebSocketSessionProbe.anonymous("anonymous-ticker");
    ticker.onOpen("101", "0", tickerProbe.session());
    append(events, "handshake", "/generalTickerPat/{uid}/{trainId}/{role}", "anonymous", tickerProbe);
  }

  private void recordInvalidHandshakeEvents(List<Map<String, Object>> events) throws Exception {
    WebSocketSessionProbe invalid = WebSocketSessionProbe.open(
        "invalid-classic", "fixture-invalid-token", "fixture-invalid-device");
    classic.onOpen(invalid.session());
    append(events, "handshake", "/websocket/{sid}", "invalid-query", invalid);

    WebSocketSessionProbe forgedPath = WebSocketSessionProbe.open(
        "invalid-forged-path", "fixture-invalid-token", "fixture-invalid-device");
    key.onOpen("not-a-number", forgedPath.session());
    append(events, "handshake", "/generalKeyPatTrain/fixture-path-user/not-a-number",
        "invalid-query-and-path", forgedPath);
  }

  private void recordHeartbeat(List<Map<String, Object>> events) {
    WebSocketSessionProbe probe = WebSocketSessionProbe.bound("heartbeat", "fixture-user");
    status.onOpen(probe.session());
    status.onMessage(WebSocketHeartbeat.PING, probe.session());
    append(events, "heartbeat", "/status", "reserved-ping", probe);
    status.onClose(probe.session());
  }

  private void recordUnionReplacementAndStaleClose(List<Map<String, Object>> events) throws Exception {
    WebSocketStateReset.clearAll();
    String token = "phase0-ws-token-" + UUID.randomUUID();
    String device = "phase0-ws-device-" + UUID.randomUUID();
    Fixtures.user(userDao, token, device);
    WebSocketSessionProbe oldConnection = WebSocketSessionProbe.open("union-old", token, device);
    union.onOpen(oldConnection.session());
    append(events, "lifecycle", "/websocketUnion/{sid}", "first-open", oldConnection);

    WebSocketSessionProbe newConnection = WebSocketSessionProbe.open("union-new", token, device);
    union.onOpen(newConnection.session());
    append(events, "lifecycle", "/websocketUnion/{sid}", "replacement-open", oldConnection);
    append(events, "lifecycle", "/websocketUnion/{sid}", "replacement-current", newConnection);

    union.onClose(oldConnection.session());
    append(events, "lifecycle", "/websocketUnion/{sid}", "stale-old-close", newConnection);
    union.onClose(newConnection.session());
    append(events, "lifecycle", "/websocketUnion/{sid}", "current-close", newConnection);
    event(events, "identity", "/websocketUnion/{sid}", "path-id-is-routing-only",
        "forged-path-user", "fixture-authenticated-user");
    WebSocketStateReset.clearAll();
  }

  private void recordGeneralTrainingFrames(List<Map<String, Object>> events) {
    WebSocketStateReset.clearAll();
    WebSocketSessionProbe student = WebSocketSessionProbe.bound("training-student", "fixture-student");
    WebSocketSessionProbe teacher = WebSocketSessionProbe.bound("training-teacher", "fixture-teacher");
    GeneralPatTrainUserModelDto studentModel = new GeneralPatTrainUserModelDto();
    studentModel.setId("fixture-student");
    studentModel.setRole(0);
    studentModel.setStatus(1);
    studentModel.setSession(student.session());
    GeneralPatTrainUserModelDto teacherModel = new GeneralPatTrainUserModelDto();
    teacherModel.setId("fixture-teacher");
    teacherModel.setRole(1);
    teacherModel.setStatus(1);
    teacherModel.setSession(teacher.session());
    GeneralPatTrainRoomUserDto room = new GeneralPatTrainRoomUserDto();
    room.getJoinUser().add(studentModel);
    room.setGroupUser(teacherModel);
    WebSocketGeneralKeyPatService.ROOM.put(101, room);

    key.onMessage("101", "{", student.session());
    append(events, "message", "/generalKeyPatTrain/{uid}/{trainId}", "malformed-frame", student);
    student.outbound().clear();
    teacher.outbound().clear();
    key.onMessage("101", "{\"topic\":\"ready\"}", student.session());
    append(events, "message", "/generalKeyPatTrain/{uid}/{trainId}", "training-ready", teacher);
    key.onClose("101", student.session());
    append(events, "lifecycle", "/generalKeyPatTrain/{uid}/{trainId}", "normal-close", teacher);
    WebSocketStateReset.clearAll();
  }

  private static void event(List<Map<String, Object>> events, String family, String path,
      String phase, String pathIdentity, String authenticatedIdentity) {
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("captured_at_utc", Instant.now().toString());
    event.put("family", family);
    event.put("path", path);
    event.put("phase", phase);
    event.put("path_identity_ref", pathIdentity);
    event.put("authenticated_identity_ref", authenticatedIdentity);
    events.add(event);
  }

  private static void append(List<Map<String, Object>> events, String family, String path,
      String phase, WebSocketSessionProbe probe) {
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("captured_at_utc", Instant.now().toString());
    event.put("family", family);
    event.put("path", path);
    event.put("phase", phase);
    event.put("session_id_ref", "fixture-session");
    event.put("open_after_callback", probe.session().isOpen());
    event.put("outbound_frames", List.copyOf(probe.outbound()));
    events.add(event);
  }
}
