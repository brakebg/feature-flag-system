package com.example.featureflags.evaluation;

import com.example.featureflags.common.NotFoundException;
import com.example.featureflags.common.RequestIdFilter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spec 7.1: read-only Evaluation API, answered from the flag cache. Every 200 and 304 carries
 * {@code ETag: "<revision>"} and {@code Cache-Control: no-cache}; a matching {@code If-None-Match}
 * gets 304 without a body.
 */
@RestController
@RequestMapping("/api/v1/evaluate")
public class EvaluationController {

  private final FlagCacheService cache;
  private final MeterRegistry meters;

  public EvaluationController(FlagCacheService cache, MeterRegistry meters) {
    this.cache = cache;
    this.meters = meters;
  }

  public record AllFlags(Map<String, Boolean> flags, long revision) {}

  public record GroupFlags(String group, Map<String, Boolean> flags, long revision) {}

  public record OneFlag(String key, boolean enabled) {}

  @GetMapping("/flags")
  public ResponseEntity<AllFlags> all(
      @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
      Principal client,
      HttpServletRequest request) {
    long revision = cache.revision();
    Map<String, Boolean> flags = cache.all();
    count("all", true, client, request);
    return respond(revision, ifNoneMatch, new AllFlags(flags, revision));
  }

  @GetMapping("/groups/{groupKey}")
  public ResponseEntity<GroupFlags> group(
      @PathVariable String groupKey,
      @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
      Principal client,
      HttpServletRequest request) {
    long revision = cache.revision();
    Optional<Map<String, Boolean>> flags = cache.group(groupKey);
    count("group", flags.isPresent(), client, request);
    Map<String, Boolean> found =
        flags.orElseThrow(() -> new NotFoundException("No group " + groupKey));
    return respond(revision, ifNoneMatch, new GroupFlags(groupKey, found, revision));
  }

  @GetMapping("/flags/{groupKey}/{flagKey}")
  public ResponseEntity<OneFlag> flag(
      @PathVariable String groupKey,
      @PathVariable String flagKey,
      @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
      Principal client,
      HttpServletRequest request) {
    long revision = cache.revision();
    Optional<Boolean> enabled = cache.flag(groupKey, flagKey);
    count("flag", enabled.isPresent(), client, request);
    boolean value =
        enabled.orElseThrow(() -> new NotFoundException("No flag " + groupKey + "." + flagKey));
    return respond(revision, ifNoneMatch, new OneFlag(groupKey + "." + flagKey, value));
  }

  private void count(String endpoint, boolean found, Principal client, HttpServletRequest req) {
    String name = client == null ? "unknown" : client.getName();
    req.setAttribute(RequestIdFilter.CLIENT_ATTRIBUTE, name);
    meters
        .counter(
            "ff_evaluations_total",
            "endpoint",
            endpoint,
            "result",
            found ? "found" : "not_found",
            "client",
            name)
        .increment();
  }

  private static <T> ResponseEntity<T> respond(long revision, String ifNoneMatch, T body) {
    String etag = "\"" + revision + "\"";
    if (matches(ifNoneMatch, etag)) {
      return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
          .eTag(etag)
          .cacheControl(CacheControl.noCache())
          .build();
    }
    return ResponseEntity.ok().eTag(etag).cacheControl(CacheControl.noCache()).body(body);
  }

  /** {@code If-None-Match} is a comma-separated list; weak tags compare equal (RFC 9110). */
  static boolean matches(String ifNoneMatch, String etag) {
    if (ifNoneMatch == null) {
      return false;
    }
    for (String tag : ifNoneMatch.split(",")) {
      String t = tag.strip();
      if (t.startsWith("W/")) {
        t = t.substring(2);
      }
      if (t.equals(etag) || t.equals("*")) {
        return true;
      }
    }
    return false;
  }
}
