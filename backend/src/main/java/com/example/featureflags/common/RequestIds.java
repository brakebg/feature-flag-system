package com.example.featureflags.common;

/** Names for the request correlation id (spec 9.3). */
public final class RequestIds {

  public static final String HEADER = "X-Request-Id";
  public static final String MDC_KEY = "requestId";

  private RequestIds() {}
}
