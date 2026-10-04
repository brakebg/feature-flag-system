package com.example.featureflags.evaluation;

import static com.example.featureflags.support.AdminClient.PROBLEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.featureflags.flag.FlagService;
import com.example.featureflags.support.AdminApiTest;
import com.example.featureflags.support.IntegrationTest;
import com.example.featureflags.support.SecurityTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/** Spec 7: Evaluation API, cache behaviour and ETags, through the real stack. */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class EvaluationIT extends AdminApiTest {

  @MockitoSpyBean EvaluationQueries queries;
  @Autowired FlagCacheService cache;
  @Autowired CacheReconciliationJob reconciliation;
  @Autowired CacheWarmUp warmUp;
  @Autowired FlagService flagService;
  @Autowired TransactionTemplate tx;
  @Autowired MeterRegistry meters;

  String clientToken;
  String groupId;
  String flagId;

  @BeforeEach
  void data() throws Exception {
    clientToken = SecurityTestSupport.clientToken(mvc, json);
    groupId = admin.createGroup("orders", "Orders").get("id").asText();
    flagId = admin.createFlag(groupId, "new-checkout", true).get("id").asText();
    admin.createFlag(groupId, "split-payments", false);
    clearInvocations(queries);
  }

  @AfterEach
  void restore() {
    reset(queries);
    SecurityContextHolder.clearContext();
  }

  private ResultActions evaluate(String path) throws Exception {
    return evaluate(path, null);
  }

  private ResultActions evaluate(String path, String ifNoneMatch) throws Exception {
    var req =
        get(java.net.URI.create("/api/v1/evaluate" + path))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + clientToken);
    if (ifNoneMatch != null) {
      req.header(HttpHeaders.IF_NONE_MATCH, ifNoneMatch);
    }
    return mvc.perform(req);
  }

  private void verifyNoQueries() {
    verify(queries, never()).findEnabled(anyString(), anyString());
    verify(queries, never()).findGroup(anyString());
    verify(queries, never()).findAllRows();
  }

  private String etag(ResultActions r) {
    return r.andReturn().getResponse().getHeader(HttpHeaders.ETAG);
  }

  @Test
  @Tag("AC-EVAL-3")
  void clientTokenReadsAllThreeEndpoints() throws Exception {
    ResultActions all =
        evaluate("/flags")
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(header().string("Cache-Control", "no-cache"))
            .andExpect(jsonPath("$.flags['orders.new-checkout']").value(true))
            .andExpect(jsonPath("$.flags['orders.split-payments']").value(false));
    long revision =
        json.readTree(all.andReturn().getResponse().getContentAsString()).get("revision").asLong();
    assertThat(etag(all)).isEqualTo("\"" + revision + "\"");

    evaluate("/groups/orders")
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"" + revision + "\""))
        .andExpect(header().string("Cache-Control", "no-cache"))
        .andExpect(jsonPath("$.group").value("orders"))
        .andExpect(jsonPath("$.flags['new-checkout']").value(true))
        .andExpect(jsonPath("$.flags['split-payments']").value(false))
        .andExpect(jsonPath("$.revision").value(revision));

    evaluate("/flags/orders/new-checkout")
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"" + revision + "\""))
        .andExpect(jsonPath("$.key").value("orders.new-checkout"))
        .andExpect(jsonPath("$.enabled").value(true));
  }

  @Test
  void emptyGroupHasAnEmptyFlagsObject() throws Exception {
    admin.createGroup("payments", "Payments");
    evaluate("/groups/payments")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.flags").isEmpty());
  }

  @Test
  @Tag("AC-EVAL-7")
  @Tag("ERR-GET-/evaluate/groups/{groupKey}-404")
  @Tag("ERR-GET-/evaluate/flags/{groupKey}/{flagKey}-404")
  void unknownGroupOrFlagIs404() throws Exception {
    for (String path :
        new String[] {
          "/groups/nope",
          "/flags/orders/nope",
          "/flags/nope/new-checkout",
          "/groups/Has-Upper",
          "/flags/orders/x"
        }) {
      evaluate(path)
          .andExpect(status().isNotFound())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.type").value(PROBLEM + "not-found"));
    }
  }

  @Test
  @Tag("AC-EVAL-5")
  @Tag("AC-CACHE-4")
  void toggleIsVisibleOnTheNextCallWithoutQueries() throws Exception {
    admin
        .postJson("/flags/" + flagId + "/toggle", "{\"enabled\":false}")
        .andExpect(status().isOk());
    clearInvocations(queries);

    evaluate("/flags/orders/new-checkout").andExpect(jsonPath("$.enabled").value(false));
    evaluate("/groups/orders").andExpect(jsonPath("$.flags['new-checkout']").value(false));
    evaluate("/flags").andExpect(jsonPath("$.flags['orders.new-checkout']").value(false));
    verifyNoQueries();
  }

  @Test
  @Tag("AC-CACHE-4")
  void createAndDeleteOfAFlagAndDeleteOfAGroupWithoutQueries() throws Exception {
    String created = admin.createFlag(groupId, "brand-new", true).get("id").asText();
    clearInvocations(queries);
    evaluate("/flags/orders/brand-new").andExpect(jsonPath("$.enabled").value(true));
    verifyNoQueries();

    admin.delete("/flags/" + created).andExpect(status().isNoContent());
    clearInvocations(queries);
    evaluate("/flags/orders/brand-new").andExpect(status().isNotFound());
    evaluate("/flags").andExpect(jsonPath("$.flags['orders.brand-new']").doesNotExist());
    verifyNoQueries();

    admin.delete("/groups/" + groupId).andExpect(status().isNoContent());
    clearInvocations(queries);
    evaluate("/groups/orders").andExpect(status().isNotFound());
    evaluate("/flags/orders/new-checkout").andExpect(status().isNotFound());
    evaluate("/flags").andExpect(jsonPath("$.flags").isEmpty());
    verifyNoQueries();
  }

  @Test
  @Tag("AC-CACHE-1")
  void thousandCallsAfterWarmUpRunNoQueries() throws Exception {
    cache.reloadAll();
    clearInvocations(queries);
    for (int i = 0; i < 1000; i++) {
      String path =
          switch (i % 3) {
            case 0 -> "/flags";
            case 1 -> "/groups/orders";
            default -> "/flags/orders/split-payments";
          };
      evaluate(path).andExpect(status().isOk());
    }
    verifyNoQueries();
  }

  @Test
  @Tag("AC-CACHE-3")
  void unknownKeyTwiceRunsOneQuery() throws Exception {
    evaluate("/flags/orders/ghost").andExpect(status().isNotFound());
    evaluate("/flags/orders/ghost").andExpect(status().isNotFound());
    verify(queries, times(1)).findEnabled("orders", "ghost");
  }

  @Test
  @Tag("AC-EVAL-6")
  void ifNoneMatchGives304UntilADataChange() throws Exception {
    String tag = etag(evaluate("/flags").andExpect(status().isOk()));

    for (String path : new String[] {"/flags", "/groups/orders", "/flags/orders/new-checkout"}) {
      evaluate(path, tag)
          .andExpect(status().isNotModified())
          .andExpect(header().string("ETag", tag))
          .andExpect(header().string("Cache-Control", "no-cache"))
          .andExpect(content().string(""));
    }
    evaluate("/flags", "W/" + tag + ", \"other\"").andExpect(status().isNotModified());

    // A no-op write and a failed write keep the ETag.
    admin.postJson("/flags/" + flagId + "/toggle", "{\"enabled\":true}").andExpect(status().isOk());
    admin
        .patchJson("/flags/" + flagId, "{\"description\":\"x\",\"version\":99}")
        .andExpect(status().isConflict());
    admin
        .postJson("/groups", "{\"key\":\"orders\",\"name\":\"dup\"}")
        .andExpect(status().isConflict());
    evaluate("/flags", tag).andExpect(status().isNotModified());

    // A real change moves the revision on.
    admin
        .patchJson("/groups/" + groupId, "{\"name\":\"Shop\",\"version\":0}")
        .andExpect(status().isOk());
    String next = etag(evaluate("/flags", tag).andExpect(status().isOk()));
    assertThat(next).isNotEqualTo(tag);
    assertThat(Long.parseLong(next.replace("\"", "")))
        .isEqualTo(Long.parseLong(tag.replace("\"", "")) + 1);
  }

  @Test
  @Tag("AC-CACHE-5")
  void rolledBackWriteLeavesTheCacheUnchanged() throws Exception {
    long revision = cache.revision();
    TestingAuthenticationToken auth = new TestingAuthenticationToken("admin", null, "SCOPE_admin");
    auth.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(auth);

    tx.executeWithoutResult(
        status -> {
          flagService.toggle(UUID.fromString(flagId), false);
          status.setRollbackOnly();
        });

    evaluate("/flags/orders/new-checkout").andExpect(jsonPath("$.enabled").value(true));
    assertThat(cache.revision()).isEqualTo(revision);
  }

  @Test
  @Tag("AC-CACHE-7")
  void directDatabaseChangeIsFixedByReconciliation(CapturedOutput output) throws Exception {
    String before = etag(evaluate("/flags/orders/new-checkout"));
    jdbc.update("UPDATE feature_flag SET enabled = false WHERE key = 'new-checkout'");

    evaluate("/flags/orders/new-checkout").andExpect(jsonPath("$.enabled").value(true));

    assertThat(reconciliation.reconcile()).isGreaterThan(0);
    String after =
        etag(evaluate("/flags/orders/new-checkout").andExpect(jsonPath("$.enabled").value(false)));
    assertThat(after).isNotEqualTo(before);
    assertThat(output.getOut())
        .contains("WARN")
        .contains("Cache drift fixed: key=orders.new-checkout");
    assertThat(meters.get("ff_cache_reconcile_drift_total").counter().count()).isGreaterThan(0);
    assertThat(meters.get("ff_cache_reconcile_last_success_seconds").gauge().value())
        .isGreaterThan(0);
  }

  @Test
  @Tag("AC-CACHE-8")
  void reconciliationWithoutDifferencesLogsNoWarnAndKeepsTheETag(CapturedOutput output)
      throws Exception {
    String before = etag(evaluate("/flags"));
    int mark = output.getOut().length();
    assertThat(reconciliation.reconcile()).isZero();
    assertThat(output.getOut().substring(mark))
        .doesNotContain("Cache drift fixed")
        .doesNotContain("\"WARN\"");
    assertThat(etag(evaluate("/flags"))).isEqualTo(before);
  }

  @Test
  void failedReconciliationLeavesTheCache(CapturedOutput output) throws Exception {
    doAnswer(
            inv -> {
              throw new IllegalStateException("db down");
            })
        .when(queries)
        .findAllRows();
    assertThat(reconciliation.reconcile()).isEqualTo(-1);
    assertThat(output.getOut()).contains("Cache reconciliation failed");
    evaluate("/flags/orders/new-checkout").andExpect(jsonPath("$.enabled").value(true));
  }

  @Test
  @Tag("AC-CACHE-6")
  void readinessIsDownUntilWarmUpHasFinished() throws Exception {
    CountDownLatch inWarmUp = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    doAnswer(
            inv -> {
              inWarmUp.countDown();
              release.await(30, TimeUnit.SECONDS);
              return java.util.List
                  .of(); // the spy wraps an interface proxy: no real method to call
            })
        .when(queries)
        .findAllRows();
    Thread t = new Thread(warmUp::warmUp);
    t.start();
    try {
      assertThat(inWarmUp.await(30, TimeUnit.SECONDS)).isTrue();
      mvc.perform(get("/actuator/health/readiness"))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.status").value("DOWN"));
      assertThat(meters.get("ff_readiness_up").gauge().value()).isZero();
    } finally {
      release.countDown();
    }
    await().atMost(Duration.ofSeconds(30)).until(() -> !t.isAlive());
    mvc.perform(get("/actuator/health/readiness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    assertThat(meters.get("ff_readiness_up").gauge().value()).isEqualTo(1);
    verify(queries, atLeastOnce()).findAllRows();
  }

  @Test
  void evaluationsAreCountedPerEndpointResultAndClient() throws Exception {
    evaluate("/flags/orders/new-checkout");
    evaluate("/flags/orders/ghost");
    assertThat(
            meters
                .get("ff_evaluations_total")
                .tag("endpoint", "flag")
                .tag("result", "found")
                .tag("client", "order-service")
                .counter()
                .count())
        .isGreaterThanOrEqualTo(1);
    assertThat(
            meters
                .get("ff_evaluations_total")
                .tag("endpoint", "flag")
                .tag("result", "not_found")
                .tag("client", "order-service")
                .counter()
                .count())
        .isGreaterThanOrEqualTo(1);
  }

  @Test
  void prometheusExposesTheSpecMetricNames() throws Exception {
    evaluate("/flags");
    String text =
        mvc.perform(
                get("/actuator/prometheus")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + SecurityTestSupport.adminToken(mvc, json)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(text)
        .contains("cache_gets_total{cache=\"flagCache\"")
        .contains("cache=\"groupCache\"")
        .contains("cache=\"allFlagsCache\"")
        .contains("ff_evaluations_total{")
        .contains("ff_admin_writes_total{action=\"FLAG_CREATED\"")
        .contains("ff_readiness_up")
        .contains("http_server_requests_seconds_bucket");
  }

  @Test
  void bodyRevisionEqualsTheETag() throws Exception {
    ResultActions r = evaluate("/groups/orders");
    JsonNode body = json.readTree(r.andReturn().getResponse().getContentAsString());
    assertThat(etag(r)).isEqualTo("\"" + body.get("revision").asLong() + "\"");
  }
}
