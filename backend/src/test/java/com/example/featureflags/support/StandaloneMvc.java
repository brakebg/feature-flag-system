package com.example.featureflags.support;

import com.example.featureflags.common.GlobalExceptionHandler;
import com.example.featureflags.common.JacksonConfig;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * MockMvc around one controller, with the real JSON rules and error handler (no Spring context).
 */
public final class StandaloneMvc {

  private StandaloneMvc() {}

  public static MockMvc of(Object... controllers) {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    return MockMvcBuilders.standaloneSetup(controllers)
        .setControllerAdvice(new GlobalExceptionHandler())
        .setMessageConverters(
            new MappingJackson2HttpMessageConverter(
                new Jackson2ObjectMapperBuilder()
                    .featuresToDisable(
                        com.fasterxml.jackson.databind.SerializationFeature
                            .WRITE_DATES_AS_TIMESTAMPS)
                    .postConfigurer(JacksonConfig::strict)
                    .build()))
        .setValidator(validator)
        .build();
  }
}
