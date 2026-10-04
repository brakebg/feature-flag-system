package com.example.featureflags;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point of the Feature Flag Service backend. */
@SpringBootApplication
public class FeatureFlagServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(FeatureFlagServiceApplication.class, args);
  }
}
