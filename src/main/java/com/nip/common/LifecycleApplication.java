package com.nip.common;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * LifecycleApplication
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2023-07-27 10:39
 */
@ApplicationScoped
public class LifecycleApplication {
  private static final Logger LOG = Logger.getLogger("Application");

  void onStart(@Observes StartupEvent event) {
    LOG.info("The Application Is Starting...");
    try {
      InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("banner.txt");
      if (is != null) {
        String banner = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        String version = ConfigProvider.getConfig().getOptionalValue("version", String.class).orElse("");
        banner = banner.replace("${version}", version);
        LOG.info("\n" + banner);
      }
    } catch (Exception ignored) {
    }
  }

  void onStop(@Observes ShutdownEvent event) {
    LOG.info("The Application Is Stopping...");
  }
}
