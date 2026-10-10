package com.example.featureflags;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.featureflags.support.PostgresContainerConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Spec 4.3: the V2 seed runs only in the {@code dev} profile, through the real config files. */
class FlywayProfileIT {

  private static final PostgreSQLContainer PG =
      new PostgreSQLContainer(PostgresContainerConfig.POSTGRES);

  @BeforeAll
  static void start() {
    PG.start();
  }

  @AfterAll
  static void stop() {
    PG.stop();
  }

  private static ConfigurableApplicationContext start(String database, String... profiles) {
    new JdbcTemplate(
            new org.springframework.jdbc.datasource.DriverManagerDataSource(
                PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()))
        .execute("CREATE DATABASE " + database);
    String url = PG.getJdbcUrl().replace("/" + PG.getDatabaseName(), "/" + database);
    return new SpringApplicationBuilder(FeatureFlagServiceApplication.class)
        .web(WebApplicationType.NONE)
        .profiles(profiles)
        .properties(
            "FF_DB_URL=" + url,
            "FF_DB_USER=" + PG.getUsername(),
            "FF_DB_PASSWORD=" + PG.getPassword())
        .run();
  }

  @Test
  @org.junit.jupiter.api.extension.ExtendWith(
      org.springframework.boot.test.system.OutputCaptureExtension.class)
  void prodProfileRequiresHttpsByDefaultAndWarnsAboutDevSecrets(
      org.springframework.boot.test.system.CapturedOutput output) {
    try (ConfigurableApplicationContext ctx = start("profile_prod_https", "prod")) {
      assertThat(ctx.getEnvironment().getProperty("featureflags.require-https", Boolean.class))
          .isTrue();
    }
    assertThat(output.getOut())
        .contains("Default dev credential in use in prod: FF_ADMIN_PASSWORD")
        .contains("Default dev credential in use in prod: FF_JWT_SECRET");
  }

  @Test
  void devProfileDoesNotRequireHttpsByDefault() {
    try (ConfigurableApplicationContext ctx = start("profile_dev_https", "dev")) {
      assertThat(ctx.getEnvironment().getProperty("featureflags.require-https", Boolean.class))
          .isFalse();
    }
  }

  private static int groupsAfterStartup(String database, String... profiles) {
    new JdbcTemplate(
            new org.springframework.jdbc.datasource.DriverManagerDataSource(
                PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()))
        .execute("CREATE DATABASE " + database);
    String url = PG.getJdbcUrl().replace("/" + PG.getDatabaseName(), "/" + database);
    SpringApplicationBuilder app =
        new SpringApplicationBuilder(FeatureFlagServiceApplication.class)
            .web(WebApplicationType.NONE)
            .properties(
                "FF_DB_URL=" + url,
                "FF_DB_USER=" + PG.getUsername(),
                "FF_DB_PASSWORD=" + PG.getPassword());
    if (profiles.length > 0) {
      app.profiles(profiles);
    }
    try (ConfigurableApplicationContext ctx = app.run()) {
      return ctx.getBean(JdbcTemplate.class)
          .queryForObject("SELECT count(*) FROM flag_group WHERE key = 'orders'", Integer.class);
    }
  }

  @Test
  void devProfileSeedsOrders() {
    assertThat(groupsAfterStartup("profile_dev", "dev")).isEqualTo(1);
  }

  @Test
  void defaultProfileIsDevAndSeeds() {
    assertThat(groupsAfterStartup("profile_default")).isEqualTo(1);
  }

  @Test
  void prodProfileHasNoSeed() {
    assertThat(groupsAfterStartup("profile_prod", "prod")).isZero();
  }
}
