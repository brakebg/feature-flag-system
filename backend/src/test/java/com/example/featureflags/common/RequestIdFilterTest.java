package com.example.featureflags.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Spec 9.3: X-Request-Id echoed or generated; every request logged with the id. */
@ExtendWith(OutputCaptureExtension.class)
class RequestIdFilterTest {

  private final RequestIdFilter filter = new RequestIdFilter();

  @Test
  void echoesTheClientValueUnchanged() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/admin/groups");
    req.addHeader("X-Request-Id", "client-id-42");
    MockHttpServletResponse res = new MockHttpServletResponse();
    AtomicReference<String> inChain = new AtomicReference<>();

    filter.doFilter(req, res, (rq, rs) -> inChain.set(MDC.get("requestId")));

    assertThat(res.getHeader("X-Request-Id")).isEqualTo("client-id-42");
    assertThat(inChain.get()).isEqualTo("client-id-42");
    assertThat(MDC.get("requestId")).isNull();
  }

  @Test
  void generatesAUuidWhenMissing() throws Exception {
    MockHttpServletResponse res = new MockHttpServletResponse();
    filter.doFilter(new MockHttpServletRequest("GET", "/x"), res, new MockFilterChain());

    assertThat(res.getHeader("X-Request-Id"))
        .matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
  }

  @Test
  void logsMethodPathStatusDurationAndId(CapturedOutput output) throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
    req.setQueryString("secret=1");
    req.addHeader("X-Request-Id", "rid-7");
    MockHttpServletResponse res = new MockHttpServletResponse();

    filter.doFilter(req, res, (rq, rs) -> ((MockHttpServletResponse) rs).setStatus(401));

    assertThat(output.getOut())
        .contains("POST")
        .contains("/api/v1/auth/login")
        .contains("401")
        .contains("rid-7")
        .containsPattern("\\d+ ms")
        .doesNotContain("secret=1");
  }
}
