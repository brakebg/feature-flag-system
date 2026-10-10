package com.example.featureflags.support;

import com.example.featureflags.common.JacksonConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

/**
 * The only place in the test code (besides the tests that check the Jackson configuration itself)
 * that uses the Jackson mapper API. A Jackson major version change touches this class.
 */
public final class TestJson {

  private static final ObjectMapper MAPPER = new ObjectMapper();

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
        .copy()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
        .writeValueAsString(MAPPER.treeToValue(doc, Object.class));
  }

  /** Message converter with the real JSON rules, for standalone MockMvc tests. */
  public static HttpMessageConverter<Object> converter() {
    return new MappingJackson2HttpMessageConverter(
        new Jackson2ObjectMapperBuilder()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .postConfigurer(JacksonConfig::strict)
            .build());
  }
}
