package com.example.featureflags.common;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spec 4.2: a wrong JSON type for any field is a malformed request, so Jackson must not coerce
 * scalars ({@code "1"} to a number, {@code 1.5} to an integer, {@code 5} to a string, {@code 1} to
 * a boolean). Unknown fields are ignored (Spring Boot default).
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

  @Bean
  Jackson2ObjectMapperBuilderCustomizer strictScalars() {
    return builder -> builder.postConfigurer(JacksonConfig::strict);
  }

  /** Applies the strict scalar rules to a mapper. */
  public static void strict(ObjectMapper mapper) {
    mapper.configure(MapperFeature.ALLOW_COERCION_OF_SCALARS, false);
    mapper.configure(DeserializationFeature.ACCEPT_FLOAT_AS_INT, false);
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    mapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true);
    for (LogicalType type :
        new LogicalType[] {LogicalType.Textual, LogicalType.Integer, LogicalType.Boolean}) {
      mapper
          .coercionConfigFor(type)
          .setCoercion(CoercionInputShape.Integer, fail(type, LogicalType.Integer))
          .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
          .setCoercion(CoercionInputShape.Boolean, fail(type, LogicalType.Boolean))
          .setCoercion(CoercionInputShape.String, fail(type, LogicalType.Textual))
          .setCoercion(CoercionInputShape.EmptyString, fail(type, LogicalType.Textual));
    }
  }

  private static CoercionAction fail(LogicalType target, LogicalType same) {
    return target == same ? CoercionAction.TryConvert : CoercionAction.Fail;
  }
}
