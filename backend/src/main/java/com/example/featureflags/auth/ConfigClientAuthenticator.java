package com.example.featureflags.auth;

import java.util.Optional;
import org.springframework.stereotype.Component;

/** Client credentials from configuration (spec 5.1). */
@Component
public class ConfigClientAuthenticator implements ClientAuthenticator {

  private final ClientRegistrationProperties props;

  public ConfigClientAuthenticator(ClientRegistrationProperties props) {
    this.props = props;
  }

  @Override
  public Optional<ClientRegistration> authenticate(String clientId, String clientSecret) {
    ClientRegistration match = null;
    for (ClientRegistration c : props.clients()) {
      boolean id = ConstantTime.equal(c.clientId(), clientId);
      boolean secret = ConstantTime.equal(c.clientSecret(), clientSecret);
      if (id & secret) {
        match = c;
      }
    }
    return Optional.ofNullable(match);
  }
}
