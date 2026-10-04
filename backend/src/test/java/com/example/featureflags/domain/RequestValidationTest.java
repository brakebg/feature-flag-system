package com.example.featureflags.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.featureflags.common.JacksonConfig;
import com.example.featureflags.flag.CreateFlagRequest;
import com.example.featureflags.flag.UpdateFlagRequest;
import com.example.featureflags.group.CreateGroupRequest;
import com.example.featureflags.group.UpdateGroupRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Import;

/** Spec 4.2: validation rules of the request bodies (6.2). */
@JsonTest
@Import(JacksonConfig.class)
class RequestValidationTest {

  private static final Validator VALIDATOR =
      Validation.buildDefaultValidatorFactory().getValidator();

  @Autowired ObjectMapper mapper;

  private static Set<String> invalidFields(Object o) {
    Set<ConstraintViolation<Object>> v = VALIDATOR.validate(o);
    return v.stream()
        .map(c -> c.getPropertyPath().iterator().next().getName())
        .collect(Collectors.toSet());
  }

  private static String repeat(String s, int n) {
    return s.repeat(n);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Orders", "1abc", "a", "has space", "-ab", "ab_c", "ÿab"})
  @Tag("AC-GRP-3")
  void keysFailingTheRegexAreRejected(String key) {
    assertThat(invalidFields(new CreateGroupRequest(key, "Name", null))).containsExactly("key");
    assertThat(invalidFields(new CreateFlagRequest(key, null, null))).containsExactly("key");
  }

  @Test
  void keyLengthIsTwoToFifty() {
    assertThat(invalidFields(new CreateGroupRequest("ab", "N", null))).isEmpty();
    assertThat(invalidFields(new CreateGroupRequest("a" + repeat("b", 49), "N", null))).isEmpty();
    assertThat(invalidFields(new CreateGroupRequest("a" + repeat("b", 50), "N", null)))
        .containsExactly("key");
    assertThat(invalidFields(new CreateGroupRequest(null, "N", null))).containsExactly("key");
  }

  @Test
  void nameIsTrimmedAndRequired() {
    assertThat(new CreateGroupRequest("ab", "  Orders \t", null).name()).isEqualTo("Orders");
    assertThat(invalidFields(new CreateGroupRequest("ab", "   ", null))).containsExactly("name");
    assertThat(invalidFields(new CreateGroupRequest("ab", null, null))).containsExactly("name");
  }

  @Test
  void lengthsCountCodePoints() {
    String emoji = "🚀"; // one code point, two UTF-16 chars
    assertThat(invalidFields(new CreateGroupRequest("ab", repeat(emoji, 100), null))).isEmpty();
    assertThat(invalidFields(new CreateGroupRequest("ab", repeat(emoji, 101), null)))
        .containsExactly("name");
    assertThat(invalidFields(new CreateGroupRequest("ab", "N", repeat(emoji, 500)))).isEmpty();
    assertThat(invalidFields(new CreateGroupRequest("ab", "N", repeat(emoji, 501))))
        .containsExactly("description");
    assertThat(invalidFields(new CreateFlagRequest("ab", repeat("x", 501), null)))
        .containsExactly("description");
  }

  @Test
  void emptyDescriptionMeansNoDescription() {
    assertThat(new CreateGroupRequest("ab", "N", "").description()).isNull();
    assertThat(new CreateFlagRequest("ab", "", null).description()).isNull();
  }

  @Test
  void createFlagEnabledDefaultsToFalse() {
    assertThat(new CreateFlagRequest("ab", null, null).enabled()).isFalse();
    assertThat(new CreateFlagRequest("ab", null, true).enabled()).isTrue();
  }

  @Test
  void patchDistinguishesOmittedFromNull() throws Exception {
    UpdateGroupRequest omitted = mapper.readValue("{\"version\":0}", UpdateGroupRequest.class);
    assertThat(omitted.name()).isNull();
    assertThat(omitted.description()).isNull();

    UpdateGroupRequest cleared =
        mapper.readValue("{\"description\":null,\"version\":0}", UpdateGroupRequest.class);
    assertThat(cleared.description()).isEqualTo(Optional.empty());

    UpdateGroupRequest emptied =
        mapper.readValue("{\"description\":\"\",\"version\":0}", UpdateGroupRequest.class);
    assertThat(emptied.description()).isEqualTo(Optional.empty());

    UpdateGroupRequest nullName =
        mapper.readValue("{\"name\":null,\"version\":0}", UpdateGroupRequest.class);
    assertThat(invalidFields(nullName)).containsExactly("name");

    UpdateGroupRequest blankName =
        mapper.readValue("{\"name\":\"  \",\"version\":0}", UpdateGroupRequest.class);
    assertThat(invalidFields(blankName)).containsExactly("name");

    UpdateGroupRequest trimmed =
        mapper.readValue("{\"name\":\" New \",\"version\":2}", UpdateGroupRequest.class);
    assertThat(trimmed.name()).contains("New");
    assertThat(invalidFields(trimmed)).isEmpty();
  }

  @Test
  void versionIsRequiredAndNotNegative() throws Exception {
    assertThat(invalidFields(mapper.readValue("{}", UpdateGroupRequest.class)))
        .containsExactly("version");
    assertThat(invalidFields(mapper.readValue("{\"version\":-1}", UpdateFlagRequest.class)))
        .containsExactly("version");
    assertThat(invalidFields(mapper.readValue("{\"version\":0}", UpdateFlagRequest.class)))
        .isEmpty();
  }

  @Test
  void patchFlagEnabledNullIsInvalidAndDescriptionNullClears() throws Exception {
    UpdateFlagRequest r =
        mapper.readValue(
            "{\"enabled\":null,\"description\":null,\"version\":0}", UpdateFlagRequest.class);
    assertThat(invalidFields(r)).containsExactly("enabled");
    assertThat(r.description()).isEqualTo(Optional.empty());
  }
}
