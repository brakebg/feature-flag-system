package com.example.featureflags.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Spec 9.1: one problem-details shape for every error. */
@org.junit.jupiter.api.extension.ExtendWith(
    org.springframework.boot.test.system.OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

  private static final String BASE = "https://featureflags.local/problems/";
  private MockMvc mvc;

  record Body(@NotNull @Pattern(regexp = "^[a-z][a-z0-9-]{1,49}$") String key, Long version) {}

  @RestController
  static class ThrowingController {
    @PostMapping("/t/body")
    String body(@Valid @RequestBody Body body) {
      return "ok";
    }

    @GetMapping("/t/id/{id}")
    String id(@PathVariable UUID id) {
      return "ok";
    }

    @GetMapping("/t/page")
    String page(@RequestParam int page) {
      return "ok";
    }

    @GetMapping("/t/size")
    String size(@RequestParam @jakarta.validation.constraints.Min(1) int size) {
      return "ok";
    }

    @PostMapping("/t/patch")
    String patch(@Valid @RequestBody com.example.featureflags.group.UpdateGroupRequest body) {
      return "ok";
    }

    @GetMapping("/t/{what}")
    String raise(@PathVariable String what) throws Exception {
      throw switch (what) {
        case "not-found" -> new NotFoundException("no group 1");
        case "duplicate" -> new DuplicateKeyException("key", "Key already exists");
        case "version" -> new VersionConflictException();
        case "optimistic" -> new ObjectOptimisticLockingFailureException(Object.class, "1");
        case "unique" ->
            new DataIntegrityViolationException("x", new java.sql.SQLException("dup", "23505"));
        case "fk" ->
            new DataIntegrityViolationException("x", new java.sql.SQLException("fk", "23503"));
        case "too-large-body" ->
            new org.springframework.http.converter.HttpMessageNotReadableException(
                "x",
                new PayloadTooLargeException(),
                new org.springframework.mock.http.MockHttpInputMessage(new byte[0]));
        case "status-404" ->
            new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND);
        case "not-acceptable" ->
            new org.springframework.web.HttpMediaTypeNotAcceptableException("application/xml");
        case "limit" -> new LimitReachedException("max 1000 groups");
        case "too-large" -> new PayloadTooLargeException();
        case "malformed" -> new MalformedRequestException("bad body");
        case "field" -> new FieldValidationException("sort", "must be key, name or updatedAt");
        default -> new IllegalStateException("secret internal detail");
      };
    }
  }

  @BeforeEach
  void setUp() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mvc =
        MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .setMessageConverters(
                new MappingJackson2HttpMessageConverter(
                    new Jackson2ObjectMapperBuilder()
                        .postConfigurer(JacksonConfig::strict)
                        .build()))
            .setValidator(validator)
            .build();
  }

  private void expectProblem(String url, int status, String type) throws Exception {
    mvc.perform(get(url + "?q=1"))
        .andExpect(status().is(status))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(BASE + type))
        .andExpect(jsonPath("$.status").value(status))
        .andExpect(jsonPath("$.title").isString())
        .andExpect(jsonPath("$.detail").isString())
        .andExpect(jsonPath("$.instance").value(url))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  @Test
  void mapsDomainExceptionsToStatusAndType() throws Exception {
    expectProblem("/t/not-found", 404, "not-found");
    expectProblem("/t/duplicate", 409, "duplicate-key");
    expectProblem("/t/version", 409, "version-conflict");
    expectProblem("/t/optimistic", 409, "version-conflict");
    expectProblem("/t/unique", 409, "duplicate-key");
    expectProblem("/t/limit", 409, "limit-reached");
    expectProblem("/t/too-large", 413, "payload-too-large");
    expectProblem("/t/malformed", 400, "malformed-request");
  }

  @Test
  void otherIntegrityViolationsAreNotDuplicateKeys() throws Exception {
    expectProblem("/t/fk", 500, "internal");
  }

  @Test
  void tooLargeBodyInsideAnUnreadableBodyIs413() throws Exception {
    expectProblem("/t/too-large-body", 413, "payload-too-large");
  }

  @Test
  void frameworkStatusExceptionsKeepTheirMeaning() throws Exception {
    expectProblem("/t/status-404", 404, "not-found");
    expectProblem("/t/not-acceptable", 400, "malformed-request");
  }

  @Test
  void unsupportedMethodIsNotFound() throws Exception {
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/t/page"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(BASE + "not-found"));
  }

  @Test
  void patchFieldErrorsNameTheJsonProperty() throws Exception {
    mvc.perform(
            post("/t/patch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":null,\"version\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(BASE + "validation"))
        .andExpect(jsonPath("$.errors[0].field").value("name"));
    String longDescription = "\uD83D\uDE80".repeat(501);
    mvc.perform(
            post("/t/patch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"" + longDescription + "\",\"version\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("description"));
  }

  @Test
  void queryParameterConstraintNamesTheParameter() throws Exception {
    mvc.perform(get("/t/size?size=0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(BASE + "validation"))
        .andExpect(jsonPath("$.errors[0].field").value("size"));
  }

  @Test
  void unexpectedErrorIsLoggedWithTheCorrelationId(
      org.springframework.boot.test.system.CapturedOutput output) throws Exception {
    org.slf4j.MDC.put(RequestIds.MDC_KEY, "rid-500");
    try {
      mvc.perform(get("/t/boom")).andExpect(status().isInternalServerError());
    } finally {
      org.slf4j.MDC.remove(RequestIds.MDC_KEY);
    }
    assertThat(output.getOut()).contains("rid-500");
  }

  @Test
  void unexpectedErrorIs500WithoutInternals() throws Exception {
    expectProblem("/t/boom", 500, "internal");
    mvc.perform(get("/t/boom"))
        .andExpect(content().string(not(containsString("secret internal detail"))))
        .andExpect(content().string(not(containsString("IllegalStateException"))));
  }

  @Test
  void beanValidationGivesFieldErrors() throws Exception {
    mvc.perform(post("/t/body").contentType(MediaType.APPLICATION_JSON).content("{\"key\":\"A\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(BASE + "validation"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.instance").value("/t/body"))
        .andExpect(jsonPath("$.errors[0].field").value("key"))
        .andExpect(jsonPath("$.errors[0].message").isString());
  }

  @Test
  void programmaticFieldErrorUsesTheSameShape() throws Exception {
    mvc.perform(get("/t/field"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(BASE + "validation"))
        .andExpect(jsonPath("$.errors[0].field").value("sort"));
  }

  @Test
  void invalidJsonWrongTypeAndWrongContentTypeAreMalformed() throws Exception {
    mvc.perform(post("/t/body").contentType(MediaType.APPLICATION_JSON).content("{nope"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(BASE + "malformed-request"));
    mvc.perform(
            post("/t/body")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"ab\",\"version\":\"1\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(BASE + "malformed-request"));
    mvc.perform(post("/t/body").contentType(MediaType.TEXT_PLAIN).content("{\"key\":\"ab\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(BASE + "malformed-request"));
    mvc.perform(post("/t/body"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(BASE + "malformed-request"));
  }

  @Test
  void pathIdThatIsNotAUuidAndNonNumericQueryAreMalformed() throws Exception {
    mvc.perform(get("/t/id/not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(BASE + "malformed-request"));
    mvc.perform(get("/t/page?page=abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(BASE + "malformed-request"));
  }

  @Test
  void unknownRouteIsNotFound() throws Exception {
    mvc.perform(get("/nothing/here"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(BASE + "not-found"));
  }
}
