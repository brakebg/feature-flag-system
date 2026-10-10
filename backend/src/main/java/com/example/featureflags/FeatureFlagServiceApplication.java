package com.example.featureflags;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Entry point of the Feature Flag Service backend. */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class FeatureFlagServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(FeatureFlagServiceApplication.class, args);
  }
}
