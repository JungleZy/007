package com.nip.common;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Contact;
import org.eclipse.microprofile.openapi.annotations.info.Info;

/**
 * StartApplication
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2023-07-25 9:24
 */
@ApplicationPath("/api")
@OpenAPIDefinition(info = @Info(title = "海军报务api", version = "1.0.0", description = "基于Quarkus框架，海军报务系统",contact = @Contact(name="N.I.P")))
@QuarkusMain
@Slf4j
public class MainApplication extends Application {
  public static void main(String... args) {
    log.info("The Application Is Initialize...");
    Quarkus.run(NipApp.class, args);
  }

  public static class NipApp implements QuarkusApplication {

    @Override
    public int run(String... args) {
      log.info("The Application Is Running...");
      Quarkus.waitForExit();
      return 0;
    }
  }
}
