package com.example.featureflags.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One PostgreSQL 16 container per test run, shared by all Spring contexts; the image tag is pinned
 * (spec 12.4 step 6).
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfig {

  public static final DockerImageName POSTGRES = DockerImageName.parse("postgres:16.15-alpine");

  private static final PostgreSQLContainer<?> CONTAINER = new PostgreSQLContainer<>(POSTGRES);

  @Bean
  @ServiceConnection
  PostgreSQLContainer<?> postgres() {
    return CONTAINER;
  }
}
