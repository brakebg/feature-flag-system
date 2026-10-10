package com.example.featureflags.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Spec 9.3: the health body is cut to status and db; anything else is left alone. */
@Tag("AC-OPS-3")
class JsonHealthFilterTest {

  private final Json json = new Json(JsonMapper.builder().build());

  private byte[] keep(String body) {
    return json.keepHealthStatusAndDb(body.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void keepsStatusAndDbOnly() {
    String body =
        "{\"status\":\"UP\",\"components\":{\"db\":{\"status\":\"UP\"},\"ping\":{}},\"groups\":[1]}";
    assertThat(new String(keep(body), StandardCharsets.UTF_8))
        .isEqualTo("{\"status\":\"UP\",\"components\":{\"db\":{\"status\":\"UP\"}}}");
  }

  @Test
  void bodyWithoutDbKeepsOnlyStatus() {
    assertThat(new String(keep("{\"status\":\"DOWN\"}"), StandardCharsets.UTF_8))
        .isEqualTo("{\"status\":\"DOWN\"}");
  }

  @Test
  void notJsonNoStatusAndEmptyBodiesGiveNull() {
    assertThat(keep("not json")).isNull();
    assertThat(keep("{}")).isNull();
    assertThat(keep("")).isNull();
  }
}
