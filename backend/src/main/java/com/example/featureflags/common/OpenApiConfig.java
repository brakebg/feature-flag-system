package com.example.featureflags.common;

import io.swagger.v3.oas.models.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spec 3, 6.2: the OpenAPI document is the UI's contract, so it carries the 6.2 optionality:
 * response fields are required except the optional ones ({@code description}, {@code details});
 * request fields are required as 6.2 says; a request {@code description} may be {@code null}.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

  static final Set<String> RESPONSES =
      Set.of(
          "Flag",
          "Group",
          "GroupSummary",
          "GroupDetail",
          "AuditEventView",
          "PagedModelAuditEventView",
          "PageMetadata",
          "LoginResponse",
          "TokenResponse",
          "AllFlags",
          "GroupFlags",
          "OneFlag");
  static final Set<String> OPTIONAL = Set.of("description", "details");
  static final Map<String, List<String>> REQUESTS =
      Map.of(
          "CreateGroupRequest", List.of("key", "name"),
          "UpdateGroupRequest", List.of("version"),
          "CreateFlagRequest", List.of("key"),
          "UpdateFlagRequest", List.of("version"),
          "ToggleFlagRequest", List.of("enabled"),
          "LoginRequest", List.of("password", "username"));

  @Bean
  OpenApiCustomizer specOptionality() {
    return api -> {
      if (api.getComponents() == null || api.getComponents().getSchemas() == null) {
        return;
      }
      @SuppressWarnings("rawtypes") // reason: the swagger model API uses raw Schema maps
      Map<String, Schema> schemas = api.getComponents().getSchemas();
      schemas.forEach(
          (name, schema) -> {
            if (schema.getProperties() == null) {
              return;
            }
            if (RESPONSES.contains(name)) {
              List<String> required = new ArrayList<>();
              for (Object property : schema.getProperties().keySet()) {
                if (!OPTIONAL.contains(property)) {
                  required.add((String) property);
                }
              }
              required.sort(String::compareTo);
              schema.setRequired(required);
            } else if (REQUESTS.containsKey(name)) {
              schema.setRequired(REQUESTS.get(name));
              // springdoc 3 turns @PositiveOrZero into "minimum": 0. The 1.0.0 contract has none
              // (spec 002 section 4 item 3); the check itself stays in Bean Validation.
              if (schema.getProperties().get("version") instanceof Schema<?> version) {
                version.setMinimum(null);
              }
              Object description = schema.getProperties().get("description");
              if (description instanceof Schema<?> d) {
                // OpenAPI 3.1: nullable is the extra JSON type "null".
                Set<String> types =
                    new java.util.LinkedHashSet<>(
                        d.getTypes() == null ? Set.of("string") : d.getTypes());
                types.add("null");
                d.setTypes(types);
              }
            }
          });
    };
  }
}
