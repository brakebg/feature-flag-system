package com.example.featureflags.auth;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

/**
 * Spec 5.1: consumer clients can be added through {@code FF_AUTH_CLIENTS_<n>_CLIENT_ID}, {@code
 * _CLIENT_SECRET} and {@code _SCOPES} (comma-separated) environment variables. They are appended
 * after the clients from the configuration file; env index 0 is the first env client (ESC-004).
 */
public class ClientEnvironment implements EnvironmentPostProcessor {

  private static final Pattern NAME =
      Pattern.compile("^FF_AUTH_CLIENTS_(\\d+)_(CLIENT_ID|CLIENT_SECRET|SCOPES)$");

  private static final String CLIENTS = "featureflags.auth.clients";

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
    List<ClientRegistration> configured =
        Binder.get(env).bind(CLIENTS, Bindable.listOf(ClientRegistration.class)).orElse(List.of());
    int offset = configured.size();
    Map<String, Object> mapped = new LinkedHashMap<>();
    for (PropertySource<?> source : env.getPropertySources()) {
      if (!(source instanceof EnumerablePropertySource<?> e)) {
        continue;
      }
      for (String name : e.getPropertyNames()) {
        Matcher m = NAME.matcher(name);
        if (!m.matches() || mapped.containsKey(key(m, offset))) {
          continue;
        }
        Object value = e.getProperty(name);
        if ("SCOPES".equals(m.group(2)) && value != null) {
          String[] scopes = value.toString().split(",");
          for (int i = 0; i < scopes.length; i++) {
            mapped.put(key(m, offset) + "[" + i + "]", scopes[i].strip());
          }
        } else {
          mapped.put(key(m, offset), value);
        }
      }
    }
    if (mapped.isEmpty()) {
      return;
    }
    // Spring binds a list from one property source only, so the new source repeats the
    // configured clients before the env clients.
    Map<String, Object> merged = new LinkedHashMap<>();
    for (int i = 0; i < configured.size(); i++) {
      ClientRegistration c = configured.get(i);
      String prefix = CLIENTS + "[" + i + "].";
      merged.put(prefix + "client-id", c.clientId());
      merged.put(prefix + "client-secret", c.clientSecret());
      for (int j = 0; j < c.scopes().size(); j++) {
        merged.put(prefix + "scopes[" + j + "]", c.scopes().get(j));
      }
    }
    merged.putAll(mapped);
    env.getPropertySources().addFirst(new MapPropertySource("ffAuthClients", merged));
  }

  private static String key(Matcher m, int offset) {
    String field =
        switch (m.group(2)) {
          case "CLIENT_ID" -> "client-id";
          case "CLIENT_SECRET" -> "client-secret";
          default -> "scopes";
        };
    return CLIENTS + "[" + (offset + Integer.parseInt(m.group(1))) + "]." + field;
  }
}
