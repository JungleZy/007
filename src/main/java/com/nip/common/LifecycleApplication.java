package com.nip.common;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;

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
  }

  void onStop(@Observes ShutdownEvent event) {
    LOG.info("The Application Is Stopping...");
  }
}
