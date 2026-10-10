package com.example.featureflags.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Import;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Spec 4.2 items 5 and 6, 6.2: JSON rules for request and response bodies. */
@JsonTest
@Import(JacksonConfig.class)
class JacksonConfigTest {

  @Autowired ObjectMapper mapper;

  record Body(String name, Long version, Boolean enabled) {}

  @Test
  void stringWhereANumberIsExpectedIsRejected() {
    assertThatThrownBy(() -> mapper.readValue("{\"version\":\"1\"}", Body.class))
        .isInstanceOf(JacksonException.class);
  }

  @Test
  void fractionWhereAnIntegerIsExpectedIsRejected() {
    assertThatThrownBy(() -> mapper.readValue("{\"version\":1.5}", Body.class))
        .isInstanceOf(JacksonException.class);
  }

  @Test
  void numberOrBooleanWhereAStringIsExpectedIsRejected() {
    assertThatThrownBy(() -> mapper.readValue("{\"name\":5}", Body.class))
        .isInstanceOf(JacksonException.class);
    assertThatThrownBy(() -> mapper.readValue("{\"name\":true}", Body.class))
        .isInstanceOf(JacksonException.class);
  }

  @Test
  void numberOrStringWhereABooleanIsExpectedIsRejected() {
    assertThatThrownBy(() -> mapper.readValue("{\"enabled\":1}", Body.class))
        .isInstanceOf(JacksonException.class);
    assertThatThrownBy(() -> mapper.readValue("{\"enabled\":\"true\"}", Body.class))
        .isInstanceOf(JacksonException.class);
  }

  record Primitive(boolean enabled) {}

  @Test
  void otherWrongTypesAreRejected() {
    assertThatThrownBy(() -> mapper.readValue("{\"version\":true}", Body.class))
        .isInstanceOf(JacksonException.class);
    assertThatThrownBy(() -> mapper.readValue("{\"version\":\"\"}", Body.class))
        .isInstanceOf(JacksonException.class);
    assertThatThrownBy(() -> mapper.readValue("{\"enabled\":null}", Primitive.class))
        .isInstanceOf(JacksonException.class);
  }

  @Test
  void correctTypesAndUnknownFieldsAreAccepted() throws Exception {
    Body b =
        mapper.readValue("{\"name\":\"a\",\"version\":3,\"enabled\":true,\"x\":1}", Body.class);
    assertThat(b).isEqualTo(new Body("a", 3L, true));
  }

  @Test
  void instantsAreIsoUtcWithZ() throws Exception {
    assertThat(mapper.writeValueAsString(Instant.parse("2026-10-01T14:32:05.123Z")))
        .isEqualTo("\"2026-10-01T14:32:05.123Z\"");
  }
}
