package com.example.featureflags.auth;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Spec 5.1: in the {@code prod} profile, WARN when a dev default password or secret is used. */
@Component
@Profile("prod")
public class DefaultSecretsWarning {

  private static final Logger log = LoggerFactory.getLogger(DefaultSecretsWarning.class);
  static final String DEV_ADMIN_PASSWORD = "admin123";
  static final String DEV_JWT_SECRET = "change-me-to-a-32-byte-minimum-secret!!";
  static final String DEV_ORDER_SERVICE_SECRET = "order-service-dev-secret";

  private final ClientRegistrationProperties props;

  public DefaultSecretsWarning(ClientRegistrationProperties props) {
    this.props = props;
  }

  /** Names of the settings that still hold a dev default (never the values). */
  static List<String> defaultsInUse(ClientRegistrationProperties props) {
    List<String> names = new ArrayList<>();
    if (DEV_ADMIN_PASSWORD.equals(props.adminPassword())) {
      names.add("FF_ADMIN_PASSWORD");
    }
    if (DEV_JWT_SECRET.equals(props.jwtSecret())) {
      names.add("FF_JWT_SECRET");
    }
    for (ClientRegistration c : props.clients()) {
      if (DEV_ORDER_SERVICE_SECRET.equals(c.clientSecret())) {
        names.add("secret of client " + c.clientId());
      }
    }
    return names;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void warn() {
    for (String name : defaultsInUse(props)) {
      log.warn("Default dev credential in use in prod: {}. Set a real value.", name);
    }
  }
}
