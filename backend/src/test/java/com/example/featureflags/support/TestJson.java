package com.example.featureflags.support;

import com.example.featureflags.common.JacksonConfig;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The only place in the test code (besides the tests that check the Jackson configuration itself)
 * that uses the Jackson mapper API. A Jackson major version change touches this class.
 */
public final class TestJson {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private TestJson() {}

  public static JsonNode tree(String json) throws Exception {
    return MAPPER.readTree(json);
  }

  public static JsonNode tree(byte[] json) throws Exception {
    return MAPPER.readTree(json);
  }

  public static byte[] bytes(Object value) throws Exception {
    return MAPPER.writeValueAsBytes(value);
  }

  /** Pretty-printed JSON with object keys in sorted order, as written to backend/openapi.json. */
  public static String prettySorted(JsonNode doc) throws Exception {
    return MAPPER
        .rebuild()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
        .build()
        .writeValueAsString(MAPPER.treeToValue(doc, Object.class));
  }

  /** Message converter with the real JSON rules, for standalone MockMvc tests. */
  public static HttpMessageConverter<Object> converter() {
    JsonMapper.Builder builder = JsonMapper.builder();
    JacksonConfig.strict(builder);
    builder.addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);
    return new JacksonJsonHttpMessageConverter(builder.build());
  }
}
