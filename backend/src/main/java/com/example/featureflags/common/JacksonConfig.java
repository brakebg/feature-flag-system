package com.example.featureflags.common;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/**
 * Spec 4.2: a wrong JSON type for any field is a malformed request, so Jackson must not coerce
 * scalars ({@code "1"} to a number, {@code 1.5} to an integer, {@code 5} to a string, {@code 1} to
 * a boolean). Unknown fields are ignored (Spring Boot default).
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

  @Bean
  JsonMapperBuilderCustomizer strictScalars() {
    return JacksonConfig::strict;
  }

  /** Applies the strict scalar rules to a mapper builder. */
  public static void strict(JsonMapper.Builder builder) {
    builder
        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
        .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        // PATCH bodies (6.1): an omitted Optional field is null, an explicit null is empty.
        .enable(DeserializationFeature.USE_NULL_FOR_MISSING_REFERENCE_VALUES);
    for (LogicalType type :
        new LogicalType[] {LogicalType.Textual, LogicalType.Integer, LogicalType.Boolean}) {
      builder.withCoercionConfig(
          type,
          config ->
              config
                  .setCoercion(CoercionInputShape.Integer, fail(type, LogicalType.Integer))
                  .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                  .setCoercion(CoercionInputShape.Boolean, fail(type, LogicalType.Boolean))
                  .setCoercion(CoercionInputShape.String, fail(type, LogicalType.Textual))
                  .setCoercion(CoercionInputShape.EmptyString, fail(type, LogicalType.Textual)));
    }
  }

  private static CoercionAction fail(LogicalType target, LogicalType same) {
    return target == same ? CoercionAction.TryConvert : CoercionAction.Fail;
  }
}
