package com.nip.controller.free;

import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * 组网升级端点的契约：登录前可达、只分发留存目录内与枚举匹配的包、业务拒绝走 HTTP200 信封。
 * 桌面外壳 electron/update/version-check.js 依赖这些行为判断版本并取包。
 */
@QuarkusTest
class UpdateControllerTest {
  private static final byte[] CONTENT = "package-bytes".getBytes(StandardCharsets.UTF_8);
  private static final String SHA = "a".repeat(64);
  private Path store;

  @BeforeEach
  void prepare() throws IOException {
    store = Path.of(ConfigProvider.getConfig().getValue("nip.update.directory", String.class));
    clear();
  }

  @AfterEach
  void clear() throws IOException {
    if (Files.exists(store)) {
      try (var paths = Files.walk(store)) {
        for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
          Files.delete(path);
        }
      }
    }
  }

  private void stored(String target, String style, String version, String fileName, long size) throws IOException {
    Path folder = store.resolve(target).resolve(style);
    Files.createDirectories(folder);
    Files.write(folder.resolve("nip-update-" + version + "-" + target + "-" + style + ".nipupd"), CONTENT);
    Files.writeString(folder.resolve("info.json"), "{\"fileName\":\"" + fileName + "\",\"version\":\"" + version
        + "\",\"size\":" + size + ",\"sha256\":\"" + SHA + "\"}");
  }

  @Test
  void versionIsReadableBeforeLoginAndListsStoredPackages() throws IOException {
    given().when().get("/api/update/info").then().statusCode(200)
        .body("code", equalTo(200))
        .body("data.version", matchesPattern("\\d+\\.\\d+\\.\\d+"))
        .body("data.packages", hasSize(0));
    stored("linux-arm64", "HJ", "3.2.0", "nip-update-3.2.0-linux-arm64-HJ.nipupd", CONTENT.length);
    given().when().get("/api/update/info").then().statusCode(200)
        .body("data.packages", hasSize(1))
        .body("data.packages[0].target", equalTo("linux-arm64"))
        .body("data.packages[0].style", equalTo("HJ"))
        .body("data.packages[0].version", equalTo("3.2.0"))
        .body("data.packages[0].size", equalTo(CONTENT.length))
        .body("data.packages[0].sha256", equalTo(SHA));
  }

  @Test
  void downloadsExactlyTheStoredFile() throws IOException {
    stored("windows-ia32", "LJ", "3.2.0", "nip-update-3.2.0-windows-ia32-LJ.nipupd", CONTENT.length);
    byte[] body = given().queryParam("target", "windows-ia32").queryParam("style", "LJ")
        .when().get("/api/update/package")
        .then().statusCode(200).header("Content-Type", containsString("application/octet-stream"))
        .extract().asByteArray();
    assertArrayEquals(CONTENT, body);
  }

  @Test
  void missingOrUnknownCombinationIsABusinessRejection() {
    given().queryParam("target", "linux-x64").queryParam("style", "HJ").when().get("/api/update/package")
        .then().statusCode(200).header("Content-Type", containsString("application/json")).body("code", equalTo(202));
    for (String[] pair : new String[][]{{"../../etc", "HJ"}, {"linux-x64", "../HJ"}, {"linux-x64", "hj"}, {"solaris-sparc", "HJ"}}) {
      given().queryParam("target", pair[0]).queryParam("style", pair[1]).when().get("/api/update/package")
          .then().statusCode(200).body("code", equalTo(202));
    }
    given().when().get("/api/update/package").then().statusCode(200).body("code", equalTo(202));
  }

  @Test
  void infoPointingOutsideItsFolderOrDisagreeingWithTheFileIsIgnored() throws IOException {
    // info.json 由外壳写入，但后端不信任其中的文件名：必须与枚举值拼出的固定格式一致。
    stored("linux-x64", "HJ", "3.2.0", "../../../secret.nipupd", CONTENT.length);
    stored("linux-x64", "LJ", "3.2.0", "nip-update-3.2.0-linux-x64-LJ.nipupd", CONTENT.length + 1);
    given().when().get("/api/update/info").then().statusCode(200).body("data.packages", hasSize(0));
    given().queryParam("target", "linux-x64").queryParam("style", "HJ").when().get("/api/update/package")
        .then().statusCode(200).body("code", equalTo(202));
  }
}
