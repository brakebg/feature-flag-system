package com.example.featureflags.common;

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

    @GetMapping("/t/{what}")
    String raise(@PathVariable String what) {
      throw switch (what) {
        case "not-found" -> new NotFoundException("no group 1");
        case "duplicate" -> new DuplicateKeyException("key", "Key already exists");
        case "version" -> new VersionConflictException();
        case "optimistic" -> new ObjectOptimisticLockingFailureException(Object.class, "1");
        case "unique" ->
            new DataIntegrityViolationException("x", new java.sql.SQLException("dup", "23505"));
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
