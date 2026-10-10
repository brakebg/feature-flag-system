package com.example.featureflags;

import static com.example.featureflags.support.Ids.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.featureflags.support.PostgresContainerConfig;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Spec 4.1, 4.3: Flyway migrations on an empty database (12.2 M2 "Done when"). */
class MigrationIT {

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

  private static JdbcTemplate freshDatabase(String name, String... locations) {
    new JdbcTemplate(ds(PG.getDatabaseName())).execute("CREATE DATABASE " + name);
    DriverManagerDataSource ds = ds(name);
    Flyway.configure().dataSource(ds).locations(locations).load().migrate();
    return new JdbcTemplate(ds);
  }

  private static DriverManagerDataSource ds(String db) {
    String url = PG.getJdbcUrl().replace("/" + PG.getDatabaseName(), "/" + db);
    return new DriverManagerDataSource(url, PG.getUsername(), PG.getPassword());
  }

  private static final String[] PROD = {"classpath:db/migration/common"};
  private static final String[] DEV = {
    "classpath:db/migration/common", "classpath:db/migration/dev"
  };

  @Test
  void v1CreatesTheThreeTablesOnAnEmptyDatabase() {
    JdbcTemplate db = freshDatabase("schema_only", PROD);

    List<String> tables =
        db.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'"
                + " AND table_name <> 'flyway_schema_history' ORDER BY table_name",
            String.class);
    assertThat(tables).containsExactly("audit_event", "feature_flag", "flag_group");
    assertThat(db.queryForObject("SELECT count(*) FROM flag_group", Integer.class)).isZero();
    assertThat(db.queryForList("SELECT version FROM flyway_schema_history", String.class))
        .containsExactly("1");
  }

  @Test
  void columnsMatchSpecTypesAndLengths() {
    JdbcTemplate db = freshDatabase("columns", PROD);

    List<Map<String, Object>> cols =
        db.queryForList(
            "SELECT table_name, column_name, data_type, character_maximum_length, is_nullable"
                + " FROM information_schema.columns WHERE table_schema = 'public'"
                + " AND table_name IN ('flag_group','feature_flag','audit_event')");
    assertThat(col(cols, "flag_group", "key")).containsEntry("character_maximum_length", 50);
    assertThat(col(cols, "flag_group", "name")).containsEntry("character_maximum_length", 100);
    assertThat(col(cols, "flag_group", "description"))
        .containsEntry("character_maximum_length", 500)
        .containsEntry("is_nullable", "YES");
    assertThat(col(cols, "flag_group", "created_by"))
        .containsEntry("character_maximum_length", 100)
        .containsEntry("is_nullable", "NO");
    assertThat(col(cols, "flag_group", "version")).containsEntry("data_type", "bigint");
    assertThat(col(cols, "flag_group", "created_at"))
        .containsEntry("data_type", "timestamp with time zone");
    assertThat(col(cols, "feature_flag", "key")).containsEntry("character_maximum_length", 50);
    assertThat(col(cols, "feature_flag", "enabled")).containsEntry("data_type", "boolean");
    assertThat(col(cols, "feature_flag", "group_id")).containsEntry("data_type", "uuid");
    assertThat(col(cols, "audit_event", "id")).containsEntry("data_type", "bigint");
    assertThat(col(cols, "audit_event", "action")).containsEntry("character_maximum_length", 30);
    assertThat(col(cols, "audit_event", "target_key"))
        .containsEntry("character_maximum_length", 101);
    assertThat(col(cols, "audit_event", "details"))
        .containsEntry("data_type", "jsonb")
        .containsEntry("is_nullable", "YES");
  }

  private static Map<String, Object> col(List<Map<String, Object>> cols, String t, String c) {
    return cols.stream()
        .filter(m -> t.equals(m.get("table_name")) && c.equals(m.get("column_name")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing column " + t + "." + c));
  }

  @Test
  void deletingAGroupCascadesToItsFlags() {
    JdbcTemplate db = freshDatabase("cascade", PROD);
    UUID g = id(1);
    insertGroup(db, g, "orders");
    insertFlag(db, g, "new-checkout");
    insertFlag(db, g, "split-payments");

    db.update("DELETE FROM flag_group WHERE id = ?", g);

    assertThat(db.queryForObject("SELECT count(*) FROM feature_flag", Integer.class)).isZero();
  }

  @Test
  void groupKeyIsUniqueAndFlagKeyIsUniqueWithinItsGroupOnly() {
    JdbcTemplate db = freshDatabase("uniques", PROD);
    UUID a = id(1);
    UUID b = id(2);
    insertGroup(db, a, "orders");
    insertGroup(db, b, "payments");
    insertFlag(db, a, "new-checkout");
    insertFlag(db, b, "new-checkout");

    assertThatThrownBy(() -> insertGroup(db, id(3), "orders"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertFlag(db, a, "new-checkout"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void auditRowsHaveNoForeignKeyAndSurviveDeletions() {
    JdbcTemplate db = freshDatabase("audit_fk", PROD);
    Integer fks =
        db.queryForObject(
            "SELECT count(*) FROM information_schema.table_constraints"
                + " WHERE table_name = 'audit_event' AND constraint_type = 'FOREIGN KEY'",
            Integer.class);
    assertThat(fks).isZero();

    UUID g = id(1);
    insertGroup(db, g, "orders");
    insertFlag(db, g, "new-checkout");
    db.update(
        "INSERT INTO audit_event (occurred_at, actor, action, target_key, details)"
            + " VALUES (now(), 'admin', 'FLAG_CREATED', 'orders.new-checkout',"
            + " '{\"enabled\":false}'::jsonb)");
    db.update("DELETE FROM flag_group WHERE id = ?", g);
    assertThat(db.queryForObject("SELECT count(*) FROM audit_event", Integer.class)).isEqualTo(1);
  }

  @Test
  void requiredColumnsAreNotNull() {
    JdbcTemplate db = freshDatabase("not_null", PROD);
    List<String> nullable =
        db.queryForList(
            "SELECT table_name || '.' || column_name FROM information_schema.columns"
                + " WHERE table_schema = 'public' AND is_nullable = 'YES'"
                + " AND table_name IN ('flag_group','feature_flag','audit_event')"
                + " ORDER BY 1",
            String.class);
    assertThat(nullable)
        .containsExactly(
            "audit_event.details", "feature_flag.description", "flag_group.description");
  }

  @Test
  void specIndexesExist() {
    JdbcTemplate db = freshDatabase("indexes", PROD);
    List<String> defs =
        db.queryForList(
            "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public'", String.class);
    assertThat(defs).anyMatch(d -> d.contains("ON public.feature_flag USING btree (group_id)"));
    assertThat(defs)
        .anyMatch(d -> d.contains("ON public.audit_event USING btree (occurred_at DESC"));
  }

  @Test
  void v2SeedsOrdersOnlyInDev() {
    JdbcTemplate dev = freshDatabase("dev_seed", DEV);

    assertThat(dev.queryForMap("SELECT key, name, created_by, updated_by, version FROM flag_group"))
        .containsEntry("key", "orders")
        .containsEntry("created_by", "system")
        .containsEntry("updated_by", "system")
        .containsEntry("version", 0L);
    List<Map<String, Object>> flags =
        dev.queryForList("SELECT key, enabled, created_by FROM feature_flag ORDER BY key");
    assertThat(flags)
        .extracting(m -> m.get("key") + "=" + m.get("enabled") + "/" + m.get("created_by"))
        .containsExactly("new-checkout=true/system", "split-payments=false/system");

    JdbcTemplate prod = freshDatabase("prod_no_seed", PROD);
    assertThat(prod.queryForObject("SELECT count(*) FROM flag_group", Integer.class)).isZero();
  }

  private static void insertGroup(JdbcTemplate db, UUID id, String key) {
    db.update(
        "INSERT INTO flag_group (id, key, name, created_by, updated_at, updated_by, version)"
            + " VALUES (?, ?, ?, 'test', now(), 'test', 0)",
        id,
        key,
        key);
  }

  private static void insertFlag(JdbcTemplate db, UUID group, String key) {
    db.update(
        "INSERT INTO feature_flag (id, group_id, key, created_at, created_by, updated_at,"
            + " updated_by, version) VALUES (?, ?, ?, now(), 'test', now(), 'test', 0)",
        UUID.nameUUIDFromBytes(
            (group + "/" + key).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
        group,
        key);
  }
}
