package com.example.featureflags.auth;

import org.springframework.stereotype.Component;

/** Admin credentials from configuration (spec 5.1). */
@Component
public class ConfigAdminAuthenticator implements AdminAuthenticator {

  private final ClientRegistrationProperties props;

  public ConfigAdminAuthenticator(ClientRegistrationProperties props) {
    this.props = props;
  }

  @Override
  public boolean authenticate(String username, String password) {
    boolean user = ConstantTime.equal(props.adminUsername(), username);
    boolean pass = ConstantTime.equal(props.adminPassword(), password);
    return user & pass;
  }
}
