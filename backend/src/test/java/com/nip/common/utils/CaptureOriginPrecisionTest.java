package com.nip.common.utils;

import com.nip.dto.CaptureInterval;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CaptureOriginPrecisionTest {
  @Test
  void legacyRoundingReproducesLogged392MillisecondExcessAndRetrySuccess() {
    LocalDateTime origin = LocalDateTime.of(2026, 11, 5, 9, 40, 0, 608_000_000);
    LocalDateTime reloaded = mysqlDatetimeZero(origin);
    LocalDateTime received = origin.plusNanos(187151_000_000L);
    long bound = Duration.between(reloaded, received).toMillis();
    assertEquals(186759, bound);
    var submitted = List.of(new CaptureInterval(4, 187151));
    assertThrows(IllegalArgumentException.class, () -> CaptureTimeline.durationMillis(submitted, bound));
    assertDoesNotThrow(() -> CaptureTimeline.durationMillis(submitted, bound + 500));
  }
  @ParameterizedTest
  @ValueSource(ints = {1, 499, 500, 608, 999})
  void firstPageSubmissionUsesSameOriginBeforeAndAfterDatabaseSecondRounding(int millis) {
    LocalDateTime begun = LocalDateTime.of(2026, 11, 5, 9, 40, 0).plusNanos(millis * 1_000_000L);
    LocalDateTime origin = CaptureTimeline.persistedOrigin(begun);
    LocalDateTime reloaded = mysqlDatetimeZero(origin);
    LocalDateTime response = begun.plusNanos(4_000_000);
    long serverElapsed = Duration.between(origin, response).toMillis();
    long capturedEnd = serverElapsed + 187147;
    LocalDateTime received = response.plusNanos(187147_000_000L);
    long bound = Duration.between(reloaded, received).toMillis();
    assertDoesNotThrow(() -> CaptureTimeline.durationMillis(List.of(new CaptureInterval(serverElapsed, capturedEnd)), bound));
    assertEquals(187147, CaptureTimeline.durationMillis(List.of(new CaptureInterval(serverElapsed, capturedEnd)), bound));
    assertThrows(IllegalArgumentException.class,
        () -> CaptureTimeline.durationMillis(List.of(new CaptureInterval(serverElapsed, bound + 1)), bound));
  }

  private LocalDateTime mysqlDatetimeZero(LocalDateTime value) {
    return value.plusNanos(500_000_000).truncatedTo(ChronoUnit.SECONDS);
  }
}
