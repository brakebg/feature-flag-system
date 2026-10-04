package com.example.featureflags.auth;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

/**
 * Spec 5.1: consumer clients can be added through {@code FF_AUTH_CLIENTS_<n>_CLIENT_ID}, {@code
 * _CLIENT_SECRET} and {@code _SCOPES} (comma-separated) environment variables. When any are set,
 * they define the client list (they replace the list from the configuration file).
 */
public class ClientEnvironment implements EnvironmentPostProcessor {

  private static final Pattern NAME =
      Pattern.compile("^FF_AUTH_CLIENTS_(\\d+)_(CLIENT_ID|CLIENT_SECRET|SCOPES)$");

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
    Map<String, Object> mapped = new LinkedHashMap<>();
    for (PropertySource<?> source : env.getPropertySources()) {
      if (!(source instanceof EnumerablePropertySource<?> e)) {
        continue;
      }
      for (String name : e.getPropertyNames()) {
        Matcher m = NAME.matcher(name);
        if (!m.matches() || mapped.containsKey(key(m))) {
          continue;
        }
        Object value = e.getProperty(name);
        if ("SCOPES".equals(m.group(2)) && value != null) {
          String[] scopes = value.toString().split(",");
          for (int i = 0; i < scopes.length; i++) {
            mapped.put(key(m) + "[" + i + "]", scopes[i].strip());
          }
        } else {
          mapped.put(key(m), value);
        }
      }
    }
    if (!mapped.isEmpty()) {
      env.getPropertySources().addFirst(new MapPropertySource("ffAuthClients", mapped));
    }
  }

  private static String key(Matcher m) {
    String field =
        switch (m.group(2)) {
          case "CLIENT_ID" -> "client-id";
          case "CLIENT_SECRET" -> "client-secret";
          default -> "scopes";
        };
    return "featureflags.auth.clients[" + m.group(1) + "]." + field;
  }
}
