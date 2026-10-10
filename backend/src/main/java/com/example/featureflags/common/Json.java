package com.example.featureflags.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.OutputStream;
import org.springframework.stereotype.Component;

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
  public void write(OutputStream out, Object value) throws IOException {
    mapper.writeValue(out, value);
  }

  /**
   * Spec 9.3: reduces a health body to {@code status} and the {@code db} component. Returns null
   * when {@code body} is not a JSON object with a {@code status} field.
   */
  public byte[] keepHealthStatusAndDb(byte[] body) throws IOException {
    JsonNode in;
    try {
      in = mapper.readTree(body);
    } catch (IOException notJson) {
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
