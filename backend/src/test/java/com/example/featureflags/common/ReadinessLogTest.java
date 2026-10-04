package com.example.featureflags.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class ReadinessLogTest {

  @Test
  void logsEachReadinessState(CapturedOutput output) {
    ReadinessLog log = new ReadinessLog();
    log.onChange(new AvailabilityChangeEvent<>(this, ReadinessState.REFUSING_TRAFFIC));
    log.onChange(new AvailabilityChangeEvent<>(this, ReadinessState.ACCEPTING_TRAFFIC));
    assertThat(output.getOut())
        .contains("Readiness state: REFUSING_TRAFFIC")
        .contains("Readiness state: ACCEPTING_TRAFFIC");
  }
}
