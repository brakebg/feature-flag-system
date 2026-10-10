package com.example.featureflags.common;

import java.io.OutputStream;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The only place in the main code that uses the Jackson tree and mapper API. Filters call these
 * methods instead of Jackson, so a Jackson major version change touches this class and {@link
 * JacksonConfig}.
 */
@Component
public class Json {

  private final ObjectMapper mapper;

  public Json(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  /** Writes {@code value} as JSON to {@code out}. */
  public void write(OutputStream out, Object value) {
    mapper.writeValue(out, value);
  }

  /**
   * Spec 9.3: reduces a health body to {@code status} and the {@code db} component. Returns null
   * when {@code body} is not a JSON object with a {@code status} field.
   */
  public byte[] keepHealthStatusAndDb(byte[] body) {
    JsonNode in;
    try {
      in = mapper.readTree(body);
    } catch (JacksonException notJson) {
      return null;
    }
    if (in == null || !in.has("status")) {
      return null;
    }
    ObjectNode out = mapper.createObjectNode();
    out.set("status", in.get("status"));
    JsonNode db = in.path("components").path("db");
    if (!db.isMissingNode()) {
      out.putObject("components").set("db", db);
    }
    return mapper.writeValueAsBytes(out);
  }
}
